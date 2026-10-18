package com.schoolerp.student.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.student.entity.Guardian;

public interface GuardianRepository extends JpaRepository<Guardian, Long> {

	List<Guardian> findBySchoolIdAndStudentIdOrderByPrimaryDescIdAsc(Long schoolId, Long studentId);

	List<Guardian> findBySchoolIdAndStudentIdIn(Long schoolId, Collection<Long> studentIds);

}
