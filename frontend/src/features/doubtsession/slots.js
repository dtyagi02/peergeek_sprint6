import { CourseDoubtSessionCard } from "./CourseDoubtSessionCard";
import { PlayerDoubtButton } from "./PlayerDoubtButton";

export default [
  {
    id: "course-doubt-session-card",
    name: "course-page.enroll-area",
    order: 20,
    component: CourseDoubtSessionCard,
  },
  {
    id: "player-doubt-button",
    name: "player.header-actions",
    order: 5,
    component: PlayerDoubtButton,
  },
];
