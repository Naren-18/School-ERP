package com.schoolerp.common.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Stores a String column as AES-GCM ciphertext (BYTEA). Apply with {@code @Convert}. */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

	@Override
	public byte[] convertToDatabaseColumn(String value) {
		return value == null ? null : FieldCipher.get().encrypt(value);
	}

	@Override
	public String convertToEntityAttribute(byte[] stored) {
		return stored == null ? null : FieldCipher.get().decrypt(stored);
	}

}
