package com.github.cerealklla.lyfe.debug;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.github.cerealklla.lyfe.api.Lyfe;
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
 * <p>{@code /lyfe xp <skill> <amount> [target]} -- requires gamemaster permission (same level
 * vanilla's own {@code /xp} command requires; verified against the decompiled 26.1.2.109 source,
 * where the old integer permission levels were replaced by named {@code Commands.LEVEL_*} checks).
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

        dispatcher.register(Commands.literal("lyfe")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("xp").then(skillArgument)));
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
}
