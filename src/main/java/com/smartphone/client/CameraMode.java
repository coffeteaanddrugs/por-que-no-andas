package com.smartphone.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Util;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Modo cámara: sales del teléfono, ves un visor en pantalla y con la tecla de foto
 * se guarda una captura (sin HUD) en la galería del teléfono.
 */
public final class CameraMode {
	public static boolean active;

	private static int stage;               // 0 = normal, 1/2 = esperando a que se dibuje un frame limpio
	private static boolean savedHudHidden;
	private static long flashStart;

	private CameraMode() {
	}

	public static void enter() {
		active = true;
		stage = 0;
	}

	public static void exit() {
		active = false;
		if (stage > 0) {
			MinecraftClient.getInstance().options.hudHidden = savedHudHidden;
		}
		stage = 0;
	}

	public static void shoot() {
		if (!active || stage != 0) {
			return;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		if (mc.currentScreen != null) {
			return;
		}
		savedHudHidden = mc.options.hudHidden;
		mc.options.hudHidden = true;
		stage = 1;
	}

	public static void tick(MinecraftClient mc) {
		if (!active) {
			return;
		}
		if (mc.player == null) {
			exit();
			return;
		}
		if (stage == 1) {
			stage = 2;
		} else if (stage == 2) {
			stage = 0;
			capture(mc);
			mc.options.hudHidden = savedHudHidden;
			flashStart = System.currentTimeMillis();
		}
	}

	private static void capture(MinecraftClient mc) {
		try {
			NativeImage image = ScreenshotRecorder.takeScreenshot(mc.getFramebuffer());
			String name = "IMG_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".png";
			Path file = GalleryStore.dir().resolve(name);
			Util.getIoWorkerExecutor().execute(() -> {
				try {
					image.writeTo(file);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					image.close();
				}
			});
			Sounds.shutter();
			ClientPhoneState.toast("Cámara", "Foto guardada en la Galería");
		} catch (Exception e) {
			ClientPhoneState.toast("Cámara", "No se pudo guardar la foto");
		}
	}

	public static void render(DrawContext ctx) {
		MinecraftClient mc = MinecraftClient.getInstance();
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();

		if (active && stage == 0) {
			int m = 14, len = 14, t = 2, c = 0xEEFFFFFF;
			// esquinas del visor
			ctx.fill(m, m, m + len, m + t, c);
			ctx.fill(m, m, m + t, m + len, c);
			ctx.fill(w - m - len, m, w - m, m + t, c);
			ctx.fill(w - m - t, m, w - m, m + len, c);
			ctx.fill(m, h - m - t, m + len, h - m, c);
			ctx.fill(m, h - m - len, m + t, h - m, c);
			ctx.fill(w - m - len, h - m - t, w - m, h - m, c);
			ctx.fill(w - m - t, h - m - len, w - m, h - m, c);
			// cruz central
			ctx.fill(w / 2 - 5, h / 2, w / 2 + 6, h / 2 + 1, 0x88FFFFFF);
			ctx.fill(w / 2, h / 2 - 5, w / 2 + 1, h / 2 + 6, 0x88FFFFFF);

			TextRenderer tr = mc.textRenderer;
			String hint = "[" + PhoneClient.photoKey.getBoundKeyLocalizedText().getString() + "] Foto    ["
					+ PhoneClient.openKey.getBoundKeyLocalizedText().getString() + "] Salir";
			int tw = tr.getWidth(hint);
			Ui.roundRect(ctx, w / 2 - tw / 2 - 6, h - 46, tw + 12, 14, 4, 0x99000000);
			ctx.drawText(tr, hint, w / 2 - tw / 2, h - 43, 0xFFFFFFFF, false);
		}

		long since = System.currentTimeMillis() - flashStart;
		if (since >= 0 && since < 350) {
			int alpha = (int) (200 * (1.0 - since / 350.0));
			ctx.fill(0, 0, w, h, (alpha << 24) | 0xFFFFFF);
		}
	}
}
