package com.schoolerp.academic.entity;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Who teaches a subject to a section. One teacher per section + subject. */
@Getter
@Setter
@Entity
@Table(name = "teacher_assignment")
public class TeacherAssignment extends TenantEntity {

	@Column(name = "section_id", nullable = false)
	private Long sectionId;

	@Column(name = "subject_id", nullable = false)
	private Long subjectId;

	@Column(name = "teacher_id", nullable = false)
	private Long teacherId;

}
