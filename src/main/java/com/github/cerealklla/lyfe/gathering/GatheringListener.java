package com.github.cerealklla.lyfe.gathering;

import java.util.List;
import java.util.Set;

import com.github.cerealklla.lyfe.api.Lyfe;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

/**
 * Grants Lumberjack/Miner XP and applies their SpeedMultiplier/BonusYieldChance/
 * WholeStructureChance effects (design doc Section 6). Tool-tier gating is deliberately NOT
 * implemented here -- Section 7's mechanic is deferred until the crafting overhaul (Section 10)
 * gives a concrete tool-tier system to gate against, per the 2026-09-24 decision to not invent one
 * ahead of that. See {@code EffectType.TOOL_TIER_GATE}'s notes.
 *
 * <p>All magnitudes below (XP per block, speed/yield/whole-structure-chance-per-level) are
 * placeholder values, deliberately easy to retune -- same framing as {@code Skills.gatheringCurve()}.
 */
public final class GatheringListener {

    private static final long XP_PER_BLOCK = 5;
    private static final float SPEED_PER_LEVEL = 0.01f; // +1%/level -> +50% at level 50
    private static final double BONUS_YIELD_PER_LEVEL = 0.005; // +0.5%/level
    private static final double BONUS_YIELD_CAP = 0.25;
    private static final double WHOLE_STRUCTURE_PER_LEVEL = 0.002; // +0.2%/level
    private static final double WHOLE_STRUCTURE_CAP = 0.10;

    // Re-entrancy guard: whole-structure-clear breaks extra blocks by re-firing this same
    // BlockDropsEvent listener (see rollWholeStructureClear). XP/bonus yield still apply
    // individually to those blocks (design doc Section 6), but they must not each roll their own
    // whole-structure-chance too, or a single lucky roll could cascade indefinitely.
    private boolean withinWholeStructureClear = false;

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        GatheringSkill skill = GatheringSkill.forBlock(event.getState());
        if (skill == null) {
            return;
        }
        int level = Lyfe.getLevel(event.getEntity(), skill.skillId());
        event.setNewSpeed(event.getNewSpeed() * (1.0f + level * SPEED_PER_LEVEL));
    }

    @SubscribeEvent
    public void onBlockDrops(BlockDropsEvent event) {
        GatheringSkill skill = GatheringSkill.forBlock(event.getState());
        if (skill == null) {
            return;
        }
        Entity breaker = event.getBreaker();
        if (!(breaker instanceof Player player)) {
            return;
        }

        Lyfe.addXp(player, skill.skillId(), XP_PER_BLOCK);
        int level = Lyfe.getLevel(player, skill.skillId());

        rollBonusYield(event, level);

        if (!withinWholeStructureClear) {
            rollWholeStructureClear(event, player, level);
        }
    }

    private void rollBonusYield(BlockDropsEvent event, int level) {
        List<ItemEntity> drops = event.getDrops();
        if (drops.isEmpty()) {
            return;
        }
        double chance = Math.min(BONUS_YIELD_CAP, level * BONUS_YIELD_PER_LEVEL);
        ServerLevel serverLevel = event.getLevel();
        if (serverLevel.getRandom().nextDouble() >= chance) {
            return;
        }
        ItemEntity original = drops.get(serverLevel.getRandom().nextInt(drops.size()));
        ItemStack copy = original.getItem().copy();
        drops.add(new ItemEntity(serverLevel, original.getX(), original.getY(), original.getZ(), copy));
    }

    private void rollWholeStructureClear(BlockDropsEvent event, Player player, int level) {
        double chance = Math.min(WHOLE_STRUCTURE_CAP, level * WHOLE_STRUCTURE_PER_LEVEL);
        ServerLevel serverLevel = event.getLevel();
        if (serverLevel.getRandom().nextDouble() >= chance) {
            return;
        }

        BlockState matchState = event.getState();
        ItemStack tool = player.getMainHandItem();
        Set<BlockPos> connected = WholeStructureClear.connectedBlocksOf(serverLevel, event.getPos(), matchState);

        withinWholeStructureClear = true;
        try {
            for (BlockPos pos : connected) {
                breakAsPartOfWholeStructureClear(serverLevel, pos, matchState, player, tool);
            }
        } finally {
            withinWholeStructureClear = false;
        }
    }

    /**
     * Breaks one extra block from a whole-structure clear, using the player's real tool (not
     * {@link ServerLevel#destroyBlock}, which always passes an empty tool stack -- that would make
     * tool-gated loot tables, like ores requiring the correct pickaxe tier, silently drop nothing).
     * Re-fires {@code BlockDropsEvent} for this position via {@link Block#dropResources}, which is
     * how this same listener grants XP/bonus yield to each individually-cleared block.
     */
    private void breakAsPartOfWholeStructureClear(ServerLevel level, BlockPos pos, BlockState matchState, Player player, ItemStack tool) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(matchState.getBlock())) {
            return; // Changed since the flood-fill snapshot (e.g. another player broke it first).
        }
        BlockEntity blockEntity = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        level.levelEvent(2001, pos, Block.getId(state));
        Block.dropResources(state, level, pos, blockEntity, player, tool);
        level.removeBlock(pos, false);
    }
}
