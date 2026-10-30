package com.schoolerp.exam.service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.schoolerp.common.error.BadRequestException;
import com.schoolerp.common.error.ConflictException;
import com.schoolerp.exam.dto.ExamDto;
import com.schoolerp.notification.dto.NotificationDto;
import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.service.NotificationService;
import com.schoolerp.school.service.SchoolService;

import lombok.RequiredArgsConstructor;

/** "Results are out" WhatsApp messages for a section, once the exam is published. */
@Service
@RequiredArgsConstructor
public class ResultMessageService {

	private final ResultService results;

	private final NotificationService notifications;

	private final SchoolService schoolService;

	/** The last results message tried per student of the section for this exam. */
	public List<NotificationDto.History> history(Long examId, Long sectionId) {
		ExamDto.ResultSheet sheet = results.sectionSheet(examId, sectionId);
		return List.copyOf(notifications
			.history(MessageType.RESULTS_PUBLISHED, null, examId,
					sheet.rows().stream().map(ExamDto.ResultRow::studentId).toList())
			.values());
	}

	public NotificationDto.SendReport send(Long examId, Long sectionId, NotificationDto.SendRequest request) {
		ExamDto.ResultSheet sheet = results.sectionSheet(examId, sectionId);
		if (!sheet.published()) {
			throw new ConflictException("Publish " + sheet.examName() + " before messaging parents about its results");
		}
		Map<Long, ExamDto.ResultRow> rows = sheet.rows()
			.stream()
			.collect(Collectors.toMap(ExamDto.ResultRow::studentId, Function.identity(), (a, b) -> a));
		if (!rows.keySet().containsAll(Set.copyOf(request.studentIds()))) {
			throw new BadRequestException("Only students of " + sheet.sectionLabel() + " can be messaged here");
		}
		String school = schoolService.requireCurrent().getName();
		List<NotificationService.Message> messages = request.studentIds()
			.stream()
			.distinct()
			.map(id -> new NotificationService.Message(id, null, examId,
					List.of(sheet.examName(), rows.get(id).fullName(), sheet.sectionLabel(), school)))
			.toList();
		return notifications.send(MessageType.RESULTS_PUBLISHED, messages);
	}

}
