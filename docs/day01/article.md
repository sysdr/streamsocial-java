# Day 1: Event-Driven Architecture Fundamentals

*Kafka Mastery: Building StreamSocial — Java & Spring Boot Edition, Module 1: Foundation & Core Concepts (Days 1–10)*

## What we'll build today

- The `streamsocial-common` module: the very first piece of the repository every remaining 59 lessons will build on top of
- A sealed `DomainEvent` interface with three permitted subtypes: `UserActionEvent`, `ContentInteractionEvent`, `SystemEvent`
- Ten concrete event types across those three subtypes — the complete taxonomy StreamSocial runs on
- Bean Validation rules that reject a malformed event before it ever gets near a Kafka producer

No broker exists yet. That's deliberate — Day 2 stands up the cluster, but a Kafka topic is only as good as the event shape flowing through it, and that shape is today's entire job.

[DIAGRAM: component-architecture]

## Why request-response stops working at StreamSocial's scale

A typical CRUD app answers questions synchronously: a client asks "did the like succeed," the server checks a database, and the client waits for the answer before doing anything else. That works fine when one service owns one piece of state and nobody else needs to know about the change.

StreamSocial doesn't have that luxury. A single like has to update an engagement counter, feed a trending-hashtag calculation, possibly trigger a notification, and eventually influence a recommendation model — four different concerns, none of which should block the user's tap from returning instantly. Wiring the like-handling service to call all four downstream services directly means every one of those services has to be up, fast, and backward-compatible at the exact moment a like happens. That's the coupling event-driven architecture exists to remove: producers publish a fact once, and however many consumers care about that fact — today or six months from now — read it on their own schedule.

This is the actual reason LinkedIn built Kafka in the first place: not to add messaging for its own sake, but to decouple "the like happened" from "everyone who needs to know about the like."

## The event is the contract

Once you decouple producers from consumers, the event itself becomes the thing every team has to agree on — not an API signature, not a shared database schema, the event. Get the event shape wrong and you'll be renegotiating it with every downstream team for the rest of the course. That's why this lesson exists before Day 2's cluster: the taxonomy has to be right before there's anywhere to publish it.

[DIAGRAM: flowchart]

StreamSocial's taxonomy splits into exactly three categories, and the split isn't cosmetic — it maps directly onto how Day 3 partitions topics and how Day 57's microservices subscribe selectively:

- **`UserActionEvent`** — something a user deliberately did: `POST_CREATED`, `POST_DELETED`, `USER_FOLLOWED`, `USER_UNFOLLOWED`, `PROFILE_UPDATED`. Volume scales with active users.
- **`ContentInteractionEvent`** — a user reacting to someone else's content: `CONTENT_LIKED`, `CONTENT_COMMENTED`, `CONTENT_SHARED`. Volume scales with content virality, which is why it gets its own topic and its own partition count starting Day 3, not a shared one with user actions.
- **`SystemEvent`** — something StreamSocial's own backend decided: `CONTENT_MODERATION_FLAGGED`, `RECOMMENDATION_MODEL_UPDATED`. No single user triggered it directly.

That's 10 event types. In Java, this taxonomy isn't just documentation — it's a `sealed interface`:

```java
public sealed interface DomainEvent
        permits UserActionEvent, ContentInteractionEvent, SystemEvent {
    UUID eventId();
    Instant occurredAt();
    String eventType();
}
```

`sealed` is doing real work here, not just looking tidy. Any code written against `DomainEvent` — including code Day 44 or Day 57 hasn't been written yet — gets a compiler error the moment someone adds a fourth category without updating every `switch` that handles all three. That's a cheap, permanent guardrail against taxonomy drift, which is the kind of thing that quietly rots real production event catalogs over a year of feature work.

Each subtype is a Java record, which is the right tool here: an event is a fact that already happened, so it should be immutable data with no behavior beyond deriving its own `eventType()`.

## Where validation earns its keep

An event that fails validation and never gets published is cheap. An event that gets published with a blank `userId` and gets consumed by six different services before anyone notices is expensive — you're now cleaning up bad data in every downstream system, not just the one that produced it. Bean Validation annotations (`@NotBlank`, `@NotNull`, `@PastOrPresent`) catch the malformed event at construction time, in the one place, before Day 4's producer ever touches it.

[DIAGRAM: state-machine]

An event moves through exactly three states before it's anywhere near Kafka: **Created** (the record exists in memory), **Validating** (Bean Validation runs against it), and then either **Valid** — ready for Day 4's producer to pick up — or **Rejected**, which today just means "the constructor threw a validation error" and starting Day 25 means "routed to a dead letter topic instead of silently dropped."

## Success criteria for today

You've got this lesson if `mvn -pl streamsocial-common -am verify` passes with all 8 tests green, the demo prints all 10 event types grouped correctly by category, and — this is the part that actually proves you understand the taxonomy, not just that the code compiles — you can explain in one sentence why `ContentInteractionEvent` needed its own category instead of being folded into `UserActionEvent`.

Tomorrow: a real 3-broker Kafka cluster in KRaft mode, so these events finally have somewhere to go.
