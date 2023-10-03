package com.swirlds.common.wiring.components;

public final class Event {
    private long number = -1; // We'll let the orphan buffer assign this, although I think consensus actually does
    private final byte[] data = new byte[1024 * 32]; // Just gotta have some bytes. Whatever.

    public Event() { }

    void reset(long number) {
        this.number = number;
    }

    @Override
    public String toString() {
        return "Event {number=" + number + "}";
    }

    public long number() {
        return number;
    }
}
