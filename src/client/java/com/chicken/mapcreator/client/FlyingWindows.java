package com.chicken.mapcreator.client;

import com.chicken.mapcreator.MapCreator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/**
 * Efekt "latajace okienka": trzy male okna z zywym obrazem gry odbijaja sie po ekranie (jak wygaszacz).
 * W kazdej klatce kopiuje obraz gry do wlasnej tekstury i rysuje go w oknach w HUD.
 */
public final class FlyingWindows {
	/** Jesli obraz w oknach jest do gory nogami na twoim sprzecie, zmien na false. */
	private static final boolean FLIP_VERTICALLY = true;
	private static final int WINDOWS = 3;
	private static final int TITLE_HEIGHT = 10;
	private static final Identifier MIRROR_ID = MapCreator.id("window_mirror");

	private static MirrorTexture mirror;
	private static int texW;
	private static int texH;
	private static boolean broken;

	private FlyingWindows() {}

	/** Tekstura opakowujaca nasza kopie klatki, zeby GuiGraphics mogl ja narysowac po Identifier. */
	private static final class MirrorTexture extends AbstractTexture {
		MirrorTexture(GpuTexture texture, GpuTextureView view, GpuSampler sampler) {
			this.texture = texture;
			this.textureView = view;
			this.sampler = sampler;
		}
	}

	public static void register() {
		HudRenderCallback.EVENT.register(FlyingWindows::render);
	}

	private static void render(GuiGraphics graphics, DeltaTracker delta) {
		if (!CameraEffects.is("windows")) {
			release();
			return;
		}
		if (broken) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		RenderTarget main = mc.getMainRenderTarget();
		try {
			ensureTexture(mc, main.width, main.height);
			// obraz gry (jeszcze bez HUD, bo HUD jest rysowany pozniej) -> nasza tekstura
			RenderSystem.getDevice().createCommandEncoder()
					.copyTextureToTexture(main.getColorTexture(), mirror.getTexture(), 0, 0, 0, 0, 0, main.width, main.height);
		} catch (RuntimeException e) {
			broken = true;
			MapCreator.LOGGER.error("Flying windows disabled: cannot copy the game frame", e);
			return;
		}
		drawWindows(graphics);
	}

	private static void ensureTexture(Minecraft mc, int width, int height) {
		if (mirror != null && texW == width && texH == height) {
			return;
		}
		release();
		GpuDevice device = RenderSystem.getDevice();
		GpuTexture texture = device.createTexture("mapcreator_window_mirror",
				GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, TextureFormat.RGBA8, width, height, 1, 1);
		GpuTextureView view = device.createTextureView(texture);
		GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		mirror = new MirrorTexture(texture, view, sampler);
		mc.getTextureManager().register(MIRROR_ID, mirror);
		texW = width;
		texH = height;
	}

	private static void release() {
		if (mirror != null) {
			Minecraft.getInstance().getTextureManager().release(MIRROR_ID);
			mirror = null;
		}
	}

	/** Plynnie zmieniajacy sie kolor tla (HSV -> RGB), nieprzezroczysty. */
	private static int backgroundColor() {
		float hue = (float) ((System.nanoTime() / 1_000_000_000.0 * 0.05) % 1.0);
		float sat = 0.65f;
		float val = 0.85f;
		float h6 = hue * 6.0f;
		int sector = (int) Math.floor(h6) % 6;
		float f = h6 - (float) Math.floor(h6);
		float p = val * (1 - sat);
		float q = val * (1 - sat * f);
		float t = val * (1 - sat * (1 - f));
		float r;
		float gr;
		float b;
		switch (sector) {
			case 0 -> { r = val; gr = t; b = p; }
			case 1 -> { r = q; gr = val; b = p; }
			case 2 -> { r = p; gr = val; b = t; }
			case 3 -> { r = p; gr = q; b = val; }
			case 4 -> { r = t; gr = p; b = val; }
			default -> { r = val; gr = p; b = q; }
		}
		return 0xFF000000 | ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
	}

	/** Losowy wzorek tla: pelny albo pusty prostokat, ktory dryfuje po ekranie i po chwili znika. */
	private static final class Shape {
		float x;
		float y;
		float w;
		float h;
		float vx;
		float vy;
		float life;
		int color;
		boolean hollow;
	}

	private static final java.util.List<Shape> SHAPES = new java.util.ArrayList<>();
	private static final java.util.Random RNG = new java.util.Random();
	private static final int SHAPE_COUNT = 36;
	private static long lastNanos = 0;

	private static int hsvColor(float hue, float sat, float val, int alpha) {
		float h6 = (hue - (float) Math.floor(hue)) * 6.0f;
		int sector = (int) Math.floor(h6) % 6;
		float f = h6 - (float) Math.floor(h6);
		float p = val * (1 - sat);
		float q = val * (1 - sat * f);
		float t = val * (1 - sat * (1 - f));
		float r;
		float gr;
		float b;
		switch (sector) {
			case 0 -> { r = val; gr = t; b = p; }
			case 1 -> { r = q; gr = val; b = p; }
			case 2 -> { r = p; gr = val; b = t; }
			case 3 -> { r = p; gr = q; b = val; }
			case 4 -> { r = t; gr = p; b = val; }
			default -> { r = val; gr = p; b = q; }
		}
		return (alpha << 24) | ((int) (r * 255) << 16) | ((int) (gr * 255) << 8) | (int) (b * 255);
	}

	private static Shape newShape(int screenW, int screenH) {
		Shape s = new Shape();
		s.w = 10 + RNG.nextFloat() * 70;
		s.h = RNG.nextBoolean() ? s.w : 6 + RNG.nextFloat() * 60;
		s.x = RNG.nextFloat() * screenW;
		s.y = RNG.nextFloat() * screenH;
		s.vx = (RNG.nextFloat() - 0.5f) * 80;
		s.vy = (RNG.nextFloat() - 0.5f) * 80;
		s.life = 3 + RNG.nextFloat() * 6;
		s.color = hsvColor(RNG.nextFloat(), 0.9f, 1.0f, 0x90 + RNG.nextInt(0x50));
		s.hollow = RNG.nextInt(100) < 40;
		return s;
	}

	/** Teczowe tlo (pionowe pasy, ktore plyna) z losowymi wzorkami: prostokaty i ramki w roznych kolorach. */
	private static void drawBackground(GuiGraphics g, int screenW, int screenH) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000.0f);
		lastNanos = now;
		double t = now / 1_000_000_000.0;
		int strips = 72;
		for (int i = 0; i < strips; i++) {
			float hue = (float) (((double) i / strips + t * 0.07) % 1.0);
			int x0 = i * screenW / strips;
			int x1 = (i + 1) * screenW / strips;
			g.fill(x0, 0, x1, screenH, hsvColor(hue, 0.7f, 0.95f, 255));
		}
		while (SHAPES.size() < SHAPE_COUNT) {
			SHAPES.add(newShape(screenW, screenH));
		}
		for (int i = 0; i < SHAPES.size(); i++) {
			Shape s = SHAPES.get(i);
			s.x += s.vx * dt;
			s.y += s.vy * dt;
			s.life -= dt;
			if (s.life <= 0 || s.x > screenW || s.y > screenH || s.x + s.w < 0 || s.y + s.h < 0) {
				s = newShape(screenW, screenH);
				SHAPES.set(i, s);
			}
			int x = (int) s.x;
			int y = (int) s.y;
			int w = (int) s.w;
			int h = (int) s.h;
			if (s.hollow) {
				int th = 3;
				g.fill(x, y, x + w, y + th, s.color);
				g.fill(x, y + h - th, x + w, y + h, s.color);
				g.fill(x, y, x + th, y + h, s.color);
				g.fill(x + w - th, y, x + w, y + h, s.color);
			} else {
				g.fill(x, y, x + w, y + h, s.color);
			}
		}
	}

	private static double triangle(double x) {
		double f = x - Math.floor(x);
		return f < 0.5 ? f * 2.0 : 2.0 - f * 2.0;
	}

	private static void drawWindows(GuiGraphics g) {
		int screenW = g.guiWidth();
		int screenH = g.guiHeight();
		// tlo: kolorowy ekran zamiast widocznego swiata (powoli zmienia barwe); obraz gry jest tylko w oknach
		drawBackground(g, screenW, screenH);
		// ustawienia z reguly: sila = rozmiar% * 100 + liczba okien (starsze efekty bez ustawien: 3 okna po 30%)
		int power = CameraEffects.intensity();
		int count = power % 100;
		int sizePercent = power / 100;
		if (power < 100 || count == 0) {
			count = WINDOWS;
		}
		if (sizePercent == 0) {
			sizePercent = 30;
		}
		count = Math.max(1, Math.min(12, count));
		double scale = Math.max(10, Math.min(90, sizePercent)) / 100.0;
		int w = (int) (screenW * scale);
		int contentH = (int) (screenH * scale);
		int h = contentH + TITLE_HEIGHT;
		double t = System.nanoTime() / 1_000_000_000.0;
		for (int i = 0; i < count; i++) {
			double fx = triangle(t * (0.11 + 0.045 * i) + i * 0.37);
			double fy = triangle(t * (0.083 + 0.05 * i) + i * 0.61);
			int x = (int) (fx * (screenW - w));
			int y = (int) (fy * (screenH - h));
			g.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF1E1E1E);
			g.fill(x, y, x + w, y + TITLE_HEIGHT, 0xFF2B5DAB);
			g.fill(x + w - 9, y + 1, x + w - 1, y + TITLE_HEIGHT - 1, 0xFFC0392B);
			float v = FLIP_VERTICALLY ? texH : 0f;
			int vHeight = FLIP_VERTICALLY ? -texH : texH;
			g.blit(RenderPipelines.GUI_TEXTURED, MIRROR_ID, x, y + TITLE_HEIGHT, 0.0f, v, w, contentH, texW, vHeight, texW, texH);
		}
	}
}
