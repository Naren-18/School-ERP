package com.schoolerp.attendance.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import lombok.RequiredArgsConstructor;

/** School-wide aggregates. Plain SQL; every table scoped by {@code school_id = :schoolId}. */
@Repository
@RequiredArgsConstructor
public class AttendanceReportRepository {

	public record SectionDay(Long sectionId, String sectionName, Long classId, String className, int classOrder,
			int enrolled, int marked, int present, int absent, int leave, int halfDay) {
	}

	private static final String SECTION_DAY = """
			SELECT sec.id AS section_id, sec.name AS section_name, c.id AS class_id, c.name AS class_name,
			       c.display_order, count(e.id) AS enrolled, count(a.id) AS marked,
			       count(a.id) FILTER (WHERE a.status = 'P') AS p, count(a.id) FILTER (WHERE a.status = 'A') AS a,
			       count(a.id) FILTER (WHERE a.status = 'L') AS l, count(a.id) FILTER (WHERE a.status = 'H') AS h
			FROM section sec
			JOIN school_class c ON c.id = sec.class_id AND c.school_id = :schoolId
			LEFT JOIN enrollment e ON e.section_id = sec.id AND e.school_id = :schoolId
			     AND e.joined_on <= :date AND (e.left_on IS NULL OR e.left_on >= :date)
			LEFT JOIN attendance a ON a.enrollment_id = e.id AND a.school_id = :schoolId AND a.att_date = :date
			WHERE sec.school_id = :schoolId AND sec.academic_year_id = :yearId
			GROUP BY sec.id, sec.name, c.id, c.name, c.display_order
			ORDER BY c.display_order, c.name, sec.name
			""";

	private final NamedParameterJdbcTemplate jdbc;

	/** One row per section of the year: who was enrolled on the date and how they were marked. */
	public List<SectionDay> sectionsOn(Long schoolId, Long yearId, LocalDate date) {
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("schoolId", schoolId)
			.addValue("yearId", yearId)
			.addValue("date", date);
		return jdbc.query(SECTION_DAY, params,
				(rs, i) -> new SectionDay(rs.getLong("section_id"), rs.getString("section_name"), rs.getLong("class_id"),
						rs.getString("class_name"), rs.getInt("display_order"), rs.getInt("enrolled"), rs.getInt("marked"),
						rs.getInt("p"), rs.getInt("a"), rs.getInt("l"), rs.getInt("h")));
	}

}
