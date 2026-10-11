package com.smartphone.client;

import com.google.gson.JsonObject;
import com.smartphone.common.CallLogEntry;
import com.smartphone.common.Contact;
import com.smartphone.common.Json;
import com.smartphone.common.Message;
import com.smartphone.net.PhoneC2S;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Todo lo que el cliente sabe de su teléfono (lo manda el servidor al entrar). */
public final class ClientPhoneState {
	public record Toast(String title, String text, long until) {
	}

	public static String myNumber = "";
	public static List<Contact> contacts = new ArrayList<>();
	public static final Map<String, List<Message>> convs = new HashMap<>();
	public static List<CallLogEntry> callLog = new ArrayList<>();

	/** idle, outgoing, incoming, active */
	public static String callState = "idle";
	public static String callPeer = "";
	public static long callStart;
	public static int callTicks;
	public static String endText = "";
	public static long endUntil;

	// Mercado Pago
	public static boolean cashAvailable;
	public static long cashBalance;
	public static String cashSymbol = "$";
	public static List<com.smartphone.common.TransferEntry> cashHistory = new ArrayList<>();

	public static final List<Toast> toasts = new ArrayList<>();

	private ClientPhoneState() {
	}

	public static void reset() {
		myNumber = "";
		contacts = new ArrayList<>();
		convs.clear();
		callLog = new ArrayList<>();
		callState = "idle";
		callPeer = "";
		endText = "";
		cashAvailable = false;
		cashBalance = 0;
		cashHistory = new ArrayList<>();
		toasts.clear();
	}

	public static String name(String number) {
		for (Contact c : contacts) {
			if (c.number.equals(number)) {
				return c.name;
			}
		}
		return number;
	}

	public static boolean isContact(String number) {
		for (Contact c : contacts) {
			if (c.number.equals(number)) {
				return true;
			}
		}
		return false;
	}

	/** Inserta o actualiza un mensaje. Devuelve true si era nuevo. */
	public static boolean upsert(String peer, Message m) {
		List<Message> list = convs.computeIfAbsent(peer, k -> new ArrayList<>());
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).id == m.id) {
				list.set(i, m);
				return false;
			}
		}
		list.add(m);
		list.sort((a, b) -> Long.compare(a.id, b.id));
		return true;
	}

	public static int unread(String peer) {
		int n = 0;
		List<Message> list = convs.get(peer);
		if (list != null) {
			for (Message m : list) {
				if (m.to.equals(myNumber) && m.status < Message.READ) {
					n++;
				}
			}
		}
		return n;
	}

	public static int unreadTotal() {
		int n = 0;
		for (String peer : convs.keySet()) {
			n += unread(peer);
		}
		return n;
	}

	/** Marca como leído todo lo recibido de ese número y avisa al servidor. */
	public static void markRead(String peer) {
		if (unread(peer) == 0) {
			return;
		}
		for (Message m : convs.get(peer)) {
			if (m.to.equals(myNumber) && m.status < Message.READ) {
				m.status = Message.READ;
			}
		}
		JsonObject o = new JsonObject();
		o.addProperty("peer", peer);
		send("mark_read", o);
	}

	public static void toast(String title, String text) {
		toasts.add(new Toast(title, text, System.currentTimeMillis() + 4500));
		while (toasts.size() > 3) {
			toasts.remove(0);
		}
	}

	public static void send(String action, JsonObject body) {
		try {
			ClientPlayNetworking.send(new PhoneC2S(action, Json.GSON.toJson(body)));
		} catch (Exception e) {
			toast("Teléfono", "No se pudo hablar con el servidor");
		}
	}

	public static void send(String action) {
		send(action, new JsonObject());
	}
}
