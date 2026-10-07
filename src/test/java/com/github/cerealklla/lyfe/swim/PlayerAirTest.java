package com.github.cerealklla.lyfe.swim;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.mojang.serialization.DataResult;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

class PlayerAirTest {

    @Test
    void startsAtVanillaDefault() {
        PlayerAir air = new PlayerAir();
        assertEquals(300, air.getTrueAir());
        assertEquals(0L, air.getCachedXp());
    }

    @Test
    void realAirDropNeverGoesBelowZero() {
        PlayerAir air = new PlayerAir();
        air.applyRealAirDrop(400, 300);
        assertEquals(0, air.getTrueAir());
    }

    @Test
    void realAirGainClampsAtCurrentMax() {
        PlayerAir air = new PlayerAir();
        air.applyRealAirGain(100, 300);
        assertEquals(300, air.getTrueAir());
    }

    @Test
    void realAirGainWorksPastVanillaCapAtGrownCapacity() {
        PlayerAir air = new PlayerAir();
        air.applyRealAirDrop(300, 900); // down to 0, grown capacity
        air.applyRealAirGain(1000, 900);
        assertEquals(900, air.getTrueAir());
    }

    @Test
    void bankXpAccumulates() {
        PlayerAir air = new PlayerAir();
        air.bankXp(5);
        air.bankXp(3);
        assertEquals(8L, air.getCachedXp());
    }

    @Test
    void spendBankSpendsUpToWhatsThere() {
        PlayerAir air = new PlayerAir();
        air.bankXp(42);
        long spent = air.spendBank(10);
        assertEquals(10L, spent);
        assertEquals(32L, air.getCachedXp());
    }

    @Test
    void spendBankClampsToWhatsAvailable() {
        PlayerAir air = new PlayerAir();
        air.bankXp(5);
        long spent = air.spendBank(100);
        assertEquals(5L, spent);
        assertEquals(0L, air.getCachedXp());
    }

    @Test
    void spendBankWhenEmptyReturnsZero() {
        PlayerAir air = new PlayerAir();
        assertEquals(0L, air.spendBank(10));
    }

    @Test
    void wipeBankClearsWithoutReturningAnything() {
        PlayerAir air = new PlayerAir();
        air.bankXp(99);
        air.wipeBank();
        assertEquals(0L, air.getCachedXp());
    }

    @Test
    void survivesEncodeDecodeRoundTrip() {
        PlayerAir original = new PlayerAir();
        original.applyRealAirDrop(50, 300);
        original.bankXp(12);

        var codec = PlayerAir.CODEC.codec();
        DataResult<Tag> encodeResult = codec.encodeStart(NbtOps.INSTANCE, original);
        Tag encoded = encodeResult.result().orElseThrow(() -> new AssertionError("Encode failed: " + encodeResult.error()));

        DataResult<PlayerAir> decodeResult = codec.parse(NbtOps.INSTANCE, encoded);
        PlayerAir decoded = decodeResult.result().orElseThrow(() -> new AssertionError("Decode failed: " + decodeResult.error()));

        assertEquals(original.getTrueAir(), decoded.getTrueAir());
        assertEquals(original.getCachedXp(), decoded.getCachedXp());
    }
}
