package com.schoolerp.certificate.service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.schoolerp.common.pdf.HtmlPdf;

/**
 * Wraps filled template bodies into one XHTML document with the shared page
 * setup and CSS classes templates can use. A {@code <style>} block written in a
 * template is moved into the head.
 */
public final class TemplateDocument {

	private static final Pattern STYLE = Pattern.compile("(?is)<style[^>]*>(.*?)</style>");

	private TemplateDocument() {
	}

	/** One page per body (page breaks between them). */
	public static String wrap(List<String> pageBodies, String primaryColor) {
		StringBuilder extraCss = new StringBuilder();
		StringBuilder pages = new StringBuilder();
		for (int i = 0; i < pageBodies.size(); i++) {
			Matcher m = STYLE.matcher(pageBodies.get(i));
			StringBuilder body = new StringBuilder();
			while (m.find()) {
				if (i == 0) {
					extraCss.append(m.group(1)).append('\n');
				}
				m.appendReplacement(body, "");
			}
			m.appendTail(body);
			pages.append("<div class=\"page").append(i < pageBodies.size() - 1 ? " break" : "").append("\">")
				.append(body).append("</div>\n");
		}
		String color = primaryColor != null && primaryColor.matches("#[0-9a-fA-F]{6}") ? primaryColor : "#1e40af";
		return """
				<!DOCTYPE html>
				<html><head><meta charset="UTF-8"/><style>
				@page { size: A4; margin: 14mm; }
				body { font-family: '%s'; font-size: 11pt; color: #111; line-height: 1.45; }
				.break { page-break-after: always; }
				.center { text-align: center; } .right { text-align: right; } .muted { color: #555; }
				.small { font-size: 9pt; } .big { font-size: 13pt; }
				.logo { height: 60px; }
				.header { text-align: center; border-bottom: 3px solid %s; padding-bottom: 8px; margin-bottom: 14px; }
				.header h1 { font-size: 18pt; margin: 4px 0 0; }
				.title { text-align: center; font-weight: 700; font-size: 14pt; letter-spacing: 1px; margin: 14px 0; color: %s; }
				.serial { display: inline-block; }
				.border { border: 2px solid %s; padding: 18px 22px; }
				table { width: 100%%; border-collapse: collapse; }
				table.grid th, table.grid td { border: 1px solid #bbb; padding: 5px 7px; vertical-align: top; }
				table.grid th { background: #f3f4f6; text-align: left; }
				table.fields td { padding: 4px 6px; vertical-align: top; }
				table.fields td.label { width: 42%%; color: #333; }
				.num { text-align: right; } .c { text-align: center; } .fail { color: #b91c1c; }
				.total td { font-weight: 700; }
				.sign { margin-top: 56px; }
				.sign td { text-align: center; border-top: 1px solid #777; padding-top: 4px; }
				.sign td.gap { border-top: none; width: 6%%; }
				%s
				</style></head><body>%s</body></html>
				""".formatted(HtmlPdf.FONT_FAMILY, color, color, color, extraCss, pages);
	}

}
