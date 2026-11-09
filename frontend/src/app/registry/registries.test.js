import { describe, expect, it } from "vitest";
import { aggregateNavigation, visibleNavigation } from "./navigation";
import { aggregateRouteGroups } from "./routes";
import { aggregateSlots } from "./slotRegistry";

describe("feature registries", () => {
  it("aggregates public and protected routes into separate groups", () => {
    const result = aggregateRouteGroups([
      [{ path: "/about", visibility: "public" }],
      [{ path: "/account", visibility: "protected" }],
    ]);

    expect(result.publicRoutes.map((route) => route.path)).toEqual(["/about"]);
    expect(result.protectedRoutes.map((route) => route.path)).toEqual([
      "/account",
    ]);
  });

  it("aggregates, orders, and filters navigation items by visibility", () => {
    const items = aggregateNavigation([
      [
        {
          id: "later",
          order: 20,
          visibility: "always",
          placement: "header",
        },
        {
          id: "member",
          order: 5,
          visibility: "authenticated",
          placement: "sidebar",
        },
      ],
      [
        {
          id: "first",
          order: 1,
          visibility: "anonymous",
          placement: "header",
        },
      ],
    ]);

    expect(items.map((item) => item.id)).toEqual(["first", "member", "later"]);
    expect(visibleNavigation(items).map((item) => item.id)).toEqual([
      "first",
      "later",
    ]);
    expect(
      visibleNavigation(items, { isAuthenticated: true }).map(
        (item) => item.id,
      ),
    ).toEqual(["member", "later"]);
    expect(
      visibleNavigation(items, {
        isAuthenticated: true,
        placement: "sidebar",
      }).map((item) => item.id),
    ).toEqual(["member"]);
  });

  it("aggregates slot contributions and orders each slot", () => {
    const registry = aggregateSlots([
      [{ id: "late", name: "header.actions", order: 10 }],
      [{ id: "early", name: "header.actions", order: 1 }],
    ]);

    expect(registry["header.actions"].map((item) => item.id)).toEqual([
      "early",
      "late",
    ]);
  });
});
