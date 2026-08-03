package com.streamsocial.common.admin;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.errors.TopicExistsException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Makes the cluster's topics match a desired catalog, idempotently -
 * Appendix C's proven shape:
 *
 * <pre>
 * existing = admin.listTopics().names().get()
 * toCreate = specs.filter(spec -> !existing.contains(spec.name))
 * if toCreate not empty: admin.createTopics(toCreate).all().get()
 * </pre>
 *
 * <p>Extended one step further here: topics that already exist get their
 * partition count verified against the desired spec, not just assumed
 * correct. Kafka won't let you shrink partitions at all, and growing
 * them later changes which partition a given key hashes to for every
 * message already flowing through the topic - silently breaking
 * per-key ordering guarantees. A mismatch is reported, not
 * auto-corrected; that's a decision for a human.
 */
public final class TopicBootstrapper {

    private final Admin adminClient;

    public TopicBootstrapper(Admin adminClient) {
        this.adminClient = adminClient;
    }

    public BootstrapResult bootstrap(List<TopicSpec> desired) throws ExecutionException, InterruptedException {
        Set<String> existingNames = adminClient.listTopics().names().get();

        List<TopicSpec> missing = desired.stream()
                .filter(spec -> !existingNames.contains(spec.name()))
                .toList();
        List<TopicSpec> present = desired.stream()
                .filter(spec -> existingNames.contains(spec.name()))
                .toList();

        List<String> created = createMissing(missing);
        VerificationResult verification = verifyPresent(present);

        return new BootstrapResult(
                List.copyOf(created),
                List.copyOf(verification.verified()),
                List.copyOf(verification.mismatched()));
    }

    private List<String> createMissing(List<TopicSpec> missing) throws ExecutionException, InterruptedException {
        List<String> created = new ArrayList<>();
        if (missing.isEmpty()) {
            return created;
        }

        List<NewTopic> newTopics = missing.stream()
                .map(spec -> new NewTopic(spec.name(), spec.partitions(), spec.replicationFactor())
                        .configs(spec.configs()))
                .toList();

        try {
            adminClient.createTopics(newTopics).all().get();
            missing.forEach(spec -> created.add(spec.name()));
        } catch (ExecutionException e) {
            if (e.getCause() instanceof TopicExistsException) {
                // Another bootstrapper won a race between our listTopics()
                // call and this createTopics() call - not an error, the
                // desired end state (topic exists) is already true.
            } else {
                throw e;
            }
        }
        return created;
    }

    private VerificationResult verifyPresent(List<TopicSpec> present) throws ExecutionException, InterruptedException {
        List<String> verified = new ArrayList<>();
        List<String> mismatched = new ArrayList<>();
        if (present.isEmpty()) {
            return new VerificationResult(verified, mismatched);
        }

        List<String> names = present.stream().map(TopicSpec::name).toList();
        Map<String, TopicDescription> descriptions =
                adminClient.describeTopics(names).allTopicNames().get();

        for (TopicSpec spec : present) {
            TopicDescription description = descriptions.get(spec.name());
            int actualPartitions = description.partitions().size();
            if (actualPartitions == spec.partitions()) {
                verified.add(spec.name());
            } else {
                mismatched.add("%s (expected %d partitions, found %d)"
                        .formatted(spec.name(), spec.partitions(), actualPartitions));
            }
        }
        return new VerificationResult(verified, mismatched);
    }

    private record VerificationResult(List<String> verified, List<String> mismatched) {
    }

    public record BootstrapResult(
            List<String> created,
            List<String> alreadyPresentVerified,
            List<String> mismatched) {

        public boolean hasMismatches() {
            return !mismatched.isEmpty();
        }
    }
}
