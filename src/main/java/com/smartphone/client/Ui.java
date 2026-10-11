package com.smartphone.client;

import net.minecraft.client.gui.DrawContext;

/** Ayudas para dibujar (rectángulos redondeados, degradados, colores). */
public final class Ui {
	public static final int BLUE = 0xFF0A66D9;
	public static final int GREEN = 0xFF2EAD5A;
	public static final int RED = 0xFFD64545;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int MUTED = 0xFF9AA4B5;
	public static final int PANEL = 0xFF1B2230;
	public static final int PANEL2 = 0xFF262E3F;
	public static final int BG = 0xFF0A0F1A;

	private static final int[] AVATARS = {
			0xFF2EAD5A, 0xFF0A66D9, 0xFFD6892E, 0xFF8E44AD, 0xFFD64545, 0xFF16A0A8, 0xFFB0A020, 0xFF5B6EE1
	};

	private Ui() {
	}

	public static int avatarColor(String s) {
		return AVATARS[Math.floorMod(s == null ? 0 : s.hashCode(), AVATARS.length)];
	}

	private static int inset(int r, int row) {
		double d = r - row - 0.5;
		return (int) Math.round(r - Math.sqrt(Math.max(0, r * r - d * d)));
	}

	public static void roundRect(DrawContext c, int x, int y, int w, int h, int r, int color) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int i = 0; i < r; i++) {
			int in = inset(r, i);
			c.fill(x + in, y + i, x + w - in, y + i + 1, color);
			c.fill(x + in, y + h - 1 - i, x + w - in, y + h - i, color);
		}
		c.fill(x, y + r, x + w, y + h - r, color);
	}

	public static int lerp(int a, int b, float t) {
		int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
		int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
		return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
	}

	public static void roundGradient(DrawContext c, int x, int y, int w, int h, int r, int top, int bottom) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		for (int i = 0; i < h; i++) {
			int in = 0;
			if (i < r) {
				in = inset(r, i);
			} else if (i >= h - r) {
				in = inset(r, h - 1 - i);
			}
			c.fill(x + in, y + i, x + w - in, y + i + 1, lerp(top, bottom, i / (float) Math.max(1, h - 1)));
		}
	}

	public static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** Círculo relleno. */
	public static void disc(DrawContext c, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.round(Math.sqrt(Math.max(0, r * r + r - dy * dy)));
			c.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}

	private static int span(int r, int dy) {
		return (int) Math.round(Math.sqrt(Math.max(0, r * r + r - dy * dy)));
	}

	/** Solo el borde de un círculo (grosor 1). */
	public static void ring(DrawContext c, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = span(r, dy);
			if (Math.abs(dy) == r || r < 2) {
				c.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
				continue;
			}
			int ix = Math.min(span(r - 1, dy), dx - 1);
			c.fill(cx - dx, cy + dy, cx - ix, cy + dy + 1, color);
			c.fill(cx + ix + 1, cy + dy, cx + dx + 1, cy + dy + 1, color);
		}
	}

	/** Color con otra transparencia (a = 0..255). */
	public static int alpha(int color, int a) {
		return (Math.max(0, Math.min(255, a)) << 24) | (color & 0xFFFFFF);
	}
}
