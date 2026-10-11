package com.smartphone.client;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.systems.RenderSystem;
import com.smartphone.common.CallLogEntry;
import com.smartphone.common.Contact;
import com.smartphone.common.Message;
import com.smartphone.common.Numbers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * La pantalla del teléfono: aparece abajo a la derecha (como en FiveM) y tiene varias apps.
 * Está dibujada "a mano": cada frame se pintan los botones y se guardan sus zonas de clic.
 */
public class PhoneScreen extends Screen {
	private enum App {
		HOME, MESSAGES, CHAT, NEW_CHAT, CONTACTS, CONTACT_ADD, CONTACT_VIEW, CALLS, CALL, SETTINGS, GALLERY, GALLERY_VIEW, CASH, CASH_SEND
	}

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private record Loaded(Identifier id, int w, int h) {
	}

	private static final int[][] WALLPAPERS = {
			{0xFF0B1B3A, 0xFF1B4A7A},
			{0xFF2A0B3A, 0xFF7A1B5A},
			{0xFF0B3A2A, 0xFF1B7A5A},
			{0xFF1A1A1F, 0xFF3A3A48}
	};

	private static final Map<String, Identifier> ICONS = new HashMap<>();

	private static Identifier icon(String name) {
		return ICONS.computeIfAbsent(name, n -> Identifier.of("smartphone", "textures/gui/app_" + n + ".png"));
	}

	private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("d/M HH:mm");

	// estado de la interfaz
	private final List<Hit> hits = new ArrayList<>();
	private final Deque<App> stack = new ArrayDeque<>();
	private App app = App.HOME;
	private String lastCallState = "idle";
	private int clipTop = Integer.MIN_VALUE;
	private int clipBottom = Integer.MAX_VALUE;
	private int scroll;
	private int maxScroll;

	// geometría del teléfono
	private int px, py, pw, ph;   // marco exterior
	private int sx, sy, sw, sh;   // pantalla interior
	private int bottom;           // límite inferior del contenido

	// campos de texto
	private final TextFieldWidget input;
	private final TextFieldWidget nameField;
	private final TextFieldWidget numberField;
	private final TextFieldWidget amountField;
	private boolean pickWallpaper;

	// contexto de las apps
	private String chatPeer = "";
	private String contactPeer = "";
	private String dial = "";
	private boolean recentsTab;
	private List<Path> gallery = new ArrayList<>();
	private int galleryIndex;

	private int nextTex;
	private final LinkedHashMap<Path, Loaded> textures = new LinkedHashMap<>(16, 0.75f, true);

	public PhoneScreen() {
		super(Text.literal("Teléfono"));
		var tr = MinecraftClient.getInstance().textRenderer;
		input = new TextFieldWidget(tr, 0, 0, 100, 12, Text.literal("Mensaje"));
		input.setMaxLength(256);
		input.setDrawsBackground(false);
		input.setPlaceholder(Text.literal("Mensaje"));
		nameField = new TextFieldWidget(tr, 0, 0, 100, 12, Text.literal("Nombre"));
		nameField.setMaxLength(24);
		nameField.setDrawsBackground(false);
		nameField.setPlaceholder(Text.literal("Nombre"));
		numberField = new TextFieldWidget(tr, 0, 0, 100, 12, Text.literal("Número"));
		numberField.setMaxLength(8);
		numberField.setDrawsBackground(false);
		numberField.setPlaceholder(Text.literal("555-0000"));
		numberField.setTextPredicate(s -> s.matches("[0-9-]*"));
		amountField = new TextFieldWidget(tr, 0, 0, 100, 12, Text.literal("Monto"));
		amountField.setMaxLength(12);
		amountField.setDrawsBackground(false);
		amountField.setPlaceholder(Text.literal("Monto"));
		amountField.setTextPredicate(s -> s.matches("[0-9]*"));
	}

	@Override
	protected void init() {
		ph = Math.min(height - 12, 340);
		pw = ph * 9 / 19;
		px = width - pw - 8;
		py = height - ph - 8;
		sx = px + 4;
		sy = py + 4;
		sw = pw - 8;
		sh = ph - 8;
		bottom = sy + sh - 12;
		addSelectableChild(input);
		addSelectableChild(nameField);
		addSelectableChild(numberField);
		addSelectableChild(amountField);
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	@Override
	public void removed() {
		for (Loaded l : textures.values()) {
			if (l.id() != null) {
				MinecraftClient.getInstance().getTextureManager().destroyTexture(l.id());
			}
		}
		textures.clear();
	}

	// ------------------------------------------------------------------ navegación

	private void navigate(App next) {
		stack.push(app);
		app = next;
		scroll = 0;
	}

	private void back() {
		app = stack.isEmpty() ? App.HOME : stack.pop();
		pickWallpaper = false;
		scroll = 0;
	}

	private void goHome() {
		stack.clear();
		app = App.HOME;
		pickWallpaper = false;
		scroll = 0;
	}

	private void openChat(String peer, boolean replaceCurrent) {
		chatPeer = peer;
		input.setText("");
		if (replaceCurrent) {
			app = App.CHAT;
			scroll = 0;
		} else {
			navigate(App.CHAT);
		}
		focus(input);
	}

	private void focus(TextFieldWidget f) {
		setFocused(f);
	}

	private void startCall(String number) {
		if (!ClientPhoneState.callState.equals("idle")) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("number", number);
		ClientPhoneState.send("call_start", o);
	}

	private void sendChat() {
		String text = input.getText().trim();
		if (text.isEmpty() || chatPeer.isEmpty()) {
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("to", chatPeer);
		o.addProperty("text", text);
		ClientPhoneState.send("send", o);
		input.setText("");
		scroll = 0;
		focus(input);
	}

	private boolean anyFieldFocused() {
		return (input.visible && input.isFocused()) || (nameField.visible && nameField.isFocused())
				|| (numberField.visible && numberField.isFocused()) || (amountField.visible && amountField.isFocused());
	}

	// ------------------------------------------------------------------ eventos

	@Override
	public void tick() {
		if (app == App.CHAT && !chatPeer.isEmpty()) {
			ClientPhoneState.markRead(chatPeer);
		}
		String cs = ClientPhoneState.callState;
		if (!cs.equals(lastCallState)) {
			if (!cs.equals("idle")) {
				stack.clear();
				app = App.CALL;
				scroll = 0;
			} else if (app == App.CALL) {
				goHome();
			}
			lastCallState = cs;
		}
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (button == 0) {
			for (int i = hits.size() - 1; i >= 0; i--) {
				Hit h = hits.get(i);
				if (Ui.in(mx, my, h.x(), h.y(), h.w(), h.h())) {
					Sounds.click();
					setFocused(null);
					h.action().run();
					return true;
				}
			}
		}
		return super.mouseClicked(mx, my, button);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
		int delta = (int) Math.round(vertical * 14);
		if (app == App.CHAT) {
			scroll += delta;
		} else {
			scroll -= delta;
		}
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		return true;
	}

	@Override
	public boolean keyPressed(int key, int scan, int mods) {
		if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
			if (app == App.CHAT && input.isFocused()) {
				sendChat();
				return true;
			}
			if (app == App.NEW_CHAT && numberField.isFocused()) {
				startNewChat();
				return true;
			}
			if (app == App.CASH_SEND && (amountField.isFocused() || numberField.isFocused())) {
				sendMoney();
				return true;
			}
		}
		if (app == App.CALLS && !recentsTab && key == GLFW.GLFW_KEY_BACKSPACE && !dial.isEmpty()) {
			dial = dial.substring(0, dial.length() - 1);
			return true;
		}
		if (app == App.GALLERY_VIEW && !gallery.isEmpty()) {
			if (key == GLFW.GLFW_KEY_RIGHT) {
				galleryIndex = Math.min(gallery.size() - 1, galleryIndex + 1);
				return true;
			}
			if (key == GLFW.GLFW_KEY_LEFT) {
				galleryIndex = Math.max(0, galleryIndex - 1);
				return true;
			}
		}
		if (!anyFieldFocused() && PhoneClient.openKey.matchesKey(key, scan)) {
			close();
			return true;
		}
		return super.keyPressed(key, scan, mods);
	}

	@Override
	public boolean charTyped(char chr, int mods) {
		if (app == App.CALLS && !recentsTab && Character.isDigit(chr)) {
			if (dial.length() < 7) {
				dial += chr;
			}
			return true;
		}
		return super.charTyped(chr, mods);
	}

	private void startNewChat() {
		String n = Numbers.normalize(numberField.getText());
		if (n == null) {
			ClientPhoneState.toast("Teléfono", "El número debe tener 7 dígitos");
			return;
		}
		if (n.equals(ClientPhoneState.myNumber)) {
			ClientPhoneState.toast("Teléfono", "Ese es tu propio número");
			return;
		}
		openChat(n, true);
	}

	// ------------------------------------------------------------------ utilidades de dibujo

	private void hit(int x, int y, int w, int h, Runnable action) {
		int y1 = Math.max(y, clipTop);
		int y2 = Math.min(y + h, clipBottom);
		if (y2 <= y1) {
			return;
		}
		hits.add(new Hit(x, y1, w, y2 - y1, action));
	}

	private void button(DrawContext ctx, int x, int y, int w, int h, String label, int color, int mx, int my, Runnable action) {
		boolean hover = Ui.in(mx, my, x, y, w, h);
		Ui.roundRect(ctx, x, y, w, h, Math.min(6, h / 2), hover ? Ui.lerp(color, 0xFFFFFFFF, 0.18f) : color);
		ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2, Ui.WHITE);
		hit(x, y, w, h, action);
	}

	private void field(DrawContext ctx, TextFieldWidget f, int x, int y, int w, int h, int mx, int my, float delta) {
		Ui.roundRect(ctx, x, y, w, h, 5, Ui.PANEL2);
		f.visible = true;
		f.setPosition(x + 5, y + (h - 8) / 2);
		f.setWidth(w - 10);
		f.render(ctx, mx, my, delta);
		hit(x, y, w, h, () -> focus(f));
	}

	private void avatar(DrawContext ctx, int x, int y, int size, String name, String key) {
		Ui.roundRect(ctx, x, y, size, size, size / 4, Ui.avatarColor(key));
		String initial = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
		ctx.drawCenteredTextWithShadow(textRenderer, initial, x + size / 2, y + (size - 8) / 2, Ui.WHITE);
	}

	private void drawIcon(DrawContext ctx, String name, int cx, int y, int size) {
		RenderSystem.enableBlend();
		ctx.drawTexture(icon(name), cx - size / 2, y, size, size, 0f, 0f, 64, 64, 64, 64);
	}

	private String fmtTime(long ms) {
		Instant i = Instant.ofEpochMilli(ms);
		LocalDate d = i.atZone(ZoneId.systemDefault()).toLocalDate();
		DateTimeFormatter f = d.equals(LocalDate.now()) ? HOUR : DAY;
		return f.withZone(ZoneId.systemDefault()).format(i);
	}

	/** Cabecera de una app. Devuelve la Y donde empieza el contenido. */
	private int header(DrawContext ctx, String title, boolean showBack, String rightLabel, Runnable rightAction, int mx, int my) {
		int hy = sy + 14;
		ctx.fill(sx, hy, sx + sw, hy + 22, Ui.PANEL);
		int tx = sx + 8;
		if (showBack) {
			ctx.drawText(textRenderer, "<", sx + 8, hy + 7, Ui.BLUE, false);
			hit(sx + 2, hy, 22, 22, this::back);
			tx = sx + 20;
		}
		ctx.drawText(textRenderer, textRenderer.trimToWidth(title, sw - 40), tx, hy + 7, Ui.WHITE, false);
		if (rightLabel != null) {
			button(ctx, sx + sw - 24, hy + 3, 18, 16, rightLabel, Ui.BLUE, mx, my, rightAction);
		}
		return hy + 22;
	}

	private void beginScroll(DrawContext ctx, int top, int bot) {
		clipTop = top;
		clipBottom = bot;
		ctx.enableScissor(sx, top, sx + sw, bot);
	}

	private void endScroll(DrawContext ctx) {
		ctx.disableScissor();
		clipTop = Integer.MIN_VALUE;
		clipBottom = Integer.MAX_VALUE;
	}

	// ------------------------------------------------------------------ render principal

	@Override
	public void render(DrawContext ctx, int mx, int my, float delta) {
		hits.clear();
		input.visible = false;
		nameField.visible = false;
		numberField.visible = false;
		amountField.visible = false;

		// marco del teléfono
		Ui.roundRect(ctx, px - 1, py - 1, pw + 2, ph + 2, 13, 0xFF3A4252);
		Ui.roundRect(ctx, px, py, pw, ph, 12, 0xFF05070C);
		if (app == App.HOME) {
			int[] wp = WALLPAPERS[Math.floorMod(ClientSettings.get().wallpaper, WALLPAPERS.length)];
			Ui.roundGradient(ctx, sx, sy, sw, sh, 9, wp[0], wp[1]);
			String photo = ClientSettings.get().wallpaperPhoto;
			if (photo != null && !photo.isEmpty()) {
				Path pp = GalleryStore.dir().resolve(photo);
				if (Files.exists(pp)) {
					Loaded l = texture(pp);
					if (l.id() != null && l.w() > 0) {
						drawImageCover(ctx, l, sx + 4, sy + 4, sw - 8, sh - 8);
						ctx.fill(sx + 4, sy + 4, sx + sw - 4, sy + sh - 4, 0x44000000);
					}
				}
			}
		} else {
			Ui.roundRect(ctx, sx, sy, sw, sh, 9, Ui.BG);
		}

		switch (app) {
			case HOME -> drawHome(ctx, mx, my);
			case MESSAGES -> drawMessages(ctx, mx, my);
			case CHAT -> drawChat(ctx, mx, my, delta);
			case NEW_CHAT -> drawNewChat(ctx, mx, my, delta);
			case CONTACTS -> drawContacts(ctx, mx, my);
			case CONTACT_ADD -> drawContactAdd(ctx, mx, my, delta);
			case CONTACT_VIEW -> drawContactView(ctx, mx, my);
			case CALLS -> drawCalls(ctx, mx, my);
			case CALL -> drawCall(ctx, mx, my);
			case SETTINGS -> drawSettings(ctx, mx, my);
			case GALLERY -> drawGallery(ctx, mx, my);
			case GALLERY_VIEW -> drawGalleryView(ctx, mx, my);
			case CASH -> drawCash(ctx, mx, my);
			case CASH_SEND -> drawCashSend(ctx, mx, my, delta);
		}

		drawStatusBar(ctx);

		// barra inferior: volver al inicio
		int hbW = 40;
		int hbX = sx + (sw - hbW) / 2;
		int hbY = sy + sh - 7;
		ctx.fill(hbX, hbY, hbX + hbW, hbY + 2, 0xCCFFFFFF);
		hit(hbX - 10, hbY - 6, hbW + 20, 12, this::goHome);
	}

	private void drawStatusBar(DrawContext ctx) {
		ctx.drawText(textRenderer, LocalTime.now().format(HOUR), sx + 9, sy + 3, Ui.WHITE, false);
		ctx.drawText(textRenderer, "5G", sx + sw - 38, sy + 3, Ui.WHITE, false);
		// batería
		ctx.fill(sx + sw - 20, sy + 3, sx + sw - 8, sy + 10, Ui.WHITE);
		ctx.fill(sx + sw - 19, sy + 4, sx + sw - 9, sy + 9, 0xFF2EAD5A);
		ctx.fill(sx + sw - 8, sy + 5, sx + sw - 7, sy + 8, Ui.WHITE);

		String cs = ClientPhoneState.callState;
		if (cs.equals("idle")) {
			Ui.roundRect(ctx, sx + sw / 2 - 17, sy + 3, 34, 8, 4, 0xFF000000);
		} else {
			long secs = (System.currentTimeMillis() - ClientPhoneState.callStart) / 1000;
			String label = cs.equals("active") ? String.format("%02d:%02d", secs / 60, secs % 60) : "Llamada";
			int w = textRenderer.getWidth(label) + 10;
			Ui.roundRect(ctx, sx + sw / 2 - w / 2, sy + 2, w, 10, 5, cs.equals("outgoing") ? Ui.BLUE : Ui.GREEN);
			ctx.drawText(textRenderer, label, sx + sw / 2 - w / 2 + 5, sy + 3, Ui.WHITE, false);
			hit(sx + sw / 2 - w / 2, sy + 2, w, 10, () -> {
				stack.clear();
				app = App.CALL;
			});
		}
	}

	// ------------------------------------------------------------------ INICIO

	private void drawHome(DrawContext ctx, int mx, int my) {
		int wx = sx + 8;
		int wy = sy + 18;
		int ww = sw - 16;
		int wh = 58;
		Ui.roundRect(ctx, wx, wy, ww, wh, 8, 0x66000000);

		// arriba: día y mes
		String date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", Locale.forLanguageTag("es")));
		date = date.isEmpty() ? date : date.substring(0, 1).toUpperCase() + date.substring(1);
		ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(date, ww - 8), wx + ww / 2, wy + 6, 0xFFDDE6F5);

		// temperatura y clima
		Weather.Info wi = Weather.now();
		var m = ctx.getMatrices();
		m.push();
		m.translate(wx + 12, wy + 21, 0);
		m.scale(2.4f, 2.4f, 1f);
		ctx.drawTextWithShadow(textRenderer, wi.celsius() + "\u00B0", 0, 0, Ui.WHITE);
		m.pop();
		Weather.icon(ctx, wi.kind(), wx + ww - 30, wy + 31);
		ctx.drawText(textRenderer, textRenderer.trimToWidth(wi.label(), ww - 20), wx + 12, wy + 45, 0xFFDDE6F5, false);

		// apps (3 por fila)
		int colW = sw / 3;
		int rowY = wy + wh + 12;
		String[][] apps = {{"gallery", "Galería"}, {"camera", "Cámara"}, {"settings", "Ajustes"}, {"mercadopago", "Mercado Pago"}};
		for (int i = 0; i < apps.length; i++) {
			int cx = sx + colW * (i % 3) + colW / 2;
			int ry = rowY + (i / 3) * 54;
			String id = apps[i][0];
			boolean hover = Ui.in(mx, my, cx - 22, ry - 3, 44, 50);
			if (hover) {
				Ui.roundRect(ctx, cx - 22, ry - 3, 44, 50, 8, 0x33FFFFFF);
			}
			drawIcon(ctx, id, cx, ry, 28);
			String label = apps[i][1];
			int sp = label.indexOf(' ');
			if (textRenderer.getWidth(label) > colW - 4 && sp > 0) {
				ctx.drawCenteredTextWithShadow(textRenderer, label.substring(0, sp), cx, ry + 31, Ui.WHITE);
				ctx.drawCenteredTextWithShadow(textRenderer, label.substring(sp + 1), cx, ry + 40, Ui.WHITE);
			} else {
				ctx.drawCenteredTextWithShadow(textRenderer, label, cx, ry + 31, Ui.WHITE);
			}
			hit(cx - 22, ry - 3, 44, 50, () -> openApp(id));
		}

		// dock
		int dy = bottom - 44;
		Ui.roundRect(ctx, sx + 8, dy, sw - 16, 40, 11, 0x66000000);
		String[][] dock = {{"phone", "calls"}, {"messages", "messages"}, {"contacts", "contacts"}};
		int dw = (sw - 16) / 3;
		for (int i = 0; i < dock.length; i++) {
			int cx = sx + 8 + dw * i + dw / 2;
			String id = dock[i][1];
			if (Ui.in(mx, my, cx - 16, dy + 4, 32, 32)) {
				Ui.roundRect(ctx, cx - 16, dy + 4, 32, 32, 8, 0x33FFFFFF);
			}
			drawIcon(ctx, dock[i][0], cx, dy + 7, 26);
			hit(cx - 16, dy + 4, 32, 32, () -> openApp(id));
			if (id.equals("messages")) {
				int unread = ClientPhoneState.unreadTotal();
				if (unread > 0) {
					String t = unread > 9 ? "9+" : String.valueOf(unread);
					Ui.roundRect(ctx, cx + 5, dy + 3, 12, 11, 5, Ui.RED);
					ctx.drawCenteredTextWithShadow(textRenderer, t, cx + 11, dy + 4, Ui.WHITE);
				}
			}
		}
	}

	private void openApp(String id) {
		switch (id) {
			case "gallery" -> {
				gallery = GalleryStore.list();
				navigate(App.GALLERY);
			}
			case "camera" -> {
				CameraMode.enter();
				close();
			}
			case "settings" -> navigate(App.SETTINGS);
			case "mercadopago" -> {
				ClientPhoneState.send("cash_open");
				navigate(App.CASH);
			}
			case "calls" -> {
				recentsTab = false;
				navigate(App.CALLS);
			}
			case "messages" -> navigate(App.MESSAGES);
			case "contacts" -> navigate(App.CONTACTS);
			default -> {
			}
		}
	}

	// ------------------------------------------------------------------ MENSAJES

	private List<String> peers() {
		List<String> peers = new ArrayList<>();
		for (Map.Entry<String, List<Message>> e : ClientPhoneState.convs.entrySet()) {
			if (!e.getValue().isEmpty()) {
				peers.add(e.getKey());
			}
		}
		peers.sort((a, b) -> {
			List<Message> la = ClientPhoneState.convs.get(a);
			List<Message> lb = ClientPhoneState.convs.get(b);
			return Long.compare(lb.get(lb.size() - 1).id, la.get(la.size() - 1).id);
		});
		return peers;
	}

	private void drawMessages(DrawContext ctx, int mx, int my) {
		int top = header(ctx, "Mensajes", true, "+", () -> {
			numberField.setText("");
			navigate(App.NEW_CHAT);
			focus(numberField);
		}, mx, my);
		List<String> peers = peers();
		if (peers.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "Sin conversaciones", sx + sw / 2, top + 30, Ui.MUTED);
			ctx.drawCenteredTextWithShadow(textRenderer, "Pulsa + para escribir", sx + sw / 2, top + 42, Ui.MUTED);
			maxScroll = 0;
			return;
		}
		int rowH = 28;
		int y = top - scroll;
		beginScroll(ctx, top, bottom);
		for (String peer : peers) {
			List<Message> list = ClientPhoneState.convs.get(peer);
			Message last = list.get(list.size() - 1);
			String name = ClientPhoneState.name(peer);
			int unread = ClientPhoneState.unread(peer);
			if (Ui.in(mx, my, sx, y, sw, rowH) && my >= top && my < bottom) {
				ctx.fill(sx, y, sx + sw, y + rowH, 0x22FFFFFF);
			}
			avatar(ctx, sx + 6, y + 4, 20, name, peer);
			String time = fmtTime(last.time);
			int tw = textRenderer.getWidth(time);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(name, sw - 40 - tw), sx + 32, y + 5, Ui.WHITE, false);
			ctx.drawText(textRenderer, time, sx + sw - 6 - tw, y + 5, Ui.MUTED, false);
			String preview = (last.from.equals(ClientPhoneState.myNumber) ? "Tú: " : "") + last.text;
			ctx.drawText(textRenderer, textRenderer.trimToWidth(preview, sw - 32 - 26), sx + 32, y + 16, unread > 0 ? 0xFFDDE6F5 : Ui.MUTED, false);
			if (unread > 0) {
				String t = unread > 9 ? "9+" : String.valueOf(unread);
				Ui.roundRect(ctx, sx + sw - 18, y + 15, 12, 11, 5, Ui.BLUE);
				ctx.drawCenteredTextWithShadow(textRenderer, t, sx + sw - 12, y + 16, Ui.WHITE);
			}
			hit(sx, y, sw, rowH, () -> openChat(peer, false));
			y += rowH;
		}
		endScroll(ctx);
		maxScroll = Math.max(0, peers.size() * rowH - (bottom - top));
	}

	private void drawNewChat(DrawContext ctx, int mx, int my, float delta) {
		int top = header(ctx, "Nuevo mensaje", true, null, null, mx, my);
		ctx.drawText(textRenderer, "Número de teléfono", sx + 8, top + 8, Ui.MUTED, false);
		field(ctx, numberField, sx + 6, top + 20, sw - 12, 20, mx, my, delta);
		button(ctx, sx + 6, top + 44, sw - 12, 18, "Escribir", Ui.BLUE, mx, my, this::startNewChat);

		ctx.drawText(textRenderer, "Contactos", sx + 8, top + 72, Ui.MUTED, false);
		int listTop = top + 84;
		beginScroll(ctx, listTop, bottom);
		int y = listTop - scroll;
		for (Contact c : ClientPhoneState.contacts) {
			if (Ui.in(mx, my, sx, y, sw, 24) && my >= listTop && my < bottom) {
				ctx.fill(sx, y, sx + sw, y + 24, 0x22FFFFFF);
			}
			avatar(ctx, sx + 6, y + 3, 18, c.name, c.number);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(c.name, sw - 40), sx + 30, y + 4, Ui.WHITE, false);
			ctx.drawText(textRenderer, c.number, sx + 30, y + 14, Ui.MUTED, false);
			String number = c.number;
			hit(sx, y, sw, 24, () -> openChat(number, true));
			y += 24;
		}
		endScroll(ctx);
		maxScroll = Math.max(0, ClientPhoneState.contacts.size() * 24 - (bottom - listTop));
	}

	private static String statusLabel(int status) {
		return switch (status) {
			case Message.READ -> "Leído";
			case Message.DELIVERED -> "Entregado";
			default -> "Enviado";
		};
	}

	private void drawChat(DrawContext ctx, int mx, int my, float delta) {
		String myNumber = ClientPhoneState.myNumber;
		String name = ClientPhoneState.name(chatPeer);
		boolean known = ClientPhoneState.isContact(chatPeer);

		// cabecera
		int hy = sy + 14;
		ctx.fill(sx, hy, sx + sw, hy + 30, Ui.PANEL);
		ctx.drawText(textRenderer, "<", sx + 8, hy + 11, Ui.BLUE, false);
		hit(sx + 2, hy, 20, 30, this::back);
		avatar(ctx, sx + 20, hy + 5, 20, name, chatPeer);
		ctx.drawText(textRenderer, textRenderer.trimToWidth(name, sw - 100), sx + 45, hy + 6, Ui.WHITE, false);
		if (known) {
			ctx.drawText(textRenderer, chatPeer, sx + 45, hy + 17, Ui.MUTED, false);
		} else {
			ctx.drawText(textRenderer, "+ Guardar", sx + 45, hy + 17, Ui.BLUE, false);
			hit(sx + 45, hy + 15, 50, 12, () -> {
				numberField.setText(chatPeer);
				nameField.setText("");
				navigate(App.CONTACT_ADD);
				focus(nameField);
			});
		}
		button(ctx, sx + sw - 42, hy + 7, 36, 16, "Llamar", Ui.GREEN, mx, my, () -> startCall(chatPeer));

		// barra de escritura
		int ibH = 20;
		int iby = bottom - ibH - 4;
		field(ctx, input, sx + 6, iby, sw - 12 - 24, ibH, mx, my, delta);
		button(ctx, sx + sw - 6 - 20, iby, 20, ibH, ">", Ui.BLUE, mx, my, this::sendChat);

		// burbujas (de abajo hacia arriba)
		int areaTop = hy + 32;
		int areaBot = iby - 4;
		List<Message> list = ClientPhoneState.convs.getOrDefault(chatPeer, List.of());
		if (list.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "Escribe tu primer mensaje", sx + sw / 2, areaTop + 30, Ui.MUTED);
			maxScroll = 0;
			return;
		}
		int maxBubble = (int) ((sw - 12) * 0.78);
		int y = areaBot - 2 + scroll;
		int total = 0;
		ctx.enableScissor(sx, areaTop, sx + sw, areaBot);
		for (int i = list.size() - 1; i >= 0; i--) {
			Message msg = list.get(i);
			boolean mine = msg.from.equals(myNumber);
			List<OrderedText> lines = textRenderer.wrapLines(Text.literal(msg.text), maxBubble - 10);
			String foot = fmtTime(msg.time) + (mine ? " · " + statusLabel(msg.status) : "");
			int textW = 0;
			for (OrderedText line : lines) {
				textW = Math.max(textW, textRenderer.getWidth(line));
			}
			int bw = Math.max(textW, textRenderer.getWidth(foot)) + 10;
			int bh = lines.size() * 9 + 9 + 8;
			int by = y - bh;
			total += bh + 4;
			if (by + bh >= areaTop && by <= areaBot) {
				int bx = mine ? sx + sw - 6 - bw : sx + 6;
				Ui.roundRect(ctx, bx, by, bw, bh, 6, mine ? Ui.BLUE : Ui.PANEL2);
				for (int l = 0; l < lines.size(); l++) {
					ctx.drawText(textRenderer, lines.get(l), bx + 5, by + 4 + l * 9, Ui.WHITE, false);
				}
				int footColor = mine ? (msg.status == Message.READ ? 0xFFA6F0B5 : 0xFFB8D4FF) : Ui.MUTED;
				ctx.drawText(textRenderer, foot, bx + 5, by + 5 + lines.size() * 9, footColor, false);
			}
			y = by - 4;
		}
		ctx.disableScissor();
		maxScroll = Math.max(0, total - (areaBot - areaTop) + 4);
	}

	// ------------------------------------------------------------------ CONTACTOS

	private void drawContacts(DrawContext ctx, int mx, int my) {
		int top = header(ctx, "Contactos", true, "+", () -> {
			nameField.setText("");
			numberField.setText("");
			navigate(App.CONTACT_ADD);
			focus(nameField);
		}, mx, my);

		// mi número
		Ui.roundRect(ctx, sx + 6, top + 6, sw - 12, 28, 6, Ui.PANEL);
		ctx.drawText(textRenderer, "Mi número", sx + 12, top + 11, Ui.MUTED, false);
		ctx.drawText(textRenderer, ClientPhoneState.myNumber, sx + 12, top + 22, Ui.WHITE, false);
		button(ctx, sx + sw - 50, top + 12, 38, 16, "Copiar", Ui.BLUE, mx, my, () -> {
			MinecraftClient.getInstance().keyboard.setClipboard(ClientPhoneState.myNumber);
			ClientPhoneState.toast("Teléfono", "Número copiado");
		});

		int listTop = top + 40;
		List<Contact> contacts = ClientPhoneState.contacts;
		if (contacts.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "No tienes contactos", sx + sw / 2, listTop + 20, Ui.MUTED);
			maxScroll = 0;
			return;
		}
		beginScroll(ctx, listTop, bottom);
		int y = listTop - scroll;
		for (Contact c : contacts) {
			if (Ui.in(mx, my, sx, y, sw, 26) && my >= listTop && my < bottom) {
				ctx.fill(sx, y, sx + sw, y + 26, 0x22FFFFFF);
			}
			avatar(ctx, sx + 6, y + 3, 20, c.name, c.number);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(c.name, sw - 40), sx + 32, y + 5, Ui.WHITE, false);
			ctx.drawText(textRenderer, c.number, sx + 32, y + 15, Ui.MUTED, false);
			String number = c.number;
			hit(sx, y, sw, 26, () -> {
				contactPeer = number;
				navigate(App.CONTACT_VIEW);
			});
			y += 26;
		}
		endScroll(ctx);
		maxScroll = Math.max(0, contacts.size() * 26 - (bottom - listTop));
	}

	private void drawContactAdd(DrawContext ctx, int mx, int my, float delta) {
		int top = header(ctx, "Nuevo contacto", true, null, null, mx, my);
		ctx.drawText(textRenderer, "Nombre", sx + 8, top + 8, Ui.MUTED, false);
		field(ctx, nameField, sx + 6, top + 19, sw - 12, 20, mx, my, delta);
		ctx.drawText(textRenderer, "Número", sx + 8, top + 46, Ui.MUTED, false);
		field(ctx, numberField, sx + 6, top + 57, sw - 12, 20, mx, my, delta);
		button(ctx, sx + 6, top + 86, sw - 12, 18, "Guardar", Ui.GREEN, mx, my, () -> {
			String number = Numbers.normalize(numberField.getText());
			if (nameField.getText().trim().isEmpty()) {
				ClientPhoneState.toast("Teléfono", "Escribe un nombre");
				return;
			}
			if (number == null) {
				ClientPhoneState.toast("Teléfono", "El número debe tener 7 dígitos");
				return;
			}
			JsonObject o = new JsonObject();
			o.addProperty("name", nameField.getText().trim());
			o.addProperty("number", number);
			ClientPhoneState.send("add_contact", o);
			back();
		});
	}

	private void drawContactView(DrawContext ctx, int mx, int my) {
		int top = header(ctx, "Contacto", true, null, null, mx, my);
		String name = ClientPhoneState.name(contactPeer);
		avatar(ctx, sx + sw / 2 - 20, top + 12, 40, name, contactPeer);
		ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(name, sw - 16), sx + sw / 2, top + 58, Ui.WHITE);
		ctx.drawCenteredTextWithShadow(textRenderer, contactPeer, sx + sw / 2, top + 70, Ui.MUTED);
		button(ctx, sx + 10, top + 90, sw - 20, 20, "Mensaje", Ui.BLUE, mx, my, () -> openChat(contactPeer, false));
		button(ctx, sx + 10, top + 116, sw - 20, 20, "Llamar", Ui.GREEN, mx, my, () -> startCall(contactPeer));
		button(ctx, sx + 10, top + 142, sw - 20, 20, "Eliminar", Ui.RED, mx, my, () -> {
			JsonObject o = new JsonObject();
			o.addProperty("number", contactPeer);
			ClientPhoneState.send("remove_contact", o);
			back();
		});
	}

	// ------------------------------------------------------------------ LLAMADAS

	private String formattedDial() {
		if (dial.length() <= 3) {
			return dial;
		}
		return dial.substring(0, 3) + "-" + dial.substring(3);
	}

	private void drawCalls(DrawContext ctx, int mx, int my) {
		int top = header(ctx, "Teléfono", true, null, null, mx, my);
		int tabW = (sw - 16) / 2;
		button(ctx, sx + 6, top + 4, tabW, 16, "Teclado", recentsTab ? Ui.PANEL2 : Ui.BLUE, mx, my, () -> recentsTab = false);
		button(ctx, sx + 10 + tabW, top + 4, tabW, 16, "Recientes", recentsTab ? Ui.BLUE : Ui.PANEL2, mx, my, () -> {
			recentsTab = true;
			scroll = 0;
		});
		int y0 = top + 26;

		if (!recentsTab) {
			Ui.roundRect(ctx, sx + 6, y0, sw - 12, 22, 6, Ui.PANEL);
			String shown = dial.isEmpty() ? "Marca un número" : formattedDial();
			ctx.drawCenteredTextWithShadow(textRenderer, shown, sx + sw / 2, y0 + 7, dial.isEmpty() ? Ui.MUTED : Ui.WHITE);

			int gap = 4;
			int kw = (sw - 12 - gap * 2) / 3;
			int kh = 24;
			int ky = y0 + 28;
			for (int i = 0; i < 12; i++) {
				int col = i % 3;
				int row = i / 3;
				int kx = sx + 6 + col * (kw + gap);
				int kyy = ky + row * (kh + gap);
				if (i < 9 || i == 10) {
					String digit = String.valueOf(i == 10 ? 0 : i + 1);
					button(ctx, kx, kyy, kw, kh, digit, Ui.PANEL2, mx, my, () -> {
						if (dial.length() < 7) {
							dial += digit;
						}
					});
				} else if (i == 9) {
					button(ctx, kx, kyy, kw, kh, "<", Ui.PANEL, mx, my, () -> {
						if (!dial.isEmpty()) {
							dial = dial.substring(0, dial.length() - 1);
						}
					});
				} else {
					button(ctx, kx, kyy, kw, kh, "Llamar", Ui.GREEN, mx, my, () -> {
						if (dial.length() == 7) {
							startCall(Numbers.normalize(dial));
						} else {
							ClientPhoneState.toast("Teléfono", "El número debe tener 7 dígitos");
						}
					});
				}
			}
			maxScroll = 0;
			return;
		}

		// recientes
		List<CallLogEntry> log = ClientPhoneState.callLog;
		if (log.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "Sin llamadas recientes", sx + sw / 2, y0 + 20, Ui.MUTED);
			maxScroll = 0;
			return;
		}
		beginScroll(ctx, y0, bottom);
		int y = y0 - scroll;
		for (CallLogEntry e : log) {
			if (Ui.in(mx, my, sx, y, sw, 26) && my >= y0 && my < bottom) {
				ctx.fill(sx, y, sx + sw, y + 26, 0x22FFFFFF);
			}
			boolean missedIn = e.dir.equals("in") && !e.result.equals("answered");
			String name = ClientPhoneState.name(e.number);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(name, sw - 16), sx + 8, y + 4, missedIn ? 0xFFFF7B7B : Ui.WHITE, false);
			String suffix = switch (e.result) {
				case "missed" -> e.dir.equals("in") ? " · Perdida" : " · Sin respuesta";
				case "declined" -> " · Rechazada";
				case "unavailable" -> " · No disponible";
				case "busy" -> " · Ocupado";
				default -> e.duration > 0 ? String.format(" · %d:%02d", e.duration / 60, e.duration % 60) : "";
			};
			String what = (e.dir.equals("in") ? "Entrante" : "Saliente") + suffix;
			ctx.drawText(textRenderer, textRenderer.trimToWidth(what + "  " + fmtTime(e.time), sw - 16), sx + 8, y + 15, Ui.MUTED, false);
			String number = e.number;
			hit(sx, y, sw, 26, () -> startCall(number));
			y += 26;
		}
		endScroll(ctx);
		maxScroll = Math.max(0, log.size() * 26 - (bottom - y0));
	}

	/** Pantalla de llamada en curso. */
	private void drawCall(DrawContext ctx, int mx, int my) {
		String state = ClientPhoneState.callState;
		if (state.equals("idle")) {
			ctx.drawCenteredTextWithShadow(textRenderer, "Sin llamada", sx + sw / 2, sy + 80, Ui.MUTED);
			return;
		}
		String name = ClientPhoneState.name(ClientPhoneState.callPeer);
		int cy = sy + 50;
		int acx = sx + sw / 2;
		long ms = System.currentTimeMillis();

		// animación: ondas que salen del avatar mientras suena
		if (!state.equals("active")) {
			int color = state.equals("incoming") ? Ui.GREEN : 0xFF4DA3FF;
			for (int k = 0; k < 3; k++) {
				float ph = ((ms % 2400L) / 2400f + k / 3f) % 1f;
				int r = 26 + (int) (ph * 20);
				int a = (int) (200 * (1f - ph));
				Ui.ring(ctx, acx, cy + 24, r, Ui.alpha(color, a));
				Ui.ring(ctx, acx, cy + 24, r + 1, Ui.alpha(color, a / 2));
			}
		} else {
			Ui.ring(ctx, acx, cy + 24, 28, Ui.alpha(Ui.GREEN, 200));
		}
		avatar(ctx, acx - 24, cy, 48, name, ClientPhoneState.callPeer);

		int ty = cy + 68;
		ctx.drawCenteredTextWithShadow(textRenderer, textRenderer.trimToWidth(name, sw - 16), acx, ty, Ui.WHITE);
		if (!name.equals(ClientPhoneState.callPeer)) {
			ctx.drawCenteredTextWithShadow(textRenderer, ClientPhoneState.callPeer, acx, ty + 12, Ui.MUTED);
		}
		int sy2 = ty + 28;
		if (state.equals("active")) {
			long secs = (ms - ClientPhoneState.callStart) / 1000;
			ctx.drawCenteredTextWithShadow(textRenderer, String.format("En llamada  %02d:%02d", secs / 60, secs % 60), acx, sy2, Ui.GREEN);
			ctx.drawCenteredTextWithShadow(textRenderer, "Habla con normalidad", acx, sy2 + 14, Ui.MUTED);
		} else {
			String base = state.equals("incoming") ? "Llamada entrante" : "Llamando";
			String full = base + "...";
			int dots = (int) ((ms / 400L) % 4L);
			int x0 = acx - textRenderer.getWidth(full) / 2;
			ctx.drawTextWithShadow(textRenderer, base, x0, sy2, 0xFFDDE6F5);
			ctx.drawTextWithShadow(textRenderer, ".".repeat(dots), x0 + textRenderer.getWidth(base), sy2, 0xFFDDE6F5);
		}

		int by = bottom - 34;
		if (state.equals("incoming")) {
			int bw = (sw - 20) / 2;
			button(ctx, sx + 6, by, bw, 24, "Rechazar", Ui.RED, mx, my, () -> ClientPhoneState.send("call_decline"));
			button(ctx, sx + 14 + bw, by, bw, 24, "Contestar", Ui.GREEN, mx, my, () -> ClientPhoneState.send("call_answer"));
		} else {
			button(ctx, sx + 20, by, sw - 40, 24, "Colgar", Ui.RED, mx, my, () -> ClientPhoneState.send("call_hangup"));
		}
	}

	// ------------------------------------------------------------------ AJUSTES

	private void drawSettings(DrawContext ctx, int mx, int my) {
		ClientSettings s = ClientSettings.get();
		int top = header(ctx, "Ajustes", true, null, null, mx, my);
		beginScroll(ctx, top, bottom);
		int y0 = top + 8 - scroll;
		int y = y0;

		ctx.drawText(textRenderer, "Volumen del tono", sx + 8, y, Ui.MUTED, false);
		y += 12;
		int barW = sw - 16;
		int segW = barW / 11;
		for (int i = 0; i <= 10; i++) {
			int bx = sx + 8 + i * segW;
			int bh = 4 + i;
			int by = y + 14 - bh;
			Ui.roundRect(ctx, bx, by, segW - 1, bh, 1, i <= s.volume && s.volume > 0 ? Ui.BLUE : Ui.PANEL2);
			int vol = i;
			hit(bx, y, segW, 16, () -> {
				s.volume = vol;
				s.save();
				Sounds.ring(false);
			});
		}
		y += 26;

		ctx.drawText(textRenderer, "No molestar", sx + 8, y + 4, Ui.WHITE, false);
		int tx = sx + sw - 8 - 26;
		Ui.roundRect(ctx, tx, y, 26, 14, 7, s.dnd ? Ui.GREEN : Ui.PANEL2);
		Ui.roundRect(ctx, s.dnd ? tx + 14 : tx + 2, y + 2, 10, 10, 5, Ui.WHITE);
		hit(tx - 60, y - 2, 90, 18, () -> {
			s.dnd = !s.dnd;
			s.save();
		});
		y += 24;

		ctx.drawText(textRenderer, "Tono de llamada", sx + 8, y, Ui.MUTED, false);
		y += 12;
		int tw = (sw - 20) / 2;
		for (int i = 0; i < Sounds.RINGTONES.length; i++) {
			int bx = sx + 8 + (i % 2) * (tw + 4);
			int by = y + (i / 2) * 20;
			boolean sel = Math.floorMod(s.ringtone, Sounds.RINGTONES.length) == i;
			int idx = i;
			button(ctx, bx, by, tw, 16, Sounds.RINGTONES[i], sel ? Ui.BLUE : Ui.PANEL2, mx, my, () -> {
				s.ringtone = idx;
				s.save();
				Sounds.preview(idx);
			});
		}
		y += ((Sounds.RINGTONES.length + 1) / 2) * 20 + 6;

		ctx.drawText(textRenderer, "Fondo de pantalla", sx + 8, y, Ui.MUTED, false);
		y += 12;
		boolean hasPhoto = s.wallpaperPhoto != null && !s.wallpaperPhoto.isEmpty();
		for (int i = 0; i < WALLPAPERS.length; i++) {
			int bx = sx + 8 + i * 30;
			Ui.roundGradient(ctx, bx, y, 24, 32, 5, WALLPAPERS[i][0], WALLPAPERS[i][1]);
			if (s.wallpaper == i && !hasPhoto) {
				ctx.drawBorder(bx - 1, y - 1, 26, 34, Ui.WHITE);
			}
			int idx = i;
			hit(bx, y, 24, 32, () -> {
				s.wallpaper = idx;
				s.wallpaperPhoto = "";
				s.save();
			});
		}
		y += 38;
		button(ctx, sx + 8, y, sw - 16, 16, hasPhoto ? "Cambiar foto de fondo" : "Usar foto de la galería", Ui.PANEL2, mx, my, () -> {
			gallery = GalleryStore.list();
			if (gallery.isEmpty()) {
				ClientPhoneState.toast("Galería", "Aún no tienes fotos");
				return;
			}
			pickWallpaper = true;
			navigate(App.GALLERY);
		});
		y += 26;

		ctx.drawText(textRenderer, "Mi número", sx + 8, y, Ui.MUTED, false);
		ctx.drawText(textRenderer, ClientPhoneState.myNumber, sx + 8, y + 11, Ui.WHITE, false);
		y += 28;
		endScroll(ctx);
		maxScroll = Math.max(0, y - y0 + 16 - (bottom - top));
	}

	// ------------------------------------------------------------------ GALERÍA

	private Loaded texture(Path p) {
		Loaded l = textures.get(p);
		if (l != null) {
			return l;
		}
		MinecraftClient mc = MinecraftClient.getInstance();
		try (InputStream in = Files.newInputStream(p)) {
			NativeImage img = NativeImage.read(in);
			int w = img.getWidth();
			int h = img.getHeight();
			NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
			Identifier id = Identifier.of("smartphone", "gallery/" + (nextTex++));
			mc.getTextureManager().registerTexture(id, tex);
			tex.setFilter(true, false);
			l = new Loaded(id, w, h);
		} catch (Exception e) {
			l = new Loaded(null, 0, 0);
		}
		textures.put(p, l);
		while (textures.size() > 16) {
			Path eldest = textures.keySet().iterator().next();
			Loaded old = textures.remove(eldest);
			if (old != null && old.id() != null) {
				mc.getTextureManager().destroyTexture(old.id());
			}
		}
		return l;
	}

	private void drawImageFit(DrawContext ctx, Loaded l, int x, int y, int w, int h) {
		if (l.id() == null || l.w() == 0) {
			ctx.fill(x, y, x + w, y + h, Ui.PANEL2);
			ctx.drawCenteredTextWithShadow(textRenderer, "?", x + w / 2, y + h / 2 - 4, Ui.MUTED);
			return;
		}
		float scale = Math.min(w / (float) l.w(), h / (float) l.h());
		int dw = Math.max(1, (int) (l.w() * scale));
		int dh = Math.max(1, (int) (l.h() * scale));
		int dx = x + (w - dw) / 2;
		int dy = y + (h - dh) / 2;
		RenderSystem.enableBlend();
		ctx.drawTexture(l.id(), dx, dy, dw, dh, 0f, 0f, l.w(), l.h(), l.w(), l.h());
	}

	private void drawImageCover(DrawContext ctx, Loaded l, int x, int y, int w, int h) {
		float scale = Math.max(w / (float) l.w(), h / (float) l.h());
		float srcW = w / scale;
		float srcH = h / scale;
		float u = (l.w() - srcW) / 2f;
		float v = (l.h() - srcH) / 2f;
		RenderSystem.enableBlend();
		ctx.drawTexture(l.id(), x, y, w, h, u, v, Math.max(1, (int) srcW), Math.max(1, (int) srcH), l.w(), l.h());
	}

	private void drawGallery(DrawContext ctx, int mx, int my) {
		int top = header(ctx, pickWallpaper ? "Elige un fondo" : "Galería", true, null, null, mx, my);
		if (gallery.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "Aún no hay fotos", sx + sw / 2, top + 30, Ui.MUTED);
			ctx.drawCenteredTextWithShadow(textRenderer, "Usa la app Cámara", sx + sw / 2, top + 42, Ui.MUTED);
			maxScroll = 0;
			return;
		}
		int gap = 4;
		int cw = (sw - 12 - gap) / 2;
		int chh = cw * 9 / 16 + 8;
		int rows = (gallery.size() + 1) / 2;
		beginScroll(ctx, top, bottom);
		for (int i = 0; i < gallery.size(); i++) {
			int col = i % 2;
			int row = i / 2;
			int x = sx + 6 + col * (cw + gap);
			int y = top + 4 + row * (chh + gap) - scroll;
			if (y + chh < top || y > bottom) {
				continue;
			}
			Ui.roundRect(ctx, x, y, cw, chh, 4, Ui.PANEL);
			drawImageFit(ctx, texture(gallery.get(i)), x + 1, y + 1, cw - 2, chh - 2);
			int index = i;
			hit(x, y, cw, chh, () -> {
				if (pickWallpaper) {
					ClientSettings cs = ClientSettings.get();
					cs.wallpaperPhoto = gallery.get(index).getFileName().toString();
					cs.save();
					pickWallpaper = false;
					back();
					return;
				}
				galleryIndex = index;
				navigate(App.GALLERY_VIEW);
			});
		}
		endScroll(ctx);
		maxScroll = Math.max(0, 4 + rows * (chh + gap) - (bottom - top));
	}

	private void drawGalleryView(DrawContext ctx, int mx, int my) {
		if (gallery.isEmpty()) {
			back();
			return;
		}
		galleryIndex = Math.max(0, Math.min(galleryIndex, gallery.size() - 1));
		Path file = gallery.get(galleryIndex);
		int top = header(ctx, "Foto " + (galleryIndex + 1) + "/" + gallery.size(), true, null, null, mx, my);
		int btnY = bottom - 22;
		drawImageFit(ctx, texture(file), sx + 4, top + 4, sw - 8, btnY - top - 8);
		int bw = (sw - 20) / 3;
		button(ctx, sx + 6, btnY, bw, 18, "<", Ui.PANEL2, mx, my, () -> galleryIndex = Math.max(0, galleryIndex - 1));
		button(ctx, sx + 10 + bw, btnY, bw, 18, "Borrar", Ui.RED, mx, my, () -> deletePhoto(file));
		button(ctx, sx + 14 + bw * 2, btnY, bw, 18, ">", Ui.PANEL2, mx, my, () -> galleryIndex = Math.min(gallery.size() - 1, galleryIndex + 1));
	}

	private void deletePhoto(Path file) {
		try {
			Files.deleteIfExists(file);
		} catch (Exception e) {
			ClientPhoneState.toast("Galería", "No se pudo borrar la foto");
			return;
		}
		Loaded l = textures.remove(file);
		if (l != null && l.id() != null) {
			MinecraftClient.getInstance().getTextureManager().destroyTexture(l.id());
		}
		gallery = GalleryStore.list();
		if (gallery.isEmpty()) {
			back();
		} else {
			galleryIndex = Math.min(galleryIndex, gallery.size() - 1);
		}
	}

	// ------------------------------------------------------------------ MERCADO PAGO

	private String money(long n) {
		return ClientPhoneState.cashSymbol + String.format(Locale.US, "%,d", n);
	}

	private void drawCash(DrawContext ctx, int mx, int my) {
		int top = header(ctx, "Mercado Pago", true, null, null, mx, my);
		int cx = sx + 8;
		int cw = sw - 16;
		int cy = top + 8;
		Ui.roundGradient(ctx, cx, cy, cw, 74, 10, 0xFF29B6F6, 0xFF0D47A1);
		ctx.drawText(textRenderer, "Saldo disponible", cx + 10, cy + 8, 0xFFE3F2FF, false);
		var m = ctx.getMatrices();
		m.push();
		m.translate(cx + 10, cy + 22, 0);
		m.scale(1.7f, 1.7f, 1f);
		ctx.drawTextWithShadow(textRenderer, ClientPhoneState.cashAvailable ? money(ClientPhoneState.cashBalance) : "---", 0, 0, Ui.WHITE);
		m.pop();
		if (!ClientPhoneState.cashAvailable) {
			ctx.drawText(textRenderer, textRenderer.trimToWidth("Banco no disponible", cw - 20), cx + 10, cy + 40, 0xFFE3F2FF, false);
		}
		button(ctx, cx + 10, cy + 50, cw - 20, 16, "Enviar dinero", 0xFF0B3C8C, mx, my, () -> {
			numberField.setText("");
			amountField.setText("");
			navigate(App.CASH_SEND);
		});

		int y0 = cy + 84;
		ctx.drawText(textRenderer, "Movimientos", cx, y0, Ui.MUTED, false);
		y0 += 12;
		var hist = ClientPhoneState.cashHistory;
		if (hist.isEmpty()) {
			ctx.drawCenteredTextWithShadow(textRenderer, "Sin movimientos", sx + sw / 2, y0 + 20, Ui.MUTED);
			maxScroll = 0;
			return;
		}
		beginScroll(ctx, y0, bottom);
		int rows = hist.size();
		for (int i = 0; i < rows; i++) {
			var t = hist.get(rows - 1 - i);
			int y = y0 + i * 26 - scroll;
			if (y + 24 < y0 || y > bottom) {
				continue;
			}
			boolean in = "in".equals(t.dir);
			Ui.roundRect(ctx, sx + 6, y, sw - 12, 24, 6, Ui.PANEL);
			avatar(ctx, sx + 9, y + 3, 18, ClientPhoneState.name(t.number), t.number);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(ClientPhoneState.name(t.number), sw - 82), sx + 32, y + 4, Ui.WHITE, false);
			ctx.drawText(textRenderer, fmtTime(t.time), sx + 32, y + 14, Ui.MUTED, false);
			String amt = (in ? "+" : "-") + money(t.amount);
			ctx.drawText(textRenderer, amt, sx + sw - 9 - textRenderer.getWidth(amt), y + 8, in ? Ui.GREEN : Ui.RED, false);
		}
		endScroll(ctx);
		maxScroll = Math.max(0, rows * 26 - (bottom - y0));
	}

	private void drawCashSend(DrawContext ctx, int mx, int my, float delta) {
		int top = header(ctx, "Enviar dinero", true, null, null, mx, my);
		int y = top + 8;
		String who = ClientPhoneState.name(Numbers.normalize(numberField.getText()) == null ? "" : Numbers.normalize(numberField.getText()));
		ctx.drawText(textRenderer, textRenderer.trimToWidth(who.isEmpty() ? "Para (número)" : "Para: " + who, sw - 16), sx + 8, y, Ui.MUTED, false);
		y += 11;
		field(ctx, numberField, sx + 8, y, sw - 16, 18, mx, my, delta);
		y += 24;
		ctx.drawText(textRenderer, "Monto (" + ClientPhoneState.cashSymbol + ")", sx + 8, y, Ui.MUTED, false);
		y += 11;
		field(ctx, amountField, sx + 8, y, sw - 16, 18, mx, my, delta);
		y += 24;
		button(ctx, sx + 8, y, sw - 16, 20, "Enviar", Ui.GREEN, mx, my, this::sendMoney);
		y += 30;

		ctx.drawText(textRenderer, "O elige un contacto", sx + 8, y, Ui.MUTED, false);
		y += 12;
		var contacts = ClientPhoneState.contacts;
		beginScroll(ctx, y, bottom);
		for (int i = 0; i < contacts.size(); i++) {
			Contact c = contacts.get(i);
			int ry = y + i * 24 - scroll;
			if (ry + 22 < y || ry > bottom) {
				continue;
			}
			Ui.roundRect(ctx, sx + 6, ry, sw - 12, 22, 6, Ui.PANEL);
			avatar(ctx, sx + 9, ry + 2, 18, c.name, c.number);
			ctx.drawText(textRenderer, textRenderer.trimToWidth(c.name, sw - 44), sx + 32, ry + 7, Ui.WHITE, false);
			hit(sx + 6, ry, sw - 12, 22, () -> numberField.setText(c.number));
		}
		endScroll(ctx);
		maxScroll = Math.max(0, contacts.size() * 24 - (bottom - y));
	}

	private void sendMoney() {
		String to = Numbers.normalize(numberField.getText());
		if (to == null) {
			ClientPhoneState.toast("Mercado Pago", "El número debe tener 7 dígitos");
			return;
		}
		long amount;
		try {
			amount = Long.parseLong(amountField.getText().trim());
		} catch (NumberFormatException e) {
			ClientPhoneState.toast("Mercado Pago", "Escribe un monto");
			return;
		}
		if (amount <= 0) {
			ClientPhoneState.toast("Mercado Pago", "Monto inválido");
			return;
		}
		JsonObject o = new JsonObject();
		o.addProperty("to", to);
		o.addProperty("amount", String.valueOf(amount));
		ClientPhoneState.send("cash_send", o);
		amountField.setText("");
		back();
	}
}
