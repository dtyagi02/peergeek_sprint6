package com.peergeek.notification;

import com.peergeek.assignment.AssignmentDeadlineSummary;
import com.peergeek.assignment.AssignmentQueryService;
import com.peergeek.course.CoursePublishedEvent;
import com.peergeek.course.CourseUnpublishedEvent;
import com.peergeek.enrollment.LearningTestSupport;
import com.peergeek.notification.dto.NotificationDto;
import com.peergeek.notification.job.AssignmentDeadlineNotificationJob;
import com.peergeek.notification.listener.NotificationEventListener;
import com.peergeek.report.ReportCategory;
import com.peergeek.report.ReportCreatedEvent;
import com.peergeek.review.ReviewCreatedEvent;
import com.peergeek.submission.SubmissionGradedEvent;
import com.peergeek.user.UserEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NotificationIntegrationTest extends LearningTestSupport {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationEventListener notificationEventListener;

	@Autowired
	private AssignmentDeadlineNotificationJob deadlineJob;

	@MockitoBean
	private AssignmentQueryService assignmentQueryService;

	@Test
	@DisplayName("List, unread-count, and unreadOnly filter isolate authenticated user notifications")
	void listAndFilterNotifications() throws Exception {
		UserEntity alice = createUser("alice.notif@test.local", "Alice");
		UserEntity bob = createUser("bob.notif@test.local", "Bob");

		String aliceToken = tokenFor(alice);
		String bobToken = tokenFor(bob);

		// Insert notifications for Alice
		notificationService.createNotification(
				alice.getId(), NotificationType.COURSE_PUBLISHED, "Alice Course 1", "Published", "COURSE", 1L, "/courses/1", "KEY:1");
		notificationService.createNotification(
				alice.getId(), NotificationType.SUBMISSION_GRADED, "Alice Grade 1", "Graded", "ASSIGNMENT", 10L, "/assignments/10", "KEY:2");
		NotificationEntity aliceRead = notificationService.createNotification(
				alice.getId(), NotificationType.REVIEW_CREATED, "Alice Review", "5 stars", "REVIEW", 100L, "/courses/1", "KEY:3");
		notificationService.markAsRead(aliceRead.getId(), alice.getId());

		// Insert notification for Bob
		notificationService.createNotification(
				bob.getId(), NotificationType.COURSE_PUBLISHED, "Bob Course", "Published", "COURSE", 2L, "/courses/2", "KEY:4");

		// Alice lists all notifications
		mockMvc.perform(get("/api/v1/notifications")
						.header("Authorization", bearer(aliceToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(3))
				.andExpect(jsonPath("$.content.length()").value(3));

		// Alice unread count
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header("Authorization", bearer(aliceToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(2));

		// Alice lists unread only
		mockMvc.perform(get("/api/v1/notifications?unreadOnly=true")
						.header("Authorization", bearer(aliceToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(2));

		// Bob unread count
		mockMvc.perform(get("/api/v1/notifications/unread-count")
						.header("Authorization", bearer(bobToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.unreadCount").value(1));
	}

	@Test
	@DisplayName("Mark as read is idempotent and prevents unauthorized access to other users' notifications")
	void markAsReadIsIdempotentAndSecured() throws Exception {
		UserEntity alice = createUser("alice.mark@test.local", "Alice Mark");
		UserEntity bob = createUser("bob.mark@test.local", "Bob Mark");

		String aliceToken = tokenFor(alice);
		String bobToken = tokenFor(bob);

		NotificationEntity notif = notificationService.createNotification(
				alice.getId(), NotificationType.COURSE_PUBLISHED, "Alice Notif", "Details", "COURSE", 1L, "/c/1", "SEC:1");

		// Bob cannot mark Alice's notification
		mockMvc.perform(patch("/api/v1/notifications/{id}/read", notif.getId())
						.header("Authorization", bearer(bobToken)))
				.andExpect(status().isNotFound());

		// Alice marks it as read
		mockMvc.perform(patch("/api/v1/notifications/{id}/read", notif.getId())
						.header("Authorization", bearer(aliceToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isRead").value(true))
				.andExpect(jsonPath("$.readAt").isNotEmpty());

		// Alice marks it as read again (idempotent)
		mockMvc.perform(patch("/api/v1/notifications/{id}/read", notif.getId())
						.header("Authorization", bearer(aliceToken)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isRead").value(true));
	}

	@Test
	@DisplayName("Mark all as read marks all user's unread notifications and leaves other users untouched")
	void markAllAsReadLeavesOtherUsersUntouched() throws Exception {
		UserEntity alice = createUser("alice.all@test.local", "Alice All");
		UserEntity bob = createUser("bob.all@test.local", "Bob All");

		notificationService.createNotification(
				alice.getId(), NotificationType.COURSE_PUBLISHED, "A1", "Msg", null, null, null, "ALL:1");
		notificationService.createNotification(
				alice.getId(), NotificationType.SUBMISSION_GRADED, "A2", "Msg", null, null, null, "ALL:2");
		notificationService.createNotification(
				bob.getId(), NotificationType.COURSE_PUBLISHED, "B1", "Msg", null, null, null, "ALL:3");

		mockMvc.perform(post("/api/v1/notifications/mark-all-read")
						.header("Authorization", bearer(tokenFor(alice))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.markedCount").value(2));

		assertThat(notificationService.getUnreadCount(alice.getId())).isZero();
		assertThat(notificationService.getUnreadCount(bob.getId())).isEqualTo(1L);
	}

	@Test
	@DisplayName("Anonymous access is rejected with 401 Unauthorized")
	void anonymousAccessRejected() throws Exception {
		mockMvc.perform(get("/api/v1/notifications"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(get("/api/v1/notifications/unread-count"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(patch("/api/v1/notifications/1/read"))
				.andExpect(status().isUnauthorized());

		mockMvc.perform(post("/api/v1/notifications/mark-all-read"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("Domain event listeners create notifications with idempotent deduplication")
	void domainEventListenersCreateNotifications() {
		UserEntity instructor = createInstructor("inst.events@test.local", "Prof Events");
		UserEntity student = createUser("student.events@test.local", "Student Events");

		// 1. SubmissionGradedEvent
		SubmissionGradedEvent gradedEvent = new SubmissionGradedEvent(
				10L, 20L, 30L, student.getId(), instructor.getId(),
				BigDecimal.valueOf(95), BigDecimal.valueOf(100), true, false);
		notificationEventListener.onSubmissionGraded(gradedEvent);

		// Duplicate event fired
		notificationEventListener.onSubmissionGraded(gradedEvent);

		// 2. CoursePublishedEvent
		CoursePublishedEvent pubEvent = new CoursePublishedEvent(10L, instructor.getId(), Instant.now());
		notificationEventListener.onCoursePublished(pubEvent);

		// 3. CourseUnpublishedEvent
		CourseUnpublishedEvent unpubEvent = new CourseUnpublishedEvent(10L, instructor.getId(), Instant.now());
		notificationEventListener.onCourseUnpublished(unpubEvent);

		// 4. ReportCreatedEvent
		ReportCreatedEvent reportEvent = new ReportCreatedEvent(
				50L, 10L, student.getId(), instructor.getId(), ReportCategory.BROKEN_RESOURCE, "Broken link", Instant.now());
		notificationEventListener.onReportCreated(reportEvent);

		// 5. ReviewCreatedEvent
		ReviewCreatedEvent reviewEvent = new ReviewCreatedEvent(
				60L, 10L, student.getId(), instructor.getId(), 5, "Excellent!", Instant.now());
		notificationEventListener.onReviewCreated(reviewEvent);

		// Student received 1 submission graded notification (no duplicates!)
		assertThat(notificationService.getUnreadCount(student.getId())).isEqualTo(1L);

		// Instructor received 4 notifications (published, unpublished, report, review)
		assertThat(notificationService.getUnreadCount(instructor.getId())).isEqualTo(4L);
	}

	@Test
	@DisplayName("Approaching deadline scheduled job notifies eligible learners without submission idempotently")
	void deadlineJobIdempotentlyNotifiesLearners() {
		UserEntity student1 = createUser("s1.job@test.local", "Student 1");
		UserEntity student2 = createUser("s2.job@test.local", "Student 2");

		AssignmentDeadlineSummary deadline = new AssignmentDeadlineSummary(
				101L, 50L, "Midterm Exam", Instant.now().plusSeconds(3600), BigDecimal.valueOf(100));

		when(assignmentQueryService.listUpcomingDeadlines(any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(deadline)))
				.thenReturn(new PageImpl<>(List.of()));

		when(assignmentQueryService.listLearnerIdsWithoutSubmission(eq(101L), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(student1.getId(), student2.getId())))
				.thenReturn(new PageImpl<>(List.of()));

		// First execution
		int createdCount = deadlineJob.processUpcomingDeadlines();
		assertThat(createdCount).isEqualTo(2);

		assertThat(notificationService.getUnreadCount(student1.getId())).isEqualTo(1L);
		assertThat(notificationService.getUnreadCount(student2.getId())).isEqualTo(1L);

		// Second execution (re-run of job)
		when(assignmentQueryService.listUpcomingDeadlines(any(), any(), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(deadline)))
				.thenReturn(new PageImpl<>(List.of()));

		when(assignmentQueryService.listLearnerIdsWithoutSubmission(eq(101L), any(Pageable.class)))
				.thenReturn(new PageImpl<>(List.of(student1.getId(), student2.getId())))
				.thenReturn(new PageImpl<>(List.of()));

		int secondRunCount = deadlineJob.processUpcomingDeadlines();
		assertThat(secondRunCount).isZero(); // strictly deduplicated!

		assertThat(notificationService.getUnreadCount(student1.getId())).isEqualTo(1L);
		assertThat(notificationService.getUnreadCount(student2.getId())).isEqualTo(1L);
	}
}
