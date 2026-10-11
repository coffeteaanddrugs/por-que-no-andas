package com.smartphone.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.world.ClientWorld;

/** Clima y temperatura de donde estás (sacados del mundo de Minecraft). */
public final class Weather {
	public enum Kind {
		SUN, MOON, CLOUD, RAIN, STORM, SNOW
	}

	public record Info(Kind kind, int celsius, String label) {
	}

	private Weather() {
	}

	public static Info now() {
		MinecraftClient mc = MinecraftClient.getInstance();
		ClientWorld w = mc.world;
		if (w == null || mc.player == null) {
			return new Info(Kind.SUN, 20, "Despejado");
		}
		float base = w.getBiome(mc.player.getBlockPos()).value().getTemperature();
		double c = -2 + base * 24;
		Kind kind = Kind.SUN;
		String label = "Despejado";
		if (w.getDimension().hasSkyLight()) {
			long t = w.getTimeOfDay() % 24000L;
			boolean night = t >= 13000 && t < 23000;
			if (night) {
				c -= 6;
				kind = Kind.MOON;
				label = "Noche despejada";
			}
			if (w.isThundering()) {
				kind = Kind.STORM;
				label = "Tormenta";
				c -= 4;
			} else if (w.isRaining()) {
				c -= 3;
				if (base < 0.15f) {
					kind = Kind.SNOW;
					label = "Nevando";
				} else {
					kind = Kind.RAIN;
					label = "Lluvia";
				}
			}
		}
		return new Info(kind, (int) Math.round(c), label);
	}

	/** Dibuja el icono dentro de un cuadro de ~24x24 con centro en (cx, cy). */
	public static void icon(DrawContext g, Kind kind, int cx, int cy) {
		switch (kind) {
			case SUN -> sun(g, cx, cy);
			case MOON -> moon(g, cx, cy);
			case CLOUD -> cloud(g, cx, cy, 0xFFE8EEF8);
			case RAIN -> {
				cloud(g, cx, cy - 3, 0xFFB8C4D6);
				for (int i = -1; i <= 1; i++) {
					g.fill(cx + i * 6 - 1, cy + 6, cx + i * 6, cy + 10, 0xFF4DA3FF);
				}
			}
			case STORM -> {
				cloud(g, cx, cy - 3, 0xFF7D879A);
				g.fill(cx, cy + 3, cx + 2, cy + 7, 0xFFFFD84D);
				g.fill(cx - 2, cy + 6, cx + 1, cy + 8, 0xFFFFD84D);
				g.fill(cx - 1, cy + 8, cx + 1, cy + 11, 0xFFFFD84D);
			}
			case SNOW -> {
				cloud(g, cx, cy - 3, 0xFFD6DEEA);
				for (int i = -1; i <= 1; i++) {
					g.fill(cx + i * 6 - 1, cy + 6 + (i == 0 ? 2 : 0), cx + i * 6 + 1, cy + 8 + (i == 0 ? 2 : 0), 0xFFFFFFFF);
				}
			}
		}
	}

	private static void sun(DrawContext g, int cx, int cy) {
		int ray = 0xFFFFC93C;
		for (int i = 0; i < 8; i++) {
			double a = Math.PI / 4 * i;
			int rx = cx + (int) Math.round(Math.cos(a) * 9);
			int ry = cy + (int) Math.round(Math.sin(a) * 9);
			g.fill(rx - 1, ry - 1, rx + 1, ry + 1, ray);
		}
		Ui.disc(g, cx, cy, 6, 0xFFFFD84D);
	}

	private static void moon(DrawContext g, int cx, int cy) {
		for (int dy = -7; dy <= 7; dy++) {
			for (int dx = -7; dx <= 7; dx++) {
				boolean inA = dx * dx + dy * dy <= 49;
				int bx = dx - 4;
				int by = dy + 2;
				boolean inB = bx * bx + by * by <= 40;
				if (inA && !inB) {
					g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, 0xFFF3EFC8);
				}
			}
		}
	}

	private static void cloud(DrawContext g, int cx, int cy, int color) {
		Ui.disc(g, cx - 5, cy + 1, 4, color);
		Ui.disc(g, cx + 1, cy - 2, 5, color);
		Ui.disc(g, cx + 6, cy + 2, 4, color);
		g.fill(cx - 6, cy + 2, cx + 8, cy + 6, color);
	}
}
