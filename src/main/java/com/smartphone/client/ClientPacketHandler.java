package com.smartphone.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.smartphone.PhoneMod;
import com.smartphone.common.CallLogEntry;
import com.smartphone.common.Contact;
import com.smartphone.common.Json;
import com.smartphone.common.Message;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/** Procesa lo que manda el servidor. */
public final class ClientPacketHandler {
	private static final Type MESSAGES = new TypeToken<List<Message>>() {
	}.getType();
	private static final Type CONTACTS = new TypeToken<List<Contact>>() {
	}.getType();
	private static final Type CALLS = new TypeToken<List<CallLogEntry>>() {
	}.getType();

	private ClientPacketHandler() {
	}

	public static void handle(String action, String raw) {
		try {
			JsonElement parsed = JsonParser.parseString(raw);
			if (!parsed.isJsonObject()) {
				return;
			}
			JsonObject o = parsed.getAsJsonObject();
			switch (action) {
				case "init" -> onInit(o);
				case "msgs" -> onMessages(o);
				case "contacts" -> ClientPhoneState.contacts = list(o.get("contacts"), CONTACTS);
				case "calllog" -> ClientPhoneState.callLog = list(o.get("calls"), CALLS);
				case "call" -> onCall(o);
				case "cash" -> onCash(o);
				case "toast" -> ClientPhoneState.toast("Teléfono", o.get("text").getAsString());
				default -> {
				}
			}
		} catch (Exception e) {
			PhoneMod.LOGGER.error("Error procesando paquete {}", action, e);
		}
	}

	private static <T> List<T> list(JsonElement e, Type type) {
		if (e == null || e.isJsonNull()) {
			return new ArrayList<>();
		}
		List<T> l = Json.GSON.fromJson(e, type);
		return l == null ? new ArrayList<>() : l;
	}

	private static void onInit(JsonObject o) {
		ClientPhoneState.reset();
		ClientPhoneState.myNumber = o.get("number").getAsString();
		ClientPhoneState.contacts = list(o.get("contacts"), CONTACTS);
		ClientPhoneState.callLog = list(o.get("calls"), CALLS);
	}

	private static void onMessages(JsonObject o) {
		String peer = o.get("peer").getAsString();
		boolean notify = o.get("notify").getAsBoolean();
		boolean reset = o.get("reset").getAsBoolean();
		List<Message> msgs = list(o.get("messages"), MESSAGES);
		if (reset) {
			List<Message> copy = new ArrayList<>(msgs);
			copy.sort((a, b) -> Long.compare(a.id, b.id));
			ClientPhoneState.convs.put(peer, copy);
			return;
		}
		for (Message m : msgs) {
			boolean isNew = ClientPhoneState.upsert(peer, m);
			if (isNew && notify && m.to.equals(ClientPhoneState.myNumber) && !ClientSettings.get().dnd) {
				ClientPhoneState.toast(ClientPhoneState.name(m.from), m.text);
				Sounds.message();
			}
		}
	}

	private static final Type TRANSFERS = new TypeToken<List<com.smartphone.common.TransferEntry>>() {
	}.getType();

	private static void onCash(JsonObject o) {
		ClientPhoneState.cashAvailable = o.get("available").getAsBoolean();
		ClientPhoneState.cashBalance = o.get("balance").getAsLong();
		ClientPhoneState.cashSymbol = o.get("symbol").getAsString();
		ClientPhoneState.cashHistory = list(o.get("history"), TRANSFERS);
	}

	private static void onCall(JsonObject o) {
		String state = o.get("state").getAsString();
		String number = o.get("number").getAsString();
		String reason = o.has("reason") ? o.get("reason").getAsString() : "";
		ClientPhoneState.callTicks = 0;
		switch (state) {
			case "outgoing" -> {
				ClientPhoneState.callState = "outgoing";
				ClientPhoneState.callPeer = number;
				ClientPhoneState.callStart = System.currentTimeMillis();
			}
			case "incoming" -> {
				ClientPhoneState.callState = "incoming";
				ClientPhoneState.callPeer = number;
				ClientPhoneState.callStart = System.currentTimeMillis();
				if (ClientSettings.get().dnd) {
					ClientPhoneState.send("call_decline");
				}
			}
			case "active" -> {
				ClientPhoneState.callState = "active";
				ClientPhoneState.callPeer = number;
				ClientPhoneState.callStart = System.currentTimeMillis();
			}
			case "ended" -> {
				boolean wasIncoming = ClientPhoneState.callState.equals("incoming");
				ClientPhoneState.callState = "idle";
				String text = switch (reason) {
					case "declined" -> "Llamada rechazada";
					case "missed" -> wasIncoming ? "Llamada perdida" : "Sin respuesta";
					case "unavailable" -> "No conectado o sin cobertura";
					case "busy" -> "Ocupado";
					default -> "Llamada finalizada";
				};
				if (reason.equals("unavailable")) {
					Sounds.unavailable();
				}
				ClientPhoneState.endText = text + " · " + ClientPhoneState.name(number);
				ClientPhoneState.endUntil = System.currentTimeMillis() + 3500;
			}
			default -> {
			}
		}
	}
}
