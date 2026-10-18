package com.schoolerp.academic.service;

import java.util.Optional;

import com.schoolerp.academic.entity.Section;

/**
 * Lets modules that build on sections (students, attendance, ...) veto a
 * section's deletion with a clear message. Implemented by those modules, so
 * the academic module does not depend on them.
 */
public interface SectionUsageCheck {

	/** Why the section cannot be deleted, or empty if this module has no objection. */
	Optional<String> blockingReason(Section section, String sectionLabel);

}
