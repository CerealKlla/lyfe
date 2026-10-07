package com.github.cerealklla.lyfe.repair;

import com.github.cerealklla.lyfe.registration.ModMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Intercepts a right-click on vanilla's own repair blocks and opens {@link RepairStructureMenu}
 * instead of the vanilla Anvil/Grindstone menu entirely (2026-10-07 explicit user decision -- vanilla
 * renaming/combining on the Anvil and disenchanting on the Grindstone are gone; repairing now only
 * happens through this screen's resources/gold economy). Covers every Anvil damage-state block
 * (Anvil/Chipped Anvil/Damaged Anvil all share the same menu, same as vanilla) plus the Grindstone.
 */
public final class RepairInteractionListener {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block != Blocks.ANVIL && block != Blocks.CHIPPED_ANVIL && block != Blocks.DAMAGED_ANVIL && block != Blocks.GRINDSTONE) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS_SERVER);

        var pos = event.getPos();
        ContainerLevelAccess access = ContainerLevelAccess.create(event.getLevel(), pos);
        player.openMenu(new SimpleMenuProvider(
                (windowId, inventory, opener) -> new RepairStructureMenu(ModMenus.REPAIR_STRUCTURE.get(), windowId, inventory, access),
                Component.literal("Repair")));
    }
}
