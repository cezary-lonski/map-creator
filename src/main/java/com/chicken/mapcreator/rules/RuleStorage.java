package com.chicken.mapcreator.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Reguly zapisane w danych swiata (data/mapcreator_rules.dat), wiec podrozuja razem ze swiatem. */
public class RuleStorage extends SavedData {
	public static final Codec<RuleStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
			Rule.CODEC.listOf().optionalFieldOf("rules", List.of()).forGetter(RuleStorage::all)
	).apply(i, RuleStorage::new));

	public static final SavedDataType<RuleStorage> TYPE =
			new SavedDataType<>("mapcreator_rules", RuleStorage::new, CODEC, DataFixTypes.LEVEL);

	/** Indeks po kluczu (kotwica + rodzaj), trzymany w pamieci. */
	private final Map<String, Rule> byKey = new LinkedHashMap<>();

	public RuleStorage() {}

	public RuleStorage(List<Rule> rules) {
		for (Rule rule : rules) {
			byKey.put(rule.key(), rule);
		}
	}

	public static RuleStorage get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public List<Rule> all() {
		return new ArrayList<>(byKey.values());
	}

	/** Dodaje regule; ta sama kotwica i rodzaj zastepuje poprzednia. */
	public void put(Rule rule) {
		byKey.put(rule.key(), rule);
		setDirty();
	}

	public Optional<Rule> find(Rule.Anchor anchor, Rule.Kind kind) {
		return Optional.ofNullable(byKey.get(Rule.keyOf(anchor, kind)));
	}

	public Optional<Rule> findById(UUID id) {
		return byKey.values().stream().filter(r -> r.id().equals(id)).findFirst();
	}

	public boolean remove(UUID id) {
		boolean removed = byKey.values().removeIf(r -> r.id().equals(id));
		if (removed) {
			setDirty();
		}
		return removed;
	}

	public int clear() {
		int n = byKey.size();
		byKey.clear();
		setDirty();
		return n;
	}
}
