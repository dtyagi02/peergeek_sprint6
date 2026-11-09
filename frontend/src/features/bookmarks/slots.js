import {
  CourseBookmarkSlot,
  LessonBookmarkSlot,
} from "./BookmarkSlotComponents";

export default [
  {
    id: "course-preview-bookmark",
    name: "course-page.enroll-area",
    order: 15,
    component: CourseBookmarkSlot,
  },
  {
    id: "player-header-bookmark",
    name: "player.header-actions",
    order: 15,
    component: CourseBookmarkSlot,
  },
  {
    id: "player-lesson-bookmark",
    name: "player.lesson-actions",
    order: 15,
    component: LessonBookmarkSlot,
  },
];
