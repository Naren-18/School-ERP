package com.schoolerp.auth.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.auth.entity.AppUser;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByIdAndSchoolId(Long id, Long schoolId);

	/**
	 * Login lookup. Runs before any tenant is in context, so it is a native query
	 * (Hibernate filters do not apply) scoped explicitly by school.
	 */
	@Query(value = """
			SELECT * FROM app_user
			WHERE school_id = :schoolId AND lower(username) = lower(:username)
			""", nativeQuery = true)
	Optional<AppUser> findForLogin(@Param("schoolId") Long schoolId, @Param("username") String username);

	@Transactional
	@Modifying
	@Query(value = "UPDATE app_user SET last_login_at = :at WHERE id = :id AND school_id = :schoolId",
			nativeQuery = true)
	int updateLastLogin(@Param("id") Long id, @Param("schoolId") Long schoolId, @Param("at") OffsetDateTime at);

}
