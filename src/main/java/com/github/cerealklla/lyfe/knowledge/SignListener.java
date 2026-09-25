package com.github.cerealklla.lyfe.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.EntityId;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 */
public final class SignListener {

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
        KnowledgeReference reference;

        if (payload.freeText().isPresent()) {
            reference = KnowledgeReference.freeText(payload.freeText().get());
        } else if (payload.chosenPlaceId().isPresent()) {
            long placeId = payload.chosenPlaceId().get();
            Optional<KnowledgeEntry> entry = player.getData(ModAttachments.PLAYER_KNOWLEDGE).get(placeId);
            Optional<GeographicEntity> geo = Cartography.getEntity(level, new EntityId(placeId));
            if (entry.isEmpty() || entry.get().locationPrecision().isEmpty() || geo.isEmpty()) {
                return;
            }
            int skillLevel = Lyfe.getLevel(player, Skills.CARTOGRAPHYR_ID);
            LocationPrecision embeddable = entry.get().locationPrecision().get().isAtLeastAsPreciseAs(levelCap(skillLevel))
                    ? levelCap(skillLevel)
                    : entry.get().locationPrecision().get();
            reference = KnowledgeReference.knownPlace(placeId, embeddable, geo.get().name().orElse("an unnamed place"));
        } else {
            return;
        }

        ItemStack held = player.getMainHandItem();
        WritingTarget target = payload.target();

        if (target.kind() == WritingTarget.Kind.SIGN) {
            if (!held.is(ModItems.CARTOGRAPHYR_SIGN.get()) || !level.getBlockState(target.blockPos()).isAir()) {
                return;
            }
            var signState = Blocks.OAK_SIGN.defaultBlockState()
                    .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(player.getYRot() + 180.0F));
            level.setBlock(target.blockPos(), signState, 3);
            if (level.getBlockEntity(target.blockPos()) instanceof SignBlockEntity sign) {
                sign.setText(new SignText().setMessage(0, Component.literal(reference.displayText())), true);
                sign.setData(ModAttachments.SIGN_REFERENCE, reference);
            }
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
            mapStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(reference.displayText()));
            frame.setItem(mapStack);
        }

        held.shrink(1);
    }

    private void handleRead(ServerPlayer reader, KnowledgeReference reference) {
        if (reference.entityId().isEmpty() || reference.embeddedPrecision().isEmpty()) {
            return; // Free text: no EntityId behind it, so it can never grant XP (anti-farming rule).
        }

        long placeId = reference.entityId().get();
        boolean changed = reader.getData(ModAttachments.PLAYER_KNOWLEDGE)
                .upgradeLocationPrecision(placeId, reference.embeddedPrecision().get());

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
