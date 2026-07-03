# Deployment guide

1. **Create the MySQL database and user** that every backend server will share, e.g.:
   ```sql
   CREATE DATABASE crossauction CHARACTER SET utf8mb4;
   CREATE USER 'crossauction'@'%' IDENTIFIED BY 'change-me';
   GRANT ALL PRIVILEGES ON crossauction.* TO 'crossauction'@'%';
   ```
   The plugin applies `schema.sql` automatically (idempotent `CREATE TABLE IF NOT EXISTS`) on first boot of each node - you do not need to run it manually.

2. **Stand up a Redis instance** reachable from every backend server (a single small instance is enough at this scale; see `docs/SCALING.md`).

3. **Build the plugin**: `./gradlew shadowJar` (requires internet access to download dependencies).

4. **Configure every backend server identically**, except for `server-id`, which must be unique per node (e.g. `survival-1`, `survival-2`, `skyblock-1`). Point `database.*` and `redis.*` at the same shared instances from step 1/2 on every node.

5. **Choose an economy provider** in `config.yml`:
   - `VAULT` - only if your Vault economy plugin's storage is ALSO shared network-wide (e.g. backed by the same MySQL server). Otherwise balances will desync between servers.
   - `INTERNAL` (recommended if you don't already have a network-wide economy) - CrossAuction manages its own shared balance table.

6. **Drop the jar into `plugins/`** on every backend server and (re)start each one.

7. **Verify**: run `/crossauction status` on any node to confirm its `server-id` and Redis connectivity, then list an item on one server and confirm it's visible in `/auction` on another.

8. **Load test before going live** with the full expected concurrent player count across all nodes, watching MySQL connection count and slow query log.
