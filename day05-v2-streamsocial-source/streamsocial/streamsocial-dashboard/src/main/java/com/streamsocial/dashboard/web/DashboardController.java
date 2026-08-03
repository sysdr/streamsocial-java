package com.streamsocial.dashboard.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * One route per named stream, backed by {@link EventStreamBroadcaster}.
 * Day 4 registers {@code user-actions}; later lessons add their own
 * stream names here without touching this method's shape.
 */
@RestController
public class DashboardController {

    private final EventStreamBroadcaster broadcaster;

    public DashboardController(EventStreamBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    @GetMapping("/api/streams/{streamName}")
    public SseEmitter stream(@PathVariable("streamName") String streamName) {
        return broadcaster.subscribe(streamName);
    }
}
