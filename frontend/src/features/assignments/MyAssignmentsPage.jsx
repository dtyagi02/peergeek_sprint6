import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getCrossCourseAssignments } from "./assignmentApi";
import { InstructorModeBanner } from "./InstructorModeBanner";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";

export function MyAssignmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const activeTab = searchParams.get("tab") || "todo";
  const [page, setPage] = useState(0);
  const pageSize = 20;

  const assignmentsQuery = useQuery({
    queryKey: [
      "learner",
      "cross-course-assignments",
      activeTab,
      page,
      pageSize,
    ],
    queryFn: () =>
      getCrossCourseAssignments({
        filter: activeTab,
        page,
        size: pageSize,
      }),
  });

  const handleTabChange = (tab) => {
    setSearchParams(tab === "todo" ? {} : { tab });
    setPage(0);
  };

  const pageData = assignmentsQuery.data;
  const assignments = pageData?.content || [];

  const formatDueDate = (dueAtStr) => {
    if (!dueAtStr) return null;
    const dueDate = new Date(dueAtStr);
    const now = new Date();
    const isOverdue = dueDate < now;
    const isDueSoon =
      !isOverdue && dueDate.getTime() - now.getTime() < 48 * 3600 * 1000;

    const formatted = dueDate.toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit",
    });

    return {
      formatted,
      isOverdue,
      isDueSoon,
    };
  };

  return (
    <div className="mx-auto max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Mode Switcher Banner (if user in INSTRUCTOR mode) */}
      <InstructorModeBanner />

      {/* Header */}
      <div className="mb-6">
        <h1 className="text-3xl font-bold tracking-tight text-ink-900">
          My Assignments
        </h1>
        <p className="mt-1 text-sm text-ink-600">
          Track upcoming deadlines, in-progress tasks, and grades across all
          your courses.
        </p>
      </div>

      {/* Filter Tabs */}
      <div className="mb-6 flex border-b border-surface-200">
        {[
          { id: "todo", label: "To Do" },
          { id: "submitted", label: "Submitted" },
          { id: "graded", label: "Graded" },
        ].map((tab) => (
          <button
            key={tab.id}
            type="button"
            onClick={() => handleTabChange(tab.id)}
            className={`border-b-2 px-5 py-3 text-sm font-semibold transition ${
              activeTab === tab.id
                ? "border-brand-600 text-brand-600"
                : "border-transparent text-ink-600 hover:border-surface-300 hover:text-ink-900"
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {/* Content Area */}
      {assignmentsQuery.isLoading ? (
        <div className="flex justify-center py-20">
          <Spinner label="Loading assignments..." />
        </div>
      ) : assignmentsQuery.isError ? (
        <ErrorState
          title="Failed to load assignments"
          message={assignmentsQuery.error?.message}
          onRetry={() => assignmentsQuery.refetch()}
        />
      ) : assignments.length === 0 ? (
        <EmptyState
          title={`No ${activeTab === "todo" ? "pending" : activeTab} assignments`}
          description={
            activeTab === "todo"
              ? "You're all caught up! There are no assignments waiting for you to complete."
              : `You don't have any assignments in ${activeTab} status.`
          }
        />
      ) : (
        <div className="space-y-4">
          {assignments.map((item) => {
            const dueInfo = formatDueDate(item.dueAt);

            return (
              <div
                key={item.id}
                className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:border-brand-300 hover:shadow-md"
              >
                {/* Left: Course & Title & Badges */}
                <div className="flex-1 min-w-[280px]">
                  <span className="text-xs font-semibold uppercase tracking-wider text-brand-600">
                    {item.courseTitle || "Course"}
                  </span>
                  <h2 className="mt-1 text-base font-bold text-ink-900">
                    {item.title}
                  </h2>

                  <div className="mt-2.5 flex flex-wrap items-center gap-2 text-xs">
                    {/* Kind badge */}
                    <span className="rounded-md bg-surface-100 px-2 py-0.5 font-medium text-ink-700">
                      {item.kind === "PROJECT" ? "Project" : "Assessment"}
                    </span>

                    {/* Status Badge */}
                    {item.status === "NOT_STARTED" && (
                      <span className="rounded-full bg-surface-100 px-2.5 py-0.5 font-medium text-ink-600">
                        Not Started
                      </span>
                    )}
                    {item.status === "IN_PROGRESS" && (
                      <span className="rounded-full bg-blue-100 px-2.5 py-0.5 font-semibold text-blue-800">
                        In Progress
                      </span>
                    )}
                    {item.status === "SUBMITTED" && (
                      <span className="rounded-full bg-amber-100 px-2.5 py-0.5 font-semibold text-amber-800">
                        Submitted
                      </span>
                    )}
                    {item.status === "GRADED" && (
                      <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 font-semibold text-emerald-800">
                        Graded
                      </span>
                    )}

                    {/* Late badge */}
                    {item.late && (
                      <span className="rounded-full bg-rose-100 px-2 py-0.5 font-semibold text-rose-800">
                        Late
                      </span>
                    )}

                    {/* Time limit chip */}
                    {item.timeLimitMinutes && (
                      <span className="rounded-md bg-purple-50 px-2 py-0.5 font-medium text-purple-700">
                        ⏱ {item.timeLimitMinutes} min
                      </span>
                    )}
                  </div>
                </div>

                {/* Center: Due date / Scoring */}
                <div className="text-left sm:text-right min-w-[160px]">
                  {/* Score if graded */}
                  {item.status === "GRADED" ? (
                    <div>
                      <span className="text-xs uppercase tracking-wider text-ink-500">
                        Score
                      </span>
                      <p className="text-base font-extrabold text-emerald-700">
                        {item.totalPoints !== null &&
                        item.totalPoints !== undefined
                          ? item.totalPoints
                          : 0}{" "}
                        / {item.maxPoints} pts
                      </p>
                    </div>
                  ) : (
                    <div>
                      <span className="text-xs uppercase tracking-wider text-ink-500">
                        Due Date
                      </span>
                      {dueInfo ? (
                        <p
                          className={`text-sm font-semibold ${
                            dueInfo.isOverdue
                              ? "text-rose-600"
                              : dueInfo.isDueSoon
                                ? "text-amber-600"
                                : "text-ink-800"
                          }`}
                        >
                          {dueInfo.isOverdue && "⚠ Overdue: "}
                          {dueInfo.isDueSoon && "⏳ Due soon: "}
                          {dueInfo.formatted}
                        </p>
                      ) : (
                        <p className="text-sm font-medium text-ink-600">
                          No deadline
                        </p>
                      )}
                      <span className="text-xs text-ink-400">
                        Max: {item.maxPoints} pts
                      </span>
                    </div>
                  )}
                </div>

                {/* Right: Action Button */}
                <div>
                  <Link
                    to={`/learning/assignments/${item.id}`}
                    className={`inline-flex items-center justify-center rounded-lg px-4 py-2 text-xs font-semibold shadow-sm transition ${
                      item.status === "NOT_STARTED"
                        ? "bg-brand-600 text-white hover:bg-brand-700"
                        : item.status === "IN_PROGRESS"
                          ? "bg-blue-600 text-white hover:bg-blue-700"
                          : "bg-surface-100 text-ink-800 hover:bg-surface-200"
                    }`}
                  >
                    {item.status === "NOT_STARTED" && "Start"}
                    {item.status === "IN_PROGRESS" && "Resume"}
                    {item.status === "SUBMITTED" && "View Submission"}
                    {item.status === "GRADED" && "View Results"}
                  </Link>
                </div>
              </div>
            );
          })}

          {/* Pagination */}
          {pageData && pageData.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-surface-200 pt-4 text-xs text-ink-600">
              <div>
                Page {page + 1} of {pageData.totalPages} (
                {pageData.totalElements} total)
              </div>
              <div className="flex items-center gap-2">
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  variant="secondary"
                  size="sm"
                  disabled={pageData.last || page + 1 >= pageData.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
