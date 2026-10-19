package com.schoolerp.dashboard.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.schoolerp.attendance.dto.AttendanceDto;
import com.schoolerp.dashboard.dto.DashboardDto;

import lombok.RequiredArgsConstructor;

/**
 * Read-only queries behind the dashboard, one per widget. Every table in every
 * query is scoped by {@code school_id = :schoolId} (native SQL bypasses the
 * Hibernate tenant filter).
 */
@Repository
@RequiredArgsConstructor
public class DashboardRepository {

	private final NamedParameterJdbcTemplate jdbc;

	/** Active students per class in the year, in class order (classes without students included). */
	public List<DashboardDto.ClassCount> studentsByClass(Long schoolId, Long yearId) {
		return jdbc.query("""
				SELECT c.id, c.name, count(e.id) AS students,
				       count(e.id) FILTER (WHERE s.gender = 'MALE') AS boys,
				       count(e.id) FILTER (WHERE s.gender = 'FEMALE') AS girls
				FROM school_class c
				LEFT JOIN section sec ON sec.class_id = c.id AND sec.school_id = :schoolId AND sec.academic_year_id = :yearId
				LEFT JOIN enrollment e ON e.section_id = sec.id AND e.school_id = :schoolId AND e.status = 'ACTIVE'
				LEFT JOIN student s ON s.id = e.student_id AND s.school_id = :schoolId
				WHERE c.school_id = :schoolId
				GROUP BY c.id, c.name, c.display_order
				ORDER BY c.display_order, c.name
				""", params(schoolId).addValue("yearId", yearId),
				(rs, i) -> new DashboardDto.ClassCount(rs.getLong("id"), rs.getString("name"), rs.getInt("students"),
						rs.getInt("boys"), rs.getInt("girls")));
	}

	/** Attendance per class between the dates, in class order; classes with nothing marked are left out. */
	public List<DashboardDto.ClassPercent> attendanceByClass(Long schoolId, LocalDate from, LocalDate to) {
		return jdbc.query("""
				SELECT c.id, c.name,
				       count(*) FILTER (WHERE a.status = 'P') AS p, count(*) FILTER (WHERE a.status = 'A') AS a,
				       count(*) FILTER (WHERE a.status = 'L') AS l, count(*) FILTER (WHERE a.status = 'H') AS h
				FROM attendance a
				JOIN enrollment e ON e.id = a.enrollment_id AND e.school_id = :schoolId
				JOIN section sec ON sec.id = e.section_id AND sec.school_id = :schoolId
				JOIN school_class c ON c.id = sec.class_id AND c.school_id = :schoolId
				WHERE a.school_id = :schoolId AND a.att_date BETWEEN :from AND :to
				GROUP BY c.id, c.name, c.display_order
				ORDER BY c.display_order, c.name
				""", params(schoolId).addValue("from", from).addValue("to", to), (rs, i) -> {
			AttendanceDto.Counts c = AttendanceDto.Counts.of(rs.getInt("p"), rs.getInt("a"), rs.getInt("l"), rs.getInt("h"), 0);
			return new DashboardDto.ClassPercent(rs.getLong("id"), rs.getString("name"),
					c.present() + c.absent() + c.leave() + c.halfDay(), c.percentage());
		});
	}

	/** Attendance per day that has any, between the dates, oldest first. */
	public List<DashboardDto.DayPercent> attendanceByDay(Long schoolId, LocalDate from, LocalDate to) {
		return jdbc.query("""
				SELECT att_date,
				       count(*) FILTER (WHERE status = 'P') AS p, count(*) FILTER (WHERE status = 'A') AS a,
				       count(*) FILTER (WHERE status = 'L') AS l, count(*) FILTER (WHERE status = 'H') AS h
				FROM attendance
				WHERE school_id = :schoolId AND att_date BETWEEN :from AND :to
				GROUP BY att_date
				ORDER BY att_date
				""", params(schoolId).addValue("from", from).addValue("to", to), (rs, i) -> {
			AttendanceDto.Counts c = AttendanceDto.Counts.of(rs.getInt("p"), rs.getInt("a"), rs.getInt("l"), rs.getInt("h"), 0);
			return new DashboardDto.DayPercent(rs.getObject("att_date", LocalDate.class),
					c.present() + c.absent() + c.leave() + c.halfDay(), c.percentage());
		});
	}

	/** Receipts (not cancelled) per day between the dates; days without receipts are absent. */
	public Map<LocalDate, BigDecimal> collectedByDay(Long schoolId, LocalDate from, LocalDate to) {
		Map<LocalDate, BigDecimal> out = new HashMap<>();
		jdbc.query("""
				SELECT paid_on, sum(total_amount) AS amount
				FROM fee_receipt
				WHERE school_id = :schoolId AND NOT cancelled AND paid_on BETWEEN :from AND :to
				GROUP BY paid_on
				""", params(schoolId).addValue("from", from).addValue("to", to),
				rs -> {
					out.put(rs.getObject("paid_on", LocalDate.class), rs.getBigDecimal("amount"));
				});
		return out;
	}

	public BigDecimal collectedBetween(Long schoolId, LocalDate from, LocalDate to) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(total_amount), 0) FROM fee_receipt
				WHERE school_id = :schoolId AND NOT cancelled AND paid_on BETWEEN :from AND :to
				""", params(schoolId).addValue("from", from).addValue("to", to), BigDecimal.class);
	}

	/** The year's receipts (not cancelled) per month of {@code paid_on}; months without receipts are absent. */
	public Map<LocalDate, BigDecimal> collectedByMonth(Long schoolId, Long yearId) {
		Map<LocalDate, BigDecimal> out = new HashMap<>();
		jdbc.query("""
				SELECT date_trunc('month', paid_on)::date AS month, sum(total_amount) AS amount
				FROM fee_receipt
				WHERE school_id = :schoolId AND academic_year_id = :yearId AND NOT cancelled
				GROUP BY 1
				""", params(schoolId).addValue("yearId", yearId), rs -> {
			out.put(rs.getObject("month", LocalDate.class), rs.getBigDecimal("amount"));
		});
		return out;
	}

	/** The year's receipts (not cancelled) per payment mode, largest first; the label is filled in by the service. */
	public List<DashboardDto.PartAmount> collectedByMode(Long schoolId, Long yearId) {
		return jdbc.query("""
				SELECT payment_mode, sum(total_amount) AS amount
				FROM fee_receipt
				WHERE school_id = :schoolId AND academic_year_id = :yearId AND NOT cancelled
				GROUP BY payment_mode
				ORDER BY amount DESC
				""", params(schoolId).addValue("yearId", yearId), (rs, i) -> new DashboardDto.PartAmount(
				rs.getString("payment_mode"), rs.getString("payment_mode"), rs.getBigDecimal("amount")));
	}

	/** The year's receipts (not cancelled) per fee head, from the receipt lines, largest first. */
	public List<DashboardDto.PartAmount> collectedByHead(Long schoolId, Long yearId) {
		return jdbc.query("""
				SELECT h.id, h.name, sum(l.amount) AS amount
				FROM fee_receipt r
				JOIN fee_receipt_line l ON l.receipt_id = r.id
				JOIN student_fee_due d ON d.id = l.student_fee_due_id AND d.school_id = :schoolId
				JOIN fee_installment i ON i.id = d.fee_installment_id AND i.school_id = :schoolId
				JOIN fee_head h ON h.id = i.fee_head_id AND h.school_id = :schoolId
				WHERE r.school_id = :schoolId AND r.academic_year_id = :yearId AND NOT r.cancelled
				GROUP BY h.id, h.name
				ORDER BY amount DESC, h.name
				""", params(schoolId).addValue("yearId", yearId), (rs, i) -> new DashboardDto.PartAmount(
				String.valueOf(rs.getLong("id")), rs.getString("name"), rs.getBigDecimal("amount")));
	}

	/** Exam id and name of the year's latest published exam (by its dates), if any. */
	public Optional<Map.Entry<Long, String>> latestPublishedExam(Long schoolId, Long yearId) {
		return jdbc.query("""
				SELECT id, name FROM exam
				WHERE school_id = :schoolId AND academic_year_id = :yearId AND is_published
				ORDER BY coalesce(end_date, start_date) DESC NULLS LAST, display_order DESC, id DESC
				LIMIT 1
				""", params(schoolId).addValue("yearId", yearId),
				(rs, i) -> Map.entry(rs.getLong("id"), rs.getString("name"))).stream().findFirst();
	}

	/**
	 * Per subject of the exam, over every class: average % of the students present, and the share that
	 * passed (pass marks reached, or any marks when the paper has none; absent fails), as the results do.
	 */
	public List<DashboardDto.SubjectResult> subjectResults(Long schoolId, Long examId) {
		return jdbc.query("""
				SELECT sub.id, sub.name, count(*) AS students,
				       round(avg(m.marks_obtained * 100 / es.max_marks) FILTER (WHERE NOT m.is_absent), 1)::float8 AS average,
				       round(100.0 * count(*) FILTER (WHERE NOT m.is_absent
				             AND (es.pass_marks IS NULL OR m.marks_obtained >= es.pass_marks)) / count(*), 1)::float8 AS passed
				FROM mark m
				JOIN exam_subject es ON es.id = m.exam_subject_id AND es.school_id = :schoolId
				JOIN subject sub ON sub.id = es.subject_id AND sub.school_id = :schoolId
				WHERE m.school_id = :schoolId AND es.exam_id = :examId
				  AND (m.is_absent OR m.marks_obtained IS NOT NULL)
				GROUP BY sub.id, sub.name
				ORDER BY min(es.id)
				""", params(schoolId).addValue("examId", examId), (rs, i) -> new DashboardDto.SubjectResult(
				rs.getLong("id"), rs.getString("name"), rs.getInt("students"), rs.getObject("average", Double.class),
				rs.getObject("passed", Double.class)));
	}

	public record DuesTotal(BigDecimal paid, BigDecimal outstanding, BigDecimal overdue) {
	}

	/** Paid and left to pay on the year's dues, and the part past its due date (same rules as the dues report). */
	public DuesTotal duesTotal(Long schoolId, Long yearId, LocalDate today) {
		return jdbc.queryForObject("""
				SELECT coalesce(sum(d.paid_amount), 0) AS paid,
				       coalesce(sum(d.amount - d.concession - d.paid_amount), 0) AS outstanding,
				       coalesce(sum(CASE WHEN i.due_date < :today THEN d.amount - d.concession - d.paid_amount ELSE 0 END), 0) AS overdue
				FROM student_fee_due d
				JOIN fee_installment i ON i.id = d.fee_installment_id AND i.school_id = :schoolId
				WHERE d.school_id = :schoolId AND i.academic_year_id = :yearId
				""", params(schoolId).addValue("yearId", yearId).addValue("today", today),
				(rs, i) -> new DuesTotal(rs.getBigDecimal("paid"), rs.getBigDecimal("outstanding"),
						rs.getBigDecimal("overdue")));
	}

	/** Classes with the most overdue money, and how many students owe it. */
	public List<DashboardDto.OverdueClass> topOverdueClasses(Long schoolId, Long yearId, LocalDate today, int limit) {
		return jdbc.query("""
				SELECT c.id, c.name, count(DISTINCT d.enrollment_id) AS students,
				       sum(d.amount - d.concession - d.paid_amount) AS overdue
				FROM student_fee_due d
				JOIN fee_installment i ON i.id = d.fee_installment_id AND i.school_id = :schoolId
				JOIN school_class c ON c.id = i.class_id AND c.school_id = :schoolId
				WHERE d.school_id = :schoolId AND i.academic_year_id = :yearId AND i.due_date < :today
				  AND d.amount - d.concession - d.paid_amount > 0
				GROUP BY c.id, c.name
				ORDER BY overdue DESC
				LIMIT :limit
				""", params(schoolId).addValue("yearId", yearId).addValue("today", today).addValue("limit", limit),
				(rs, i) -> new DashboardDto.OverdueClass(rs.getLong("id"), rs.getString("name"), rs.getInt("students"),
						rs.getBigDecimal("overdue")));
	}

	/** Exams of the year that have not ended yet (by their dates), soonest first. */
	public List<DashboardDto.UpcomingExam> upcomingExams(Long schoolId, Long yearId, LocalDate today, int limit) {
		return jdbc.query("""
				SELECT id, name, start_date, end_date, is_published
				FROM exam
				WHERE school_id = :schoolId AND academic_year_id = :yearId
				  AND coalesce(end_date, start_date) >= :today
				ORDER BY start_date, display_order
				LIMIT :limit
				""", params(schoolId).addValue("yearId", yearId).addValue("today", today).addValue("limit", limit),
				(rs, i) -> new DashboardDto.UpcomingExam(rs.getLong("id"), rs.getString("name"),
						rs.getObject("start_date", LocalDate.class), rs.getObject("end_date", LocalDate.class),
						rs.getBoolean("is_published")));
	}

	/** Latest admissions, with their class this year when they have one. */
	public List<DashboardDto.Admission> recentAdmissions(Long schoolId, Long yearId, int limit) {
		return jdbc.query("""
				SELECT s.id, s.first_name, s.last_name, s.admission_no, s.admission_date,
				       c.name AS class_name, sec.name AS section_name
				FROM student s
				LEFT JOIN enrollment e ON e.student_id = s.id AND e.school_id = :schoolId AND e.academic_year_id = :yearId
				LEFT JOIN section sec ON sec.id = e.section_id AND sec.school_id = :schoolId
				LEFT JOIN school_class c ON c.id = sec.class_id AND c.school_id = :schoolId
				WHERE s.school_id = :schoolId
				ORDER BY s.admission_date DESC, s.id DESC
				LIMIT :limit
				""", params(schoolId).addValue("yearId", yearId).addValue("limit", limit), (rs, i) -> {
			String last = rs.getString("last_name");
			String className = rs.getString("class_name");
			return new DashboardDto.Admission(rs.getLong("id"),
					rs.getString("first_name") + (last == null ? "" : " " + last), rs.getString("admission_no"),
					rs.getObject("admission_date", LocalDate.class),
					className == null ? null : className + " – " + rs.getString("section_name"));
		});
	}

	/** Active students per section, for the given sections. */
	public Map<Long, Integer> activeStudents(Long schoolId, Collection<Long> sectionIds) {
		Map<Long, Integer> out = new HashMap<>();
		if (sectionIds.isEmpty()) {
			return out;
		}
		jdbc.query("""
				SELECT section_id, count(*) AS n FROM enrollment
				WHERE school_id = :schoolId AND status = 'ACTIVE' AND section_id IN (:sectionIds)
				GROUP BY section_id
				""", params(schoolId).addValue("sectionIds", sectionIds), rs -> {
			out.put(rs.getLong("section_id"), rs.getInt("n"));
		});
		return out;
	}

	private static MapSqlParameterSource params(Long schoolId) {
		return new MapSqlParameterSource("schoolId", schoolId);
	}

}
