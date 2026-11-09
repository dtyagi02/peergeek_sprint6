import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { Button } from "../../shared/components/Button";
import { ErrorState } from "../../shared/components/ErrorState";
import { Spinner } from "../../shared/components/Spinner";
import { useToast } from "../../shared/components/useToast";
import { getCourse } from "../instructor-courses/courseApi";
import {
  getLatestReadinessRun,
  getReadinessRun,
  publishCourse,
  startReadinessRun,
  unpublishCourse,
} from "./publishApi";

export function PublishReadinessPanel() {
  const { courseId } = useParams();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [runId, setRunId] = useState(null);
  const [mutationError, setMutationError] = useState(null);

  const courseQuery = useQuery({
    queryKey: ["instructor-course", courseId],
    queryFn: () => getCourse(courseId),
  });

  // Query for previously completed readiness run on mount
  const latestQuery = useQuery({
    queryKey: ["readiness-run-latest", courseId],
    queryFn: () => getLatestReadinessRun(courseId),
    enabled: !runId,
  });

  const runQuery = useQuery({
    queryKey: ["readiness-run", courseId, runId],
    queryFn: () => getReadinessRun(courseId, runId),
    enabled: Boolean(runId),
    refetchInterval: (query) =>
      query.state.data?.status === "RUNNING" ? 1200 : false,
  });

  const readinessMutation = useMutation({
    mutationFn: () => {
      setMutationError(null);
      return startReadinessRun(courseId);
    },
    onSuccess: (run) => {
      setRunId(run.id);
      queryClient.invalidateQueries({
        queryKey: ["readiness-run-latest", courseId],
      });
    },
    onError: (error) => {
      setMutationError(error.message);
      toast.notify({
        title: "Readiness check could not start",
        description: error.message,
      });
    },
  });

  const updateStatus = useMutation({
    mutationFn: (action) =>
      action === "publish"
        ? publishCourse(courseId)
        : unpublishCourse(courseId),
    onSuccess: async (course) => {
      await queryClient.invalidateQueries({
        queryKey: ["instructor-course", courseId],
      });
      toast.notify({
        title:
          course.status === "PUBLISHED"
            ? "Course published"
            : "Course unpublished",
        description:
          course.status === "PUBLISHED"
            ? "Learners can now access this course."
            : "Learner access to this course has been disabled.",
      });
    },
    onError: (error) =>
      toast.notify({
        title: "Course status was not changed",
        description: error.message,
      }),
  });

  const activeRun = runId ? runQuery.data : latestQuery.data;
  const isChecking =
    readinessMutation.isPending || activeRun?.status === "RUNNING";
  const canPublish =
    activeRun?.status === "COMPLETED" && activeRun.blockingCount === 0;
  const currentStatus = courseQuery.data?.status;

  return (
    <section
      aria-label="Readiness and publishing"
      className="my-5 rounded-lg border border-surface-200 bg-surface-50 p-4"
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 className="text-lg font-semibold text-ink-900">
            Readiness &amp; publishing
          </h2>
          <p className="mt-1 text-sm text-ink-700">
            Check the course before publishing. Any content change makes the
            previous report stale.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            data-testid="check-course-readiness-btn"
            aria-label="Check Course Readiness"
            disabled={isChecking}
            onClick={() => readinessMutation.mutate()}
            variant="secondary"
          >
            {isChecking ? "Checking readiness…" : "Check Course Readiness"}
          </Button>
          <Button
            disabled={
              updateStatus.isPending ||
              (currentStatus !== "PUBLISHED" && !canPublish)
            }
            onClick={() =>
              updateStatus.mutate(
                currentStatus === "PUBLISHED" ? "unpublish" : "publish",
              )
            }
          >
            {currentStatus === "PUBLISHED" ? "Unpublish" : "Publish"}
          </Button>
          <Link
            className="inline-flex min-h-10 items-center justify-center rounded-md border border-surface-200 bg-white px-4 py-2 text-sm font-semibold text-ink-900 hover:bg-surface-100"
            to={`/instructor/courses/${courseId}/preview`}
          >
            Preview as learner
          </Link>
        </div>
      </div>

      {/* Mutation Error / Failed to Start */}
      {mutationError && (
        <div
          role="alert"
          aria-live="assertive"
          className="mt-4 rounded-lg border border-danger-200 bg-danger-50 p-4"
        >
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <h3 className="text-sm font-semibold text-danger-900">
                Readiness check could not start
              </h3>
              <p className="mt-1 text-sm text-danger-700">{mutationError}</p>
            </div>
            <Button
              size="sm"
              variant="secondary"
              onClick={() => readinessMutation.mutate()}
            >
              Retry check
            </Button>
          </div>
        </div>
      )}

      {/* Active Checking State */}
      {isChecking && (
        <div
          role="status"
          aria-live="polite"
          className="mt-4 flex items-center gap-3 rounded-lg border border-brand-200 bg-brand-50/50 p-4"
        >
          <Spinner label="Checking course readiness" />
          <div>
            <p className="text-sm font-semibold text-brand-900">
              Checking course readiness…
            </p>
            <p className="text-xs text-brand-700">
              Validating course structure, modules, lessons, and content requirements.
            </p>
          </div>
        </div>
      )}

      {/* Report Available State */}
      {!isChecking && activeRun && (
        <div
          aria-live="polite"
          className="mt-4 border-t border-surface-200 pt-4"
        >
          {runQuery.isError ? (
            <ErrorState
              description="The readiness report could not be loaded."
              title="Report unavailable"
            />
          ) : activeRun.status === "FAILED" ? (
            <div
              role="alert"
              className="rounded-lg border border-danger-200 bg-danger-50 p-4"
            >
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-semibold text-danger-900">
                    Readiness check failed
                  </h3>
                  <p className="mt-1 text-sm text-danger-700">
                    {activeRun.failureReason ||
                      "An internal error occurred during validation."}
                  </p>
                </div>
                <Button
                  size="sm"
                  variant="secondary"
                  onClick={() => readinessMutation.mutate()}
                >
                  Retry check
                </Button>
              </div>
            </div>
          ) : (
            <>
              {/* Ready Success Banner */}
              {canPublish ? (
                <div
                  role="status"
                  className="rounded-lg border border-emerald-200 bg-emerald-50/80 p-4"
                >
                  <div className="flex items-start gap-3">
                    <span
                      aria-hidden="true"
                      className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-emerald-600 text-xs font-bold text-white"
                    >
                      ✓
                    </span>
                    <div className="flex-1">
                      <h3 className="text-sm font-semibold text-emerald-900">
                        Course is ready for publication!
                      </h3>
                      <p className="mt-0.5 text-sm text-emerald-700">
                        All course structure, module, and lesson requirements are satisfied.
                        You can now publish this course for learners.
                      </p>
                      {activeRun.warningCount > 0 && (
                        <p className="mt-2 text-xs font-medium text-amber-800">
                          Note: There{" "}
                          {activeRun.warningCount === 1
                            ? "is 1 non-blocking warning"
                            : `are ${activeRun.warningCount} non-blocking warnings`}{" "}
                          below, but they do not prevent publication.
                        </p>
                      )}
                    </div>
                  </div>
                </div>
              ) : (
                /* Blocking Issues Alert */
                <div
                  role="alert"
                  className="rounded-lg border border-danger-200 bg-danger-50/80 p-3.5"
                >
                  <div className="flex items-center gap-2">
                    <span
                      aria-hidden="true"
                      className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-danger-600 text-xs font-bold text-white"
                    >
                      !
                    </span>
                    <h3 className="text-sm font-semibold text-danger-900">
                      Course is not ready to publish ({activeRun.blockingCount}{" "}
                      blocking{" "}
                      {activeRun.blockingCount === 1 ? "issue" : "issues"})
                    </h3>
                  </div>
                  <p className="mt-1 text-xs text-danger-700">
                    Resolve the incomplete course content or structure issues below before publishing.
                  </p>
                </div>
              )}

              {/* Issues List */}
              {activeRun.issues && activeRun.issues.length > 0 && (
                <ul
                  aria-label="Readiness issues"
                  className="mt-3 grid gap-2.5"
                >
                  {activeRun.issues.map((issue, idx) => (
                    <li
                      className="rounded-lg border border-surface-200 bg-white p-3.5 shadow-xs"
                      key={`${issue.ruleCode || issue.code}-${issue.moduleId ?? ""}-${issue.lessonId ?? ""}-${issue.resourceId ?? ""}-${idx}`}
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span
                            className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${
                              issue.severity === "BLOCKING_ERROR"
                                ? "bg-danger-100 text-danger-800"
                                : "bg-amber-100 text-amber-800"
                            }`}
                          >
                            {issue.severity === "BLOCKING_ERROR"
                              ? "Blocking"
                              : "Warning"}
                          </span>
                          {issue.scope && (
                            <span className="rounded bg-surface-100 px-2 py-0.5 text-2xs font-semibold uppercase tracking-wider text-ink-600">
                              {issue.scope}
                            </span>
                          )}
                        </div>
                        {/* Navigation link to module/lesson or course details */}
                        {issue.scope === "MODULE" ||
                        issue.scope === "LESSON" ||
                        issue.moduleId ||
                        issue.lessonId ? (
                          <Link
                            className="inline-flex items-center gap-1 rounded bg-surface-100 px-2 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-50 hover:text-brand-900 transition"
                            to={`/instructor/courses/${courseId}?tab=outline`}
                          >
                            Go to Outline →
                          </Link>
                        ) : issue.scope === "COURSE" &&
                          (issue.ruleCode || issue.code)?.startsWith(
                            "COURSE_",
                          ) ? (
                          <Link
                            className="inline-flex items-center gap-1 rounded bg-surface-100 px-2 py-1 text-xs font-semibold text-brand-700 hover:bg-brand-50 hover:text-brand-900 transition"
                            to={`/instructor/courses/${courseId}?tab=details`}
                          >
                            Edit Details →
                          </Link>
                        ) : null}
                      </div>

                      {/* Module / Lesson context headers */}
                      {(issue.moduleTitle ||
                        issue.lessonTitle ||
                        issue.resourceTitle) && (
                        <div className="mt-2 flex flex-wrap gap-x-4 text-xs font-medium text-ink-600">
                          {issue.moduleTitle && (
                            <span>
                              Module:{" "}
                              <strong className="text-ink-900">
                                {issue.moduleTitle}
                              </strong>
                            </span>
                          )}
                          {issue.lessonTitle && (
                            <span>
                              Lesson:{" "}
                              <strong className="text-ink-900">
                                {issue.lessonTitle}
                              </strong>
                            </span>
                          )}
                          {issue.resourceTitle && (
                            <span>
                              Resource:{" "}
                              <strong className="text-ink-900">
                                {issue.resourceTitle}
                              </strong>
                            </span>
                          )}
                        </div>
                      )}

                      <p className="mt-1.5 text-sm font-medium text-ink-900">
                        {issue.message}
                      </p>

                      {issue.fixHint && (
                        <p className="mt-1 rounded border border-surface-200 bg-surface-50 p-2 text-xs text-ink-700">
                          <span className="font-semibold text-brand-700">
                            Fix:
                          </span>{" "}
                          {issue.fixHint}
                        </p>
                      )}
                    </li>
                  ))}
                </ul>
              )}
            </>
          )}
        </div>
      )}
    </section>
  );
}
