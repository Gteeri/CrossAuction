package dev.crossauction.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map;

public final class Messages {

    private final JavaPlugin plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration messages;

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
    }

    private String prefix() {
        return messages.getString("prefix", "");
    }

    public String raw(String key) {
        return messages.getString(key, key);
    }

    /** Raw (un-prefixed) message text for a key, for inline use in commands/GUI/chat. */
    public String get(String key) {
        return raw(key);
    }

    public void send(CommandSender to, String key) {
        send(to, key, Map.of());
    }

    public void send(CommandSender to, String key, Map<String, String> placeholders) {
        String template = prefix() + raw(key);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            template = template.replace("{" + e.getKey() + "}", e.getValue());
        }
        Component component = mm.deserialize(template);
        to.sendMessage(component);
    }
}
