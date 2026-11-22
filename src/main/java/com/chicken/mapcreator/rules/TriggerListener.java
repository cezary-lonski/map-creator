package com.chicken.mapcreator.rules;

import com.chicken.mapcreator.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Wykrywanie wywolywaczy: klikniecie, otwarcie, zniszczenie, stanie, redstone. */
public final class TriggerListener {
	/** Gracz -> reguly STAND, w obszarze ktorych aktualnie stoi (wykrywanie zbocza). */
	private static final Map<UUID, Set<UUID>> STANDING = new HashMap<>();
	/** Reguly REDSTONE, ktore w poprzednim ticku byly zasilone. */
	private static final Set<UUID> POWERED = new HashSet<>();

	private TriggerListener() {}

	public static void register() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() || hand != InteractionHand.MAIN_HAND || !(player instanceof ServerPlayer sp)) {
				return InteractionResult.PASS;
			}
			if (player.getMainHandItem().is(ModItems.WAND)) {
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			MinecraftServer server = sp.level().getServer();
			BlockState state = level.getBlockState(pos);
			if (MapSettings.get(server).playing() && level.getBlockEntity(pos) instanceof net.minecraft.world.Container box) {
				MapSnapshot.get(server).recordBox(dimensionOf(level), pos, box);
			}
			fireAt(server, level, pos, Rule.TriggerType.CLICK, sp);
			if (isOpenable(state)) {
				fireAt(server, level, pos, Rule.TriggerType.OPEN, sp);
			}
			// Storage bloku: PPM (bez skradania) otwiera skrzynie z zapisanymi przedmiotami
			if (!sp.isShiftKeyDown() && BlockStorage.open(sp, dimensionOf(level), pos,
					com.chicken.mapcreator.menu.ServerLang.tr(sp, "gui.blockstorage.chest_title"))) {
				return InteractionResult.SUCCESS_SERVER;
			}
			return InteractionResult.PASS;
		});

		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (!level.isClientSide() && player instanceof ServerPlayer sp) {
				fireAt(sp.level().getServer(), level, pos, Rule.TriggerType.BREAK, sp);
				if (!MapSettings.get(sp.level().getServer()).playing()) {
					// tryb edycji: zniszczenie bloku ze skryptem usuwa tez sam skrypt
					Rule.Anchor broken = new Rule.Anchor(dimensionOf(level), pos);
					RuleStorage rs = RuleStorage.get(sp.level().getServer());
					for (Rule r : java.util.List.copyOf(rs.all())) {
						if (r.kind() == Rule.Kind.RULE && r.anchor().equals(broken)) {
							rs.remove(r.id());
						}
					}
				}
				if (MapSettings.get(sp.level().getServer()).playing()) {
					MapSnapshot.get(sp.level().getServer()).record(dimensionOf(level), pos, state);
				}
				if (level instanceof ServerLevel serverLevel) {
					BlockStorage.dropAll(serverLevel, pos, BlockStorage.get(sp.level().getServer()).remove(dimensionOf(level), pos));
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(TriggerListener::tick);

		// NPC odporny na gracza nie dostaje obrazen od gracza; "NPC otrzyma obrazenia" odpala sie po kazdym obrazeniu > 0.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity.level().isClientSide() || !(source.getEntity() instanceof Player)) {
				return true;
			}
			if (entity.getTags().contains(MobSpawner.IMMUNE_TAG)) {
				return false;
			}
			return NpcManager.ruleByEntity(entity.level().getServer(), entity.getUUID())
					.flatMap(Rule::npc).map(npc -> !npc.playerImmune()).orElse(true);
		});
		// wlasny napis smierci: gracz zabity przez moba z regula (rzut tez liczy sie do wlasciciela pocisku)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer victim && source.getEntity() != null) {
				for (String tag : source.getEntity().getTags()) {
					if (tag.startsWith(MobSpawner.TEXT_TAG_PREFIX)) {
						KillText.request(victim.getUUID(), tag.substring(MobSpawner.TEXT_TAG_PREFIX.length()));
						break;
					}
				}
			}
			// wlasny drop: zamiast zwyklego lupu mob wyrzuca wskazany przedmiot
			if (!(entity instanceof ServerPlayer) && entity.level() instanceof ServerLevel dropLevel) {
				for (String tag : java.util.List.copyOf(entity.getTags())) {
					if (tag.startsWith(MobSpawner.DROP_TAG_PREFIX)) {
						net.minecraft.world.item.ItemStack drop = MobSpawner.stack(tag.substring(MobSpawner.DROP_TAG_PREFIX.length()));
						if (!drop.isEmpty()) {
							entity.spawnAtLocation(dropLevel, drop);
						}
						dropLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.POOF, entity.getX(), entity.getY() + 0.5,
								entity.getZ(), 8, 0.2, 0.2, 0.2, 0.02);
						entity.discard();
						return false;
					}
				}
			}
			return true;
		});
		// zasady swiata po smierci: reset mapy (tylko w trakcie gry) i powrot na punkt startu
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			MinecraftServer server = newPlayer.level().getServer();
			MapSettings settings = MapSettings.get(server);
			if (!settings.playing()) {
				return;
			}
			if (!alive && settings.clearOnDeath()) {
				// bez dropu: przedmioty znikaja (jak /clear)
				newPlayer.getInventory().clearContent();
			}
			if (!alive && settings.resetOnDeath()) {
				MapControl.reset(server);
				newPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable("mapcreator.reset.after_death"));
			}
			MapControl.sendToStart(server, newPlayer);
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (entity.level().isClientSide() || damageTaken <= 0.0f) {
				return;
			}
			MinecraftServer server = entity.level().getServer();
			NpcManager.ruleByEntity(server, entity.getUUID())
					.filter(rule -> rule.trigger().type() == Rule.TriggerType.NPC_HURT)
					.ifPresent(rule -> RuleEngine.fire(server, rule, source.getEntity() instanceof ServerPlayer sp ? sp
							: (ServerPlayer) entity.level().getNearestPlayer(entity, 64.0)));
		});
	}

	/** Reset mapy: zapomina, kto na czym stal i co bylo zasilone, zeby wywolywacze mogly odpalic sie od nowa. */
	public static void resetState() {
		STANDING.clear();
		POWERED.clear();
	}

	private static boolean isOpenable(BlockState state) {
		var block = state.getBlock();
		return block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock
				|| block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof BarrelBlock
				|| block instanceof ShulkerBoxBlock;
	}

	private static String dimensionOf(Level level) {
		return level.dimension().identifier().toString();
	}

	private static void fireAt(MinecraftServer server, Level level, BlockPos pos, Rule.TriggerType type, ServerPlayer player) {
		Rule.Anchor anchor = new Rule.Anchor(dimensionOf(level), pos);
		for (Rule.Kind kind : Rule.Kind.values()) {
			RuleStorage.get(server).find(anchor, kind)
					.filter(rule -> rule.trigger().type() == type)
					.ifPresent(rule -> RuleEngine.fire(server, rule, player));
		}
	}

	private static void tick(MinecraftServer server) {
		Scheduler.tick();
		MobSpawner.tickGo(server);
		RuleStorage storage = RuleStorage.get(server);
		var rules = storage.all();
		if (rules.isEmpty()) {
			STANDING.clear();
			POWERED.clear();
			return;
		}
		tickStanding(server, rules);
		tickRedstone(server, rules);
		NpcRunner.tick(server, rules);

	}

	private static void tickStanding(MinecraftServer server, java.util.List<Rule> rules) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Set<UUID> inside = STANDING.computeIfAbsent(player.getUUID(), k -> new HashSet<>());
			String dim = dimensionOf(player.level());
			BlockPos standing = player.blockPosition().below();
			for (Rule rule : rules) {
				if (rule.trigger().type() != Rule.TriggerType.STAND
						|| !rule.anchor().dimension().equals(dim)) {
					continue;
				}
				boolean inArea = player.onGround() && rule.trigger().area()
						.map(a -> a.contains(standing))
						.orElse(standing.equals(rule.anchor().pos()));
				if (inArea && inside.add(rule.id())) {
					RuleEngine.fire(server, rule, player);
				} else if (!inArea) {
					inside.remove(rule.id());
				}
			}
		}
	}

	private static void tickRedstone(MinecraftServer server, java.util.List<Rule> rules) {
		for (Rule rule : rules) {
			if (rule.trigger().type() != Rule.TriggerType.REDSTONE) {
				continue;
			}
			ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, Identifier.parse(rule.anchor().dimension()));
			ServerLevel level = server.getLevel(key);
			if (level == null) {
				continue;
			}
			BlockPos pos = rule.anchor().pos();
			boolean powered = level.hasNeighborSignal(pos);
			if (powered && POWERED.add(rule.id())) {
				ServerPlayer nearest = (ServerPlayer) level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5,
						pos.getZ() + 0.5, 64.0, false);
				RuleEngine.fire(server, rule, nearest);
			} else if (!powered) {
				POWERED.remove(rule.id());
			}
		}
	}
}
