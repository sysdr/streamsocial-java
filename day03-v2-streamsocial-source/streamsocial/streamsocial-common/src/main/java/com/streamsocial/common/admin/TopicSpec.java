package com.streamsocial.common.admin;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * A desired topic definition: what {@link TopicBootstrapper} tries to make
 * true on the cluster, idempotently.
 *
 * @param name               topic name
 * @param partitions         partition count - see {@link StreamSocialTopics}
 *                           for the throughput math and the local-demo-scale
 *                           override
 * @param replicationFactor  must be 3 for every topic on this cluster;
 *                           matches the 3-broker cluster from Day 2
 * @param configs            topic-level config overrides (retention,
 *                           cleanup policy, etc.)
 */
public record TopicSpec(
        @NotBlank String name,
        @Min(1) int partitions,
        @Min(1) short replicationFactor,
        Map<String, String> configs
) {
}
