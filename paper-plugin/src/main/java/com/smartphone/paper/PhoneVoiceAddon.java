package com.smartphone.paper;

import su.plo.voice.api.addon.AddonInitializer;
import su.plo.voice.api.addon.InjectPlasmoVoice;
import su.plo.voice.api.addon.annotation.Addon;
import su.plo.voice.api.event.EventSubscribe;
import su.plo.voice.api.server.PlasmoVoiceServer;
import su.plo.voice.api.server.audio.line.ServerSourceLine;
import su.plo.voice.api.server.audio.source.ServerDirectSource;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEndEvent;
import su.plo.voice.api.server.event.audio.source.PlayerSpeakEvent;
import su.plo.voice.api.server.player.VoicePlayer;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Addon de Plasmo Voice. Mientras dos jugadores están en llamada, lo que cada uno habla
 * (con su micrófono normal de Plasmo Voice) también llega al otro, sin importar la distancia.
 * No hay que configurar ninguna activación nueva.
 */
@Addon(
		id = "pv-addon-smartphone",
		name = "Smartphone",
		version = "1.0.0",
		authors = {"Smartphone"}
)
public final class PhoneVoiceAddon implements AddonInitializer {
	private static PhoneVoiceAddon instance;

	@InjectPlasmoVoice
	private PlasmoVoiceServer voiceServer;

	private ServerSourceLine line;

	/** jugador que habla -> jugador que escucha */
	private final Map<UUID, UUID> partners = new ConcurrentHashMap<>();
	/** jugador que escucha -> fuente de audio dirigida solo a él */
	private final Map<UUID, ServerDirectSource> sources = new ConcurrentHashMap<>();

	/** Registra el addon en Plasmo Voice. */
	public static void register() {
		PlasmoVoiceServer.getAddonsLoader().load(new PhoneVoiceAddon());
	}

	@Override
	public void onAddonInitialize() {
		instance = this;
		line = voiceServer.getSourceLineManager().createBuilder(
				this,
				"phone",
				"Teléfono",
				"plasmovoice:textures/icons/speaker.png",
				5
		).build();
	}

	@Override
	public void onAddonShutdown() {
		for (ServerDirectSource s : sources.values()) {
			s.remove();
		}
		sources.clear();
		partners.clear();
		instance = null;
	}

	/** Cada trozo de voz que habla un jugador (con cualquier activación). */
	@EventSubscribe(ignoreCancelled = false)
	public void onPlayerSpeak(PlayerSpeakEvent event) {
		if (partners.isEmpty()) {
			return;
		}
		ServerDirectSource source = sourceFor(event.getPlayer());
		if (source != null) {
			source.sendAudioFrame(event.getPacket().getData(), event.getPacket().getSequenceNumber());
		}
	}

	/** El jugador dejó de hablar. */
	@EventSubscribe(ignoreCancelled = false)
	public void onPlayerSpeakEnd(PlayerSpeakEndEvent event) {
		if (partners.isEmpty()) {
			return;
		}
		ServerDirectSource source = sourceFor(event.getPlayer());
		if (source != null) {
			source.sendAudioEnd(event.getPacket().getSequenceNumber());
		}
	}

	/** Fuente de audio del interlocutor del jugador que está hablando (la crea la primera vez). */
	private ServerDirectSource sourceFor(VoicePlayer speaker) {
		UUID speakerId = idOf(speaker);
		if (speakerId == null) {
			return null;
		}
		UUID listenerId = partners.get(speakerId);
		if (listenerId == null) {
			return null;
		}
		ServerDirectSource existing = sources.get(listenerId);
		if (existing != null) {
			return existing;
		}
		VoicePlayer listener = voiceServer.getPlayerManager().getPlayerById(listenerId).orElse(null);
		if (listener == null) {
			return null;
		}
		ServerDirectSource created = line.createDirectSource(listener, false);
		sources.put(listenerId, created);
		return created;
	}

	/** Averigua el UUID del jugador que habla comparándolo con los jugadores que están en llamada. */
	private UUID idOf(VoicePlayer speaker) {
		for (UUID id : partners.keySet()) {
			VoicePlayer p = voiceServer.getPlayerManager().getPlayerById(id).orElse(null);
			if (p != null && (p == speaker || p.equals(speaker) || p.getInstance().equals(speaker.getInstance()))) {
				return id;
			}
		}
		return null;
	}

	public static void startCall(UUID a, UUID b) {
		if (instance == null) {
			return;
		}
		instance.partners.put(a, b);
		instance.partners.put(b, a);
	}

	public static void endCall(UUID a, UUID b) {
		if (instance == null) {
			return;
		}
		instance.partners.remove(a);
		instance.partners.remove(b);
		for (UUID id : new UUID[]{a, b}) {
			ServerDirectSource s = instance.sources.remove(id);
			if (s != null) {
				s.remove();
			}
		}
	}
}
