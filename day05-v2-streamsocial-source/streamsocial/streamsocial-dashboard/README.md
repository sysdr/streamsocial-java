# streamsocial-dashboard

Day 4: the live, browser-viewable dashboard every lesson with
observable behavior extends from here on. No JS framework — static
HTML/CSS/vanilla JS, pushed live via Server-Sent Events backed by this
module's own Kafka consumers.

## Why the dashboard has its own consumers, not a shared one

`UserActionsFeedListener` reads `user-actions` in its own consumer
group (`dashboard-live-feed`), completely separate from any business
consumer group. It exists purely to observe — if it falls behind or
restarts, it just picks up from `latest` and starts watching again,
rather than replaying history the way a real business consumer would
need to. It never competes for partitions with `feed-service` or
anything else that actually needs to process every record.

## Prerequisites

- The Day 2 cluster running with Day 3's topics provisioned

## Run it

```bash
mvn -pl streamsocial-dashboard -am spring-boot:run
```

Open **http://localhost:8080** — you'll see one panel, `user-actions`,
showing 0 events until something publishes.

## Watch it live

With the dashboard running, publish events through Day 4's producer:

```bash
curl -X POST http://localhost:8082/api/v1/actions \
  -H 'Content-Type: application/json' \
  -d '{"userId":"user-42","actionType":"POST_CREATED","targetId":"post-1"}'
```

**Expected:** within a second, a new row appears at the top of the
`user-actions` panel's feed — `POST_CREATED · user-42 · p<N>@<offset>`
— and the event counter increments. This is the real event you just
posted, read back off the real topic, not a simulated row.

**Day 5 adds a second panel**, `content-interactions`, fed by
`ContentInteractionsFeedListener` (its own consumer group,
`dashboard-live-feed-content-interactions`). Run
`streamsocial-engagement-consumer`'s test-data generator and watch the
same 14 events appear here live, alongside — not instead of — what
Day 5's own consumer is independently processing.

## Tests

```bash
mvn -pl streamsocial-dashboard -am verify
```

`DashboardPageSmokeTest` (Surefire, no Docker) asserts the index page
renders both panels' real markup. `UserActionsFeedBroadcastIT` and
`ContentInteractionsFeedBroadcastIT` (Failsafe, need Docker) each
publish a real event to a real Testcontainers broker, consume it for
real, and assert the correct listener broadcasts the correct real
payload.

## What's next

Day 6 scales `feed-service`-style consumers with consumer groups and
partition assignment — no new dashboard panel required unless that
lesson's own scaling demo needs one.
