import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { Slot, SlotRegistryProvider } from "./slots";
import { aggregateSlots } from "./slotRegistry";

describe("Slot", () => {
  it("renders nothing when no feature contributes content", () => {
    const { container } = render(<Slot name="player.side-panel" />);
    expect(container).toBeEmptyDOMElement();
  });

  it.each([
    "lesson-editor.side-panel",
    "course-details.thumbnail",
    "course-editor.header-actions",
  ])("renders no DOM for the empty %s slot", (name) => {
    const { container } = render(
      <SlotRegistryProvider registry={{}}>
        <Slot name={name} />
      </SlotRegistryProvider>,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders registered slot content in order", () => {
    const registry = aggregateSlots([
      [
        {
          id: "enroll-control",
          name: "course-page.enroll-area",
          order: 1,
          element: <button type="button">Join course</button>,
        },
      ],
    ]);

    render(
      <SlotRegistryProvider registry={registry}>
        <Slot name="course-page.enroll-area" />
      </SlotRegistryProvider>,
    );

    expect(
      screen.getByRole("button", { name: "Join course" }),
    ).toBeInTheDocument();
  });
});
