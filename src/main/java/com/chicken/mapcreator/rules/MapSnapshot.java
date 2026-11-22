package com.chicken.mapcreator.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Migawka blokow zmienionych przez zdarzenia (i zniszczonych przez gracza w trakcie gry): pierwotny stan kazdego bloku,
 * zeby reset mapy mogl go przywrocic. Zapisywana w swiecie, wiec reset dziala tez po restarcie serwera.
 */
public class MapSnapshot extends SavedData {
	/** Powyzej tego limitu kolejne bloki nie sa juz zapamietywane (ochrona pamieci i pliku swiata). */
	public static final int MAX_ENTRIES = 400_000;

	public record Entry(String dimension, BlockPos pos, BlockState state) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dim").forGetter(Entry::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Entry::pos),
				BlockState.CODEC.fieldOf("state").forGetter(Entry::state)
		).apply(i, Entry::new));
	}

	/** Zawartosc zwyklego pojemnika (skrzynia, beczka, piec...) z chwili pierwszego otwarcia w trakcie gry. */
	public record Box(String dimension, BlockPos pos, List<net.minecraft.world.item.ItemStack> items) {
		public static final Codec<Box> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("dim").forGetter(Box::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(Box::pos),
				net.minecraft.world.item.ItemStack.OPTIONAL_CODEC.listOf().fieldOf("items").forGetter(Box::items)
		).apply(i, Box::new));
	}

	public static final Codec<MapSnapshot> CODEC = RecordCodecBuilder.create(i -> i.group(
			Entry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(s -> new ArrayList<>(s.entries.values())),
			Box.CODEC.listOf().optionalFieldOf("boxes", List.of()).forGetter(s -> new ArrayList<>(s.boxes.values()))
	).apply(i, MapSnapshot::new));

	public static final SavedDataType<MapSnapshot> TYPE =
			new SavedDataType<>("mapcreator_snapshot", MapSnapshot::new, CODEC, DataFixTypes.LEVEL);

	private final Map<String, Entry> entries = new LinkedHashMap<>();
	private final Map<String, Box> boxes = new LinkedHashMap<>();

	public MapSnapshot() {}

	public MapSnapshot(List<Entry> list, List<Box> boxList) {
		for (Entry e : list) {
			entries.put(key(e.dimension(), e.pos()), e);
		}
		for (Box b : boxList) {
			boxes.put(key(b.dimension(), b.pos()), b);
		}
	}

	/** Zapamietuje zawartosc pojemnika przy pierwszym otwarciu (pozniejsze otwarcia sa ignorowane). */
	public void recordBox(String dimension, BlockPos pos, net.minecraft.world.Container container) {
		String k = key(dimension, pos);
		if (boxes.containsKey(k) || boxes.size() >= 5000) {
			return;
		}
		List<net.minecraft.world.item.ItemStack> items = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			items.add(container.getItem(i).copy());
		}
		boxes.put(k, new Box(dimension, pos.immutable(), items));
		setDirty();
	}

	public List<Box> boxes() {
		return new ArrayList<>(boxes.values());
	}

	public static MapSnapshot get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private static String key(String dim, BlockPos pos) {
		return dim + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	/** Zapamietuje pierwotny stan bloku (tylko pierwszy raz, kolejne zmiany tego samego bloku sa ignorowane). */
	public void record(String dimension, BlockPos pos, BlockState original) {
		if (entries.size() >= MAX_ENTRIES) {
			return;
		}
		if (entries.putIfAbsent(key(dimension, pos), new Entry(dimension, pos.immutable(), original)) == null) {
			setDirty();
		}
	}

	public List<Entry> entries() {
		return new ArrayList<>(entries.values());
	}

	public void clear() {
		if (!entries.isEmpty() || !boxes.isEmpty()) {
			entries.clear();
			boxes.clear();
			setDirty();
		}
	}
}
