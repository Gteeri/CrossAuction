# Architecture

## Source of truth: shared MySQL

All state that must be consistent network-wide lives in MySQL, in five tables (see `src/main/resources/schema.sql`):

- `ca_listings` - every buy-now/auction listing, its status, current bid, and optimistic `version` counter.
- `ca_claims` - an inbox of items/money owed to a player (sold payout, purchased item, expired/cancelled item return, outbid refund). Delivered lazily on join/GUI-collect so a player never loses anything even if they're offline or on a different server when a listing resolves.
- `ca_history` - a permanent record of how every listing was resolved (SOLD/EXPIRED/CANCELLED).
- `ca_balances` - only used when `economy.provider: INTERNAL`; a network-wide balance ledger.
- `ca_locks` - fallback distributed lock table if Redis is disabled.

## Concurrency safety across many servers

Every money/state-mutating repository call runs inside `DatabaseManager#transaction`, which:

1. Opens a JDBC transaction (autocommit off).
2. Locks the specific listing row with `SELECT ... FOR UPDATE`.
3. Re-validates status/type/ownership against the row it just locked (never trusts anything read earlier, e.g. from the GUI cache).
4. Commits (or rolls back on any exception).

Because the lock is a real MySQL row lock, if two servers race to buy the same listing at the same instant, MySQL simply serializes them: the second transaction blocks until the first commits, then sees `status <> 'ACTIVE'` and cleanly rejects the buy - it is impossible to double-sell a listing regardless of how many backend servers are running.

## Redis: fast propagation, not correctness

Redis pub/sub (`crossauction:events`) is used purely to make the OTHER servers feel instant - invalidating their local browse cache and notifying an affected online player - but it is never required for correctness. If Redis drops a message, the next natural cache expiry (a few seconds, see `auction.browse-cache-ttl-ms`) re-syncs everything from MySQL. Money/state correctness always comes from the MySQL transaction above, never from Redis.

Redis is also used for a simple `SET NX PX` distributed lock so only one node in the whole network runs the expired-listing sweep at any given moment, even though every node schedules the same repeating task.

## Folia-safe scheduling

All scheduling goes through `scheduler/SchedulerUtil`, which wraps Paper's region-aware scheduler APIs (`AsyncScheduler`, `GlobalRegionScheduler`, per-entity `EntityScheduler`). These APIs exist on regular Paper too (as a compatibility shim), so the same code path works unmodified on both Paper and Folia, per https://docs.papermc.io/paper/dev/folia-support/.

## Read path vs write path

The GUI browse list is backed by a short-TTL Caffeine cache (`cache/BrowseCache`) so pagination/search under high concurrency doesn't hit MySQL on every click. Buy/bid/cancel NEVER read from this cache - they always re-read and lock the row fresh inside a transaction.
