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
 * holding viral-content keys stay hot regardless of total partition count.
 * The real fix for that skew is a smarter partitioning strategy (Day 15's
 * custom {@code Partitioner}), not a bigger partition count.
 *
 * <h2>Local-demo-scale override</h2>
 * {@code 1000} and {@code 500} are this class's documented, real defaults
 * - faithful to the lesson, and what production would actually run. A
 * single 3-broker laptop cluster creating 1000 partitions for a five-
 * minute demo works but is needlessly slow to bring up and tear down.
 * Both counts can be overridden via system property for a fast local
 * check, preserving the same 2:1 ratio the real math produced:
 * {@code -Dstreamsocial.topics.user-actions-partitions=12
 *   -Dstreamsocial.topics.content-interactions-partitions=6}. The demo
 * output and this course's guide always say plainly when the smaller
 * numbers are in play - see {@code docs/day03/guide.md}.
 */
public final class StreamSocialTopics {

    private static final int USER_ACTIONS_PARTITIONS_DEFAULT = 1000;
    private static final int CONTENT_INTERACTIONS_PARTITIONS_DEFAULT = 500;

    private static int resolvePartitions(String propertyKey, int productionDefault) {
        return Integer.parseInt(System.getProperty(propertyKey, String.valueOf(productionDefault)));
    }

    public static final TopicSpec USER_ACTIONS = new TopicSpec(
            "user-actions",
            resolvePartitions("streamsocial.topics.user-actions-partitions", USER_ACTIONS_PARTITIONS_DEFAULT),
            (short) 3,
            Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000), // 7 days
                    "min.insync.replicas", "2"
            )
    );

    public static final TopicSpec CONTENT_INTERACTIONS = new TopicSpec(
            "content-interactions",
            resolvePartitions("streamsocial.topics.content-interactions-partitions", CONTENT_INTERACTIONS_PARTITIONS_DEFAULT),
            (short) 3,
            Map.of(
                    "cleanup.policy", "delete",
                    "retention.ms", String.valueOf(7L * 24 * 60 * 60 * 1000), // 7 days
                    "min.insync.replicas", "2"
            )
    );

    public static final List<TopicSpec> ALL = List.of(USER_ACTIONS, CONTENT_INTERACTIONS);

    /** True if either topic is running at a scaled-down local demo partition count today. */
    public static boolean isRunningAtLocalDemoScale() {
        return USER_ACTIONS.partitions() != USER_ACTIONS_PARTITIONS_DEFAULT
                || CONTENT_INTERACTIONS.partitions() != CONTENT_INTERACTIONS_PARTITIONS_DEFAULT;
    }

    private StreamSocialTopics() {
        // catalog holder, not instantiable
    }
}
