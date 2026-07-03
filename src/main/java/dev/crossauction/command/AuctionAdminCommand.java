package dev.crossauction.command;

import dev.crossauction.service.AuctionService;
import dev.crossauction.service.ExpirySweepService;
import dev.crossauction.util.Messages;
import dev.crossauction.util.Text;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public final class AuctionAdminCommand implements CommandExecutor {

    private final AuctionService service;
    private final ExpirySweepService expirySweepService;
    private final Messages messages;

    public AuctionAdminCommand(AuctionService service, ExpirySweepService expirySweepService, Messages messages) {
        this.service = service;
        this.expirySweepService = expirySweepService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("crossauction.admin")) {
            sender.sendMessage(Text.mm(messages.get("error.no-permission")));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("cancel") && args.length >= 2) {
            try {
                long id = Long.parseLong(args[1]);
                service.cancelListing(sender instanceof org.bukkit.entity.Player p ? p.getUniqueId() : new java.util.UUID(0, 0), id, true)
                        .whenComplete((v, err) -> sender.sendMessage(Text.mm(
                                err != null ? messages.get("error.generic") : messages.get("admin.cancel-success"))));
            } catch (NumberFormatException e) {
                sender.sendMessage(Text.mm(messages.get("error.invalid-number")));
            }
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("forcesweep")) {
            expirySweepService.forceSweepNow();
            sender.sendMessage(Text.mm(messages.get("admin.sweep-triggered")));
            return true;
        }
        sender.sendMessage(Text.mm(messages.get("usage.auctionadmin")));
        return true;
    }
}
