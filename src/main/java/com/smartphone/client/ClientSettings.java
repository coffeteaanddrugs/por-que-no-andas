package com.smartphone.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.smartphone.PhoneMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Ajustes del teléfono guardados en config/smartphone-client.json */
public final class ClientSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public int volume = 7;       // 0..10
	public boolean dnd = false;  // no molestar
	public int wallpaper = 0;
	public String wallpaperPhoto = "";  // nombre de una foto de la galería (vacío = no usar)
	public int ringtone = 0;

	private static ClientSettings instance;

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("smartphone-client.json");
	}

	public static ClientSettings get() {
		if (instance == null) {
			instance = new ClientSettings();
			Path p = path();
			if (Files.exists(p)) {
				try (Reader r = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
					ClientSettings loaded = GSON.fromJson(r, ClientSettings.class);
					if (loaded != null) {
						instance = loaded;
					}
				} catch (Exception e) {
					PhoneMod.LOGGER.warn("No se pudieron leer los ajustes del teléfono", e);
				}
			}
		}
		return instance;
	}

	public void save() {
		try (Writer w = Files.newBufferedWriter(path(), StandardCharsets.UTF_8)) {
			GSON.toJson(this, w);
		} catch (Exception e) {
			PhoneMod.LOGGER.warn("No se pudieron guardar los ajustes del teléfono", e);
		}
	}
}
