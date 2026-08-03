package com.streamsocial.dashboard.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One broadcaster instance per dashboard panel that needs a live feed -
 * keyed by stream name so every lesson from Day 4 on can register its
 * own panel without touching what earlier lessons already wired up.
 * Panels accumulate; nothing here ever removes an earlier stream name.
 */
@Component
public class EventStreamBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(EventStreamBroadcaster.class);
    private static final long EMITTER_TIMEOUT_MS = 30 * 60 * 1000L; // 30 minutes

    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emittersByStream = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String streamName) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        CopyOnWriteArrayList<SseEmitter> emitters =
                emittersByStream.computeIfAbsent(streamName, key -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        Runnable cleanup = () -> emitters.remove(emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(throwable -> cleanup.run());

        return emitter;
    }

    public void broadcast(String streamName, String eventName, Object payload) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByStream.get(streamName);
        if (emitters == null || emitters.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (Exception e) {
                // ClientAbort / broken pipe often wraps as a runtime exception —
                // never let a dead browser tab fail the Kafka listener thread.
                emitters.remove(emitter);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // already dead
                }
                log.debug("Removed a dead SSE emitter for stream {}: {}", streamName, e.toString());
            }
        }
    }
}
