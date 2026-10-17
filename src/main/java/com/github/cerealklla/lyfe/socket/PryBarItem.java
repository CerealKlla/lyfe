package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModMenus;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Mirrors {@link DieGrinderItem} exactly, for Extracting (explicit user spec: "removing socketed gems/runes"). */
public class PryBarItem extends Item {

    public PryBarItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(new SimpleMenuProvider(
                    (windowId, inventory, opener) -> new ExtractingMenu(ModMenus.EXTRACTING.get(), windowId, inventory),
                    Component.literal("Extract a Gem or Rune")));
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
