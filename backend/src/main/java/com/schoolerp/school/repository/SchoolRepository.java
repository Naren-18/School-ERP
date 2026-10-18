package com.schoolerp.school.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.school.entity.School;

public interface SchoolRepository extends JpaRepository<School, Long> {

	Optional<School> findByCodeIgnoreCase(String code);

}
