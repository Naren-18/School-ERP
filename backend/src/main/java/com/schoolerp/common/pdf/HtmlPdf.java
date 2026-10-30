package com.schoolerp.common.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.Base64;
import java.util.function.BiPredicate;

import org.springframework.web.util.HtmlUtils;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.outputdevice.helper.ExternalResourceType;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.schoolerp.common.money.Rupees;

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

	/** The document could not be laid out, usually because a school-edited template is not well-formed XHTML. */
	public static class InvalidDocumentException extends RuntimeException {

		InvalidDocumentException(Throwable cause) {
			super(cause.getMessage(), cause);
		}

	}

	/**
	 * Renders the document. Only inline {@code data:} resources are loaded:
	 * templates are edited by schools, so they must not make the server fetch
	 * URLs or read local files.
	 */
	public static byte[] render(String xhtml) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFont(() -> font(FONT_REGULAR), FONT_FAMILY, 400, PdfRendererBuilder.FontStyle.NORMAL, true);
			builder.useFont(() -> font(FONT_BOLD), FONT_FAMILY, 700, PdfRendererBuilder.FontStyle.NORMAL, true);
			BiPredicate<String, ExternalResourceType> inlineOnly = (uri, type) -> uri != null && uri.startsWith("data:");
			builder.useExternalResourceAccessControl(inlineOnly, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
			builder.useExternalResourceAccessControl(inlineOnly, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
			builder.withHtmlContent(xhtml, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Could not create the PDF", ex);
		}
		catch (RuntimeException ex) {
			throw new InvalidDocumentException(ex);
		}
	}

	/** Escapes text for the XHTML template. */
	public static String esc(String s) {
		return s == null ? "" : HtmlUtils.htmlEscape(s, "UTF-8");
	}

	/** ₹1,23,456.00 (Indian grouping), escaped. */
	public static String inr(BigDecimal amount) {
		return esc(Rupees.withSymbol(amount));
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
