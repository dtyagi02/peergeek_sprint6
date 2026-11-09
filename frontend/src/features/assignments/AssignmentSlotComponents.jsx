import { Link, useParams } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { getCourseAssignments } from "./assignmentApi";

export function CourseEditorAssignmentsHeaderLink(props) {
  const params = useParams();
  const courseId = props.courseId || params.courseId;
  if (!courseId) return null;

  return (
    <Link
      to={`/instructor/courses/${courseId}/assignments`}
      className="inline-flex items-center gap-1.5 rounded-lg border border-surface-300 bg-white px-3 py-1.5 text-xs font-semibold text-ink-700 shadow-sm transition hover:bg-surface-50 hover:text-ink-900"
    >
      Assignments
    </Link>
  );
}

export function PlayerHeaderAssignmentsLink(props) {
  const params = useParams();
  const courseId = props.courseId || params.courseId;
  if (!courseId) return null;

  return (
    <Link
      to={`/learning/courses/${courseId}/assignments`}
      className="inline-flex items-center gap-1.5 rounded-lg bg-surface-100 px-3 py-1.5 text-xs font-medium text-ink-700 transition hover:bg-surface-200 hover:text-ink-900"
    >
      Assignments
    </Link>
  );
}

export function LessonAssignmentsAction({ courseId, lessonId }) {
  const { data: assignments = [] } = useQuery({
    queryKey: ["learner", "lesson-assignments", courseId, lessonId],
    queryFn: () => getCourseAssignments(courseId, { lessonId }),
    enabled: Boolean(courseId && lessonId),
  });

  if (!assignments || assignments.length === 0) {
    return null;
  }

  return (
    <div className="rounded-lg border border-brand-200 bg-brand-50/60 p-3.5">
      <h4 className="text-xs font-semibold text-brand-900">
        Assignments for this lesson ({assignments.length})
      </h4>
      <ul className="mt-2 space-y-2">
        {assignments.map((a) => (
          <li key={a.id} className="flex items-center justify-between text-xs">
            <Link
              to={`/learning/assignments/${a.id}`}
              className="font-medium text-brand-700 hover:underline truncate max-w-[240px]"
            >
              {a.title}
            </Link>
            <span className="rounded bg-white/80 px-2 py-0.5 text-2xs font-semibold uppercase text-ink-600 border border-brand-100">
              {a.status === "NOT_STARTED" ? "Not started" : a.status}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}
