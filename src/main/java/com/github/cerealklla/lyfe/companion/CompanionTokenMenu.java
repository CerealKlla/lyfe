package com.github.cerealklla.lyfe.companion;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The Companion Token's own Equip GUI, Shift+Right-click -- real user request, 2026-10-31 ("why
 * does shift + right click not open the companions inventory GUI?"). The first pass deliberately
 * left this out on the assumption a Tavern Companion's gear needed no separate configuration (it's
 * already baked into {@link CompanionTokenData} the moment the NPC is hired) -- that assumption was
 * wrong; the player still wants to be able to change it afterward, same as Allay in a Bottle's own
 * Equip/Gather GUI. Same "opened straight from a held item's own use(), no block/extra-data at all"
 * shape as {@code summoning.AllayBottleMenu}, adapted to 6 real equipment slots (Head/Chest/Legs/
 * Feet/Mainhand/Offhand) instead of a flat equip/gather pair, since {@link CompanionTokenData}
 * already stores a real per-slot loadout rather than two opaque items.
 */
public final class CompanionTokenMenu extends AbstractContainerMenu {

    /** Visual/container-index order -- independent of {@code EquipmentSlot}'s own ordinal order. */
    public static final EquipmentSlot[] SLOT_ORDER = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
    };

    // Real report, 2026-11-01: "This UI is overlapping everywhere" -- the original layout copied
    // AllayBottleMenu's *pre-2026-10-24* constants (before that menu's own overlap fix moved its
    // slot row down to make room for a name row + stats block above it), so the title/name row,
    // stats block, and slot-label row were all crammed into the same ~30px of vertical space here.
    // Six slot labels ("Head"/"Chest"/"Legs"/"Feet"/"Main"/"Off") also needed wider spacing than two
    // ("Equip"/"Gather") ever did -- 20px apart let "Chest" (~30px wide) bleed into its neighbors.
    // Widened the whole panel and pushed the slot row down past a new rename row (see
    // client.CompanionTokenScreen) and the existing stats block, mirroring AllayBottleMenu's own
    // fixed layout shape.
    public static final int SLOT_X_START = 12;
    public static final int SLOT_Y = 86;
    public static final int SLOT_SPACING = 30;
    public static final int PLAYER_INV_Y = 120;

    private final Container container = new SimpleContainer(SLOT_ORDER.length);
    private final Player owner;
    private final String currentName;
    private final Optional<UUID> summonedCompanionId;

    public CompanionTokenMenu(MenuType<?> type, int containerId, Inventory inventory) {
        super(type, containerId);
        this.owner = inventory.player;
        ItemStack token = resolveToken(owner);
        CompanionTokenData data = token.isEmpty() ? null : token.get(ModItems.COMPANION_TOKEN_DATA.get());
        if (data != null) {
            for (CompanionTokenData.EquipmentEntry entry : data.equipment()) {
                int index = indexOf(entry.slot());
                if (index >= 0) {
                    container.setItem(index, entry.stack().copy());
                }
            }
            this.currentName = data.displayName();
            this.summonedCompanionId = data.summonedCompanionId();
        } else {
            this.currentName = "Companion";
            this.summonedCompanionId = Optional.empty();
        }
        layoutSlots(inventory);
    }

    public String currentName() {
        return currentName;
    }

    public Optional<UUID> summonedCompanionId() {
        return summonedCompanionId;
    }

    private static int indexOf(EquipmentSlot slot) {
        for (int i = 0; i < SLOT_ORDER.length; i++) {
            if (SLOT_ORDER[i] == slot) {
                return i;
            }
        }
        return -1;
    }

    private void layoutSlots(Inventory inventory) {
        for (int i = 0; i < SLOT_ORDER.length; i++) {
            addSlot(new NoTokenSlot(container, i, SLOT_X_START + i * SLOT_SPACING, SLOT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, PLAYER_INV_Y + 58));
        }
    }

    private static ItemStack resolveToken(Player player) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof CompanionTokenItem) {
            return main;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof CompanionTokenItem) {
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
            if (slotIndex < SLOT_ORDER.length) {
                if (!moveItemStackTo(stack, SLOT_ORDER.length, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, SLOT_ORDER.length, false)) {
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
        ItemStack token = resolveToken(owner);
        if (!token.isEmpty() && token.get(ModItems.COMPANION_TOKEN_DATA.get()) instanceof CompanionTokenData data) {
            List<CompanionTokenData.EquipmentEntry> newEquipment = new ArrayList<>();
            for (int i = 0; i < SLOT_ORDER.length; i++) {
                ItemStack stack = container.getItem(i);
                if (!stack.isEmpty()) {
                    newEquipment.add(new CompanionTokenData.EquipmentEntry(SLOT_ORDER[i], stack.copy()));
                }
            }
            CompanionTokenData updated = data.withEquipment(newEquipment);
            token.set(ModItems.COMPANION_TOKEN_DATA.get(), updated);

            if (updated.summonedCompanionId().isPresent() && player.level() instanceof ServerLevel level) {
                Entity summoned = level.getEntity(updated.summonedCompanionId().get());
                if (summoned instanceof LivingEntity companion && companion.isAlive()) {
                    for (int i = 0; i < SLOT_ORDER.length; i++) {
                        companion.setItemSlot(SLOT_ORDER[i], container.getItem(i).copy());
                    }
                }
            }
            // Already preserved into the token's own data above -- clear the scratch container
            // before clearContainer() below, same dupe-prevention shape as AllayBottleMenu#removed.
            for (int i = 0; i < SLOT_ORDER.length; i++) {
                container.setItem(i, ItemStack.EMPTY);
            }
        }
        clearContainer(player, container);
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Same dupe-prevention shape as {@code summoning.AllayBottleMenu}'s own {@code NoBottleSlot} -- see its own doc. */
    private static final class NoTokenSlot extends Slot {
        NoTokenSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !(stack.getItem() instanceof CompanionTokenItem);
        }
    }
}
