package com.chicken.mapcreator.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Storage: pamiec swiata o tym, ktore reguly juz sie odpalily, oraz ktore wywolywacze
 * "Jesli te wydarzenia sie staly" juz zadzialaly (zeby nie powtarzaly sie nawet po restarcie).
 * Zapisuje sie w danych swiata, wiec przetrwa restart i kopiowanie swiata.
 */
public class FiredStorage extends SavedData {
	public static final Codec<FiredStorage> CODEC = RecordCodecBuilder.create(i -> i.group(
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("fired", List.of()).forGetter(FiredStorage::firedList),
			UUIDUtil.STRING_CODEC.listOf().optionalFieldOf("if_done", List.of()).forGetter(FiredStorage::ifDoneList)
	).apply(i, FiredStorage::new));

	public static final SavedDataType<FiredStorage> TYPE =
			new SavedDataType<>("mapcreator_storage", FiredStorage::new, CODEC, DataFixTypes.LEVEL);

	private final Set<UUID> fired = new LinkedHashSet<>();
	private final Set<UUID> ifDone = new LinkedHashSet<>();

	public FiredStorage() {}

	public FiredStorage(List<UUID> fired, List<UUID> ifDone) {
		this.fired.addAll(fired);
		this.ifDone.addAll(ifDone);
	}

	public static FiredStorage get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	private List<UUID> firedList() {
		return new ArrayList<>(fired);
	}

	private List<UUID> ifDoneList() {
		return new ArrayList<>(ifDone);
	}

	public List<UUID> allFired() {
		return firedList();
	}

	public boolean markFired(UUID ruleId) {
		boolean added = fired.add(ruleId);
		if (added) {
			setDirty();
		}
		return added;
	}

	public boolean hasFired(UUID ruleId) {
		return fired.contains(ruleId);
	}

	public boolean isIfDone(UUID ruleId) {
		return ifDone.contains(ruleId);
	}

	public void markIfDone(UUID ruleId) {
		if (ifDone.add(ruleId)) {
			setDirty();
		}
	}

	public void clear() {
		fired.clear();
		ifDone.clear();
		setDirty();
	}
}
