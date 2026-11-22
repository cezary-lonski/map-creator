package com.chicken.mapcreator.menu;

import com.chicken.mapcreator.ai.AiAssistant;
import com.chicken.mapcreator.ai.AiClient;
import com.chicken.mapcreator.ai.AiConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Rozmowa z AI jako menu: gracz wpisuje prompta na czacie, a AI odpowiada "bloczkami" akcji.
 * Klik w bloczek wykonuje go, Shift lub prawy klik usuwa. "Zastosuj wszystkie" wykonuje wszystkie.
 */
public final class AiChatMenu {
	static final String KEY = "ai_chat";

	private AiChatMenu() {}

	public static boolean isOpen(ServerPlayer p) {
		return p.containerMenu instanceof UiMenu menu && KEY.equals(menu.key());
	}

	private static List<String> wrap(String text, int width, int maxLines) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.replace("\n", " ").split(" +")) {
			if (line.length() + word.length() + 1 > width && line.length() > 0) {
				lines.add(line.toString());
				line = new StringBuilder();
			}
			if (line.length() > 0) {
				line.append(' ');
			}
			line.append(word);
		}
		if (line.length() > 0) {
			lines.add(line.toString());
		}
		if (lines.size() > maxLines) {
			List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
			cut.set(maxLines - 1, cut.get(maxLines - 1) + "...");
			return cut;
		}
		return lines;
	}

	private static Item iconFor(String action) {
		return switch (action) {
			case "trigger", "after", "if", "filter" -> Items.LEVER;
			case "kill", "kill_off", "text" -> Items.WITHER_SKELETON_SKULL;
			case "give" -> Items.CHEST;
			case "effect", "clear_effects" -> Items.POTION;
			case "camera", "camera_custom", "clear_camera" -> Items.ENDER_EYE;
			case "tp_here" -> Items.ENDER_PEARL;
			case "mob_create", "mob_clear" -> Items.ZOMBIE_SPAWN_EGG;
			case "item_give" -> Items.ANVIL;
			case "block_storage" -> Items.BARREL;
			case "delete", "npc_delete", "reset_map" -> Items.LAVA_BUCKET;
			case "play", "stop" -> Items.LIME_CONCRETE;
			default -> action.startsWith("npc_") ? Items.VILLAGER_SPAWN_EGG
					: action.startsWith("settings_") || action.equals("set_start_here") ? Items.COMMAND_BLOCK : Items.PAPER;
		};
	}

	public static void open(ServerPlayer p) {
		AiAssistant.Chat chat = AiAssistant.chat(p);
		Menus.open(p, "gui.ai.chat_title", 6, m -> {
			m.setKey(KEY);
			// odpowiedz AI (albo "mysle", albo blad, albo podpowiedz)
			if (chat.thinking) {
				m.decor(0, Menus.icon(Items.CLOCK, Menus.tr(p, "gui.ai.thinking_item", AiConfig.provider())));
			} else if (!chat.error.isEmpty()) {
				List<String> lines = new ArrayList<>(wrap(chat.error, 38, 8));
				m.decor(0, Menus.icon(Items.BARRIER, Menus.tr(p, "gui.ai.error_title"), lines.toArray(new String[0])));
			} else if (!chat.say.isEmpty()) {
				m.decor(0, Menus.icon(Items.WRITTEN_BOOK, Menus.tr(p, "gui.ai.answer"), wrap(chat.say, 38, 12).toArray(new String[0])));
			} else {
				m.decor(0, Menus.icon(Items.BOOK, Menus.tr(p, "gui.ai.answer"), wrap(Menus.tr(p, "gui.ai.hint"), 38, 10).toArray(new String[0])));
			}
			m.decor(4, Menus.icon(Items.PAPER, Menus.tr(p, "gui.ai.chat_title"), AiConfig.provider() + " / " + AiConfig.model(AiConfig.provider()),
					Menus.tr(p, "gui.ai.block_lore")));
			// bloczki akcji
			for (int i = 0; i < Math.min(36, chat.blocks.size()); i++) {
				final AiAssistant.Block block = chat.blocks.get(i);
				List<String> lore = new ArrayList<>(wrap(block.arg.isEmpty() ? "-" : block.arg, 34, 4));
				lore.add(block.done ? Menus.tr(p, "gui.ai.block_done") : Menus.tr(p, "gui.ai.block_lore"));
				ItemStack icon = Menus.icon(iconFor(block.action), block.action, lore.toArray(new String[0]));
				m.button(9 + i, block.done ? Menus.glow(icon) : icon, (pl, t, b) -> {
					if (t == net.minecraft.world.inventory.ClickType.QUICK_MOVE || b == 1) {
						chat.blocks.remove(block);
					} else {
						AiAssistant.applyBlock(pl, block);
					}
					open(pl);
				});
			}
			m.button(45, Menus.glow(Menus.icon(Items.WRITABLE_BOOK, Menus.tr(p, "gui.ai.write"), Menus.tr(p, "gui.menu.type_in_chat"))), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.ai.prompt", text -> AiAssistant.ask(pl, text)));
			boolean pending = chat.blocks.stream().anyMatch(x -> !x.done);
			if (pending) {
				m.button(46, Menus.glow(Menus.icon(Items.LIME_CONCRETE, Menus.tr(p, "gui.ai.apply_all"))), (pl, t, b) -> AiAssistant.applyAll(pl));
			}
			m.button(47, Menus.icon(Items.BARRIER, Menus.tr(p, "gui.ai.new_chat")), (pl, t, b) -> {
				AiAssistant.newChat(pl);
				open(pl);
			});
			m.button(48, Menus.icon(Items.REPEATER, Menus.tr(p, "gui.ai.open"), Menus.tr(p, "gui.ai.open_lore")), (pl, t, b) -> Menus.aiSettings(pl));
			m.button(49, Menus.back(p), (pl, t, b) -> Menus.main(pl));
		});
	}

	/** Pobiera liste modeli od dostawcy i pokazuje ja jako menu do wyboru (nie trzeba znac nazw z glowy). */
	public static void pickModel(ServerPlayer p) {
		String provider = AiConfig.provider();
		String key = AiConfig.key(provider);
		if (key.isEmpty()) {
			p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.ai.no_key", provider)));
			return;
		}
		p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.ai.models_loading")));
		MinecraftServer server = p.level().getServer();
		AiClient.listModels(provider, key).whenComplete((list, error) -> server.execute(() -> {
			if (error != null) {
				Throwable cause = error.getCause() != null ? error.getCause() : error;
				p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.ai.models_error", String.valueOf(cause.getMessage()))));
				return;
			}
			Menus.open(p, "gui.ai.models_title", 6, m -> {
				for (int i = 0; i < Math.min(45, list.size()); i++) {
					final String id = list.get(i);
					ItemStack s = Menus.icon(Items.BOOK, id);
					m.button(i, id.equals(AiConfig.model(provider)) ? Menus.glow(s) : s, (pl, t, b) -> {
						AiConfig.setModel(provider, id);
						Menus.aiSettings(pl);
					});
				}
				m.button(49, Menus.back(p), (pl, t, b) -> Menus.aiSettings(pl));
			});
		}));
	}
}
