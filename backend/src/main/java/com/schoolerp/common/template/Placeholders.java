package com.schoolerp.common.template;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.schoolerp.common.pdf.HtmlPdf;

/**
 * School-editable HTML templates with two kinds of placeholders:
 * <ul>
 * <li>{@code {{student.fullName}}}: a value, HTML-escaped;</li>
 * <li>{@code {{{marks.table}}}}: a block of HTML built by the application
 * (tables, the logo). Only keys the caller lists as blocks are allowed.</li>
 * </ul>
 * No loops or conditions: anything repeated is a block built in code.
 */
public final class Placeholders {

	private static final Pattern TOKEN = Pattern.compile("\\{\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}}|\\{\\{\\s*([A-Za-z0-9_.]+)\\s*}}");

	/** Markup that has no place in a template: scripts, DTDs and entity tricks, processing instructions. */
	private static final Pattern FORBIDDEN = Pattern.compile("(?i)<\\s*script|<!DOCTYPE|<!ENTITY|<\\?|<\\s*iframe|<\\s*object|<\\s*embed|<\\s*link");

	private Placeholders() {
	}

	/** Fills the template. Unknown keys become empty (templates are checked with {@link #problems} when saved). */
	public static String render(String template, Map<String, String> values, Map<String, String> blocks) {
		Matcher m = TOKEN.matcher(template);
		StringBuilder out = new StringBuilder(template.length() + 1024);
		while (m.find()) {
			String replacement = m.group(1) != null ? blocks.getOrDefault(m.group(1), "")
					: HtmlPdf.esc(values.getOrDefault(m.group(2), ""));
			m.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		m.appendTail(out);
		return out.toString();
	}

	/** What is wrong with a template, for the editor; empty when it can be saved. */
	public static List<String> problems(String template, Set<String> valueKeys, Set<String> blockKeys) {
		List<String> problems = new ArrayList<>();
		Matcher forbidden = FORBIDDEN.matcher(template);
		if (forbidden.find()) {
			problems.add("Remove \"" + forbidden.group() + "…\": scripts, embedded objects, links and DOCTYPEs are not allowed");
		}
		Set<String> unknown = new LinkedHashSet<>();
		Matcher m = TOKEN.matcher(template);
		while (m.find()) {
			if (m.group(1) != null && !blockKeys.contains(m.group(1))) {
				unknown.add(valueKeys.contains(m.group(1)) ? "{{{" + m.group(1) + "}}} (use two braces for this value)"
						: "{{{" + m.group(1) + "}}}");
			}
			if (m.group(2) != null && !valueKeys.contains(m.group(2))) {
				unknown.add(blockKeys.contains(m.group(2)) ? "{{" + m.group(2) + "}} (use three braces for this block)"
						: "{{" + m.group(2) + "}}");
			}
		}
		if (!unknown.isEmpty()) {
			problems.add("Unknown placeholder" + (unknown.size() == 1 ? ": " : "s: ") + String.join(", ", unknown));
		}
		return problems;
	}

}
