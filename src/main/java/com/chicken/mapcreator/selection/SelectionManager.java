package com.chicken.mapcreator.selection;

import com.chicken.mapcreator.rules.Rule;
import com.chicken.mapcreator.rules.RuleEditor;
import com.chicken.mapcreator.rules.RuleStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Zaznaczanie obszaru jak w WorldEdit: dwa klikniecia rozdzka wyznaczaja dwa rogi,
 * potem Potwierdzam / Powtorz / Anuluj. Stan jest tylko w pamieci serwera (per gracz).
 */
public final class SelectionManager {
	public enum Purpose { TRIGGER_AREA, EVENT_AREA, SPAWN }

	private static final class State {
		final Rule.Anchor anchor;
		final Purpose purpose;
		final Rule.EventType eventType;
		final String eventBlock;
		BlockPos a;
		BlockPos b;

		State(Rule.Anchor anchor, Purpose purpose, Rule.EventType eventType, String eventBlock) {
			this.anchor = anchor;
			this.purpose = purpose;
			this.eventType = eventType;
			this.eventBlock = eventBlock;
		}

		boolean complete() {
			return a != null && b != null;
		}
	}

	private static final Map<UUID, Rule.Anchor> CURRENT_ANCHOR = new HashMap<>();
	private static final Map<UUID, State> SELECTING = new HashMap<>();
	/** Miejsce respu wybrane rozdzka (blok nad klikniętym), uzyte przez nastepnego moba / NPC. */
	private static final Map<UUID, BlockPos> PENDING_SPAWN = new HashMap<>();

	private SelectionManager() {}

	/** Klikniecie rozdzka PPM na blok: albo kolejny rog, albo ustawienie kotwicy. */
	public static void onWandClick(ServerPlayer player, Rule.Anchor clicked) {
		State state = SELECTING.get(player.getUUID());
		if (state == null) {
			CURRENT_ANCHOR.put(player.getUUID(), clicked);
			RuleEditor.sendMenu(player, clicked);
			return;
		}
		BlockPos p = clicked.pos();
		if (state.purpose == Purpose.SPAWN) {
			BlockPos spawn = p.above();
			PENDING_SPAWN.put(player.getUUID(), spawn);
			SELECTING.remove(player.getUUID());
			if (com.chicken.mapcreator.menu.Menus.onSpawnPicked(player, spawn)) {
				PENDING_SPAWN.remove(player.getUUID());
			}
			player.sendSystemMessage(Component.translatable("mapcreator.sel.spawn_set", spawn.getX(), spawn.getY(), spawn.getZ()));
			return;
		}
		if (state.a == null) {
			state.a = p;
			player.sendSystemMessage(Component.translatable("mapcreator.sel.first", p.getX(), p.getY(), p.getZ()));
		} else if (!state.complete()) {
			state.b = p;
			player.sendSystemMessage(Component.translatable("mapcreator.sel.second", p.getX(), p.getY(), p.getZ()));
			sendConfirmPrompt(player, state);
		} else {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.pending"));
			sendConfirmPrompt(player, state);
		}
	}

	/** Wlacza wybor miejsca respu: kliknij blok rozdzka, mob/NPC pojawi sie nad nim. */
	public static void startSpawnSelect(ServerPlayer player) {
		Rule.Anchor anchor = CURRENT_ANCHOR.get(player.getUUID());
		if (anchor == null) {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.no_anchor"));
			return;
		}
		SELECTING.put(player.getUUID(), new State(anchor, Purpose.SPAWN, Rule.EventType.BROKE, ""));
		player.sendSystemMessage(Component.translatable("mapcreator.sel.spawn_start"));
	}

	/** Zwraca i zuzywa wybrane miejsce respu (albo pusty, gdy nie wybrano). */
	public static Optional<BlockPos> takeSpawn(ServerPlayer player) {
		return Optional.ofNullable(PENDING_SPAWN.remove(player.getUUID()));
	}

	public static Optional<Rule.Anchor> currentAnchor(ServerPlayer player) {
		return Optional.ofNullable(CURRENT_ANCHOR.get(player.getUUID()));
	}

	public static boolean start(ServerPlayer player, Purpose purpose) {
		return start(player, purpose, Rule.EventType.BROKE, "");
	}

	public static boolean start(ServerPlayer player, Purpose purpose, Rule.EventType eventType, String eventBlock) {
		Rule.Anchor anchor = CURRENT_ANCHOR.get(player.getUUID());
		if (anchor == null) {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.no_anchor"));
			return false;
		}
		SELECTING.put(player.getUUID(), new State(anchor, purpose, eventType, eventBlock));
		player.sendSystemMessage(Component.translatable("mapcreator.sel.start", purposeName(purpose)));
		return true;
	}

	public static void confirm(ServerPlayer player) {
		State state = SELECTING.get(player.getUUID());
		if (state == null || !state.complete()) {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.nothing"));
			return;
		}
		Rule.Area area = new Rule.Area(state.a, state.b);
		RuleStorage storage = RuleStorage.get(player.level().getServer());
		Rule.Kind kind = RuleEditor.editKind(player);
		if (kind == Rule.Kind.NPC && storage.find(state.anchor, kind).isEmpty()) {
			SELECTING.remove(player.getUUID());
			player.sendSystemMessage(Component.translatable("mapcreator.npc.none"));
			return;
		}
		Rule rule = storage.find(state.anchor, kind).orElseGet(() -> new Rule(UUID.randomUUID(), Rule.Kind.RULE,
				state.anchor, Rule.Trigger.of(Rule.TriggerType.STAND), List.of(), List.of(), Rule.PlayerEffects.NONE,
				Optional.empty()));
		Rule updated;
		if (state.purpose == Purpose.TRIGGER_AREA) {
			Rule.Trigger t = rule.trigger();
			updated = new Rule(rule.id(), rule.kind(), rule.anchor(),
					new Rule.Trigger(t.type(), Optional.of(area), t.ifIds(), t.filter()),
					rule.events(), rule.mobs(), rule.effects(), rule.npc());
		} else {
			List<Rule.Event> events = new ArrayList<>(rule.events());
			events.add(new Rule.Event(state.eventType, Optional.of(area), state.eventBlock));
			updated = new Rule(rule.id(), rule.kind(), rule.anchor(), rule.trigger(), events, rule.mobs(),
					rule.effects(), rule.npc());
		}
		storage.put(updated);
		SELECTING.remove(player.getUUID());
		player.sendSystemMessage(Component.translatable("mapcreator.sel.saved", updated.shortId()));
	}

	public static void repeat(ServerPlayer player) {
		State old = SELECTING.get(player.getUUID());
		if (old == null) {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.nothing"));
			return;
		}
		SELECTING.put(player.getUUID(), new State(old.anchor, old.purpose, old.eventType, old.eventBlock));
		player.sendSystemMessage(Component.translatable("mapcreator.sel.start", purposeName(old.purpose)));
	}

	public static void cancel(ServerPlayer player) {
		if (SELECTING.remove(player.getUUID()) != null) {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.cancelled"));
		} else {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.nothing"));
		}
	}

	private static Component purposeName(Purpose purpose) {
		return Component.translatable(purpose == Purpose.TRIGGER_AREA
				? "mapcreator.sel.purpose.trigger" : "mapcreator.sel.purpose.event");
	}

	private static void sendConfirmPrompt(ServerPlayer player, State state) {
		BlockPos a = state.a;
		BlockPos b = state.b;
		long size = (long) (Math.abs(a.getX() - b.getX()) + 1) * (Math.abs(a.getY() - b.getY()) + 1)
				* (Math.abs(a.getZ() - b.getZ()) + 1);
		MutableComponent line = Component.translatable("mapcreator.sel.prompt", a.getX(), a.getY(), a.getZ(),
				b.getX(), b.getY(), b.getZ(), size);
		line.append(" ").append(button("mapcreator.sel.confirm", "mapcreator confirm", ChatFormatting.GREEN))
				.append(" ").append(button("mapcreator.sel.repeat", "mapcreator repeat", ChatFormatting.YELLOW))
				.append(" ").append(button("mapcreator.sel.cancel", "mapcreator cancel", ChatFormatting.RED));
		player.sendSystemMessage(line);
	}

	private static Component button(String key, String command, ChatFormatting color) {
		return Component.translatable(key).withStyle(style -> style
				.withClickEvent(new ClickEvent.RunCommand(command)).withColor(color));
	}
}
