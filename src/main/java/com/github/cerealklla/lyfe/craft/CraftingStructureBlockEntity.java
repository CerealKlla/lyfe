package com.github.cerealklla.lyfe.craft;

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

/**
 * No input/output {@code Container} at all (removed 2026-10-02, see decisions.md) -- the user's own
 * reference UI (a villager-trade/recipe-book style screen: known recipes listed on the left, the
 * selected one's cost + a "Craft" button on the right) consumes directly from the player's carried
 * inventory, with no manual ingredient placement step. See {@code CraftingStructureMenu} for the
 * actual recipe list/selection/crafting logic.
 */
public class CraftingStructureBlockEntity extends BlockEntity implements MenuProvider {

    // The structure ladder's own max -- a separate axis from EquipmentTierLadder.MAX_TIER (2026-10-03
    // fix: those two counts coincidentally matched before Copper's insertion made the equipment
    // ladder longer than the physical structure ladder; code that gates "is this the top station"
    // must compare against THIS constant, not the equipment one, or it tries to build a 6th block
    // that doesn't exist).
    public static final int MAX_STRUCTURE_TIER = 5;

    private final int tier;

    public CraftingStructureBlockEntity(BlockPos pos, BlockState state, int tier) {
        super(blockEntityTypeFor(tier), pos, state);
        this.tier = tier;
    }

    private static BlockEntityType<CraftingStructureBlockEntity> blockEntityTypeFor(int tier) {
        return switch (tier) {
            case 1 -> ModBlockEntities.CRAFTING_STATION.get();
            case 2 -> ModBlockEntities.CRAFTING_MAT.get();
            case 3 -> ModBlockEntities.TINKER_BENCH.get();
            case 4 -> ModBlockEntities.CRAFTING_TABLE_LYFE.get();
            case 5 -> ModBlockEntities.ENGINEERS_BENCH.get();
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    /** The real placeable {@link CraftingStructureBlock} instance for {@code tier} -- used by {@link CraftingStructureMenu}'s "Upgrade Station" action. */
    public static CraftingStructureBlock blockForTier(int tier) {
        return switch (tier) {
            case 1 -> ModBlocks.CRAFTING_STATION.get();
            case 2 -> ModBlocks.CRAFTING_MAT.get();
            case 3 -> ModBlocks.TINKER_BENCH.get();
            case 4 -> ModBlocks.CRAFTING_TABLE_LYFE.get();
            case 5 -> ModBlocks.ENGINEERS_BENCH.get();
            default -> throw new IllegalArgumentException("tier " + tier);
        };
    }

    public int tier() {
        return tier;
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Crafting Structure (Tier " + tier + ")");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CraftingStructureMenu(ModMenus.CRAFTING_STRUCTURE.get(), containerId, inventory, this, player);
    }

    /**
     * Tiers 1-5 have different slot counts, so the client needs to know which one to build before it
     * can construct a matching {@code CraftingStructureMenu} -- real crash found live, 2026-10-02:
     * without this, the client always reconstructed a fixed (Tier 2) slot count regardless of which
     * tier was actually opened, so {@code ClientboundContainerSetContentPacket} indexed out of bounds
     * the moment a different tier was opened. Now also carries the player's known+tier-eligible
     * recipe list (computed once, server-side, in {@code CraftingStructureMenu}'s constructor) so the
     * client can render the recipe-list/cost UI without a second round-trip.
     */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(tier);
        var craftable = ((CraftingStructureMenu) menu).craftable();
        buffer.writeVarInt(craftable.size());
        for (GeneratedRecipe recipe : craftable) {
            GeneratedRecipe.writeTo(buffer, recipe);
        }
    }
}
