package com.github.cerealklla.lyfe.fishing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.github.cerealklla.lyfe.LyfeMod;
import com.github.cerealklla.lyfe.api.Lyfe;
import com.github.cerealklla.lyfe.craft.EquipmentTierLadder;
import com.github.cerealklla.lyfe.registration.ModItems;
import com.github.cerealklla.lyfe.research.ResearchNoteConstants;
import com.github.cerealklla.lyfe.research.ResearchNoteItem;
import com.github.cerealklla.lyfe.skill.Skills;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Fisherman skill (design doc, 2026-10-03 user request): XP on catch, bonus loot scaling with
 * level, the one-player-visible "lucky spot" bonus, Sunken Treasure Bags, Locked Chest/Key rare
 * finds, and -- since vanilla fishing cannot function in the Nether/End at all (confirmed via
 * decompiled {@code FishingHook} research: it only ever bobs/catches when sitting in a block
 * satisfying {@code FluidTags.WATER}, which neither dimension naturally has) -- a parallel, fully
 * custom catch loop for those two dimensions that never spawns a real {@code FishingHook}.
 */
public final class FishermanListener {

    // Session-only, per-player: this cast's lucky spot, and whether the hook has landed on it yet
    // (consumed once at catch time, same session-tracking idiom as swim.SwimmerListener's maps).
    private final Map<UUID, BlockPos> luckySpots = new HashMap<>();
    private final Map<UUID, Boolean> luckySpotHit = new HashMap<>();

    // Nether/End custom catch loop (Section F) -- a per-player countdown running only while the
    // player is actively use-holding a fishing rod in one of those dimensions.
    private final Map<UUID, Integer> voidCatchTimer = new HashMap<>();

    @SubscribeEvent
    public void onItemFished(ItemFishedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        FishingHook hook = event.getHookEntity();
        ServerLevel level = (ServerLevel) hook.level();
        BlockPos pos = hook.blockPosition();
        RandomSource random = level.getRandom();
        UUID playerId = player.getUUID();

        boolean lucky = Boolean.TRUE.equals(luckySpotHit.remove(playerId));
        if (luckySpots.remove(playerId) != null) {
            PacketDistributor.sendToPlayer(player, new LuckySpotPayload(Optional.empty()));
        }

        double totalXp = 0;
        boolean caughtFish = false;
        for (ItemStack stack : event.getDrops()) {
            FishingConstants.WeightRange range = FishingConstants.WEIGHT_RANGES.get(stack.getItem());
            if (range == null) {
                continue;
            }
            caughtFish = true;
            double pounds = rollWeight(range, lucky, random);
            stack.set(ModItems.FISH_WEIGHT.get(), new FishWeight(pounds));
            stack.set(DataComponents.CUSTOM_NAME, catchName(stack.getItem(), pounds));
            totalXp += FishingConstants.XP_PER_POUND * pounds;
        }
        if (!caughtFish) {
            return;
        }
        Lyfe.addXp(player, Skills.FISHERMAN_ID, FishingConstants.BASE_CATCH_XP + Math.round(totalXp));

        int skillLevel = Lyfe.getLevel(player, Skills.FISHERMAN_ID);
        double bonusChance = bonusLootChance(skillLevel) + (lucky ? FishingConstants.LUCKY_SPOT_BONUS_LOOT_BUMP : 0.0);
        double bonusRoll = random.nextDouble();
        boolean bonusHit = bonusRoll < bonusChance;
        LyfeMod.LOGGER.info("[Fisherman] bonus loot: level={} chance={}% (lucky={}) roll={} -> {}",
                skillLevel, String.format(Locale.ROOT, "%.1f", bonusChance * 100.0), lucky,
                String.format(Locale.ROOT, "%.3f", bonusRoll), bonusHit ? "HIT" : "miss");
        if (bonusHit) {
            int count = FishingConstants.MIN_BONUS_ITEMS
                    + random.nextInt(FishingConstants.MAX_BONUS_ITEMS - FishingConstants.MIN_BONUS_ITEMS + 1);
            Holder<Biome> biome = level.getBiome(pos);
            FishLootPools.BiomeCategory category = FishLootPools.categoryFor(biome);
            for (int i = 0; i < count; i++) {
                grant(player, rollBonusItem(category, random));
            }
        }

        boolean nearShipwreck = level.structureManager()
                .getStructureWithPieceAt(pos, StructureTags.SHIPWRECK) != null;

        if (hook.isOpenWaterFishing() && isDeepWater(level, pos)) {
            double chance = sunkenTreasureChance(skillLevel);
            double roll = random.nextDouble();
            boolean hit = roll < chance;
            LyfeMod.LOGGER.info("[Fisherman] sunken treasure: level={} chance={}% roll={} -> {}",
                    skillLevel, String.format(Locale.ROOT, "%.1f", chance * 100.0),
                    String.format(Locale.ROOT, "%.3f", roll), hit ? "HIT" : "miss");
            if (hit) {
                grant(player, SunkenTreasureItem.createWith(FixedLootContents.rollSunkenTreasure(random)));
            }
        }

        double lockedChestChance = nearShipwreck && !alreadyCarryingChestAndKey(player)
                ? FishingConstants.LOCKED_CHEST_SHIPWRECK_CHANCE
                : FishingConstants.LOCKED_CHEST_BASE_CHANCE;
        double lockedChestRoll = random.nextDouble();
        boolean lockedChestHit = lockedChestRoll < lockedChestChance;
        LyfeMod.LOGGER.info("[Fisherman] locked chest/key: nearShipwreck={} chance={}% roll={} -> {}",
                nearShipwreck, String.format(Locale.ROOT, "%.1f", lockedChestChance * 100.0),
                String.format(Locale.ROOT, "%.3f", lockedChestRoll), lockedChestHit ? "HIT" : "miss");
        if (lockedChestHit) {
            int variantIndex = random.nextInt(FishingConstants.CHEST_VARIANTS.length);
            String variant = FishingConstants.CHEST_VARIANTS[variantIndex];
            if (random.nextBoolean()) {
                grant(player, LockedChestItem.createLocked(
                        ModItems.lockedChestFor(variant), FixedLootContents.rollLockedChest(random)));
            } else {
                grant(player, new ItemStack(ModItems.keyFor(variant).get()));
            }
        }
    }

    /**
     * Grants an item straight to the player's inventory (or drops it at their feet if full). Real
     * fix, 2026-10-03: {@code ItemFishedEvent#getDrops()} looked mutable and additions compiled/ran
     * with no error, but NeoForge's real source copies the constructor's input list into its own
     * private field ({@code this.stacks.addAll(stacks)}) -- completely decoupled from the list
     * {@code FishingHook#retrieve} actually iterates afterward to spawn real {@code ItemEntity}s. The
     * javadoc says so directly ("You cannot use this to modify the drops the player will get.") but
     * an earlier pass had concluded otherwise from reading the vanilla fire site, not the event
     * class's own source -- every bonus loot/Sunken Treasure/Locked Chest/Key grant was a silent
     * no-op despite the roll logic itself always being correct (confirmed live via the debug logging
     * below: rolls were landing "HIT" but nothing ever reached the player). The fish's own weight/
     * name tagging was never affected, since mutating an existing shared {@code ItemStack} works
     * fine -- only adding new stacks to the list was broken.
     */
    private static void grant(ServerPlayer player, ItemStack item) {
        if (!player.getInventory().add(item)) {
            player.drop(item, false);
        }
    }

    private static Component catchName(Item fish, double pounds) {
        String base = new ItemStack(fish).getHoverName().getString();
        return Component.literal(base + " (" + String.format(Locale.ROOT, "%.2f", pounds) + " lb)");
    }

    static double rollWeight(FishingConstants.WeightRange range, boolean lucky, RandomSource random) {
        double min = range.minLb();
        double max = range.maxLb();
        if (lucky) {
            min = max - (max - min) * FishingConstants.LUCKY_SPOT_UPPER_BAND;
        }
        double raw = min + random.nextDouble() * (max - min);
        return Math.round(raw * 100.0) / 100.0;
    }

    private static ItemStack rollBonusItem(FishLootPools.BiomeCategory category, RandomSource random) {
        if (random.nextDouble() < FishingConstants.RESEARCH_NOTE_SLOT_CHANCE) {
            int tier = 1 + random.nextInt(EquipmentTierLadder.MAX_REACHABLE_TIER);
            Identifier resultId = ResearchNoteConstants.randomTargetForTier(tier, random);
            return ResearchNoteItem.createFor(resultId, tier);
        }
        List<Item> pool = FishLootPools.poolFor(category);
        return new ItemStack(pool.get(random.nextInt(pool.size())));
    }

    /** "Deep" = at least DEEP_WATER_MIN_COLUMN contiguous water blocks below the bobber, bounded scan. */
    private static boolean isDeepWater(ServerLevel level, BlockPos hookPos) {
        int waterDepth = 0;
        for (int i = 1; i <= FishingConstants.DEEP_WATER_SCAN_CAP; i++) {
            if (level.getFluidState(hookPos.below(i)).is(FluidTags.WATER)) {
                waterDepth++;
            } else {
                break;
            }
        }
        return waterDepth >= FishingConstants.DEEP_WATER_MIN_COLUMN;
    }

    /** Never surfaced in player-facing text -- see design doc Section G's own secret-design note. */
    private static boolean alreadyCarryingChestAndKey(ServerPlayer player) {
        boolean hasChest = false;
        boolean hasKey = false;
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            if (stack.getItem() instanceof LockedChestItem) {
                hasChest = true;
            } else if (stack.getItem() instanceof KeyItem) {
                hasKey = true;
            }
        }
        return hasChest && hasKey;
    }

    /**
     * Auto-routes a caught/dropped fish straight into a Catch Bag already in the player's inventory,
     * if one has room (2026-10-03 playtest request) -- intercepted pre-pickup so a fish that fits
     * never touches a loose inventory slot at all. A fish that doesn't fit in any carried bag (none
     * present, or all would exceed {@link FishingConstants#CATCH_BAG_MAX_POUNDS}) falls through to
     * vanilla's own default pickup, unchanged.
     */
    @SubscribeEvent
    public void onItemPickupPre(ItemEntityPickupEvent.Pre event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (event.getItemEntity().hasPickUpDelay()) {
            // Respect vanilla's own throw-immunity window (e.g. a freshly-dropped fish) -- without
            // this check the bag was swallowing a tossed fish the instant it hit the ground, before
            // the player could actually throw it anywhere (found live, 2026-10-03).
            return;
        }
        ItemStack groundStack = event.getItemEntity().getItem();
        if (!CatchBagContents.isFish(groundStack)) {
            return;
        }
        for (ItemStack invStack : player.getInventory().getNonEquipmentItems()) {
            if (!(invStack.getItem() instanceof CatchBagItem)) {
                continue;
            }
            CatchBagContents contents = invStack.getOrDefault(ModItems.CATCH_BAG_CONTENTS, CatchBagContents.EMPTY);
            CatchBagContents.InsertResult result = contents.insert(groundStack);
            if (result.inserted()) {
                invStack.set(ModItems.CATCH_BAG_CONTENTS, result.contents());
                groundStack.shrink(groundStack.getCount());
                event.setCanPickup(TriState.FALSE);
                return;
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        handleLuckySpot(player);
        handleVoidFishing(player);
    }

    private void handleLuckySpot(ServerPlayer player) {
        UUID playerId = player.getUUID();
        boolean holdingRod = player.getMainHandItem().is(Items.FISHING_ROD)
                || player.getOffhandItem().is(Items.FISHING_ROD);

        if (!holdingRod) {
            if (luckySpots.remove(playerId) != null) {
                PacketDistributor.sendToPlayer(player, new LuckySpotPayload(Optional.empty()));
            }
            luckySpotHit.remove(playerId);
            return;
        }

        if (!luckySpots.containsKey(playerId)) {
            BlockPos chosen = pickLuckySpot(player);
            if (chosen != null) {
                luckySpots.put(playerId, chosen);
                PacketDistributor.sendToPlayer(player, new LuckySpotPayload(Optional.of(chosen)));
            }
            luckySpotHit.put(playerId, false);
        }

        FishingHook hook = player.fishing;
        BlockPos spot = luckySpots.get(playerId);
        if (hook != null && spot != null && hook.blockPosition().equals(spot)) {
            luckySpotHit.put(playerId, true);
            // Constant splashing while the bobber actually sits on the lucky spot (2026-10-03
            // playtest request) -- so the player can tell a good cast from a bad one without
            // needing to look for the marker particle anymore. Reuses vanilla's own real bite-splash
            // particle (FishingHook#catchingFish), visible only to this player, same "one player
            // only" precedent as the marker itself.
            ((ServerLevel) player.level()).sendParticles(player, ParticleTypes.SPLASH, false, false,
                    hook.getX(), hook.getY(), hook.getZ(), 2, 0.15, 0.0, 0.15, 0.0);
        }
    }

    private BlockPos pickLuckySpot(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = level.getRandom();
        BlockPos origin = player.blockPosition();
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = random.nextInt(FishingConstants.LUCKY_SPOT_RADIUS * 2 + 1) - FishingConstants.LUCKY_SPOT_RADIUS;
            int dz = random.nextInt(FishingConstants.LUCKY_SPOT_RADIUS * 2 + 1) - FishingConstants.LUCKY_SPOT_RADIUS;
            BlockPos candidate = origin.offset(dx, 0, dz);
            BlockPos surface = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, candidate);
            BlockPos waterPos = surface.below();
            if (level.getFluidState(waterPos).is(FluidTags.WATER) && level.getBlockState(surface).isAir()) {
                return waterPos;
            }
        }
        return null;
    }

    private void handleVoidFishing(ServerPlayer player) {
        Level level = player.level();
        Optional<List<Item>> loot = FishLootPools.voidCatchFor(level);
        if (loot.isEmpty()) {
            return;
        }
        UUID playerId = player.getUUID();
        boolean usingRod = player.isUsingItem()
                && player.getUseItem().is(Items.FISHING_ROD);
        if (!usingRod) {
            voidCatchTimer.remove(playerId);
            return;
        }

        RandomSource random = level.getRandom();
        int timer = voidCatchTimer.computeIfAbsent(playerId, k ->
                FishingConstants.VOID_CATCH_MIN_TICKS + random.nextInt(
                        FishingConstants.VOID_CATCH_MAX_TICKS - FishingConstants.VOID_CATCH_MIN_TICKS + 1));
        timer--;
        if (timer > 0) {
            voidCatchTimer.put(playerId, timer);
            return;
        }
        voidCatchTimer.remove(playerId);

        List<Item> pool = loot.get();
        ItemStack caught = new ItemStack(pool.get(random.nextInt(pool.size())));
        if (!player.getInventory().add(caught)) {
            player.drop(caught, false);
        }
        Lyfe.addXp(player, Skills.FISHERMAN_ID, FishingConstants.BASE_CATCH_XP);
        player.stopUsingItem();
    }

    public static double bonusLootChance(int level) {
        return FishingConstants.MAX_BONUS_LOOT_CHANCE * level / Skills.MAX_LEVEL;
    }

    public static double sunkenTreasureChance(int level) {
        if (level < FishingConstants.SUNKEN_TREASURE_MIN_LEVEL) {
            return FishingConstants.SUNKEN_TREASURE_BASE_CHANCE;
        }
        int steps = (level - FishingConstants.SUNKEN_TREASURE_MIN_LEVEL) / FishingConstants.SUNKEN_TREASURE_LEVEL_STEP;
        return FishingConstants.SUNKEN_TREASURE_BASE_CHANCE + steps * FishingConstants.SUNKEN_TREASURE_STEP_BONUS;
    }

    public static List<String> benefitLines(int level) {
        List<String> lines = new ArrayList<>();
        lines.add("Bonus loot chance on catch: " + String.format(Locale.ROOT, "%.1f", bonusLootChance(level) * 100.0) + "%");
        if (level >= FishingConstants.SUNKEN_TREASURE_MIN_LEVEL) {
            lines.add("Sunken Treasure chance (deep water): " + String.format(Locale.ROOT, "%.1f", sunkenTreasureChance(level) * 100.0) + "%");
        } else {
            lines.add("Sunken Treasure unlocks at level " + FishingConstants.SUNKEN_TREASURE_MIN_LEVEL);
        }
        lines.add("Lucky spot bonus: +" + String.format(Locale.ROOT, "%.0f", FishingConstants.LUCKY_SPOT_BONUS_LOOT_BUMP * 100.0)
                + "% bonus-loot chance, bigger fish");
        return lines;
    }
}
