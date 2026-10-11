package com.smartphone.paper;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Formato de los paquetes: igual que el mod de Fabric (dos textos: acción y datos),
 * cada texto = longitud en VarInt + bytes UTF-8.
 */
public final class Net {
	public static final String C2S = "smartphone:c2s";
	public static final String S2C = "smartphone:s2c";
	/** Límite de Bukkit para un mensaje de plugin (32766 bytes). */
	public static final int MAX_BYTES = 32000;

	private Net() {
	}

	private static void writeVarInt(ByteArrayOutputStream out, int value) {
		while ((value & -128) != 0) {
			out.write(value & 127 | 128);
			value >>>= 7;
		}
		out.write(value);
	}

	private static void writeString(ByteArrayOutputStream out, String s) {
		byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
		writeVarInt(out, bytes.length);
		out.write(bytes, 0, bytes.length);
	}

	/** Devuelve {acción, datos} o null si el paquete está mal formado. */
	public static String[] read(byte[] data) {
		try {
			int[] pos = {0};
			String action = readString(data, pos);
			String body = readString(data, pos);
			return new String[]{action, body};
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static String readString(byte[] data, int[] pos) {
		int len = 0;
		int shift = 0;
		int b;
		do {
			b = data[pos[0]++];
			len |= (b & 127) << shift;
			shift += 7;
			if (shift > 35) {
				throw new IllegalArgumentException("VarInt demasiado grande");
			}
		} while ((b & 128) != 0);
		if (len < 0 || pos[0] + len > data.length) {
			throw new IllegalArgumentException("Longitud inválida");
		}
		String s = new String(data, pos[0], len, StandardCharsets.UTF_8);
		pos[0] += len;
		return s;
	}

	public static void send(Plugin plugin, Player player, String action, String json) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		writeString(out, action);
		writeString(out, json);
		byte[] bytes = out.toByteArray();
		if (bytes.length > MAX_BYTES) {
			Log.error("Paquete '{}' demasiado grande ({} bytes), no se envía", action, bytes.length);
			return;
		}
		player.sendPluginMessage(plugin, S2C, bytes);
	}
}
