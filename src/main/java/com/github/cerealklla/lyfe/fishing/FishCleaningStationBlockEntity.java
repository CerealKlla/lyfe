package com.github.cerealklla.lyfe.fishing;

import com.github.cerealklla.lyfe.registration.ModBlockEntities;
import com.github.cerealklla.lyfe.registration.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The Fish Cleaning Station's real GUI (rebuilt 2026-10-05, replacing the original one-click
 * {@code useItemOn} interaction -- user feedback: "let's make it show a UI with 1 slot ... and 2
 * buttons"). Same shape as {@code research.ResearchBenchBlockEntity}: a single input slot, the
 * {@code BlockEntity} itself as {@code MenuProvider}.
 */
public class FishCleaningStationBlockEntity extends BlockEntity implements MenuProvider {

    private final Container inputContainer = new SimpleContainer(1);

    public FishCleaningStationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FISH_CLEANING_STATION.get(), pos, state);
    }

    public Container inputContainer() {
        return inputContainer;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Fish Cleaning Station");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new FishCleaningMenu(ModMenus.FISH_CLEANING_STATION.get(), containerId, inventory, inputContainer);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        ContainerHelper.loadAllItems(input.childOrEmpty("Input"), ((SimpleContainer) inputContainer).getItems());
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output.child("Input"), ((SimpleContainer) inputContainer).getItems());
    }
}
