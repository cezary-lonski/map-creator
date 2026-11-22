package com.chicken.mapcreator.ai;

import com.chicken.mapcreator.MapCreator;
import com.chicken.mapcreator.menu.AiChatMenu;
import com.chicken.mapcreator.menu.ItemCreator;
import com.chicken.mapcreator.menu.ServerLang;
import com.chicken.mapcreator.rules.BlockStorage;
import com.chicken.mapcreator.rules.MapControl;
import com.chicken.mapcreator.rules.MapSettings;
import com.chicken.mapcreator.rules.MobSpawner;
import com.chicken.mapcreator.rules.Rule;
import com.chicken.mapcreator.rules.RuleActions;
import com.chicken.mapcreator.rules.RuleEditor;
import com.chicken.mapcreator.rules.RuleStorage;
import com.chicken.mapcreator.selection.SelectionManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Asystent AI wbudowany w edytor. Gracz pisze prompta na czacie, a AI odpowiada "bloczkami" akcji w menu.
 * AI dostaje pelny stan edytora i moze zmienic wszystko, ale tylko przez STALA liste akcji (te same, ktore daje menu).
 * Kazdy bloczek gracz klika osobno albo wszystkie naraz, wiec nic sie nie zmienia bez jego decyzji (chyba ze wlaczy auto-wykonywanie).
 */
public final class AiAssistant {
	/** Jeden bloczek: akcja z argumentem, wykonywana kliknieciem. */
	public static final class Block {
		public final String action;
		public final String arg;
		public boolean done;

		Block(String action, String arg) {
			this.action = action;
			this.arg = arg;
		}
	}

	/** Stan rozmowy jednego gracza. */
	public static final class Chat {
		public String say = "";
		public String error = "";
		public boolean thinking = false;
		public final List<Block> blocks = new ArrayList<>();
		final List<AiClient.Message> history = new ArrayList<>();
	}

	/** Dozwolone akcje; wszystko inne z odpowiedzi AI jest ignorowane. */
	private static final Set<String> ALLOWED = Set.of(
			"edit_kind", "trigger", "after", "if", "need", "forbid", "cond_clear", "filter", "kill", "kill_off", "text", "give", "effect", "camera",
			"camera_custom", "clear_camera", "clear_effects", "tp_here", "delete",
			"mob_create", "mob_clear",
			"npc_create", "npc_appear", "npc_trade", "npc_attack", "npc_go", "npc_immune", "npc_none", "npc_delete",
			"item_give", "block_storage",
			"settings_lose_items", "settings_clear_on_death", "settings_reset_on_death", "set_start_here", "reset_map", "play", "stop");
	private static final Set<String> NO_ANCHOR_NEEDED = Set.of("item_give", "settings_lose_items", "settings_reset_on_death",
			"set_start_here", "reset_map", "play", "stop");
	private static final int MAX_BLOCKS = 36;
	private static final int MAX_HISTORY = 8;

	private static final Map<UUID, Chat> CHATS = new HashMap<>();

	private AiAssistant() {}

	public static Chat chat(ServerPlayer p) {
		return CHATS.computeIfAbsent(p.getUUID(), k -> new Chat());
	}

	public static void newChat(ServerPlayer p) {
		CHATS.remove(p.getUUID());
	}

	private static String tr(ServerPlayer p, String key, Object... args) {
		return ServerLang.tr(p, key, args);
	}

	private static String ruleJson(Rule r) {
		return Rule.CODEC.encodeStart(JsonOps.INSTANCE, r).result().map(JsonElement::toString).orElse("{}");
	}

	/** Pelny opis stanu edytora: bierzaca kotwica i jej regula (JSON), lista wszystkich regul i zasady swiata. */
	private static String context(ServerPlayer p) {
		MinecraftServer server = p.level().getServer();
		StringBuilder sb = new StringBuilder();
		Optional<Rule.Anchor> anchor = SelectionManager.currentAnchor(p);
		if (anchor.isEmpty()) {
			sb.append("No anchor selected (the player must right-click a block with the wand to edit its rule). ");
		} else {
			sb.append("Selected anchor ").append(anchor.get().pos().toShortString()).append(" in ").append(anchor.get().dimension())
					.append(". Currently editing the ").append(RuleEditor.editKind(p)).append(" of this anchor. ");
			Optional<Rule> rule = RuleEditor.currentRule(p, RuleEditor.editKind(p));
			sb.append(rule.map(r -> "Its current data (JSON): " + ruleJson(r)).orElse("It has no data yet; actions will create it."));
		}
		sb.append("\nAll rules in the world: ");
		for (Rule r : RuleStorage.get(server).all()) {
			sb.append("[").append(r.shortId()).append(" ").append(r.kind()).append(" at ").append(r.anchor().pos().toShortString())
					.append(" trigger ").append(r.trigger().type()).append("] ");
		}
		MapSettings s = MapSettings.get(server);
		sb.append("\nWorld rules: lose items on death=").append(s.loseItems()).append(", reset map after death=").append(s.resetOnDeath())
				.append(", playing=").append(s.playing()).append(", start point=").append(s.start().map(b -> b.toShortString()).orElse("not set"));
		return sb.toString();
	}

	private static String systemPrompt(ServerPlayer p) {
		return "You are the assistant built into the editor of the Minecraft (Java 1.21.11) mod \"Map Creator\", used to build "
				+ "\"unfair\" adventure maps (Enzzi style). You know you are inside the editor and may change ANYTHING in it through the actions below. "
				+ "A rule belongs to an anchor block: when its trigger fires, its events, mobs and player effects happen. NPCs are a second thing on the same anchor.\n"
				+ "Reply with ONLY a JSON object: {\"say\": \"short helpful answer in the player's language\", \"actions\": [{\"action\": \"<name>\", \"arg\": \"<string>\"}]}. "
				+ "Each action becomes a clickable block in the player's menu. Use an empty list when you only answer a question. Never invent action names. "
				+ "Selecting areas (trigger or event areas) needs the wand, so tell the player to do that part.\n"
				+ "Actions (arg format):\n"
				+ "- edit_kind: RULE or NPC (which of the two on the anchor the following actions edit; put it first when needed)\n"
				+ "- trigger: CLICK | STAND | REDSTONE | OPEN | BREAK (NPC also NPC_STEP_ON, NPC_PICKUP, NPC_HURT); filter: block or item id for NPC triggers\n"
				+ "- need / forbid: rule ids; the current rule fires only if those rules already fired (need) / have not fired (forbid); cond_clear: empty\n"
				+ "- settings_clear_on_death: true/false (player drops nothing, inventory is cleared on death)\n"
				+ "- after / if: rule ids (a1b2c3, d4e5f6) for the after-event / if-happened triggers\n"
				+ "- kill: custom death text (turns on killing the player); kill_off: \"\"; text: text in the middle of the screen\n"
				+ "- give: \"minecraft:item_id,count\"; effect: \"minecraft:effect_id,seconds,amplifier\"; tp_here: \"\" (target = the player's position)\n"
				+ "- camera: \"type,seconds,intensity\", type wave, upside_down, sideways, blur, nearsighted, pixelate, windows "
				+ "(windows intensity = size_percent*100 + window_count, e.g. 3003)\n"
				+ "- camera_custom: \"roll=<degrees>;wave=<0-1>;speed=<0.1-10>;seconds=<n>\" makes a NEW custom camera effect\n"
				+ "- clear_camera: \"\"; clear_effects: \"\"; delete: \"\" (delete the rule)\n"
				+ "- mob_create: \"minecraft:zombie|nick|BEHAVIOR|damage|delay_seconds|gravity|held_item\" (BEHAVIOR NONE, THROW, ATTACK); mob_clear: \"\"\n"
				+ "- npc_create: \"minecraft:villager|\" or \"player|SkinNick\"; npc_appear: \"1\" (NPC appears only when the trigger fires) or \"0\"; "
				+ "npc_trade: \"in_item,count,out_item,count\"; npc_attack: \"damage\"; npc_go: \"x,y,z\"; npc_immune: \"1\"/\"0\"; npc_none: \"\"; npc_delete: \"\"\n"
				+ "- item_give: \"base_item|name|lore|break_block1,break_block2|durability|unbreakable(true/false)|glint(true/false)\" "
				+ "gives the player a custom item (e.g. a pickaxe that breaks only chosen blocks in adventure mode)\n"
				+ "- block_storage: \"rows\" (1-6 gives the anchor block an inventory, 0 removes it)\n"
				+ "- settings_lose_items: true/false; settings_reset_on_death: true/false; set_start_here: \"\"; reset_map: \"\"; play: \"\"; stop: \"\"\n"
				+ "Current editor state: " + context(p);
	}

	/** Wysyla prompta gracza do wybranego dostawcy. Menu pokazuje "mysle...", a odpowiedz wraca jako bloczki. */
	public static void ask(ServerPlayer p, String text) {
		MinecraftServer server = p.level().getServer();
		String provider = AiConfig.provider();
		String key = AiConfig.key(provider);
		Chat chat = chat(p);
		if (key.isEmpty()) {
			chat.error = tr(p, "gui.ai.no_key", provider);
			AiChatMenu.open(p);
			return;
		}
		chat.history.add(new AiClient.Message("user", text));
		while (chat.history.size() > MAX_HISTORY) {
			chat.history.remove(0);
		}
		chat.thinking = true;
		chat.error = "";
		AiChatMenu.open(p);
		String system = systemPrompt(p);
		AiClient.ask(provider, key, AiConfig.model(provider), system, new ArrayList<>(chat.history)).whenComplete((reply, error) ->
				server.execute(() -> {
					chat.thinking = false;
					if (error != null) {
						Throwable cause = error.getCause() != null ? error.getCause() : error;
						MapCreator.LOGGER.warn("AI request failed: {}", cause.toString());
						chat.error = friendlyError(p, cause, AiConfig.model(provider));
						if (!chat.history.isEmpty()) {
							chat.history.remove(chat.history.size() - 1);
						}
					} else {
						chat.history.add(new AiClient.Message("assistant", reply));
						parseReply(chat, reply);
					}
					if (AiChatMenu.isOpen(p)) {
						AiChatMenu.open(p);
					} else {
						p.sendSystemMessage(Component.literal("[AI] " + tr(p, "gui.ai.ready")).withStyle(ChatFormatting.LIGHT_PURPLE));
					}
					if (error == null && AiConfig.autoRun()) {
						applyAll(p);
					}
				}));
	}

	/** Zamienia surowy blad HTTP na zrozumialy komunikat z podpowiedzia, co zrobic. */
	private static String friendlyError(ServerPlayer p, Throwable cause, String model) {
		String m = String.valueOf(cause.getMessage());
		if (cause instanceof java.net.http.HttpTimeoutException || m.contains("timed out")) {
			return tr(p, "gui.ai.err_timeout", model);
		}
		if (m.startsWith("HTTP 429")) {
			return tr(p, "gui.ai.err_quota", model);
		}
		if (m.startsWith("HTTP 401") || m.startsWith("HTTP 403")) {
			return tr(p, "gui.ai.err_key");
		}
		if (m.startsWith("HTTP 404")) {
			return tr(p, "gui.ai.err_model", model);
		}
		return m;
	}

	private static void parseReply(Chat chat, String reply) {
		chat.say = reply;
		chat.blocks.clear();
		try {
			int a = reply.indexOf('{');
			int b = reply.lastIndexOf('}');
			JsonObject json = JsonParser.parseString(reply.substring(a, b + 1)).getAsJsonObject();
			if (json.has("say")) {
				chat.say = json.get("say").getAsString();
			}
			if (json.has("actions") && json.get("actions").isJsonArray()) {
				JsonArray array = json.getAsJsonArray("actions");
				for (JsonElement el : array) {
					if (chat.blocks.size() >= MAX_BLOCKS || !el.isJsonObject()) {
						continue;
					}
					JsonObject o = el.getAsJsonObject();
					String name = o.has("action") ? o.get("action").getAsString() : "";
					String arg = o.has("arg") && !o.get("arg").isJsonNull() ? o.get("arg").getAsString() : "";
					if (ALLOWED.contains(name)) {
						chat.blocks.add(new Block(name, arg));
					}
				}
			}
		} catch (RuntimeException e) {
			// odpowiedz nie jest JSON-em: pokazujemy ja jako zwykly tekst, bez bloczkow
		}
	}

	/** Wykonuje jeden bloczek (klik w menu). Zwraca true, gdy sie udalo. */
	public static boolean applyBlock(ServerPlayer p, Block block) {
		if (block.done) {
			return true;
		}
		if (!NO_ANCHOR_NEEDED.contains(block.action) && SelectionManager.currentAnchor(p).isEmpty()) {
			p.sendSystemMessage(Component.literal(tr(p, "mapcreator.sel.no_anchor")));
			return false;
		}
		try {
			run(p, block.action, block.arg);
			block.done = true;
			return true;
		} catch (RuntimeException e) {
			MapCreator.LOGGER.warn("AI block {} failed: {}", block.action, e.toString());
			p.sendSystemMessage(Component.literal("[AI] " + block.action + ": " + e.getMessage()));
			return false;
		}
	}

	public static void applyAll(ServerPlayer p) {
		Chat chat = chat(p);
		int done = 0;
		int total = 0;
		for (Block b : chat.blocks) {
			if (b.done) {
				continue;
			}
			total++;
			if (applyBlock(p, b)) {
				done++;
			}
		}
		p.sendSystemMessage(Component.literal("[AI] " + tr(p, "gui.ai.done", done, total)).withStyle(ChatFormatting.LIGHT_PURPLE));
		if (AiChatMenu.isOpen(p)) {
			AiChatMenu.open(p);
		}
	}

	private static void run(ServerPlayer p, String action, String arg) {
		MinecraftServer server = p.level().getServer();
		switch (action) {
			case "mob_create" -> mobCreate(p, arg);
			case "camera_custom" -> cameraCustom(p, arg);
			case "item_give" -> ItemCreator.giveSpec(p, arg);
			case "settings_lose_items" -> {
				MapSettings.get(server).setLoseItems(!arg.trim().equalsIgnoreCase("false"));
				MapControl.applyGameRules(server);
			}
			case "settings_clear_on_death" -> {
				MapSettings.get(server).setClearOnDeath(!arg.trim().equalsIgnoreCase("false"));
				MapControl.applyGameRules(server);
			}
			case "settings_reset_on_death" -> MapSettings.get(server).setResetOnDeath(!arg.trim().equalsIgnoreCase("false"));
			case "set_start_here" -> MapSettings.get(server).setStart(p.level().dimension().identifier().toString(), p.blockPosition());
			case "reset_map" -> MapControl.reset(server);
			case "play" -> MapControl.play(server);
			case "stop" -> MapControl.stop(server);
			case "block_storage" -> blockStorage(p, (int) number(arg, 0, 0, 6));
			default -> RuleEditor.apply(p, action, arg);
		}
	}

	private static void blockStorage(ServerPlayer p, int rows) {
		MinecraftServer server = p.level().getServer();
		Rule.Anchor a = SelectionManager.currentAnchor(p).orElseThrow();
		BlockStorage storage = BlockStorage.get(server);
		ServerLevel level = RuleActions.levelOf(server, a.dimension());
		List<ItemStack> overflow = rows == 0 ? storage.remove(a.dimension(), a.pos()) : storage.setRows(a.dimension(), a.pos(), rows);
		if (level != null) {
			BlockStorage.dropAll(level, a.pos().above(), overflow);
		}
	}

	private static double number(String s, double fallback, double min, double max) {
		try {
			return Math.max(min, Math.min(max, Double.parseDouble(s.trim().replace(',', '.'))));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** arg: mob|nick|BEHAVIOR|damage|delay|gravity|item */
	private static void mobCreate(ServerPlayer p, String arg) {
		String[] f = (arg + "|||||||").split("\\|", -1);
		if (EntityType.byString(f[0].trim()).isEmpty()) {
			throw new IllegalArgumentException("unknown mob " + f[0]);
		}
		String behavior = f[2].trim().toUpperCase();
		if (!Set.of("NONE", "THROW", "ATTACK").contains(behavior)) {
			behavior = "NONE";
		}
		Rule.MobSpec spec = Rule.MobSpec.create(f[0].trim(), f[1].trim()).withAction(behavior)
				.withDamage(number(f[3], 1.0, 0, 100)).withDelay((int) number(f[4], 0, 0, 600))
				.withGravity(!f[5].trim().equalsIgnoreCase("false"));
		if (!MobSpawner.stack(f[6]).isEmpty()) {
			spec = spec.withEquip("item", f[6].trim());
		}
		Rule.MobSpec finalSpec = spec;
		RuleEditor.edit(p, r -> {
			List<Rule.MobSpec> list = new ArrayList<>(r.mobs());
			list.add(finalSpec);
			return new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), list, r.effects(), r.npc());
		});
	}

	/** arg: roll=..;wave=..;speed=..;seconds=..  Dodaje nowy, wlasny efekt kamery (parametry sa liczbami, nie kodem). */
	private static void cameraCustom(ServerPlayer p, String arg) {
		double roll = 0;
		double wave = 0;
		double speed = 1;
		int seconds = 10;
		for (String part : arg.split(";")) {
			String[] kv = part.split("=", 2);
			if (kv.length != 2) {
				continue;
			}
			switch (kv[0].trim().toLowerCase()) {
				case "roll" -> roll = number(kv[1], 0, -360, 360);
				case "wave" -> wave = number(kv[1], 0, 0, 1);
				case "speed" -> speed = number(kv[1], 1, 0.1, 10);
				case "seconds" -> seconds = (int) number(kv[1], 10, 1, 3600);
				default -> { }
			}
		}
		String id = "mapcreator:custom?roll=" + roll + ";wave=" + wave + ";speed=" + speed;
		int secs = seconds;
		RuleEditor.edit(p, r -> {
			List<Rule.EffectSpec> list = new ArrayList<>(r.effects().effects());
			list.add(new Rule.EffectSpec(id, 1, secs));
			return new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), r.mobs(),
					new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(), r.effects().give(), r.effects().tp(),
							list, r.effects().screenText()), r.npc());
		});
	}
}
