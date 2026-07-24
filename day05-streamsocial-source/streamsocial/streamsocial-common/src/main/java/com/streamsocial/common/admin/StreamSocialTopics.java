package com.streamsocial.common.admin;

import java.util.List;
import java.util.Map;

/**
 * The two topics StreamSocial's cluster carries starting today, and the
 * throughput math behind each partition count.
 *
 * <h2>The math</h2>
 * StreamSocial's platform-wide peak target is 50M events/second across
 * every topic combined. Today's two topics split that target like this:
 *
 * <ul>
 *   <li><b>user-actions</b> - assumed peak 30M events/sec (posts, follows,
 *       profile updates). High key cardinality: traffic spreads evenly
 *       across millions of distinct {@code userId} keys, so raw partition
 *       count translates almost directly into usable parallelism.</li>
 *   <li><b>content-interactions</b> - assumed peak 20M events/sec (likes,
 *       comments, shares). Lower <i>effective</i> parallelism: interaction
 *       volume follows a power law toward viral content, so a handful of
 *       {@code contentId} keys absorb a disproportionate share of traffic
 *       no matter how many partitions exist.</li>
 * </ul>
 *
 * Using a conservative sustained-throughput budget of ~30,000 events/sec
 * per partition (small JSON events, replication factor 3, headroom for
 * consumer fan-out):
 *
 * <pre>
 * user-actions:          30,000,000 / 30,000  ≈ 1000 partitions
 * content-interactions:  20,000,000 / 30,000  ≈  667 → capped at 500
 * </pre>
 *
 * <p>The content-interactions cap is deliberate, not rounding. Partitions
 * beyond ~500 mostly sit idle for this topic - a handful of partitions
 * holding viral-content keys stay hot regardless of total partition count,
 * so throwing more partitions at the topic doesn't fix the actual
 * bottleneck. The real fix for that skew is a smarter partitioning
 * strategy (Day 15's custom {@code Partitioner}, and later hot-key
 * salting), not a bigger partition count.
 */
public final class StreamSocialTopics {

    public static final TopicSpec USER_ACTIONS = new TopicSpec(
            "user-actions",
            1000,
            (short) 3,
            Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000), // 7 days
                    "min.insync.replicas", "2"
            )
    );

    public static final TopicSpec CONTENT_INTERACTIONS = new TopicSpec(
            "content-interactions",
            500,
            (short) 3,
            Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000), // 7 days
                    "min.insync.replicas", "2"
            )
    );

    public static final List<TopicSpec> ALL = List.of(USER_ACTIONS, CONTENT_INTERACTIONS);

    private StreamSocialTopics() {
        // catalog holder, not instantiable
    }
}
