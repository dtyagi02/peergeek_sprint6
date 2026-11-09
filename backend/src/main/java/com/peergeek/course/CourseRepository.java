package com.peergeek.course;

import java.util.Optional;
import java.util.List;
import java.util.Set;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<CourseEntity, Long> {
	Optional<CourseEntity> findByIdAndDeletedAtIsNull(Long id);

	List<CourseEntity> findByOwnerIdAndStatusAndDeletedAtIsNull(Long ownerId, CourseStatus status);

	List<CourseEntity> findByOwnerIdAndDeletedAtIsNull(Long ownerId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select c from CourseEntity c where c.id = :id and c.deletedAt is null")
	Optional<CourseEntity> findByIdAndDeletedAtIsNullForUpdate(@Param("id") Long id);

	@Query(value = """
			select c from CourseEntity c
			where c.owner.id = :ownerId and c.deletedAt is null
			and (:status is null or c.status = :status)
			and (:query is null or lower(c.title) like lower(concat('%', :query, '%')))
			""",
			countQuery = """
			select count(c) from CourseEntity c
			where c.owner.id = :ownerId and c.deletedAt is null
			and (:status is null or c.status = :status)
			and (:query is null or lower(c.title) like lower(concat('%', :query, '%')))
			""")
	Page<CourseEntity> findOwnedCourses(
			@Param("ownerId") Long ownerId,
			@Param("status") CourseStatus status,
			@Param("query") String query,
			Pageable pageable);

	@Query("""
			select new com.peergeek.course.CourseCountRow(
				c.id,
				(select count(m) from ModuleEntity m where m.course = c),
				(select count(l) from LessonEntity l where l.module.course = c),
				(select coalesce(sum(content.wordCount), 0) from LessonContentEntity content
					where content.lesson.module.course = c))
			from CourseEntity c where c.id in :courseIds
			""")
	List<CourseCountRow> countsByCourseIds(@Param("courseIds") List<Long> courseIds);

	@Query("""
			select new com.peergeek.course.LessonLocation(
				l.id, m.id, c.id, courseOwner.id,
				case when c.deletedAt is null then false else true end)
			from LessonEntity l
			join l.module m
			join m.course c
			join c.owner courseOwner
			where l.id = :lessonId
			""")
	Optional<LessonLocation> findLessonLocation(@Param("lessonId") Long lessonId);

	@Query("select l.id from LessonEntity l where l.id in :lessonIds")
	List<Long> findExistingLessonIds(@Param("lessonIds") List<Long> lessonIds);

	@Query("select c.thumbnailResourceId from CourseEntity c where c.id = :courseId")
	Optional<Long> findThumbnailResourceId(@Param("courseId") Long courseId);

	@Modifying
	@Query("update CourseEntity c set c.thumbnailResourceId = :resourceId where c.id = :courseId")
	int updateThumbnailResourceId(
			@Param("courseId") Long courseId, @Param("resourceId") Long resourceId);

	@Query("""
			select new com.peergeek.course.CourseMetadataRow(
				c.id, courseOwner.id, c.status,
				case when c.deletedAt is null then false else true end,
				c.version, c.title, c.description, category.id, category.name,
				case when category.active = true then true else false end,
				c.difficulty, topic.name, c.thumbnailResourceId)
			from CourseEntity c
			join c.owner courseOwner
			left join c.category category
			left join c.topics topic
			where c.id = :courseId
			order by topic.position
			""")
	List<CourseMetadataRow> findCourseMetadataRows(@Param("courseId") Long courseId);

	@Query("""
			select new com.peergeek.course.CourseMetadataRow(
				c.id, courseOwner.id, c.status,
				case when c.deletedAt is null then false else true end,
				c.version, c.title, c.description, category.id, category.name,
				case when category.active = true then true else false end,
				c.difficulty, topic.name, c.thumbnailResourceId)
			from CourseEntity c
			join c.owner courseOwner
			left join c.category category
			left join c.topics topic
			where c.id in :courseIds
			order by c.id, topic.position
			""")
	List<CourseMetadataRow> findCourseMetadataRowsBulk(@Param("courseIds") java.util.Collection<Long> courseIds);

	@Query("""
			select new com.peergeek.course.CourseStatusSnapshot(
				c.id, courseOwner.id, c.status,
				case when c.deletedAt is null then false else true end,
				c.version, c.title)
			from CourseEntity c join c.owner courseOwner
			where c.id = :courseId
			""")
	Optional<CourseStatusSnapshot> findStatusSnapshot(@Param("courseId") Long courseId);

	@Modifying(clearAutomatically = true)
	@Query("""
			update CourseEntity c
			set c.status = :toStatus,
				c.publishedAt = case when :toStatus = com.peergeek.course.CourseStatus.PUBLISHED
					then :publishedAt else c.publishedAt end,
				c.version = c.version + 1
			where c.id = :courseId and c.deletedAt is null and c.status in :allowedFrom
			""")
	int transitionStatus(
			@Param("courseId") Long courseId,
			@Param("allowedFrom") Set<CourseStatus> allowedFrom,
			@Param("toStatus") CourseStatus toStatus,
			@Param("publishedAt") java.time.Instant publishedAt);

	boolean existsByIdAndStatusAndDeletedAtIsNull(Long id, CourseStatus status);

	@Query("""
			select c.id from CourseEntity c
			where c.status = com.peergeek.course.CourseStatus.PUBLISHED
			and c.deletedAt is null
			and (:afterId is null or c.id > :afterId)
			order by c.id asc
			""")
	List<Long> findPublishedCourseIdsAfter(
			@Param("afterId") Long afterId,
			Pageable pageable);

	long countByDeletedAtIsNull();

	long countByStatusAndDeletedAtIsNull(CourseStatus status);

	@Query(value = """
			select c from CourseEntity c
			left join fetch c.owner
			left join fetch c.category
			where c.deletedAt is null
			and (:status is null or c.status = :status)
			and (:query is null or lower(c.title) like lower(concat('%', :query, '%')))
			order by c.createdAt desc
			""",
			countQuery = """
			select count(c) from CourseEntity c
			where c.deletedAt is null
			and (:status is null or c.status = :status)
			and (:query is null or lower(c.title) like lower(concat('%', :query, '%')))
			""")
	Page<CourseEntity> findAdminCourses(
			@Param("status") CourseStatus status,
			@Param("query") String query,
			Pageable pageable);
}
