package com.chicken.mapcreator.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Zaawansowane warunki wywolywacza: regula odpala sie tylko, gdy wskazane skrypty juz sie wykonaly ("needs")
 * i/albo jeszcze sie nie wykonaly ("forbids"). Dzialaja dla kazdego typu wywolywacza.
 */
public class ConditionStorage extends SavedData {
	public record Cond(UUID rule, List<UUID> needs, List<UUID> forbids) {
		public static final Codec<Cond> CODEC = RecordCodecBuilder.create(i -> i.group(
				UUIDUtil.STRING_CODEC.fieldOf("rule").forGetter(Cond::rule),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("needs", List.of()).forGetter(Cond::needs),
				UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("forbids", List.of()).forGetter(Cond::forbids)
		).apply(i, Cond::new));
	}

	public static final Codec<ConditionStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
			Cond.CODEC.listOf().optionalFieldOf("conds", List.of()).forGetter(s -> new ArrayList<>(s.conds.values()))
	).apply(i, ConditionStorage::new));

	public static final SavedDataType<ConditionStorage> TYPE =
			new SavedDataType<>("mapcreator_conditions", ConditionStorage::new, CODEC, DataFixTypes.LEVEL);

	private final Map<UUID, Cond> conds = new LinkedHashMap<>();

	public ConditionStorage() {}

	public ConditionStorage(List<Cond> list) {
		for (Cond c : list) {
			conds.put(c.rule(), c);
		}
	}

	public static ConditionStorage get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Cond of(UUID rule) {
		return conds.getOrDefault(rule, new Cond(rule, List.of(), List.of()));
	}

	public void set(UUID rule, List<UUID> needs, List<UUID> forbids) {
		if (needs.isEmpty() && forbids.isEmpty()) {
			conds.remove(rule);
		} else {
			conds.put(rule, new Cond(rule, List.copyOf(needs), List.copyOf(forbids)));
		}
		setDirty();
	}

	/** Czy warunki reguly sa spelnione wedlug pamieci wydarzen (Storage). */
	public boolean met(MinecraftServer server, UUID rule) {
		Cond c = conds.get(rule);
		if (c == null) {
			return true;
		}
		FiredStorage fired = FiredStorage.get(server);
		return c.needs().stream().allMatch(fired::hasFired) && c.forbids().stream().noneMatch(fired::hasFired);
	}
}
