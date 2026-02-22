package me.scoltbr.scoltEconomys.audit;

import java.util.ArrayList;
import java.util.List;

public final class RingBuffer<T> {

    private final Object[] data;
    private int index = 0;
    private int size = 0;

    public RingBuffer(int capacity) {
        if (capacity < 10) throw new IllegalArgumentException("capacity must be >= 10");
        this.data = new Object[capacity];
    }

    public synchronized void add(T value) {
        data[index] = value;
        index = (index + 1) % data.length;
        if (size < data.length) size++;
    }

    @SuppressWarnings("unchecked")
    public synchronized List<T> snapshot() {
        List<T> out = new ArrayList<>(size);
        int start = (index - size + data.length) % data.length;
        for (int i = 0; i < size; i++) {
            int pos = (start + i) % data.length;
            out.add((T) data[pos]);
        }
        return out;
    }

    public int capacity() { return data.length; }

    public synchronized int size() { return size; }
}