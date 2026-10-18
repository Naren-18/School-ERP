package com.schoolerp.files.service;

import java.util.Optional;

/**
 * Binary file storage. Local disk in dev; S3-compatible (Cloudflare R2) in prod.
 * Keys are relative paths such as {@code schools/12/logo-1718000000.png} and must
 * always start with the owning school's prefix.
 */
public interface FileStorage {

	void put(String key, byte[] content, String contentType);

	Optional<byte[]> get(String key);

	void delete(String key);

}
