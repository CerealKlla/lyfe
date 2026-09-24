package com.github.cerealklla.lyfe.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import com.github.cerealklla.lyfe.skill.SkillId;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

class PlayerSkillsCodecTest {

    @Test
    void roundTripsXpForMultipleSkills() {
        PlayerSkills original = new PlayerSkills();
        original.addXp(new SkillId("lumberjack"), 42);
        original.addXp(new SkillId("miner"), 7);

        PlayerSkills decoded = roundTrip(original);

        assertEquals(42, decoded.getXp(new SkillId("lumberjack")));
        assertEquals(7, decoded.getXp(new SkillId("miner")));
        assertEquals(original.allXp(), decoded.allXp());
    }

    @Test
    void unrecordedSkillReportsZeroXp() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0, skills.getXp(new SkillId("never_touched")));
    }

    @Test
    void addXpNeverGoesBelowZero() {
        PlayerSkills skills = new PlayerSkills();
        skills.addXp(new SkillId("lumberjack"), 5);
        long result = skills.addXp(new SkillId("lumberjack"), -100);
        assertEquals(0, result);
        assertEquals(0, skills.getXp(new SkillId("lumberjack")));
    }

    private static PlayerSkills roundTrip(PlayerSkills skills) {
        DataResult<Tag> encodeResult = PlayerSkills.CODEC.codec().encodeStart(NbtOps.INSTANCE, skills);
        Tag encoded = encodeResult.result().orElseThrow(() -> new AssertionError("Encode failed: " + encodeResult.error()));

        DataResult<PlayerSkills> decodeResult = PlayerSkills.CODEC.codec().parse(NbtOps.INSTANCE, encoded);
        return decodeResult.result().orElseThrow(() -> new AssertionError("Decode failed: " + decodeResult.error()));
    }
}
