package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * "Player holds the Die Grinder in their hand and right clicks. This opens a GUI" (explicit user
 * spec). Lyfe's first-ever held-item-triggered custom menu -- every existing {@code player.openMenu}
 * call in this mod originates from a Block/BlockEntity (confirmed by a repo-wide search before
 * writing this); the mechanics are identical either way, {@link Player#openMenu} doesn't care what
 * triggered it, so this just skips the block-position lookup every block-based opener has.
 */
public class DieGrinderItem extends Item {

    public DieGrinderItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, opener) -> new SocketingMenu(ModMenus.SOCKETING.get(), windowId, inventory),
                    Component.literal("Add a Socket")));
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
