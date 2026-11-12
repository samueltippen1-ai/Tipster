package dev.randomised.dispensertest;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Hand-off between the client thread (which computes outcomes) and the integrated server thread
 * (which actually dispenses). Entries are one-shot and expire quickly, so nothing lingers.
 * Only ever populated in single-player; on a remote server nothing is armed.
 */
public final class ForcedSlots {

    private record Entry(int slot, long expiresAtNanos) {}

    private static final ConcurrentHashMap<BlockKey, Entry> MAP = new ConcurrentHashMap<>();

    private ForcedSlots() {}

    public static void arm(BlockKey key, int slot, long ttlMillis) {
        MAP.put(key, new Entry(slot, System.nanoTime() + ttlMillis * 1_000_000L));
    }

    /** Called from the server thread. Returns the forced slot once, or -1 if none/expired. */
    public static int consume(BlockKey key) {
        if (MAP.isEmpty()) return -1; // fast path for every normal dispenser in the world
        Entry e = MAP.remove(key);
        if (e == null || System.nanoTime() > e.expiresAtNanos()) return -1;
        return e.slot();
    }

    public static void clear() {
        MAP.clear();
    }
}
