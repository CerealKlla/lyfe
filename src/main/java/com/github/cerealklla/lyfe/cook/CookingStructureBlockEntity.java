package com.github.cerealklla.lyfe.cook;

import com.github.cerealklla.lyfe.registration.ModBlockEntities;
import com.github.cerealklla.lyfe.registration.ModBlocks;
import com.github.cerealklla.lyfe.registration.ModMenus;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Direct structural mirror of {@code craft.CraftingStructureBlockEntity} -- see {@code CookingStructureMenu} for the actual recipe list/selection/cooking logic. */
public class CookingStructureBlockEntity extends BlockEntity implements MenuProvider {

    public static final int MAX_STRUCTURE_TIER = 5;

    private final int tier;

    public CookingStructureBlockEntity(BlockPos pos, BlockState state, int tier) {
        super(blockEntityTypeFor(tier), pos, state);
        this.tier = tier;
    }

    private static BlockEntityType<CookingStructureBlockEntity> blockEntityTypeFor(int tier) {
        return switch (tier) {
            case 1 -> ModBlockEntities.COOKING_STATION.get();
            case 2 -> ModBlockEntities.GRILL.get();
            case 3 -> ModBlockEntities.STOVE.get();
            case 4 -> ModBlockEntities.OVEN.get();
            case 5 -> ModBlockEntities.CHEF_SET.get();
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    /** The real placeable {@link CookingStructureBlock} instance for {@code tier} -- used by {@link CookingStructureMenu}'s "Upgrade Station" action. */
    public static CookingStructureBlock blockForTier(int tier) {
        return switch (tier) {
            case 1 -> ModBlocks.COOKING_STATION.get();
            case 2 -> ModBlocks.GRILL.get();
            case 3 -> ModBlocks.STOVE.get();
            case 4 -> ModBlocks.OVEN.get();
            case 5 -> ModBlocks.CHEF_SET.get();
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    public int tier() {
        return tier;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Cooking Structure (Tier " + tier + ")");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CookingStructureMenu(ModMenus.COOKING_STRUCTURE.get(), containerId, inventory, this, player);
    }

    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(tier);
        var craftable = ((CookingStructureMenu) menu).craftable();
        buffer.writeVarInt(craftable.size());
        for (GeneratedFoodRecipe recipe : craftable) {
            GeneratedFoodRecipe.writeTo(buffer, recipe);
        }
        buffer.writeBoolean(((CookingStructureMenu) menu).canUpgrade());
    }
}
