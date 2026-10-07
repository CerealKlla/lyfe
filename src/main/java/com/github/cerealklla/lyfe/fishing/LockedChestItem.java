package com.github.cerealklla.lyfe.fishing;

import java.util.Optional;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * One of 5 mineral-named Locked Chest variants (design doc Section G). Starts locked (fixed
 * contents baked in at creation, inaccessible, display name suffixed " (Locked)"); {@link
 * LockedChestUnlockListener} flips the lock component once a matching Key succeeds its 50% roll.
 * Once unlocked, behaves exactly like {@link SunkenTreasureItem} -- pop-only, self-destructs when
 * emptied, never accepts insertion (plain {@code Item}, no override needed for that).
 */
public class LockedChestItem extends Item {

    private final String variant;

    public LockedChestItem(String variant, Properties properties) {
        super(properties);
        this.variant = variant;
    }

    public String variant() {
        return variant;
    }

    public static ItemStack createLocked(net.neoforged.neoforge.registries.DeferredItem<LockedChestItem> item, FixedLootContents contents) {
        ItemStack stack = new ItemStack(item.get());
        stack.set(ModItems.FIXED_LOOT_CONTENTS, contents);
        stack.set(ModItems.LOCKED_CHEST_LOCKED, Boolean.TRUE);
        return stack;
    }

    public static boolean isLocked(ItemStack stack) {
        return stack.getOrDefault(ModItems.LOCKED_CHEST_LOCKED, Boolean.TRUE);
    }

    private static FixedLootContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(ModItems.FIXED_LOOT_CONTENTS, FixedLootContents.EMPTY);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        String base = capitalize(variant) + " Chest";
        if (isLocked(itemStack)) {
            return Component.literal(base + " (Locked)");
        }
        return Component.literal(base + " (" + contentsOf(itemStack).totalCount() + ")");
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** Same grid-of-icons preview as Catch Bag/Sunken Treasure, via {@link FixedLootTooltip} -- only
     * once unlocked, since a locked chest's contents are meant to stay a surprise. */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack itemStack) {
        if (isLocked(itemStack)) {
            return Optional.empty();
        }
        FixedLootContents contents = contentsOf(itemStack);
        if (contents.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FixedLootTooltip(contents.stacks()));
    }

    /**
     * The real fix for "dump via right-click on an empty inventory slot" (2026-10-03) -- that gesture
     * never reaches {@link #use}, which only fires for an in-world right-click; a GUI-slot click goes
     * through this override instead, exactly like vanilla's own {@code BundleItem}. Pop-out only --
     * never accepts insertion, and still locked out entirely while {@link #isLocked}.
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        if (clickAction != ClickAction.SECONDARY || !slot.getItem().isEmpty() || isLocked(self)) {
            return false;
        }
        FixedLootContents.RemoveResult result = contentsOf(self).removeLast();
        if (result.removed().isEmpty()) {
            return true;
        }
        self.set(ModItems.FIXED_LOOT_CONTENTS, result.contents());
        slot.set(result.removed());
        if (result.contents().isEmpty()) {
            self.shrink(1);
        }
        if (player.containerMenu != null) {
            player.containerMenu.slotsChanged(player.getInventory());
        }
        return true;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        ItemStack chest = player.getItemInHand(hand);
        if (isLocked(chest)) {
            return InteractionResult.FAIL;
        }
        FixedLootContents.RemoveResult result = contentsOf(chest).removeLast();
        if (result.removed().isEmpty()) {
            return InteractionResult.CONSUME;
        }
        chest.set(ModItems.FIXED_LOOT_CONTENTS, result.contents());
        if (!player.getInventory().add(result.removed())) {
            player.drop(result.removed(), false);
        }
        if (result.contents().isEmpty()) {
            chest.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
