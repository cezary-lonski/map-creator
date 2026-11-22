package com.chicken.mapcreator.ai;

import com.chicken.mapcreator.MapCreator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ustawienia asystenta AI: dostawca (claude, openai, gemini), klucze API i modele. Zapisane w config/mapcreator-ai.json
 * na komputerze, na ktorym dziala serwer. Klucza nie wysylamy nigdzie poza adresem wybranego dostawcy.
 */
public final class AiConfig {
	public static final List<String> PROVIDERS = List.of("claude", "openai", "gemini");

	public static final class Data {
		public String provider = "claude";
		public Map<String, String> keys = new HashMap<>();
		public Map<String, String> models = new HashMap<>();
		public boolean autoRun = false;
	}

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Data data;

	private AiConfig() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mapcreator-ai.json");
	}

	public static synchronized Data get() {
		if (data == null) {
			try {
				Path f = file();
				data = Files.exists(f) ? GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Data.class) : new Data();
			} catch (Exception e) {
				MapCreator.LOGGER.warn("Cannot read the AI config, using defaults", e);
			}
			if (data == null) {
				data = new Data();
			}
			if (data.keys == null) {
				data.keys = new HashMap<>();
			}
			if (data.models == null) {
				data.models = new HashMap<>();
			}
			if (!PROVIDERS.contains(data.provider)) {
				data.provider = "claude";
			}
		}
		return data;
	}

	public static synchronized void save() {
		try {
			Files.writeString(file(), GSON.toJson(get()), StandardCharsets.UTF_8);
		} catch (Exception e) {
			MapCreator.LOGGER.warn("Cannot save the AI config", e);
		}
	}

	public static String provider() {
		return get().provider;
	}

	public static void setProvider(String provider) {
		if (PROVIDERS.contains(provider)) {
			get().provider = provider;
			save();
		}
	}

	public static String key(String provider) {
		return get().keys.getOrDefault(provider, "");
	}

	public static void setKey(String provider, String key) {
		get().keys.put(provider, key.trim());
		save();
	}

	public static String defaultModel(String provider) {
		return switch (provider) {
			case "openai" -> "gpt-4o";
			case "gemini" -> "gemini-3.8-flash";
			default -> "claude-sonnet-5-5";
		};
	}

	public static String model(String provider) {
		String m = get().models.getOrDefault(provider, "");
		return m.isBlank() ? defaultModel(provider) : m;
	}

	public static void setModel(String provider, String model) {
		get().models.put(provider, model.trim());
		save();
	}

	public static boolean autoRun() {
		return get().autoRun;
	}

	public static void setAutoRun(boolean v) {
		get().autoRun = v;
		save();
	}

	/** Klucz widziany w menu: tylko ostatnie 4 znaki. */
	public static String masked(String provider) {
		String k = key(provider);
		if (k.isEmpty()) {
			return "";
		}
		return "****" + k.substring(Math.max(0, k.length() - 4));
	}
}
