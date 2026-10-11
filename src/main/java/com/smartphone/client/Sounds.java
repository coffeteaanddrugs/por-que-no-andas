package com.smartphone.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;

/** Sonidos del teléfono (usan sonidos de Minecraft, respetan el volumen de Ajustes). */
public final class Sounds {
	private Sounds() {
	}

	private static void play(RegistryEntry<SoundEvent> sound, float pitch, float volume) {
		float v = volume * ClientSettings.get().volume / 10f;
		if (v <= 0f) {
			return;
		}
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(sound.value(), pitch, v));
	}

	public static void click() {
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.5f));
	}

	public static void message() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_CHIME, 1.4f, 1.0f);
	}

	public static void ring(boolean second) {
		play(SoundEvents.BLOCK_NOTE_BLOCK_BELL, second ? 1.26f : 1.0f, 1.0f);
	}

	public static void ringBack() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_BELL, 0.6f, 0.5f);
	}

	public static final String[] RINGTONES = {"Clásico", "Campana", "Alegre", "Suave"};

	/** Cada tono: pares {tick, tono}. Se repite cada 50 ticks. */
	private static final float[][][] PATTERNS = {
			{{0, 1.0f}, {4, 1.26f}, {8, 1.0f}, {12, 1.26f}},
			{{0, 0.8f}, {10, 1.0f}, {20, 1.5f}},
			{{0, 1.0f}, {3, 1.26f}, {6, 1.5f}, {9, 2.0f}, {18, 1.5f}},
			{{0, 0.7f}, {8, 0.9f}}
	};

	/** Suena la nota del tono elegido que toque en este tick (t = 0..49). */
	public static void ringtoneStep(int t) {
		ringtoneStepFor(ClientSettings.get().ringtone, t);
	}

	/** Prueba un tono desde Ajustes. */
	public static void preview(int idx) {
		for (int t = 0; t < 25; t++) {
			final int tt = t;
			new Thread(() -> {
				try {
					Thread.sleep(tt * 50L);
				} catch (InterruptedException ignored) {
				}
				MinecraftClient.getInstance().execute(() -> ringtoneStepFor(idx, tt));
			}).start();
		}
	}

	private static void ringtoneStepFor(int idx, int t) {
		int i = Math.floorMod(idx, PATTERNS.length);
		for (float[] step : PATTERNS[i]) {
			if ((int) step[0] == t) {
				play(i == 1 ? SoundEvents.BLOCK_NOTE_BLOCK_BELL : SoundEvents.BLOCK_NOTE_BLOCK_CHIME, step[1], 1.0f);
			}
		}
	}

	/** Audio de "el usuario no está conectado o no tiene cobertura" (assets/smartphone/sounds/unavailable.ogg). */
	public static void unavailable() {
		SoundEvent ev = SoundEvent.of(Identifier.of("smartphone", "unavailable"));
		MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(ev, 1.0f, 1.0f));
	}

	public static void shutter() {
		play(SoundEvents.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.0f);
	}
}
