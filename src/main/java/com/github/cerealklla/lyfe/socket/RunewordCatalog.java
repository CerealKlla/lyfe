package com.github.cerealklla.lyfe.socket;

import java.util.List;

import com.github.cerealklla.lyfe.socket.RunewordEffect.Type;

/**
 * The 60 fixed Runeword definitions (explicit user spec: "20 different Runewords for Weapons, Tools,
 * and Armor each... at least 8 specialized for each"). Names/effects are authored once and never
 * change; which real {@link RuneId} sequence each one maps to IS randomized per server (see {@link
 * ServerRunewordStore}). {@code runeCount} (2-5) correlates to strength -- the lower bracket (2) is
 * common/cheap, the top bracket (5) is rare/powerful, per the user's own explicit rule.
 *
 * <p>"Life Tap" (the user's own worked example -- 5% chance on hit to heal allies in a 15-foot/~5-
 * block radius for 2 hearts) is included here verbatim as entry 1.
 *
 * <p>Every entry reuses one of {@link RunewordEffect.Type}'s ~18 generic primitives (see that
 * class's own doc for why), varied in chance/magnitude/radius/duration AND runeCount bracket so no
 * two entries read as a plain %-only reskin of each other, per the user's explicit instruction to
 * avoid that.
 */
public final class RunewordCatalog {

    public static final List<RunewordDefinition> WEAPON = List.of(
            new RunewordDefinition("Life Tap", EquipmentCategory.WEAPON, 2,
                    new RunewordEffect(Type.ON_HIT_HEAL_PULSE_AOE, 0.05, 4.0, 5, 0, 0),
                    "On hit: 5% chance to heal every ally within 5 blocks of the target for 2 hearts."),
            new RunewordDefinition("Quickstrike", EquipmentCategory.WEAPON, 2,
                    new RunewordEffect(Type.ON_HIT_SPEED_BURST, 0.10, 1, 0, 60, 0),
                    "On hit: 10% chance for Speed I for 3 seconds."),
            new RunewordDefinition("Emberkiss", EquipmentCategory.WEAPON, 2,
                    new RunewordEffect(Type.ON_HIT_IGNITE, 0.15, 0, 0, 60, 0),
                    "On hit: 15% chance to ignite the target for 3 seconds."),
            new RunewordDefinition("Coinrend", EquipmentCategory.WEAPON, 2,
                    new RunewordEffect(Type.ON_KILL_GOLD_BURST, 0.25, 3, 0, 0, 0),
                    "On kill: 25% chance for 3 bonus Gold Nuggets."),
            new RunewordDefinition("Huntmark", EquipmentCategory.WEAPON, 2,
                    new RunewordEffect(Type.ON_KILL_XP_BURST, 0.30, 10, 0, 0, 0),
                    "On kill: 30% chance for a burst of bonus XP."),
            new RunewordDefinition("Frostbite", EquipmentCategory.WEAPON, 2,
                    new RunewordEffect(Type.ON_HIT_SLOW_TARGET, 0.10, 1, 0, 40, 0),
                    "On hit: 10% chance to apply Slowness I for 2 seconds."),
            new RunewordDefinition("Bloodletter", EquipmentCategory.WEAPON, 3,
                    new RunewordEffect(Type.ON_HIT_LIFESTEAL_BURST, 0.15, 0.15, 0, 0, 0),
                    "On hit: 15% chance to heal yourself for 15% of the damage dealt."),
            new RunewordDefinition("Searing Edge", EquipmentCategory.WEAPON, 3,
                    new RunewordEffect(Type.ON_HIT_DAMAGE_PROC, 0.12, 3.0, 0, 0, 0),
                    "On hit: 12% chance for 3 bonus true damage."),
            new RunewordDefinition("Vanguard's Pulse", EquipmentCategory.WEAPON, 3,
                    new RunewordEffect(Type.ON_HIT_HEAL_PULSE_AOE, 0.08, 6.0, 6, 0, 0),
                    "On hit: 8% chance to heal every ally within 6 blocks for 3 hearts."),
            new RunewordDefinition("Reaper's Toll", EquipmentCategory.WEAPON, 3,
                    new RunewordEffect(Type.ON_KILL_EXPLOSION, 0.20, 4.0, 4, 0, 0),
                    "On kill: 20% chance to burst 4 damage to nearby hostiles within 4 blocks."),
            new RunewordDefinition("Windrunner", EquipmentCategory.WEAPON, 3,
                    new RunewordEffect(Type.ON_HIT_SPEED_BURST, 0.20, 2, 0, 100, 0),
                    "On hit: 20% chance for Speed II for 5 seconds."),
            new RunewordDefinition("Cinderfall", EquipmentCategory.WEAPON, 3,
                    new RunewordEffect(Type.ON_HIT_IGNITE, 0.25, 0, 0, 100, 0),
                    "On hit: 25% chance to ignite the target for 5 seconds."),
            new RunewordDefinition("Soulrend", EquipmentCategory.WEAPON, 4,
                    new RunewordEffect(Type.ON_HIT_LIFESTEAL_BURST, 0.25, 0.25, 0, 0, 0),
                    "On hit: 25% chance to heal yourself for 25% of the damage dealt."),
            new RunewordDefinition("Thunderstrike", EquipmentCategory.WEAPON, 4,
                    new RunewordEffect(Type.ON_HIT_DAMAGE_PROC, 0.18, 6.0, 0, 0, 0),
                    "On hit: 18% chance for 6 bonus true damage."),
            new RunewordDefinition("Glacial Touch", EquipmentCategory.WEAPON, 4,
                    new RunewordEffect(Type.ON_HIT_SLOW_TARGET, 0.20, 2, 0, 80, 0),
                    "On hit: 20% chance to apply Slowness II for 4 seconds."),
            new RunewordDefinition("Headsman's Due", EquipmentCategory.WEAPON, 4,
                    new RunewordEffect(Type.ON_KILL_EXPLOSION, 0.35, 8.0, 5, 0, 0),
                    "On kill: 35% chance to burst 8 damage to nearby hostiles within 5 blocks."),
            new RunewordDefinition("Fortune's Edge", EquipmentCategory.WEAPON, 4,
                    new RunewordEffect(Type.ON_KILL_GOLD_BURST, 0.50, 8, 0, 0, 0),
                    "On kill: 50% chance for 8 bonus Gold Nuggets."),
            new RunewordDefinition("Eternal Thirst", EquipmentCategory.WEAPON, 5,
                    new RunewordEffect(Type.ON_HIT_LIFESTEAL_BURST, 0.35, 0.40, 0, 0, 0),
                    "On hit: 35% chance to heal yourself for 40% of the damage dealt."),
            new RunewordDefinition("Cataclysm", EquipmentCategory.WEAPON, 5,
                    new RunewordEffect(Type.ON_HIT_DAMAGE_PROC, 0.25, 10.0, 0, 0, 0),
                    "On hit: 25% chance for 10 bonus true damage."),
            new RunewordDefinition("Worldbreaker", EquipmentCategory.WEAPON, 5,
                    new RunewordEffect(Type.ON_KILL_EXPLOSION, 0.50, 14.0, 7, 0, 0),
                    "On kill: 50% chance to burst 14 damage to nearby hostiles within 7 blocks.")
    );

    public static final List<RunewordDefinition> TOOL = List.of(
            new RunewordDefinition("Lucky Strike", EquipmentCategory.TOOL, 2,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_BONUS_YIELD, 0.10, 0, 0, 0, 0),
                    "On breaking a block: 10% chance to double the drops."),
            new RunewordDefinition("Swift Pick", EquipmentCategory.TOOL, 2,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_HASTE_BURST, 0.15, 1, 0, 60, 0),
                    "On breaking a block: 15% chance for Haste I for 3 seconds."),
            new RunewordDefinition("Scholar's Edge", EquipmentCategory.TOOL, 2,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_XP_BURST, 0.20, 5, 0, 0, 0),
                    "On breaking a block: 20% chance for a small bonus XP burst."),
            new RunewordDefinition("Coinvein", EquipmentCategory.TOOL, 2,
                    new RunewordEffect(Type.ON_KILL_GOLD_BURST, 0.20, 3, 0, 0, 0),
                    "On kill: 20% chance for 3 bonus Gold Nuggets."),
            new RunewordDefinition("Steady Grip", EquipmentCategory.TOOL, 2,
                    new RunewordEffect(Type.ON_HIT_SLOW_TARGET, 0.10, 1, 0, 40, 0),
                    "On hit: 10% chance to apply Slowness I for 2 seconds."),
            new RunewordDefinition("Warmth", EquipmentCategory.TOOL, 2,
                    new RunewordEffect(Type.ON_HIT_IGNITE, 0.10, 0, 0, 60, 0),
                    "On hit: 10% chance to ignite the target for 3 seconds."),
            new RunewordDefinition("Prospector's Boon", EquipmentCategory.TOOL, 3,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_BONUS_YIELD, 0.20, 0, 0, 0, 0),
                    "On breaking a block: 20% chance to double the drops."),
            new RunewordDefinition("Miner's Fury", EquipmentCategory.TOOL, 3,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_HASTE_BURST, 0.25, 2, 0, 100, 0),
                    "On breaking a block: 25% chance for Haste II for 5 seconds."),
            new RunewordDefinition("Deep Study", EquipmentCategory.TOOL, 3,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_XP_BURST, 0.30, 10, 0, 0, 0),
                    "On breaking a block: 30% chance for a bonus XP burst."),
            new RunewordDefinition("Harvest Moon", EquipmentCategory.TOOL, 3,
                    new RunewordEffect(Type.ON_KILL_GOLD_BURST, 0.30, 5, 0, 0, 0),
                    "On kill: 30% chance for 5 bonus Gold Nuggets."),
            new RunewordDefinition("Driving Blow", EquipmentCategory.TOOL, 3,
                    new RunewordEffect(Type.ON_HIT_DAMAGE_PROC, 0.12, 3.0, 0, 0, 0),
                    "On hit: 12% chance for 3 bonus true damage."),
            new RunewordDefinition("Tireless Hands", EquipmentCategory.TOOL, 3,
                    new RunewordEffect(Type.ON_HIT_SPEED_BURST, 0.15, 1, 0, 80, 0),
                    "On hit: 15% chance for Speed I for 4 seconds."),
            new RunewordDefinition("Golden Vein", EquipmentCategory.TOOL, 4,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_BONUS_YIELD, 0.30, 0, 0, 0, 0),
                    "On breaking a block: 30% chance to double the drops."),
            new RunewordDefinition("Overdrive", EquipmentCategory.TOOL, 4,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_HASTE_BURST, 0.35, 2, 0, 140, 0),
                    "On breaking a block: 35% chance for Haste II for 7 seconds."),
            new RunewordDefinition("Archivist's Insight", EquipmentCategory.TOOL, 4,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_XP_BURST, 0.40, 18, 0, 0, 0),
                    "On breaking a block: 40% chance for a large bonus XP burst."),
            new RunewordDefinition("Gravedigger's Pay", EquipmentCategory.TOOL, 4,
                    new RunewordEffect(Type.ON_KILL_GOLD_BURST, 0.40, 10, 0, 0, 0),
                    "On kill: 40% chance for 10 bonus Gold Nuggets."),
            new RunewordDefinition("Rending Blow", EquipmentCategory.TOOL, 4,
                    new RunewordEffect(Type.ON_HIT_DAMAGE_PROC, 0.20, 6.0, 0, 0, 0),
                    "On hit: 20% chance for 6 bonus true damage."),
            new RunewordDefinition("Bottomless Vein", EquipmentCategory.TOOL, 5,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_BONUS_YIELD, 0.45, 0, 0, 0, 0),
                    "On breaking a block: 45% chance to double the drops."),
            new RunewordDefinition("Unbroken Momentum", EquipmentCategory.TOOL, 5,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_HASTE_BURST, 0.50, 3, 0, 200, 0),
                    "On breaking a block: 50% chance for Haste III for 10 seconds."),
            new RunewordDefinition("Grand Archive", EquipmentCategory.TOOL, 5,
                    new RunewordEffect(Type.ON_BREAK_BLOCK_XP_BURST, 0.50, 30, 0, 0, 0),
                    "On breaking a block: 50% chance for a huge bonus XP burst.")
    );

    public static final List<RunewordDefinition> ARMOR = List.of(
            new RunewordDefinition("Thornguard", EquipmentCategory.ARMOR, 2,
                    new RunewordEffect(Type.ON_DAMAGED_REFLECT, 0.15, 0.15, 0, 0, 0),
                    "On taking damage: 15% chance to reflect 15% of it back at the attacker."),
            new RunewordDefinition("Mendshield", EquipmentCategory.ARMOR, 2,
                    new RunewordEffect(Type.ON_DAMAGED_HEAL, 0.15, 1.0, 0, 0, 0),
                    "On taking damage: 15% chance to heal half a heart."),
            new RunewordDefinition("Adrenaline", EquipmentCategory.ARMOR, 2,
                    new RunewordEffect(Type.ON_DAMAGED_SPEED_BURST, 0.15, 1, 0, 60, 0),
                    "On taking damage: 15% chance for Speed I for 3 seconds."),
            new RunewordDefinition("Second Wind", EquipmentCategory.ARMOR, 2,
                    new RunewordEffect(Type.PERIODIC_SELF_REGEN, 0, 0.5, 0, 200, 0),
                    "Every 10 seconds: heal a quarter heart."),
            new RunewordDefinition("Lifelink", EquipmentCategory.ARMOR, 2,
                    new RunewordEffect(Type.ON_DAMAGED_HEAL, 0.10, 2.0, 0, 0, 0),
                    "On taking damage: 10% chance to heal a full heart."),
            new RunewordDefinition("Steelskin", EquipmentCategory.ARMOR, 2,
                    new RunewordEffect(Type.ON_DAMAGED_REFLECT, 0.10, 0.10, 0, 0, 0),
                    "On taking damage: 10% chance to reflect 10% of it back at the attacker."),
            new RunewordDefinition("Vengeance", EquipmentCategory.ARMOR, 3,
                    new RunewordEffect(Type.ON_DAMAGED_REFLECT, 0.25, 0.25, 0, 0, 0),
                    "On taking damage: 25% chance to reflect 25% of it back at the attacker."),
            new RunewordDefinition("Last Stand", EquipmentCategory.ARMOR, 3,
                    new RunewordEffect(Type.LOW_HEALTH_DAMAGE_REDUCTION, 0, 0.15, 0, 0, 0.3),
                    "Take 15% less damage whenever you're below 30% health."),
            new RunewordDefinition("Guardian's Pulse", EquipmentCategory.ARMOR, 3,
                    new RunewordEffect(Type.PERIODIC_AREA_BUFF_ALLIES, 0, 1, 8, 200, 0),
                    "Every 10 seconds: grant Regeneration I to every ally within 8 blocks."),
            new RunewordDefinition("Flight Response", EquipmentCategory.ARMOR, 3,
                    new RunewordEffect(Type.ON_DAMAGED_SPEED_BURST, 0.25, 2, 0, 80, 0),
                    "On taking damage: 25% chance for Speed II for 4 seconds."),
            new RunewordDefinition("Renewal", EquipmentCategory.ARMOR, 3,
                    new RunewordEffect(Type.PERIODIC_SELF_REGEN, 0, 1.0, 0, 160, 0),
                    "Every 8 seconds: heal half a heart."),
            new RunewordDefinition("Spiteful Ward", EquipmentCategory.ARMOR, 3,
                    new RunewordEffect(Type.ON_DAMAGED_REFLECT, 0.20, 0.30, 0, 0, 0),
                    "On taking damage: 20% chance to reflect 30% of it back at the attacker."),
            new RunewordDefinition("Undying Resolve", EquipmentCategory.ARMOR, 4,
                    new RunewordEffect(Type.LOW_HEALTH_DAMAGE_REDUCTION, 0, 0.25, 0, 0, 0.25),
                    "Take 25% less damage whenever you're below 25% health."),
            new RunewordDefinition("Communal Shield", EquipmentCategory.ARMOR, 4,
                    new RunewordEffect(Type.PERIODIC_AREA_BUFF_ALLIES, 0, 2, 10, 160, 0),
                    "Every 8 seconds: grant Regeneration II to every ally within 10 blocks."),
            new RunewordDefinition("Phoenix Heart", EquipmentCategory.ARMOR, 4,
                    new RunewordEffect(Type.ON_DAMAGED_HEAL, 0.20, 4.0, 0, 0, 0),
                    "On taking damage: 20% chance to heal 2 hearts."),
            new RunewordDefinition("Stormward", EquipmentCategory.ARMOR, 4,
                    new RunewordEffect(Type.ON_DAMAGED_REFLECT, 0.30, 0.35, 0, 0, 0),
                    "On taking damage: 30% chance to reflect 35% of it back at the attacker."),
            new RunewordDefinition("Bastion's Grace", EquipmentCategory.ARMOR, 4,
                    new RunewordEffect(Type.PERIODIC_SELF_REGEN, 0, 1.5, 0, 120, 0),
                    "Every 6 seconds: heal three-quarters of a heart."),
            new RunewordDefinition("Immortal Bulwark", EquipmentCategory.ARMOR, 5,
                    new RunewordEffect(Type.LOW_HEALTH_DAMAGE_REDUCTION, 0, 0.40, 0, 0, 0.20),
                    "Take 40% less damage whenever you're below 20% health."),
            new RunewordDefinition("Sanctuary", EquipmentCategory.ARMOR, 5,
                    new RunewordEffect(Type.PERIODIC_AREA_BUFF_ALLIES, 0, 3, 12, 200, 0),
                    "Every 10 seconds: grant Regeneration III to every ally within 12 blocks."),
            new RunewordDefinition("Martyr's Pact", EquipmentCategory.ARMOR, 5,
                    new RunewordEffect(Type.ON_DAMAGED_REFLECT, 0.40, 0.50, 0, 0, 0),
                    "On taking damage: 40% chance to reflect 50% of it back at the attacker.")
    );

    public static List<RunewordDefinition> all() {
        List<RunewordDefinition> all = new java.util.ArrayList<>();
        all.addAll(WEAPON);
        all.addAll(TOOL);
        all.addAll(ARMOR);
        return all;
    }

    private RunewordCatalog() {
    }
}
