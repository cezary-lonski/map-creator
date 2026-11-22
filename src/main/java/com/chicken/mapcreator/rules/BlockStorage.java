package com.chicken.mapcreator.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Storage bloku: dodaje blokowi pojemnosc (1-6 rzedow). PPM na taki blok otwiera skrzynie z zapisanymi przedmiotami.
 * Zawartosc zapisuje sie w danych swiata, wiec przetrwa restart i kopiowanie swiata.
 */
public class BlockStorage extends SavedData {
	public static final int MAX_ROWS = 6;

	/** Jedna pojemnosc: klucz bloku, liczba rzedow, zawartosc (puste sloty to pusty stos). */
	public record Entry(String key, int rows, List<ItemStack> items, List<ItemStack> base) {
		public Entry(String key, int rows, List<ItemStack> items) {
			this(key, rows, items, List.of());
		}

		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("key").forGetter(Entry::key),
				Codec.INT.fieldOf("rows").forGetter(Entry::rows),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(Entry::items),
				ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("base", List.of()).forGetter(Entry::base)
		).apply(i, Entry::new));
	}

	public static final Codec<BlockStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(s -> new ArrayList<>(s.entries.values()))
	).apply(i, BlockStorage::new));

	public static final SavedDataType<BlockStorage> TYPE =
			new SavedDataType<>("mapcreator_block_storage", BlockStorage::new, CODEC, DataFixTypes.LEVEL);

	private final Map<String, Entry> entries = new LinkedHashMap<>();

	public BlockStorage() {}

	public BlockStorage(List<Entry> list) {
		for (Entry e : list) {
			entries.put(e.key(), e);
		}
	}

	public static BlockStorage get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public static String keyOf(String dimension, BlockPos pos) {
		return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public Optional<Entry> find(String dimension, BlockPos pos) {
		return Optional.ofNullable(entries.get(keyOf(dimension, pos)));
	}

	/** Wlacza storage (albo zmienia liczbe rzedow); przedmioty, ktore sie nie mieszcza, wracaja jako lista. */
	public List<ItemStack> setRows(String dimension, BlockPos pos, int rows) {
		int r = Math.max(1, Math.min(MAX_ROWS, rows));
		String key = keyOf(dimension, pos);
		Entry old = entries.get(key);
		List<ItemStack> kept = new ArrayList<>();
		List<ItemStack> overflow = new ArrayList<>();
		if (old != null) {
			for (int i = 0; i < old.items().size(); i++) {
				ItemStack s = old.items().get(i);
				if (s.isEmpty()) {
					continue;
				}
				(i < r * 9 ? kept : overflow).add(s);
			}
			// zachowaj pozycje slotow, ktore sie mieszcza
			kept.clear();
			for (int i = 0; i < r * 9; i++) {
				kept.add(i < old.items().size() ? old.items().get(i) : ItemStack.EMPTY);
			}
		} else {
			for (int i = 0; i < r * 9; i++) {
				kept.add(ItemStack.EMPTY);
			}
		}
		entries.put(key, new Entry(key, r, kept, old == null ? List.of() : old.base()));
		setDirty();
		return overflow;
	}

	/** Start gry: zapamietuje obecna zawartosc wszystkich storage jako stan poczatkowy. */
	public void snapshotBaseline() {
		for (String k : new ArrayList<>(entries.keySet())) {
			Entry e = entries.get(k);
			List<ItemStack> base = new ArrayList<>();
			for (ItemStack st : e.items()) {
				base.add(st.copy());
			}
			entries.put(k, new Entry(e.key(), e.rows(), e.items(), base));
		}
		setDirty();
	}

	/** Reset mapy: przywraca zawartosc storage do stanu poczatkowego (przedmioty zabrane przez gracza wracaja do skrzynek). */
	public void restoreBaseline() {
		for (String k : new ArrayList<>(entries.keySet())) {
			Entry e = entries.get(k);
			if (e.base().isEmpty()) {
				continue;
			}
			List<ItemStack> items = new ArrayList<>();
			for (int i = 0; i < e.rows() * 9; i++) {
				items.add(i < e.base().size() ? e.base().get(i).copy() : ItemStack.EMPTY);
			}
			entries.put(k, new Entry(e.key(), e.rows(), items, e.base()));
		}
		setDirty();
	}

	/** Wylacza storage i zwraca jego zawartosc. */
	public List<ItemStack> remove(String dimension, BlockPos pos) {
		Entry old = entries.remove(keyOf(dimension, pos));
		List<ItemStack> items = new ArrayList<>();
		if (old != null) {
			old.items().stream().filter(s -> !s.isEmpty()).forEach(items::add);
			setDirty();
		}
		return items;
	}

	private void save(String key, SimpleContainer container) {
		Entry old = entries.get(key);
		if (old == null) {
			return;
		}
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			items.add(container.getItem(i).copy());
		}
		entries.put(key, new Entry(key, old.rows(), items, old.base()));
		setDirty();
	}

	private static MenuType<ChestMenu> typeFor(int rows) {
		return switch (rows) {
			case 1 -> MenuType.GENERIC_9x1;
			case 2 -> MenuType.GENERIC_9x2;
			case 3 -> MenuType.GENERIC_9x3;
			case 4 -> MenuType.GENERIC_9x4;
			case 5 -> MenuType.GENERIC_9x5;
			default -> MenuType.GENERIC_9x6;
		};
	}

	/** Otwiera zwykla skrzynie z zawartoscia storage bloku; kazda zmiana zapisuje sie od razu. */
	public static boolean open(ServerPlayer player, String dimension, BlockPos pos, String title) {
		BlockStorage storage = get(player.level().getServer());
		Optional<Entry> found = storage.find(dimension, pos);
		if (found.isEmpty()) {
			return false;
		}
		Entry e = found.get();
		SimpleContainer container = new SimpleContainer(e.rows() * 9);
		for (int i = 0; i < Math.min(e.items().size(), container.getContainerSize()); i++) {
			container.setItem(i, e.items().get(i).copy());
		}
		container.addListener(c -> storage.save(e.key(), container));
		player.openMenu(new SimpleMenuProvider((id, inv, p) ->
				new ChestMenu(typeFor(e.rows()), id, inv, container, e.rows()), Component.literal(title)));
		return true;
	}

	/** Wyrzuca przedmioty na ziemie w danym miejscu (np. po zniszczeniu bloku albo zmniejszeniu pojemnosci). */
	public static void dropAll(ServerLevel level, BlockPos pos, List<ItemStack> items) {
		for (ItemStack s : items) {
			Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, s);
		}
	}
}
