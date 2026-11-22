package com.chicken.mapcreator.net;

import com.chicken.mapcreator.MapCreator;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/**
 * Serwer -> klient: wlacz efekt kamery na ticks tickow.
 * kind: wave, upside_down, sideways, blur, nearsighted, pixelate, windows albo custom (parametry w params, np. roll=45;wave=0.2;speed=2).
 * intensity: sila (dla okienek: rozmiar% * 100 + liczba okien).
 */
public record CameraEffectPayload(String kind, int ticks, int intensity, String params) implements CustomPacketPayload {
	public static final List<String> TYPES = List.of("wave", "upside_down", "sideways", "blur", "nearsighted", "pixelate", "windows", "custom");
	public static final String ID_PREFIX = "mapcreator:";

	public static final CustomPacketPayload.Type<CameraEffectPayload> TYPE =
			new CustomPacketPayload.Type<>(MapCreator.id("camera_effect"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CameraEffectPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CameraEffectPayload::kind,
			ByteBufCodecs.VAR_INT, CameraEffectPayload::ticks,
			ByteBufCodecs.VAR_INT, CameraEffectPayload::intensity,
			ByteBufCodecs.STRING_UTF8, CameraEffectPayload::params,
			CameraEffectPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(TYPE, CODEC);
	}

	/** Wysyla efekt graczowi. seconds <= 0 wylacza biezacy efekt. */
	public static void send(ServerPlayer player, String type, int seconds, int intensity) {
		send(player, type, seconds, intensity, "");
	}

	public static void send(ServerPlayer player, String type, int seconds, int intensity, String params) {
		ServerPlayNetworking.send(player, new CameraEffectPayload(type, Math.max(0, seconds) * 20, Math.max(1, intensity), params));
	}

	/** Efekt zapisany w regule jako id, np. "mapcreator:wave" albo "mapcreator:custom?roll=45;wave=0.2;speed=2". */
	public static void sendSpec(ServerPlayer player, String effectId, int seconds, int power) {
		String rest = effectId.substring(ID_PREFIX.length());
		int q = rest.indexOf('?');
		send(player, q < 0 ? rest : rest.substring(0, q), seconds, power, q < 0 ? "" : rest.substring(q + 1));
	}
}
