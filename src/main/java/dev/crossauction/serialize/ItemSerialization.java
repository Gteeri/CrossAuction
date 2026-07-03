package dev.crossauction.serialize;

import org.bukkit.inventory.ItemStack;

import java.util.Base64;

/**
 * Item (de)serialization for storing an ItemStack as TEXT in MySQL. Uses
 * Paper's modern ItemStack#serializeAsBytes()/deserializeBytes() (1.20.5+),
 * which is simpler and more robust than the legacy YAML-config approach.
 */
public final class ItemSerialization {

    private ItemSerialization() {}

    public static String toBase64(ItemStack item) {
        byte[] bytes = item.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ItemStack fromBase64(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ItemStack.deserializeBytes(bytes);
    }
}
