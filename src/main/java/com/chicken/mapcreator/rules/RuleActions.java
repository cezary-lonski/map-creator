package com.chicken.mapcreator.rules;

import com.chicken.mapcreator.MapCreator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Optional;

/** Wykonywanie zdarzen na obszarze (etap 5) i efektow dla gracza (etap 6). */
public final class RuleActions {
	/** Zabezpieczenie przed zawieszeniem serwera zbyt duzym obszarem. */
	public static final long MAX_AREA_BLOCKS = 200_000L;

	private RuleActions() {}

	public static ServerLevel levelOf(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		if (id == null) {
			return null;
		}
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	public static void runEvent(MinecraftServer server, Rule rule, Rule.Event event) {
		if (event.area().isEmpty() || event.type() == Rule.EventType.NONE) {
			return;
		}
		ServerLevel level = levelOf(server, rule.anchor().dimension());
		if (level == null) {
			return;
		}
		Rule.Area area = event.area().get();
		long size = (long) (Math.abs(area.a().getX() - area.b().getX()) + 1)
				* (Math.abs(area.a().getY() - area.b().getY()) + 1)
				* (Math.abs(area.a().getZ() - area.b().getZ()) + 1);
		if (size > MAX_AREA_BLOCKS) {
			MapCreator.LOGGER.warn("Rule [{}]: area of {} blocks is too big (max {})", rule.shortId(), size, MAX_AREA_BLOCKS);
			return;
		}
		Block target = null;
		if (event.type() == Rule.EventType.CHANGE_BLOCK) {
			target = parseBlock(event.block());
			if (target == null) {
				MapCreator.LOGGER.warn("Rule [{}]: unknown block '{}'", rule.shortId(), event.block());
				return;
			}
		}
		MapSnapshot snapshot = MapSnapshot.get(server);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = Math.min(area.a().getX(), area.b().getX()); x <= Math.max(area.a().getX(), area.b().getX()); x++) {
			for (int y = Math.min(area.a().getY(), area.b().getY()); y <= Math.max(area.a().getY(), area.b().getY()); y++) {
				for (int z = Math.min(area.a().getZ(), area.b().getZ()); z <= Math.max(area.a().getZ(), area.b().getZ()); z++) {
					pos.set(x, y, z);
					BlockState before = level.getBlockState(pos);
					if (!before.isAir() || event.type() == Rule.EventType.CHANGE_BLOCK) {
						snapshot.record(rule.anchor().dimension(), pos.immutable(), before);
					}
					if (event.type() == Rule.EventType.BROKE) {
						if (!level.getBlockState(pos).isAir()) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
						}
					} else {
						level.setBlock(pos, target.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	private static Block parseBlock(String id) {
		Identifier parsed = Identifier.tryParse(id);
		if (parsed == null) {
			return null;
		}
		return BuiltInRegistries.BLOCK.getOptional(parsed).orElse(null);
	}

	/** Efekty dla gracza: give, tp, mikstury, tekst na ekranie, na koncu zabicie. */
	public static void applyEffects(Rule rule, ServerPlayer player) {
		Rule.PlayerEffects fx = rule.effects();
		for (Rule.ItemSpec spec : fx.give()) {
			Identifier id = Identifier.tryParse(spec.item());
			Optional<Item> item = id == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(id);
			if (item.isEmpty()) {
				MapCreator.LOGGER.warn("Rule [{}]: unknown item '{}'", rule.shortId(), spec.item());
				continue;
			}
			ItemStack stack = new ItemStack(item.get(), Math.max(1, Math.min(99, spec.count())));
			if (!spec.name().isEmpty()) {
				stack.set(DataComponents.CUSTOM_NAME, Component.literal(spec.name()));
			}
			if (!player.getInventory().add(stack)) {
				player.drop(stack, false);
			}
		}
		fx.tp().ifPresent(p -> player.teleportTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5));
		for (Rule.EffectSpec spec : fx.effects()) {
			if (spec.effect().startsWith(com.chicken.mapcreator.net.CameraEffectPayload.ID_PREFIX)) {
				// efekt kamery (po stronie klienta), np. mapcreator:wave
				com.chicken.mapcreator.net.CameraEffectPayload.sendSpec(player, spec.effect(), spec.seconds(), spec.amplifier());
				continue;
			}
			Identifier id = Identifier.tryParse(spec.effect());
			var effect = id == null ? Optional.<net.minecraft.core.Holder.Reference<net.minecraft.world.effect.MobEffect>>empty()
					: BuiltInRegistries.MOB_EFFECT.get(id);
			if (effect.isEmpty()) {
				MapCreator.LOGGER.warn("Rule [{}]: unknown effect '{}'", rule.shortId(), spec.effect());
				continue;
			}
			player.addEffect(new MobEffectInstance(effect.get(), Math.max(1, spec.seconds()) * 20, Math.max(0, spec.amplifier())));
		}
		if (!fx.screenText().isEmpty()) {
			player.connection.send(new ClientboundSetTitleTextPacket(Component.literal(fx.screenText())));
		}
		if (fx.kill()) {
			if (!fx.killText().isEmpty()) {
				KillText.request(player.getUUID(), fx.killText());
			}
			ServerLevel level = (ServerLevel) player.level();
			player.hurtServer(level, level.damageSources().genericKill(), Float.MAX_VALUE);
		}
	}
}
