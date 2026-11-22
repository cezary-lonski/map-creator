package com.chicken.mapcreator.rules;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Optional;

/**
 * Zasady swiata (ustawienia mapy): czy gracz traci przedmioty po smierci, czy kazda smierc resetuje mape,
 * czy mapa jest teraz w trybie gry oraz punkt startu. Zapisywane w danych swiata.
 */
public class MapSettings extends SavedData {
	public static final Codec<MapSettings> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("lose_items", true).forGetter(MapSettings::loseItems),
			Codec.BOOL.optionalFieldOf("reset_on_death", false).forGetter(MapSettings::resetOnDeath),
			Codec.BOOL.optionalFieldOf("playing", false).forGetter(MapSettings::playing),
			Codec.STRING.optionalFieldOf("start_dim", "").forGetter(MapSettings::startDimension),
			BlockPos.CODEC.optionalFieldOf("start").forGetter(MapSettings::start),
			Codec.BOOL.optionalFieldOf("clear_on_death", false).forGetter(MapSettings::clearOnDeath)
	).apply(i, MapSettings::new));

	public static final SavedDataType<MapSettings> TYPE =
			new SavedDataType<>("mapcreator_settings", MapSettings::new, CODEC, DataFixTypes.LEVEL);

	private boolean loseItems = true;
	private boolean resetOnDeath = false;
	private boolean playing = false;
	private String startDimension = "";
	private Optional<BlockPos> start = Optional.empty();
	private boolean clearOnDeath = false;

	public MapSettings() {}

	public MapSettings(boolean loseItems, boolean resetOnDeath, boolean playing, String startDimension, Optional<BlockPos> start, boolean clearOnDeath) {
		this.clearOnDeath = clearOnDeath;
		this.loseItems = loseItems;
		this.resetOnDeath = resetOnDeath;
		this.playing = playing;
		this.startDimension = startDimension;
		this.start = start;
	}

	public static MapSettings get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public boolean loseItems() {
		return loseItems;
	}

	public boolean clearOnDeath() {
		return clearOnDeath;
	}

	public void setClearOnDeath(boolean v) {
		clearOnDeath = v;
		setDirty();
	}

	public boolean resetOnDeath() {
		return resetOnDeath;
	}

	public boolean playing() {
		return playing;
	}

	public String startDimension() {
		return startDimension;
	}

	public Optional<BlockPos> start() {
		return start;
	}

	public void setLoseItems(boolean v) {
		loseItems = v;
		setDirty();
	}

	public void setResetOnDeath(boolean v) {
		resetOnDeath = v;
		setDirty();
	}

	public void setPlaying(boolean v) {
		playing = v;
		setDirty();
	}

	public void setStart(String dimension, BlockPos pos) {
		startDimension = dimension;
		start = Optional.of(pos);
		setDirty();
	}
}
