package com.github.cerealklla.lyfe.socket;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Persisted per-{@code ItemStack} socket state (explicit user spec: "up to 5 sockets," "the progress
 * of the socket currently being added is stored on the item"). {@code sockets} is ordered -- index 0
 * is the first socket ever drilled, which matters twice over: {@link SocketeerConstants}' escalating
 * difficulty is keyed to "which socket number is this," and Extracting always removes in LIFO order
 * (explicit user spec), i.e. the highest-index filled socket first.
 */
public record SocketData(List<SocketSlot> sockets, int progressTowardNextSocket) {

    public static final int MAX_SOCKETS = 5;
    public static final SocketData EMPTY = new SocketData(List.of(), 0);

    public SocketData {
        sockets = List.copyOf(sockets);
        if (sockets.size() > MAX_SOCKETS) {
            throw new IllegalArgumentException("more than " + MAX_SOCKETS + " sockets");
        }
    }

    public static final Codec<SocketData> CODEC = RecordCodecBuilder.create(i -> i.group(
            SocketSlot.CODEC.listOf().fieldOf("sockets").forGetter(SocketData::sockets),
            Codec.INT.fieldOf("progress").forGetter(SocketData::progressTowardNextSocket)
    ).apply(i, SocketData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SocketData> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public int socketCount() {
        return sockets.size();
    }

    public boolean isFull() {
        return sockets.size() >= MAX_SOCKETS;
    }

    /** Index into {@code sockets} of the first {@link SocketSlot.Kind#EMPTY} one, or {@code -1} if none/no sockets exist. */
    public int firstEmptyIndex() {
        for (int i = 0; i < sockets.size(); i++) {
            if (sockets.get(i).kind() == SocketSlot.Kind.EMPTY) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the highest-index filled (non-empty) socket -- extraction's LIFO target (explicit user spec) -- or {@code -1} if none. */
    public int lastFilledIndex() {
        for (int i = sockets.size() - 1; i >= 0; i--) {
            if (sockets.get(i).kind() != SocketSlot.Kind.EMPTY) {
                return i;
            }
        }
        return -1;
    }

    /** {@code sockets} with a brand-new {@link SocketSlot#EMPTY} appended -- the result of a completed drilling attempt. */
    public SocketData withNewEmptySocket() {
        List<SocketSlot> next = new ArrayList<>(sockets);
        next.add(SocketSlot.EMPTY);
        return new SocketData(next, 0);
    }

    public SocketData withProgress(int progress) {
        return new SocketData(sockets, progress);
    }

    public SocketData withSlot(int index, SocketSlot slot) {
        List<SocketSlot> next = new ArrayList<>(sockets);
        next.set(index, slot);
        return new SocketData(next, progressTowardNextSocket);
    }

    /**
     * {@code sockets} with the socket at {@code index} reset to {@link SocketSlot#EMPTY} -- used by
     * every Extracting outcome (success/degrade/destroy all remove the gem/rune from the socket one
     * way or another; only the socket's own drilled-hole investment survives, ready to be refilled).
     */
    public SocketData withSlotEmptied(int index) {
        return withSlot(index, SocketSlot.EMPTY);
    }

    // "The presence of empty sockets on a piece of equipment increases the value baseline by an
    // escalating amount based on the number of sockets; from 1 -> 5 sockets, the value should go
    // from base + 20 -> base + 200" (explicit user spec, exact endpoints given, escalation shape
    // filled in here as evenly-growing +20/+30/+40/+50/+60 increments per socket -- the simplest
    // curve that hits both given endpoints exactly while still genuinely escalating).
    private static final int[] VALUE_BONUS_BY_SOCKET_COUNT = {0, 20, 50, 90, 140, 200};

    /** The flat value bonus {@code api.Yconomics}-style pricing should add for having {@code socketCount} sockets (0 for an unsocketed item). */
    public static int valueBonusForSocketCount(int socketCount) {
        int clamped = Math.max(0, Math.min(socketCount, MAX_SOCKETS));
        return VALUE_BONUS_BY_SOCKET_COUNT[clamped];
    }
}
