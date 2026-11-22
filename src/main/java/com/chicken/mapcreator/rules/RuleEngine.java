package com.chicken.mapcreator.rules;

import com.chicken.mapcreator.MapCreator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Odpalanie regul. Lancuch regul "po innym zdarzeniu" ma limit glebokosci i ochrone przed petla
 * (regula, ktora juz jest w biezacym lancuchu, nie odpali sie drugi raz).
 */
public final class RuleEngine {
	public static final int MAX_DEPTH = 8;

	private RuleEngine() {}

	/** Odpala regule dla gracza (moze byc null, np. redstone bez gracza w poblizu). */
	public static void fire(MinecraftServer server, Rule rule, ServerPlayer player) {
		fire(server, rule, player, new ArrayList<>());
	}

	private static void fire(MinecraftServer server, Rule rule, ServerPlayer player, List<UUID> chain) {
		if (chain.size() >= MAX_DEPTH || chain.contains(rule.id())) {
			MapCreator.LOGGER.warn("Rule chain stopped at [{}] (depth {} or loop)", rule.shortId(), chain.size());
			return;
		}
		if (!ConditionStorage.get(server).met(server, rule.id())) {
			return;
		}
		chain.add(rule.id());
		execute(server, rule, player);
		FiredStorage.get(server).markFired(rule.id());
		for (Rule next : RuleStorage.get(server).all()) {
			if (next.trigger().type() == Rule.TriggerType.AFTER_EVENT && next.trigger().ifIds().contains(rule.id())) {
				fire(server, next, player, chain);
			}
		}
		checkIfHappened(server, player, chain);
		chain.remove(chain.size() - 1);
	}

	/**
	 * Wywolywacz "Jesli te wydarzenia sie staly": odpala sie raz, gdy wszystkie wymienione reguly sa w storage.
	 * Sprawdzane po kazdym odpaleniu jakiejkolwiek reguly. Zapamietany w storage, wiec nie powtarza sie po restarcie.
	 */
	private static void checkIfHappened(MinecraftServer server, ServerPlayer player, List<UUID> chain) {
		FiredStorage storage = FiredStorage.get(server);
		for (Rule candidate : RuleStorage.get(server).all()) {
			Rule.Trigger t = candidate.trigger();
			if (t.type() != Rule.TriggerType.IF_HAPPENED || t.ifIds().isEmpty() || storage.isIfDone(candidate.id())) {
				continue;
			}
			if (t.ifIds().stream().allMatch(storage::hasFired)) {
				storage.markIfDone(candidate.id());
				fire(server, candidate, player, chain);
			}
		}
	}

	/** Najpierw zdarzenia na obszarze, potem efekty dla gracza (zabicie na samym koncu). */
	private static void execute(MinecraftServer server, Rule rule, ServerPlayer player) {
		MapCreator.LOGGER.info("Rule [{}] fired (trigger {})", rule.shortId(), rule.trigger().type());
		for (Rule.Event event : rule.events()) {
			RuleActions.runEvent(server, rule, event);
		}
		for (Rule.MobSpec mob : rule.mobs()) {
			MobSpawner.spawn(server, rule, mob, player);
		}
		if (player != null) {
			player.sendSystemMessage(Component.translatable("mapcreator.fire", rule.shortId(), rule.trigger().type().name()));
			RuleActions.applyEffects(rule, player);
		}
		// Funkcja po wywolaniu NPC: "Usun NPC".
		if (rule.kind() == Rule.Kind.NPC && rule.npc().map(Rule.NpcSpec::appear).orElse(false)) {
			NpcManager.spawn(server, rule);
		} else if (rule.kind() == Rule.Kind.NPC && rule.npc().map(Rule.NpcSpec::deleteOnTrigger).orElse(false)) {
			NpcManager.removeEntity(server, rule);
		}
	}
}
