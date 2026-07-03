package dev.crossauction.cache;

import com.google.gson.Gson;
import dev.crossauction.config.ConfigManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;
import redis.clients.jedis.params.SetParams;

import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Pub/Sub bridge between backend servers. Every server publishes an Event
 * whenever a listing changes (created/sold/bid/cancelled/expired) so every
 * other server can invalidate its browse cache and notify an affected
 * player instantly, without needing a proxy plugin-messaging channel and
 * without hammering MySQL with polling at 1k+ concurrent players.
 *
 * Also provides a simple distributed lock (SET NX PX) used by
 * {@code ExpirySweepService} so only one node in the whole network performs
 * the expired-listing sweep at any given moment.
 */
public final class RedisManager {

    public static final class Event {
        public String type;
        public long listingId;
        public String sellerUuid;
        public String buyerUuid;
        public String originServer;
    }

    private final ConfigManager cfg;
    private final Logger logger;
    private final Gson gson = new Gson();
    private final ExecutorService subscriberExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CrossAuction-Redis-Subscriber");
        t.setDaemon(true);
        return t;
    });

    private JedisPool pool;
    private Consumer<Event> listener;
    private volatile boolean enabled;

    public RedisManager(ConfigManager cfg, Logger logger) {
        this.cfg = cfg;
        this.logger = logger;
    }

    public void connect() {
        this.enabled = cfg.redisEnabled();
        if (!enabled) {
            logger.info("Redis disabled in config.yml - running in single-node fallback mode.");
            return;
        }
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(16);
        poolConfig.setMaxIdle(8);
        String password = cfg.redisPassword();
        this.pool = new JedisPool(poolConfig, cfg.redisHost(), cfg.redisPort(), 5000,
                (password == null || password.isEmpty()) ? null : password, cfg.redisDatabase());
        subscriberExecutor.submit(this::subscribeLoop);
    }

    private void subscribeLoop() {
        while (enabled && !subscriberExecutor.isShutdown()) {
            try (Jedis jedis = pool.getResource()) {
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        try {
                            Event event = gson.fromJson(message, Event.class);
                            if (listener != null && event != null) {
                                listener.accept(event);
                            }
                        } catch (Exception e) {
                            logger.warning("Failed to process CrossAuction redis event: " + e.getMessage());
                        }
                    }
                }, cfg.redisChannel());
            } catch (Exception e) {
                if (!enabled) return;
                logger.warning("Redis subscription dropped, retrying in 5s: " + e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); return; }
            }
        }
    }

    public void onEvent(Consumer<Event> listener) {
        this.listener = listener;
    }

    public void publish(String type, long listingId, UUID seller, UUID buyer) {
        if (!enabled || pool == null) return;
        Event event = new Event();
        event.type = type;
        event.listingId = listingId;
        event.sellerUuid = seller == null ? null : seller.toString();
        event.buyerUuid = buyer == null ? null : buyer.toString();
        event.originServer = cfg.serverId();
        try (Jedis jedis = pool.getResource()) {
            jedis.publish(cfg.redisChannel(), gson.toJson(event));
        } catch (Exception e) {
            logger.warning("Failed to publish CrossAuction redis event: " + e.getMessage());
        }
    }

    /**
     * Attempts to become the network-wide leader for the expiry sweep.
     * Returns true if this node acquired the lock for {@code ttlMs}.
     * If Redis is disabled, every node returns true (falls back to each
     * node sweeping independently - each UPDATE is still safe because it's
     * guarded by the row's status check inside a transaction).
     */
    public boolean tryAcquireLock(String key, long ttlMs) {
        if (!enabled || pool == null) return true;
        try (Jedis jedis = pool.getResource()) {
            SetParams params = SetParams.setParams().nx().px(ttlMs);
            String result = jedis.set(key, cfg.serverId(), params);
            return "OK".equals(result);
        } catch (Exception e) {
            logger.warning("Redis lock attempt failed, assuming not acquired: " + e.getMessage());
            return false;
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void shutdown() {
        this.enabled = false;
        subscriberExecutor.shutdownNow();
        if (pool != null) {
            pool.close();
        }
    }
}
