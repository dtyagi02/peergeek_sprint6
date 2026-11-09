import {
  CourseEditorAssignmentsHeaderLink,
  PlayerHeaderAssignmentsLink,
  LessonAssignmentsAction,
} from "./AssignmentSlotComponents";

export default [
  {
    id: "assignments-course-editor-header",
    name: "course-editor.header-actions",
    order: 30,
    component: CourseEditorAssignmentsHeaderLink,
  },
  {
    id: "assignments-player-header",
    name: "player.header-actions",
    order: 20,
    component: PlayerHeaderAssignmentsLink,
  },
  {
    id: "assignments-player-lesson-actions",
    name: "player.lesson-actions",
    order: 20,
    component: LessonAssignmentsAction,
  },
];
