# CrossAuction

Network-wide (cross-server) auction house plugin for **Paper/Folia 1.21.11**, built to keep listings, bids, and payouts consistent across every backend server in a network sized for **~1000 concurrent players**.

## Why it's "cross-server"

Every backend server (survival-1, survival-2, skyblock-1, ...) runs the same plugin and points at:

- **One shared MySQL database** - the single source of truth for listings, bids, claims (item/money inbox), and optionally balances.
- **One shared Redis instance** - pub/sub broadcasts every auction event (new listing, sold, bid placed, cancelled, expired) to all nodes instantly, and provides a distributed lock so only one node runs the expired-listing sweep at a time.

A player can list an item on `survival-1`, and a player on `survival-2` can buy or bid on it seconds later, with the payout delivered to the seller's claim inbox regardless of which server they're on when it sells.

## Features

- Buy-now and timed auction (bidding) listings
- Transactional, race-safe buy/bid handling (`SELECT ... FOR UPDATE`) so two servers can never double-sell the same listing
- GUI browser with pagination and search, backed by a short-lived local cache to keep MySQL read load flat under high concurrency
- Claim inbox for items/money so nothing is lost if a player is offline or logged into a different server when a listing resolves
- Automatic expiry sweep with network-wide leader election via Redis
- Pluggable economy: Vault (if your economy is already network-wide) or a built-in network-wide balance table
- Fully Folia-safe scheduling (region/entity/async schedulers per Paper's Folia support guidelines) - also runs unmodified on regular Paper

## Requirements

- Paper or Folia **1.21.11**, Java 21
- MySQL 8+ reachable from every backend server
- Redis reachable from every backend server (optional but strongly recommended at this scale; falls back to independent per-node sweeping if disabled)
- Optional: Vault + an economy plugin whose storage is ALSO network-wide

## Building

```
./gradlew shadowJar
```

The shaded jar is produced at `build/libs/CrossAuction-1.0.0.jar`.

## Installing

1. Create the MySQL database/user.
2. Copy `config.yml` to every backend server, giving each one a unique `server-id`, but pointing every node's `database.*` and `redis.*` sections at the **same** MySQL/Redis instances.
3. Drop the built jar into `plugins/` on every backend node and restart.
4. The schema in `src/main/resources/schema.sql` is applied automatically (idempotent) on first boot.

See `docs/DEPLOYMENT.md` for a full walkthrough, `docs/ARCHITECTURE.md` for how consistency is guaranteed across servers, and `docs/SCALING.md` for tuning notes at ~1000 concurrent players.

## Commands

| Command | Description |
|---|---|
| `/auction` (`/ah`, `/auc`) | Open the auction house GUI |
| `/auction sell <price> [minutes]` | List the item in your hand as buy-now |
| `/auction auction <startprice> [minutes]` | List the item in your hand as a timed auction |
| `/auction cancel <id>` | Cancel your own active listing |
| `/auction collect` | Collect items/payouts waiting for you |
| `/auction search <query>` | Search active listings |
| `/crossauction reload\|remove\|forceexpire\|status` | Admin management |

## Permissions

- `crossauction.use` (default: true)
- `crossauction.sell` (default: true)
- `crossauction.sell.bypasslimit` (default: op)
- `crossauction.admin` (default: op)
