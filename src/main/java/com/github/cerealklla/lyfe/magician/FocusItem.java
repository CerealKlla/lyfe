package com.github.cerealklla.lyfe.magician;

import java.util.function.Consumer;

import com.github.cerealklla.lyfe.craft.FocusLadder;
import com.github.cerealklla.lyfe.registration.ModAttributes;
import com.github.cerealklla.lyfe.registration.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;

/**
 * Focus (design doc Section 25) -- an off-hand item with randomized passive stats and (Tier 3+,
 * a chance) a granted spell, cast by shift+right-click so a mainhand spell weapon (Nature Staff)
 * and an off-hand Focus can both be used without stealing each other's input -- see {@link
 * com.github.cerealklla.lyfe.magician.NatureStaffItem}'s own edit gating its cast to
 * {@code !isShiftKeyDown()} and returning {@code PASS} otherwise, which is what lets vanilla's own
 * interaction dispatch fall through to this item's {@link #use} at all when both are equipped.
 *
 * <p>Not damageable (no durability/repair) -- neither was in the spec; see {@code
 * craft.CraftingStructureMenu#attemptCraft}'s own Focus branch for why the generic
 * base-durability-override step must skip it explicitly.
 *
 * <p><b>Socketable, and treated as a Weapon for Gem/Rune purposes</b> (explicit user request,
 * 2026-10-21, reversing the original "not socketable" scope cut) -- {@code
 * socket.EquipmentCategory#of} classifies purely off a real {@code DataComponents.WEAPON}/{@code
 * EQUIPPABLE}/{@code TOOL} component, so a zero-value {@code Weapon} component here (this item has
 * no real melee role; {@code itemDamagePerAttack=0} is purely a classification marker, never
 * actually applied since Focus has no durability to lose) is enough to make every socketing/
 * extracting/pre-socketed-loot code path recognize it with zero further changes -- none of that
 * machinery is durability-gated.
 */
public final class FocusItem extends Item {

    private final int tier;

    public FocusItem(int tier, Properties properties) {
        super(properties.component(net.minecraft.core.component.DataComponents.WEAPON, new Weapon(0)));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public static FocusData dataOf(ItemStack stack) {
        return stack.get(ModItems.FOCUS_DATA.get());
    }

    /** Built from the real rolled stat list, all at {@link EquipmentSlotGroup#OFFHAND} -- same shape as {@code NatureStaffItem#attributesFor} but data-driven instead of a fixed per-tier formula. */
    public static ItemAttributeModifiers attributesFor(FocusData data) {
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        for (FocusData.RolledStat stat : data.stats()) {
            Identifier modifierId = Identifier.fromNamespaceAndPath("lyfe", "focus_" + stat.type().name().toLowerCase(java.util.Locale.ROOT));
            switch (stat.type()) {
                case MAX_MANA -> builder.add(ModAttributes.MAX_MANA,
                        new AttributeModifier(modifierId, stat.value(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
                case MANA_REGEN -> builder.add(ModAttributes.MANA_REGEN,
                        new AttributeModifier(modifierId, stat.value(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
                case FASTER_CASTING_RATE -> builder.add(ModAttributes.CASTING_SPEED,
                        new AttributeModifier(modifierId, -stat.value() / 100.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), EquipmentSlotGroup.OFFHAND);
                case MAX_HEALTH -> builder.add(Attributes.MAX_HEALTH,
                        new AttributeModifier(modifierId, stat.value() * 2.0, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
                case MAGIC_FIND -> builder.add(ModAttributes.MAGIC_FIND,
                        new AttributeModifier(modifierId, stat.value(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
                case GOLD_FIND -> builder.add(ModAttributes.GOLD_FIND,
                        new AttributeModifier(modifierId, stat.value(), AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.OFFHAND);
            }
        }
        return builder.build();
    }

    @Override
    public Component getName(ItemStack stack) {
        FocusData data = dataOf(stack);
        String tierName = FocusLadder.tierDisplayName(tier);
        if (data == null) {
            return Component.literal(tierName + " Focus");
        }
        return Component.literal(tierName + " Focus (" + data.variant().displayName() + ")");
    }

    /**
     * Real report, 2026-10-30: socketing Runes/Gems into a Focus never showed up in its tooltip at
     * all, neither as individual stat lines nor (when a full Runeword sequence matched) the Runeword
     * name. Root cause: {@code craft.CraftingStructureMenu#attemptCraft} hides vanilla's own
     * automatic {@code DataComponents.ATTRIBUTE_MODIFIERS} tooltip section for every crafted Focus
     * (2026-10-21, to stop the base roll from double-printing once in vanilla's generic format and
     * again in this method's own friendlier one) -- but that hid socket-derived modifiers too, since
     * they live on the exact same component, and this method never read {@link
     * com.github.cerealklla.lyfe.socket.SocketData} at all to begin with. Now manually renders each
     * filled socket's own {@link com.github.cerealklla.lyfe.socket.GemCatalog}/{@link
     * com.github.cerealklla.lyfe.socket.RuneCatalog} stat line -- skipped whenever a Runeword is
     * active ({@code socket.SocketApplier} adds no individual socket modifiers in that case, same
     * "Runewords override entirely" rule every other socketable item follows). The active Runeword's
     * own name/description line is unaffected by any of this -- {@code LyfeModClient#onSocketTooltip}
     * is a separate, item-agnostic {@code ItemTooltipEvent} hook that already runs for every item
     * regardless of its own {@code appendHoverText}, reading {@code ModItems#ACTIVE_RUNEWORD_NAME}
     * directly; if it's still not showing after this fix, the inserted sequence genuinely isn't the
     * server's real rolled sequence for any Weapon Runeword (order-sensitive, server-randomized), not
     * a display bug.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag tooltipFlag) {
        FocusData data = dataOf(stack);
        if (data == null) {
            return;
        }
        for (FocusData.RolledStat stat : data.stats()) {
            builder.accept(Component.literal(stat.type().formatted(stat.value())).withStyle(ChatFormatting.BLUE));
        }
        if (data.grantedSpellId().isPresent()) {
            // Same two-line style as NatureStaffItem's own tooltip (explicit user request, 2026-10-21:
            // "Focus' description for the spell should be the same style as the Nature Staff's").
            builder.accept(Component.literal("Shift+Right-click: Cast Spark of Life").withStyle(ChatFormatting.AQUA));
            builder.accept(Component.literal("Fires a bolt of light -- heals an ally or damages an enemy on hit.").withStyle(ChatFormatting.GRAY));
        }
        if (!stack.has(ModItems.ACTIVE_RUNEWORD_NAME.get())) {
            com.github.cerealklla.lyfe.socket.SocketData socketData =
                    stack.getOrDefault(ModItems.SOCKET_DATA.get(), com.github.cerealklla.lyfe.socket.SocketData.EMPTY);
            for (com.github.cerealklla.lyfe.socket.SocketSlot slot : socketData.sockets()) {
                switch (slot.kind()) {
                    case GEM -> {
                        com.github.cerealklla.lyfe.socket.GemCatalog.StatEntry entry =
                                com.github.cerealklla.lyfe.socket.GemCatalog.entryFor(slot.gemType().orElseThrow(),
                                        com.github.cerealklla.lyfe.socket.EquipmentCategory.WEAPON);
                        double amount = entry.amountFor(slot.gemQuality().orElseThrow());
                        builder.accept(socketStatLine(amount, entry.shortLabel()));
                    }
                    case RUNE -> {
                        com.github.cerealklla.lyfe.socket.RuneCatalog.RuneStat stat =
                                com.github.cerealklla.lyfe.socket.RuneCatalog.of(slot.runeId().orElseThrow());
                        builder.accept(socketStatLine(stat.amount(), stat.shortLabel()));
                    }
                    case EMPTY -> {
                        // Nothing to show.
                    }
                }
            }
        }
    }

    private static Component socketStatLine(double amount, String shortLabel) {
        String sign = amount >= 0 ? "+" : "";
        return Component.literal(sign + amount + " " + shortLabel).withStyle(ChatFormatting.BLUE);
    }

    /** Only when sneaking (explicit spec: "the spell is cast by shift+right click") -- a plain right-click does nothing, leaving the hand free for whatever else vanilla would do with it. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        FocusData data = dataOf(player.getItemInHand(hand));
        if (data == null || data.grantedSpellId().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        SparkOfLifeCasting.CastResult result = SparkOfLifeCasting.attemptCast(player);
        switch (result) {
            case SUCCESS -> {
                int cooldownTicks = SpellProficiencyCurve.cooldownTicks(SparkOfLifeCasting.proficiencyLevel(player));
                player.getCooldowns().addCooldown(player.getItemInHand(hand), cooldownTicks);
                return InteractionResult.SUCCESS;
            }
            case ON_COOLDOWN -> {
                player.sendSystemMessage(Component.literal("Spark of Life is still on cooldown."));
                return InteractionResult.FAIL;
            }
            case NOT_ENOUGH_MANA -> {
                player.sendSystemMessage(Component.literal("Not enough mana to cast Spark of Life."));
                return InteractionResult.FAIL;
            }
            default -> {
                return InteractionResult.FAIL;
            }
        }
    }
}
