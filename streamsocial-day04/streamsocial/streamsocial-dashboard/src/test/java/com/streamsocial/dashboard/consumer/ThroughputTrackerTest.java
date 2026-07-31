package com.streamsocial.dashboard.consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThroughputTrackerTest {

    @Test
    void tickReportsEventsRecordedSinceLastTickThenResets() {
        ThroughputTracker tracker = new ThroughputTracker();

        tracker.recordEvent();
        tracker.recordEvent();
        tracker.recordEvent();

        ThroughputTracker.Snapshot first = tracker.tick();
        assertEquals(3, first.eventsPerSecond());
        assertEquals(3, first.totalEvents());

        ThroughputTracker.Snapshot second = tracker.tick();
        assertEquals(0, second.eventsPerSecond(), "rate must reset to zero after a tick with no new events");
        assertEquals(3, second.totalEvents(), "total is cumulative and must not reset");
    }

    @Test
    void historyIsCappedAtThirtySamples() {
        ThroughputTracker tracker = new ThroughputTracker();

        for (int i = 0; i < 40; i++) {
            tracker.recordEvent();
            tracker.tick();
        }

        ThroughputTracker.Snapshot snapshot = tracker.tick();
        assertTrue(snapshot.history().size() <= 30);
    }
}
