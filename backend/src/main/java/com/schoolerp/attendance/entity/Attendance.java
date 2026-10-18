package com.schoolerp.attendance.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import com.schoolerp.common.entity.TenantEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One student's attendance on one day (one row per enrollment per date). */
@Getter
@Setter
@Entity
@Table(name = "attendance")
public class Attendance extends TenantEntity {

	/** Present, Absent, Leave, Half-day. */
	public enum Status { P, A, L, H }

	@Column(name = "enrollment_id", nullable = false)
	private Long enrollmentId;

	@Column(name = "att_date", nullable = false)
	private LocalDate date;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 1, columnDefinition = "bpchar(1)")
	private Status status;

	@Column(name = "marked_by")
	private Long markedBy;

	@Column(name = "marked_at", nullable = false)
	private OffsetDateTime markedAt;

}
