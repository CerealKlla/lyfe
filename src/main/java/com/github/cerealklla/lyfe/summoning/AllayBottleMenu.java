package com.github.cerealklla.lyfe.summoning;

import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Equip/Gather GUI (design doc Section 27, Shift+Right-Click on an Allay Bottle) -- opened via
 * {@code AllayBottleItem#use}, same "opened straight from a held item's own use(), no block/extra-
 * data at all" shape as Lyfe's existing {@code socket.SocketingMenu}/{@code socket.ExtractingMenu}.
 * Resolves the bottle being configured by re-checking the player's own hands directly (mainhand then
 * offhand) rather than threading an explicit reference through the client-side menu-type factory --
 * simpler, and always correct since this menu can only ever be opened by {@code AllayBottleItem
 * #use} itself, always from whichever hand is currently holding it.
 */
public final class AllayBottleMenu extends AbstractContainerMenu {

    // Real report, 2026-10-24: "can you fix the overlaps" -- the Equip/Gather slot labels (drawn at
    // slot_y - 10 = 36) sat squarely inside the Rename button's own y-range (20-40), so "Gather"
    // visibly ran behind it. Slots moved down to make room for both a clean label row AND the new
    // stats block below the name row (see client.AllayBottleScreen#extractLabels).
    public static final int EQUIP_SLOT_X = 62;
    public static final int EQUIP_SLOT_Y = 94;
    public static final int GATHER_SLOT_X = 98;
    public static final int GATHER_SLOT_Y = 94;
    public static final int PLAYER_INV_Y = 120;

    private final Container container = new SimpleContainer(2);
    private final Player owner;
    private final String currentName;
    private final Optional<UUID> summonedAllayId;

    public AllayBottleMenu(MenuType<?> type, int containerId, Inventory inventory) {
        super(type, containerId);
        this.owner = inventory.player;
        ItemStack bottle = resolveBottle(owner);
        AllayBottleData data = bottle.isEmpty() ? null : bottle.get(ModItems.ALLAY_BOTTLE_DATA.get());
        if (data != null) {
            container.setItem(0, data.equipItem().copy());
            container.setItem(1, data.gatherItem().copy());
            this.currentName = data.displayName();
            this.summonedAllayId = data.summonedAllayId();
        } else {
            this.currentName = "Allay";
            this.summonedAllayId = Optional.empty();
        }
        layoutSlots(inventory);
    }

    public String currentName() {
        return currentName;
    }

    /**
     * The currently-summoned Allay's id, if one is out right now -- resolved identically on both
     * sides from the held bottle's own (already-synced-as-a-normal-item-component) data, same "no
     * new networking needed" shape {@link #currentName} already uses. {@code client.AllayBottleScreen}
     * reads the live entity straight off the client's own level with this (2026-10-24, explicit user
     * request: "show the Allay's stats in here").
     */
    public Optional<UUID> summonedAllayId() {
        return summonedAllayId;
    }

    private void layoutSlots(Inventory inventory) {
        addSlot(new NoBottleSlot(container, 0, EQUIP_SLOT_X, EQUIP_SLOT_Y));
        addSlot(new NoBottleSlot(container, 1, GATHER_SLOT_X, GATHER_SLOT_Y));
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    private static ItemStack resolveBottle(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof AllayBottleItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof AllayBottleItem) {
            return off;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        ItemStack clicked = ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            clicked = stack.copy();
            if (slotIndex < 2) {
                if (!moveItemStackTo(stack, 2, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, 2, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return clicked;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        ItemStack bottle = resolveBottle(owner);
        if (!bottle.isEmpty() && bottle.get(ModItems.ALLAY_BOTTLE_DATA.get()) instanceof AllayBottleData data) {
            ItemStack equip = container.getItem(0);
            ItemStack gather = container.getItem(1);
            AllayBottleData updated = data.withEquipItem(equip).withGatherItem(gather);
            bottle.set(ModItems.ALLAY_BOTTLE_DATA.get(), updated);

            if (updated.summonedAllayId().isPresent() && player.level() instanceof ServerLevel level) {
                Entity summoned = level.getEntity(updated.summonedAllayId().get());
                if (summoned instanceof Allay allay && allay.isAlive()) {
                    AllayEquipment.applyEquipAndGather(allay, equip, gather, owner.getUUID());
                    AllayGoalInjectionListener.reassessSpellGoal(allay);
                }
            }
            // Already preserved into the bottle's own data above -- clear the scratch container
            // before the usual clearContainer() below, or these would be duplicated back into the
            // player's inventory as loose items on top of what's now saved on the bottle.
            container.setItem(0, ItemStack.EMPTY);
            container.setItem(1, ItemStack.EMPTY);
        }
        clearContainer(player, container);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /**
     * Real dupe report, 2026-10-24: "I somehow duped an item by moving it around on the Allay and
     * closing/reopening it's menu." Root cause confirmed, not guessed -- Equip/Gather were plain,
     * unrestricted {@code Slot}s, so vanilla's own hotbar-swap hotkey (number key while hovering a
     * slot) could swap the bottle item itself -- currently held in hand, so already visible as one
     * of this very menu's own hotbar slots -- directly into its own Equip/Gather slot. Once that
     * happens, {@link #removed}'s own {@code resolveBottle(owner)} can no longer find the bottle in
     * either hand (it's sitting in the scratch container instead), so the whole write-back is
     * silently skipped -- including clearing the scratch slots -- and {@code clearContainer} just
     * hands the bottle straight back unmodified. Whatever was previously in that slot (now sitting
     * loose in the player's real hotbar, having been swapped out to make room) stays permanently
     * referenced in the bottle's own never-updated stored data too -- the same item now exists both
     * as a real possession and as the bottle's own "saved" copy, a real duplication once the bottle
     * is reopened and that stale copy is read out again. Blocking the bottle itself here closes the
     * hole at its actual source (the swap can never initiate at all, since vanilla's own generic
     * SWAP click handling already checks {@code mayPlace} before allowing it) rather than trying to
     * patch every possible downstream path that could exploit it.
     */
    private static final class NoBottleSlot extends Slot {
        NoBottleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !(stack.getItem() instanceof AllayBottleItem);
        }
    }
}
