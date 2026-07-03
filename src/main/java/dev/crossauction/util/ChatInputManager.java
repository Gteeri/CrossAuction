package dev.crossauction.util;

import dev.crossauction.CrossAuctionPlugin;
import dev.crossauction.scheduler.SchedulerUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Lets commands/GUI prompt a player for one line of free-text chat input
 * (e.g. "type your bid amount in chat") without depending on a
 * conversation/anvil-GUI library.
 */
public final class ChatInputManager implements Listener {

    private record PendingRequest(String prompt, Consumer<String> callback) {}

    private final CrossAuctionPlugin plugin;
    private final Map<UUID, PendingRequest> pending = new ConcurrentHashMap<>();

    public ChatInputManager(CrossAuctionPlugin plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String prompt, Consumer<String> callback) {
        pending.put(player.getUniqueId(), new PendingRequest(prompt, callback));
        plugin.messages().send(player, "chat-input-prompt", Map.of("prompt", prompt));
    }

    public void cancel(UUID player) {
        pending.remove(player);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        PendingRequest request = pending.remove(event.getPlayer().getUniqueId());
        if (request == null) return;
        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());
        SchedulerUtil.runAsync(plugin, () -> request.callback().accept(input));
    }
}
