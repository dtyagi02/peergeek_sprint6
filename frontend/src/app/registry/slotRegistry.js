export const SLOT_NAMES = Object.freeze([
  "course-page.enroll-area",
  "player.side-panel",
  "player.lesson-actions",
  "player.header-actions",
  "header.actions",
  "lesson-editor.side-panel",
  "course-details.thumbnail",
  "course-editor.header-actions",
  "home.sections",
]);

const featureSlotModules = import.meta.glob("../../features/**/slots.js", {
  eager: true,
  import: "default",
});

export function aggregateSlots(featureSlotArrays) {
  return featureSlotArrays.flat().reduce((registry, contribution) => {
    const entries = registry[contribution.name] ?? [];
    entries.push(contribution);
    registry[contribution.name] = entries.toSorted(
      (left, right) => (left.order ?? 0) - (right.order ?? 0),
    );
    return registry;
  }, {});
}

export const slotRegistry = aggregateSlots(Object.values(featureSlotModules));
