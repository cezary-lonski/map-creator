package com.chicken.mapcreator.rules;

import com.chicken.mapcreator.MapCreator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * NPC zwiazany z kotwica (regula rodzaju NPC). Etap 13: tworzenie (mob albo gracz ze skinem po nicku),
 * zapis w regule (z UUID bytu) i przywolanie jeden blok nad kotwica. NPC-gracz to minecraft:mannequin.
 */
public final class NpcManager {
	/** Specjalna nazwa "moba": gracz (mannequin ze skinem). */
	public static final String PLAYER_MOB = "player";
	private static final Pattern NICK = Pattern.compile("[A-Za-z0-9_]{3,16}");

	private NpcManager() {}

	public static void create(ServerPlayer player, Rule.Anchor anchor, String mobInput, String skinInput) {
		create(player, anchor, mobInput, skinInput, Optional.empty());
	}

	/** Tworzy NPC; base to dotychczasowe ustawienia funkcji (przy podmianie moba, np. na wiesniaka dla Trade). */
	public static void create(ServerPlayer player, Rule.Anchor anchor, String mobInput, String skinInput,
			Optional<Rule.NpcSpec> base) {
		MinecraftServer server = player.level().getServer();
		ServerLevel level = RuleActions.levelOf(server, anchor.dimension());
		if (level == null) {
			return;
		}
		String mobName = mobInput.trim().toLowerCase();
		String skin = skinInput.trim();
		boolean isPlayer = mobName.equals(PLAYER_MOB);
		EntityType<?> type;
		if (isPlayer) {
			type = EntityType.MANNEQUIN;
			if (!NICK.matcher(skin).matches()) {
				player.sendSystemMessage(Component.translatable("mapcreator.npc.bad_skin", skin));
				return;
			}
		} else {
			Optional<EntityType<?>> found = EntityType.byString(mobName);
			if (found.isEmpty() || found.get() == EntityType.PLAYER) {
				player.sendSystemMessage(Component.translatable("mapcreator.npc.bad_mob", mobInput));
				return;
			}
			type = found.get();
		}
		RuleStorage storage = RuleStorage.get(server);
		Optional<Rule> existing = storage.find(anchor, Rule.Kind.NPC);
		Entity previous = existing.map(r -> entityOf(server, r)).orElse(null);
		BlockPos previousPos = previous == null ? null : previous.blockPosition();
		existing.ifPresent(r -> removeEntity(server, r));

		Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (!(entity instanceof LivingEntity)) {
			if (entity != null) {
				entity.discard();
			}
			player.sendSystemMessage(Component.translatable("mapcreator.npc.bad_mob", mobInput));
			return;
		}
		BlockPos above = com.chicken.mapcreator.selection.SelectionManager.takeSpawn(player)
				.orElseGet(() -> previousPos != null ? previousPos : anchor.pos().above());
		entity.snapTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, player.getYRot() + 180.0f, 0.0f);
		if (isPlayer) {
			entity.setComponent(DataComponents.PROFILE, ResolvableProfile.createUnresolved(skin));
		}
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			mob.setNoAi(true);
		}
		level.addFreshEntity(entity);

		UUID entityId = entity.getUUID();
		Rule.NpcSpec spec = new Rule.NpcSpec(isPlayer ? PLAYER_MOB : mobName, isPlayer ? skin : "",
				base.map(Rule.NpcSpec::action).orElse("NONE"), base.map(Rule.NpcSpec::tradeIn).orElse(""),
				base.map(Rule.NpcSpec::tradeOut).orElse(""), base.map(Rule.NpcSpec::damage).orElse(1.0),
				base.flatMap(Rule.NpcSpec::goTo), base.map(Rule.NpcSpec::deleteOnTrigger).orElse(true),
				Optional.of(entityId), base.map(Rule.NpcSpec::playerImmune).orElse(true), Optional.of(above),
				base.map(Rule.NpcSpec::appear).orElse(false));
		applyTrade(entity, spec);
		Rule rule = new Rule(existing.map(Rule::id).orElseGet(UUID::randomUUID), Rule.Kind.NPC, anchor,
				existing.map(Rule::trigger).orElse(Rule.Trigger.of(Rule.TriggerType.NONE)), List.of(), List.of(),
				Rule.PlayerEffects.NONE, Optional.of(spec));
		storage.put(rule);
		player.sendSystemMessage(Component.translatable("mapcreator.npc.created", rule.shortId(), above.getX(), above.getY(), above.getZ()));
	}

	/** Usuwa NPC (byt przez discard, nie komenda kill) i jego regule. */
	public static void delete(ServerPlayer player, Rule.Anchor anchor) {
		MinecraftServer server = player.level().getServer();
		RuleStorage storage = RuleStorage.get(server);
		Optional<Rule> rule = storage.find(anchor, Rule.Kind.NPC);
		if (rule.isEmpty()) {
			player.sendSystemMessage(Component.translatable("mapcreator.npc.none"));
			return;
		}
		removeEntity(server, rule.get());
		storage.remove(rule.get().id());
		player.sendSystemMessage(Component.translatable("mapcreator.npc.deleted"));
	}

	/** Przywraca NPC na zapamietanym miejscu (reset mapy): usuwa stary byt i tworzy nowy z tymi samymi ustawieniami. */
	public static void respawn(MinecraftServer server, Rule rule) {
		if (rule.npc().map(Rule.NpcSpec::appear).orElse(false)) {
			removeEntity(server, rule);
		} else {
			spawn(server, rule);
		}
	}

	/** Tworzy NPC na zapamietanym miejscu: przy resecie mapy albo gdy odpali sie trigger NPC "pojawia sie po triggerze". */
	public static void spawn(MinecraftServer server, Rule rule) {
		Rule.NpcSpec spec = rule.npc().orElse(null);
		if (spec == null || spec.pos().isEmpty()) {
			return;
		}
		removeEntity(server, rule);
		ServerLevel level = RuleActions.levelOf(server, rule.anchor().dimension());
		boolean isPlayer = spec.mob().equals(PLAYER_MOB);
		EntityType<?> type = isPlayer ? EntityType.MANNEQUIN : EntityType.byString(spec.mob()).orElse(null);
		if (level == null || type == null) {
			return;
		}
		Entity entity = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (!(entity instanceof LivingEntity)) {
			return;
		}
		BlockPos at = spec.pos().get();
		entity.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0f, 0.0f);
		if (isPlayer) {
			entity.setComponent(DataComponents.PROFILE, ResolvableProfile.createUnresolved(spec.skin()));
		}
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			mob.setNoAi(!spec.action().equals("ATTACK"));
		}
		applyTrade(entity, spec);
		level.addFreshEntity(entity);
		Rule.NpcSpec updated = new Rule.NpcSpec(spec.mob(), spec.skin(), spec.action(), spec.tradeIn(), spec.tradeOut(),
				spec.damage(), spec.goTo(), spec.deleteOnTrigger(), Optional.of(entity.getUUID()), spec.playerImmune(), spec.pos(), spec.appear());
		RuleStorage.get(server).put(new Rule(rule.id(), rule.kind(), rule.anchor(), rule.trigger(), rule.events(),
				rule.mobs(), rule.effects(), Optional.of(updated)));
	}

	public static void removeEntity(MinecraftServer server, Rule rule) {
		rule.npc().flatMap(Rule.NpcSpec::uuid).ifPresent(uuid -> {
			for (ServerLevel level : server.getAllLevels()) {
				Entity e = level.getEntity(uuid);
				if (e != null) {
					e.discard();
					return;
				}
			}
			MapCreator.LOGGER.info("NPC entity {} not loaded, only the rule was removed", uuid);
		});
	}

	/** Znajduje zaladowany byt NPC danej reguly albo null. */
	public static Entity entityOf(MinecraftServer server, Rule rule) {
		UUID uuid = rule.npc().flatMap(Rule.NpcSpec::uuid).orElse(null);
		if (uuid == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(uuid);
			if (e != null && !e.isRemoved()) {
				return e;
			}
		}
		return null;
	}

	public static Optional<Rule> ruleByEntity(MinecraftServer server, UUID entityId) {
		return RuleStorage.get(server).all().stream().filter(r -> r.kind() == Rule.Kind.NPC
				&& r.npc().flatMap(Rule.NpcSpec::uuid).filter(entityId::equals).isPresent()).findFirst();
	}

	/** Funkcja Trade: wiesniak z jedna oferta (co gracz oddaje -> co dostaje). Dla innych NPC nic nie robi. */
	public static void applyTrade(Entity entity, Rule.NpcSpec spec) {
		if (!(entity instanceof net.minecraft.world.entity.npc.villager.Villager villager) || !spec.action().equals("TRADE")) {
			return;
		}
		net.minecraft.world.item.ItemStack in = stackWithCount(spec.tradeIn());
		net.minecraft.world.item.ItemStack out = stackWithCount(spec.tradeOut());
		villager.getOffers().clear();
		if (!in.isEmpty() && !out.isEmpty()) {
			villager.getOffers().add(new net.minecraft.world.item.trading.MerchantOffer(
					new net.minecraft.world.item.trading.ItemCost(in.getItem(), in.getCount()), out, 999999, 0, 0.0f));
		}
	}

	/** "id,ilosc" -> stos (pusty przy zlym id). */
	public static net.minecraft.world.item.ItemStack stackWithCount(String spec) {
		String[] p = (spec + ",1").split(",");
		net.minecraft.world.item.ItemStack stack = MobSpawner.stack(p[0]);
		if (stack.isEmpty()) {
			return stack;
		}
		try {
			stack.setCount(Math.max(1, Math.min(64, Integer.parseInt(p[1].trim()))));
		} catch (NumberFormatException e) {
			stack.setCount(1);
		}
		return stack;
	}

	/** Opis NPC do menu: "villager" albo "player (skin Nick)", a gdy brak NPC "-". */
	public static String describe(RuleStorage storage, Rule.Anchor anchor) {
		return storage.find(anchor, Rule.Kind.NPC).flatMap(Rule::npc).map(n ->
				n.mob().replace(';', ' ') + (n.skin().isEmpty() ? "" : " (" + n.skin() + ")")).orElse("-");
	}

	/** "1" gdy NPC jest odporny na gracza, "0" gdy nie, "-" gdy brak NPC. */
	public static String describeImmune(RuleStorage storage, Rule.Anchor anchor) {
		return storage.find(anchor, Rule.Kind.NPC).flatMap(Rule::npc).map(n -> n.playerImmune() ? "1" : "0").orElse("-");
	}
}
