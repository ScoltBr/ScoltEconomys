package me.scoltbr.scoltEconomys.util;

import java.util.concurrent.locks.ReentrantLock;

public class StripedLocks<T> {

    private final ReentrantLock[] locks;
    private final int mask;

    public StripedLocks(int stripes) {
        int size = tableSizeFor(stripes);
        this.locks = new ReentrantLock[size];
        for (int i = 0; i < size; i++) locks[i] = new ReentrantLock();
        this.mask = size - 1;
    }

    public ReentrantLock lockFor(T key) {
        int h = smear(key.hashCode());
        return locks[h & mask];
    }

    private static int smear(int hashCode) {
        int h = hashCode;
        h ^= (h >>> 16);
        h *= 0x7feb352d;
        h ^= (h >>> 15);
        h *= 0x846ca68b;
        h ^= (h >>> 16);
        return h;
    }

    private static int tableSizeFor(int n) {
        int cap = 1;
        while (cap < n) cap <<= 1;
        return Math.max(2, cap);
    }
}