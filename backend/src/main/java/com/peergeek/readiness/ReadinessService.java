package com.peergeek.readiness;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import com.peergeek.config.ReadinessSettings;
import com.peergeek.course.CourseMetadataSnapshot;
import com.peergeek.course.CourseStructureQueryService;
import com.peergeek.file.LessonResourceQueryService;
import com.peergeek.security.CurrentUserProvider;
import com.peergeek.security.OwnershipGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReadinessService {
	private final CourseStructureQueryService structure;
	private final LessonResourceQueryService resources;
	private final CurrentUserProvider users;
	private final OwnershipGuard ownership;
	private final List<ReadinessRule> rules;
	private final ReadinessFingerprint fingerprint;
	private final ReadinessRunStore runs;
	private final ReadinessSettings settings;
	private final ThreadPoolTaskExecutor executor;

	public ReadinessService(CourseStructureQueryService structure, LessonResourceQueryService resources,
			CurrentUserProvider users, OwnershipGuard ownership, List<ReadinessRule> rules,
			ReadinessFingerprint fingerprint, ReadinessRunStore runs, ReadinessSettings settings,
			@Qualifier("readinessExecutor") ThreadPoolTaskExecutor executor) {
		this.structure = structure;
		this.resources = resources;
		this.users = users;
		this.ownership = ownership;
		this.rules = rules != null ? List.copyOf(rules) : List.of();
		this.fingerprint = fingerprint;
		this.runs = runs;
		this.settings = settings;
		this.executor = executor;
	}

	public ReadinessRunView start(long courseId) {
		long userId = users.requireCurrentUser().id();
		ReadinessContext context = ownedContext(courseId, userId);
		if (runs.countRunningByUser(userId) >= settings.getRuns().getMaxConcurrentPerUser()) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"Too many readiness checks are already running for this user.");
		}
		Instant latest = runs.latestRunCreatedAt(courseId).orElse(null);
		if (latest != null && latest.plus(settings.getRuns().getCourseCooldown()).isAfter(Instant.now())) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
					"A readiness check was started too recently for this course.");
		}
		String value = fingerprint.calculate(context);
		long runId = runs.create(courseId, userId, value);
		try {
			executor.execute(() -> execute(runId, context));
		} catch (RejectedExecutionException exception) {
			runs.fail(runId, "Readiness service is at capacity; retry later.");
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
					"Readiness service is temporarily at capacity.", exception);
		}
		runs.enforceRetention(courseId, settings.getRuns().getRetentionPerCourse());
		return runs.find(courseId, runId).orElseThrow();
	}

	public ReadinessRunView get(long courseId, long runId) {
		long userId = users.requireCurrentUser().id();
		ownedMetadata(courseId, userId);
		return runs.find(courseId, runId).orElseThrow(() ->
				new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND,
						"Readiness run not found."));
	}

	public ReadinessRunView getLatest(long courseId) {
		long userId = users.requireCurrentUser().id();
		ownedMetadata(courseId, userId);
		return runs.latestCompletedRunId(courseId)
				.flatMap(id -> runs.find(courseId, id))
				.orElse(null);
	}

	public void assertFreshPassingRun(long courseId) {
		long userId = users.requireCurrentUser().id();
		ReadinessContext current = ownedContext(courseId, userId);
		ReadinessRunView latest = runs.latestCompletedRunId(courseId)
				.flatMap(id -> runs.find(courseId, id))
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						HttpStatus.CONFLICT, "Run a readiness check before publishing."));
		if (!latest.fingerprint().equals(fingerprint.calculate(current))) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,
					"The course has changed since its latest readiness check. Run readiness again.");
		}
		if (latest.finishedAt() == null
				|| latest.finishedAt().plus(settings.getReport().getMaxAge()).isBefore(Instant.now())) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,
					"The latest readiness report has expired. Run readiness again.");
		}
		if (latest.blockingCount() > 0) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.CONFLICT,
					"The latest readiness check contains blocking issues.");
		}
	}

	private void execute(long runId, ReadinessContext context) {
		try {
			java.util.ArrayList<ReadinessIssue> issues = new java.util.ArrayList<>();
			for (ReadinessRule rule : rules) {
				issues.addAll(rule.evaluate(context));
			}
			runs.complete(runId, issues.subList(0, Math.min(issues.size(), settings.getReport().getMaxIssues())));
		} catch (RuntimeException exception) {
			runs.fail(runId, "Readiness check failed. Review server logs for the run ID " + runId + ".");
			org.slf4j.LoggerFactory.getLogger(ReadinessService.class)
					.error("Readiness run {} failed", runId, exception);
		}
	}

	private ReadinessContext ownedContext(long courseId, long userId) {
		CourseMetadataSnapshot metadata = ownedMetadata(courseId, userId);
		return new ReadinessContext(metadata, structure.getStructure(courseId),
				resources.getCourseResourceState(courseId));
	}

	private CourseMetadataSnapshot ownedMetadata(long courseId, long userId) {
		CourseMetadataSnapshot metadata = structure.getCourseMetadata(courseId)
				.filter(value -> !value.deleted())
				.orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
						HttpStatus.NOT_FOUND, "The requested resource was not found."));
		ownership.requireOwnerOrNotFound(metadata.ownerId(), userId);
		return metadata;
	}
}
