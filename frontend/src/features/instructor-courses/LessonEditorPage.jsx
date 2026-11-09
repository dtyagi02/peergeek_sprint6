import { useMemo } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { ErrorState } from "../../shared/components/ErrorState";
import { Spinner } from "../../shared/components/Spinner";
import { useToast } from "../../shared/components/useToast";
import { Slot } from "../../app/registry/slots";
import { getCourse, getLessonContent, getOutline } from "./courseApi";
import { InstructorModeBanner } from "./InstructorModeBanner";
import { LessonEditor } from "./LessonEditor";

export function LessonEditorPage() {
  const { courseId, lessonId } = useParams();
  const queryClient = useQueryClient();
  const toast = useToast();
  const courseQuery = useQuery({
    queryKey: ["instructor-course", courseId],
    queryFn: () => getCourse(courseId),
  });
  const outlineQuery = useQuery({
    queryKey: ["course-outline", courseId],
    queryFn: () => getOutline(courseId),
  });
  const lesson = useMemo(
    () =>
      outlineQuery.data?.modules
        .flatMap((module) => module.lessons)
        .find((item) => String(item.id) === lessonId),
    [outlineQuery.data, lessonId],
  );
  const contentQuery = useQuery({
    queryKey: ["lesson-content", lessonId],
    queryFn: () => getLessonContent(lessonId),
  });

  if (
    courseQuery.isPending ||
    outlineQuery.isPending ||
    contentQuery.isPending
  ) {
    return <Spinner label="Loading lesson editor" />;
  }
  if (
    courseQuery.isError ||
    outlineQuery.isError ||
    contentQuery.isError ||
    !lesson
  ) {
    return (
      <ErrorState
        description="The lesson could not be loaded."
        title="Lesson unavailable"
      />
    );
  }

  return (
    <section className="mx-auto max-w-5xl">
      <InstructorModeBanner />
      <Slot name="lesson-editor.side-panel" />
      <LessonEditor
        courseId={courseId}
        initialContent={contentQuery.data}
        lesson={lesson}
        lessonId={lessonId}
        onSaved={(saved) => {
          queryClient.setQueryData(["lesson-content", lessonId], saved);
          queryClient.invalidateQueries({
            queryKey: ["course-outline", courseId],
          });
          toast.notify({
            title: "Lesson saved",
            description: "Your content is up to date.",
          });
        }}
        queryClient={queryClient}
      />
    </section>
  );
}
