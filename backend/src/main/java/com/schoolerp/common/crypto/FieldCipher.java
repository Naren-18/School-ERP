package com.schoolerp.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM for individual sensitive columns (Aadhaar). Output is
 * {@code 12-byte random IV || ciphertext+tag}. Key: base64 32 bytes from
 * {@code APP_ENCRYPTION_KEY}.
 *
 * <p>JPA converters are created by Hibernate, not Spring, so the instance is also
 * published through {@link #get()} once Spring has built it.
 */
@Component
public class FieldCipher {

	private static final int IV_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static volatile FieldCipher instance;

	private final SecretKey key;

	private final SecureRandom random = new SecureRandom();

	public FieldCipher(@Value("${app.encryption.key}") String base64Key) {
		byte[] raw;
		try {
			raw = Base64.getDecoder().decode(base64Key);
		}
		catch (IllegalArgumentException ex) {
			throw new IllegalStateException("APP_ENCRYPTION_KEY must be base64", ex);
		}
		if (raw.length != 32) {
			throw new IllegalStateException("APP_ENCRYPTION_KEY must decode to 32 bytes (AES-256)");
		}
		this.key = new SecretKeySpec(raw, "AES");
		instance = this;
	}

	public static FieldCipher get() {
		FieldCipher current = instance;
		if (current == null) {
			throw new IllegalStateException("FieldCipher is not initialised");
		}
		return current;
	}

	public byte[] encrypt(String plain) {
		try {
			byte[] iv = new byte[IV_BYTES];
			random.nextBytes(iv);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
			byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			return ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Encryption failed", ex);
		}
	}

	public String decrypt(byte[] stored) {
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
			byte[] plain = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
			return new String(plain, StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException ex) {
			throw new IllegalStateException("Decryption failed (wrong key or corrupted data)", ex);
		}
	}

}
