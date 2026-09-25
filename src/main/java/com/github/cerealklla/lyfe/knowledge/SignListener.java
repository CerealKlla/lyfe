package com.github.cerealklla.lyfe.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.cartographyr.geo.Geometry;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Placement/reading behavior for the Cartographyr-skill sign/map mechanic (design doc Section
 * 9.1). Only ever registered when Cartographyr is loaded (see {@code LyfeMod}) -- every method
 * here freely calls Cartographyr's API.
 *
 * <p>Signs are real vanilla {@link Blocks#OAK_SIGN} blocks placed on top of the clicked fence
 * post, with a {@link KnowledgeReference} attached directly to the resulting {@link
 * SignBlockEntity} via NeoForge's attachment system (block entities already support this, being
 * {@code AttachmentHolder}s). Maps are a plain item carrying the same {@link KnowledgeReference}
 * as a data component, inserted into the target item frame -- see decisions.md for why this
 * doesn't render an actual cartographic minimap texture.
 *
 * <p>Every sign/map references a real known place (free text was removed 2026-09-24).
 */
public final class SignListener {

    private static final String ARROW_RIGHT = "--->";
    private static final String ARROW_LEFT = "<---";

    // Placeholder thresholds -- untuned, like every other magnitude in this project. A writer can
    // never embed better precision than either this level cap or their own actual knowledge of the
    // place (design doc Section 9.1's double constraint).
    static LocationPrecision levelCap(int cartographyrLevel) {
        if (cartographyrLevel < 10) {
            return LocationPrecision.RELATIVE;
        }
        if (cartographyrLevel < 25) {
            return LocationPrecision.APPROXIMATE;
        }
        return LocationPrecision.EXACT;
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        BlockPos pos = event.getPos();
        ItemStack held = event.getItemStack();

        if (held.is(ModItems.CARTOGRAPHYR_SIGN.get()) && level.getBlockState(pos).is(BlockTags.FENCES)) {
            BlockPos signPos = pos.above();
            if (!level.getBlockState(signPos).isAir()) {
                return;
            }
            openWritingScreen(player, WritingTarget.sign(signPos));
            return;
        }

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SignBlockEntity) {
            blockEntity.getExistingData(ModAttachments.SIGN_REFERENCE)
                    .ifPresent(reference -> handleRead(player, reference));
        }
    }

    @SubscribeEvent
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (!(event.getTarget() instanceof ItemFrame frame)) {
            return;
        }
        ItemStack held = event.getItemStack();

        if (held.is(ModItems.CARTOGRAPHYR_MAP.get()) && frame.getItem().isEmpty()) {
            openWritingScreen(player, WritingTarget.map(frame.getId()));
            event.setCanceled(true);
            return;
        }

        KnowledgeReference reference = frame.getItem().get(ModItems.KNOWLEDGE_REFERENCE);
        if (reference != null) {
            handleRead(player, reference);
            event.setCanceled(true);
        }
    }

    private void openWritingScreen(ServerPlayer player, WritingTarget target) {
        int level = Lyfe.getLevel(player, Skills.CARTOGRAPHYR_ID);
        LocationPrecision cap = levelCap(level);

        List<OpenWritingScreenPayload.KnownPlace> knownPlaces = new ArrayList<>();
        for (Map.Entry<Long, KnowledgeEntry> entry : player.getData(ModAttachments.PLAYER_KNOWLEDGE).entries().entrySet()) {
            Optional<LocationPrecision> known = entry.getValue().locationPrecision();
            if (known.isEmpty()) {
                continue;
            }
            EntityId id = new EntityId(entry.getKey());
            Optional<GeographicEntity> geo = Cartography.getEntity((ServerLevel) player.level(), id);
            if (geo.isEmpty() || !geo.get().classification().equals(Classification.CONSTRUCTED)) {
                continue;
            }
            LocationPrecision embeddable = known.get().isAtLeastAsPreciseAs(cap) ? cap : known.get();
            String name = geo.get().name().orElse("an unnamed place");
            knownPlaces.add(new OpenWritingScreenPayload.KnownPlace(entry.getKey(), name, embeddable));
        }

        PacketDistributor.sendToPlayer(player, new OpenWritingScreenPayload(target, knownPlaces));
    }

    /** Called from {@code LyfeMod}'s payload registration -- see that class for why this is guarded there too. */
    public static void handleSubmit(ServerPlayer player, SubmitWritingPayload payload) {
        ServerLevel level = (ServerLevel) player.level();
        long placeId = payload.chosenPlaceId();

        Optional<KnowledgeEntry> entry = player.getData(ModAttachments.PLAYER_KNOWLEDGE).get(placeId);
        Optional<GeographicEntity> geo = Cartography.getEntity(level, new EntityId(placeId));
        if (entry.isEmpty() || entry.get().locationPrecision().isEmpty() || geo.isEmpty()) {
            return;
        }
        int skillLevel = Lyfe.getLevel(player, Skills.CARTOGRAPHYR_ID);
        LocationPrecision embeddable = entry.get().locationPrecision().get().isAtLeastAsPreciseAs(levelCap(skillLevel))
                ? levelCap(skillLevel)
                : entry.get().locationPrecision().get();
        KnowledgeReference reference = new KnowledgeReference(placeId, embeddable, geo.get().name().orElse("an unnamed place"));

        ItemStack held = player.getMainHandItem();
        WritingTarget target = payload.target();

        if (target.kind() == WritingTarget.Kind.SIGN) {
            if (!held.is(ModItems.CARTOGRAPHYR_SIGN.get()) || !level.getBlockState(target.blockPos()).isAir()) {
                return;
            }
            placeSign(level, player, target.blockPos(), reference, geo.get().geometry());
        } else {
            if (!held.is(ModItems.CARTOGRAPHYR_MAP.get())) {
                return;
            }
            var entity = level.getEntity(target.frameEntityId());
            if (!(entity instanceof ItemFrame frame) || !frame.getItem().isEmpty()) {
                return;
            }
            ItemStack mapStack = new ItemStack(ModItems.CARTOGRAPHYR_MAP.get());
            mapStack.set(ModItems.KNOWLEDGE_REFERENCE, reference);
            mapStack.set(DataComponents.CUSTOM_NAME, Component.literal(reference.displayText()));
            frame.setItem(mapStack);
        }

        held.shrink(1);
    }

    /**
     * Places the sign rotated so a text arrow points toward {@code targetGeometry}'s center, and
     * writes the place name / distance (if precise enough) / arrow onto the front face. The
     * rotation math is derived from {@link RotationSegment}'s documented NORTH_0/EAST_90/SOUTH_180/
     * WEST_270 constants and vanilla's own view-vector formula, but -- unlike everything else in
     * this project -- hasn't been empirically verified in-game yet (no live feedback loop during
     * design); if a placed sign points the wrong way, the fix is swapping the two {@code
     * frontNormal} cases below, not a deeper redesign. See decisions.md for the full derivation.
     */
    private static void placeSign(ServerLevel level, ServerPlayer player, BlockPos signPos, KnowledgeReference reference, Geometry targetGeometry) {
        ChunkPos min = targetGeometry.minChunk();
        ChunkPos max = targetGeometry.maxChunk();
        ChunkPos centerChunk = new ChunkPos((min.x() + max.x()) / 2, (min.z() + max.z()) / 2);
        double targetX = centerChunk.getMiddleBlockX();
        double targetZ = centerChunk.getMiddleBlockZ();

        double toTargetX = targetX - signPos.getX();
        double toTargetZ = targetZ - signPos.getZ();
        double distance = Math.sqrt(toTargetX * toTargetX + toTargetZ * toTargetZ);
        double normX = toTargetX / distance;
        double normZ = toTargetZ / distance;

        double yawRad = Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);

        boolean targetIsRight = isRightOf(forwardX, forwardZ, normX, normZ);
        int rotationSegment = RotationSegment.convertToSegment((float) frontFacingBearingDegrees(normX, normZ, targetIsRight));
        var signState = Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, rotationSegment);
        level.setBlock(signPos, signState, 3);

        if (level.getBlockEntity(signPos) instanceof SignBlockEntity sign) {
            SignText text = new SignText().setMessage(0, Component.literal(reference.displayText()));
            if (reference.embeddedPrecision() == LocationPrecision.EXACT) {
                text = text.setMessage(2, Component.literal(Math.round(distance) + " blocks"));
            }
            text = text.setMessage(3, Component.literal(targetIsRight ? ARROW_RIGHT : ARROW_LEFT));
            sign.setText(text, true);
            sign.setData(ModAttachments.SIGN_REFERENCE, reference);
        }
    }

    /**
     * Whether {@code (towardX, towardZ)} is to the right of facing direction {@code (forwardX,
     * forwardZ)} (both MC-convention direction vectors: north=-Z, south=+Z, east=+X, west=-X).
     * Verified against a concrete example: facing north {@code (0,-1)} with something due east
     * {@code (1,0)} of the observer must read as "right" (east is right of north) -- confirmed
     * {@code cross = 0*0 - (-1)*1 = 1 > 0}. Package-visible for {@code SignListenerTest}.
     */
    static boolean isRightOf(double forwardX, double forwardZ, double towardX, double towardZ) {
        double cross = forwardX * towardZ - forwardZ * towardX;
        return cross >= 0;
    }

    /**
     * The {@link RotationSegment#convertToSegment(float)} degree value for a sign whose front
     * should show its right-hand arrow (if {@code targetIsRight}) or left-hand arrow pointing at
     * direction {@code (normX, normZ)}. The direction-vector-to-reader-position math is solved by
     * "a reader's right-hand direction, given they face the sign, equals the direction to the
     * target"; separately, {@link RotationSegment}'s degree value for a sign is offset 180° from
     * that reader-facing direction, not equal to it -- confirmed 2026-09-24 against a concrete
     * vanilla placement example (player facing south, sign appears in front of them; for the
     * placer to read it immediately, the front must face north/toward them, but vanilla's own
     * {@code getStateForPlacement} computes {@code rotation = yRot + 180 = south}, i.e. the
     * *opposite* of the required front-facing direction) after live playtesting showed the first
     * version of this formula placed the text on the opposite side from the placer with the arrow
     * pointing the wrong way. See decisions.md for the full derivation and worked example.
     * Package-visible for {@code SignListenerTest}.
     */
    static double frontFacingBearingDegrees(double normX, double normZ, boolean targetIsRight) {
        double frontX = targetIsRight ? -normZ : normZ;
        double frontZ = targetIsRight ? normX : -normX;
        return bearingDegrees(-frontX, -frontZ);
    }

    /**
     * Converts a MC-convention direction vector (north=-Z, south=+Z, east=+X, west=-X) into the
     * degree space {@link RotationSegment#convertToSegment(float)} expects -- verified against
     * that class's own documented constants (NORTH_0=0, EAST_90=4*22.5=90, SOUTH_180=8*22.5=180,
     * WEST_270=12*22.5=270). Package-visible for {@code SignListenerTest}.
     */
    static double bearingDegrees(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(dx, -dz));
        return degrees < 0 ? degrees + 360 : degrees;
    }

    private void handleRead(ServerPlayer reader, KnowledgeReference reference) {
        boolean changed = reader.getData(ModAttachments.PLAYER_KNOWLEDGE)
                .upgradeLocationPrecision(reference.entityId(), reference.embeddedPrecision());

        if (changed) {
            int amount = 10; // Placeholder flat XP per read, untuned.
            long newXp = Lyfe.addXp(reader, Skills.CARTOGRAPHYR_ID, amount);
            int newLevel = Lyfe.getLevel(reader, Skills.CARTOGRAPHYR_ID);
            reader.sendSystemMessage(Component.literal(
                    "+" + amount + " Cartographyr XP (Level " + newLevel + ", total " + newXp + ")"));
        } else {
            reader.sendSystemMessage(Component.literal("(already know as much or more about this place)"));
        }
    }
}
