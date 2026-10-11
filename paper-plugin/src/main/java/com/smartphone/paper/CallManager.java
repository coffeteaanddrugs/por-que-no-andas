package com.smartphone.paper;

import com.google.gson.JsonObject;
import com.smartphone.common.CallLogEntry;
import com.smartphone.common.Numbers;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;

/** Estado de las llamadas entre jugadores (quién llama a quién, timbre, contestar, colgar). */
public final class CallManager {
	private static final long RING_TIMEOUT_MS = 30_000;

	private static final class Call {
		final UUID caller;
		final UUID callee;
		final long created = System.currentTimeMillis();
		boolean active;
		long answered;

		Call(UUID caller, UUID callee) {
			this.caller = caller;
			this.callee = callee;
		}
	}

	private final Map<UUID, Call> calls = new HashMap<>();

	private static PhoneData data() {
		return Handler.data;
	}

	private static void sendCall(Player p, String state, String number, String reason) {
		JsonObject o = new JsonObject();
		o.addProperty("state", state);
		o.addProperty("number", number);
		o.addProperty("reason", reason);
		Handler.send(p, "call", o);
	}

	/** Intento fallido de llamada (no existe, ocupado, desconectado...). */
	private void failed(Player caller, String number, String result) {
		data().addLog(caller.getUniqueId().toString(), new CallLogEntry(number, "out", System.currentTimeMillis(), 0, result));
		sendCall(caller, "ended", number, result);
		Handler.sendCallLog(caller);
	}

	public void start(Player caller, String rawNumber) {
		if (calls.containsKey(caller.getUniqueId())) {
			return;
		}
		String number = Numbers.normalize(rawNumber);
		String myNumber = data().numberFor(caller.getUniqueId());
		if (number == null || data().uuidOf(number) == null) {
			Handler.toast(caller, "Ese número no existe");
			return;
		}
		if (number.equals(myNumber)) {
			Handler.toast(caller, "No puedes llamarte a ti mismo");
			return;
		}
		Player callee = Handler.online(data().uuidOf(number));
		if (callee == null || !PhoneItem.has(callee)) {
			failed(caller, number, "unavailable");
			return;
		}
		if (calls.containsKey(callee.getUniqueId())) {
			failed(caller, number, "busy");
			return;
		}
		Call call = new Call(caller.getUniqueId(), callee.getUniqueId());
		calls.put(caller.getUniqueId(), call);
		calls.put(callee.getUniqueId(), call);
		sendCall(caller, "outgoing", number, "");
		sendCall(callee, "incoming", myNumber, "");
	}

	public void answer(Player player) {
		Call call = calls.get(player.getUniqueId());
		if (call == null || call.active || !call.callee.equals(player.getUniqueId())) {
			return;
		}
		call.active = true;
		call.answered = System.currentTimeMillis();
		Player caller = Bukkit.getPlayer(call.caller);
		if (caller != null) {
			sendCall(caller, "active", data().numberFor(call.callee), "");
		}
		sendCall(player, "active", data().numberFor(call.caller), "");
		VoiceBridge.start(call.caller, call.callee);
	}

	public void decline(Player player) {
		Call call = calls.get(player.getUniqueId());
		if (call == null) {
			return;
		}
		if (!call.active && call.callee.equals(player.getUniqueId())) {
			finish(call, "declined");
		} else {
			hangup(player);
		}
	}

	public void hangup(Player player) {
		Call call = calls.get(player.getUniqueId());
		if (call == null) {
			return;
		}
		finish(call, call.active ? "answered" : "missed");
	}

	public void onDisconnect(Player player) {
		Call call = calls.get(player.getUniqueId());
		if (call != null) {
			finish(call, call.active ? "answered" : "missed");
		}
	}

	/** Se llama una vez por segundo: corta las llamadas que nadie contesta. */
	public void tick() {
		if (calls.isEmpty()) {
			return;
		}
		long now = System.currentTimeMillis();
		for (Call call : new ArrayList<>(calls.values())) {
			if (!call.active && now - call.created > RING_TIMEOUT_MS && calls.containsKey(call.caller)) {
				finish(call, "missed");
			}
		}
	}

	public void endAll() {
		for (Call call : new ArrayList<>(calls.values())) {
			if (calls.containsKey(call.caller)) {
				finish(call, call.active ? "answered" : "missed");
			}
		}
	}

	private void finish(Call call, String result) {
		calls.remove(call.caller);
		calls.remove(call.callee);
		if (call.active) {
			VoiceBridge.end(call.caller, call.callee);
		}
		long now = System.currentTimeMillis();
		int duration = call.active ? (int) ((now - call.answered) / 1000) : 0;
		String callerNumber = data().numberFor(call.caller);
		String calleeNumber = data().numberFor(call.callee);
		data().addLog(call.caller.toString(), new CallLogEntry(calleeNumber, "out", now, duration, result));
		data().addLog(call.callee.toString(), new CallLogEntry(callerNumber, "in", now, duration, result));

		String reason = result.equals("answered") ? "ended" : result;
		Player caller = Bukkit.getPlayer(call.caller);
		Player callee = Bukkit.getPlayer(call.callee);
		if (caller != null) {
			sendCall(caller, "ended", calleeNumber, reason);
			Handler.sendCallLog(caller);
		}
		if (callee != null) {
			sendCall(callee, "ended", callerNumber, reason);
			Handler.sendCallLog(callee);
		}
	}
}
