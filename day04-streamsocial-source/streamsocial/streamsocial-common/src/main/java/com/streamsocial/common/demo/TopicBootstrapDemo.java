package com.streamsocial.common.demo;

import com.streamsocial.common.admin.StreamSocialTopics;
import com.streamsocial.common.admin.TopicBootstrapper;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;

import java.util.Map;

/**
 * Day 3 demo. Connects to the Day 2 cluster and idempotently creates
 * {@code user-actions} and {@code content-interactions}. Run it twice in a
 * row - the second run should report both topics already present and
 * verified, and create nothing.
 *
 * <p>Run with (cluster from Day 2 must already be running):
 * {@code mvn -q -pl streamsocial-common -am exec:java
 *   -Dexec.mainClass=com.streamsocial.common.demo.TopicBootstrapDemo}
 */
public final class TopicBootstrapDemo {

    private static final String BOOTSTRAP_SERVERS =
            "localhost:29092,localhost:29093,localhost:29094";

    public static void main(String[] args) throws Exception {
        Map<String, Object> config = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);

        try (Admin adminClient = Admin.create(config)) {
            TopicBootstrapper bootstrapper = new TopicBootstrapper(adminClient);
            TopicBootstrapper.BootstrapResult result = bootstrapper.bootstrap(StreamSocialTopics.ALL);

            System.out.println("StreamSocial topic bootstrap against " + BOOTSTRAP_SERVERS);
            System.out.println("-".repeat(60));
            System.out.println("Created:            " + result.created());
            System.out.println("Already present ok:  " + result.alreadyPresentVerified());
            System.out.println("Mismatched:          " + result.mismatched());

            if (result.hasMismatches()) {
                System.out.println();
                System.out.println("One or more topics exist with an unexpected partition count.");
                System.out.println("This needs a human decision - see docs/day03/guide.md.");
                System.exit(1);
            }

            System.out.println();
            System.out.println("Run this again right now - it should create nothing the second time.");
        }
    }

    private TopicBootstrapDemo() {
    }
}
