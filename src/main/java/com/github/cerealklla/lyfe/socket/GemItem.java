package com.github.cerealklla.lyfe.socket;

import java.util.function.Consumer;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

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

    /**
     * Real report, 2026-10-18: "Gems need to have a tooltip for what they grant when inserted" --
     * same gap {@code RuneItem#appendHoverText} was already fixed for, 2026-10-17. Unlike a Rune (one
     * fixed universal stat), a Gem's actual bonus depends on which {@link EquipmentCategory} it ends
     * up socketed into (see {@link GemCatalog}'s own doc) -- shown here as one line per category,
     * each already scaled to this exact stack's current {@link GemQuality} via {@link
     * GemCatalog.StatEntry#amountFor}, so the number shown is exactly what socketing it right now
     * would actually grant in that slot type, not a generic Chipped-tier baseline.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        builder.accept(Component.literal("Socket into a Weapon, Armor, or Tool with an open socket.").withStyle(ChatFormatting.GRAY));
        GemQuality quality = gemQualityOf(stack);
        for (EquipmentCategory category : EquipmentCategory.values()) {
            GemCatalog.StatEntry entry = GemCatalog.entryFor(gemType, category);
            if (entry == null) {
                continue;
            }
            double amount = entry.amountFor(quality);
            String sign = amount >= 0 ? "+" : "";
            builder.accept(Component.literal(categoryLabel(category) + ": " + sign + amount + " " + entry.shortLabel())
                    .withStyle(ChatFormatting.BLUE));
        }
    }

    private static String categoryLabel(EquipmentCategory category) {
        return switch (category) {
            case WEAPON -> "Weapon";
            case ARMOR -> "Armor";
            case TOOL -> "Tool";
        };
    }
}
