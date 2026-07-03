package dev.crossauction.command;

import dev.crossauction.config.ConfigManager;
import dev.crossauction.gui.AuctionMenu;
import dev.crossauction.model.ListingType;
import dev.crossauction.scheduler.SchedulerUtil;
import dev.crossauction.service.AuctionService;
import dev.crossauction.service.ServiceException;
import dev.crossauction.util.Messages;
import dev.crossauction.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;

public final class AuctionCommand implements CommandExecutor {

    private final Plugin plugin;
    private final AuctionService service;
    private final AuctionMenu menu;
    private final Messages messages;
    private final ConfigManager cfg;

    public AuctionCommand(Plugin plugin, AuctionService service, AuctionMenu menu, Messages messages, ConfigManager cfg) {
        this.plugin = plugin;
        this.service = service;
        this.menu = menu;
        this.messages = messages;
        this.cfg = cfg;
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
        if (args.length == 0) {
            menu.open(player, 0, null);
            return true;
        }
        if (args[0].equalsIgnoreCase("sell")) {
            return handleSell(player, args);
        }
        if (args[0].equalsIgnoreCase("search") && args.length >= 2) {
            String search = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            menu.open(player, 0, search);
            return true;
        }
        player.sendMessage(Text.mm(messages.get("usage.auction")));
        return true;
    }

    private boolean handleSell(Player player, String[] args) {
        if (!player.hasPermission("crossauction.sell")) {
            player.sendMessage(Text.mm(messages.get("error.no-permission")));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(Text.mm(messages.get("usage.sell")));
            return true;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(Text.mm(messages.get("error.no-item-in-hand")));
            return true;
        }
        ListingType type;
        if (args[1].equalsIgnoreCase("bin")) {
            type = ListingType.BUY_NOW;
        } else if (args[1].equalsIgnoreCase("auction")) {
            type = ListingType.AUCTION;
        } else {
            player.sendMessage(Text.mm(messages.get("usage.sell")));
            return true;
        }
        BigDecimal price;
        try {
            price = new BigDecimal(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Text.mm(messages.get("error.invalid-number")));
            return true;
        }
        if (price.signum() <= 0) {
            player.sendMessage(Text.mm(messages.get("error.invalid-number")));
            return true;
        }
        BigDecimal minIncrement = cfg.defaultMinBidIncrement();
        if (type == ListingType.AUCTION && args.length >= 4) {
            try {
                minIncrement = new BigDecimal(args[3]);
            } catch (NumberFormatException ignored) { }
        }
        ItemStack toSell = hand.clone();
        player.getInventory().setItemInMainHand(null);

        service.createListing(player.getUniqueId(), player.getName(), toSell, type, price, minIncrement, cfg.defaultDurationSeconds())
                .whenComplete((id, err) -> SchedulerUtil.runForEntity(plugin, player, () -> {
                    if (err != null) {
                        String key = (err.getCause() instanceof ServiceException se || err instanceof ServiceException)
                                ? ((err instanceof ServiceException sex) ? sex.getMessage() : ((ServiceException) err.getCause()).getMessage())
                                : "error.generic";
                        player.sendMessage(Text.mm(messages.get(key)));
                        // refund the item since the listing failed
                        var leftover = player.getInventory().addItem(toSell);
                        leftover.values().forEach(extra -> player.getWorld().dropItemNaturally(player.getLocation(), extra));
                        return;
                    }
                    player.sendMessage(Text.mm(messages.get("listing-created")));
                }));
        return true;
    }
}
