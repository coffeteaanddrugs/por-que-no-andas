package com.smartphone.paper;

import java.util.UUID;

/** Puente hacia Plasmo Voice. Si no está instalado o falla, el teléfono sigue funcionando (sin audio). */
public final class VoiceBridge {
	private static boolean available;
	private static boolean warned;

	private VoiceBridge() {
	}

	/** Se llama al activar el plugin. */
	public static void init(SmartphonePlugin plugin) {
		if (!plugin.getServer().getPluginManager().isPluginEnabled("PlasmoVoice")) {
			Log.info("PlasmoVoice no está instalado: las llamadas no tendrán audio");
			return;
		}
		try {
			PhoneVoiceAddon.register();
			available = true;
			Log.info("Addon de voz registrado en Plasmo Voice");
		} catch (Throwable t) {
			Log.error("No se pudo registrar el addon de Plasmo Voice", t);
		}
	}

	public static void start(UUID a, UUID b) {
		if (!available) {
			return;
		}
		try {
			PhoneVoiceAddon.startCall(a, b);
		} catch (Throwable t) {
			warn(t);
		}
	}

	public static void end(UUID a, UUID b) {
		if (!available) {
			return;
		}
		try {
			PhoneVoiceAddon.endCall(a, b);
		} catch (Throwable t) {
			warn(t);
		}
	}

	private static void warn(Throwable t) {
		if (!warned) {
			warned = true;
			Log.error("Error con Plasmo Voice", t);
		}
	}
}
