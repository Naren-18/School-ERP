package com.schoolerp.exam.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.exam.entity.ExamSubject;

public interface ExamSubjectRepository extends JpaRepository<ExamSubject, Long> {

	Optional<ExamSubject> findByIdAndSchoolId(Long id, Long schoolId);

	List<ExamSubject> findBySchoolIdAndExamId(Long schoolId, Long examId);

	List<ExamSubject> findBySchoolIdAndExamIdAndClassId(Long schoolId, Long examId, Long classId);

	List<ExamSubject> findBySchoolIdAndExamIdInAndClassId(Long schoolId, Collection<Long> examIds, Long classId);

	Optional<ExamSubject> findBySchoolIdAndExamIdAndClassIdAndSubjectId(Long schoolId, Long examId, Long classId,
			Long subjectId);

}
