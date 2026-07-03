package dev.crossauction.listener;

import dev.crossauction.scheduler.SchedulerUtil;
import dev.crossauction.service.AuctionService;
import dev.crossauction.util.Messages;
import dev.crossauction.util.Text;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

/**
 * Notifies players of unclaimed items/money on join (e.g. they were offline
 * when their auction sold, or when they were outbid on another server).
 */
public final class PlayerJoinListener implements Listener {

    private final Plugin plugin;
    private final AuctionService service;
    private final Messages messages;

    public PlayerJoinListener(Plugin plugin, AuctionService service, Messages messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        service.countUnclaimed(player.getUniqueId()).whenComplete((count, err) -> {
            if (err != null || count == null || count <= 0) return;
            SchedulerUtil.runForEntity(plugin, player, () -> {
                if (!player.isOnline()) return;
                player.sendMessage(Text.mm(messages.get("items-waiting").replace("{count}", String.valueOf(count))));
            });
        });
    }
}
