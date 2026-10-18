package com.schoolerp.files.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Stores files under {@code app.files.local-dir}. Used in dev until the R2 implementation (phase 10). */
@Service
public class LocalFileStorage implements FileStorage {

	private final Path root;

	public LocalFileStorage(@Value("${app.files.local-dir}") String rootDir) {
		this.root = Path.of(rootDir).toAbsolutePath().normalize();
	}

	@Override
	public void put(String key, byte[] content, String contentType) {
		Path target = resolve(key);
		try {
			Files.createDirectories(target.getParent());
			Files.write(target, content);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not store " + key, ex);
		}
	}

	@Override
	public Optional<byte[]> get(String key) {
		try {
			return Optional.of(Files.readAllBytes(resolve(key)));
		}
		catch (NoSuchFileException ex) {
			return Optional.empty();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not read " + key, ex);
		}
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not delete " + key, ex);
		}
	}

	private Path resolve(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root) || path.equals(root)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return path;
	}

}
