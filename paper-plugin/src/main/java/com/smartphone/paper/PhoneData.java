package com.smartphone.paper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.smartphone.common.CallLogEntry;
import com.smartphone.common.Contact;
import com.smartphone.common.Message;
import com.smartphone.common.TransferEntry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Toda la información persistente del teléfono: números, contactos, mensajes e historial de llamadas.
 * Se guarda en plugins/Smartphone/smartphone.json, así los mensajes siguen ahí aunque te desconectes.
 */
public final class PhoneData {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final int MAX_LOG = 40;

	public Map<String, String> numbers = new HashMap<>();                 // uuid -> número
	public Map<String, List<Contact>> contacts = new HashMap<>();         // uuid -> contactos
	public Map<String, List<Message>> conversations = new HashMap<>();    // "a|b" -> mensajes
	public Map<String, List<CallLogEntry>> callLogs = new HashMap<>();    // uuid -> historial
	public Map<String, List<TransferEntry>> transfers = new HashMap<>();  // uuid -> movimientos de Cashapp
	public long nextMessageId = 1;

	private transient Map<String, String> byNumber = new HashMap<>();
	private transient Path file;
	private transient boolean dirty;

	public static PhoneData load(Path file) {
		PhoneData data = null;
		if (Files.exists(file)) {
			try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				data = GSON.fromJson(r, PhoneData.class);
			} catch (Exception e) {
				Log.error("No se pudo leer {}, se empieza de cero", file, e);
			}
		}
		if (data == null) {
			data = new PhoneData();
		}
		data.file = file;
		data.index();
		return data;
	}

	private void index() {
		if (numbers == null) numbers = new HashMap<>();
		if (contacts == null) contacts = new HashMap<>();
		if (conversations == null) conversations = new HashMap<>();
		if (callLogs == null) callLogs = new HashMap<>();
		if (transfers == null) transfers = new HashMap<>();
		byNumber = new HashMap<>();
		for (Map.Entry<String, String> e : numbers.entrySet()) {
			byNumber.put(e.getValue(), e.getKey());
		}
	}

	public synchronized void save() {
		if (file == null) {
			return;
		}
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
				GSON.toJson(this, w);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			dirty = false;
		} catch (IOException e) {
			Log.error("No se pudo guardar {}", file, e);
		}
	}

	public boolean isDirty() {
		return dirty;
	}

	public void markDirty() {
		dirty = true;
	}

	// ---------- números ----------

	/** Devuelve el número del jugador; si no tenía, le crea uno único. */
	public synchronized String numberFor(UUID id) {
		String key = id.toString();
		String n = numbers.get(key);
		if (n != null) {
			return n;
		}
		Random rnd = new Random();
		do {
			n = String.format("%03d-%04d", 100 + rnd.nextInt(900), rnd.nextInt(10000));
		} while (byNumber.containsKey(n));
		numbers.put(key, n);
		byNumber.put(n, key);
		markDirty();
		return n;
	}

	/** UUID (como texto) del dueño de un número, o null. */
	public String uuidOf(String number) {
		return byNumber.get(number);
	}

	// ---------- contactos ----------

	public List<Contact> contactsOf(String uuid) {
		return contacts.computeIfAbsent(uuid, k -> new ArrayList<>());
	}

	// ---------- mensajes ----------

	public static String key(String a, String b) {
		return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
	}

	public List<Message> conversation(String a, String b) {
		return conversations.computeIfAbsent(key(a, b), k -> new ArrayList<>());
	}

	public Message addMessage(String from, String to, String text, int status) {
		Message m = new Message();
		m.id = nextMessageId++;
		m.from = from;
		m.to = to;
		m.text = text;
		m.time = System.currentTimeMillis();
		m.status = status;
		conversation(from, to).add(m);
		markDirty();
		return m;
	}

	/** Todos los números con los que "number" tiene una conversación. */
	public List<String> peersOf(String number) {
		List<String> peers = new ArrayList<>();
		for (String k : conversations.keySet()) {
			String[] parts = k.split("\\|");
			if (parts.length != 2) {
				continue;
			}
			if (parts[0].equals(number)) {
				peers.add(parts[1]);
			} else if (parts[1].equals(number)) {
				peers.add(parts[0]);
			}
		}
		return peers;
	}

	// ---------- llamadas ----------

	public void addLog(String uuid, CallLogEntry e) {
		List<CallLogEntry> list = callLogs.computeIfAbsent(uuid, k -> new ArrayList<>());
		list.add(0, e);
		while (list.size() > MAX_LOG) {
			list.remove(list.size() - 1);
		}
		markDirty();
	}

	public List<CallLogEntry> logOf(String uuid) {
		return callLogs.computeIfAbsent(uuid, k -> new ArrayList<>());
	}

	// ---------- Cashapp ----------

	public List<TransferEntry> transfersOf(String uuid) {
		return transfers.computeIfAbsent(uuid, k -> new ArrayList<>());
	}

	public void addTransfer(String uuid, TransferEntry e) {
		List<TransferEntry> list = transfersOf(uuid);
		list.add(0, e);
		while (list.size() > 25) {
			list.remove(list.size() - 1);
		}
		markDirty();
	}
}
