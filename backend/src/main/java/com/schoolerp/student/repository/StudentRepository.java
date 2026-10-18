package com.schoolerp.student.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.schoolerp.student.entity.Student;

public interface StudentRepository extends JpaRepository<Student, Long> {

	Optional<Student> findByIdAndSchoolId(Long id, Long schoolId);

	List<Student> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	boolean existsBySchoolIdAndAdmissionNoIgnoreCase(Long schoolId, String admissionNo);

	boolean existsBySchoolIdAndAdmissionNoIgnoreCaseAndIdNot(Long schoolId, String admissionNo, Long id);

	List<Student> findBySchoolIdAndAdmissionNoIn(Long schoolId, Collection<String> admissionNos);

	@Query("SELECT s.admissionNo FROM Student s WHERE s.schoolId = :schoolId")
	List<String> admissionNumbers(@Param("schoolId") Long schoolId);

}
