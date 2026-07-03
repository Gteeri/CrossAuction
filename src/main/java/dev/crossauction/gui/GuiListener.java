package dev.crossauction.gui;

import dev.crossauction.scheduler.SchedulerUtil;
import dev.crossauction.service.AuctionService;
import dev.crossauction.service.ServiceException;
import dev.crossauction.util.ChatInputManager;
import dev.crossauction.util.Messages;
import dev.crossauction.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.Plugin;

import java.math.BigDecimal;

public final class GuiListener implements Listener {

    private final Plugin plugin;
    private final AuctionService service;
    private final AuctionMenu menu;
    private final Messages messages;
    private final ChatInputManager chatInputManager;

    public GuiListener(Plugin plugin, AuctionService service, AuctionMenu menu, Messages messages, ChatInputManager chatInputManager) {
        this.plugin = plugin;
        this.service = service;
        this.menu = menu;
        this.messages = messages;
        this.chatInputManager = chatInputManager;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof AuctionMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getInventory().getSize()) return;

        int size = event.getInventory().getSize();
        if (slot == size - 9) { // previous page
            menu.open(player, Math.max(0, holder.getPage() - 1), holder.getSearch());
            return;
        }
        if (slot == size - 1) { // next page
            menu.open(player, holder.getPage() + 1, holder.getSearch());
            return;
        }
        if (slot == size - 5) { // close
            player.closeInventory();
            return;
        }

        AuctionMenuHolder.ListingRef ref = holder.getListing(slot);
        if (ref == null) return;

        if (ref.type() == dev.crossauction.model.ListingType.AUCTION && event.isRightClick()) {
            player.closeInventory();
            player.sendMessage(Text.mm(messages.get("chat-input-prompt").replace("{amount}", ref.minNextBid().toPlainString())));
            chatInputManager.awaitInput(player, input -> handleBidInput(player, ref.id(), input));
            return;
        }

        service.buyNow(player.getUniqueId(), player.getName(), ref.id()).whenComplete((v, err) ->
                SchedulerUtil.runForEntity(plugin, player, () -> {
                    if (err != null) {
                        player.sendMessage(Text.mm(messages.get(resolveErrorKey(err))));
                    } else {
                        player.sendMessage(Text.mm(messages.get("purchase-success")));
                    }
                    menu.open(player, holder.getPage(), holder.getSearch());
                }));
    }

    private void handleBidInput(Player player, long listingId, String input) {
        BigDecimal amount;
        try {
            amount = new BigDecimal(input.trim());
        } catch (NumberFormatException e) {
            player.sendMessage(Text.mm(messages.get("error.invalid-number")));
            return;
        }
        if (amount.signum() <= 0) {
            player.sendMessage(Text.mm(messages.get("error.invalid-number")));
            return;
        }
        service.placeBid(player.getUniqueId(), player.getName(), listingId, amount).whenComplete((v, err) ->
                SchedulerUtil.runForEntity(plugin, player, () -> {
                    if (err != null) {
                        player.sendMessage(Text.mm(messages.get(resolveErrorKey(err))));
                    } else {
                        player.sendMessage(Text.mm(messages.get("bid-success")));
                    }
                }));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AuctionMenuHolder holder) {
            holder.clearListings();
        }
    }

    private String resolveErrorKey(Throwable err) {
        Throwable actual = err;
        if (actual instanceof ServiceException se) {
            return se.getMessage();
        }
        return "error.generic";
    }
}
