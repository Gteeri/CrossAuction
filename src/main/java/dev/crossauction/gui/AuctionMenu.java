package dev.crossauction.gui;

import dev.crossauction.config.ConfigManager;
import dev.crossauction.model.AuctionListing;
import dev.crossauction.model.ListingType;
import dev.crossauction.scheduler.SchedulerUtil;
import dev.crossauction.serialize.ItemSerialization;
import dev.crossauction.service.AuctionService;
import dev.crossauction.util.Messages;
import dev.crossauction.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Builds and opens the paginated browse GUI. All DB access happens off the
 * main thread via AuctionService; the built inventory is opened on the
 * player's own region thread via SchedulerUtil (Folia-safe).
 */
public final class AuctionMenu {

    private static final int ROWS = 6;
    private static final int PAGE_SIZE = ROWS * 9 - 9; // bottom row reserved for controls

    private final Plugin plugin;
    private final AuctionService service;
    private final Messages messages;

    public AuctionMenu(Plugin plugin, AuctionService service, Messages messages) {
        this.plugin = plugin;
        this.service = service;
        this.messages = messages;
    }

    public void open(Player player, int page, String search) {
        service.browse(page, search).whenComplete((listings, err) -> {
            SchedulerUtil.runForEntity(plugin, player, () -> {
                if (err != null) {
                    player.sendMessage(Text.mm(messages.get("error.generic")));
                    return;
                }
                if (!player.isOnline()) return;
                build(player, page, search, listings);
            });
        });
    }

    private void build(Player player, int page, String search, List<AuctionListing> listings) {
        AuctionMenuHolder holder = new AuctionMenuHolder();
        Inventory inv = Bukkit.createInventory(holder, ROWS * 9, Text.mm(messages.get("gui.title")));
        holder.setInventory(inv);
        holder.setPage(page);
        holder.setSearch(search);

        int slot = 0;
        for (AuctionListing listing : listings) {
            if (slot >= PAGE_SIZE) break;
            ItemStack display = ItemSerialization.deserialize(listing.getItemData()).clone();
            display.setAmount(Math.max(1, listing.getItemAmount()));
            ItemMeta meta = display.getItemMeta();
            if (meta != null) {
                java.util.List<String> lore = new java.util.ArrayList<>();
                lore.add(messages.get("gui.lore.seller").replace("{seller}", listing.getSellerName()));
                if (listing.getType() == ListingType.AUCTION) {
                    lore.add(messages.get("gui.lore.current-bid").replace("{price}", listing.getCurrentBid().toPlainString()));
                    lore.add(messages.get("gui.lore.min-next-bid").replace("{price}",
                            listing.getCurrentBid().add(listing.getMinBidIncrement()).toPlainString()));
                } else {
                    lore.add(messages.get("gui.lore.price").replace("{price}", listing.getPrice().toPlainString()));
                }
                meta.lore(lore.stream().map(Text::mm).toList());
                display.setItemMeta(meta);
            }
            inv.setItem(slot, display);
            holder.putListing(slot, new AuctionMenuHolder.ListingRef(listing.getId(), listing.getType(),
                    listing.getType() == ListingType.AUCTION
                            ? listing.getCurrentBid().add(listing.getMinBidIncrement())
                            : listing.getPrice()));
            slot++;
        }

        if (page > 0) {
            inv.setItem(ROWS * 9 - 9, navItem(Material.ARROW, messages.get("gui.previous-page")));
        }
        if (listings.size() >= PAGE_SIZE) {
            inv.setItem(ROWS * 9 - 1, navItem(Material.ARROW, messages.get("gui.next-page")));
        }
        inv.setItem(ROWS * 9 - 5, navItem(Material.HOPPER, messages.get("gui.close")));

        player.openInventory(inv);
    }

    private ItemStack navItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.mm(name));
            item.setItemMeta(meta);
        }
        return item;
    }
}
