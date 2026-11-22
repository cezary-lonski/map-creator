package com.chicken.mapcreator.menu;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Kreator wlasnych przedmiotow: bazowy przedmiot (z kursora albo wyszukany po nazwie z podpowiedziami jak w komendzie),
 * nazwa, opis, lista blokow, ktore niszczy w trybie przygodowym (can_break), wytrzymalosc, niezniszczalnosc, poswiata.
 * Przedmiot jest budowany z komponentow tym samym parserem, co komenda /give.
 */
public final class ItemCreator {
	private static final class Draft {
		String base = "minecraft:diamond_pickaxe";
		String name = "";
		String lore = "";
		List<String> breaks = new ArrayList<>();
		int durability = 0;
		boolean unbreakable = false;
		boolean glint = false;
	}

	private static final Map<UUID, Draft> DRAFTS = new HashMap<>();

	private ItemCreator() {}

	private static Draft draft(ServerPlayer p) {
		return DRAFTS.computeIfAbsent(p.getUUID(), k -> new Draft());
	}

	private static String esc(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " ");
	}

	/** Buduje przedmiot z ustawien; przy bledzie zwraca pusty wynik i komunikat dla gracza. */
	private static Optional<ItemStack> build(ServerPlayer p, Draft d, boolean report) {
		List<String> parts = new ArrayList<>();
		if (!d.breaks.isEmpty()) {
			List<String> quoted = new ArrayList<>();
			for (String b : d.breaks) {
				quoted.add("\"" + esc(b) + "\"");
			}
			parts.add("can_break={blocks:[" + String.join(",", quoted) + "]}");
		}
		if (!d.name.isEmpty()) {
			parts.add("custom_name={text:\"" + esc(d.name) + "\",italic:false}");
		}
		if (!d.lore.isEmpty()) {
			parts.add("lore=[{text:\"" + esc(d.lore) + "\",italic:false}]");
		}
		if (d.durability > 0) {
			parts.add("max_damage=" + d.durability);
			parts.add("max_stack_size=1");
		}
		if (d.unbreakable) {
			parts.add("unbreakable={}");
		}
		if (d.glint) {
			parts.add("enchantment_glint_override=true");
		}
		String spec = d.base + (parts.isEmpty() ? "" : "[" + String.join(",", parts) + "]");
		try {
			ItemParser.ItemResult result = new ItemParser(p.registryAccess()).parse(new StringReader(spec));
			return Optional.of(new ItemInput(result.item(), result.components()).createItemStack(1, false));
		} catch (Exception e) {
			if (report) {
				p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.item.error", String.valueOf(e.getMessage()))));
			}
			return Optional.empty();
		}
	}

	/** Daje graczowi przedmiot z opisu "baza|nazwa|opis|blok1,blok2|wytrzymalosc|niezniszczalny|poswiata" (uzywane przez AI). */
	public static boolean giveSpec(ServerPlayer p, String arg) {
		String[] f = (arg + "||||||").split("\\|", -1);
		Identifier id = Identifier.tryParse(f[0].trim());
		if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
			p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.item.bad_id", f[0])));
			return false;
		}
		Draft d = new Draft();
		d.base = id.toString();
		d.name = f[1].trim();
		d.lore = f[2].trim();
		for (String b : f[3].split(",")) {
			Identifier bid = Identifier.tryParse(b.trim());
			if (bid != null && BuiltInRegistries.BLOCK.getOptional(bid).isPresent()) {
				d.breaks.add(bid.toString());
			}
		}
		try {
			d.durability = Math.max(0, Math.min(100000, Integer.parseInt(f[4].trim())));
		} catch (NumberFormatException e) {
			d.durability = 0;
		}
		d.unbreakable = f[5].trim().equalsIgnoreCase("true");
		d.glint = f[6].trim().equalsIgnoreCase("true");
		Optional<ItemStack> stack = build(p, d, true);
		stack.ifPresent(s -> {
			if (!p.getInventory().add(s)) {
				p.drop(s, false);
			}
		});
		return stack.isPresent();
	}

	public static void open(ServerPlayer p) {
		Draft d = draft(p);
		Optional<ItemStack> preview = build(p, d, false);
		Menus.open(p, "gui.item.title", 5, m -> {
			ItemStack shown = preview.map(ItemStack::copy).orElseGet(() ->
					Menus.icon(Items.BARRIER, Menus.tr(p, "gui.item.error", "?")));
			m.decor(4, shown);
			m.ghost(10, Menus.marker(p, "gui.item.base_marker"), Menus.stackOf(d.base), (pl, v) -> {
				d.base = v.isEmpty() ? "minecraft:diamond_pickaxe" : Menus.idOf(v);
				open(pl);
			});
			m.button(11, Menus.icon(Items.SPYGLASS, Menus.tr(p, "gui.item.search"), d.base, Menus.tr(p, "gui.item.search_lore")),
					(pl, t, b) -> searchPrompt(pl, "base"));
			m.button(12, Menus.icon(Items.NAME_TAG, Menus.tr(p, "gui.item.name"),
					d.name.isEmpty() ? Menus.tr(p, "gui.menu.none") : d.name, Menus.tr(p, "gui.menu.type_in_chat")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.prompt.text", text -> {
						d.name = text;
						open(pl);
					}));
			m.button(13, Menus.icon(Items.BOOK, Menus.tr(p, "gui.item.lore"),
					d.lore.isEmpty() ? Menus.tr(p, "gui.menu.none") : d.lore, Menus.tr(p, "gui.menu.type_in_chat")), (pl, t, b) ->
					ChatPrompt.ask(pl, "gui.prompt.text", text -> {
						d.lore = text;
						open(pl);
					}));
			List<String> breakLore = new ArrayList<>();
			breakLore.add(Menus.tr(p, "gui.item.breaks_lore"));
			for (int i = 0; i < Math.min(6, d.breaks.size()); i++) {
				breakLore.add("- " + d.breaks.get(i));
			}
			if (d.breaks.size() > 6) {
				breakLore.add("... +" + (d.breaks.size() - 6));
			}
			breakLore.add(Menus.tr(p, "gui.item.breaks_clear"));
			m.button(14, Menus.icon(Items.IRON_PICKAXE, Menus.tr(p, "gui.item.breaks", d.breaks.size()), breakLore.toArray(new String[0])),
					(pl, t, b) -> {
						if (t == net.minecraft.world.inventory.ClickType.QUICK_MOVE || b == 1) {
							d.breaks.clear();
							open(pl);
						} else {
							searchPrompt(pl, "breaks");
						}
					});
			m.ghost(15, Menus.marker(p, "gui.item.add_block"), ItemStack.EMPTY, (pl, v) -> {
				if (v.isEmpty()) {
					return;
				}
				Block block = Block.byItem(v.getItem());
				if (block == Blocks.AIR) {
					pl.sendSystemMessage(Component.literal(Menus.tr(pl, "gui.item.not_block")));
				} else {
					String id = BuiltInRegistries.BLOCK.getKey(block).toString();
					if (!d.breaks.contains(id)) {
						d.breaks.add(id);
					}
				}
				open(pl);
			});
			// wytrzymalosc wpisuje sie na czacie (0 = domyslna dla bazowego przedmiotu)
			m.button(20, Menus.icon(Items.ANVIL, Menus.tr(p, "gui.item.durability", d.durability), Menus.tr(p, "gui.menu.type_in_chat")),
					(pl, t, b) -> ChatPrompt.ask(pl, "gui.prompt.number", text -> {
						try {
							d.durability = Math.max(0, Math.min(100000, Integer.parseInt(text.trim())));
						} catch (NumberFormatException e) {
							pl.sendSystemMessage(Component.literal(Menus.tr(pl, "gui.prompt.bad_number")));
						}
						open(pl);
					}));
			ItemStack unb = Menus.icon(Items.OBSIDIAN, Menus.tr(p, "gui.item.unbreakable") + ": " + Menus.tr(p, d.unbreakable ? "gui.yes" : "gui.no"));
			m.button(23, d.unbreakable ? Menus.glow(unb) : unb, (pl, t, b) -> {
				d.unbreakable = !d.unbreakable;
				open(pl);
			});
			ItemStack gl = Menus.icon(Items.ENCHANTED_BOOK, Menus.tr(p, "gui.item.glint") + ": " + Menus.tr(p, d.glint ? "gui.yes" : "gui.no"));
			m.button(24, d.glint ? Menus.glow(gl) : gl, (pl, t, b) -> {
				d.glint = !d.glint;
				open(pl);
			});
			m.button(31, Menus.glow(Menus.icon(Items.LIME_CONCRETE, Menus.tr(p, "gui.item.give"))), (pl, t, b) ->
					build(pl, d, true).ifPresent(stack -> {
						if (!pl.getInventory().add(stack)) {
							pl.drop(stack, false);
						}
						pl.sendSystemMessage(Component.literal(Menus.tr(pl, "gui.item.given")));
					}));
			m.button(40, Menus.back(p), (pl, t, b) -> Menus.settings(pl));
		});
	}

	/** Zamyka menu i wysyla klikalna wiadomosc: klik wpisuje komende /mapcreator pick ..., a TAB podpowiada nazwy. */
	private static void searchPrompt(ServerPlayer p, String kind) {
		p.closeContainer();
		Component msg = Component.literal(Menus.tr(p, "gui.item.click_search")).withStyle(s -> s
				.withClickEvent(new ClickEvent.SuggestCommand("/mapcreator pick " + kind + " "))
				.withColor(ChatFormatting.AQUA).withUnderlined(true));
		p.sendSystemMessage(msg);
	}

	/** Wywolywane z komendy /mapcreator pick: wybor bazowego przedmiotu albo bloku do zniszczenia. */
	public static int pick(ServerPlayer p, String kind, String raw) {
		Identifier id = Identifier.tryParse(raw.trim());
		Draft d = draft(p);
		if (kind.equals("base")) {
			if (id == null || BuiltInRegistries.ITEM.getOptional(id).isEmpty()) {
				p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.item.bad_id", raw)));
				return 0;
			}
			d.base = id.toString();
		} else {
			if (id == null || BuiltInRegistries.BLOCK.getOptional(id).isEmpty()) {
				p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.item.bad_id", raw)));
				return 0;
			}
			if (!d.breaks.contains(id.toString())) {
				d.breaks.add(id.toString());
			}
		}
		p.sendSystemMessage(Component.literal(Menus.tr(p, "gui.item.picked", id.toString())));
		p.level().getServer().execute(() -> open(p));
		return 1;
	}

	/** Podpowiedzi jak w komendzie: wszystkie id zawierajace wpisany tekst (do 300). */
	public static CompletableFuture<Suggestions> suggest(Iterable<Identifier> ids, SuggestionsBuilder builder) {
		String in = builder.getRemainingLowerCase();
		int count = 0;
		for (Identifier id : ids) {
			String s = id.toString();
			if (in.isEmpty() || s.contains(in)) {
				builder.suggest(s);
				if (++count >= 300) {
					break;
				}
			}
		}
		return builder.buildFuture();
	}
}
