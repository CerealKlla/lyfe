package com.github.cerealklla.lyfe.socket;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * What each (GemType, EquipmentCategory) pair boosts, and by how much per {@link GemQuality}
 * (explicit user spec: "Each type has a stat that it boosts, depending on if it's socketed into a
 * Weapon, Tool, or Armor... Quality influences the # the stat gains," with the one worked example
 * given directly -- Ruby: Armor = Max Hearts, Weapon = Damage, Chipped +0.5 -&gt; Perfect +2.5).
 *
 * <p>Every stat here maps onto a real vanilla {@link Attribute} (confirmed against the decompiled
 * {@code Attributes.java} before picking any of them) rather than a bespoke percentage mechanic --
 * deliberately, so the whole gem system needs exactly one application mechanism ({@link
 * SocketApplier}) instead of 18 separate custom event hooks. First-pass values, flagged tunable like
 * every other numeric constant in this mod -- Ruby's own two entries are the one given anchor
 * (0.5 heart = 1.0 {@code MAX_HEALTH} point per step, since that attribute is in half-heart units);
 * every other gem/category uses the same "5 linear steps, Chipped = 1 step, Perfect = 5 steps" shape,
 * scaled to whatever magnitude suits that attribute's own real value range.
 *
 * <p>Thematic pairing (not mechanically load-bearing, just naming sense): Ruby = vitality, Diamond =
 * hardness/precision, Topaz = haste, Amethyst = force/control, Cobalt = resilience, Jade = fortune.
 * A few assignments (e.g. Cobalt on a weapon granting Max Absorption) are a looser flavor fit than
 * others purely because the usable vanilla-attribute pool is finite -- accepted, not a bug.
 */
public final class GemCatalog {

    /** One stat entry: the attribute it grants, and the per-quality-step amount (Chipped = 1x, Perfect = 5x). */
    public record StatEntry(Holder<Attribute> attribute, double stepAmount, String shortLabel) {
        public double amountFor(GemQuality quality) {
            return stepAmount * (quality.index() + 1);
        }
    }

    private static final Map<GemType, Map<EquipmentCategory, StatEntry>> TABLE = new EnumMap<>(GemType.class);

    static {
        put(GemType.RUBY, EquipmentCategory.WEAPON, Attributes.ATTACK_DAMAGE, 0.5, "Attack Damage");
        put(GemType.RUBY, EquipmentCategory.ARMOR, Attributes.MAX_HEALTH, 1.0, "Max Hearts (x0.5)");
        put(GemType.RUBY, EquipmentCategory.TOOL, Attributes.OXYGEN_BONUS, 0.1, "Oxygen Bonus");

        put(GemType.DIAMOND, EquipmentCategory.WEAPON, Attributes.SWEEPING_DAMAGE_RATIO, 0.05, "Sweeping Damage");
        put(GemType.DIAMOND, EquipmentCategory.ARMOR, Attributes.ARMOR_TOUGHNESS, 0.3, "Armor Toughness");
        put(GemType.DIAMOND, EquipmentCategory.TOOL, Attributes.MINING_EFFICIENCY, 0.3, "Mining Efficiency");

        put(GemType.TOPAZ, EquipmentCategory.WEAPON, Attributes.ATTACK_SPEED, 0.04, "Attack Speed");
        put(GemType.TOPAZ, EquipmentCategory.ARMOR, Attributes.MOVEMENT_SPEED, 0.004, "Movement Speed");
        put(GemType.TOPAZ, EquipmentCategory.TOOL, Attributes.SUBMERGED_MINING_SPEED, 0.3, "Underwater Mining Speed");

        put(GemType.AMETHYST, EquipmentCategory.WEAPON, Attributes.ATTACK_KNOCKBACK, 0.1, "Attack Knockback");
        put(GemType.AMETHYST, EquipmentCategory.ARMOR, Attributes.KNOCKBACK_RESISTANCE, 0.02, "Knockback Resistance");
        put(GemType.AMETHYST, EquipmentCategory.TOOL, Attributes.STEP_HEIGHT, 0.1, "Step Height");

        put(GemType.COBALT, EquipmentCategory.WEAPON, Attributes.FALL_DAMAGE_MULTIPLIER, -0.05, "Fall Damage Reduction");
        put(GemType.COBALT, EquipmentCategory.ARMOR, Attributes.MAX_ABSORPTION, 1.0, "Max Absorption");
        put(GemType.COBALT, EquipmentCategory.TOOL, Attributes.SAFE_FALL_DISTANCE, 1.0, "Safe Fall Distance");

        put(GemType.JADE, EquipmentCategory.WEAPON, Attributes.LUCK, 0.5, "Luck");
        put(GemType.JADE, EquipmentCategory.ARMOR, Attributes.JUMP_STRENGTH, 0.02, "Jump Strength");
        put(GemType.JADE, EquipmentCategory.TOOL, Attributes.LUCK, 0.5, "Luck");
    }

    private static void put(GemType type, EquipmentCategory category, Holder<Attribute> attribute, double stepAmount, String label) {
        TABLE.computeIfAbsent(type, t -> new EnumMap<>(EquipmentCategory.class))
                .put(category, new StatEntry(attribute, stepAmount, label));
    }

    private GemCatalog() {
    }

    /** {@code null} if {@code category} is somehow not one of the 3 (shouldn't happen -- every {@link GemType} covers all 3). */
    public static StatEntry entryFor(GemType type, EquipmentCategory category) {
        return TABLE.get(type).get(category);
    }
}
