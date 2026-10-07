package com.github.cerealklla.lyfe.research;

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

public class ResearchBenchBlockEntity extends BlockEntity implements MenuProvider {

    private final Container inputContainer = new SimpleContainer(1);

    public ResearchBenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESEARCH_BENCH.get(), pos, state);
    }

    public Container inputContainer() {
        return inputContainer;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Research Bench");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new ResearchMenu(ModMenus.RESEARCH_BENCH.get(), containerId, inventory, inputContainer, this);
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
