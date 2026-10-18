package com.schoolerp.common.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Base64;
import java.util.Locale;

import org.springframework.web.util.HtmlUtils;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

/**
 * XHTML → PDF with the embedded DejaVu Sans font (it has the ₹ glyph; the
 * built-in PDF fonts do not). Shared by receipts, report cards and certificates.
 */
public final class HtmlPdf {

	/** From the jasperreports-fonts jar (runtime dependency). */
	public static final String FONT_REGULAR = "/net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf";

	public static final String FONT_BOLD = "/net/sf/jasperreports/fonts/dejavu/DejaVuSans-Bold.ttf";

	/** Use this as the CSS font-family. */
	public static final String FONT_FAMILY = "DejaVu Sans";

	private HtmlPdf() {
	}

	public static byte[] render(String xhtml) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFont(() -> font(FONT_REGULAR), FONT_FAMILY, 400, PdfRendererBuilder.FontStyle.NORMAL, true);
			builder.useFont(() -> font(FONT_BOLD), FONT_FAMILY, 700, PdfRendererBuilder.FontStyle.NORMAL, true);
			builder.withHtmlContent(xhtml, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not create the PDF", ex);
		}
	}

	/** Escapes text for the XHTML template. */
	public static String esc(String s) {
		return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
	}

	/** ₹1,23,456.00 (Indian grouping), escaped. */
	public static String inr(BigDecimal amount) {
		return esc(NumberFormat.getCurrencyInstance(Locale.of("en", "IN")).format(amount));
	}

	/** An {@code <img>} with the image inlined as a data URI, or "" when there is no image. */
	public static String img(byte[] content, String contentType, String cssClass) {
		if (content == null) {
			return "";
		}
		return "<img class=\"" + cssClass + "\" src=\"data:" + contentType + ";base64,"
				+ Base64.getEncoder().encodeToString(content) + "\"/>";
	}

	private static InputStream font(String path) {
		InputStream in = HtmlPdf.class.getResourceAsStream(path);
		if (in == null) {
			throw new IllegalStateException("Font " + path + " is missing from the classpath (jasperreports-fonts)");
		}
		return in;
	}

}
