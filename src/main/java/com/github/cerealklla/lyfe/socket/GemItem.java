package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * One registered {@link Item} per {@link GemType} (6 total -- {@code gem_diamond}/{@code gem_ruby}/
 * etc. in {@code registration.ModItems}), each capable of carrying any {@link GemQuality} via a
 * persisted data component rather than registering 30 separate items for the 6x5 type/quality
 * matrix (same "store the variable part as a component, not as more registered items" convention
 * {@code cook.CookingListener#bakeIcons}/Lyfe's own Quality system already use). {@code gemType} is
 * fixed per registered item (baked at construction, never stored as its own component); {@code
 * gemQuality} IS a real stored component, defaulting to {@link GemQuality#NORMAL} for a freshly
 * spawned stack with none set (shouldn't normally happen -- every real source, Extracting and
 * {@code GemDropListener}'s mob drops, always sets it explicitly).
 */
public class GemItem extends Item {

    private final GemType gemType;

    public GemItem(GemType gemType, Properties properties) {
        super(properties);
        this.gemType = gemType;
    }

    public GemType gemType() {
        return gemType;
    }

    public static ItemStack create(GemType type, GemQuality quality) {
        Item item = ModItems.gemFor(type).get();
        ItemStack stack = new ItemStack(item);
        stack.set(ModItems.GEM_QUALITY.get(), quality);
        return stack;
    }

    /** {@code null} if {@code stack} isn't a Gem item at all. */
    public static GemType gemTypeOf(ItemStack stack) {
        return stack.getItem() instanceof GemItem gemItem ? gemItem.gemType() : null;
    }

    public static GemQuality gemQualityOf(ItemStack stack) {
        return stack.getOrDefault(ModItems.GEM_QUALITY.get(), GemQuality.NORMAL);
    }

    @Override
    public Component getName(ItemStack stack) {
        GemQuality quality = gemQualityOf(stack);
        return Component.literal(quality.displayName() + " " + gemType.displayName());
    }

    /** "Pick up the gem... click on an item with an open socket" -- see {@link SocketScoopHandler}'s own doc. */
    @Override
    public boolean overrideStackedOnOther(ItemStack self, Slot slot, ClickAction clickAction, Player player) {
        return SocketScoopHandler.tryScoopInto(self, slot, clickAction, player, SocketSlot.ofGem(gemType, gemQualityOf(self)));
    }
}
