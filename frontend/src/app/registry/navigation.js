const featureNavigationModules = import.meta.glob(
  "../../features/**/navigation.js",
  { eager: true, import: "default" },
);

export function aggregateNavigation(featureNavigationArrays) {
  return featureNavigationArrays
    .flat()
    .toSorted(
      (left, right) =>
        left.order - right.order || left.id.localeCompare(right.id),
    );
}

export function visibleNavigation(
  items,
  { isAuthenticated = false, placement, userMode, userRole } = {},
) {
  return items.filter((item) => {
    if (placement) {
      const itemPlacement = item.placement ?? "header";
      const matches =
        itemPlacement === placement ||
        itemPlacement === "both" ||
        (Array.isArray(itemPlacement) && itemPlacement.includes(placement));
      if (!matches) return false;
    }
    if (item.role && item.role !== userRole) return false;
    if (item.mode && item.mode !== userMode) return false;
    if (item.visibility === "authenticated") return isAuthenticated;
    if (item.visibility === "anonymous") return !isAuthenticated;
    return true;
  });
}

export const navigationItems = aggregateNavigation(
  Object.values(featureNavigationModules),
);
