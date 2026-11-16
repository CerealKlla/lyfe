package com.github.cerealklla.lyfe.socket;

import com.mojang.serialization.MapCodec;

import com.github.cerealklla.lyfe.registration.ModRecipes;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * "3 of one quality becomes 1 of the next tier up" -- explicit user spec, 2026-11-15, vanilla
 * crafting table. A {@link CustomRecipe} rather than a data-driven shaped/shapeless recipe, same
 * precedent as {@code yconomics.recipe.GamblingRecipe} and for the same underlying reason: vanilla's
 * shaped/shapeless codecs can only match by registered item, with no way to also require the 3
 * inputs share the same stored {@link GemQuality} component value, and the output's item+quality are
 * both derived from whichever matched, not a single fixed result. No JSON fields at all -- declared
 * in {@code data/lyfe/recipe/gem_upgrade.json} as just {@code {"type": "lyfe:gem_upgrade"}}, same
 * shape as {@code gambling.json}.
 *
 * <p>No recipe exists for {@link GemQuality#PERFECT} inputs -- {@link GemQuality#next()} returns
 * {@code null} there, and {@link #matches} correctly declines (nothing to upgrade to).
 */
public class GemUpgradeRecipe extends CustomRecipe {

    public static final GemUpgradeRecipe INSTANCE = new GemUpgradeRecipe();
    public static final MapCodec<GemUpgradeRecipe> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final StreamCodec<RegistryFriendlyByteBuf, GemUpgradeRecipe> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return findMatch(input) != null;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        Match match = findMatch(input);
        return match == null ? ItemStack.EMPTY : GemItem.create(match.type(), match.nextQuality());
    }

    private record Match(GemType type, GemQuality nextQuality) {
    }

    /**
     * Non-null only when the grid holds exactly 3 non-empty slots, each a single (count 1) Gem of
     * the same {@link GemType} and the same {@link GemQuality}, with a tier above it to upgrade into.
     */
    private static Match findMatch(CraftingInput input) {
        if (input.ingredientCount() != 3) {
            return null;
        }
        GemType sharedType = null;
        GemQuality sharedQuality = null;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.getCount() != 1) {
                return null;
            }
            GemType type = GemItem.gemTypeOf(stack);
            if (type == null) {
                return null;
            }
            GemQuality quality = GemItem.gemQualityOf(stack);
            if (sharedType == null) {
                sharedType = type;
                sharedQuality = quality;
            } else if (sharedType != type || sharedQuality != quality) {
                return null;
            }
        }
        if (sharedType == null) {
            return null;
        }
        GemQuality next = sharedQuality.next();
        return next == null ? null : new Match(sharedType, next);
    }

    @Override
    public RecipeSerializer<GemUpgradeRecipe> getSerializer() {
        return ModRecipes.GEM_UPGRADE_SERIALIZER.get();
    }
}
