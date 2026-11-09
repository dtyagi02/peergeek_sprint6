package com.peergeek.identity.repository;

import java.util.List;
import java.util.Optional;
import com.peergeek.identity.entity.UserEntity;
import com.peergeek.identity.entity.UserMode;
import com.peergeek.identity.entity.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {
	Optional<UserEntity> findByEmail(String email);

	boolean existsByEmail(String email);

	Optional<UserEntity> findByUsername(String username);

	boolean existsByUsername(String username);

	boolean existsByRole(UserRole role);

	long countByLastMode(UserMode lastMode);

	long countByRole(UserRole role);

	@Query(value = """
			select u from UserEntity u
			where (:role is null or u.role = :role)
			and (:mode is null or u.lastMode = :mode)
			and (:enabled is null or u.enabled = :enabled)
			and (:query is null or lower(u.displayName) like lower(concat('%', :query, '%')) or lower(u.email) like lower(concat('%', :query, '%')))
			order by u.createdAt desc
			""",
			countQuery = """
			select count(u) from UserEntity u
			where (:role is null or u.role = :role)
			and (:mode is null or u.lastMode = :mode)
			and (:enabled is null or u.enabled = :enabled)
			and (:query is null or lower(u.displayName) like lower(concat('%', :query, '%')) or lower(u.email) like lower(concat('%', :query, '%')))
			""")
	Page<UserEntity> findAdminUsers(
			@Param("role") UserRole role,
			@Param("mode") UserMode mode,
			@Param("enabled") Boolean enabled,
			@Param("query") String query,
			Pageable pageable);

	@Query(value = """
			select u from UserEntity u
			where u.enabled = true
			and (:prefix is null or lower(u.username) like lower(concat(:prefix, '%'))
			  or lower(u.displayName) like lower(concat('%', :prefix, '%')))
			order by u.username asc
			""")
	List<UserEntity> findMentionSuggestions(@Param("prefix") String prefix, Pageable pageable);
}

