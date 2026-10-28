package com.github.cerealklla.lyfe.magician;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.item.Item;

/**
 * Real design correction, 2026-10-28: Bo is a weapon CLASSIFICATION, not a single item -- explicit
 * user spec: "The Bo Staff is not a specific weapon, it's a classification of weapons. Nature Staff
 * is a 'Bo Staff', in the future an 'Ember Staff', or a 'Charged Staff' would also be Bo Staff type
 * weapons. It's just any kind of blunt staff. Meleeing with any of these would grant Bo Staff xp."
 * {@link BoSkillListener} used to hardcode a check against {@code NatureStaffLadder} directly, which
 * would have needed editing again for every future staff. Same "open, trust-based registry" shape
 * this suite already uses for {@code ZoneTypeRegistry}/{@code MenuContributor}/Cartographyr's
 * {@code Layer} -- any current or future staff item just calls {@link #register} once at startup
 * (see {@code LyfeMod}'s commonSetup for Nature Staff's own registration).
 *
 * <p>Each registered item carries its own tier (on the same universal 0-6 Wood-Netherite scale
 * {@link EquipmentTierLadder}/{@link ToolTierUnlocks} already use -- Nature Staff's own 1-5 Stone-
 * Diamond ladder already maps onto it 1:1, no conversion needed), so {@link
 * ProficiencyDurabilityListener} can apply the real melee-proficiency-penalty mechanic generically
 * to ANY Bo-Staff-classified item without needing to know which specific ladder (NatureStaffLadder
 * today, a future one tomorrow) actually owns it -- same follow-up request, same day: "the Bo Staff
 * skill should behave the same as other weapons... now that Magician exists" the original "don't
 * penalize spellcasting" carve-out is no longer needed (Magician now owns spell proficiency
 * separately) -- melee combat gets the same exaggerated-durability-loss penalty Swordsman/Axeman/
 * Pikeman already have.
 */
public final class BoStaffClassification {

    private static final Map<Item, Integer> BO_STAFF_TIERS = new HashMap<>();

    private BoStaffClassification() {
    }

    public static void register(final Item item, final int tier) {
        BO_STAFF_TIERS.put(item, tier);
    }

    public static boolean isBoStaff(final Item item) {
        return BO_STAFF_TIERS.containsKey(item);
    }

    /** -1 if {@code item} isn't a registered Bo Staff-classified item. */
    public static int tierOf(final Item item) {
        return BO_STAFF_TIERS.getOrDefault(item, -1);
    }
}
