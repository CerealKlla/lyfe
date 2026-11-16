package com.github.cerealklla.lyfe.registration;

import java.util.function.Supplier;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.socket.GemUpgradeRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRecipes {

    private ModRecipes() {
    }

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, LyfeMod.MODID);

    // No custom RecipeType needed -- GemUpgradeRecipe is a CraftingRecipe (via CustomRecipe), whose
    // default getType() is the vanilla shared RecipeType.CRAFTING, same precedent as Yconomics'
    // own registration.ModRecipes#GAMBLING_SERIALIZER.
    public static final Supplier<RecipeSerializer<GemUpgradeRecipe>> GEM_UPGRADE_SERIALIZER = RECIPE_SERIALIZERS.register(
            "gem_upgrade",
            () -> new RecipeSerializer<>(GemUpgradeRecipe.MAP_CODEC, GemUpgradeRecipe.STREAM_CODEC)
    );
}
