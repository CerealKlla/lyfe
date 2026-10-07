package com.github.cerealklla.lyfe.gathering;

import org.jspecify.annotations.Nullable;

import com.github.cerealklla.lyfe.skill.SkillId;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** Which gathering skill (if any) a block belongs to, for XP/speed/yield purposes (design doc Section 6). */
public enum GatheringSkill {
    LUMBERJACK(new SkillId("lumberjack"), "Lumberjack"),
    MINER(new SkillId("miner"), "Miner");

    private final SkillId skillId;
    private final String displayName;

    GatheringSkill(SkillId skillId, String displayName) {
        this.skillId = skillId;
        this.displayName = displayName;
    }

    public SkillId skillId() {
        return skillId;
    }

    public String displayName() {
        return displayName;
    }

    @Nullable
    public static GatheringSkill forBlock(BlockState state) {
        if (state.is(BlockTags.LOGS)) {
            return LUMBERJACK;
        }
        if (state.is(Tags.Blocks.ORES)) {
            return MINER;
        }
        return null;
    }
}
