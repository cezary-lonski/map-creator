package com.chicken.mapcreator.rules;

import com.chicken.mapcreator.selection.SelectionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;

/** Edycja reguly na biezacej kotwicy gracza. Wywolywana z GUI (pakiet ActionPayload). Cala walidacja jest tutaj. */
public final class RuleEditor {
	private RuleEditor() {}

	/** Co gracz aktualnie edytuje w GUI: zwykla regule czy NPC kotwicy. */
	private static final java.util.Map<UUID, Rule.Kind> EDIT_KIND = new java.util.HashMap<>();

	public static Rule.Kind editKind(ServerPlayer player) {
		return EDIT_KIND.getOrDefault(player.getUUID(), Rule.Kind.RULE);
	}

	/** Otwiera menu mapy (skrzynia) dla wybranej kotwicy. */
	public static void sendMenu(ServerPlayer player, Rule.Anchor anchor) {
		com.chicken.mapcreator.menu.Menus.main(player);
	}

	/** Regula (albo NPC) na biezacej kotwicy gracza. */
	public static Optional<Rule> currentRule(ServerPlayer player, Rule.Kind kind) {
		return SelectionManager.currentAnchor(player)
				.flatMap(anchor -> RuleStorage.get(player.level().getServer()).find(anchor, kind));
	}

	/** Zmienia (albo tworzy) regule na biezacej kotwicy; uzywane przez menu. */
	public static void edit(ServerPlayer player, UnaryOperator<Rule> change) {
		modify(player, change);
	}

	public static void apply(ServerPlayer player, String action, String arg) {
		Optional<Rule.Anchor> anchor = SelectionManager.currentAnchor(player);
		if (anchor.isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.sel.no_anchor"));
			return;
		}
		String a = arg == null ? "" : arg.trim();
		switch (action) {
			case "open" -> sendMenu(player, anchor.get());
			case "edit_kind" -> EDIT_KIND.put(player.getUUID(), a.equals("NPC") ? Rule.Kind.NPC : Rule.Kind.RULE);
			case "filter" -> modify(player, r -> new Rule(r.id(), r.kind(), r.anchor(), new Rule.Trigger(r.trigger().type(),
					r.trigger().area(), r.trigger().ifIds(), a), r.events(), r.mobs(), r.effects(), r.npc()));
			case "npc_immune" -> modifyNpc(player, n -> withNpc(n, n.action(), n.tradeIn(), n.tradeOut(), n.damage(), n.goTo(), a.equals("1")));
			case "npc_appear" -> {
				boolean on = a.equals("1");
				modifyNpc(player, n -> withAppear(n, on));
				currentRule(player, Rule.Kind.NPC).ifPresent(r -> {
					if (on) {
						NpcManager.removeEntity(player.level().getServer(), r);
					} else {
						NpcManager.spawn(player.level().getServer(), r);
					}
				});
			}
			case "npc_none" -> modifyNpc(player, n -> withNpc(n, "NONE", n.tradeIn(), n.tradeOut(), n.damage(), n.goTo(), n.playerImmune()));
			case "npc_attack" -> modifyNpc(player, n -> withNpc(n, "ATTACK", n.tradeIn(), n.tradeOut(),
					parseDouble(a, 1.0), n.goTo(), n.playerImmune()));
			case "npc_go" -> {
				String[] c = a.split(",");
				if (c.length == 3) {
					BlockPos goal = new BlockPos(parseInt(c[0].trim(), 0, -30000000, 30000000),
							parseInt(c[1].trim(), 64, -64, 320), parseInt(c[2].trim(), 0, -30000000, 30000000));
					modifyNpc(player, n -> withNpc(n, "GO", n.tradeIn(), n.tradeOut(), n.damage(), Optional.of(goal), n.playerImmune()));
				}
			}
			case "npc_trade" -> setTrade(player, anchor.get(), a);
			case "trigger" -> parseTrigger(a).ifPresentOrElse(type -> {
				if (type.name().startsWith("NPC_") && editKind(player) != Rule.Kind.NPC) {
					player.sendSystemMessage(Component.translatable("mapcreator.command.bad_trigger"));
					return;
				}
				modify(player, r -> withTrigger(r, type, r.trigger().ifIds()));
				if (type == Rule.TriggerType.NPC_HURT) {
					modifyNpc(player, n -> withNpc(n, n.action(), n.tradeIn(), n.tradeOut(), n.damage(), n.goTo(), false));
				}
			}, () -> player.sendSystemMessage(Component.translatable("mapcreator.command.bad_trigger")));
			case "after" -> findRule(player, a).ifPresentOrElse(
					src -> modify(player, r -> withTrigger(r, Rule.TriggerType.AFTER_EVENT, List.of(src.id()))),
					() -> player.sendSystemMessage(Component.translatable("mapcreator.command.not_found", a)));
			case "if" -> setIf(player, a);
			case "need", "forbid", "cond_clear" -> setCondition(player, action, a);
			case "area" -> SelectionManager.start(player, SelectionManager.Purpose.TRIGGER_AREA);
			case "event_start" -> startEvent(player, a);
			case "kill" -> modify(player, r -> withEffects(r, new Rule.PlayerEffects(true, a, r.effects().give(),
					r.effects().tp(), r.effects().effects(), r.effects().screenText())));
			case "kill_off" -> modify(player, r -> withEffects(r, new Rule.PlayerEffects(false, "", r.effects().give(),
					r.effects().tp(), r.effects().effects(), r.effects().screenText())));
			case "text" -> modify(player, r -> withEffects(r, new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(),
					r.effects().give(), r.effects().tp(), r.effects().effects(), a)));
			case "give" -> addGive(player, a);
			case "effect" -> addEffect(player, a);
			case "tp_here" -> modify(player, r -> withEffects(r, new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(),
					r.effects().give(), Optional.of(player.blockPosition()), r.effects().effects(), r.effects().screenText())));
			case "mob_add" -> addMob(player, a);
			case "camera" -> addCamera(player, a);
			case "clear_camera" -> modify(player, r -> withEffects(r, new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(),
					r.effects().give(), r.effects().tp(), r.effects().effects().stream()
					.filter(e -> !e.effect().startsWith(com.chicken.mapcreator.net.CameraEffectPayload.ID_PREFIX)).toList(),
					r.effects().screenText())));
			case "spawn_select" -> SelectionManager.startSpawnSelect(player);
			case "mob_test" -> testMobs(player, anchor.get());
			case "npc_create" -> {
				String[] np = (a + "|").split("\\|", -1);
				NpcManager.create(player, anchor.get(), np[0], np[1]);
			}
			case "npc_delete" -> NpcManager.delete(player, anchor.get());
			case "mob_clear" -> {
				modify(player, r -> new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(),
						List.of(), r.effects(), r.npc()));
				player.sendSystemMessage(Component.translatable("mapcreator.mob.cleared"));
			}
			case "clear_effects" -> modify(player, r -> withEffects(r, Rule.PlayerEffects.NONE));
			case "delete" -> {
				RuleStorage storage = RuleStorage.get(player.level().getServer());
				storage.find(anchor.get(), Rule.Kind.RULE).ifPresent(r -> storage.remove(r.id()));
				player.displayClientMessage(Component.translatable("mapcreator.gui.deleted"), true);
			}
			default -> player.sendSystemMessage(Component.translatable("mapcreator.gui.unknown_action", action));
		}
	}

	/** Test spawn: od razu wywoluje wszystkie moby reguly (bez wywolywacza), zeby sprawdzic, ze sie pojawiaja. */
	private static void testMobs(ServerPlayer player, Rule.Anchor anchor) {
		Optional<Rule> rule = RuleStorage.get(player.level().getServer()).find(anchor, Rule.Kind.RULE);
		if (rule.isEmpty() || rule.get().mobs().isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.mob.none"));
			return;
		}
		for (Rule.MobSpec spec : rule.get().mobs()) {
			MobSpawner.spawn(player.level().getServer(), rule.get(), spec, player);
		}
	}

	/** Efekt kamery po wywolaniu reguly: arg "typ,sekundy" (typ: wave, upside_down, sideways, blur, nearsighted, pixelate). */
	private static void addCamera(ServerPlayer player, String arg) {
		String[] p = (arg + ",,").split(",", -1);
		String type = p[0].trim();
		if (!com.chicken.mapcreator.net.CameraEffectPayload.TYPES.contains(type)) {
			return;
		}
		int seconds = parseInt(p[1].trim(), 10, 1, 3600);
		int power = parseInt(p[2].trim(), 1, 1, 100000);
		modify(player, r -> {
			List<Rule.EffectSpec> list = new ArrayList<>(r.effects().effects());
			list.add(new Rule.EffectSpec(com.chicken.mapcreator.net.CameraEffectPayload.ID_PREFIX + type, power, seconds));
			return withEffects(r, new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(), r.effects().give(),
					r.effects().tp(), list, r.effects().screenText()));
		});
	}

	private static Rule.NpcSpec withAppear(Rule.NpcSpec n, boolean v) {
		return new Rule.NpcSpec(n.mob(), n.skin(), n.action(), n.tradeIn(), n.tradeOut(), n.damage(), n.goTo(),
				n.deleteOnTrigger(), n.uuid(), n.playerImmune(), n.pos(), v);
	}

	private static double parseDouble(String s, double fallback) {
		try {
			return Math.max(0.0, Math.min(100.0, Double.parseDouble(s.trim().replace(',', '.'))));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static Rule.NpcSpec withNpc(Rule.NpcSpec n, String action, String tradeIn, String tradeOut, double damage,
			Optional<BlockPos> goTo, boolean immune) {
		return new Rule.NpcSpec(n.mob(), n.skin(), action, tradeIn, tradeOut, damage, goTo, n.deleteOnTrigger(), n.uuid(), immune, n.pos(), n.appear());
	}

	/** Zmienia ustawienia NPC na kotwicy i podaje zmiany od razu do bytu (np. oferta handlu). */
	private static void modifyNpc(ServerPlayer player, UnaryOperator<Rule.NpcSpec> change) {
		Rule.Anchor anchor = SelectionManager.currentAnchor(player).orElseThrow();
		RuleStorage storage = RuleStorage.get(player.level().getServer());
		Optional<Rule> rule = storage.find(anchor, Rule.Kind.NPC);
		if (rule.isEmpty() || rule.get().npc().isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.npc.none"));
			return;
		}
		Rule r = rule.get();
		Rule.NpcSpec spec = change.apply(r.npc().get());
		storage.put(new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), r.mobs(), r.effects(), Optional.of(spec)));
		net.minecraft.world.entity.Entity entity = NpcManager.entityOf(player.level().getServer(), r);
		NpcManager.applyTrade(entity, spec);
		player.displayClientMessage(Component.translatable("mapcreator.gui.saved", r.shortId()), true);
	}

	/** Trade: arg "wejscie,ilosc,wyjscie,ilosc". Mob zmienia sie na wiesniaka z jedna oferta. */
	private static void setTrade(ServerPlayer player, Rule.Anchor anchor, String arg) {
		String[] p = (arg + ",,,,").split(",", -1);
		String in = p[0].trim() + "," + (p[1].isBlank() ? "1" : p[1].trim());
		String out = p[2].trim() + "," + (p[3].isBlank() ? "1" : p[3].trim());
		if (NpcManager.stackWithCount(in).isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.gui.bad_item", p[0]));
			return;
		}
		if (NpcManager.stackWithCount(out).isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.gui.bad_item", p[2]));
			return;
		}
		RuleStorage storage = RuleStorage.get(player.level().getServer());
		Optional<Rule> rule = storage.find(anchor, Rule.Kind.NPC);
		if (rule.isEmpty() || rule.get().npc().isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.npc.none"));
			return;
		}
		Rule.NpcSpec old = rule.get().npc().get();
		Rule.NpcSpec base = withNpc(old, "TRADE", in, out, old.damage(), old.goTo(), old.playerImmune());
		if (!old.mob().equals("minecraft:villager")) {
			NpcManager.create(player, anchor, "minecraft:villager", "", Optional.of(base));
		} else {
			modifyNpc(player, n -> base);
		}
	}

	private static Optional<Rule.TriggerType> parseTrigger(String name) {
		try {
			return Optional.of(Rule.TriggerType.valueOf(name.toUpperCase()));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	private static Optional<Rule> findRule(ServerPlayer player, String prefix) {
		String p = prefix.toLowerCase();
		return RuleStorage.get(player.level().getServer()).all().stream()
				.filter(r -> !p.isEmpty() && (r.shortId().startsWith(p) || r.id().toString().startsWith(p))).findFirst();
	}

	/** Warunki: need = skrypt sie wykonal, forbid = skrypt sie nie wykonal, cond_clear = usun warunki. */
	private static void setCondition(ServerPlayer player, String action, String ids) {
		List<UUID> found = new ArrayList<>();
		for (String part : ids.split("[, ]+")) {
			if (part.isBlank()) {
				continue;
			}
			Optional<Rule> rule = findRule(player, part);
			if (rule.isEmpty()) {
				player.sendSystemMessage(Component.translatable("mapcreator.command.not_found", part));
				return;
			}
			found.add(rule.get().id());
		}
		modify(player, r -> r);
		Optional<Rule> current = currentRule(player, editKind(player));
		if (current.isEmpty()) {
			return;
		}
		ConditionStorage cs = ConditionStorage.get(player.level().getServer());
		UUID id = current.get().id();
		ConditionStorage.Cond c = cs.of(id);
		switch (action) {
			case "need" -> cs.set(id, found, c.forbids());
			case "forbid" -> cs.set(id, c.needs(), found);
			default -> cs.set(id, List.of(), List.of());
		}
	}

	private static void setIf(ServerPlayer player, String ids) {
		List<UUID> found = new ArrayList<>();
		for (String part : ids.split("[, ]+")) {
			if (part.isBlank()) {
				continue;
			}
			Optional<Rule> rule = findRule(player, part);
			if (rule.isEmpty()) {
				player.sendSystemMessage(Component.translatable("mapcreator.command.not_found", part));
				return;
			}
			found.add(rule.get().id());
		}
		if (!found.isEmpty()) {
			modify(player, r -> withTrigger(r, Rule.TriggerType.IF_HAPPENED, found));
		}
	}

	private static void startEvent(ServerPlayer player, String arg) {
		if (arg.equals("broke")) {
			SelectionManager.start(player, SelectionManager.Purpose.EVENT_AREA, Rule.EventType.BROKE, "");
		} else if (arg.startsWith("change:") && Identifier_ok(arg.substring(7))) {
			SelectionManager.start(player, SelectionManager.Purpose.EVENT_AREA, Rule.EventType.CHANGE_BLOCK, arg.substring(7));
		} else {
			player.sendSystemMessage(Component.translatable("mapcreator.gui.bad_block"));
		}
	}

	private static boolean Identifier_ok(String id) {
		return net.minecraft.resources.Identifier.tryParse(id) != null
				&& net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getOptional(net.minecraft.resources.Identifier.tryParse(id)).isPresent();
	}

	private static void addGive(ServerPlayer player, String arg) {
		String[] parts = arg.split(",");
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(parts[0].trim());
		if (id == null || net.minecraft.core.registries.BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.gui.bad_item", parts[0]));
			return;
		}
		int count = parseInt(parts.length > 1 ? parts[1].trim() : "1", 1, 1, 99);
		modify(player, r -> {
			List<Rule.ItemSpec> list = new ArrayList<>(r.effects().give());
			list.add(new Rule.ItemSpec(id.toString(), count, ""));
			return withEffects(r, new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(), list,
					r.effects().tp(), r.effects().effects(), r.effects().screenText()));
		});
	}

	private static void addEffect(ServerPlayer player, String arg) {
		String[] parts = arg.split(",");
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(parts[0].trim());
		if (id == null || net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.get(id).isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.gui.bad_effect", parts[0]));
			return;
		}
		int seconds = parseInt(parts.length > 1 ? parts[1].trim() : "10", 10, 1, 3600);
		int amp = parseInt(parts.length > 2 ? parts[2].trim() : "0", 0, 0, 255);
		modify(player, r -> {
			List<Rule.EffectSpec> list = new ArrayList<>(r.effects().effects());
			list.add(new Rule.EffectSpec(id.toString(), amp, seconds));
			return withEffects(r, new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(), r.effects().give(),
					r.effects().tp(), list, r.effects().screenText()));
		});
	}

	/** arg: mob|nick|AKCJA|przedmiot|damage|offhand|head|chest|legs|feet|drop (pola oddzielone znakiem |). */
	private static void addMob(ServerPlayer player, String arg) {
		String[] p = (arg + "|||||||||||").split("\\|", -1);
		net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(p[0].trim());
		if (id == null || net.minecraft.world.entity.EntityType.byString(id.toString()).isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.gui.bad_mob", p[0]));
			return;
		}
		String action = p[2].trim().equalsIgnoreCase("ATTACK") ? "ATTACK" : "THROW";
		double damage;
		try {
			damage = Math.max(0.0, Math.min(100.0, Double.parseDouble(p[4].trim().replace(',', '.'))));
		} catch (NumberFormatException e) {
			damage = 1.0;
		}
		for (int i : new int[]{3, 5, 6, 7, 8, 9, 10}) {
			String item = p[i].trim();
			if (!item.isEmpty() && MobSpawner.stack(item).isEmpty()) {
				player.sendSystemMessage(Component.translatable("mapcreator.gui.bad_item", item));
				return;
			}
		}
		Rule.MobSpec spec = new Rule.MobSpec(id.toString(), p[1].trim(), action, p[3].trim(), damage,
				p[5].trim(), p[6].trim(), p[7].trim(), p[8].trim(), p[9].trim(), p[10].trim(),
				SelectionManager.takeSpawn(player), "", Optional.empty(), Rule.MobExtras.DEFAULT);
		modify(player, r -> {
			List<Rule.MobSpec> list = new ArrayList<>(r.mobs());
			list.add(spec);
			return new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), list, r.effects(), r.npc());
		});
		Rule.Anchor anchor = SelectionManager.currentAnchor(player).orElseThrow();
		RuleStorage.get(player.level().getServer()).find(anchor, editKind(player)).ifPresent(saved -> {
			BlockPos at = spec.spawn().orElse(anchor.pos().above());
			player.sendSystemMessage(Component.translatable("mapcreator.mob.added", spec.mob(), saved.shortId(),
					saved.mobs().size(), at.getX(), at.getY(), at.getZ()));
		});
	}

	private static int parseInt(String s, int fallback, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(s)));
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static Rule withTrigger(Rule r, Rule.TriggerType type, List<UUID> ifIds) {
		return new Rule(r.id(), r.kind(), r.anchor(),
				new Rule.Trigger(type, r.trigger().area(), ifIds, r.trigger().filter()),
				r.events(), r.mobs(), r.effects(), r.npc());
	}

	private static Rule withEffects(Rule r, Rule.PlayerEffects fx) {
		return new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), r.mobs(), fx, r.npc());
	}

	/** Zmienia (albo tworzy) regule na biezacej kotwicy i potwierdza zapis napisem nad paskiem. */
	private static void modify(ServerPlayer player, UnaryOperator<Rule> change) {
		Rule.Anchor anchor = SelectionManager.currentAnchor(player).orElseThrow();
		RuleStorage storage = RuleStorage.get(player.level().getServer());
		Rule.Kind kind = editKind(player);
		Optional<Rule> found = storage.find(anchor, kind);
		if (found.isEmpty() && kind == Rule.Kind.NPC) {
			player.sendSystemMessage(Component.translatable("mapcreator.npc.none"));
			return;
		}
		Rule rule = found.orElseGet(() -> new Rule(UUID.randomUUID(), Rule.Kind.RULE,
				anchor, Rule.Trigger.of(Rule.TriggerType.CLICK), List.of(), List.of(), Rule.PlayerEffects.NONE,
				Optional.empty()));
		Rule updated = change.apply(rule);
		storage.put(updated);
		player.displayClientMessage(Component.translatable("mapcreator.gui.saved", updated.shortId()), true);
	}
}
