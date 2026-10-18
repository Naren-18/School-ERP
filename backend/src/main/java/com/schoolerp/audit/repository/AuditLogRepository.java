package com.schoolerp.audit.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.audit.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	List<AuditLog> findBySchoolIdAndEntityAndEntityIdOrderByIdDesc(Long schoolId, String entity, Long entityId);

}
