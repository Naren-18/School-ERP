package com.schoolerp.exam.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.exam.entity.ReportCardNote;

public interface ReportCardNoteRepository extends JpaRepository<ReportCardNote, Long> {

	List<ReportCardNote> findBySchoolIdAndExamIdAndEnrollmentIdIn(Long schoolId, Long examId, Collection<Long> enrollmentIds);

	List<ReportCardNote> findBySchoolIdAndExamId(Long schoolId, Long examId);

}
