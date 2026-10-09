package com.github.cerealklla.lyfe.structure;

import java.util.Optional;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One resource a crafting/cooking structure's tier upgrade needs (design doc Section 19.9, added
 * 2026-10-05) -- a whole category (a real vanilla {@link TagKey}, e.g. "any log") or one specific
 * item, never both, plus how many units. Same tag-or-item shape as Settlemynts'
 * {@code zone.ShopResource}/Yconomics' {@code shop.ShopResource} (deliberately, since an entry here
 * gets converted straight into one of those for a Shop purchase -- see
 * {@code SettlemyntsStructureBridge}) -- not Lyfe's own {@code craft.ComponentGroups}, which has no
 * real registered tag backing it and so can't be priced/matched against a Shop listing at all.
 */
public record UpgradeCostEntry(Optional<TagKey<Item>> tag, Optional<Identifier> itemId, int amount) {

    public static UpgradeCostEntry ofTag(TagKey<Item> tag, int amount) {
        return new UpgradeCostEntry(Optional.of(tag), Optional.empty(), amount);
    }

    public static UpgradeCostEntry ofItem(Identifier itemId, int amount) {
        return new UpgradeCostEntry(Optional.empty(), Optional.of(itemId), amount);
    }

    public boolean matches(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag.isPresent()) {
            return stack.is(tag.get());
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(itemId.get());
    }

    /**
     * A short player-facing label -- "any log" for a tag (stripped of its namespace/path noise), or
     * the item's real display name for a specific item. Fixed 2026-10-09 -- a specific item used to
     * show its raw registry id ("minecraft:cobblestone") instead of a real name, same class of bug
     * already fixed once for {@code repair.RepairCost}'s own preview.
     */
    public String label() {
        return tag.map(t -> "any " + t.location().getPath().replace('_', ' '))
                .orElseGet(() -> new ItemStack(BuiltInRegistries.ITEM.getValue(itemId.get())).getHoverName().getString());
    }
}
