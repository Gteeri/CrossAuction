# Scaling notes (~1000 concurrent players network-wide)

## Connection pooling

`database.pool-size` (HikariCP) is **per backend server**, not network-wide. Keep it small (8-10) - with, say, 10 backend servers that's 80-100 total MySQL connections, which is comfortable for a properly tuned MySQL instance. Raising `max_connections` on the MySQL server itself and giving it enough `innodb_buffer_pool_size` to hold the working set matters far more than a large per-node pool.

## Read amplification

The browse GUI is the highest-traffic read path at 1000 concurrent players. `BrowseCache` (Caffeine, TTL from `auction.browse-cache-ttl-ms`, default 3s) absorbs repeated page/search requests per node so MySQL only sees a fraction of the actual GUI opens. Increase the TTL slightly if you have very high GUI traffic and don't need sub-3-second freshness; it never affects buy/bid correctness since those always bypass the cache.

## Indexing

`schema.sql` includes indexes for every access pattern the plugin actually uses: `status`, `expires_at`, `seller_uuid`, the combined `(status, created_at)` used by the browse query's `ORDER BY`, and `item_display` for search.

## Expiry sweep cost

Only ONE node processes expired listings at a time (Redis distributed lock), so sweep cost does not multiply with the number of backend servers. `auction.expiry-sweep-interval-seconds` (default 30) and the sweep's internal batch `LIMIT 200` keep each run cheap even with a large `ca_listings` table.

## Horizontal headroom

If the network grows well beyond ~1000 players:

- Point read-heavy queries (the browse page) at a MySQL read replica.
- Redis pub/sub scales horizontally on its own; a single small instance comfortably handles auction-event volume even at this scale.
- `ca_history` grows unbounded - consider archiving/pruning it on a schedule once it's no longer needed for support/dispute lookups.
