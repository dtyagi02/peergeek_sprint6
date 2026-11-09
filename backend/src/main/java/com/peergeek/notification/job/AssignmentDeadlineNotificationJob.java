package com.peergeek.notification.job;

import com.peergeek.assignment.AssignmentDeadlineSummary;
import com.peergeek.assignment.AssignmentQueryService;
import com.peergeek.notification.NotificationEntity;
import com.peergeek.notification.NotificationService;
import com.peergeek.notification.NotificationType;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AssignmentDeadlineNotificationJob {

	private static final Logger log = LoggerFactory.getLogger(AssignmentDeadlineNotificationJob.class);

	private final AssignmentQueryService assignmentQueryService;
	private final NotificationService notificationService;
	private final Clock clock;
	private final int windowHours;

	public AssignmentDeadlineNotificationJob(
			AssignmentQueryService assignmentQueryService,
			NotificationService notificationService,
			Clock clock,
			@Value("${peergeek.notifications.deadline-window-hours:24}") int windowHours) {
		this.assignmentQueryService = assignmentQueryService;
		this.notificationService = notificationService;
		this.clock = clock;
		this.windowHours = windowHours;
	}

	@Scheduled(cron = "${peergeek.notifications.deadline-cron:0 0 * * * *}")
	public int processUpcomingDeadlines() {
		Instant now = Instant.now(clock);
		Instant windowEnd = now.plus(Duration.ofHours(windowHours));
		log.info("Processing approaching assignment deadlines from {} to {}", now, windowEnd);

		int notificationsCreated = 0;
		int deadlinePage = 0;
		int pageSize = 50;

		Page<AssignmentDeadlineSummary> deadlines;
		do {
			deadlines = assignmentQueryService.listUpcomingDeadlines(
					now, windowEnd, PageRequest.of(deadlinePage, pageSize));

			for (AssignmentDeadlineSummary deadline : deadlines.getContent()) {
				int learnerPage = 0;
				Page<Long> learners;
				do {
					learners = assignmentQueryService.listLearnerIdsWithoutSubmission(
							deadline.assignmentId(), PageRequest.of(learnerPage, pageSize));

					for (Long learnerId : learners.getContent()) {
						String eventKey = "DEADLINE:" + deadline.assignmentId() + ":" + learnerId;
						String title = "Assignment deadline approaching: " + deadline.title();
						String message = "Assignment '" + deadline.title() + "' is due on " + deadline.dueAt()
								+ ". Please submit your work before the deadline.";
						String actionUrl = "/learning/courses/" + deadline.courseId() + "/assignments/" + deadline.assignmentId();

						NotificationEntity created = notificationService.createNotification(
								learnerId,
								NotificationType.ASSIGNMENT_DEADLINE_APPROACHING,
								title,
								message,
								"ASSIGNMENT",
								deadline.assignmentId(),
								actionUrl,
								eventKey);

						if (created != null) {
							notificationsCreated++;
						}
					}
					learnerPage++;
				} while (learners.hasNext());
			}
			deadlinePage++;
		} while (deadlines.hasNext());

		log.info("Finished approaching deadline processing. Created {} notifications.", notificationsCreated);
		return notificationsCreated;
	}
}
