package com.github.cerealklla.lyfe.socket;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The real insertion mechanic (explicit user spec, 2026-10-18 correction of the original Die-
 * Grinder-GUI "Insert" design): "you should not need a die grinder to insert a gem, just pick up the
 * gem and drop in inventory and click on an item with an open socket... treat it just like we do with
 * bundles, you are scooping up an item but can't remove them kind of thing."
 *
 * <p>Mirrors vanilla {@code BundleItem#overrideStackedOnOther} exactly (confirmed against the
 * decompiled source before building this) -- that's the real vanilla extension point for "item A,
 * held on the cursor, clicked onto a slot holding item B" that works identically in ANY open
 * container screen (the player's own inventory, a chest, a Crafting Table, this mod's own menus) with
 * zero per-screen special-casing, which is exactly the universal "pick it up and click on the target"
 * interaction asked for -- no recipe, no custom GUI, no Die Grinder needed at all.
 *
 * <p><b>Direction is the mirror image of a real Bundle</b>: a Bundle is the thing held on the cursor
 * that scoops up whatever you click on. Here, since Lyfe doesn't own every possible socketable
 * item's class (swords/pickaxes/armor are all vanilla or other mods' classes, nothing to override
 * {@code overrideStackedOnOther} on), it's the GEM/RUNE on the cursor whose class overrides it, and
 * the socketable item sits in the clicked {@code Slot} instead -- same real hook, same real one-click
 * interaction, just "gem clicks onto equipment" instead of "equipment clicks onto gem."
 *
 * <p><b>"Can't remove them"</b> (explicit user spec) -- unlike a real Bundle, which also supports the
 * reverse ({@code ClickAction.SECONDARY} on an empty slot pulls the last item back out), this
 * deliberately implements ONLY the insert direction. There is no secondary-click removal at all --
 * getting a Gem/Rune back out is exclusively the Pry Bar's Extracting mechanic.
 */
final class SocketScoopHandler {

    private SocketScoopHandler() {
    }

    /** {@code self} is the Gem/Rune stack on the cursor; {@code slot} is whatever the player clicked on. Returns whether the click was handled (consumed) by this mechanic at all. */
    static boolean tryScoopInto(ItemStack self, Slot slot, ClickAction clickAction, Player player, SocketSlot toInsert) {
        if (clickAction != ClickAction.PRIMARY) {
            return false;
        }
        ItemStack target = slot.getItem();
        if (EquipmentCategory.of(target) == null) {
            return false;
        }
        SocketData data = target.getOrDefault(ModItems.SOCKET_DATA.get(), SocketData.EMPTY);
        int emptyIndex = data.firstEmptyIndex();
        if (emptyIndex < 0) {
            player.playSound(SoundEvents.BUNDLE_INSERT_FAIL, 1.0F, 1.0F);
            return true; // Handled (an open-socket item was clicked), just nothing to do -- no socket free.
        }

        target.set(ModItems.SOCKET_DATA.get(), data.withSlot(emptyIndex, toInsert));
        SocketApplier.recompute(target);
        slot.setChanged();
        self.shrink(1);
        player.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.8F + player.level().getRandom().nextFloat() * 0.4F);
        if (player.containerMenu != null) {
            player.containerMenu.slotsChanged(player.getInventory());
        }
        return true;
    }
}
