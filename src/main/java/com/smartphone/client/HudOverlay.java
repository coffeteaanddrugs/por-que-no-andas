package com.smartphone.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

import java.util.Iterator;

/** Lo que se dibuja abajo a la derecha aunque el teléfono esté cerrado: llamadas y notificaciones. */
public final class HudOverlay {
	private HudOverlay() {
	}

	public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
		MinecraftClient mc = MinecraftClient.getInstance();
		CameraMode.render(ctx);
		if (mc.options.hudHidden || mc.player == null || mc.currentScreen instanceof PhoneScreen || !ClientPhoneItem.has(mc.player)) {
			return;
		}
		TextRenderer tr = mc.textRenderer;
		int w = ctx.getScaledWindowWidth();
		int h = ctx.getScaledWindowHeight();
		int cardW = 150;
		int x = w - cardW - 8;
		int y = h - 8;
		long now = System.currentTimeMillis();

		// --- tarjeta de llamada ---
		String state = ClientPhoneState.callState;
		if (!state.equals("idle")) {
			int ch = 46;
			y -= ch;
			String title;
			int accent;
			switch (state) {
				case "incoming" -> {
					title = "Llamada entrante";
					accent = Ui.GREEN;
				}
				case "outgoing" -> {
					title = "Llamando...";
					accent = Ui.BLUE;
				}
				default -> {
					long secs = (now - ClientPhoneState.callStart) / 1000;
					title = String.format("En llamada  %02d:%02d", secs / 60, secs % 60);
					accent = Ui.GREEN;
				}
			}
			Ui.roundRect(ctx, x, y, cardW, ch, 8, 0xEE10141D);
			Ui.roundRect(ctx, x + 6, y + 6, 20, 20, 6, Ui.avatarColor(ClientPhoneState.callPeer));
			String name = ClientPhoneState.name(ClientPhoneState.callPeer);
			ctx.drawCenteredTextWithShadow(tr, name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(), x + 16, y + 12, 0xFFFFFFFF);
			ctx.drawText(tr, title, x + 32, y + 7, accent, false);
			ctx.drawText(tr, tr.trimToWidth(name, cardW - 38), x + 32, y + 18, 0xFFFFFFFF, false);
			String hint;
			if (state.equals("incoming")) {
				hint = "[" + PhoneClient.answerKey.getBoundKeyLocalizedText().getString() + "] Contestar   ["
						+ PhoneClient.hangupKey.getBoundKeyLocalizedText().getString() + "] Rechazar";
			} else {
				hint = "[" + PhoneClient.hangupKey.getBoundKeyLocalizedText().getString() + "] Colgar";
			}
			ctx.drawText(tr, hint, x + 8, y + 34, Ui.MUTED, false);
			y -= 4;
		} else if (now < ClientPhoneState.endUntil) {
			int ch = 20;
			y -= ch;
			Ui.roundRect(ctx, x, y, cardW, ch, 6, 0xEE10141D);
			ctx.drawText(tr, tr.trimToWidth(ClientPhoneState.endText, cardW - 12), x + 6, y + 6, 0xFFFFFFFF, false);
			y -= 4;
		}

		// --- notificaciones ---
		Iterator<ClientPhoneState.Toast> it = ClientPhoneState.toasts.iterator();
		while (it.hasNext()) {
			if (it.next().until() < now) {
				it.remove();
			}
		}
		for (int i = ClientPhoneState.toasts.size() - 1; i >= 0; i--) {
			ClientPhoneState.Toast t = ClientPhoneState.toasts.get(i);
			int th = 28;
			y -= th;
			Ui.roundRect(ctx, x, y, cardW, th, 6, 0xEE10141D);
			ctx.fill(x + 3, y + 7, x + 5, y + th - 7, Ui.BLUE);
			ctx.drawText(tr, tr.trimToWidth(t.title(), cardW - 16), x + 10, y + 6, 0xFFFFFFFF, false);
			ctx.drawText(tr, tr.trimToWidth(t.text(), cardW - 16), x + 10, y + 16, Ui.MUTED, false);
			y -= 3;
		}
	}
}
