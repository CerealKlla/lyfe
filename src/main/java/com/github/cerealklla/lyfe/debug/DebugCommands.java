package com.github.cerealklla.lyfe.debug;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.github.cerealklla.cartographyr.api.Cartography;
import com.github.cerealklla.cartographyr.geo.Classification;
import com.github.cerealklla.cartographyr.geo.DisplayText;
import com.github.cerealklla.cartographyr.geo.GeographicEntity;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.hunger.HungerListener;
import com.github.cerealklla.lyfe.hunger.PlayerHunger;
import com.github.cerealklla.lyfe.knowledge.KnowledgeFactor;
import com.github.cerealklla.lyfe.knowledge.PlayerKnowledge;
import com.github.cerealklla.lyfe.knowledge.SignListener;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.skill.SkillRegistry;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

/**
 * DEBUG ONLY -- lets an operator directly add to a player's skill XP for testing (e.g. reaching a
 * level to check a chance-based effect like WholeStructureChance) instead of grinding it out.
 * Design doc Section 11 (real XP feedback) and Section 12 (skill tree UI) don't exist yet, so this
 * is currently also one of the only ways to see a skill's XP/level at all. Remove or gate more
 * strictly before any real release.
 *
 * <p>{@code /lyfe xp <skill> <amount> [target]} (adds {@code amount} XP) and
 * {@code /lyfe hunger <amount> [target]} (sets true hunger directly to {@code amount}) --
 * deliberately requires no permission ({@code Commands.LEVEL_ALL}), not gamemaster, despite these
 * being privileged-feeling commands. Originally gated at gamemaster, but the dev test user was
 * never actually opped in `run/ops.json` (empty by default, gitignored, easy to lose on a fresh
 * checkout -- see decisions.md, 2026-09-24) and this is explicitly debug-only tooling anyway
 * ("remove or gate more strictly before any real release," same as the rest of this class) -- not
 * worth fighting local dev-environment permission setup for a tool nobody but the developer runs.
 */
public final class DebugCommands {

    private static final Random RANDOM = new Random();

    private DebugCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var amountWithTarget = Commands.argument("target", EntityArgument.player())
                .executes(context -> addXp(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "target"),
                        StringArgumentType.getString(context, "skill"),
                        IntegerArgumentType.getInteger(context, "amount")));

        var amountArgument = Commands.argument("amount", IntegerArgumentType.integer())
                .executes(context -> addXp(
                        context.getSource(),
                        context.getSource().getPlayerOrException(),
                        StringArgumentType.getString(context, "skill"),
                        IntegerArgumentType.getInteger(context, "amount")))
                .then(amountWithTarget);

        var skillArgument = Commands.argument("skill", StringArgumentType.word())
                .suggests((context, builder) -> {
                    SkillRegistry.all().forEach(def -> builder.suggest(def.id().value()));
                    return builder.buildFuture();
                })
                .then(amountArgument);

        var hungerWithTarget = Commands.argument("target", EntityArgument.player())
                .executes(context -> adjustHunger(
                        context.getSource(),
                        EntityArgument.getPlayer(context, "target"),
                        IntegerArgumentType.getInteger(context, "amount")));

        var hungerAmountArgument = Commands.argument("amount", IntegerArgumentType.integer())
                .executes(context -> adjustHunger(
                        context.getSource(),
                        context.getSource().getPlayerOrException(),
                        IntegerArgumentType.getInteger(context, "amount")))
                .then(hungerWithTarget);

        var knowledgeResetWithTarget = Commands.argument("target", EntityArgument.player())
                .executes(context -> resetKnowledge(context.getSource(), EntityArgument.getPlayer(context, "target")));

        var knowledgeLiteral = Commands.literal("knowledge")
                .then(Commands.literal("reset")
                        .executes(context -> resetKnowledge(context.getSource(), context.getSource().getPlayerOrException()))
                        .then(knowledgeResetWithTarget));

        // learn <x> <z> <count> is only wired up when Cartographyr is loaded -- it needs a real
        // GeographicEntity to attach factors to, same soft-dependency gate LyfeMod uses for
        // LocationTracker/SignListener (see that class's comment). Takes x/z instead of using the
        // target's current position on purpose: LocationTracker.markVisited fires every
        // CHECK_INTERVAL_TICKS for whatever the target is standing on/near, which would instantly
        // overwrite a partial grant back to full knowledge if this worked off their live position.
        if (ModList.get().isLoaded("cartographyr")) {
            var learnCount = Commands.argument("count", IntegerArgumentType.integer(0, KnowledgeFactor.values().length))
                    .executes(context -> learnFactors(
                            context.getSource(),
                            context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "x"),
                            IntegerArgumentType.getInteger(context, "z"),
                            IntegerArgumentType.getInteger(context, "count")));
            var learnZ = Commands.argument("z", IntegerArgumentType.integer()).then(learnCount);
            var learnX = Commands.argument("x", IntegerArgumentType.integer()).then(learnZ);
            knowledgeLiteral.then(Commands.literal("learn").then(learnX));

            // Faster path for the common case: no coordinates to look up at all -- picks a random
            // already-discovered settlement and resets-then-grants in one step (the "walk to a
            // town, note x/z, walk out, reset, learn" loop was the whole reason this got added).
            var learnRandomCount = Commands.argument("count", IntegerArgumentType.integer(0, KnowledgeFactor.values().length))
                    .executes(context -> learnRandomSettlement(
                            context.getSource(),
                            context.getSource().getPlayerOrException(),
                            IntegerArgumentType.getInteger(context, "count")));
            knowledgeLiteral.then(Commands.literal("learnrandom").then(learnRandomCount));
        }

        dispatcher.register(Commands.literal("lyfe")
                .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                .then(Commands.literal("xp").then(skillArgument))
                .then(Commands.literal("hunger").then(hungerAmountArgument))
                .then(knowledgeLiteral));
    }

    private static int addXp(CommandSourceStack source, ServerPlayer target, String skillIdValue, int amount) {
        SkillId skillId = new SkillId(skillIdValue);
        if (SkillRegistry.get(skillId).isEmpty()) {
            source.sendFailure(Component.literal("Unknown skill: " + skillIdValue));
            return 0;
        }
        long newXp = Lyfe.addXp(target, skillId, amount);
        int newLevel = Lyfe.getLevel(target, skillId);
        source.sendSuccess(() -> Component.literal(
                target.getName().getString() + "'s " + skillIdValue + " XP is now " + newXp + " (Level " + newLevel + ")"), true);
        return (int) newXp;
    }

    /** Sets true hunger directly to {@code amount}, clamped to [0, current max]. */
    private static int adjustHunger(CommandSourceStack source, ServerPlayer target, int amount) {
        PlayerHunger hunger = target.getData(ModAttachments.PLAYER_HUNGER);
        int currentMax = HungerListener.currentMaxHunger(target);
        hunger.setTrueHunger(amount, currentMax);
        target.syncData(ModAttachments.PLAYER_HUNGER); // Mutating in place doesn't auto-sync -- see Lyfe#addXp's note.
        int newHunger = hunger.getTrueHunger();
        source.sendSuccess(() -> Component.literal(
                target.getName().getString() + "'s true hunger is now " + newHunger + "/" + currentMax), true);
        return newHunger;
    }

    /**
     * Wipes every entry from the target's {@code PlayerKnowledge} -- lets a tester re-exercise
     * "learning a place for the first time" (e.g. Cartographyr XP on a genuine sign/map read)
     * without needing a fresh character. Note that {@code location.LocationTracker} calls {@code
     * PlayerKnowledge#markVisited} on anything it detects the player standing near, so a reset
     * won't stay clean if the target is currently at/near a place they'd immediately re-detect --
     * move away first, or reset then read about a different, distant place.
     */
    private static int resetKnowledge(CommandSourceStack source, ServerPlayer target) {
        target.setData(ModAttachments.PLAYER_KNOWLEDGE, new PlayerKnowledge());
        source.sendSuccess(() -> Component.literal(target.getName().getString() + "'s world knowledge has been reset"), true);
        return 1;
    }

    /**
     * Grants the target the first {@code count} {@link KnowledgeFactor}s (declaration order --
     * {@code DIRECTION, DISTANCE, POSITION}) for whatever {@code GeographicEntity} sits at world
     * coordinates ({@code x}, {@code z}) in the target's own dimension, without requiring them to
     * physically visit it. Exists because the only two real ways to gain factors --
     * {@code PlayerKnowledge#markVisited} (always grants all three) and reading a sign/map written
     * by someone whose own knowledge/level cap already covers all three -- make it practically
     * impossible to reach a genuinely partial knowledge state during solo testing. {@code
     * learnLocationFactors} is purely additive, so to test a lower count than the target already
     * has, {@code /lyfe knowledge reset} first.
     */
    private static int learnFactors(CommandSourceStack source, ServerPlayer target, int x, int z, int count) {
        ServerLevel level = (ServerLevel) target.level();
        Set<GeographicEntity> here = Cartography.getEntitiesAt(level, x, z);
        if (here.isEmpty()) {
            source.sendFailure(Component.literal("No known place at " + x + ", " + z));
            return 0;
        }
        return grantFactors(source, target, here.iterator().next(), count);
    }

    /**
     * Picks a random already-discovered settlement in the target's own dimension and grants
     * {@code count} factors for it -- purely additive (same as the real {@code
     * learnLocationFactors}), deliberately does NOT reset first (see decisions.md, 2026-09-25:
     * originally reset-then-granted, but that made it impossible to run more than once to build up
     * a richer test set -- run it several times to accumulate knowledge across settlements, or
     * {@code /lyfe knowledge reset} first for a clean slate). One-command replacement for the "walk
     * to a town, note x/z from F3, walk out, reset, learn x z count" loop {@code learn} required.
     */
    private static int learnRandomSettlement(CommandSourceStack source, ServerPlayer target, int count) {
        ServerLevel level = (ServerLevel) target.level();
        Set<GeographicEntity> settlements = Cartography.findEntities(level, Classification.CONSTRUCTED);
        if (settlements.isEmpty()) {
            source.sendFailure(Component.literal(
                    "No settlements discovered yet in " + level.dimension() + " -- explore until one is found first."));
            return 0;
        }
        List<GeographicEntity> list = new ArrayList<>(settlements);
        GeographicEntity entity = list.get(RANDOM.nextInt(list.size()));

        return grantFactors(source, target, entity, count);
    }

    private static int grantFactors(CommandSourceStack source, ServerPlayer target, GeographicEntity entity, int count) {
        Set<KnowledgeFactor> factors = EnumSet.noneOf(KnowledgeFactor.class);
        KnowledgeFactor[] values = KnowledgeFactor.values();
        for (int i = 0; i < count; i++) {
            factors.add(values[i]);
        }

        PlayerKnowledge knowledge = target.getData(ModAttachments.PLAYER_KNOWLEDGE);
        knowledge.learnLocationFactors(entity.id().value(), factors);
        target.syncData(ModAttachments.PLAYER_KNOWLEDGE); // Mutating in place doesn't auto-sync -- see Lyfe#addXp's note.

        String placeName = DisplayText.forEntity(entity);
        int finalCount = knowledge.get(entity.id().value()).map(e -> e.locationFactors().size()).orElse(0);

        // Also report what a sign/map can actually embed at the target's current Cartographyr
        // level -- the writer-quality cap (SignListener#levelCap) is independent of raw knowledge
        // and can be lower, which was a real point of confusion during testing (see that method's
        // own note).
        int cartographyrLevel = Lyfe.getLevel(target, Skills.CARTOGRAPHYR_ID);
        int embeddableCount = Math.min(finalCount, SignListener.levelCap(cartographyrLevel));
        String capNote = embeddableCount < finalCount
                ? " (but only " + embeddableCount + "/" + values.length + " embeddable on a sign/map at Cartographyr level " + cartographyrLevel + ")"
                : "";

        source.sendSuccess(() -> Component.literal(
                target.getName().getString() + " now knows " + finalCount + "/" + values.length + " factors about " + placeName + capNote), true);
        return finalCount;
    }
}
