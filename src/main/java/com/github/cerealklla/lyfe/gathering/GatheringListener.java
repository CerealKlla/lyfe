package com.github.cerealklla.lyfe.gathering;

import java.util.List;
import java.util.Set;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.ToolTierUnlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/**
 * Grants Lumberjack/Miner XP and applies their SpeedMultiplier/BonusYieldChance/
 * WholeStructureChance effects (design doc Section 6).
 *
 * <p><b>Tool-tier gating moved out, 2026-10-06 (second round)</b> -- this class used to force break
 * speed to 0 for an under-leveled Axe/Pickaxe (Section 7, 2026-10-04). The user changed their mind
 * the same night: being under-leveled no longer blocks anything at all, it just costs extra
 * durability -- see {@code craft.ProficiencyDurabilityListener}, which covers this same Axe/Pickaxe
 * case generically alongside every other {@code EquipmentTierLadder.ToolType}. `onBreakSpeed` here
 * is back to a plain speed bonus with no gate.
 *
 * <p>Anti-farming (decided 2026-09-24, see decisions.md): a block a player placed themselves never
 * grants gathering rewards when broken -- see {@link PlacedGatheringBlocks}. Placed blocks are also
 * immovable by pistons ({@link #onPistonPre}), closing the alternative exploit of pushing a placed
 * block to "launder" it back into looking natural.
 *
 * <p>All magnitudes below (XP per block, speed/yield/whole-structure-chance-per-level) are
 * placeholder values, deliberately easy to retune -- same framing as {@code Skills.gatheringCurve()}.
 */
public final class GatheringListener {

    private static final long XP_PER_BLOCK = 5;
    private static final float SPEED_PER_LEVEL = 0.01f; // +1%/level -> +50% at level 50
    // Doubled 2026-09-25 (see decisions.md) -- playtest feedback that 25% at max level didn't feel
    // very high. +1%/level -> 50% at level 50.
    private static final double BONUS_YIELD_PER_LEVEL = 0.01;
    private static final double BONUS_YIELD_CAP = 0.50;
    // Per-skill whole-structure-chance caps (2026-09-25, see decisions.md) -- Lumberjack tops out
    // at 20% and Miner at 40% (bumped from 30% the same day per playtest feedback), both at level
    // 50 (per-level rate is just cap/50). Previously a single shared curve for both skills; split
    // out once the user asked for different caps.
    private static final double LUMBERJACK_WHOLE_STRUCTURE_CAP = 0.20;
    private static final double LUMBERJACK_WHOLE_STRUCTURE_PER_LEVEL = LUMBERJACK_WHOLE_STRUCTURE_CAP / 50;
    private static final double MINER_WHOLE_STRUCTURE_CAP = 0.40;
    private static final double MINER_WHOLE_STRUCTURE_PER_LEVEL = MINER_WHOLE_STRUCTURE_CAP / 50;

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
        Player player = event.getEntity();
        int level = Lyfe.getLevel(player, skill.skillId());
        event.setNewSpeed(event.getNewSpeed() * (1.0f + level * SPEED_PER_LEVEL));
    }

    @SubscribeEvent
    public void onEntityPlace(BlockEvent.EntityPlaceEvent event) {
        if (GatheringSkill.forBlock(event.getPlacedBlock()) == null) {
            return;
        }
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        PlacedGatheringBlocks.get(serverLevel.getServer())
                .markPlaced(GlobalPos.of(serverLevel.dimension(), event.getPos()));
    }

    /**
     * Cancels piston movement of any tracked placed block, so a player can't launder a placed
     * block's tracked status by pushing it to a new position and having the old (now-cleared)
     * position look natural again.
     */
    @SubscribeEvent
    public void onPistonPre(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        PlacedGatheringBlocks placedBlocks = PlacedGatheringBlocks.get(serverLevel.getServer());
        if (placedBlocks.isEmpty()) {
            return; // Fast path: nothing tracked anywhere, skip resolving the piston structure at all.
        }
        PistonStructureResolver resolver = event.getStructureHelper();
        if (resolver == null || !resolver.resolve()) {
            return;
        }
        for (BlockPos pos : resolver.getToPush()) {
            if (placedBlocks.isPlaced(GlobalPos.of(serverLevel.dimension(), pos))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos pos : resolver.getToDestroy()) {
            if (placedBlocks.isPlaced(GlobalPos.of(serverLevel.dimension(), pos))) {
                event.setCanceled(true);
                return;
            }
        }
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

        ServerLevel serverLevel = event.getLevel();
        PlacedGatheringBlocks placedBlocks = PlacedGatheringBlocks.get(serverLevel.getServer());
        GlobalPos pos = GlobalPos.of(serverLevel.dimension(), event.getPos());
        boolean wasPlayerPlaced = placedBlocks.isPlaced(pos);
        placedBlocks.clearPlaced(pos); // Untrack regardless of eligibility -- a no-op if untracked.
        if (wasPlayerPlaced) {
            return; // Normal drops still happen; just no gathering rewards (anti-farming).
        }

        Lyfe.addXp(player, skill.skillId(), XP_PER_BLOCK);
        int level = Lyfe.getLevel(player, skill.skillId());

        rollBonusYield(event, level);

        if (!withinWholeStructureClear) {
            rollWholeStructureClear(event, player, skill, level);
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

    private void rollWholeStructureClear(BlockDropsEvent event, Player player, GatheringSkill skill, int level) {
        double cap = skill == GatheringSkill.MINER ? MINER_WHOLE_STRUCTURE_CAP : LUMBERJACK_WHOLE_STRUCTURE_CAP;
        double perLevel = skill == GatheringSkill.MINER ? MINER_WHOLE_STRUCTURE_PER_LEVEL : LUMBERJACK_WHOLE_STRUCTURE_PER_LEVEL;
        double chance = Math.min(cap, level * perLevel);
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
     * Live benefit readout for the Skills screen (common.skill.SkillBenefits) -- current computed
     * values only, no formulas, matching every other skill's own benefit-line convention.
     */
    public static List<String> benefitLines(GatheringSkill skill, int level) {
        double cap = skill == GatheringSkill.MINER ? MINER_WHOLE_STRUCTURE_CAP : LUMBERJACK_WHOLE_STRUCTURE_CAP;
        double perLevel = skill == GatheringSkill.MINER ? MINER_WHOLE_STRUCTURE_PER_LEVEL : LUMBERJACK_WHOLE_STRUCTURE_PER_LEVEL;
        int speedPercent = Math.round(level * SPEED_PER_LEVEL * 100);
        double bonusYieldPercent = Math.min(BONUS_YIELD_CAP, level * BONUS_YIELD_PER_LEVEL) * 100;
        double wholeStructurePercent = Math.min(cap, level * perLevel) * 100;
        String wholeStructureLabel = skill == GatheringSkill.MINER ? "Whole-vein clear chance" : "Whole-tree clear chance";
        String toolLabel = skill == GatheringSkill.MINER ? "Pickaxe" : "Axe";
        return List.of(
                "Break speed +" + speedPercent + "%",
                "Bonus yield chance " + formatPercent(bonusYieldPercent) + "%",
                wholeStructureLabel + " " + formatPercent(wholeStructurePercent) + "%",
                "Unlocked " + toolLabel + " tier: " + ToolTierUnlocks.unlockedTierName(level)
        );
    }

    private static String formatPercent(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
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
