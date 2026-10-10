package com.github.cerealklla.lyfe.hunger;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

class PlayerHungerTest {

    @Test
    void startsAtVanillaDefaults() {
        PlayerHunger hunger = new PlayerHunger();
        assertEquals(20, hunger.getTrueHunger());
        assertEquals(5.0F, hunger.getTrueSaturation());
    }

    @Test
    void realHungerDropNeverGoesBelowZero() {
        PlayerHunger hunger = new PlayerHunger();
        hunger.applyRealHungerDrop(25);
        assertEquals(0, hunger.getTrueHunger());
    }

    @Test
    void eatingClampsAtCurrentMax() {
        PlayerHunger hunger = new PlayerHunger();
        hunger.applyRealHungerDrop(15); // down to 5
        hunger.eat(100, 0.0F, 20);
        assertEquals(20, hunger.getTrueHunger());
    }

    @Test
    void eatingPastCurrentMaxWorksAtGrownCapacity() {
        PlayerHunger hunger = new PlayerHunger();
        hunger.applyRealHungerDrop(20); // down to 0
        hunger.eat(100, 0.0F, 60);
        assertEquals(60, hunger.getTrueHunger());
    }

    @Test
    void savedSaturationIsSpentBeforeTrueHungerDrops() {
        // Real bug fixed 2026-10-10: saturation used to be merely clamped, never actually spent --
        // true hunger dropped immediately on every real exhaustion crossing regardless of how much
        // saturation was available. Starts at 20 hunger / 5.0 saturation.
        PlayerHunger hunger = new PlayerHunger();
        hunger.applyRealHungerDrop(3);
        assertEquals(20, hunger.getTrueHunger()); // fully absorbed by saturation
        assertEquals(2.0F, hunger.getTrueSaturation());
    }

    @Test
    void trueHungerOnlyDropsOnceSaturationIsDepleted() {
        PlayerHunger hunger = new PlayerHunger(); // 20 hunger, 5.0 saturation
        hunger.applyRealHungerDrop(8); // 5 absorbed by saturation, 3 spills into true hunger
        assertEquals(0.0F, hunger.getTrueSaturation());
        assertEquals(17, hunger.getTrueHunger());
    }

    @Test
    void setTrueHungerSetsAbsoluteValueNotDelta() {
        PlayerHunger hunger = new PlayerHunger(); // starts at 20/20
        hunger.setTrueHunger(2, 20);
        assertEquals(2, hunger.getTrueHunger());
    }

    @Test
    void setTrueHungerClampsToRange() {
        PlayerHunger hunger = new PlayerHunger();
        hunger.setTrueHunger(-5, 20);
        assertEquals(0, hunger.getTrueHunger());
        hunger.setTrueHunger(999, 20);
        assertEquals(20, hunger.getTrueHunger());
    }

    @Test
    void survivesEncodeDecodeRoundTrip() {
        PlayerHunger original = new PlayerHunger();
        original.applyRealHungerDrop(7);

        var codec = PlayerHunger.CODEC.codec();
        DataResult<Tag> encodeResult = codec.encodeStart(NbtOps.INSTANCE, original);
        Tag encoded = encodeResult.result().orElseThrow(() -> new AssertionError("Encode failed: " + encodeResult.error()));

        DataResult<PlayerHunger> decodeResult = codec.parse(NbtOps.INSTANCE, encoded);
        PlayerHunger decoded = decodeResult.result().orElseThrow(() -> new AssertionError("Decode failed: " + decodeResult.error()));

        assertEquals(original.getTrueHunger(), decoded.getTrueHunger());
        assertEquals(original.getTrueSaturation(), decoded.getTrueSaturation());
    }
}
