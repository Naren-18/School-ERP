package com.schoolerp.common.csv;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV: comma separated, double-quoted fields with "" escapes,
 * CRLF or LF line endings, optional UTF-8 BOM (Excel adds one). Small enough not
 * to justify a library.
 */
public final class Csv {

	private Csv() {
	}

	/** Parses rows; blank lines are skipped. */
	public static List<List<String>> parse(String text) {
		if (text.startsWith("﻿")) {
			text = text.substring(1);
		}
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		StringBuilder field = new StringBuilder();
		boolean quoted = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (quoted) {
				if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
					field.append('"');
					i++;
				}
				else if (c == '"') {
					quoted = false;
				}
				else {
					field.append(c);
				}
			}
			else if (c == '"') {
				quoted = true;
			}
			else if (c == ',') {
				row.add(field.toString());
				field.setLength(0);
			}
			else if (c == '\n' || c == '\r') {
				if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
					i++;
				}
				row.add(field.toString());
				field.setLength(0);
				addIfNotBlank(rows, row);
				row = new ArrayList<>();
			}
			else {
				field.append(c);
			}
		}
		if (field.length() > 0 || !row.isEmpty()) {
			row.add(field.toString());
			addIfNotBlank(rows, row);
		}
		return rows;
	}

	public static String write(List<List<String>> rows) {
		StringBuilder out = new StringBuilder();
		for (List<String> row : rows) {
			for (int i = 0; i < row.size(); i++) {
				if (i > 0) {
					out.append(',');
				}
				String v = row.get(i) == null ? "" : row.get(i);
				boolean quote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
				out.append(quote ? "\"" + v.replace("\"", "\"\"") + "\"" : v);
			}
			out.append("\r\n");
		}
		return out.toString();
	}

	private static void addIfNotBlank(List<List<String>> rows, List<String> row) {
		if (row.stream().anyMatch(v -> !v.isBlank())) {
			rows.add(row);
		}
	}

}
