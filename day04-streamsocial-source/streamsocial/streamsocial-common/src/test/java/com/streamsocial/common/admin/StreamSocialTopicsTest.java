package com.streamsocial.common.admin;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class StreamSocialTopicsTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    void catalogContainsExactlyTwoTopics() {
        assertThat(StreamSocialTopics.ALL).hasSize(2);
    }

    @Test
    void userActionsHasOneThousandPartitions() {
        assertThat(StreamSocialTopics.USER_ACTIONS.partitions()).isEqualTo(1000);
    }

    @Test
    void contentInteractionsHasFiveHundredPartitions() {
        assertThat(StreamSocialTopics.CONTENT_INTERACTIONS.partitions()).isEqualTo(500);
    }

    @Test
    void bothTopicsReplicateAcrossAllThreeBrokers() {
        assertThat(StreamSocialTopics.ALL)
                .extracting(TopicSpec::replicationFactor)
                .allMatch(rf -> rf == 3);
    }

    @Test
    void catalogEntriesPassValidation() {
        for (TopicSpec spec : StreamSocialTopics.ALL) {
            Set<ConstraintViolation<TopicSpec>> violations = validator.validate(spec);
            assertThat(violations).as("violations for " + spec.name()).isEmpty();
        }
    }

    @Test
    void topicSpecRejectsBlankName() {
        TopicSpec spec = new TopicSpec("", 10, (short) 3, Map.of());

        Set<ConstraintViolation<TopicSpec>> violations = validator.validate(spec);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("name");
    }

    @Test
    void topicSpecRejectsZeroPartitions() {
        TopicSpec spec = new TopicSpec("test-topic", 0, (short) 3, Map.of());

        Set<ConstraintViolation<TopicSpec>> violations = validator.validate(spec);

        assertThat(violations)
                .extracting(ConstraintViolation::getPropertyPath)
                .extracting(Object::toString)
                .contains("partitions");
    }
}
