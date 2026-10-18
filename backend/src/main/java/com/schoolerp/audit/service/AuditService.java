package com.schoolerp.audit.service;

import java.util.Map;

import org.springframework.stereotype.Service;

import com.schoolerp.audit.entity.AuditAction;
import com.schoolerp.audit.entity.AuditLog;
import com.schoolerp.audit.repository.AuditLogRepository;
import com.schoolerp.auth.service.CurrentUser;

import lombok.RequiredArgsConstructor;

/** Writes in-app audit entries for the current school (see CLAUDE.md for what to log). */
@Service
@RequiredArgsConstructor
public class AuditService {

	private final AuditLogRepository repository;

	/** Logs as the signed-in user. */
	public void log(AuditAction action, String entity, Long entityId, Map<String, Object> details) {
		logAs(CurrentUser.idOrNull(), action, entity, entityId, details);
	}

	/** Logs as a specific user, for flows with no signed-in user yet (login). */
	public void logAs(Long userId, AuditAction action, String entity, Long entityId, Map<String, Object> details) {
		AuditLog entry = new AuditLog();
		entry.setUserId(userId);
		entry.setAction(action.name());
		entry.setEntity(entity);
		entry.setEntityId(entityId);
		entry.setDetails(details);
		repository.save(entry);
	}

}
