package com.schoolerp.student.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.schoolerp.student.dto.StudentDto;
import com.schoolerp.student.entity.Student;

import lombok.RequiredArgsConstructor;

/**
 * Student list search. Plain SQL because it joins five tables with optional
 * filters; every table is scoped by {@code school_id = :schoolId} explicitly.
 */
@Repository
@RequiredArgsConstructor
public class StudentSearchRepository {

	public record Criteria(Long schoolId, Long yearId, String q, Long classId, Long sectionId, Student.Status status,
			Set<Long> onlySectionIds, int page, int size) {
	}

	public record Result(List<StudentDto.ListItem> rows, long total) {
	}

	private static final String JOINS = """
			FROM student s
			LEFT JOIN enrollment e ON e.student_id = s.id AND e.school_id = :schoolId AND e.academic_year_id = :yearId
			LEFT JOIN section sec ON sec.id = e.section_id AND sec.school_id = :schoolId
			LEFT JOIN school_class c ON c.id = sec.class_id AND c.school_id = :schoolId
			""";

	private static final String WHERE = " WHERE s.school_id = :schoolId";

	/** Leading newline matters: text blocks strip indentation, and this is appended to the WHERE clause. */
	private static final String ORDER = """

			ORDER BY c.display_order NULLS LAST, sec.name NULLS LAST, e.roll_no NULLS LAST, lower(s.first_name), s.id
			LIMIT :limit OFFSET :offset""";

	private static final String SELECT = """
			SELECT s.id, s.admission_no, s.first_name, s.last_name, s.gender, s.date_of_birth, s.status,
			       s.photo_url IS NOT NULL AS has_photo, e.id AS enrollment_id, e.section_id, e.roll_no,
			       c.name AS class_name, sec.name AS section_name, g.full_name AS guardian_name, g.phone AS guardian_phone
			""";

	private static final String GUARDIAN = """
			LEFT JOIN LATERAL (
			  SELECT full_name, phone FROM guardian
			  WHERE student_id = s.id AND school_id = :schoolId
			  ORDER BY is_primary DESC, id LIMIT 1) g ON true
			""";

	private static final RowMapper<StudentDto.ListItem> ROW = (rs, i) -> {
		String last = rs.getString("last_name");
		String first = rs.getString("first_name");
		return new StudentDto.ListItem(rs.getLong("id"), rs.getString("admission_no"),
				last == null || last.isBlank() ? first : first + " " + last, Student.Gender.valueOf(rs.getString("gender")),
				rs.getObject("date_of_birth", LocalDate.class), Student.Status.valueOf(rs.getString("status")),
				rs.getObject("enrollment_id", Long.class), rs.getObject("section_id", Long.class),
				rs.getString("class_name"), rs.getString("section_name"), rs.getObject("roll_no", Integer.class),
				rs.getString("guardian_name"), rs.getString("guardian_phone"), rs.getBoolean("has_photo"));
	};

	private final NamedParameterJdbcTemplate jdbc;

	public Result search(Criteria c) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("schoolId", c.schoolId())
			.addValue("yearId", c.yearId() == null ? -1L : c.yearId());
		StringBuilder where = new StringBuilder();
		if (c.status() != null) {
			where.append(" AND s.status = :status");
			params.addValue("status", c.status().name());
		}
		if (c.classId() != null) {
			where.append(" AND sec.class_id = :classId");
			params.addValue("classId", c.classId());
		}
		if (c.sectionId() != null) {
			where.append(" AND e.section_id = :sectionId");
			params.addValue("sectionId", c.sectionId());
		}
		if (c.onlySectionIds() != null) {
			if (c.onlySectionIds().isEmpty()) {
				return new Result(List.of(), 0);
			}
			where.append(" AND e.section_id IN (:onlySections)");
			params.addValue("onlySections", c.onlySectionIds());
		}
		if (c.q() != null && !c.q().isBlank()) {
			String q = c.q().trim().toLowerCase(Locale.ROOT);
			String digits = q.replaceAll("\\D", "");
			where.append(" ").append("""
					 AND (lower(s.first_name || ' ' || coalesce(s.last_name, '')) LIKE :q
					      OR lower(s.admission_no) LIKE :q""");
			params.addValue("q", "%" + q + "%");
			if (digits.length() >= 4) {
				where.append(" ").append("""
						 OR EXISTS (SELECT 1 FROM guardian g2
						            WHERE g2.student_id = s.id AND g2.school_id = :schoolId AND g2.phone LIKE :phone)""");
				params.addValue("phone", "%" + digits + "%");
			}
			where.append(")");
		}

		Long total = jdbc.queryForObject("SELECT count(*) " + JOINS + WHERE + where, params, Long.class);
		params.addValue("limit", c.size()).addValue("offset", (long) c.page() * c.size());
		List<StudentDto.ListItem> rows = jdbc.query(SELECT + JOINS + GUARDIAN + WHERE + where + ORDER, params, ROW);
		return new Result(rows, total == null ? 0 : total);
	}

}
