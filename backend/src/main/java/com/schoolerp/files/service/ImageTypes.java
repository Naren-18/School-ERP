package com.schoolerp.files.service;

import java.util.Optional;

/** Recognises uploaded images by their first bytes rather than trusting names or headers. */
public final class ImageTypes {

	private ImageTypes() {
	}

	/** "png", "jpg" or "webp", or empty if the bytes are not one of those. */
	public static Optional<String> detect(byte[] b) {
		if (startsWith(b, 0, 0x89, 'P', 'N', 'G')) {
			return Optional.of("png");
		}
		if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
			return Optional.of("jpg");
		}
		if (startsWith(b, 0, 'R', 'I', 'F', 'F') && startsWith(b, 8, 'W', 'E', 'B', 'P')) {
			return Optional.of("webp");
		}
		return Optional.empty();
	}

	public static String contentType(String extension) {
		return switch (extension) {
			case "png" -> "image/png";
			case "jpg" -> "image/jpeg";
			default -> "image/webp";
		};
	}

	private static boolean startsWith(byte[] b, int offset, int... expected) {
		if (b.length < offset + expected.length) {
			return false;
		}
		for (int i = 0; i < expected.length; i++) {
			if ((b[offset + i] & 0xFF) != expected[i]) {
				return false;
			}
		}
		return true;
	}

}
