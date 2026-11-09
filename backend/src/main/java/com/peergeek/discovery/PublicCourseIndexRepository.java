package com.peergeek.discovery;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PublicCourseIndexRepository extends JpaRepository<PublicCourseIndexEntity, Long> {
	Optional<PublicCourseIndexEntity> findByCourseId(Long courseId);

	void deleteByCourseId(Long courseId);

	boolean existsByCourseId(Long courseId);

	@Query("""
			select p.courseId from PublicCourseIndexEntity p
			where (:afterId is null or p.courseId > :afterId)
			order by p.courseId asc
			""")
	List<Long> findIndexedCourseIdsAfter(@Param("afterId") Long afterId, Pageable pageable);

	@Query("""
			select p.categoryId, count(p) from PublicCourseIndexEntity p
			where p.categoryId is not null
			group by p.categoryId
			""")
	List<Object[]> countPublishedCoursesByCategoryId();
}
