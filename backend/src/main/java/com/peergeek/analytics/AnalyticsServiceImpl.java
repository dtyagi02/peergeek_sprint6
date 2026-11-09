package com.peergeek.analytics;

import com.peergeek.analytics.dto.AssignmentPerformanceDto;
import com.peergeek.analytics.dto.InstructorCourseAnalyticsDto;
import com.peergeek.analytics.dto.LessonDropOffDto;
import com.peergeek.analytics.dto.ScoreBucketDto;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.exception.NotFoundException;
import com.peergeek.security.OwnershipGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

	private final AnalyticsRepository analyticsRepository;
	private final AnalyticsCacheService analyticsCacheService;
	private final CourseStructureQueryService courseStructureQueryService;
	private final OwnershipGuard ownershipGuard;

	public AnalyticsServiceImpl(
			AnalyticsRepository analyticsRepository,
			AnalyticsCacheService analyticsCacheService,
			CourseStructureQueryService courseStructureQueryService,
			OwnershipGuard ownershipGuard) {
		this.analyticsRepository = analyticsRepository;
		this.analyticsCacheService = analyticsCacheService;
		this.courseStructureQueryService = courseStructureQueryService;
		this.ownershipGuard = ownershipGuard;
	}

	@Override
	public InstructorCourseAnalyticsDto getInstructorCourseAnalytics(long courseId, long instructorId) {
		Long ownerId = courseStructureQueryService.getCourseOwnerId(courseId);
		if (ownerId == null) {
			throw new NotFoundException("Course not found");
		}
		ownershipGuard.requireOwnerOrNotFound(ownerId, instructorId);

		return analyticsCacheService.getCourseAnalytics(courseId, () -> computeAnalytics(courseId));
	}

	private InstructorCourseAnalyticsDto computeAnalytics(long courseId) {
		CourseStructureQueryService.CourseStructure structure = courseStructureQueryService.getStructure(courseId);
		String courseTitle = structure.course() != null ? structure.course().title() : "Course #" + courseId;

		long totalEnrollments = analyticsRepository.countTotalEnrollments(courseId);
		long activeLearners = analyticsRepository.countActiveLearners(courseId);
		long completedLearners = analyticsRepository.countCompletedLearners(courseId);

		double completionRate = activeLearners > 0
				? Math.round((completedLearners * 100.0 / activeLearners) * 10.0) / 10.0
				: 0.0;

		Double avgScoreRaw = analyticsRepository.getAverageAssessmentScore(courseId);
		Double averageAssessmentScore = avgScoreRaw != null
				? Math.round(avgScoreRaw * 10.0) / 10.0
				: null;

		AnalyticsRepository.RatingSummary ratingSummary = analyticsRepository.getRatingSummary(courseId);
		Double averageRating = ratingSummary.averageRating() != null
				? Math.round(ratingSummary.averageRating() * 10.0) / 10.0
				: null;
		long ratingCount = ratingSummary.ratingCount();

		long pendingSubmissions = analyticsRepository.countPendingSubmissions(courseId);

		List<AssignmentPerformanceDto> assignmentStats = analyticsRepository.getAssignmentStats(courseId, activeLearners);

		List<AssignmentPerformanceDto> publishedAssignments = assignmentStats.stream()
				.filter(a -> "PUBLISHED".equalsIgnoreCase(a.status()))
				.toList();

		double assignmentSubmissionRate = 0.0;
		if (activeLearners > 0 && !publishedAssignments.isEmpty()) {
			long totalExpected = (long) publishedAssignments.size() * activeLearners;
			long totalSubmitted = publishedAssignments.stream()
					.mapToLong(AssignmentPerformanceDto::submittedCount)
					.sum();
			assignmentSubmissionRate = Math.round((totalSubmitted * 100.0 / totalExpected) * 10.0) / 10.0;
		}

		List<ScoreBucketDto> scoreDistribution = analyticsRepository.getScoreDistribution(courseId);

		Map<Long, Long> lessonCompletedMap = analyticsRepository.getLessonCompletionCounts(courseId);
		List<LessonDropOffDto> lessonDropOff = new ArrayList<>();
		long prevCompleted = -1;
		int orderIndex = 1;

		if (structure.modules() != null) {
			for (CourseStructureQueryService.ModuleSnapshot module : structure.modules()) {
				if (module.lessons() != null) {
					for (CourseStructureQueryService.LessonSnapshot lesson : module.lessons()) {
						long completed = lessonCompletedMap.getOrDefault(lesson.id(), 0L);
						double completionPercent = activeLearners > 0
								? Math.round((completed * 100.0 / activeLearners) * 10.0) / 10.0
								: 0.0;
						double dropOffRate = 0.0;
						if (prevCompleted > 0 && completed < prevCompleted) {
							dropOffRate = Math.round(((prevCompleted - completed) * 100.0 / prevCompleted) * 10.0) / 10.0;
						}

						lessonDropOff.add(new LessonDropOffDto(
								lesson.id(),
								lesson.title(),
								module.title(),
								orderIndex++,
								completed,
								completionPercent,
								dropOffRate));
						prevCompleted = completed;
					}
				}
			}
		}

		return new InstructorCourseAnalyticsDto(
				courseId,
				courseTitle,
				totalEnrollments,
				activeLearners,
				completionRate,
				averageAssessmentScore,
				averageRating,
				ratingCount,
				assignmentSubmissionRate,
				pendingSubmissions,
				lessonDropOff,
				assignmentStats,
				scoreDistribution);
	}
}
