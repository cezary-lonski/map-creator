package com.chicken.mapcreator.client;

import com.chicken.mapcreator.MapCreator;
import com.chicken.mapcreator.client.mixin.GameRendererAccessor;
import com.chicken.mapcreator.net.CameraEffectPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;

/**
 * Efekty kamery po stronie klienta. Obrot i falowanie to modyfikacja obrotu kamery (mixin w CameraMixin),
 * rozmycie, krotkowzrocznosc i pikselowanie to pelnoekranowe shadery (post_effect).
 * W danej chwili dziala jeden efekt, nowy zastepuje poprzedni.
 */
public final class CameraEffects {
	private static String active = null;
	private static long endNanos = 0;
	private static int intensity = 1;

	private CameraEffects() {}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(CameraEffectPayload.TYPE, (payload, context) ->
				start(payload.kind(), payload.ticks(), payload.intensity(), payload.params()));
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (active != null && System.nanoTime() >= endNanos) {
				stop();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> stop());
	}

	public static void start(String type, int ticks, int power, String params) {
		stop();
		if (ticks <= 0 || !CameraEffectPayload.TYPES.contains(type)) {
			return;
		}
		active = type;
		intensity = Math.max(1, power);
		parseCustom(params);
		endNanos = System.nanoTime() + ticks * 50_000_000L;
		Identifier post = switch (type) {
			case "blur", "nearsighted", "pixelate" -> MapCreator.id(type);
			default -> null;
		};
		if (post != null) {
			((GameRendererAccessor) Minecraft.getInstance().gameRenderer).mapcreator$setPostEffect(post);
		}
	}

	public static int intensity() {
		return intensity;
	}

	/** Czy aktualnie dziala dany efekt (uzywane przez renderowanie okienek). */
	public static boolean is(String type) {
		return type.equals(active);
	}

	private static double customRoll = 0;
	private static double customWave = 0;
	private static double customSpeed = 1;

	/** Parametry efektu "custom": roll (stopnie), wave (0-1), speed (0.1-10). To tylko liczby, nigdy kod. */
	private static void parseCustom(String params) {
		customRoll = 0;
		customWave = 0;
		customSpeed = 1;
		if (params == null) {
			return;
		}
		for (String part : params.split(";")) {
			String[] kv = part.split("=", 2);
			if (kv.length != 2) {
				continue;
			}
			try {
				double v = Double.parseDouble(kv[1].trim());
				switch (kv[0].trim()) {
					case "roll" -> customRoll = Math.max(-360, Math.min(360, v));
					case "wave" -> customWave = Math.max(0, Math.min(1, v));
					case "speed" -> customSpeed = Math.max(0.1, Math.min(10, v));
					default -> { }
				}
			} catch (NumberFormatException ignored) {
				// zla liczba: parametr zostaje domyslny
			}
		}
	}

	public static void stop() {
		if (active != null && (active.equals("blur") || active.equals("nearsighted") || active.equals("pixelate"))) {
			Minecraft.getInstance().gameRenderer.clearPostEffect();
		}
		active = null;
	}

	/** Wywolywane z CameraMixin po ustawieniu obrotu kamery: dokleja obrot dookola osi patrzenia (roll) i kolysanie. */
	public static void applyRotation(Quaternionf rotation) {
		if (active == null) {
			return;
		}
		switch (active) {
			case "upside_down" -> rotation.rotateZ((float) Math.PI);
			case "sideways" -> rotation.rotateZ((float) (Math.PI / 2.0));
			case "wave" -> {
				double t = System.nanoTime() / 1_000_000_000.0;
				float power = Math.min(4, intensity);
				rotation.rotateZ((float) (0.14 * power * Math.sin(t * 1.7)));
				rotation.rotateX((float) (0.05 * power * Math.sin(t * 1.1)));
				rotation.rotateY((float) (0.04 * power * Math.sin(t * 0.9)));
			}
			case "custom" -> {
				double t = System.nanoTime() / 1_000_000_000.0 * customSpeed;
				double amp = customWave * 0.35;
				rotation.rotateZ((float) Math.toRadians(customRoll));
				rotation.rotateZ((float) (amp * Math.sin(t * 1.7)));
				rotation.rotateX((float) (amp * 0.4 * Math.sin(t * 1.1)));
				rotation.rotateY((float) (amp * 0.3 * Math.sin(t * 0.9)));
			}
			default -> { }
		}
	}
}
