package com.schoolerp.certificate.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.service.AuditService;
import com.schoolerp.certificate.dto.TemplateDto;
import com.schoolerp.certificate.entity.CertificateTemplate;
import com.schoolerp.certificate.entity.TemplateType;
import com.schoolerp.certificate.repository.CertificateTemplateRepository;
import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.pdf.HtmlPdf;
import com.schoolerp.common.template.Placeholders;
import com.schoolerp.tenant.TenantContext;

import lombok.RequiredArgsConstructor;

/**
 * School-editable templates for certificates and report cards. A school
 * without its own version gets the built-in default, so nothing has to be
 * seeded and "reset" simply deletes the school's version.
 */
@Service
@RequiredArgsConstructor
public class TemplateService {

	/** Plenty for a page of HTML; keeps a pasted image or document out. */
	public static final int MAX_LENGTH = 50_000;

	private final CertificateTemplateRepository templates;

	private final AuditService audit;

	private final Clock clock;

	@Transactional(readOnly = true)
	public TemplateDto.View get(TemplateType type) {
		Optional<CertificateTemplate> own = own(type);
		return view(type, own.map(CertificateTemplate::getHtmlTemplate).orElseGet(() -> defaultHtml(type)), own.isPresent(),
				own.map(CertificateTemplate::getUpdatedAt).orElse(null));
	}

	/** The template in force: the school's own, or the default. */
	@Transactional(readOnly = true)
	public String activeHtml(TemplateType type) {
		return own(type).map(CertificateTemplate::getHtmlTemplate).orElseGet(() -> defaultHtml(type));
	}

	/**
	 * Saves the school's version after checking its placeholders and that it
	 * renders: {@code renderSample} fills it with sample data and makes the PDF.
	 */
	@Transactional
	public TemplateDto.View save(TemplateType type, String html, Function<String, byte[]> renderSample) {
		check(type, html, renderSample);
		CertificateTemplate t = own(type).orElseGet(() -> {
			CertificateTemplate created = new CertificateTemplate();
			created.setType(type);
			return created;
		});
		t.setHtmlTemplate(html);
		t.setUpdatedAt(OffsetDateTime.now(clock));
		templates.save(t);
		audit.log(AuditAction.UPDATE, "template", t.getId(), Map.of("type", type.name()));
		return view(type, html, true, t.getUpdatedAt());
	}

	@Transactional
	public TemplateDto.View reset(TemplateType type) {
		own(type).ifPresent(t -> {
			templates.delete(t);
			audit.log(AuditAction.DELETE, "template", t.getId(), Map.of("type", type.name(), "change", "reset to default"));
		});
		return view(type, defaultHtml(type), false, null);
	}

	/** Renders a draft for the live preview, with the same checks as saving. */
	public byte[] preview(TemplateType type, String html, Function<String, byte[]> renderSample) {
		return check(type, html, renderSample);
	}

	private byte[] check(TemplateType type, String html, Function<String, byte[]> renderSample) {
		if (html == null || html.isBlank()) {
			throw new BadRequestException("The template is empty");
		}
		if (html.length() > MAX_LENGTH) {
			throw new BadRequestException("The template is too long (at most " + MAX_LENGTH + " characters)");
		}
		TemplateFields.Fields fields = TemplateFields.of(type);
		List<String> problems = Placeholders.problems(html, fields.values().keySet(), fields.blocks().keySet());
		if (!problems.isEmpty()) {
			throw new BadRequestException(String.join(". ", problems));
		}
		try {
			return renderSample.apply(html);
		}
		catch (HtmlPdf.InvalidDocumentException ex) {
			throw new BadRequestException("The template is not valid XHTML (every tag must be closed, e.g. <br/>): "
					+ shorten(ex.getMessage()));
		}
	}

	private Optional<CertificateTemplate> own(TemplateType type) {
		return templates.findBySchoolIdAndTypeAndActiveTrue(TenantContext.require(), type);
	}

	private static TemplateDto.View view(TemplateType type, String html, boolean custom, OffsetDateTime updatedAt) {
		TemplateFields.Fields fields = TemplateFields.of(type);
		return new TemplateDto.View(type, type.title(), html, custom, updatedAt, fields.values(), fields.blocks());
	}

	public static String defaultHtml(TemplateType type) {
		try (InputStream in = TemplateService.class.getResourceAsStream(type.defaultResource())) {
			if (in == null) {
				throw new IllegalStateException("Missing default template " + type.defaultResource());
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static String shorten(String message) {
		if (message == null) {
			return "unknown error";
		}
		String m = message.replaceAll("^[\\w.]+Exception: ", "");
		return m.length() > 200 ? m.substring(0, 200) + "…" : m;
	}

}
