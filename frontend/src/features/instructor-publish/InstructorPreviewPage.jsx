import { useQuery } from "@tanstack/react-query";
import { Link, useParams } from "react-router-dom";
import { ErrorState } from "../../shared/components/ErrorState";
import { Spinner } from "../../shared/components/Spinner";
import { LessonDocument } from "../../shared/lesson-renderer/LessonDocument";
import { getInstructorPreview } from "./publishApi";

export function InstructorPreviewPage() {
  const { courseId } = useParams();
  const previewQuery = useQuery({
    queryKey: ["instructor-course-preview", courseId],
    queryFn: () => getInstructorPreview(courseId),
  });
  if (previewQuery.isPending) return <Spinner label="Loading course preview" />;
  if (previewQuery.isError) {
    return (
      <ErrorState
        description="The preview could not be loaded."
        title="Preview unavailable"
      />
    );
  }
  const course = previewQuery.data;
  return (
    <main className="mx-auto max-w-4xl px-4 py-8">
      <Link
        className="text-sm text-brand-700 underline"
        to={`/instructor/courses/${courseId}`}
      >
        ← Back to course editor
      </Link>
      <header className="my-8 border-b border-surface-200 pb-6">
        {course.categoryName && (
          <p className="text-sm font-medium text-brand-700">
            {course.categoryName}
          </p>
        )}
        <h1 className="mt-2 text-4xl font-bold text-ink-900">{course.title}</h1>
        {course.description && (
          <p className="mt-4 whitespace-pre-wrap text-ink-700">
            {course.description}
          </p>
        )}
        <p className="mt-3 text-sm text-ink-700">
          {course.difficulty ?? "Difficulty not set"}
          {course.topics.length > 0 && ` · ${course.topics.join(", ")}`}
        </p>
      </header>
      <div className="grid gap-8">
        {course.modules.map((module) => (
          <section className="grid gap-4" key={module.id}>
            <div>
              <h2 className="text-2xl font-semibold text-ink-900">
                {module.title}
              </h2>
              {module.description && (
                <p className="mt-1 whitespace-pre-wrap text-ink-700">
                  {module.description}
                </p>
              )}
            </div>
            {module.lessons.map((lesson) => (
              <article
                className="rounded-lg border border-surface-200 p-5"
                key={lesson.id}
              >
                <h3 className="text-xl font-semibold text-ink-900">
                  {lesson.title}
                </h3>
                {lesson.summary && (
                  <p className="mt-1 text-sm text-ink-700">{lesson.summary}</p>
                )}
                <LessonDocument
                  className="prose mt-5 max-w-none"
                  document={lesson.document}
                />
                {lesson.resources.length > 0 && (
                  <ul className="mt-5 grid gap-2 border-t border-surface-200 pt-4">
                    {lesson.resources.map((resource) => (
                      <li key={resource.id}>
                        <a
                          className="text-sm text-brand-700 underline"
                          href={resource.contentUrl ?? resource.externalUrl}
                          rel="noreferrer"
                          target="_blank"
                        >
                          {resource.title}
                        </a>
                      </li>
                    ))}
                  </ul>
                )}
              </article>
            ))}
          </section>
        ))}
      </div>
    </main>
  );
}
