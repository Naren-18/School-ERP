package com.schoolerp.certificate.entity;

/** The kinds of school-editable templates (stored in {@code certificate_template.type}). */
public enum TemplateType {

	BONAFIDE("Bonafide certificate", "BON"),

	TC("Transfer certificate", "TC"),

	/** One page per student; used by the exams module. Never issued as a certificate. */
	REPORT_CARD("Report card", null);

	private final String title;

	/** Short code in serial numbers, e.g. SSV/TC/0001; null when not issued. */
	private final String serialCode;

	TemplateType(String title, String serialCode) {
		this.title = title;
		this.serialCode = serialCode;
	}

	public String title() {
		return title;
	}

	public String serialCode() {
		return serialCode;
	}

	public boolean isCertificate() {
		return serialCode != null;
	}

	/** Classpath resource with the built-in default. */
	public String defaultResource() {
		return "/pdf-templates/" + name().toLowerCase().replace('_', '-') + ".html";
	}

}
