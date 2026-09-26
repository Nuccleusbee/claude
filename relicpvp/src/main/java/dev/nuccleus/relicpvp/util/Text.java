package dev.nuccleus.relicpvp.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

public final class Text {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private Text() {}

    public static Component mm(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    /** For item names and lore: turns off the default italic. */
    public static Component item(String miniMessage) {
        return MM.deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    /** Escapes user input so it can be embedded in a MiniMessage string. */
    public static String esc(String raw) {
        return MM.escapeTags(raw);
    }

    public static String serialize(Component component) {
        return MM.serialize(component);
    }
}
