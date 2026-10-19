package com.schoolerp.exam.entity;

import java.time.OffsetDateTime;
import java.util.Map;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** The class teacher's remark and co-scholastic grades for a student in one exam. */
@Getter
@Setter
@Entity
@Table(name = "report_card_note")
public class ReportCardNote extends TenantEntity {

	@Column(name = "exam_id", nullable = false)
	private Long examId;

	@Column(name = "enrollment_id", nullable = false)
	private Long enrollmentId;

	@Column(name = "teacher_remark", length = 300)
	private String teacherRemark;

	/** Area id (as text) → grade, e.g. {"3": "A"}. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "co_scholastic", nullable = false)
	private Map<String, String> coScholastic;

	@Column(name = "updated_by")
	private Long updatedBy;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

}
