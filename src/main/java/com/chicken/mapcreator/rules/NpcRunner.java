package com.chicken.mapcreator.rules;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Zachowanie NPC co tick (serwer): funkcje Atak i Go oraz wywolywacze "NPC przejdzie na blok"
 * i "NPC podnosi przedmiot" (z wykrywaniem zbocza, odpalaja sie raz przy wejsciu).
 */
public final class NpcRunner {
	private static final double GO_SPEED = 0.12;
	/** NPC, ktore w poprzednim ticku staly na bloku z obszaru (reguly NPC_STEP_ON). */
	private static final Set<UUID> STEPPING = new HashSet<>();

	private NpcRunner() {}

	public static void tick(MinecraftServer server, List<Rule> rules) {
		for (Rule rule : rules) {
			if (rule.kind() != Rule.Kind.NPC || rule.npc().isEmpty()) {
				continue;
			}
			Entity entity = NpcManager.entityOf(server, rule);
			if (entity == null || !(entity.level() instanceof ServerLevel level)) {
				continue;
			}
			Rule.NpcSpec spec = rule.npc().get();
			runFunction(level, entity, spec);
			runTrigger(server, level, entity, rule);
		}
	}

	private static void runFunction(ServerLevel level, Entity entity, Rule.NpcSpec spec) {
		if (entity instanceof Mob mob) {
			boolean attack = spec.action().equals("ATTACK");
			if (mob.isNoAi() == attack) {
				mob.setNoAi(!attack);
			}
			if (attack) {
				AttributeInstance dmg = mob.getAttribute(Attributes.ATTACK_DAMAGE);
				if (dmg != null && dmg.getBaseValue() != spec.damage()) {
					dmg.setBaseValue(spec.damage());
				}
				if (mob.getTarget() == null) {
					var target = level.getNearestPlayer(mob, 24.0);
					if (target != null && !target.isCreative() && !target.isSpectator()) {
						mob.setTarget(target);
					}
				}
			}
		}
		if (spec.action().equals("GO") && spec.goTo().isPresent()) {
			BlockPos goal = spec.goTo().get();
			Vec3 target = new Vec3(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
			Vec3 delta = target.subtract(entity.position());
			double dist = delta.length();
			if (dist > 0.2) {
				Vec3 step = delta.scale(Math.min(GO_SPEED, dist) / dist);
				float yaw = (float) (Math.toDegrees(Math.atan2(-delta.x, delta.z)));
				entity.snapTo(entity.getX() + step.x, entity.getY() + step.y, entity.getZ() + step.z, yaw, 0.0f);
			}
		}
	}

	private static void runTrigger(MinecraftServer server, ServerLevel level, Entity entity, Rule rule) {
		Rule.Trigger trigger = rule.trigger();
		if (trigger.type() == Rule.TriggerType.NPC_STEP_ON) {
			BlockPos standing = entity.blockPosition().below();
			boolean inside = trigger.area().map(a -> a.contains(standing)).orElse(false)
					&& (trigger.filter().isEmpty() || blockId(level, standing).equals(trigger.filter()));
			if (inside && STEPPING.add(rule.id())) {
				RuleEngine.fire(server, rule, nearestPlayer(level, entity));
			} else if (!inside) {
				STEPPING.remove(rule.id());
			}
		} else if (trigger.type() == Rule.TriggerType.NPC_PICKUP) {
			for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(1.0))) {
				String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
				if (trigger.filter().isEmpty() || id.equals(trigger.filter())) {
					item.discard();
					RuleEngine.fire(server, rule, nearestPlayer(level, entity));
					return;
				}
			}
		}
	}

	private static String blockId(ServerLevel level, BlockPos pos) {
		return BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString();
	}

	private static ServerPlayer nearestPlayer(ServerLevel level, Entity entity) {
		return (ServerPlayer) level.getNearestPlayer(entity, 64.0);
	}
}
