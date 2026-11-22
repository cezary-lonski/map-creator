package com.chicken.mapcreator.rules;

import com.chicken.mapcreator.MapCreator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/** Wywolywanie mobow z reguly (etap 7): spawn nad kotwica, nick, ekwipunek, rzut albo atak. */
public final class MobSpawner {
	public static final String IMMUNE_TAG = "mapcreator_immune";
	/** Znacznik mobow przywolanych przez reguly: reset mapy usuwa wszystkie z tym znacznikiem. */
	public static final String SPAWNED_TAG = "mapcreator_spawned";
	public static final String DROP_TAG_PREFIX = "mapcreator_drop:";
	public static final String TEXT_TAG_PREFIX = "mapcreator_text:";
	/** Moby z zachowaniem GO: cel marszu (tylko w pamieci, znika po restarcie serwera). */
	private static final java.util.Map<java.util.UUID, BlockPos> GO_TARGETS = new java.util.HashMap<>();

	private MobSpawner() {}

	/** Co sekunde ponawia marsz mobow typu GO do celu (nawigacja moba). */
	public static void tickGo(MinecraftServer server) {
		if (GO_TARGETS.isEmpty() || server.getTickCount() % 20 != 0) {
			return;
		}
		var it = GO_TARGETS.entrySet().iterator();
		while (it.hasNext()) {
			var entry = it.next();
			Entity e = null;
			for (ServerLevel level : server.getAllLevels()) {
				e = level.getEntity(entry.getKey());
				if (e != null) {
					break;
				}
			}
			BlockPos goal = entry.getValue();
			if (e == null || e.isRemoved() || e.distanceToSqr(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5) < 2.25) {
				it.remove();
			} else if (e instanceof Mob mob) {
				mob.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0);
			}
		}
	}

	public static void spawn(MinecraftServer server, Rule rule, Rule.MobSpec spec, ServerPlayer player) {
		ServerLevel level = RuleActions.levelOf(server, rule.anchor().dimension());
		if (level == null) {
			report(player, "mapcreator.mob.failed", spec.mob(), "dimension");
			return;
		}
		Optional<EntityType<?>> type = EntityType.byString(spec.mob());
		if (type.isEmpty()) {
			MapCreator.LOGGER.warn("Rule [{}]: unknown mob '{}'", rule.shortId(), spec.mob());
			report(player, "mapcreator.mob.failed", spec.mob(), "unknown mob");
			return;
		}
		Entity entity = type.get().create(level, EntitySpawnReason.MOB_SUMMONED);
		if (entity == null) {
			report(player, "mapcreator.mob.failed", spec.mob(), "cannot create");
			return;
		}
		BlockPos above = spec.spawn().orElse(rule.anchor().pos().above());
		entity.snapTo(above.getX() + 0.5, above.getY(), above.getZ() + 0.5, 0.0f, 0.0f);
		entity.addTag(SPAWNED_TAG);
		if (!spec.nick().isEmpty()) {
			entity.setCustomName(Component.literal(spec.nick()));
			entity.setCustomNameVisible(true);
		}
		if (entity instanceof LivingEntity living) {
			equip(living, EquipmentSlot.MAINHAND, spec.item());
			equip(living, EquipmentSlot.OFFHAND, spec.offhand());
			equip(living, EquipmentSlot.HEAD, spec.head());
			equip(living, EquipmentSlot.CHEST, spec.chest());
			equip(living, EquipmentSlot.LEGS, spec.legs());
			equip(living, EquipmentSlot.FEET, spec.feet());
		}
		if (spec.immune()) {
			entity.addTag(IMMUNE_TAG);
		}
		if (!spec.deathText().isEmpty()) {
			entity.addTag(TEXT_TAG_PREFIX + spec.deathText());
		}
		if (!spec.drop().isEmpty()) {
			entity.addTag(DROP_TAG_PREFIX + spec.drop());
		}
		if (entity instanceof Mob mob) {
			mob.setPersistenceRequired();
			// NONE i THROW stoja w miejscu (bez AI), ATTACK i GO maja normalne AI
			mob.setNoAi(spec.action().equals("NONE") || spec.action().equals("THROW"));
			if (spec.action().equals("GO") && spec.goTo().isPresent()) {
				GO_TARGETS.put(entity.getUUID(), spec.goTo().get());
			}
			if (spec.action().equals("ATTACK") && player != null) {
				AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
				if (dmg != null) {
					dmg.setBaseValue(spec.damage());
				}
				if (spec.delay() > 0) {
					// czeka (bez AI), a po opoznieniu rusza do ataku
					mob.setNoAi(true);
					Scheduler.runLater(spec.delay() * 20, () -> {
						if (!mob.isRemoved() && mob.isAlive()) {
							mob.setNoAi(false);
							mob.setTarget(player);
						}
					});
				} else {
					mob.setTarget(player);
				}
			}
		}
		if (!level.addFreshEntity(entity)) {
			report(player, "mapcreator.mob.failed", spec.mob(), "spawn refused");
			return;
		}
		report(player, "mapcreator.mob.spawned", spec.mob(), above.getX(), above.getY(), above.getZ());
		if (spec.action().equals("THROW") && player != null && entity instanceof LivingEntity thrower) {
			if (spec.delay() > 0) {
				Scheduler.runLater(spec.delay() * 20, () -> throwNow(level, thrower, spec, player));
			} else {
				throwNow(level, thrower, spec, player);
			}
		}
	}

	/** Rzut w gracza (po ewentualnym opoznieniu): pocisk wyglada jak przedmiot, a trafienie zadaje Damage. */
	private static void throwNow(ServerLevel level, LivingEntity thrower, Rule.MobSpec spec, ServerPlayer player) {
		if (thrower.isRemoved() || !thrower.isAlive() || player.isRemoved() || !player.isAlive()) {
			return;
		}
		ItemStack missile = stack(spec.item());
		if (missile.isEmpty()) {
			missile = new ItemStack(net.minecraft.world.item.Items.SNOWBALL);
		}
		ThrownRuleItem projectile = new ThrownRuleItem(level, thrower, missile, (float) spec.damage());
		projectile.setNoGravity(!spec.gravity());
		Vec3 to = player.getEyePosition().subtract(projectile.position());
		projectile.shoot(to.x, to.y, to.z, 1.3f, 0.0f);
		level.addFreshEntity(projectile);
	}

	public static void clearTargets() {
		GO_TARGETS.clear();
	}

	private static void report(ServerPlayer player, String key, Object... args) {
		if (player != null) {
			player.sendSystemMessage(Component.translatable(key, args));
		}
	}

	private static void equip(LivingEntity living, EquipmentSlot slot, String id) {
		ItemStack stack = stack(id);
		if (!stack.isEmpty()) {
			living.setItemSlot(slot, stack);
		}
	}

	/** Pusty stos, gdy id jest puste albo nieznane. */
	public static ItemStack stack(String id) {
		if (id == null || id.isBlank()) {
			return ItemStack.EMPTY;
		}
		Identifier parsed = Identifier.tryParse(id.trim());
		Optional<Item> item = parsed == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(parsed);
		return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
	}
}
