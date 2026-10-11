package com.smartphone.client;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** Las fotos del teléfono viven en la carpeta phone_gallery dentro de la carpeta de Minecraft. */
public final class GalleryStore {
	private GalleryStore() {
	}

	public static Path dir() {
		Path d = FabricLoader.getInstance().getGameDir().resolve("phone_gallery");
		try {
			Files.createDirectories(d);
		} catch (IOException ignored) {
		}
		return d;
	}

	/** Fotos ordenadas de la más nueva a la más vieja. */
	public static List<Path> list() {
		List<Path> out = new ArrayList<>();
		try (Stream<Path> s = Files.list(dir())) {
			s.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png")).forEach(out::add);
		} catch (IOException ignored) {
		}
		out.sort(Comparator.comparingLong((Path p) -> {
			try {
				return Files.getLastModifiedTime(p).toMillis();
			} catch (IOException e) {
				return 0L;
			}
		}).reversed());
		return out;
	}
}
