package com.chicken.mapcreator.menu;

import com.chicken.mapcreator.ai.AiAssistant;
import com.chicken.mapcreator.ai.AiConfig;
import com.chicken.mapcreator.rules.BlockStorage;
import com.chicken.mapcreator.rules.FiredStorage;
import com.chicken.mapcreator.rules.MapControl;
import com.chicken.mapcreator.rules.ConditionStorage;
import com.chicken.mapcreator.rules.MapSettings;
import com.chicken.mapcreator.rules.MobSpawner;
import com.chicken.mapcreator.rules.NpcManager;
import com.chicken.mapcreator.rules.Rule;
import com.chicken.mapcreator.rules.RuleEditor;
import com.chicken.mapcreator.rules.RuleStorage;
import com.chicken.mapcreator.selection.SelectionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Wszystkie menu moda w stylu skrzyn / ekwipunku. Zmiany zapisuja sie od razu w regule na biezacej kotwicy. */
public final class Menus {
	private Menus() {}

	// ---------- pomocnicy ----------

	static String tr(ServerPlayer p, String key, Object... args) {
		return ServerLang.tr(p, key, args);
	}

	private static Component plain(String text) {
		return Component.literal(text).withStyle(s -> s.withItalic(false));
	}

	static ItemStack icon(Item item, String name, String... lore) {
		ItemStack stack = new ItemStack(item);
		stack.set(DataComponents.CUSTOM_NAME, plain(name).copy().withStyle(ChatFormatting.WHITE));
		if (lore.length > 0) {
			List<Component> lines = new ArrayList<>();
			for (String line : lore) {
				lines.add(plain(line).copy().withStyle(ChatFormatting.GRAY));
			}
			stack.set(DataComponents.LORE, new ItemLore(lines));
		}
		return stack;
	}

	static ItemStack glow(ItemStack stack) {
		stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		return stack;
	}

	private static ItemStack pane() {
		ItemStack stack = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		stack.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return stack;
	}

	static ItemStack marker(ServerPlayer p, String labelKey) {
		return icon(Items.LIGHT_GRAY_STAINED_GLASS_PANE, tr(p, labelKey), tr(p, "gui.menu.place_item"));
	}

	static void open(ServerPlayer p, String titleKey, int rows, Consumer<UiMenu> init) {
		p.level().getServer().execute(() -> UiMenu.open(p, Component.literal(tr(p, titleKey)), rows, menu -> {
			init.accept(menu);
			menu.fillEmpty(pane());
		}));
	}

	static ItemStack back(ServerPlayer p) {
		return icon(Items.ARROW, tr(p, "gui.back"));
	}

	static String idOf(ItemStack stack) {
		return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	static ItemStack stackOf(String id) {
		return MobSpawner.stack(id);
	}

	private static String mobIdFromEgg(ItemStack stack) {
		if (stack.getItem() instanceof SpawnEggItem egg) {
			EntityType<?> type = egg.getType(stack);
			return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
		}
		return null;
	}

	private static ItemStack eggFor(String mobId) {
		Optional<EntityType<?>> type = EntityType.byString(mobId);
		if (type.isPresent()) {
			SpawnEggItem egg = SpawnEggItem.byId(type.get());
			if (egg != null) {
				return new ItemStack(egg);
			}
		}
		return new ItemStack(Items.EGG);
	}

	// ---------- menu glowne ----------

	public static void main(ServerPlayer p) {
		open(p, "gui.title", 3, m -> {
			Optional<Rule> rule = RuleEditor.currentRule(p, Rule.Kind.RULE);
			String where = SelectionManager.currentAnchor(p).map(a -> a.pos().getX() + " " + a.pos().getY() + " " + a.pos().getZ()).orElse("?");
			List<String> info = new ArrayList<>();
			info.add(tr(p, "gui.anchor", where, rule.map(Rule::shortId).orElse("-")));
			rule.ifPresent(r -> {
				info.add(tr(p, "gui.menu.info_trigger", r.trigger().type().name()));
				info.add(tr(p, "gui.menu.info_counts", r.events().size(), r.mobs().size(), r.effects().give().size(), r.effects().effects().size()));
			});
			m.decor(4, icon(Items.PAPER, tr(p, "gui.title"), info.toArray(new String[0])));
			m.button(9, icon(Items.ENDER_EYE, tr(p, "gui.ai.chat"), tr(p, "gui.ai.chat_lore")), (pl, t, b) -> aiChat(pl));
			m.button(10, icon(Items.WRITABLE_BOOK, tr(p, "gui.edit")), (pl, t, b) -> {
				RuleEditor.apply(pl, "edit_kind", "RULE");
				edit(pl);
			});
			m.button(11, icon(Items.VILLAGER_SPAWN_EGG, tr(p, "gui.edit_npc"), NpcManager.describe(
					RuleStorage.get(p.level().getServer()), SelectionManager.currentAnchor(p).orElseThrow())), (pl, t, b) -> {
				RuleEditor.apply(pl, "edit_kind", "NPC");
				editNpc(pl);
			});
			m.button(12, icon(Items.ARMOR_STAND, tr(p, "gui.create_npc")), (pl, t, b) -> {
				RuleEditor.apply(pl, "edit_kind", "NPC");
				createNpc(pl, new String[]{"minecraft:villager", ""});
			});
			m.button(14, icon(Items.REPEATER, tr(p, "gui.settings.open"), tr(p, "gui.settings.lore")), (pl, t, b) -> settings(pl));
			m.button(15, icon(Items.ENDER_CHEST, tr(p, "gui.storage.open"), tr(p, "gui.storage.lore")), (pl, t, b) -> storage(pl));
			boolean playing = MapSettings.get(p.level().getServer()).playing();
			m.button(22, playing ? icon(Items.RED_CONCRETE, tr(p, "gui.stop"), tr(p, "gui.stop.lore"))
					: glow(icon(Items.LIME_CONCRETE, tr(p, "gui.play"), tr(p, "gui.play.lore"))), (pl, t, b) -> {
				if (playing) {
					MapControl.stop(pl.level().getServer());
				} else {
					MapControl.play(pl.level().getServer());
				}
				pl.closeContainer();
			});
			m.button(16, icon(Items.BARRIER, tr(p, "gui.esc")), (pl, t, b) -> pl.closeContainer());
		});
	}

	// ---------- Asystent AI ----------

	/** Przycisk "Chat AI" obok Edytuj: pyta o wiadomosc na czacie i wysyla ja do wybranego dostawcy AI. */
	private static void aiChat(ServerPlayer p) {
		if (AiConfig.key(AiConfig.provider()).isEmpty()) {
			p.sendSystemMessage(Component.literal(tr(p, "gui.ai.no_key", AiConfig.provider())));
			aiSettings(p);
			return;
		}
		AiChatMenu.open(p);
	}

	static void aiSettings(ServerPlayer p) {
		String provider = AiConfig.provider();
		open(p, "gui.ai.title", 3, m -> {
			String[] names = {"claude", "openai", "gemini"};
			String[] labels = {"Claude (Anthropic)", "ChatGPT (OpenAI)", "Gemini (Google)"};
			Item[] items = {Items.AMETHYST_SHARD, Items.EMERALD, Items.DIAMOND};
			for (int i = 0; i < names.length; i++) {
				final String name = names[i];
				ItemStack s = icon(items[i], labels[i], AiConfig.key(name).isEmpty() ? tr(p, "gui.ai.key_missing") : tr(p, "gui.ai.key_set"));
				m.button(10 + i, name.equals(provider) ? glow(s) : s, (pl, t, b) -> {
					AiConfig.setProvider(name);
					aiSettings(pl);
				});
			}
			String masked = AiConfig.masked(provider);
			m.button(14, icon(Items.TRIPWIRE_HOOK, tr(p, "gui.ai.key"), masked.isEmpty() ? tr(p, "gui.menu.none") : masked,
					tr(p, "gui.menu.type_in_chat"), tr(p, "gui.ai.key_warn")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.ai.key_prompt", text -> {
						AiConfig.setKey(provider, text);
						pl.sendSystemMessage(Component.literal(tr(pl, "gui.ai.key_saved")));
						aiSettings(pl);
					}));
			m.button(15, icon(Items.BOOK, tr(p, "gui.ai.model"), AiConfig.model(provider), tr(p, "gui.ai.model_lore")), (pl, t, b) -> {
				if (t == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
					ChatPrompt.ask(pl, "gui.ai.model_prompt", text -> {
						AiConfig.setModel(provider, text);
						aiSettings(pl);
					});
				} else {
					AiChatMenu.pickModel(pl);
				}
			});
			ItemStack auto = icon(Items.REDSTONE_TORCH, tr(p, "gui.ai.autorun") + ": " + tr(p, AiConfig.autoRun() ? "gui.yes" : "gui.no"),
					tr(p, "gui.ai.autorun_lore"));
			m.button(16, AiConfig.autoRun() ? glow(auto) : auto, (pl, t, b) -> {
				AiConfig.setAutoRun(!AiConfig.autoRun());
				aiSettings(pl);
			});
			m.button(22, back(p), (pl, t, b) -> settings(pl));
		});
	}

	// ---------- Zasady swiata ----------

	static void rules(ServerPlayer p) {
		net.minecraft.server.MinecraftServer server = p.level().getServer();
		MapSettings s = MapSettings.get(server);
		open(p, "gui.rules.title", 3, m -> {
			ItemStack lose = icon(Items.CHEST, tr(p, "gui.rules.lose") + ": " + tr(p, s.loseItems() ? "gui.yes" : "gui.no"),
					tr(p, "gui.rules.lose_lore"));
			m.button(10, s.loseItems() ? glow(lose) : lose, (pl, t, b) -> {
				s.setLoseItems(!s.loseItems());
				MapControl.applyGameRules(server);
				rules(pl);
			});
			ItemStack reset = icon(Items.TNT, tr(p, "gui.rules.reset_death") + ": " + tr(p, s.resetOnDeath() ? "gui.yes" : "gui.no"),
					tr(p, "gui.rules.reset_death_lore"));
			m.button(12, s.resetOnDeath() ? glow(reset) : reset, (pl, t, b) -> {
				s.setResetOnDeath(!s.resetOnDeath());
				rules(pl);
			});
			ItemStack clr = icon(Items.STRUCTURE_VOID, tr(p, "gui.rules.clear") + ": " + tr(p, s.clearOnDeath() ? "gui.yes" : "gui.no"),
					tr(p, "gui.rules.clear_lore"));
			m.button(20, s.clearOnDeath() ? glow(clr) : clr, (pl, t, b) -> {
				s.setClearOnDeath(!s.clearOnDeath());
				MapControl.applyGameRules(server);
				rules(pl);
			});
			m.button(14, icon(Items.COMPASS, tr(p, "gui.rules.start"),
					s.start().map(b -> b.getX() + " " + b.getY() + " " + b.getZ()).orElse(tr(p, "gui.menu.none")),
					tr(p, "gui.rules.start_lore")), (pl, t, b) -> {
				s.setStart(pl.level().dimension().identifier().toString(), pl.blockPosition());
				rules(pl);
			});
			m.button(16, icon(Items.LAVA_BUCKET, tr(p, "gui.rules.reset_now"), tr(p, "gui.rules.reset_now_lore")), (pl, t, b) -> {
				int n = MapControl.reset(server);
				pl.sendSystemMessage(Component.literal(tr(pl, "gui.rules.reset_done", n)));
			});
			m.button(22, back(p), (pl, t, b) -> settings(pl));
		});
	}

	// ---------- Ustawienia ----------

	static void settings(ServerPlayer p) {
		open(p, "gui.settings.title", 3, m -> {
			m.button(11, icon(Items.BOOK, tr(p, "gui.language") + ": " + ServerLang.code(p).toUpperCase()), (pl, t, b) -> {
				ServerLang.toggle(pl);
				settings(pl);
			});
			m.button(13, icon(Items.ANVIL, tr(p, "gui.item.title"), tr(p, "gui.item.title_lore")), (pl, t, b) -> ItemCreator.open(pl));
			m.button(15, icon(Items.COMMAND_BLOCK, tr(p, "gui.rules.open"), tr(p, "gui.rules.open_lore")), (pl, t, b) -> rules(pl));
			m.button(17, icon(Items.ENDER_EYE, tr(p, "gui.ai.open"), tr(p, "gui.ai.open_lore")), (pl, t, b) -> aiSettings(pl));
			m.button(22, back(p), (pl, t, b) -> main(pl));
		});
	}

	// ---------- Storage: pamiec swiata o odpalonych regulach ----------

	private static void storage(ServerPlayer p) {
		RuleStorage rules = RuleStorage.get(p.level().getServer());
		List<java.util.UUID> fired = FiredStorage.get(p.level().getServer()).allFired();
		open(p, "gui.storage.title", 6, m -> {
			if (fired.isEmpty()) {
				m.decor(22, icon(Items.BARRIER, tr(p, "gui.storage.empty")));
			}
			for (int i = 0; i < Math.min(45, fired.size()); i++) {
				java.util.UUID id = fired.get(i);
				Optional<Rule> r = rules.findById(id);
				String shortId = r.map(Rule::shortId).orElse(id.toString().substring(0, 6) + "?");
				m.decor(i, icon(Items.PAPER, "[" + shortId + "]", r.map(x -> tr(p, "gui.storage.line", x.kind().name(),
						x.trigger().type().name())).orElse(tr(p, "gui.storage.deleted"))));
			}
			m.decor(4 + 45 - 4, pane());
			m.button(48, icon(Items.LAVA_BUCKET, tr(p, "gui.storage.clear"), tr(p, "gui.storage.clear_lore")), (pl, t, b) -> {
				FiredStorage.get(pl.level().getServer()).clear();
				storage(pl);
			});
			m.button(49, back(p), (pl, t, b) -> main(pl));
		});
	}

	// ---------- Edit ----------

	private static void edit(ServerPlayer p) {
		open(p, "gui.edit", 3, m -> {
			m.button(10, icon(Items.LEVER, tr(p, "gui.edit.trigger")), (pl, t, b) -> trigger(pl));
			m.button(11, icon(Items.TNT, tr(p, "gui.edit.event")), (pl, t, b) -> event(pl));
			m.button(12, icon(Items.ZOMBIE_SPAWN_EGG, tr(p, "gui.edit.mob")), (pl, t, b) -> mobList(pl));
			m.button(13, icon(Items.CHEST, tr(p, "gui.edit.give")), (pl, t, b) -> give(pl));
			m.button(14, icon(Items.WITHER_SKELETON_SKULL, tr(p, "gui.edit.kill")), (pl, t, b) -> kill(pl));
			m.button(15, icon(Items.POTION, tr(p, "gui.edit.effect")), (pl, t, b) -> effects(pl, new int[]{10, 0}));
			m.button(16, icon(Items.LAVA_BUCKET, tr(p, "gui.edit.delete")), (pl, t, b) -> {
				RuleEditor.apply(pl, "delete", "");
				main(pl);
			});
			m.button(19, icon(Items.BARREL, tr(p, "gui.blockstorage.open"), tr(p, "gui.blockstorage.lore")), (pl, t, b) -> blockStorage(pl));
			m.button(22, back(p), (pl, t, b) -> main(pl));
		});
	}

	// ---------- Storage bloku (pojemnosc) ----------

	private static void blockStorage(ServerPlayer p) {
		Rule.Anchor anchor = SelectionManager.currentAnchor(p).orElseThrow();
		BlockStorage storage = BlockStorage.get(p.level().getServer());
		Optional<BlockStorage.Entry> entry = storage.find(anchor.dimension(), anchor.pos());
		int rows = entry.map(BlockStorage.Entry::rows).orElse(0);
		long used = entry.map(e -> e.items().stream().filter(s -> !s.isEmpty()).count()).orElse(0L);
		open(p, "gui.blockstorage.title", 3, m -> {
			ItemStack toggle = entry.isPresent()
					? glow(icon(Items.LIME_DYE, tr(p, "gui.blockstorage.on"), tr(p, "gui.blockstorage.click_off")))
					: icon(Items.RED_DYE, tr(p, "gui.blockstorage.off"), tr(p, "gui.blockstorage.click_on"));
			m.button(10, toggle, (pl, t, b) -> {
				net.minecraft.server.level.ServerLevel level = RuleStorage.get(pl.level().getServer()) == null ? null
						: com.chicken.mapcreator.rules.RuleActions.levelOf(pl.level().getServer(), anchor.dimension());
				if (entry.isPresent()) {
					List<ItemStack> items = storage.remove(anchor.dimension(), anchor.pos());
					if (level != null) {
						BlockStorage.dropAll(level, anchor.pos().above(), items);
					}
				} else {
					storage.setRows(anchor.dimension(), anchor.pos(), 3);
				}
				blockStorage(pl);
			});
			if (entry.isPresent()) {
				m.button(12, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> resizeStorage(pl, anchor, rows - 1));
				m.decor(13, icon(Items.CHEST, tr(p, "gui.blockstorage.capacity", rows * 9, rows), tr(p, "gui.blockstorage.used", used)));
				m.button(14, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> resizeStorage(pl, anchor, rows + 1));
				m.button(16, icon(Items.ENDER_CHEST, tr(p, "gui.blockstorage.view")), (pl, t, b) ->
						BlockStorage.open(pl, anchor.dimension(), anchor.pos(), tr(pl, "gui.blockstorage.chest_title")));
			}
			m.button(22, back(p), (pl, t, b) -> edit(pl));
		});
	}

	private static void resizeStorage(ServerPlayer p, Rule.Anchor anchor, int rows) {
		List<ItemStack> overflow = BlockStorage.get(p.level().getServer()).setRows(anchor.dimension(), anchor.pos(), rows);
		net.minecraft.server.level.ServerLevel level = com.chicken.mapcreator.rules.RuleActions.levelOf(p.level().getServer(), anchor.dimension());
		if (level != null) {
			BlockStorage.dropAll(level, anchor.pos().above(), overflow);
		}
		blockStorage(p);
	}

	// ---------- wywolywacz ----------

	private static void trigger(ServerPlayer p) {
		boolean npc = RuleEditor.editKind(p) == Rule.Kind.NPC;
		Optional<Rule> rule = RuleEditor.currentRule(p, RuleEditor.editKind(p));
		Rule.TriggerType current = rule.map(r -> r.trigger().type()).orElse(Rule.TriggerType.NONE);
		open(p, "gui.trigger.title", 4, m -> {
			addTrigger(m, p, 10, Items.STONE_BUTTON, "CLICK", current);
			addTrigger(m, p, 11, Items.STONE_PRESSURE_PLATE, "STAND", current);
			addTrigger(m, p, 12, Items.REDSTONE, "REDSTONE", current);
			addTrigger(m, p, 13, Items.OAK_DOOR, "OPEN", current);
			addTrigger(m, p, 14, Items.IRON_PICKAXE, "BREAK", current);
			m.button(15, icon(Items.CLOCK, tr(p, "gui.trigger.after"), tr(p, "gui.menu.type_ids")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.prompt.ids", text -> {
						RuleEditor.apply(pl, "after", text);
						trigger(pl);
					}));
			m.button(16, icon(Items.COMPARATOR, tr(p, "gui.trigger.if"), tr(p, "gui.menu.type_ids")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.prompt.ids", text -> {
						RuleEditor.apply(pl, "if", text);
						trigger(pl);
					}));
			if (npc) {
				addTrigger(m, p, 19, Items.LEATHER_BOOTS, "NPC_STEP_ON", current);
				addTrigger(m, p, 20, Items.HOPPER, "NPC_PICKUP", current);
				addTrigger(m, p, 21, Items.IRON_SWORD, "NPC_HURT", current);
				ItemStack filter = rule.map(r -> stackOf(r.trigger().filter())).orElse(ItemStack.EMPTY);
				m.ghost(23, marker(p, "gui.slot.filter"), filter, (pl, v) ->
						RuleEditor.apply(pl, "filter", idOf(v)));
			}
			ConditionStorage.Cond cond = rule.map(r -> ConditionStorage.get(p.level().getServer()).of(r.id()))
					.orElse(new ConditionStorage.Cond(java.util.UUID.randomUUID(), List.of(), List.of()));
			m.button(28, icon(Items.LIME_DYE, tr(p, "gui.cond.need"), tr(p, "gui.cond.now", shortIds(p, cond.needs())),
					tr(p, "gui.menu.type_ids")), (pl, t, b) -> ChatPrompt.ask(pl, "gui.prompt.ids", text -> {
				RuleEditor.apply(pl, "need", text);
				trigger(pl);
			}));
			m.button(29, icon(Items.RED_DYE, tr(p, "gui.cond.forbid"), tr(p, "gui.cond.now", shortIds(p, cond.forbids())),
					tr(p, "gui.menu.type_ids")), (pl, t, b) -> ChatPrompt.ask(pl, "gui.prompt.ids", text -> {
				RuleEditor.apply(pl, "forbid", text);
				trigger(pl);
			}));
			m.button(30, icon(Items.BARRIER, tr(p, "gui.cond.clear")), (pl, t, b) -> {
				RuleEditor.apply(pl, "cond_clear", "");
				trigger(pl);
			});
			m.button(25, icon(Items.WOODEN_AXE, tr(p, "gui.trigger.area")), (pl, t, b) -> {
				RuleEditor.apply(pl, "area", "");
				pl.closeContainer();
			});
			m.button(31, back(p), (pl, t, b) -> {
				if (RuleEditor.editKind(pl) == Rule.Kind.NPC) {
					editNpc(pl);
				} else {
					edit(pl);
				}
			});
		});
	}

	private static String shortIds(ServerPlayer p, List<java.util.UUID> ids) {
		if (ids.isEmpty()) {
			return tr(p, "gui.menu.none");
		}
		return ids.stream().map(u -> u.toString().substring(0, 6)).collect(java.util.stream.Collectors.joining(", "));
	}

	private static void addTrigger(UiMenu m, ServerPlayer p, int slot, Item item, String type, Rule.TriggerType current) {
		ItemStack stack = icon(item, tr(p, "gui.trigger." + type.toLowerCase()));
		if (current.name().equals(type)) {
			glow(stack);
		}
		m.button(slot, stack, (pl, t, b) -> {
			RuleEditor.apply(pl, "trigger", type);
			trigger(pl);
		});
	}

	// ---------- zdarzenie ----------

	private static void event(ServerPlayer p) {
		open(p, "gui.event.title", 3, m -> {
			m.button(11, icon(Items.TNT, tr(p, "gui.event.broke"), tr(p, "gui.menu.then_select")), (pl, t, b) -> {
				RuleEditor.apply(pl, "event_start", "broke");
				pl.closeContainer();
			});
			ItemStack[] block = {new ItemStack(Items.GOLD_BLOCK)};
			m.ghost(13, marker(p, "gui.slot.block"), block[0], (pl, v) -> block[0] = v);
			m.button(15, icon(Items.CRAFTING_TABLE, tr(p, "gui.event.change"), tr(p, "gui.menu.then_select")), (pl, t, b) -> {
				if (block[0].isEmpty()) {
					pl.sendSystemMessage(Component.literal(tr(pl, "gui.menu.place_block_first")));
					return;
				}
				RuleEditor.apply(pl, "event_start", "change:" + idOf(block[0]));
				pl.closeContainer();
			});
			m.button(22, back(p), (pl, t, b) -> edit(pl));
		});
	}

	// ---------- zabij ----------

	private static void kill(ServerPlayer p) {
		Optional<Rule> rule = RuleEditor.currentRule(p, RuleEditor.editKind(p));
		boolean on = rule.map(r -> r.effects().kill()).orElse(false);
		String text = rule.map(r -> r.effects().killText()).orElse("");
		open(p, "gui.kill.title", 3, m -> {
			m.button(11, on ? glow(icon(Items.LIME_DYE, tr(p, "gui.kill.on"), tr(p, "gui.menu.click_toggle")))
					: icon(Items.RED_DYE, tr(p, "gui.kill.off"), tr(p, "gui.menu.click_toggle")), (pl, t, b) -> {
				RuleEditor.apply(pl, on ? "kill_off" : "kill", text);
				kill(pl);
			});
			m.button(13, icon(Items.NAME_TAG, tr(p, "gui.kill.text_hint"), text.isEmpty() ? tr(p, "gui.menu.none") : text,
					tr(p, "gui.menu.type_in_chat")), (pl, t, b) -> ChatPrompt.ask(pl, "gui.prompt.text", input -> {
				RuleEditor.apply(pl, "kill", input);
				kill(pl);
			}));
			m.button(22, back(p), (pl, t, b) -> edit(pl));
		});
	}

	// ---------- give (siatka slotow-duchow) ----------

	private static void give(ServerPlayer p) {
		Optional<Rule> rule = RuleEditor.currentRule(p, RuleEditor.editKind(p));
		ItemStack[] values = new ItemStack[27];
		java.util.Arrays.fill(values, ItemStack.EMPTY);
		rule.ifPresent(r -> {
			for (int i = 0; i < Math.min(27, r.effects().give().size()); i++) {
				Rule.ItemSpec spec = r.effects().give().get(i);
				ItemStack s = stackOf(spec.item());
				if (!s.isEmpty()) {
					s.setCount(Math.max(1, Math.min(64, spec.count())));
					values[i] = s;
				}
			}
		});
		open(p, "gui.give.title", 4, m -> {
			for (int i = 0; i < 27; i++) {
				final int idx = i;
				m.ghost(i, marker(p, "gui.slot.give"), values[i], (pl, v) -> {
					values[idx] = v;
					List<Rule.ItemSpec> list = new ArrayList<>();
					for (ItemStack s : values) {
						if (!s.isEmpty()) {
							list.add(new Rule.ItemSpec(idOf(s), s.getCount(), ""));
						}
					}
					RuleEditor.edit(pl, r -> new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), r.mobs(),
							new Rule.PlayerEffects(r.effects().kill(), r.effects().killText(), list, r.effects().tp(),
									r.effects().effects(), r.effects().screenText()), r.npc()));
				});
			}
			m.button(31, back(p), (pl, t, b) -> edit(pl));
		});
	}

	// ---------- efekty ----------

	private static final List<Holder<MobEffect>> EFFECTS = List.of(MobEffects.SPEED, MobEffects.SLOWNESS, MobEffects.HASTE,
			MobEffects.MINING_FATIGUE, MobEffects.STRENGTH, MobEffects.JUMP_BOOST, MobEffects.NAUSEA, MobEffects.REGENERATION,
			MobEffects.RESISTANCE, MobEffects.FIRE_RESISTANCE, MobEffects.WATER_BREATHING, MobEffects.INVISIBILITY,
			MobEffects.BLINDNESS, MobEffects.NIGHT_VISION, MobEffects.HUNGER, MobEffects.WEAKNESS, MobEffects.POISON,
			MobEffects.WITHER, MobEffects.HEALTH_BOOST, MobEffects.ABSORPTION, MobEffects.SATURATION, MobEffects.GLOWING,
			MobEffects.LEVITATION, MobEffects.LUCK, MobEffects.SLOW_FALLING, MobEffects.DARKNESS, MobEffects.DOLPHINS_GRACE);

	/** state = {sekundy, moc}. */
	private static void effects(ServerPlayer p, int[] state) {
		open(p, "gui.effect.title", 6, m -> {
			for (int i = 0; i < EFFECTS.size(); i++) {
				Holder<MobEffect> effect = EFFECTS.get(i);
				String id = BuiltInRegistries.MOB_EFFECT.getKey(effect.value()).toString();
				ItemStack potion = icon(Items.POTION, Component.translatable(effect.value().getDescriptionId()).getString(),
						tr(p, "gui.menu.click_add"));
				m.button(i, potion, (pl, t, b) -> {
					RuleEditor.apply(pl, "effect", id + "," + state[0] + "," + state[1]);
					pl.displayClientMessage(Component.literal(tr(pl, "gui.menu.effect_added", id, state[0], state[1])), true);
				});
			}
			m.button(28, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
				state[0] = Math.max(1, state[0] - 5);
				effects(pl, state);
			});
			m.decor(29, icon(Items.CLOCK, tr(p, "gui.menu.dur", state[0])));
			m.button(30, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
				state[0] = Math.min(3600, state[0] + 5);
				effects(pl, state);
			});
			m.button(32, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
				state[1] = Math.max(0, state[1] - 1);
				effects(pl, state);
			});
			m.decor(33, icon(Items.GLOWSTONE_DUST, tr(p, "gui.menu.amp", state[1])));
			m.button(34, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
				state[1] = Math.min(255, state[1] + 1);
				effects(pl, state);
			});
			m.button(37, icon(Items.NAME_TAG, tr(p, "gui.effect.set_text"), tr(p, "gui.menu.type_in_chat")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.prompt.text", input -> {
						RuleEditor.apply(pl, "text", input);
						effects(pl, state);
					}));
			m.button(38, icon(Items.ENDER_PEARL, tr(p, "gui.effect.tp_here")), (pl, t, b) -> {
				RuleEditor.apply(pl, "tp_here", "");
				pl.displayClientMessage(Component.literal(tr(pl, "gui.menu.tp_set")), true);
			});
			m.button(39, icon(Items.ENDER_EYE, tr(p, "gui.camera.title"), tr(p, "gui.camera.lore")), (pl, t, b) ->
					cameraEffects(pl, new int[]{10, 3, 30}));
			m.button(40, icon(Items.LAVA_BUCKET, tr(p, "gui.effect.clear")), (pl, t, b) -> {
				RuleEditor.apply(pl, "clear_effects", "");
				effects(pl, state);
			});
			m.button(49, back(p), (pl, t, b) -> edit(pl));
		});
	}

	/** Sila efektu kamery: dla okienek liczba okien (1-12) i rozmiar w procentach zakodowane w jednej liczbie. */
	private static int cameraPower(String type, int[] state) {
		return type.equals("windows") ? state[2] * 100 + state[1] : 1;
	}

	// ---------- efekty kamery ----------

	/** state = {sekundy}. Klik w efekt dodaje go do reguly (odpali sie po wywolaniu), "Test" odpala go od razu na tobie. */
	private static void cameraEffects(ServerPlayer p, int[] state) {
		String[] types = {"wave", "upside_down", "sideways", "blur", "nearsighted", "pixelate", "windows"};
		Item[] items = {Items.WATER_BUCKET, Items.ENDER_EYE, Items.ITEM_FRAME, Items.GLASS, Items.SPYGLASS, Items.WHITE_CONCRETE, Items.PAINTING};
		open(p, "gui.camera.title", 5, m -> {
			for (int i = 0; i < types.length; i++) {
				final String type = types[i];
				m.button(10 + i, icon(items[i], tr(p, "gui.camera." + type), tr(p, "gui.camera.click_add", state[0]),
						tr(p, "gui.camera.shift_test")), (pl, t, b) -> {
					if (t == net.minecraft.world.inventory.ClickType.QUICK_MOVE) {
						com.chicken.mapcreator.net.CameraEffectPayload.send(pl, type, Math.min(state[0], 10), cameraPower(type, state));
					} else {
						RuleEditor.apply(pl, "camera", type + "," + state[0] + "," + cameraPower(type, state));
						pl.displayClientMessage(Component.literal(tr(pl, "gui.camera.added", tr(pl, "gui.camera." + type), state[0])), true);
					}
				});
			}
			m.button(19, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
				state[0] = Math.max(1, state[0] - 5);
				cameraEffects(pl, state);
			});
			m.decor(20, icon(Items.CLOCK, tr(p, "gui.menu.dur", state[0])));
			m.button(21, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
				state[0] = Math.min(3600, state[0] + 5);
				cameraEffects(pl, state);
			});
			m.button(23, icon(Items.BARRIER, tr(p, "gui.camera.stop")), (pl, t, b) ->
					com.chicken.mapcreator.net.CameraEffectPayload.send(pl, "wave", 0, 1));
			m.button(25, icon(Items.LAVA_BUCKET, tr(p, "gui.camera.clear")), (pl, t, b) -> {
				RuleEditor.apply(pl, "clear_camera", "");
				cameraEffects(pl, state);
			});
			// ustawienia okienek: liczba (1-12) i rozmiar (10-90% ekranu)
			m.button(28, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
				state[1] = Math.max(1, state[1] - 1);
				cameraEffects(pl, state);
			});
			m.decor(29, icon(Items.PAINTING, tr(p, "gui.camera.count", state[1])));
			m.button(30, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
				state[1] = Math.min(12, state[1] + 1);
				cameraEffects(pl, state);
			});
			m.button(32, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
				state[2] = Math.max(10, state[2] - 5);
				cameraEffects(pl, state);
			});
			m.decor(33, icon(Items.ITEM_FRAME, tr(p, "gui.camera.size", state[2])));
			m.button(34, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
				state[2] = Math.min(90, state[2] + 5);
				cameraEffects(pl, state);
			});
			m.button(40, back(p), (pl, t, b) -> effects(pl, new int[]{10, 0}));
		});
	}

	// ---------- moby: lista -> moby -> (eq / respawn / zachowanie) ----------

	private static final java.util.Map<java.util.UUID, Integer> PENDING_SPAWN_MOB = new java.util.HashMap<>();

	private static List<Rule.MobSpec> mobsOf(ServerPlayer p) {
		return RuleEditor.currentRule(p, RuleEditor.editKind(p)).map(Rule::mobs).orElse(List.of());
	}

	private static Optional<Rule.MobSpec> mobAt(ServerPlayer p, int idx) {
		List<Rule.MobSpec> mobs = mobsOf(p);
		return idx >= 0 && idx < mobs.size() ? Optional.of(mobs.get(idx)) : Optional.empty();
	}

	private static void updateMob(ServerPlayer p, int idx, java.util.function.UnaryOperator<Rule.MobSpec> change) {
		RuleEditor.edit(p, r -> {
			List<Rule.MobSpec> list = new ArrayList<>(r.mobs());
			if (idx >= 0 && idx < list.size()) {
				list.set(idx, change.apply(list.get(idx)));
			}
			return new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), list, r.effects(), r.npc());
		});
	}

	private static String where(ServerPlayer p, Optional<BlockPos> pos) {
		return pos.map(b -> b.getX() + " " + b.getY() + " " + b.getZ()).orElse(tr(p, "gui.menu.default_spawn"));
	}

	/** Lista mobow reguly (na poczatku pusta) + przycisk dodawania. */
	private static void mobList(ServerPlayer p) {
		List<Rule.MobSpec> mobs = mobsOf(p);
		open(p, "gui.mob.list_title", 6, m -> {
			if (mobs.isEmpty()) {
				m.decor(22, icon(Items.BARRIER, tr(p, "gui.mob.empty")));
			}
			for (int i = 0; i < Math.min(45, mobs.size()); i++) {
				final int idx = i;
				Rule.MobSpec s = mobs.get(i);
				m.button(i, icon(eggFor(s.mob()).getItem(), s.nick().isEmpty() ? s.mob() : s.nick(),
						s.mob(), tr(p, "gui.menu.mob_line", s.action(), s.damage()), tr(p, "gui.menu.click_open")),
						(pl, t, b) -> mobMenu(pl, idx));
			}
			m.button(48, glow(icon(Items.EMERALD, tr(p, "gui.menu.new_mob"))), (pl, t, b) -> mobCreate(pl, new String[]{"minecraft:zombie", ""}));
			m.button(49, back(p), (pl, t, b) -> edit(pl));
		});
	}

	/** Tworzenie moba: tylko typ i nick, bez ataku i reszty (to dopiero w menu moba). */
	private static void mobCreate(ServerPlayer p, String[] draft) {
		open(p, "gui.mob.create_title", 3, m -> {
			m.ghost(10, marker(p, "gui.slot.egg"), eggFor(draft[0]), (pl, v) -> {
				String id = mobIdFromEgg(v);
				if (id != null) {
					draft[0] = id;
				} else if (!v.isEmpty()) {
					pl.sendSystemMessage(Component.literal(tr(pl, "gui.menu.not_egg")));
				}
				mobCreate(pl, draft);
			});
			m.button(12, icon(Items.NAME_TAG, tr(p, "gui.mob.nick_hint"), draft[1].isEmpty() ? tr(p, "gui.menu.none") : draft[1],
					tr(p, "gui.menu.type_in_chat")), (pl, t, b) -> ChatPrompt.ask(pl, "gui.prompt.nick", text -> {
				draft[1] = text;
				mobCreate(pl, draft);
			}));
			m.decor(14, icon(eggFor(draft[0]).getItem(), draft[0]));
			m.button(16, glow(icon(Items.LIME_CONCRETE, tr(p, "gui.mob.create"), draft[0])), (pl, t, b) -> {
				Rule.MobSpec spec = Rule.MobSpec.create(draft[0], draft[1]);
				RuleEditor.edit(pl, r -> {
					List<Rule.MobSpec> list = new ArrayList<>(r.mobs());
					list.add(spec);
					return new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), list, r.effects(), r.npc());
				});
				pl.sendSystemMessage(Component.literal(tr(pl, "gui.mob.created", spec.mob())));
				mobList(pl);
			});
			m.button(22, back(p), (pl, t, b) -> mobList(pl));
		});
	}

	/** Menu jednego moba: edytuj eq, ustaw resp, zachowanie, test, usun. */
	private static void mobMenu(ServerPlayer p, int idx) {
		Optional<Rule.MobSpec> found = mobAt(p, idx);
		if (found.isEmpty()) {
			mobList(p);
			return;
		}
		Rule.MobSpec s = found.get();
		open(p, "gui.mob.title", 3, m -> {
			m.decor(4, icon(eggFor(s.mob()).getItem(), s.nick().isEmpty() ? s.mob() : s.nick(), s.mob(),
					tr(p, "gui.menu.mob_line", s.action(), s.damage()), tr(p, "gui.menu.mob_spawn", where(p, s.spawn()))));
			m.button(10, icon(Items.IRON_CHESTPLATE, tr(p, "gui.mob.menu_eq")), (pl, t, b) -> mobEquip(pl, idx));
			m.button(12, icon(Items.COMPASS, tr(p, "gui.mob.menu_spawn"), where(p, s.spawn())), (pl, t, b) -> {
				PENDING_SPAWN_MOB.put(pl.getUUID(), idx);
				RuleEditor.apply(pl, "spawn_select", "");
				pl.closeContainer();
			});
			m.button(14, icon(Items.COMPARATOR, tr(p, "gui.mob.menu_behavior"), tr(p, "gui.menu.mob_line", s.action(), s.damage())),
					(pl, t, b) -> mobBehavior(pl, idx));
			m.button(16, icon(Items.LAVA_BUCKET, tr(p, "gui.mob.delete")), (pl, t, b) -> {
				RuleEditor.edit(pl, r -> {
					List<Rule.MobSpec> list = new ArrayList<>(r.mobs());
					if (idx < list.size()) {
						list.remove(idx);
					}
					return new Rule(r.id(), r.kind(), r.anchor(), r.trigger(), r.events(), list, r.effects(), r.npc());
				});
				mobList(pl);
			});
			m.button(21, icon(Items.TNT, tr(p, "gui.mob.test")), (pl, t, b) ->
					RuleEditor.currentRule(pl, RuleEditor.editKind(pl)).ifPresent(r ->
							MobSpawner.spawn(pl.level().getServer(), r, mobAt(pl, idx).orElse(s), pl)));
			m.button(22, back(p), (pl, t, b) -> mobList(pl));
		});
	}

	/** Ekwipunek moba: sloty-duchy (reka, druga reka, helm, napiersnik, spodnie, buty, przedmiot wyrzucany). */
	private static void mobEquip(ServerPlayer p, int idx) {
		Optional<Rule.MobSpec> found = mobAt(p, idx);
		if (found.isEmpty()) {
			mobList(p);
			return;
		}
		Rule.MobSpec s = found.get();
		open(p, "gui.mob.eq_title", 3, m -> {
			String[] slots = {"item", "offhand", "head", "chest", "legs", "feet", "drop"};
			String[] labels = {"gui.slot.main", "gui.slot.off", "gui.slot.head", "gui.slot.chest", "gui.slot.legs", "gui.slot.feet", "gui.slot.drop"};
			String[] values = {s.item(), s.offhand(), s.head(), s.chest(), s.legs(), s.feet(), s.drop()};
			for (int i = 0; i < slots.length; i++) {
				final String slot = slots[i];
				m.ghost(10 + i, marker(p, labels[i]), stackOf(values[i]), (pl, v) ->
						updateMob(pl, idx, spec -> spec.withEquip(slot, idOf(v))));
			}
			m.button(22, back(p), (pl, t, b) -> mobMenu(pl, idx));
		});
	}

	/** Zachowanie: brak / rzut / atak / idz + Damage, wlasny napis smierci i niesmiertelnosc na gracza. */
	private static void mobBehavior(ServerPlayer p, int idx) {
		Optional<Rule.MobSpec> found = mobAt(p, idx);
		if (found.isEmpty()) {
			mobList(p);
			return;
		}
		Rule.MobSpec s = found.get();
		open(p, "gui.mob.behavior_title", 5, m -> {
			addBehavior(m, p, idx, 10, Items.BARRIER, "NONE", "gui.mob.behavior_none", s.action());
			addBehavior(m, p, idx, 11, Items.BOW, "THROW", "gui.mob.behavior_throw", s.action());
			addBehavior(m, p, idx, 12, Items.IRON_SWORD, "ATTACK", "gui.mob.behavior_attack", s.action());
			ItemStack go = icon(Items.COMPASS, tr(p, "gui.mob.behavior_go"), s.goTo().map(b -> b.getX() + " " + b.getY() + " " + b.getZ()).orElse(tr(p, "gui.menu.none")));
			m.button(13, s.action().equals("GO") ? glow(go) : go, (pl, t, b) -> mobGo(pl, idx));
			if (s.action().equals("ATTACK") || s.action().equals("THROW")) {
				m.button(19, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
					updateMob(pl, idx, spec -> spec.withDamage(Math.max(0, spec.damage() - 1)));
					mobBehavior(pl, idx);
				});
				m.decor(20, icon(Items.PAPER, tr(p, "gui.menu.damage", s.damage())));
				m.button(21, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
					updateMob(pl, idx, spec -> spec.withDamage(Math.min(100, spec.damage() + 1)));
					mobBehavior(pl, idx);
				});
				// poczekaj na rzut / atak: ile sekund od pojawienia sie moba
				m.button(28, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
					updateMob(pl, idx, spec -> spec.withDelay(Math.max(0, spec.delay() - 1)));
					mobBehavior(pl, idx);
				});
				m.decor(29, icon(Items.CLOCK, tr(p, s.action().equals("THROW") ? "gui.mob.delay_throw" : "gui.mob.delay_attack", s.delay())));
				m.button(30, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
					updateMob(pl, idx, spec -> spec.withDelay(Math.min(600, spec.delay() + 1)));
					mobBehavior(pl, idx);
				});
				if (s.action().equals("THROW")) {
					ItemStack grav = icon(s.gravity() ? Items.ANVIL : Items.FEATHER,
							tr(p, "gui.mob.gravity") + ": " + tr(p, s.gravity() ? "gui.yes" : "gui.no"), tr(p, "gui.mob.gravity_lore"));
					m.button(32, s.gravity() ? grav : glow(grav), (pl, t, b) -> {
						updateMob(pl, idx, spec -> spec.withGravity(!spec.gravity()));
						mobBehavior(pl, idx);
					});
				}
			}
			m.button(23, icon(Items.NAME_TAG, tr(p, "gui.mob.death_text"), s.deathText().isEmpty() ? tr(p, "gui.menu.none") : s.deathText(),
					tr(p, "gui.menu.type_in_chat")), (pl, t, b) -> ChatPrompt.ask(pl, "gui.prompt.text", text -> {
				updateMob(pl, idx, spec -> spec.withDeathText(text));
				mobBehavior(pl, idx);
			}));
			ItemStack shield = icon(Items.SHIELD, tr(p, "gui.mob.immune") + ": " + tr(p, s.immune() ? "gui.yes" : "gui.no"),
					tr(p, "gui.menu.click_toggle"));
			m.button(25, s.immune() ? glow(shield) : shield, (pl, t, b) -> {
				updateMob(pl, idx, spec -> spec.withImmune(!spec.immune()));
				mobBehavior(pl, idx);
			});
			m.button(40, back(p), (pl, t, b) -> mobMenu(pl, idx));
		});
	}

	private static void addBehavior(UiMenu m, ServerPlayer p, int idx, int slot, Item item, String action, String nameKey, String current) {
		ItemStack stack = icon(item, tr(p, nameKey));
		m.button(slot, current.equals(action) ? glow(stack) : stack, (pl, t, b) -> {
			updateMob(pl, idx, spec -> spec.withAction(action));
			mobBehavior(pl, idx);
		});
	}

	/** Idz: cel marszu moba (z twojej pozycji albo wspolrzedne z czatu). */
	private static void mobGo(ServerPlayer p, int idx) {
		open(p, "gui.mob.go_title", 3, m -> {
			m.button(11, icon(Items.ENDER_PEARL, tr(p, "gui.menu.use_pos")), (pl, t, b) -> {
				BlockPos pos = pl.blockPosition();
				updateMob(pl, idx, spec -> spec.withAction("GO").withGoTo(Optional.of(pos)));
				mobBehavior(pl, idx);
			});
			m.button(15, icon(Items.NAME_TAG, tr(p, "gui.menu.type_coords"), tr(p, "gui.menu.type_in_chat")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.prompt.coords", text -> {
						String[] c = text.trim().split("[ ,]+");
						if (c.length == 3) {
							try {
								BlockPos goal = new BlockPos(Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2]));
								updateMob(pl, idx, spec -> spec.withAction("GO").withGoTo(Optional.of(goal)));
							} catch (NumberFormatException e) {
								pl.sendSystemMessage(Component.literal(tr(pl, "gui.menu.bad_coords")));
							}
						}
						mobBehavior(pl, idx);
					}));
			m.button(22, back(p), (pl, t, b) -> mobBehavior(pl, idx));
		});
	}

	/** Po wybraniu miejsca respu rozdzka wraca do menu moba z ustawionym miejscem. */
	public static boolean onSpawnPicked(ServerPlayer p, BlockPos spawn) {
		Integer idx = PENDING_SPAWN_MOB.remove(p.getUUID());
		if (idx == null) {
			return false;
		}
		updateMob(p, idx, spec -> spec.withSpawn(Optional.of(spawn)));
		mobMenu(p, idx);
		return true;
	}

	// ---------- NPC ----------

	private static void editNpc(ServerPlayer p) {
		RuleStorage storage = RuleStorage.get(p.level().getServer());
		Rule.Anchor anchor = SelectionManager.currentAnchor(p).orElseThrow();
		Optional<Rule> rule = storage.find(anchor, Rule.Kind.NPC);
		boolean has = rule.isPresent() && rule.get().npc().isPresent();
		open(p, "gui.npc.edit_title", 4, m -> {
			if (!has) {
				m.decor(13, icon(Items.BARRIER, tr(p, "gui.npc.none")));
				m.button(31, back(p), (pl, t, b) -> main(pl));
				return;
			}
			Rule.NpcSpec spec = rule.get().npc().get();
			m.decor(4, icon(Items.PAPER, tr(p, "gui.npc.info", NpcManager.describe(storage, anchor)),
					tr(p, "gui.menu.npc_function", spec.action())));
			m.button(10, icon(Items.LEVER, tr(p, "gui.npc.trigger")), (pl, t, b) -> trigger(pl));
			m.button(11, icon(Items.EMERALD, tr(p, "gui.npc.fn_trade")), (pl, t, b) -> trade(pl));
			m.button(12, icon(Items.IRON_SWORD, tr(p, "gui.npc.fn_attack")), (pl, t, b) -> attack(pl, new double[]{spec.damage()}));
			m.button(13, icon(Items.COMPASS, tr(p, "gui.npc.fn_go")), (pl, t, b) -> go(pl));
			m.button(14, icon(Items.BARRIER, tr(p, "gui.npc.fn_none")), (pl, t, b) -> {
				RuleEditor.apply(pl, "npc_none", "");
				editNpc(pl);
			});
			m.button(15, spec.playerImmune() ? glow(icon(Items.SHIELD, tr(p, "gui.npc.immune") + ": " + tr(p, "gui.yes")))
					: icon(Items.SHIELD, tr(p, "gui.npc.immune") + ": " + tr(p, "gui.no")), (pl, t, b) -> {
				RuleEditor.apply(pl, "npc_immune", spec.playerImmune() ? "0" : "1");
				editNpc(pl);
			});
			ItemStack appear = icon(Items.ENDER_PEARL, tr(p, "gui.npc.appear") + ": " + tr(p, spec.appear() ? "gui.yes" : "gui.no"),
					tr(p, "gui.npc.appear_lore"));
			m.button(19, spec.appear() ? glow(appear) : appear, (pl, t, b) -> {
				RuleEditor.apply(pl, "npc_appear", spec.appear() ? "0" : "1");
				editNpc(pl);
			});
			m.button(16, icon(Items.LAVA_BUCKET, tr(p, "gui.npc.delete")), (pl, t, b) -> {
				RuleEditor.apply(pl, "npc_delete", "");
				main(pl);
			});
			m.button(31, back(p), (pl, t, b) -> main(pl));
		});
	}

	/** draft = {mob, skin}. */
	private static void createNpc(ServerPlayer p, String[] draft) {
		open(p, "gui.npc.create_title", 3, m -> {
			boolean isPlayer = draft[0].equals("player");
			m.ghost(10, marker(p, "gui.slot.egg"), isPlayer ? ItemStack.EMPTY : eggFor(draft[0]), (pl, v) -> {
				String id = mobIdFromEgg(v);
				if (id != null) {
					draft[0] = id;
					createNpc(pl, draft);
				} else if (!v.isEmpty()) {
					pl.sendSystemMessage(Component.literal(tr(pl, "gui.menu.not_egg")));
				}
			});
			ItemStack head = icon(Items.PLAYER_HEAD, tr(p, "gui.npc.player_preset"), draft[1].isEmpty() ? tr(p, "gui.menu.none") : draft[1],
					tr(p, "gui.menu.type_in_chat"));
			m.button(12, isPlayer ? glow(head) : head, (pl, t, b) -> ChatPrompt.ask(pl, "gui.prompt.skin", text -> {
				draft[0] = "player";
				draft[1] = text;
				createNpc(pl, draft);
			}));
			m.button(14, icon(Items.COMPASS, tr(p, "gui.npc.spawn_select")), (pl, t, b) -> {
				RuleEditor.apply(pl, "spawn_select", "");
				pl.closeContainer();
			});
			m.button(16, glow(icon(Items.LIME_CONCRETE, tr(p, "gui.npc.create"), draft[0])), (pl, t, b) -> {
				RuleEditor.apply(pl, "npc_create", draft[0] + "|" + draft[1]);
				editNpc(pl);
			});
			m.button(22, back(p), (pl, t, b) -> main(pl));
		});
	}

	private static void trade(ServerPlayer p) {
		ItemStack[] io = {new ItemStack(Items.EMERALD), new ItemStack(Items.DIAMOND)};
		open(p, "gui.npc.fn_trade", 3, m -> {
			m.ghost(11, marker(p, "gui.slot.trade_in"), io[0], (pl, v) -> io[0] = v);
			m.decor(13, icon(Items.ARROW, tr(p, "gui.menu.trade_arrow")));
			m.ghost(15, marker(p, "gui.slot.trade_out"), io[1], (pl, v) -> io[1] = v);
			m.button(21, glow(icon(Items.LIME_CONCRETE, tr(p, "gui.trade.apply"))), (pl, t, b) -> {
				if (io[0].isEmpty() || io[1].isEmpty()) {
					pl.sendSystemMessage(Component.literal(tr(pl, "gui.menu.trade_need_both")));
					return;
				}
				RuleEditor.apply(pl, "npc_trade", idOf(io[0]) + "," + io[0].getCount() + "," + idOf(io[1]) + "," + io[1].getCount());
				editNpc(pl);
			});
			m.button(22, back(p), (pl, t, b) -> editNpc(pl));
		});
	}

	private static void attack(ServerPlayer p, double[] damage) {
		open(p, "gui.npc.fn_attack", 3, m -> {
			m.button(11, icon(Items.RED_DYE, tr(p, "gui.menu.less")), (pl, t, b) -> {
				damage[0] = Math.max(0, damage[0] - 1);
				attack(pl, damage);
			});
			m.decor(13, icon(Items.PAPER, tr(p, "gui.menu.damage", damage[0])));
			m.button(15, icon(Items.LIME_DYE, tr(p, "gui.menu.more")), (pl, t, b) -> {
				damage[0] = Math.min(100, damage[0] + 1);
				attack(pl, damage);
			});
			m.button(21, glow(icon(Items.LIME_CONCRETE, tr(p, "gui.attack.apply"))), (pl, t, b) -> {
				RuleEditor.apply(pl, "npc_attack", String.valueOf(damage[0]));
				editNpc(pl);
			});
			m.button(22, back(p), (pl, t, b) -> editNpc(pl));
		});
	}

	private static void go(ServerPlayer p) {
		open(p, "gui.npc.fn_go", 3, m -> {
			m.button(11, icon(Items.ENDER_PEARL, tr(p, "gui.menu.use_pos")), (pl, t, b) -> {
				BlockPos pos = pl.blockPosition();
				RuleEditor.apply(pl, "npc_go", pos.getX() + "," + pos.getY() + "," + pos.getZ());
				editNpc(pl);
			});
			m.button(15, icon(Items.NAME_TAG, tr(p, "gui.menu.type_coords"), tr(p, "gui.menu.type_in_chat")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.prompt.coords", text -> {
						RuleEditor.apply(pl, "npc_go", text.trim().replaceAll("[ ,]+", ","));
						editNpc(pl);
					}));
			m.button(22, back(p), (pl, t, b) -> editNpc(pl));
		});
	}
}
