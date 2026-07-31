package com.streamsocial.dashboard.consumer;

import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** Holds live counters the background consumer writes to and the SSE broadcaster reads from. */
@Component
public class ThroughputTracker {

    private static final int HISTORY_SIZE = 30;

    private final AtomicLong currentSecondCount = new AtomicLong();
    private final AtomicLong totalEvents = new AtomicLong();
    private final Deque<Long> history = new ArrayDeque<>(HISTORY_SIZE);

    public void recordEvent() {
        currentSecondCount.incrementAndGet();
        totalEvents.incrementAndGet();
    }

    public synchronized Snapshot tick() {
        long rate = currentSecondCount.getAndSet(0);
        history.addLast(rate);
        if (history.size() > HISTORY_SIZE) {
            history.removeFirst();
        }
        return new Snapshot(rate, totalEvents.get(), List.copyOf(history));
    }

    public record Snapshot(long eventsPerSecond, long totalEvents, List<Long> history) {
    }
}
