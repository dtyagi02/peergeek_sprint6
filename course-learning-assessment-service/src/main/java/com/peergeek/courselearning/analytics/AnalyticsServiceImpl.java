package com.peergeek.courselearning.analytics;

import com.peergeek.courselearning.course.CourseEntity;
import com.peergeek.courselearning.course.CourseRepository;
import com.peergeek.courselearning.exception.ForbiddenException;
import com.peergeek.courselearning.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsRepository analyticsRepository;
    private final CourseRepository courseRepository;

    public AnalyticsServiceImpl(AnalyticsRepository analyticsRepository, CourseRepository courseRepository) {
        this.analyticsRepository = analyticsRepository;
        this.courseRepository = courseRepository;
    }

    @Override
    public InstructorCourseAnalyticsDto getInstructorCourseAnalytics(long courseId, long instructorId) {
        CourseEntity course = courseRepository.findByIdAndDeletedAtIsNull(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found"));

        if (!course.getOwnerId().equals(instructorId)) {
            throw new ForbiddenException("You are not authorized to view analytics for this course");
        }

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

        return new InstructorCourseAnalyticsDto(
                courseId,
                course.getTitle(),
                totalEnrollments,
                activeLearners,
                completedLearners,
                completionRate,
                averageAssessmentScore,
                ratingSummary.averageRating(),
                ratingSummary.ratingCount(),
                analyticsRepository.getLessonDropOff(courseId),
                analyticsRepository.getScoreBuckets(courseId),
                analyticsRepository.getAssignmentPerformance(courseId));
    }
}
