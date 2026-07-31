package com.streamsocial.dashboard.web;

import com.streamsocial.dashboard.consumer.ThroughputTracker;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
public class DashboardStreamController {

    private final ThroughputTracker throughputTracker;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public DashboardStreamController(ThroughputTracker throughputTracker) {
        this.throughputTracker = throughputTracker;
    }

    @GetMapping(value = "/api/dashboard/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    @Scheduled(fixedRate = 1000)
    void broadcast() {
        ThroughputTracker.Snapshot snapshot = throughputTracker.tick();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(snapshot));
            } catch (IOException | IllegalStateException e) {
                emitters.remove(emitter);
            }
        }
    }
}
