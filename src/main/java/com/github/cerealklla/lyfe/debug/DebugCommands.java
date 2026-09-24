package com.github.cerealklla.lyfe.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.hunger.HungerListener;
import com.github.cerealklla.lyfe.hunger.PlayerHunger;
import com.github.cerealklla.lyfe.registration.ModAttachments;
import com.github.cerealklla.lyfe.skill.SkillId;
import com.github.cerealklla.lyfe.skill.SkillRegistry;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * DEBUG ONLY -- lets an operator directly add to a player's skill XP for testing (e.g. reaching a
 * level to check a chance-based effect like WholeStructureChance) instead of grinding it out.
 * Design doc Section 11 (real XP feedback) and Section 12 (skill tree UI) don't exist yet, so this
 * is currently also one of the only ways to see a skill's XP/level at all. Remove or gate more
 * strictly before any real release.
 *
 * <p>{@code /lyfe xp <skill> <amount> [target]} and {@code /lyfe hunger <amount> [target]} --
 * deliberately requires no permission ({@code Commands.LEVEL_ALL}), not gamemaster, despite these
 * being privileged-feeling commands. Originally gated at gamemaster, but the dev test user was
 * never actually opped in `run/ops.json` (empty by default, gitignored, easy to lose on a fresh
 * checkout -- see decisions.md, 2026-09-24) and this is explicitly debug-only tooling anyway
 * ("remove or gate more strictly before any real release," same as the rest of this class) -- not
 * worth fighting local dev-environment permission setup for a tool nobody but the developer runs.
 */
public final class DebugCommands {

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

        dispatcher.register(Commands.literal("lyfe")
                .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                .then(Commands.literal("xp").then(skillArgument))
                .then(Commands.literal("hunger").then(hungerAmountArgument)));
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

    /** Adjusts true hunger by {@code amount} (positive or negative), clamped to [0, current max]. */
    private static int adjustHunger(CommandSourceStack source, ServerPlayer target, int amount) {
        PlayerHunger hunger = target.getData(ModAttachments.PLAYER_HUNGER);
        int currentMax = HungerListener.currentMaxHunger(target);
        hunger.eat(amount, 0.0F, currentMax);
        target.syncData(ModAttachments.PLAYER_HUNGER); // Mutating in place doesn't auto-sync -- see Lyfe#addXp's note.
        int newHunger = hunger.getTrueHunger();
        source.sendSuccess(() -> Component.literal(
                target.getName().getString() + "'s true hunger is now " + newHunger + "/" + currentMax), true);
        return newHunger;
    }
}
