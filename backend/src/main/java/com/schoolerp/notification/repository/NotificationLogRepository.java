package com.schoolerp.notification.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.notification.entity.MessageType;
import com.schoolerp.notification.entity.NotificationLog;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long> {

	List<NotificationLog> findBySchoolIdAndTypeAndRefDateAndStudentIdInOrderByIdDesc(Long schoolId, MessageType type,
			LocalDate refDate, Collection<Long> studentIds);

	List<NotificationLog> findBySchoolIdAndTypeAndRefIdAndStudentIdInOrderByIdDesc(Long schoolId, MessageType type,
			Long refId, Collection<Long> studentIds);

}
