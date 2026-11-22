package com.chicken.mapcreator.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Teksty menu po stronie serwera (PL / EN). Jezyk wybiera sie przyciskiem w menu, zapisuje w swiecie, per gracz. */
public final class ServerLang {
	private static Map<String, String> en;
	private static Map<String, String> pl;

	private ServerLang() {}

	/** Zapamietany jezyk graczy (UUID -> pl/en) w danych swiata. */
	public static class Storage extends SavedData {
		public static final Codec<Storage> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("codes", Map.of()).forGetter(s -> s.codes)
		).apply(i, Storage::new));
		public static final SavedDataType<Storage> TYPE =
				new SavedDataType<>("mapcreator_lang", Storage::new, CODEC, DataFixTypes.LEVEL);

		private final Map<String, String> codes = new HashMap<>();

		public Storage() {}

		public Storage(Map<String, String> codes) {
			this.codes.putAll(codes);
		}
	}

	private static Storage storage(ServerPlayer player) {
		return player.level().getServer().overworld().getDataStorage().computeIfAbsent(Storage.TYPE);
	}

	public static String code(ServerPlayer player) {
		return storage(player).codes.getOrDefault(player.getUUID().toString(), "pl");
	}

	public static void toggle(ServerPlayer player) {
		Storage s = storage(player);
		s.codes.put(player.getUUID().toString(), code(player).equals("pl") ? "en" : "pl");
		s.setDirty();
	}

	public static String tr(ServerPlayer player, String key, Object... args) {
		if (en == null) {
			en = read("en_us");
			pl = read("pl_pl");
		}
		Map<String, String> map = code(player).equals("en") ? en : pl;
		String text = map.getOrDefault(key, en.getOrDefault(key, key));
		try {
			return args.length == 0 ? text : String.format(text, args);
		} catch (Exception e) {
			return text;
		}
	}

	private static Map<String, String> read(String file) {
		Map<String, String> map = new HashMap<>();
		try (InputStream in = ServerLang.class.getResourceAsStream("/assets/mapcreator/lang/" + file + ".json")) {
			if (in != null) {
				JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
				o.entrySet().forEach(e -> map.put(e.getKey(), e.getValue().getAsString()));
			}
		} catch (Exception ignored) {
			// brak pliku jezyka: zostaja klucze
		}
		return map;
	}
}
