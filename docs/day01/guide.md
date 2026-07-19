# Day 1 Guide: Build, Test, and Demo the Event Taxonomy

*Companion to Day 1: Event-Driven Architecture Fundamentals. Full source is in `day01-streamsocial-source.zip`; this guide walks you through what's in it, not the code itself.*

## The idea, in pseudo-code

Before touching Java, sketch the taxonomy as pseudo-code — this is the design step that actually matters, the syntax is secondary:

```
sealed DomainEvent:
    eventId, occurredAt, eventType()

    UserActionEvent(userId, actionType, targetId)
        actionType in {POST_CREATED, POST_DELETED, USER_FOLLOWED,
                        USER_UNFOLLOWED, PROFILE_UPDATED}

    ContentInteractionEvent(userId, contentId, interactionType)
        interactionType in {CONTENT_LIKED, CONTENT_COMMENTED, CONTENT_SHARED}

    SystemEvent(systemEventType, subjectId, metadata)
        systemEventType in {CONTENT_MODERATION_FLAGGED,
                             RECOMMENDATION_MODEL_UPDATED}

validate(event):
    if any @NotNull/@NotBlank field is missing -> reject
    if occurredAt is in the future -> reject
    else -> valid, ready to publish (Day 4)
```

Three categories, ten leaf types, one validation gate. Everything in the zip is the Java expression of exactly this.

## Step 1 — Unpack and build

```bash
unzip day01-streamsocial-source.zip
cd streamsocial
mvn -q -pl streamsocial-common -am verify
```

**Expected output:** `BUILD SUCCESS`. Surefire reports `Tests run: 8, Failures: 0, Errors: 0` somewhere in the log for `DomainEventValidationTest`.

If this is your first build, Maven needs to pull `hibernate-validator`, `jakarta.validation-api`, `jakarta.el`, `junit-jupiter`, and `assertj-core` from Maven Central — that requires internet access on this run only; subsequent builds use your local `~/.m2` cache.

## Step 2 — Run the demo

```bash
mvn -q -pl streamsocial-common -am exec:java \
  -Dexec.mainClass=com.streamsocial.common.demo.EventTaxonomyDemo
```

**Expected output**, in order:

1. All 10 event types printed, grouped under `UserActionEvent:`, `ContentInteractionEvent:`, `SystemEvent:` headers.
2. Three constructed events (one per category) printed with their class name, `eventType()`, and generated `eventId`.
3. A malformed `UserActionEvent` (blank `userId`) run through a `Validator`, with output like:
   ```
   rejected: userId must not be blank
   ```

If step 3 prints "unexpectedly valid," the `@NotBlank` annotation isn't being picked up — check that `hibernate-validator` resolved correctly in step 1's build log.

## Step 3 — Read the sealed switch

Open `DomainEventValidationTest.java` and find `domainEventSealedHierarchyExhaustiveSwitchCompiles`. Delete the `SystemEvent` branch and try to build again:

```bash
mvn -q -pl streamsocial-common -am test-compile
```

**Expected output:** a compiler error — an unexhaustive `switch` over a sealed type without a `default`. Put the branch back and rebuild to confirm it's green again. This is the guardrail in action, not a bug.

## Homework

Extend the taxonomy with an eleventh event type: `USER_BLOCKED`, fired when one user blocks another. Decide which category it belongs in — `UserActionEvent` or `SystemEvent` — and justify the choice in a one-line code comment before you write any code.

Implementation checklist:
1. Add the enum constant to the correct existing type enum (don't create a fourth `DomainEvent` subtype — the sealed hierarchy stays at three).
2. Add a matching `EventTaxonomy.CatalogEntry` row.
3. Add a JUnit test asserting the catalog size grew to 11 and that a `UserActionEvent` (or `SystemEvent`) constructed with the new type passes validation.
4. Re-run `mvn -q -pl streamsocial-common -am verify` and confirm everything is still green.

## Solution hints

- `USER_BLOCKED` belongs in `UserActionEvent`: it's a direct, deliberate action one user takes against another, same shape as `USER_FOLLOWED`/`USER_UNFOLLOWED` — reuse `targetId` for the blocked user's id, don't add a new field.
- You do not need a new record or a new permitted subtype. If your solution touches `DomainEvent.java`, you've over-built it — that file shouldn't need to change today.
- `EventTaxonomy.ALL` is a `List.of(...)` — appending is a one-line addition, not a restructure.
- The test that currently asserts `hasSize(10)` needs to become `hasSize(11)` — that's expected and correct, not a sign something broke.
