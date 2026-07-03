package dev.crossauction.command;

import dev.crossauction.scheduler.SchedulerUtil;
import dev.crossauction.service.AuctionService;
import dev.crossauction.util.Messages;
import dev.crossauction.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.List;

public final class CollectCommand implements CommandExecutor {

    private final Plugin plugin;
    private final AuctionService service;
    private final Messages messages;

    public CollectCommand(Plugin plugin, AuctionService service, Messages messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("crossauction.use")) {
            player.sendMessage(Text.mm(messages.get("error.no-permission")));
            return true;
        }
        service.collect(player.getUniqueId()).whenComplete((items, err) ->
                SchedulerUtil.runForEntity(plugin, player, () -> {
                    if (err != null) {
                        player.sendMessage(Text.mm(messages.get("error.generic")));
                        return;
                    }
                    deliver(player, items);
                }));
        return true;
    }

    private void deliver(Player player, List<ItemStack> items) {
        if (items.isEmpty()) {
            player.sendMessage(Text.mm(messages.get("nothing-to-collect")));
            return;
        }
        for (ItemStack item : items) {
            var leftover = player.getInventory().addItem(item);
            leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
        }
        player.sendMessage(Text.mm(messages.get("collect-success").replace("{count}", String.valueOf(items.size()))));
    }
}
