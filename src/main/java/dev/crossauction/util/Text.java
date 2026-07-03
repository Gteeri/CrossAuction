package dev.crossauction.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    private Text() {}

    public static Component parse(String raw) {
        return MINI_MESSAGE.deserialize(raw == null ? "" : raw);
    }
}
