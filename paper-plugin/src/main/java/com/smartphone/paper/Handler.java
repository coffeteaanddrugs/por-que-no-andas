package com.smartphone.paper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.smartphone.common.Contact;
import com.smartphone.common.Json;
import com.smartphone.common.Message;
import com.smartphone.common.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Toda la lógica del servidor (mensajes, contactos, sincronización). */
public final class Handler {
	public static PhoneData data;
	public static final CallManager calls = new CallManager();

	private static final int MAX_TEXT = 256;
	private static final int MAX_SYNC_MESSAGES = 300;
	private static final int MAX_CONTACTS = 150;

	private Handler() {
	}

	// ---------- envío ----------

	public static void send(Player player, String action, JsonElement body) {
		Net.send(SmartphonePlugin.get(), player, action, Json.GSON.toJson(body));
	}

	public static void toast(Player player, String text) {
		JsonObject o = new JsonObject();
		o.addProperty("text", text);
		send(player, "toast", o);
	}

	public static Player online(String uuid) {
		if (uuid == null) {
			return null;
		}
		try {
			return Bukkit.getPlayer(UUID.fromString(uuid));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	/** Manda mensajes en paquetes pequeños (Bukkit limita cada paquete a ~32 KB). */
	private static void sendMessages(Player to, String peer, List<Message> messages, boolean notify, boolean reset) {
		List<Message> batch = new ArrayList<>();
		int size = 0;
		boolean first = true;
		for (Message m : messages) {
			int len = Json.GSON.toJson(m).getBytes(StandardCharsets.UTF_8).length + 2;
			if (!batch.isEmpty() && size + len > 24000) {
				sendBatch(to, peer, batch, notify, reset && first);
				first = false;
				batch = new ArrayList<>();
				size = 0;
			}
			batch.add(m);
			size += len;
		}
		if (!batch.isEmpty() || (reset && first)) {
			sendBatch(to, peer, batch, notify, reset && first);
		}
	}

	private static void sendBatch(Player to, String peer, List<Message> batch, boolean notify, boolean reset) {
		JsonObject o = new JsonObject();
		o.addProperty("peer", peer);
		o.addProperty("notify", notify);
		o.addProperty("reset", reset);
		o.add("messages", Json.GSON.toJsonTree(batch));
		send(to, "msgs", o);
	}

	public static void sendContacts(Player p) {
		JsonObject o = new JsonObject();
		o.add("contacts", Json.GSON.toJsonTree(data.contactsOf(p.getUniqueId().toString())));
		send(p, "contacts", o);
	}

	public static void sendCallLog(Player p) {
		JsonObject o = new JsonObject();
		o.add("calls", Json.GSON.toJsonTree(data.logOf(p.getUniqueId().toString())));
		send(p, "calllog", o);
	}

	// ---------- el cliente pide sus datos ----------

	private static void onHello(Player player) {
		String uuid = player.getUniqueId().toString();
		String number = data.numberFor(player.getUniqueId());

		JsonObject init = new JsonObject();
		init.addProperty("number", number);
		init.add("contacts", Json.GSON.toJsonTree(data.contactsOf(uuid)));
		init.add("calls", Json.GSON.toJsonTree(data.logOf(uuid)));
		send(player, "init", init);

		// Mensajes que llegaron mientras estabas desconectado: pasan a "entregado"
		int unread = 0;
		for (String peer : data.peersOf(number)) {
			List<Message> conv = data.conversation(number, peer);
			List<Message> changed = new ArrayList<>();
			for (Message m : conv) {
				if (m.to.equals(number)) {
					if (m.status == Message.SENT) {
						m.status = Message.DELIVERED;
						changed.add(m);
						data.markDirty();
					}
					if (m.status < Message.READ) {
						unread++;
					}
				}
			}
			if (!changed.isEmpty()) {
				Player sender = online(data.uuidOf(peer));
				if (sender != null) {
					sendMessages(sender, number, changed, false, false);
				}
			}
			int from = Math.max(0, conv.size() - MAX_SYNC_MESSAGES);
			sendMessages(player, peer, new ArrayList<>(conv.subList(from, conv.size())), false, true);
		}
		if (unread > 0) {
			toast(player, "Tienes " + unread + (unread == 1 ? " mensaje sin leer" : " mensajes sin leer"));
		}
	}

	// ---------- peticiones del cliente ----------

	public static void handle(Player player, String action, String raw) {
		if (data == null) {
			return;
		}
		JsonObject in;
		try {
			JsonElement parsed = JsonParser.parseString(raw == null || raw.isEmpty() ? "{}" : raw);
			in = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
		} catch (Exception e) {
			return;
		}
		try {
			switch (action) {
				case "hello" -> onHello(player);
				case "send" -> {
					if (needPhone(player)) onSend(player, in);
				}
				case "mark_read" -> onMarkRead(player, in);
				case "add_contact" -> {
					if (needPhone(player)) onAddContact(player, in);
				}
				case "remove_contact" -> {
					if (needPhone(player)) onRemoveContact(player, in);
				}
				case "call_start" -> {
					if (needPhone(player)) calls.start(player, str(in, "number"));
				}
				case "call_answer" -> {
					if (needPhone(player)) calls.answer(player);
				}
				case "cash_open" -> {
					if (needPhone(player)) Cash.sendCash(player);
				}
				case "cash_send" -> {
					if (needPhone(player)) Cash.send(player, in);
				}
				case "call_decline" -> calls.decline(player);
				case "call_hangup" -> calls.hangup(player);
				default -> {
				}
			}
		} catch (Exception e) {
			Log.error("Error procesando acción {}", action, e);
		}
	}

	static String str(JsonObject o, String key) {
		JsonElement e = o.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : "";
	}

	/** Para usar el teléfono hay que llevar el item encima. */
	private static boolean needPhone(Player player) {
		if (PhoneItem.has(player)) {
			return true;
		}
		toast(player, "Necesitas tener el teléfono encima");
		return false;
	}

	private static void onSend(Player player, JsonObject in) {
		String me = data.numberFor(player.getUniqueId());
		String to = Numbers.normalize(str(in, "to"));
		String text = str(in, "text").trim();
		if (text.isEmpty()) {
			return;
		}
		if (text.length() > MAX_TEXT) {
			text = text.substring(0, MAX_TEXT);
		}
		if (to == null || data.uuidOf(to) == null) {
			toast(player, "Ese número no existe");
			return;
		}
		if (to.equals(me)) {
			toast(player, "No puedes escribirte a ti mismo");
			return;
		}
		Player target = online(data.uuidOf(to));
		Message m = data.addMessage(me, to, text, target != null ? Message.DELIVERED : Message.SENT);
		sendMessages(player, to, List.of(m), false, false);
		if (target != null) {
			sendMessages(target, me, List.of(m), true, false);
		}
	}

	private static void onMarkRead(Player player, JsonObject in) {
		String me = data.numberFor(player.getUniqueId());
		String peer = Numbers.normalize(str(in, "peer"));
		if (peer == null) {
			return;
		}
		List<Message> changed = new ArrayList<>();
		for (Message m : data.conversation(me, peer)) {
			if (m.to.equals(me) && m.status < Message.READ) {
				m.status = Message.READ;
				changed.add(m);
			}
		}
		if (changed.isEmpty()) {
			return;
		}
		data.markDirty();
		Player sender = online(data.uuidOf(peer));
		if (sender != null) {
			sendMessages(sender, me, changed, false, false);
		}
	}

	private static void onAddContact(Player player, JsonObject in) {
		String me = data.numberFor(player.getUniqueId());
		String number = Numbers.normalize(str(in, "number"));
		String name = str(in, "name").trim();
		if (name.length() > 24) {
			name = name.substring(0, 24);
		}
		if (name.isEmpty()) {
			toast(player, "Escribe un nombre");
			return;
		}
		if (number == null || data.uuidOf(number) == null) {
			toast(player, "Ese número no existe");
			return;
		}
		if (number.equals(me)) {
			toast(player, "Ese es tu propio número");
			return;
		}
		List<Contact> list = data.contactsOf(player.getUniqueId().toString());
		Contact existing = null;
		for (Contact c : list) {
			if (c.number.equals(number)) {
				existing = c;
			}
		}
		if (existing != null) {
			existing.name = name;
		} else {
			if (list.size() >= MAX_CONTACTS) {
				toast(player, "Tu agenda está llena");
				return;
			}
			list.add(new Contact(name, number));
		}
		list.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
		data.markDirty();
		sendContacts(player);
		toast(player, "Contacto guardado");
	}

	private static void onRemoveContact(Player player, JsonObject in) {
		String number = Numbers.normalize(str(in, "number"));
		if (number == null) {
			return;
		}
		data.contactsOf(player.getUniqueId().toString()).removeIf(c -> c.number.equals(number));
		data.markDirty();
		sendContacts(player);
	}
}
