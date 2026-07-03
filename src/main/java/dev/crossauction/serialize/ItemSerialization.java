package dev.crossauction.serialize;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Base64;
import java.util.Locale;

/**
 * Item (de)serialization for storing an ItemStack as TEXT in MySQL. Uses
 * Paper's modern ItemStack#serializeAsBytes()/deserializeBytes() (1.20.5+),
 * which is simpler and more robust than the legacy YAML-config approach.
 */
public final class ItemSerialization {

    private ItemSerialization() {}

    public static String serialize(ItemStack item) {
        byte[] bytes = item.serializeAsBytes();
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static ItemStack deserialize(String base64) {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return ItemStack.deserializeBytes(bytes);
    }

    /** Human-readable name for GUI/lore/message use: custom display name if set, otherwise a formatted material name. */
    public static String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(meta.displayName());
        }
        return formatMaterialName(item.getType().name());
    }

    private static String formatMaterialName(String raw) {
        String[] parts = raw.toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}
