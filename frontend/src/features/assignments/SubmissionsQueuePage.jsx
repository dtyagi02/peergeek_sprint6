import { useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { listSubmissions, listInstructorAssignments } from "./assignmentApi";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";

export function SubmissionsQueuePage() {
  const { courseId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();

  const selectedAssignmentId = searchParams.get("assignmentId") || "";
  const selectedStatus = searchParams.get("status") || "SUBMITTED";
  const [page, setPage] = useState(0);
  const pageSize = 20;

  // Fetch assignments list for filter dropdown
  const assignmentsQuery = useQuery({
    queryKey: ["instructor", "assignments", courseId],
    queryFn: () => listInstructorAssignments(courseId, { size: 100 }),
  });

  // Fetch submissions queue
  const submissionsQuery = useQuery({
    queryKey: [
      "instructor",
      "submissions",
      courseId,
      selectedAssignmentId,
      selectedStatus,
      page,
      pageSize,
    ],
    queryFn: () =>
      listSubmissions(courseId, {
        assignmentId: selectedAssignmentId || undefined,
        status: selectedStatus,
        page,
        size: pageSize,
        sort: "submittedAt,asc",
      }),
  });

  const handleAssignmentFilterChange = (e) => {
    const val = e.target.value;
    const next = new URLSearchParams(searchParams);
    if (val) {
      next.set("assignmentId", val);
    } else {
      next.delete("assignmentId");
    }
    setPage(0);
    setSearchParams(next);
  };

  const handleStatusFilterChange = (status) => {
    const next = new URLSearchParams(searchParams);
    next.set("status", status);
    setPage(0);
    setSearchParams(next);
  };

  const assignments = assignmentsQuery.data?.content || [];
  const submissionsPage = submissionsQuery.data;
  const submissions = submissionsPage?.content || [];

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Header & Breadcrumb */}
      <div className="mb-6">
        <Link
          to={`/instructor/courses/${courseId}/assignments`}
          className="text-sm font-medium text-brand-600 hover:text-brand-700"
        >
          ← Back to Course Assignments
        </Link>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-ink-900">
              Submissions Queue
            </h1>
            <p className="mt-1 text-sm text-ink-600">
              Review and grade submissions for this course.
            </p>
          </div>
        </div>
      </div>

      {/* Filter Toolbar */}
      <div className="mb-6 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-surface-200 bg-white p-4 shadow-sm">
        {/* Status Tabs */}
        <div className="flex flex-wrap items-center gap-2">
          <span className="mr-2 text-xs font-semibold uppercase tracking-wider text-ink-500">
            Status:
          </span>
          {[
            { id: "SUBMITTED", label: "Pending Review" },
            { id: "GRADED", label: "Graded" },
            { id: "IN_PROGRESS", label: "In Progress" },
          ].map((tab) => (
            <button
              key={tab.id}
              type="button"
              onClick={() => handleStatusFilterChange(tab.id)}
              className={`rounded-lg px-3 py-1.5 text-xs font-medium transition ${
                selectedStatus === tab.id
                  ? "bg-brand-600 text-white shadow-sm"
                  : "bg-surface-100 text-ink-700 hover:bg-surface-200"
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Assignment Filter */}
        <div className="flex items-center gap-2">
          <label
            htmlFor="assignment-filter"
            className="text-xs font-semibold text-ink-700"
          >
            Assignment:
          </label>
          <select
            id="assignment-filter"
            value={selectedAssignmentId}
            onChange={handleAssignmentFilterChange}
            className="rounded-lg border border-surface-300 bg-white px-3 py-1.5 text-xs text-ink-900 shadow-sm focus:border-brand-500 focus:outline-none focus:ring-1 focus:ring-brand-500"
          >
            <option value="">All Assignments</option>
            {assignments.map((a) => (
              <option key={a.id} value={a.id}>
                {a.title} ({a.kind})
              </option>
            ))}
          </select>
        </div>
      </div>

      {/* Content Area */}
      {submissionsQuery.isLoading ? (
        <div className="flex justify-center py-16">
          <Spinner label="Loading submissions..." />
        </div>
      ) : submissionsQuery.isError ? (
        <ErrorState
          title="Failed to load submissions"
          message={submissionsQuery.error?.message}
          onRetry={() => submissionsQuery.refetch()}
        />
      ) : submissions.length === 0 ? (
        <EmptyState
          title="No submissions found"
          description={
            selectedStatus === "SUBMITTED"
              ? "All submitted assignments for this course have been graded!"
              : `No submissions currently in ${selectedStatus.toLowerCase()} status.`
          }
        />
      ) : (
        <div className="overflow-hidden rounded-xl border border-surface-200 bg-white shadow-sm">
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-surface-200 text-left text-sm">
              <thead className="bg-surface-50 text-xs font-semibold uppercase tracking-wider text-ink-500">
                <tr>
                  <th scope="col" className="px-6 py-3">
                    Learner
                  </th>
                  <th scope="col" className="px-6 py-3">
                    Assignment
                  </th>
                  <th scope="col" className="px-6 py-3">
                    Status & Badges
                  </th>
                  <th scope="col" className="px-6 py-3">
                    Submitted
                  </th>
                  <th scope="col" className="px-6 py-3">
                    Score
                  </th>
                  <th scope="col" className="px-6 py-3 text-right">
                    Action
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-200">
                {submissions.map((sub) => {
                  return (
                    <tr
                      key={sub.submissionId}
                      className="transition hover:bg-surface-50/50"
                    >
                      {/* Learner */}
                      <td className="whitespace-nowrap px-6 py-4 font-medium text-ink-900">
                        <div className="flex items-center gap-2">
                          <span className="flex h-7 w-7 items-center justify-center rounded-full bg-brand-100 text-xs font-bold text-brand-700">
                            {(sub.learner?.displayName || "L")
                              .charAt(0)
                              .toUpperCase()}
                          </span>
                          <span>
                            {sub.learner?.displayName || "Anonymous Learner"}
                          </span>
                        </div>
                      </td>

                      {/* Assignment */}
                      <td className="px-6 py-4">
                        <div className="font-medium text-ink-900">
                          {sub.assignmentTitle}
                        </div>
                      </td>

                      {/* Status & Badges */}
                      <td className="whitespace-nowrap px-6 py-4">
                        <div className="flex flex-wrap items-center gap-1.5">
                          {sub.status === "SUBMITTED" && (
                            <span className="rounded-full bg-amber-100 px-2.5 py-0.5 text-xs font-medium text-amber-800">
                              Submitted
                            </span>
                          )}
                          {sub.status === "GRADED" && (
                            <span className="rounded-full bg-emerald-100 px-2.5 py-0.5 text-xs font-medium text-emerald-800">
                              Graded
                            </span>
                          )}
                          {sub.status === "IN_PROGRESS" && (
                            <span className="rounded-full bg-slate-100 px-2.5 py-0.5 text-xs font-medium text-slate-800">
                              In Progress
                            </span>
                          )}

                          {sub.late && (
                            <span className="rounded-full bg-rose-100 px-2 py-0.5 text-xs font-semibold text-rose-800">
                              Late
                            </span>
                          )}
                          {sub.autoSubmitted && (
                            <span className="rounded-full bg-purple-100 px-2 py-0.5 text-xs font-semibold text-purple-800">
                              Auto-submitted
                            </span>
                          )}
                          {sub.autoGraded && (
                            <span className="rounded-full bg-teal-100 px-2 py-0.5 text-xs font-semibold text-teal-800">
                              Auto-graded
                            </span>
                          )}
                        </div>
                      </td>

                      {/* Submitted At */}
                      <td className="whitespace-nowrap px-6 py-4 text-ink-600">
                        {sub.submittedAt
                          ? new Date(sub.submittedAt).toLocaleString(
                              undefined,
                              {
                                dateStyle: "medium",
                                timeStyle: "short",
                              },
                            )
                          : "—"}
                      </td>

                      {/* Score */}
                      <td className="whitespace-nowrap px-6 py-4 font-medium text-ink-900">
                        {sub.totalPoints !== null &&
                        sub.totalPoints !== undefined ? (
                          <span className="text-emerald-700">
                            {sub.totalPoints} / {sub.maxPoints} pts
                          </span>
                        ) : (
                          <span className="text-ink-400">
                            — / {sub.maxPoints} pts
                          </span>
                        )}
                      </td>

                      {/* Action */}
                      <td className="whitespace-nowrap px-6 py-4 text-right">
                        {sub.status === "IN_PROGRESS" ? (
                          <span className="text-xs text-ink-400 italic">
                            In progress
                          </span>
                        ) : (
                          <Link
                            to={`/instructor/submissions/${sub.submissionId}`}
                            className="inline-flex items-center rounded-lg bg-brand-50 px-3 py-1.5 text-xs font-semibold text-brand-700 transition hover:bg-brand-100 hover:text-brand-800"
                          >
                            {sub.status === "GRADED" ? "Review Grade" : "Grade"}{" "}
                            →
                          </Link>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {submissionsPage && (submissionsPage.totalPages > 1 || page > 0) && (
            <div className="flex items-center justify-between border-t border-surface-200 px-6 py-3 text-xs text-ink-600">
              <div>
                Page {page + 1} of {Math.max(submissionsPage.totalPages, 1)} (
                {submissionsPage.totalElements} total)
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
                  disabled={
                    submissionsPage.last ||
                    page + 1 >= submissionsPage.totalPages
                  }
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
