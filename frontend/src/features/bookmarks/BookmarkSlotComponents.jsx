import { BookmarkToggle } from "./BookmarkToggle";

export function CourseBookmarkSlot({ courseId }) {
  if (!courseId) return null;
  return (
    <div className="inline-flex items-center">
      <BookmarkToggle
        targetType="COURSE"
        courseId={courseId}
        showLabel={true}
      />
    </div>
  );
}

export function LessonBookmarkSlot({ courseId, lessonId }) {
  if (!courseId || !lessonId) return null;
  return (
    <div className="inline-flex items-center">
      <BookmarkToggle
        targetType="LESSON"
        courseId={courseId}
        lessonId={lessonId}
        showLabel={true}
      />
    </div>
  );
}
