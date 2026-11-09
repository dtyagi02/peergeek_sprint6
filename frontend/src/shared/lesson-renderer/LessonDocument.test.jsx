import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { LessonDocument } from "./LessonDocument";

describe("LessonDocument", () => {
  afterEach(() => {
    cleanup();
  });

  it("renders the supported block, inline, and mark types", () => {
    const document = {
      type: "doc",
      content: [
        {
          type: "heading",
          attrs: { level: 2 },
          content: [{ type: "text", text: "Title" }],
        },
        {
          type: "paragraph",
          content: [
            { type: "text", text: "bold", marks: [{ type: "bold" }] },
            { type: "text", text: " italic", marks: [{ type: "italic" }] },
            { type: "text", text: " code", marks: [{ type: "code" }] },
            {
              type: "text",
              text: " link",
              marks: [
                { type: "link", attrs: { href: "https://example.test/path" } },
              ],
            },
            { type: "hardBreak" },
          ],
        },
        {
          type: "bulletList",
          content: [
            {
              type: "listItem",
              content: [
                {
                  type: "paragraph",
                  content: [{ type: "text", text: "Item" }],
                },
              ],
            },
          ],
        },
        {
          type: "orderedList",
          content: [
            {
              type: "listItem",
              content: [
                {
                  type: "paragraph",
                  content: [{ type: "text", text: "Ordered" }],
                },
              ],
            },
          ],
        },
        {
          type: "blockquote",
          content: [
            { type: "paragraph", content: [{ type: "text", text: "Quote" }] },
          ],
        },
        {
          type: "codeBlock",
          content: [{ type: "text", text: "const x = 1;" }],
        },
        { type: "horizontalRule" },
      ],
    };

    const { container } = render(<LessonDocument document={document} />);

    expect(
      screen.getByRole("heading", { name: "Title", level: 2 }),
    ).toBeInTheDocument();
    expect(screen.getByText("bold").tagName).toBe("STRONG");
    expect(container.querySelector("em").textContent).toBe(" italic");
    expect(container.querySelector("p code").textContent).toBe(" code");
    expect(screen.getByRole("link").textContent).toBe(" link");
    expect(screen.getByRole("link")).toHaveAttribute(
      "rel",
      "noopener noreferrer",
    );
    expect(screen.getByText("Item")).toBeInTheDocument();
    expect(screen.getByText("Ordered")).toBeInTheDocument();
    expect(screen.getByText("Quote")).toBeInTheDocument();
    expect(container.querySelector("pre code")).toHaveTextContent(
      "const x = 1;",
    );
    expect(container.querySelector("hr")).toBeInTheDocument();
    expect(container.querySelector("br")).toBeInTheDocument();
  });

  it("neutralizes unsafe links and safely ignores unknown nodes and marks", () => {
    const document = {
      type: "doc",
      content: [
        {
          type: "paragraph",
          content: [
            {
              type: "text",
              text: "unsafe",
              marks: [{ type: "link", attrs: { href: "javascript:alert(1)" } }],
            },
            {
              type: "text",
              text: " survives",
              marks: [{ type: "strike" }],
            },
          ],
        },
        { type: "futureNode", content: [{ type: "text", text: "fallback" }] },
      ],
    };

    const { container } = render(<LessonDocument document={document} />);

    expect(container.querySelector("a")).not.toBeInTheDocument();
    expect(container.querySelector("p")).toHaveTextContent("unsafe survives");
    expect(screen.getByText("fallback")).toBeInTheDocument();
    expect(container.querySelector("[dangerouslySetInnerHTML]")).toBeNull();
  });

  it("renders a large text document as React text without HTML injection", () => {
    const largeText = "lesson ".repeat(12_000);
    render(
      <LessonDocument
        document={{
          type: "doc",
          content: [
            { type: "paragraph", content: [{ type: "text", text: largeText }] },
          ],
        }}
      />,
    );

    expect(document.querySelector("p").textContent).toBe(largeText);
  });

  it("renders HTML-like text as text without HTML injection", () => {
    const unsafeText = '<img src="x" onerror="alert(1)">';
    const { container } = render(
      <LessonDocument
        document={{
          type: "doc",
          content: [
            {
              type: "paragraph",
              content: [{ type: "text", text: unsafeText }],
            },
          ],
        }}
      />,
    );

    expect(container.querySelector("img")).toBeNull();
    expect(container.textContent).toBe(unsafeText);
  });
});
