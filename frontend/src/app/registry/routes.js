const featureRouteModules = import.meta.glob("../../features/**/routes.js", {
  eager: true,
  import: "default",
});

export function aggregateRouteGroups(featureRouteArrays) {
  const routes = featureRouteArrays.flat();

  return {
    publicRoutes: routes.filter((route) => route.visibility !== "protected"),
    protectedRoutes: routes.filter((route) => route.visibility === "protected"),
  };
}

const groups = aggregateRouteGroups(Object.values(featureRouteModules));

export const publicRoutes = groups.publicRoutes;
export const protectedRoutes = groups.protectedRoutes;
