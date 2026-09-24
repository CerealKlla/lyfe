package com.github.cerealklla.lyfe.gathering;

import org.jspecify.annotations.Nullable;

import com.github.cerealklla.lyfe.skill.SkillId;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/** Which gathering skill (if any) a block belongs to, for XP/speed/yield purposes (design doc Section 6). */
public enum GatheringSkill {
    LUMBERJACK(new SkillId("lumberjack")),
    MINER(new SkillId("miner"));

    private final SkillId skillId;

    GatheringSkill(SkillId skillId) {
        this.skillId = skillId;
    }

    public SkillId skillId() {
        return skillId;
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
