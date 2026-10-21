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
 * Intercepts a right-click on the Grindstone and opens {@link RepairStructureMenu} instead of
 * vanilla's own Grindstone menu (2026-10-07 explicit user decision -- disenchanting on the
 * Grindstone is gone; repairing now happens through this screen's resources/gold economy).
 *
 * <p><b>Reverted 2026-10-21, explicit user request</b>: the Anvil is no longer intercepted at all --
 * right-clicking any Anvil damage-state block (Anvil/Chipped Anvil/Damaged Anvil) now opens vanilla's
 * own real Anvil menu again (renaming, combining/repairing two items, enchantment application), while
 * the Grindstone keeps this mod's custom repair economy. Vanilla's own Anvil repair path sets damage
 * directly rather than going through {@code ItemStack#hurtAndBreak}, so it's unaffected by Unbreakable
 * Equipment's damage-clamping mixin.
 */
public final class RepairInteractionListener {

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        var block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block != Blocks.GRINDSTONE) {
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
