package com.github.cerealklla.lyfe.socket;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.util.RandomSource;

/**
 * Each {@link RuneId}'s "worth" (explicit user spec, 2026-10-18): "A Rune's rarity/worth should be
 * determined by the number of Runewords it's rolled into, with heavier weight on runewords with more
 * rune pieces." {@code worth(rune) = sum over every Runeword whose real rolled sequence (see {@link
 * ServerRunewordStore}) contains this rune, of that Runeword's own {@code runeCount}} -- a rune that's
 * part of a 5-rune Runeword contributes more than one that's only ever part of a 2-rune Runeword, and
 * a rune in several Runewords accumulates across all of them.
 *
 * <p>Recomputed once, right after {@link ServerRunewordStore} generates/loads its own server-random
 * mapping (that's the only data this depends on) -- {@code worth} is otherwise immutable for the
 * life of the server, same "generate once, reuse" shape as the mapping itself.
 *
 * <p>"The % chance of it choosing a higher worth rune is lower than a 'more common' rune" (explicit
 * spec) -- {@link #rollRandomRune} weights selection by {@code 1 / (1 + worth)}: a worth-0 rune (not
 * part of any Runeword at all -- possible with 30 runes spread across 60 Runewords' worth of slots)
 * gets the single highest weight, and weight falls off as worth climbs. First-pass weighting shape,
 * flagged tunable like every other numeric constant in this mod.
 */
public final class RuneWorth {

    private static volatile Map<RuneId, Integer> worth = Map.of();

    private RuneWorth() {
    }

    public static void recompute() {
        Map<RuneId, Integer> result = new EnumMap<>(RuneId.class);
        for (RuneId id : RuneId.values()) {
            result.put(id, 0);
        }
        for (RunewordDefinition def : RunewordCatalog.all()) {
            for (RuneId id : ServerRunewordStore.sequenceFor(def.name())) {
                result.merge(id, def.runeCount(), Integer::sum);
            }
        }
        worth = result;
    }

    public static int worthOf(RuneId id) {
        return worth.getOrDefault(id, 0);
    }

    /** Weighted random pick across all 30 runes, rarer (higher-worth) runes less likely -- see class doc. */
    public static RuneId rollRandomRune(RandomSource random) {
        Map<RuneId, Integer> snapshot = worth;
        RuneId[] ids = RuneId.values();
        double[] weights = new double[ids.length];
        double total = 0;
        for (int i = 0; i < ids.length; i++) {
            weights[i] = 1.0 / (1 + snapshot.getOrDefault(ids[i], 0));
            total += weights[i];
        }
        double roll = random.nextDouble() * total;
        for (int i = 0; i < ids.length; i++) {
            roll -= weights[i];
            if (roll < 0) {
                return ids[i];
            }
        }
        return ids[ids.length - 1];
    }
}
