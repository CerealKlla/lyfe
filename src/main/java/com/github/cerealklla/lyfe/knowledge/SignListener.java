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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RotationSegment;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Placement/reading behavior for the Cartographyr-skill sign/map mechanic (design doc Section
 * 9.1). Only ever registered when Cartographyr is loaded (see {@code LyfeMod}) -- every method
 * here freely calls Cartographyr's API.
 *
 * <p><b>Rides on genuinely vanilla items and blocks, 2026-09-25</b> (see decisions.md) -- there
 * is no special trigger item anymore. Signs are placed entirely through vanilla's own sign-item
 * placement flow; a "Cartographyr" button is injected into vanilla's own {@code SignEditScreen}
 * on a sign's first edit when it was placed on a fence post (see {@code LyfeModClient}), which
 * routes to {@link #requestSignWritingScreen}/{@link #handleSubmit} instead of vanilla's normal
 * free-text submission. Maps are triggered by right-clicking while holding any plain vanilla
 * {@link Items#MAP}/{@link Items#FILLED_MAP} ({@link #onRightClickItem}) -- the resulting item
 * stays a completely ordinary {@code Items#FILLED_MAP} the player can carry, trade, or sell, not
 * a special item or something that has to live in an item frame. Either way, a {@link
 * KnowledgeReference} is attached (a sign block entity attachment, or an item data component) --
 * once attached, that specific sign/map is "used up": a bound sign can no longer be re-edited
 * (see {@link #onRightClickBlock}'s read branch, which cancels the interaction) and a bound map
 * can no longer be re-targeted (see {@link #onRightClickItem}'s existing-reference check) -- so a
 * player can't transfer unlimited knowledge through a single physical item.
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

        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof SignBlockEntity) {
            blockEntity.getExistingData(ModAttachments.SIGN_REFERENCE).ifPresent(reference -> {
                handleRead(player, reference);
                // A bound sign's front text is still plain literal content, which vanilla's own
                // SignBlock#hasEditableText would otherwise happily let a player reopen for
                // editing (closing that screen unconditionally re-sends its text, wiping the
                // name/arrow this mod wrote) -- cancel here so vanilla's useWithoutItem never runs.
                event.setCanceled(true);
            });
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

        KnowledgeReference reference = frame.getItem().get(ModItems.KNOWLEDGE_REFERENCE);
        if (reference != null) {
            handleRead(player, reference);
            event.setCanceled(true);
        }
    }

    /**
     * Right-clicking while holding any plain vanilla map (blank {@link Items#MAP} or a real,
     * player-explored {@link Items#FILLED_MAP}) opens the known-places picker; confirming
     * overwrites that exact physical map with a rendered one, cancelling leaves it untouched.
     * Right-clicking a map that's already a Cartographyr map ({@link KnowledgeReference} present)
     * reads it instead -- it can't be re-targeted, so one physical map can't be reused to harvest
     * unlimited knowledge.
     */
    @SubscribeEvent
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        ItemStack held = event.getItemStack();
        if (!held.is(Items.MAP) && !held.is(Items.FILLED_MAP)) {
            return;
        }

        KnowledgeReference reference = held.get(ModItems.KNOWLEDGE_REFERENCE);
        if (reference != null) {
            handleRead(player, reference);
        } else {
            openWritingScreen(player, WritingTarget.map());
        }
        event.setCanceled(true);
    }

    /**
     * Server-side handler for {@link RequestWritingScreenPayload} -- the client-initiated request
     * sent when a player clicks the "Cartographyr" button injected into vanilla's own {@code
     * SignEditScreen} (see {@code LyfeModClient}). Re-validates the target independently of the
     * client-side button-visibility heuristic, same "don't trust the client" posture as {@link
     * #handleSubmit}.
     */
    public static void requestSignWritingScreen(ServerPlayer player, WritingTarget target) {
        if (target.kind() != WritingTarget.Kind.SIGN) {
            return;
        }
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = target.blockPos();
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || sign.getExistingData(ModAttachments.SIGN_REFERENCE).isPresent()) {
            return;
        }
        if (!level.getBlockState(pos.below()).is(BlockTags.FENCES)) {
            return;
        }
        openWritingScreen(player, target);
    }

    private static void openWritingScreen(ServerPlayer player, WritingTarget target) {
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

        WritingTarget target = payload.target();

        if (target.kind() == WritingTarget.Kind.SIGN) {
            BlockPos pos = target.blockPos();
            if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign) || sign.getExistingData(ModAttachments.SIGN_REFERENCE).isPresent()) {
                return;
            }
            if (!level.getBlockState(pos.below()).is(BlockTags.FENCES)) {
                return;
            }
            bindSign(level, player, sign, reference, geo.get().geometry());
        } else {
            ItemStack held = player.getMainHandItem();
            if ((!held.is(Items.MAP) && !held.is(Items.FILLED_MAP)) || held.has(ModItems.KNOWLEDGE_REFERENCE)) {
                return;
            }
            ServerLevel targetLevel = level.getServer().getLevel(geo.get().dimension());
            if (targetLevel == null) {
                return;
            }
            BlockPos center = geometryCenter(geo.get().geometry());
            byte scale = mapScaleFor(geo.get().geometry());
            ItemStack mapStack = TerrainMapRenderer.createFilledMap(targetLevel, center.getX(), center.getZ(), scale);
            MapItemSavedData mapData = MapItem.getSavedData(mapStack, targetLevel);
            if (mapData != null) {
                TerrainMapRenderer.renderTerrain(targetLevel, mapData, center.getX(), center.getZ(), scale);
            }
            mapStack.set(ModItems.KNOWLEDGE_REFERENCE, reference);
            mapStack.set(DataComponents.CUSTOM_NAME, Component.literal(reference.displayText()));

            // Replaces the exact physical map the player right-clicked -- a stack of blank maps
            // only ever loses one (the rest stay a normal stackable blank-map stack), a single
            // held map (blank or already-explored) is entirely overwritten in place.
            if (held.getCount() == 1) {
                player.setItemInHand(InteractionHand.MAIN_HAND, mapStack);
            } else {
                held.shrink(1);
                if (!player.getInventory().add(mapStack)) {
                    player.drop(mapStack, false);
                }
            }
        }
    }

    /**
     * Binds {@code reference} to a sign that vanilla has already placed (see {@code
     * LyfeModClient}'s "Cartographyr" button and {@link #requestSignWritingScreen}) -- rotates it
     * so a text arrow points toward {@code targetGeometry}'s center and writes the place name /
     * distance (if precise enough) / arrow onto the front face. The rotation math is derived from
     * {@link RotationSegment}'s documented NORTH_0/EAST_90/SOUTH_180/WEST_270 constants and
     * vanilla's own view-vector formula -- unchanged from the original placement code, still not
     * yet empirically re-confirmed in-game since this rewrite (see decisions.md/CLAUDE.md).
     */
    private static void bindSign(ServerLevel level, ServerPlayer player, SignBlockEntity sign, KnowledgeReference reference, Geometry targetGeometry) {
        BlockPos signPos = sign.getBlockPos();
        BlockPos center = geometryCenter(targetGeometry);
        double targetX = center.getX();
        double targetZ = center.getZ();

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
        BlockState currentState = level.getBlockState(signPos);
        level.setBlock(signPos, currentState.setValue(StandingSignBlock.ROTATION, rotationSegment), 3);

        // Re-fetch rather than trust the pre-rotation `sign` reference is still valid -- same
        // defensive habit the original placement code used, since setBlock can in principle
        // replace the block entity instance.
        if (!(level.getBlockEntity(signPos) instanceof SignBlockEntity boundSign)) {
            return;
        }
        SignText text = new SignText().setMessage(0, Component.literal(reference.displayText()));
        if (reference.embeddedPrecision() == LocationPrecision.EXACT) {
            text = text.setMessage(2, Component.literal(Math.round(distance) + " blocks"));
        }
        text = text.setMessage(3, Component.literal(arrowFor(targetIsRight)));
        boundSign.setText(text, true);
        boundSign.setData(ModAttachments.SIGN_REFERENCE, reference);
    }

    /**
     * A target's representative center: the bounding-box midpoint of its {@link Geometry}'s
     * {@code minChunk()}/{@code maxChunk()}. Used both for sign-rotation math and, since 2026-09-24,
     * as the origin for {@link TerrainMapRenderer}'s auto-rendered maps.
     */
    private static BlockPos geometryCenter(Geometry geometry) {
        ChunkPos min = geometry.minChunk();
        ChunkPos max = geometry.maxChunk();
        ChunkPos centerChunk = new ChunkPos((min.x() + max.x()) / 2, (min.z() + max.z()) / 2);
        return new BlockPos(centerChunk.getMiddleBlockX(), 0, centerChunk.getMiddleBlockZ());
    }

    /**
     * Picks the smallest vanilla map scale (0-4, grid size {@code 128 * 2^scale}) that comfortably
     * contains {@code geometry}'s footprint with room for surrounding context. Placeholder heuristic
     * (grid >= 3x footprint width) -- untuned, like every other magnitude in this project. Package-
     * visible for {@code SignListenerTest}.
     */
    static byte mapScaleFor(Geometry geometry) {
        ChunkPos min = geometry.minChunk();
        ChunkPos max = geometry.maxChunk();
        int footprintWidth = Math.max(
                (max.x() - min.x() + 1) * 16,
                (max.z() - min.z() + 1) * 16);

        for (byte scale = 0; scale < 4; scale++) {
            int gridWidth = 128 * (1 << scale);
            if (gridWidth >= footprintWidth * 3) {
                return scale;
            }
        }
        return 4;
    }

    /**
     * The arrow glyph a reader should see for a given {@code targetIsRight}. Briefly "corrected"
     * to a mirrored mapping on 2026-09-24 based on a playtest report that turned out to be a false
     * alarm -- the reporter had gotten turned around at night and the original, unmirrored mapping
     * (kept here) was actually correct all along. See decisions.md, 2026-09-24 (the "retraction"
     * entry), for the full story. Package-visible for {@code SignListenerTest}.
     */
    static String arrowFor(boolean targetIsRight) {
        return targetIsRight ? ARROW_RIGHT : ARROW_LEFT;
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
