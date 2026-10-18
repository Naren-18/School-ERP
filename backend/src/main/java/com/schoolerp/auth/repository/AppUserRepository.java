package com.schoolerp.auth.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.schoolerp.auth.entity.AppUser;
import com.schoolerp.auth.entity.Role;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

	Optional<AppUser> findByIdAndSchoolId(Long id, Long schoolId);

	List<AppUser> findBySchoolIdAndIdIn(Long schoolId, Collection<Long> ids);

	List<AppUser> findBySchoolIdAndActiveTrueOrderByFullNameAsc(Long schoolId);

	boolean existsBySchoolIdAndUsernameIgnoreCase(Long schoolId, String username);

	@Query("""
			SELECT count(u) FROM AppUser u JOIN u.roles r
			WHERE u.schoolId = :schoolId AND u.active = true AND r = :role
			""")
	long countActiveWithRole(@Param("schoolId") Long schoolId, @Param("role") Role role);

	@Query(value = """
			SELECT DISTINCT u FROM AppUser u LEFT JOIN u.roles r
			WHERE u.schoolId = :schoolId
			  AND (:q IS NULL OR lower(u.fullName) LIKE :q OR lower(u.username) LIKE :q OR u.phone LIKE :q)
			  AND (:role IS NULL OR r = :role)
			  AND (:active IS NULL OR u.active = :active)
			""", countQuery = """
			SELECT count(DISTINCT u) FROM AppUser u LEFT JOIN u.roles r
			WHERE u.schoolId = :schoolId
			  AND (:q IS NULL OR lower(u.fullName) LIKE :q OR lower(u.username) LIKE :q OR u.phone LIKE :q)
			  AND (:role IS NULL OR r = :role)
			  AND (:active IS NULL OR u.active = :active)
			""")
	Page<AppUser> search(@Param("schoolId") Long schoolId, @Param("q") String q, @Param("role") Role role,
			@Param("active") Boolean active, Pageable pageable);

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
