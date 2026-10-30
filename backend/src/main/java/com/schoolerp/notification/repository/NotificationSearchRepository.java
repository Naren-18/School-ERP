package com.schoolerp.notification.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.schoolerp.notification.dto.NotificationDto;
import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.entity.NotificationLog;

import lombok.RequiredArgsConstructor;

/**
 * The message log page. Plain SQL to search by student name; every table is
 * scoped by {@code school_id = :schoolId} explicitly.
 */
@Repository
@RequiredArgsConstructor
public class NotificationSearchRepository {

	public record Criteria(Long schoolId, MessageType type, NotificationLog.Status status, OffsetDateTime from,
			OffsetDateTime to, String q, int page, int size) {
	}

	public record Result(List<NotificationDto.LogRow> rows, long total) {
	}

	private static final String FROM = """
			FROM notification_log n
			JOIN student s ON s.id = n.student_id AND s.school_id = :schoolId
			LEFT JOIN app_user u ON u.id = n.sent_by AND u.school_id = :schoolId
			WHERE n.school_id = :schoolId AND n.created_at >= :from AND n.created_at < :to
			""";

	private final NamedParameterJdbcTemplate jdbc;

	public Result search(Criteria c) {
		StringBuilder where = new StringBuilder();
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("schoolId", c.schoolId())
			.addValue("from", c.from())
			.addValue("to", c.to());
		if (c.type() != null) {
			where.append(" AND n.type = :type");
			params.addValue("type", c.type().name());
		}
		if (c.status() != null) {
			where.append(" AND n.status = :status");
			params.addValue("status", c.status().name());
		}
		if (c.q() != null && !c.q().isBlank()) {
			where.append(" AND (lower(s.first_name || ' ' || coalesce(s.last_name, '')) LIKE :q OR n.phone LIKE :q)");
			params.addValue("q", "%" + c.q().trim().toLowerCase(Locale.ROOT) + "%");
		}
		Long total = jdbc.queryForObject("SELECT count(*) " + FROM + where, params, Long.class);
		params.addValue("limit", c.size()).addValue("offset", (long) c.page() * c.size());
		List<NotificationDto.LogRow> rows = jdbc.query("""
				SELECT n.id, n.created_at, n.type, n.student_id, s.first_name, s.last_name, n.phone, n.status,
				       n.error, u.full_name AS sent_by_name
				""" + FROM + where + """

				ORDER BY n.created_at DESC, n.id DESC
				LIMIT :limit OFFSET :offset""", params, (rs, i) -> {
			String last = rs.getString("last_name");
			String name = last == null || last.isBlank() ? rs.getString("first_name")
					: rs.getString("first_name") + " " + last;
			return new NotificationDto.LogRow(rs.getLong("id"), rs.getObject("created_at", OffsetDateTime.class),
					MessageType.valueOf(rs.getString("type")), rs.getLong("student_id"), name, rs.getString("phone"),
					NotificationLog.Status.valueOf(rs.getString("status")), rs.getString("error"),
					rs.getString("sent_by_name"));
		});
		return new Result(rows, total == null ? 0 : total);
	}

}
