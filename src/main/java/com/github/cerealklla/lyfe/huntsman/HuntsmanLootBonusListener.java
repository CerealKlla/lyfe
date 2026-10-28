package com.github.cerealklla.lyfe.huntsman;

import java.util.List;
import java.util.UUID;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.combat.AssistTracker;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * Huntsman's first real level-based perk (explicit user spec, 2026-10-28): a chance, per
 * assist-credited attacker, to duplicate the mob's own normal drop -- up to 3 independent rolls at
 * higher levels, each an extra full copy of what the mob actually dropped. Same "who counts as a
 * Huntsman kill" exclusion as {@link HuntsmanListener} (no Villagers/Settlemynts Guards), and the
 * same multi-attacker-stacking precedent {@code loot.GoldFindDropListener} already established --
 * each credited player rolls independently off their own Huntsman level.
 *
 * <p>Per-roll chance is cumulative across tiers (confirmed explicitly with the user, who corrected
 * an initial reading of the spec): level 0-10 gets 1 roll at 5%; 11-25 gets 2 rolls at 15% each;
 * 26-35 gets 3 rolls at 25% each; 36+ gets 3 rolls at 35% each. "Duplicate" means a genuine second
 * copy of the mob's own real drops -- snapshotted at {@link EventPriority#HIGH} (before
 * {@code loot.GoldFindDropListener}/Gem/Rune drop listeners run) so a duplicate is always the mob's
 * true vanilla-ish loot, never a copy of another bonus system's own added item.
 */
public final class HuntsmanLootBonusListener {

    private record Tier(int minLevel, double chancePerRoll, int rolls) {
    }

    // Highest minLevel first -- tierFor scans top-down for the first match.
    private static final Tier[] TIERS = {
            new Tier(36, 0.35, 3),
            new Tier(26, 0.25, 3),
            new Tier(11, 0.15, 2),
            new Tier(0, 0.05, 1),
    };

    private final RandomSource random = RandomSource.create();

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel serverLevel) || victim instanceof Player || HuntsmanListener.isExcludedFromHuntsman(victim)) {
            return;
        }
        if (event.getDrops().isEmpty()) {
            return;
        }
        List<ItemStack> originalDrops = event.getDrops().stream().map(e -> e.getItem().copy()).toList();

        for (UUID playerId : AssistTracker.creditedPlayers(victim)) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                continue;
            }
            Tier tier = tierFor(Lyfe.getLevel(player, Skills.HUNTSMAN_ID));
            for (int roll = 0; roll < tier.rolls(); roll++) {
                if (random.nextDouble() < tier.chancePerRoll()) {
                    for (ItemStack stack : originalDrops) {
                        event.getDrops().add(new ItemEntity(serverLevel, victim.getX(), victim.getY(), victim.getZ(), stack.copy()));
                    }
                }
            }
        }
    }

    private static Tier tierFor(int huntsmanLevel) {
        for (Tier tier : TIERS) {
            if (huntsmanLevel >= tier.minLevel()) {
                return tier;
            }
        }
        return TIERS[TIERS.length - 1];
    }
}
