package com.chicken.mapcreator.menu;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Wpisywanie tekstu na czacie dla menu (nick, tekst, id regul, wspolrzedne). "anuluj" / "cancel" przerywa. */
public final class ChatPrompt {
	private static final Map<UUID, Consumer<String>> PENDING = new HashMap<>();

	private ChatPrompt() {}

	public static void register() {
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			Consumer<String> handler = PENDING.remove(sender.getUUID());
			if (handler == null) {
				return true;
			}
			String text = message.signedContent().trim();
			sender.level().getServer().execute(() -> {
				if (text.equalsIgnoreCase("anuluj") || text.equalsIgnoreCase("cancel")) {
					sender.sendSystemMessage(Component.literal(ServerLang.tr(sender, "gui.prompt.cancelled")));
				} else {
					handler.accept(text);
				}
			});
			return false;
		});
	}

	public static void ask(ServerPlayer player, String promptKey, Consumer<String> handler) {
		PENDING.put(player.getUUID(), handler);
		player.closeContainer();
		player.sendSystemMessage(Component.literal(ServerLang.tr(player, promptKey)));
		player.sendSystemMessage(Component.literal(ServerLang.tr(player, "gui.prompt.hint")));
	}
}
