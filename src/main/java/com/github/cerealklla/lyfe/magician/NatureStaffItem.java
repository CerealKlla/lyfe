package com.github.cerealklla.lyfe.magician;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.craft.NatureStaffLadder;
import com.github.cerealklla.lyfe.registration.ModAttributes;

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
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.Level;

/**
 * Nature Staff (design doc Section 24) -- a right-click weapon that casts Spark of Life (explicit
 * user clarification: "if it's easier to do left vs right click for the spells that's fine too,
 * like with the crossbow" -- Shift+Left Click would fight vanilla's own attack-swing handling,
 * right-click is the standard Minecraft convention for an active item ability). No durability cost
 * to cast (explicit spec) -- {@link #use} never touches the stack's damage value at all; only a
 * real melee swing (vanilla's own automatic hurtAndBreak-on-hit) ever costs durability, which is
 * exactly what {@code magician.BoSkillListener} grants XP from.
 *
 * <p>Melee stats are hand-built here to exactly match vanilla's own {@code AxeItem} registration at
 * this tier's material (see {@link NatureStaffLadder}'s own doc) -- deliberately NOT using {@code
 * Item.Properties#axe(...)}, which would also attach the {@code Tool} component's axe mining-
 * efficiency/block-tag behavior (stripping logs, "mines efficiently" on axe-tagged blocks). That's
 * a tool-use stat, not a melee stat -- "similar to an Axe" in the spec means combat feel (damage,
 * speed, the shield-disable-on-hit mechanic), not that a magic staff should also function as a
 * woodcutting axe.
 */
public final class NatureStaffItem extends Item {

    private final int tier;

    public NatureStaffItem(int tier, Properties properties) {
        super(properties.component(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS, attributesFor(tier))
                .component(net.minecraft.core.component.DataComponents.WEAPON, new Weapon(2, Weapon.AXE_DISABLES_BLOCKING_FOR_SECONDS)));
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    private static ItemAttributeModifiers attributesFor(int tier) {
        return attributesFor(tier, 0.0);
    }

    /**
     * Same base modifiers, but with the Mana/Mana Regen bonus scaled by {@code qualityScore} the
     * same way {@code QualityApplier} scales Durability (a percentage, e.g. {@code 25.0} = +25%) --
     * called from {@code CraftingStructureMenu#attemptCraft} right after {@code QualityApplier.apply}
     * so a freshly-crafted Nature Staff's Mana bonus reflects the same roll its Durability already
     * did. Attack Damage/Speed are deliberately NOT scaled by Quality -- only Mana/ManaRegen are
     * named in the spec ("Crafting Quality further increase[s] the stat bonuses to Mana and Mana
     * Regen, similar to how Durability is buffed").
     */
    public static ItemAttributeModifiers attributesFor(int tier, double qualityScore) {
        Identifier maxManaId = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "nature_staff_max_mana");
        Identifier manaRegenId = Identifier.fromNamespaceAndPath(LyfeMod.MODID, "nature_staff_mana_regen");
        double scale = 1.0 + qualityScore / 100.0;
        int scaledMaxMana = (int) Math.round(NatureStaffLadder.maxManaBonus(tier) * scale);
        int scaledManaRegen = (int) Math.round(NatureStaffLadder.manaRegenBonus(tier) * scale);
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID,
                                NatureStaffLadder.attackDamageBaseline(tier) + NatureStaffLadder.material(tier).attackDamageBonus(),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, NatureStaffLadder.attackSpeedBaseline(tier), AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(ModAttributes.MAX_MANA,
                        new AttributeModifier(maxManaId, scaledMaxMana, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(ModAttributes.MANA_REGEN,
                        new AttributeModifier(manaRegenId, scaledManaRegen, AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            return InteractionResult.CONSUME;
        }
        SparkOfLifeCasting.CastResult result = SparkOfLifeCasting.attemptCast(player);
        switch (result) {
            case SUCCESS -> {
                int cooldownTicks = SpellProficiencyCurve.cooldownTicks(SparkOfLifeCasting.proficiencyLevel(player));
                // Cosmetic only -- the hotbar swipe. Casting is already fully enforced above via
                // magician.SpellCooldowns; nothing here depends on vanilla's own cooldown map.
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
