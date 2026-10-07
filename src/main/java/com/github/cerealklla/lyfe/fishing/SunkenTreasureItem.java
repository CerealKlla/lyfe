package com.github.cerealklla.lyfe.fishing;

import java.util.Optional;

import com.github.cerealklla.lyfe.LyfeMod;
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
 * A rare fishing find (design doc Section E) -- behaves exactly like a Bundle once obtained: pop
 * one item at a time via right-click, never accepts insertion, and self-destructs (shrinks itself
 * to nothing) the instant its contents become empty. Its payload is baked in once at creation
 * ({@link FixedLootContents#rollSunkenTreasure}) by {@code FishermanListener}.
 */
public class SunkenTreasureItem extends Item {

    public SunkenTreasureItem(Properties properties) {
        super(properties);
    }

    public static ItemStack createWith(FixedLootContents contents) {
        ItemStack stack = new ItemStack(ModItems.SUNKEN_TREASURE.get());
        stack.set(ModItems.FIXED_LOOT_CONTENTS, contents);
        return stack;
    }

    private static FixedLootContents contentsOf(ItemStack stack) {
        return stack.getOrDefault(ModItems.FIXED_LOOT_CONTENTS, FixedLootContents.EMPTY);
    }

    @Override
    public Component getName(ItemStack itemStack) {
        return Component.literal("Sunken Treasure Bag (" + contentsOf(itemStack).totalCount() + ")");
    }

    /** Same grid-of-icons preview as Catch Bag (2026-10-03 playtest request), via {@link FixedLootTooltip}. */
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack itemStack) {
        FixedLootContents contents = contentsOf(itemStack);
        if (contents.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new FixedLootTooltip(contents.stacks()));
    }

    /**
     * The real fix for "dump via right-click on an empty inventory slot" (2026-10-03) -- that gesture
     * never reaches {@link #use}, which only fires for an in-world right-click; a GUI-slot click goes
     * through this override instead, exactly like vanilla's own {@code BundleItem}. Pop-out only
     * (right-click an empty slot while this bag is on the cursor) -- never accepts insertion.
     */
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        if (clickAction != ClickAction.SECONDARY || !slot.getItem().isEmpty()) {
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
        ItemStack bag = player.getItemInHand(hand);
        int before = contentsOf(bag).stacks().size();
        FixedLootContents.RemoveResult result = contentsOf(bag).removeLast();
        LyfeMod.LOGGER.info("[SunkenTreasure] use(): hand={} stacksBefore={} removed={}",
                hand, before, result.removed().isEmpty() ? "EMPTY" : result.removed());
        if (result.removed().isEmpty()) {
            return InteractionResult.CONSUME;
        }
        bag.set(ModItems.FIXED_LOOT_CONTENTS, result.contents());
        if (!player.getInventory().add(result.removed())) {
            player.drop(result.removed(), false);
        }
        if (result.contents().isEmpty()) {
            bag.shrink(1);
        }
        return InteractionResult.SUCCESS_SERVER;
    }
}
