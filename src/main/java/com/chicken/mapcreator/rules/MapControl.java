package com.chicken.mapcreator.rules;

import com.chicken.mapcreator.net.CameraEffectPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Sterowanie gra: Graj (start mapy), Stop (tryb edycji), reset mapy i zasady swiata. */
public final class MapControl {
	private MapControl() {}

	/** Stosuje zasady swiata, ktore sa regulami gry (tracenie przedmiotow = keep_inventory wylaczone). */
	public static void applyGameRules(MinecraftServer server) {
		boolean keep = !MapSettings.get(server).loseItems() || MapSettings.get(server).clearOnDeath();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
				"gamerule keep_inventory " + keep);
	}

	/** Przywraca mape do stanu poczatkowego: bloki zmienione zdarzeniami, pamiec zdarzen, moby, NPC, efekty. Zwraca liczbe przywroconych blokow. */
	public static int reset(MinecraftServer server) {
		MapSnapshot snapshot = MapSnapshot.get(server);
		int restored = 0;
		for (MapSnapshot.Entry e : snapshot.entries()) {
			ServerLevel level = RuleActions.levelOf(server, e.dimension());
			if (level != null) {
				level.setBlock(e.pos(), e.state(), 2);
				restored++;
			}
		}
		// pojemniki: przedmioty zabrane przez gracza wracaja na miejsce (po przywroceniu blokow, bo skrzynia mogla byc zniszczona)
		for (MapSnapshot.Box box : snapshot.boxes()) {
			ServerLevel level = RuleActions.levelOf(server, box.dimension());
			if (level != null && level.getBlockEntity(box.pos()) instanceof net.minecraft.world.Container c) {
				for (int i = 0; i < Math.min(c.getContainerSize(), box.items().size()); i++) {
					c.setItem(i, box.items().get(i).copy());
				}
				c.setChanged();
			}
		}
		BlockStorage.get(server).restoreBaseline();
		snapshot.clear();
		FiredStorage.get(server).clear();
		Scheduler.clear();
		MobSpawner.clearTargets();
		for (ServerLevel level : server.getAllLevels()) {
			List<Entity> toRemove = new ArrayList<>();
			for (Entity entity : level.getAllEntities()) {
				if (entity.getTags().contains(MobSpawner.SPAWNED_TAG) || entity instanceof ThrownRuleItem
						|| entity instanceof net.minecraft.world.entity.item.ItemEntity
						|| entity instanceof net.minecraft.world.entity.ExperienceOrb) {
					toRemove.add(entity);
				}
			}
			toRemove.forEach(Entity::discard);
		}
		for (Rule rule : RuleStorage.get(server).all()) {
			if (rule.kind() == Rule.Kind.NPC) {
				NpcManager.respawn(server, rule);
			}
		}
		TriggerListener.resetState();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			CameraEffectPayload.send(player, "wave", 0, 1);
			player.removeAllEffects();
		}
		return restored;
	}

	/** Przenosi gracza do punktu startu (jesli ustawiony). */
	public static void sendToStart(MinecraftServer server, ServerPlayer player) {
		MapSettings settings = MapSettings.get(server);
		if (settings.start().isEmpty()) {
			return;
		}
		ServerLevel level = RuleActions.levelOf(server, settings.startDimension());
		if (level != null) {
			var p = settings.start().get();
			player.teleportTo(level, p.getX() + 0.5, p.getY(), p.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		}
	}

	/** Przycisk Graj: reset mapy, tryb przygodowy i teleport wszystkich graczy do punktu startu. */
	public static void play(MinecraftServer server) {
		reset(server);
		BlockStorage.get(server).snapshotBaseline();
		MapSettings settings = MapSettings.get(server);
		settings.setPlaying(true);
		applyGameRules(server);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setGameMode(GameType.ADVENTURE);
			sendToStart(server, player);
			player.sendSystemMessage(Component.translatable("mapcreator.play.started"));
		}
	}

	/** Stop: wraca tryb edycji (kreatywny), mapa przestaje resetowac sie po smierci. */
	public static void stop(MinecraftServer server) {
		if (MapSettings.get(server).playing()) {
			reset(server);
		}
		MapSettings.get(server).setPlaying(false);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			player.setGameMode(GameType.CREATIVE);
			player.sendSystemMessage(Component.translatable("mapcreator.play.stopped"));
		}
	}
}
