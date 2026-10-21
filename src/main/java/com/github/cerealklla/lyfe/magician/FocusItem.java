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
import net.minecraft.world.level.Level;

/**
 * Focus (design doc Section 25) -- an off-hand item with randomized passive stats and (Tier 3+,
 * a chance) a granted spell, cast by shift+right-click so a mainhand spell weapon (Nature Staff)
 * and an off-hand Focus can both be used without stealing each other's input -- see {@link
 * com.github.cerealklla.lyfe.magician.NatureStaffItem}'s own edit gating its cast to
 * {@code !isShiftKeyDown()} and returning {@code PASS} otherwise, which is what lets vanilla's own
 * interaction dispatch fall through to this item's {@link #use} at all when both are equipped.
 *
 * <p>Not damageable (no durability/repair) and not socketable -- neither was in the spec; see
 * {@code craft.CraftingStructureMenu#attemptCraft}'s own Focus branch for why the generic
 * base-durability-override step must skip it explicitly.
 */
public final class FocusItem extends Item {

    private final int tier;

    public FocusItem(int tier, Properties properties) {
        super(properties);
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
            builder.accept(Component.literal("Shift+Right-click: Cast Spark of Life").withStyle(ChatFormatting.AQUA));
        }
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
