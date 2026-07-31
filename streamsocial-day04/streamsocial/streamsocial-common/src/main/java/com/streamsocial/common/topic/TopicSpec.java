package com.streamsocial.common.topic;

/** Declares the partition/replication shape a topic must have - not what data flows through it. */
public record TopicSpec(String name, int partitions, short replicationFactor) {
}
