package com.schoolerp.fee.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.fee.entity.FeeHead;

public interface FeeHeadRepository extends JpaRepository<FeeHead, Long> {

	Optional<FeeHead> findByIdAndSchoolId(Long id, Long schoolId);

	List<FeeHead> findBySchoolIdOrderByNameAsc(Long schoolId);

	List<FeeHead> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	boolean existsBySchoolIdAndNameIgnoreCase(Long schoolId, String name);

	boolean existsBySchoolIdAndNameIgnoreCaseAndIdNot(Long schoolId, String name, Long id);

}
