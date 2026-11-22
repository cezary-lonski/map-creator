package com.chicken.mapcreator.rules;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Wlasne teksty smierci: regula zapisuje tekst, a mixin w CombatTracker podmienia komunikat o smierci. */
public final class KillText {
	private static final Map<UUID, String> PENDING = new HashMap<>();

	private KillText() {}

	public static void request(UUID player, String text) {
		PENDING.put(player, text);
	}

	/** Zwraca i usuwa oczekujacy tekst albo null. */
	public static String take(UUID player) {
		return PENDING.remove(player);
	}
}
