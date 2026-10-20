package com.github.cerealklla.lyfe.hunger;

import com.github.cerealklla.lyfe.registration.ModAttachments;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * Client-side sprint lock -- real report, 2026-10-19: "I am able to sprint with less than 3 food
 * icons," despite {@link HungerListener#enforceSprintLock} already forcing {@code setSprinting(false)}
 * server-side every tick once true hunger crosses {@link HungerConstants#SPRINT_LEVEL}. Confirmed the
 * real cause: sprinting is primarily a CLIENT-authoritative decision in vanilla -- the controlling
 * client's own input handling decides whether to request/continue sprinting, based on the food value
 * IT knows about, which is vanilla's real {@code FoodData}. That value is deliberately kept pinned to
 * a constant ({@link HungerConstants#REAL_FOOD_PIN}) specifically so vanilla's own systems never see
 * true hunger -- so the client never locally refuses to sprint, and the server's own after-the-fact
 * correction gets immediately re-overridden by the controlling client's own continued local sprint
 * request the very next tick. This mirrors the same check, but reads the already-synced true
 * {@link PlayerHunger} attachment (the same one {@link HungerOverlay} already reads) instead of
 * vanilla's real, deliberately-faked {@code FoodData} -- and runs on the controlling client itself,
 * which actually is authoritative over its own sprinting state, unlike the server's correction.
 */
public final class ClientSprintLock {

    private ClientSprintLock() {
    }

    public static void tick() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        PlayerHunger hunger = player.getData(ModAttachments.PLAYER_HUNGER);
        if (player.isSprinting() && hunger.getTrueHunger() <= HungerConstants.SPRINT_LEVEL) {
            player.setSprinting(false);
        }
    }
}
