package com.schoolerp.fee.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.schoolerp.fee.dto.FeeDto;
import com.schoolerp.fee.entity.PaymentMode;

import lombok.RequiredArgsConstructor;

/** Fee report aggregates. Plain SQL; every table is scoped by {@code school_id = :schoolId}. */
@Repository
@RequiredArgsConstructor
public class FeeReportRepository {

	private final NamedParameterJdbcTemplate jdbc;

	/** Money received per day and payment mode (cancelled receipts excluded). */
	public List<FeeDto.CollectionDay> collection(Long schoolId, LocalDate from, LocalDate to) {
		return jdbc.query("""
				SELECT paid_on, payment_mode, count(*) AS receipts, sum(total_amount) AS total
				FROM fee_receipt
				WHERE school_id = :schoolId AND NOT cancelled AND paid_on BETWEEN :from AND :to
				GROUP BY paid_on, payment_mode
				ORDER BY paid_on, payment_mode
				""", new MapSqlParameterSource().addValue("schoolId", schoolId).addValue("from", from).addValue("to", to),
				(rs, i) -> new FeeDto.CollectionDay(rs.getObject("paid_on", LocalDate.class),
						PaymentMode.valueOf(rs.getString("payment_mode")), rs.getInt("receipts"), rs.getBigDecimal("total")));
	}

	/** Students with something left to pay in the year; {@code overdue} counts installments past their due date. */
	public List<FeeDto.DueRow> dues(Long schoolId, Long yearId, Long classId, Long sectionId, boolean overdueOnly,
			LocalDate today) {
		MapSqlParameterSource p = new MapSqlParameterSource().addValue("schoolId", schoolId)
			.addValue("yearId", yearId)
			.addValue("today", today);
		StringBuilder where = new StringBuilder();
		if (classId != null) {
			where.append(" AND sec.class_id = :classId");
			p.addValue("classId", classId);
		}
		if (sectionId != null) {
			where.append(" AND sec.id = :sectionId");
			p.addValue("sectionId", sectionId);
		}
		String having = overdueOnly ? " AND sum(CASE WHEN i.due_date < :today THEN d.amount - d.concession - d.paid_amount ELSE 0 END) > 0" : "";
		String sql = """
				SELECT s.id, s.admission_no, s.first_name, s.last_name, c.name AS class_name, sec.name AS section_name,
				       e.roll_no,
				       (SELECT g.phone FROM guardian g WHERE g.student_id = s.id AND g.school_id = :schoolId
				        ORDER BY g.is_primary DESC, g.id LIMIT 1) AS phone,
				       sum(d.amount - d.concession) AS net, sum(d.paid_amount) AS paid,
				       sum(d.amount - d.concession - d.paid_amount) AS balance,
				       sum(CASE WHEN i.due_date < :today THEN d.amount - d.concession - d.paid_amount ELSE 0 END) AS overdue
				FROM student_fee_due d
				JOIN fee_installment i ON i.id = d.fee_installment_id AND i.school_id = :schoolId
				JOIN enrollment e ON e.id = d.enrollment_id AND e.school_id = :schoolId
				JOIN student s ON s.id = e.student_id AND s.school_id = :schoolId
				JOIN section sec ON sec.id = e.section_id AND sec.school_id = :schoolId
				JOIN school_class c ON c.id = sec.class_id AND c.school_id = :schoolId
				WHERE d.school_id = :schoolId AND e.academic_year_id = :yearId
				""" + where + """

				GROUP BY s.id, s.admission_no, s.first_name, s.last_name, c.name, c.display_order, sec.name, e.roll_no
				HAVING sum(d.amount - d.concession - d.paid_amount) > 0""" + having + """

				ORDER BY c.display_order, sec.name, e.roll_no NULLS LAST, lower(s.first_name)
				""";
		return jdbc.query(sql, p, (rs, i) -> {
			String last = rs.getString("last_name");
			return new FeeDto.DueRow(rs.getLong("id"), rs.getString("admission_no"),
					last == null ? rs.getString("first_name") : rs.getString("first_name") + " " + last,
					rs.getString("class_name") + " – " + rs.getString("section_name"), rs.getObject("roll_no", Integer.class),
					rs.getString("phone"), rs.getBigDecimal("net"), rs.getBigDecimal("paid"), rs.getBigDecimal("balance"),
					rs.getBigDecimal("overdue"));
		});
	}

	/** Expected (after concessions) vs collected per class for a year. */
	public List<FeeDto.ClassSummary> classSummary(Long schoolId, Long yearId) {
		return jdbc.query("""
				SELECT c.id, c.name, count(DISTINCT e.id) AS students,
				       coalesce(sum(d.amount - d.concession), 0) AS expected, coalesce(sum(d.paid_amount), 0) AS collected
				FROM student_fee_due d
				JOIN enrollment e ON e.id = d.enrollment_id AND e.school_id = :schoolId
				JOIN section sec ON sec.id = e.section_id AND sec.school_id = :schoolId
				JOIN school_class c ON c.id = sec.class_id AND c.school_id = :schoolId
				WHERE d.school_id = :schoolId AND e.academic_year_id = :yearId
				GROUP BY c.id, c.name, c.display_order
				ORDER BY c.display_order, c.name
				""", new MapSqlParameterSource().addValue("schoolId", schoolId).addValue("yearId", yearId), (rs, i) -> {
			var expected = rs.getBigDecimal("expected");
			var collected = rs.getBigDecimal("collected");
			Double pct = expected.signum() == 0 ? null
					: Math.round(collected.doubleValue() * 1000.0 / expected.doubleValue()) / 10.0;
			return new FeeDto.ClassSummary(rs.getLong("id"), rs.getString("name"), rs.getInt("students"), expected,
					collected, expected.subtract(collected), pct);
		});
	}

}
