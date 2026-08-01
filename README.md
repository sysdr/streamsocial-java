## [Course Link](https://handsonkafka.substack.com/)
## Introduction
------------

Event-driven systems are one of the most deceptively difficult problems in backend engineering. Every non-trivial platform eventually needs to move data between services in real time, fan events out to dozens of downstream consumers, or guarantee that a payment event is processed exactly once — and the moment a system moves past a single-broker tutorial setup, the simple producer-and-consumer pattern that worked in a five-minute quickstart starts causing duplicate processing, out-of-order writes, and silent data loss in production.

This course was built to address that exact gap. It takes learners from a three-broker Kafka cluster and a first producer all the way to a secured, observable, production-hardened event-driven platform — the kind of system that underlies real-time feeds, activity streams, and event backbones at companies operating at genuine scale. Rather than treating Kafka as a message queue with extra configuration, this program treats it as a complete systems-design subject, covering producer and consumer internals, schema management, Kafka Connect, Kafka Streams, observability, and security along the way.

Hands On Kafka is a reader-supported publication. To receive new posts and support my work, consider becoming a free or paid subscriber.

The material is organized as a 60-day publishing plan, with each day representing a focused lesson that builds directly on the one before it, inside a single continuously growing codebase. Java 17 and Spring Boot are the implementation language throughout — the JVM-native, production-standard way real teams actually run Kafka clients — but Kafka itself is the subject of every lesson. By the end, learners will have designed, coded, and operated a distributed, event-driven social platform called StreamSocial, comparable in shape to the real-time backbones behind large-scale social and activity-feed systems, built from first principles, one architectural decision at a time.

## Why This Course Matters :
-------------------------

Event-driven architecture sits at the center of modern backend systems. Activity feeds, engagement and analytics pipelines, fraud and trust signals, recommendation inputs, and cross-service communication all depend on a messaging backbone that can move enormous volumes of data without losing ordering, duplicating writes, or falling over during a rebalance. As companies move from request-response APIs to event-driven architectures, a naive producer-consumer setup becomes a liability rather than a convenience, and engineers are expected to understand why.

## Common challenges learners face:
--------------------------------

Most Java developers know how to call send() on a KafkaTemplate, but far fewer understand what happens when that producer retries under network failure, or when a consumer group rebalances mid-batch. Typical pain points include:

*   Duplicate or lost messages when producers retry after a transient failure
    
*   No clear strategy for partition count, keys, or ordering guarantees at scale
    
*   Consumers that silently drop records during a rebalance or crash mid-commit
    
*   Schemas that break downstream consumers the moment a field changes
    
*   Lack of production readiness — no security, no observability, no plan for connecting Kafka to the rest of the data platform
    

## Skills addressed by the course :
--------------------------------

The curriculum directly targets these gaps by teaching producer reliability (idempotence, transactions, acknowledgment strategies, custom partitioning), consumer resilience (manual commits, rebalance listeners, dead letter queues), schema management (JSON Schema, Avro, Protobuf, and Schema Registry compatibility modes), data integration (Kafka Connect, custom connectors, Debezium-based CDC), stream processing (the Kafka Streams DSL and the low-level Processor API), and full production operations (observability, SASL/TLS/ACL security, and a real microservices split).

## Learning Outcomes
-----------------

By the end of the course, learners will be able to:

*   Explain why toy, single-broker Kafka setups fail in production and design around the failure modes
    
*   Implement idempotent and transactional producers for exactly-once write guarantees across topics
    
*   Design partition count, key, and custom partitioning strategy for ordering and throughput at a defined scale target
    
*   Build resilient consumers using manual offset commits, cooperative rebalance listeners, and dead letter topics for poison-pill handling
    
*   Apply Avro and Protobuf serialization with Confluent Schema Registry, and manage schema evolution under forward, backward, and full compatibility
    
*   Build custom Kafka Connect source and sink connectors, chain Single Message Transformations, and stream database changes in real time with Debezium
    
*   Build Kafka Streams topologies — stateless transformations, windowed aggregations, KTable materialized views, stream-table and table-table joins, interactive queries, and the low-level Processor API
    
*   Instrument a Kafka-based system with Micrometer, Prometheus, and Grafana, and ship structured logs across every service
    
*   Secure a cluster end-to-end with SASL/SCRAM authentication, least-privilege ACLs, and cluster-wide TLS
    
*   Split a single growing codebase into independently deployable microservices that communicate purely through Kafka topics
    

## Practical Applications
----------------------

The concepts in this course map directly onto real engineering work. Producer and consumer reliability patterns apply to any system moving data through a message broker — not just Kafka, but any queue-backed pipeline that needs to avoid duplication and data loss. The schema-management arc in Module 3 is directly transferable to any service boundary where data contracts need to evolve without breaking consumers. The Connect and CDC patterns in Module 4 are the same techniques used to build real-time data lakes, analytics pipelines, and cross-database synchronization in production systems. The stream-processing work in Module 5 mirrors what real-time analytics and fraud-detection teams build, and the observability and security work in Module 6 is standard practice for operating any Kafka-backed platform in production.

Taken together, the completed project functions as a credible portfolio piece for backend, platform, or data infrastructure engineering roles, and the individual modules serve as standalone references that can be revisited whenever a related problem — schema evolution, exactly-once processing, connector development — comes up in professional work.

## Conclusion
----------

Event-driven systems sit at the intersection of several of the hardest problems in backend engineering — ordering, delivery guarantees, schema evolution, and operational trust — and this course was designed to teach all of them through the lens of a single, coherent, incrementally built system. Rather than presenting isolated Kafka features in the abstract, each of the 60 lessons adds a working piece to the same architecture, so that by the end, the concepts of producer reliability, consumer correctness, stream processing, and production security are not just understood but implemented.

For engineers looking to move beyond basic Kafka familiarity into genuine systems-design capability, this curriculum offers a structured, thorough, and practically grounded path. The full lesson plan and published content are available through Hands On Kafka on Substack, with new lessons rolling out according to the course schedule.
