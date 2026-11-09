import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { apiRequest } from "../../shared/api/client";
import { Button } from "../../shared/components/Button";
import { ErrorState } from "../../shared/components/ErrorState";
import { Spinner } from "../../shared/components/Spinner";

export function InstructorStudentDetailPage() {
  const { courseId, studentId } = useParams();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);

    apiRequest(`instructor/courses/${courseId}/students/${studentId}`)
      .then((res) => {
        if (active) setData(res);
      })
      .catch((err) => {
        if (active) setError(err.message || "Failed to load student course details");
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [courseId, studentId]);

  if (loading) return <Spinner label="Loading student course performance" />;

  if (error || !data) {
    return (
      <div className="mx-auto max-w-4xl py-8">
        <ErrorState
          title="Student Record Unavailable"
          description={error || "You are not authorized to view this student's records or the student is not enrolled in this course."}
        />
        <div className="mt-4">
          <Link to={`/instructor/courses`}>
            <Button variant="secondary">Back to Instructor Courses</Button>
          </Link>
        </div>
      </div>
    );
  }

  const { student, courseTitle, enrollmentStatus, enrolledAt, lastAccessedAt, totalLessons, completedLessons, progressPercent, completedAt, submissions } = data;

  return (
    <div className="mx-auto max-w-4xl space-y-6 py-6">
      <div className="flex items-center justify-between border-b border-surface-200 pb-4">
        <div>
          <Link
            className="text-sm font-medium text-brand-600 hover:text-brand-700"
            to={`/instructor/courses`}
          >
            &larr; Back to Instructor Courses
          </Link>
          <h1 className="mt-1 text-2xl font-bold text-ink-900">
            Student Course Performance
          </h1>
          <p className="text-sm text-ink-500">
            Course: <span className="font-semibold text-ink-700">{courseTitle}</span>
          </p>
        </div>
        <span className="inline-flex items-center rounded-full bg-brand-50 px-3 py-1 text-xs font-semibold text-brand-700">
          Enrolled: {enrollmentStatus}
        </span>
      </div>

      {/* Student Identity Card */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center gap-4 rounded-xl border border-surface-200 bg-white p-5 shadow-sm">
        {student.avatarUrl ? (
          <img
            alt={student.displayName}
            className="h-16 w-16 rounded-full object-cover border border-surface-200"
            src={student.avatarUrl}
          />
        ) : (
          <div className="flex h-16 w-16 items-center justify-center rounded-full bg-brand-100 text-xl font-bold text-brand-700 border border-brand-200">
            {student.displayName?.charAt(0) || "U"}
          </div>
        )}
        <div className="flex-1">
          <div className="flex items-center gap-2">
            <h2 className="text-xl font-bold text-ink-900">{student.displayName}</h2>
            <Link
              to={`/profile/${student.username}`}
              className="text-sm font-medium text-brand-600 hover:underline"
            >
              @{student.username}
            </Link>
          </div>
          {student.headline && (
            <p className="text-sm text-ink-600">{student.headline}</p>
          )}
          {student.bio && (
            <p className="mt-1 text-xs text-ink-500 line-clamp-2">{student.bio}</p>
          )}
        </div>
      </div>

      {/* Progress & Metrics */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="rounded-xl border border-surface-200 bg-white p-4 shadow-sm">
          <span className="text-xs font-medium text-ink-500">Course Progress</span>
          <div className="mt-2 flex items-baseline gap-2">
            <span className="text-2xl font-bold text-ink-900">{progressPercent}%</span>
            <span className="text-xs text-ink-500">
              ({completedLessons} / {totalLessons} lessons)
            </span>
          </div>
          <div className="mt-2 h-2 w-full rounded-full bg-surface-100 overflow-hidden">
            <div
              className="h-full bg-brand-600 rounded-full transition-all duration-300"
              style={{ width: `${Math.min(100, progressPercent)}%` }}
            />
          </div>
        </div>

        <div className="rounded-xl border border-surface-200 bg-white p-4 shadow-sm">
          <span className="text-xs font-medium text-ink-500">Enrolled On</span>
          <p className="mt-2 text-base font-semibold text-ink-900">
            {enrolledAt ? new Date(enrolledAt).toLocaleDateString() : "—"}
          </p>
          <span className="text-xs text-ink-400">
            Last active: {lastAccessedAt ? new Date(lastAccessedAt).toLocaleDateString() : "Never"}
          </span>
        </div>

        <div className="rounded-xl border border-surface-200 bg-white p-4 shadow-sm">
          <span className="text-xs font-medium text-ink-500">Completion Status</span>
          <p className="mt-2 text-base font-semibold text-ink-900">
            {completedAt ? "Completed" : "In Progress"}
          </p>
          <span className="text-xs text-ink-400">
            {completedAt ? new Date(completedAt).toLocaleDateString() : "Pending"}
          </span>
        </div>
      </div>

      {/* Assignment Submissions */}
      <div className="rounded-xl border border-surface-200 bg-white p-5 shadow-sm">
        <h3 className="text-lg font-bold text-ink-900">Course Assignment Submissions</h3>
        <p className="text-xs text-ink-500 mt-0.5">
          Assessments and submissions completed by this student for this course.
        </p>

        {submissions && submissions.length > 0 ? (
          <div className="mt-4 divide-y divide-surface-100 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-surface-200 text-xs font-semibold text-ink-500">
                  <th className="py-2.5 px-3">Assignment</th>
                  <th className="py-2.5 px-3">Status</th>
                  <th className="py-2.5 px-3">Score</th>
                  <th className="py-2.5 px-3">Submitted At</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-surface-100">
                {submissions.map((sub) => (
                  <tr key={sub.assignmentId} className="hover:bg-surface-50">
                    <td className="py-3 px-3 font-medium text-ink-900">
                      {sub.assignmentTitle}
                      {sub.late && (
                        <span className="ml-2 rounded bg-danger-100 px-1.5 py-0.5 text-[10px] font-semibold text-danger-700">
                          Late
                        </span>
                      )}
                    </td>
                    <td className="py-3 px-3">
                      <span className="inline-flex rounded-full bg-surface-100 px-2 py-0.5 text-xs font-medium text-ink-700">
                        {sub.status}
                      </span>
                    </td>
                    <td className="py-3 px-3 font-semibold text-ink-800">
                      {sub.marks != null ? `${sub.marks} / ${sub.totalPoints}` : "Not Graded"}
                    </td>
                    <td className="py-3 px-3 text-xs text-ink-500">
                      {sub.submittedAt ? new Date(sub.submittedAt).toLocaleString() : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="mt-4 rounded-lg border border-dashed border-surface-200 p-6 text-center text-sm text-ink-500">
            No assignment submissions recorded for this student in this course.
          </div>
        )}
      </div>
    </div>
  );
}
