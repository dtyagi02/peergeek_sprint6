import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getCourseAssignments } from "./assignmentApi";
import { InstructorModeBanner } from "./InstructorModeBanner";
import { Spinner } from "../../shared/components/Spinner";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";

export function CourseAssignmentsPage() {
  const { courseId } = useParams();

  const assignmentsQuery = useQuery({
    queryKey: ["learner", "course-assignments", courseId],
    queryFn: () => getCourseAssignments(courseId),
  });

  const assignments = assignmentsQuery.data || [];

  return (
    <div className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
      {/* Mode Switcher Banner */}
      <InstructorModeBanner />

      {/* Header & Back link */}
      <div className="mb-6">
        <Link
          to={`/learning/courses/${courseId}`}
          className="text-sm font-medium text-brand-600 hover:text-brand-700"
        >
          ← Back to Course Player
        </Link>
        <div className="mt-2 flex flex-wrap items-center justify-between gap-4">
          <div>
            <h1 className="text-2xl font-bold tracking-tight text-ink-900 sm:text-3xl">
              Course Assignments & Assessments
            </h1>
            <p className="mt-1 text-sm text-ink-600">
              Complete these assignments to test and demonstrate your knowledge.
            </p>
          </div>
        </div>
      </div>

      {/* Main Content */}
      {assignmentsQuery.isLoading ? (
        <div className="flex justify-center py-20">
          <Spinner label="Loading course assignments..." />
        </div>
      ) : assignmentsQuery.isError ? (
        <ErrorState
          title="Failed to load assignments"
          message={assignmentsQuery.error?.message}
          onRetry={() => assignmentsQuery.refetch()}
        />
      ) : assignments.length === 0 ? (
        <EmptyState
          title="No assignments posted yet"
          description="The instructor has not published any assignments or assessments for this course yet."
        />
      ) : (
        <div className="space-y-4">
          {assignments.map((item) => {
            const dueDate = item.dueAt ? new Date(item.dueAt) : null;
            const isOverdue =
              dueDate &&
              dueDate < new Date() &&
              item.status !== "GRADED" &&
              item.status !== "SUBMITTED";

            return (
              <div
                key={item.id}
                className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-surface-200 bg-white p-5 shadow-sm transition hover:border-brand-300 hover:shadow-md"
              >
                {/* Details */}
                <div className="flex-1 min-w-[280px]">
                  <div className="flex items-center gap-2">
                    <span className="rounded-md bg-brand-50 px-2 py-0.5 text-xs font-semibold text-brand-700">
                      {item.kind === "PROJECT" ? "Project" : "Assessment"}
                    </span>
                    {item.timeLimitMinutes && (
                      <span className="rounded-md bg-purple-50 px-2 py-0.5 text-xs font-semibold text-purple-700">
                        ⏱ {item.timeLimitMinutes} min
                      </span>
                    )}
                  </div>

                  <h2 className="mt-2 text-base font-bold text-ink-900">
                    {item.title}
                  </h2>

                  <div className="mt-2 flex flex-wrap items-center gap-3 text-xs text-ink-600">
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
                    {item.late && (
                      <span className="rounded-full bg-rose-100 px-2 py-0.5 font-semibold text-rose-800">
                        Late
                      </span>
                    )}

                    {/* Deadline */}
                    {dueDate && (
                      <span
                        className={
                          isOverdue ? "font-semibold text-rose-600" : ""
                        }
                      >
                        {isOverdue ? "Overdue: " : "Due: "}
                        {dueDate.toLocaleString(undefined, {
                          month: "short",
                          day: "numeric",
                          hour: "numeric",
                          minute: "2-digit",
                        })}
                      </span>
                    )}
                  </div>
                </div>

                {/* Score & Action */}
                <div className="flex items-center gap-6">
                  <div className="text-right">
                    <span className="text-xs uppercase tracking-wider text-ink-500">
                      Score
                    </span>
                    <p className="text-base font-bold text-ink-900">
                      {item.status === "GRADED"
                        ? `${item.totalPoints ?? 0} / ${item.maxPoints} pts`
                        : `${item.maxPoints} pts`}
                    </p>
                  </div>

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
        </div>
      )}
    </div>
  );
}
