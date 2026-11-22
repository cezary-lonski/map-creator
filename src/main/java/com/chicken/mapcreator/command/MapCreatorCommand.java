package com.chicken.mapcreator.command;

import com.chicken.mapcreator.Perms;
import com.chicken.mapcreator.rules.FiredStorage;
import com.chicken.mapcreator.rules.Rule;
import com.chicken.mapcreator.rules.RuleStorage;
import com.chicken.mapcreator.selection.SelectionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Komendy diagnostyczne etapu 2: sprawdzanie zapisu regul w swiecie. GUI dojdzie w pozniejszych etapach. */
public final class MapCreatorCommand {
	private MapCreatorCommand() {}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> build(dispatcher));
	}

	private static void build(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("mapcreator")
				.then(Commands.literal("add").executes(MapCreatorCommand::add))
				.then(Commands.literal("list").executes(MapCreatorCommand::list))
				.then(Commands.literal("clear").executes(MapCreatorCommand::clear))
				.then(Commands.literal("area")
						.then(Commands.literal("trigger").executes(c -> startArea(c, SelectionManager.Purpose.TRIGGER_AREA)))
						.then(Commands.literal("event").executes(c -> startArea(c, SelectionManager.Purpose.EVENT_AREA))))
				.then(Commands.literal("trigger")
						.then(Commands.argument("type", StringArgumentType.word()).executes(MapCreatorCommand::setTrigger)))
				.then(Commands.literal("after")
						.then(Commands.argument("id", StringArgumentType.word()).executes(MapCreatorCommand::setAfter)))
				.then(Commands.literal("kill")
						.then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> editEffects(c,
								fx -> new Rule.PlayerEffects(true, StringArgumentType.getString(c, "text"), fx.give(), fx.tp(), fx.effects(), fx.screenText())))))
				.then(Commands.literal("text")
						.then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> editEffects(c,
								fx -> new Rule.PlayerEffects(fx.kill(), fx.killText(), fx.give(), fx.tp(), fx.effects(), StringArgumentType.getString(c, "text"))))))
				.then(Commands.literal("give")
						.then(Commands.argument("item", StringArgumentType.word())
								.then(Commands.argument("count", IntegerArgumentType.integer(1, 99)).executes(c -> editEffects(c, fx -> {
									List<Rule.ItemSpec> list = new java.util.ArrayList<>(fx.give());
									list.add(new Rule.ItemSpec(StringArgumentType.getString(c, "item"), IntegerArgumentType.getInteger(c, "count"), ""));
									return new Rule.PlayerEffects(fx.kill(), fx.killText(), list, fx.tp(), fx.effects(), fx.screenText());
								})))))
				.then(Commands.literal("effect")
						.then(Commands.argument("effect", StringArgumentType.word())
								.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
										.then(Commands.argument("amplifier", IntegerArgumentType.integer(0, 255)).executes(c -> editEffects(c, fx -> {
											List<Rule.EffectSpec> list = new java.util.ArrayList<>(fx.effects());
											list.add(new Rule.EffectSpec(StringArgumentType.getString(c, "effect"),
													IntegerArgumentType.getInteger(c, "amplifier"), IntegerArgumentType.getInteger(c, "seconds")));
											return new Rule.PlayerEffects(fx.kill(), fx.killText(), fx.give(), fx.tp(), list, fx.screenText());
										}))))))
				.then(Commands.literal("tp")
						.then(Commands.argument("x", IntegerArgumentType.integer())
								.then(Commands.argument("y", IntegerArgumentType.integer())
										.then(Commands.argument("z", IntegerArgumentType.integer()).executes(c -> editEffects(c,
												fx -> new Rule.PlayerEffects(fx.kill(), fx.killText(), fx.give(), Optional.of(new BlockPos(
														IntegerArgumentType.getInteger(c, "x"), IntegerArgumentType.getInteger(c, "y"),
														IntegerArgumentType.getInteger(c, "z"))), fx.effects(), fx.screenText())))))))
				.then(Commands.literal("eventtype")
						.then(Commands.literal("broke").executes(c -> setLastEvent(c, Rule.EventType.BROKE, "")))
						.then(Commands.literal("change")
								.then(Commands.argument("block", StringArgumentType.word()).executes(c -> setLastEvent(c,
										Rule.EventType.CHANGE_BLOCK, StringArgumentType.getString(c, "block"))))))
				.then(Commands.literal("if")
						.then(Commands.argument("ids", StringArgumentType.greedyString()).executes(MapCreatorCommand::setIfHappened)))
				.then(Commands.literal("storage")
						.executes(MapCreatorCommand::storageList)
						.then(Commands.literal("clear").executes(MapCreatorCommand::storageClear)))
				.then(Commands.literal("pick")
						.then(Commands.literal("base").then(Commands.argument("id", StringArgumentType.greedyString())
								.suggests((c, b) -> com.chicken.mapcreator.menu.ItemCreator.suggest(
										net.minecraft.core.registries.BuiltInRegistries.ITEM.keySet(), b))
								.executes(c -> pickItem(c, "base"))))
						.then(Commands.literal("breaks").then(Commands.argument("id", StringArgumentType.greedyString())
								.suggests((c, b) -> com.chicken.mapcreator.menu.ItemCreator.suggest(
										net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet(), b))
								.executes(c -> pickItem(c, "breaks")))))
				.then(Commands.literal("ai")
						.then(Commands.literal("run").executes(c -> {
							ServerPlayer pl = c.getSource().getPlayer();
							if (pl != null) {
								com.chicken.mapcreator.ai.AiAssistant.applyAll(pl);
							}
							return 1;
						}))
						.then(Commands.literal("ask").then(Commands.argument("text", StringArgumentType.greedyString()).executes(c -> {
							ServerPlayer pl = c.getSource().getPlayer();
							if (pl != null) {
								com.chicken.mapcreator.ai.AiAssistant.ask(pl, StringArgumentType.getString(c, "text"));
							}
							return 1;
						}))))
				.then(Commands.literal("play").executes(c -> {
					com.chicken.mapcreator.rules.MapControl.play(c.getSource().getServer());
					return 1;
				}))
				.then(Commands.literal("stop").executes(c -> {
					com.chicken.mapcreator.rules.MapControl.stop(c.getSource().getServer());
					return 1;
				}))
				.then(Commands.literal("reset").executes(c -> {
					int n = com.chicken.mapcreator.rules.MapControl.reset(c.getSource().getServer());
					c.getSource().sendSuccess(() -> Component.translatable("mapcreator.reset.done", n), false);
					return n;
				}))
				.then(Commands.literal("confirm").executes(c -> selection(c, 0)))
				.then(Commands.literal("repeat").executes(c -> selection(c, 1)))
				.then(Commands.literal("cancel").executes(c -> selection(c, 2)))
				.then(Commands.literal("remove")
						.then(Commands.argument("id", StringArgumentType.word()).executes(MapCreatorCommand::remove))));
	}

	private static int add(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			src.sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		BlockPos below = player.blockPosition().below();
		Rule.Anchor anchor = new Rule.Anchor(src.getLevel().dimension().identifier().toString(), below);
		RuleStorage storage = RuleStorage.get(src.getServer());
		Rule rule = new Rule(UUID.randomUUID(), Rule.Kind.RULE, anchor, Rule.Trigger.of(Rule.TriggerType.CLICK),
				List.of(), List.of(), new Rule.PlayerEffects(true, "test", List.of(), Optional.empty(), List.of(), ""),
				Optional.empty());
		storage.put(rule);
		src.sendSuccess(() -> Component.translatable("mapcreator.command.added", rule.shortId(),
				below.getX(), below.getY(), below.getZ()), false);
		return 1;
	}

	private static int pickItem(CommandContext<CommandSourceStack> ctx, String kind) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			ctx.getSource().sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		return com.chicken.mapcreator.menu.ItemCreator.pick(player, kind, StringArgumentType.getString(ctx, "id"));
	}

	private static int startArea(CommandContext<CommandSourceStack> ctx, SelectionManager.Purpose purpose) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			ctx.getSource().sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		return SelectionManager.start(player, purpose) ? 1 : 0;
	}

	private static int selection(CommandContext<CommandSourceStack> ctx, int action) {
		ServerPlayer player = ctx.getSource().getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			ctx.getSource().sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		switch (action) {
			case 0 -> SelectionManager.confirm(player);
			case 1 -> SelectionManager.repeat(player);
			default -> SelectionManager.cancel(player);
		}
		return 1;
	}

	/** Ustawia typ wywolywacza reguly na biezacej kotwicy (diagnostyka do czasu GUI). */
	private static int setTrigger(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			src.sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		Rule.TriggerType type;
		try {
			type = Rule.TriggerType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase());
		} catch (IllegalArgumentException e) {
			src.sendFailure(Component.translatable("mapcreator.command.bad_trigger"));
			return 0;
		}
		return updateAtAnchor(src, player, rule -> new Rule(rule.id(), rule.kind(), rule.anchor(),
				new Rule.Trigger(type, rule.trigger().area(), rule.trigger().ifIds(), rule.trigger().filter()),
				rule.events(), rule.mobs(), rule.effects(), rule.npc()));
	}

	private static int setAfter(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			src.sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		String prefix = StringArgumentType.getString(ctx, "id").toLowerCase();
		Optional<Rule> source = RuleStorage.get(src.getServer()).all().stream()
				.filter(r -> r.shortId().startsWith(prefix) || r.id().toString().startsWith(prefix)).findFirst();
		if (source.isEmpty()) {
			src.sendFailure(Component.translatable("mapcreator.command.not_found", prefix));
			return 0;
		}
		UUID sourceId = source.get().id();
		return updateAtAnchor(src, player, rule -> new Rule(rule.id(), rule.kind(), rule.anchor(),
				new Rule.Trigger(Rule.TriggerType.AFTER_EVENT, rule.trigger().area(), List.of(sourceId), rule.trigger().filter()),
				rule.events(), rule.mobs(), rule.effects(), rule.npc()));
	}

	private static int editEffects(CommandContext<CommandSourceStack> ctx, java.util.function.UnaryOperator<Rule.PlayerEffects> change) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			src.sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		return updateAtAnchor(src, player, rule -> new Rule(rule.id(), rule.kind(), rule.anchor(), rule.trigger(),
				rule.events(), rule.mobs(), change.apply(rule.effects()), rule.npc()));
	}

	/** Ustawia typ ostatnio zaznaczonego zdarzenia (BROKE albo CHANGE_BLOCK z blokiem). */
	private static int setLastEvent(CommandContext<CommandSourceStack> ctx, Rule.EventType type, String block) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			src.sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		return updateAtAnchor(src, player, rule -> {
			List<Rule.Event> events = new java.util.ArrayList<>(rule.events());
			if (events.isEmpty()) {
				return rule;
			}
			Rule.Event last = events.remove(events.size() - 1);
			events.add(new Rule.Event(type, last.area(), block));
			return new Rule(rule.id(), rule.kind(), rule.anchor(), rule.trigger(), events, rule.mobs(), rule.effects(), rule.npc());
		});
	}

	/** Wywolywacz "Jesli te wydarzenia sie staly": id regul po przecinku (krotkie id z listy). */
	private static int setIfHappened(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		ServerPlayer player = src.getPlayer();
		if (player == null || !Perms.canEdit(player)) {
			src.sendFailure(Component.translatable("mapcreator.command.no_permission"));
			return 0;
		}
		List<Rule> all = RuleStorage.get(src.getServer()).all();
		List<UUID> ids = new java.util.ArrayList<>();
		for (String part : StringArgumentType.getString(ctx, "ids").split("[,\s]+")) {
			if (part.isBlank()) {
				continue;
			}
			String prefix = part.toLowerCase();
			Optional<Rule> found = all.stream()
					.filter(r -> r.shortId().startsWith(prefix) || r.id().toString().startsWith(prefix)).findFirst();
			if (found.isEmpty()) {
				src.sendFailure(Component.translatable("mapcreator.command.not_found", part));
				return 0;
			}
			ids.add(found.get().id());
		}
		if (ids.isEmpty()) {
			src.sendFailure(Component.translatable("mapcreator.command.not_found", ""));
			return 0;
		}
		return updateAtAnchor(src, player, rule -> new Rule(rule.id(), rule.kind(), rule.anchor(),
				new Rule.Trigger(Rule.TriggerType.IF_HAPPENED, rule.trigger().area(), ids, rule.trigger().filter()),
				rule.events(), rule.mobs(), rule.effects(), rule.npc()));
	}

	private static int storageList(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		RuleStorage rules = RuleStorage.get(src.getServer());
		List<UUID> fired = FiredStorage.get(src.getServer()).allFired();
		src.sendSuccess(() -> Component.translatable("mapcreator.storage.header", fired.size()), false);
		for (UUID id : fired) {
			String shown = rules.findById(id).map(Rule::shortId).orElse(id.toString().substring(0, 6) + "?");
			src.sendSuccess(() -> Component.literal(" - [" + shown + "]"), false);
		}
		return fired.size();
	}

	private static int storageClear(CommandContext<CommandSourceStack> ctx) {
		FiredStorage.get(ctx.getSource().getServer()).clear();
		ctx.getSource().sendSuccess(() -> Component.translatable("mapcreator.storage.cleared"), false);
		return 1;
	}

	private static int updateAtAnchor(CommandSourceStack src, ServerPlayer player, java.util.function.UnaryOperator<Rule> change) {
		Optional<Rule.Anchor> anchor = com.chicken.mapcreator.selection.SelectionManager.currentAnchor(player);
		if (anchor.isEmpty()) {
			src.sendFailure(Component.translatable("mapcreator.sel.no_anchor"));
			return 0;
		}
		RuleStorage storage = RuleStorage.get(src.getServer());
		Rule rule = storage.find(anchor.get(), Rule.Kind.RULE).orElseGet(() -> new Rule(UUID.randomUUID(), Rule.Kind.RULE,
				anchor.get(), Rule.Trigger.of(Rule.TriggerType.CLICK), List.of(), List.of(), Rule.PlayerEffects.NONE,
				Optional.empty()));
		Rule updated = change.apply(rule);
		storage.put(updated);
		src.sendSuccess(() -> Component.translatable("mapcreator.command.updated", updated.shortId(),
				updated.trigger().type().name()), false);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		List<Rule> rules = RuleStorage.get(src.getServer()).all();
		src.sendSuccess(() -> Component.translatable("mapcreator.command.list_header", rules.size()), false);
		for (Rule r : rules) {
			BlockPos p = r.anchor().pos();
			src.sendSuccess(() -> Component.translatable("mapcreator.command.list_entry", r.shortId(), r.kind().name(),
					r.trigger().type().name(), p.getX(), p.getY(), p.getZ()), false);
			r.trigger().area().ifPresent(a -> src.sendSuccess(() -> Component.translatable("mapcreator.command.list_area",
					a.a().getX(), a.a().getY(), a.a().getZ(), a.b().getX(), a.b().getY(), a.b().getZ()), false));
		}
		return rules.size();
	}

	private static int clear(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		int n = RuleStorage.get(src.getServer()).clear();
		src.sendSuccess(() -> Component.translatable("mapcreator.command.cleared", n), false);
		return n;
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		CommandSourceStack src = ctx.getSource();
		String prefix = StringArgumentType.getString(ctx, "id").toLowerCase();
		RuleStorage storage = RuleStorage.get(src.getServer());
		for (Rule r : storage.all()) {
			if (r.shortId().startsWith(prefix) || r.id().toString().startsWith(prefix)) {
				storage.remove(r.id());
				src.sendSuccess(() -> Component.translatable("mapcreator.command.removed", r.shortId()), false);
				return 1;
			}
		}
		src.sendFailure(Component.translatable("mapcreator.command.not_found", prefix));
		return 0;
	}
}
