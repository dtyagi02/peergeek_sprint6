import { Fragment, createElement } from "react";

const SAFE_LINK_PROTOCOLS = new Set(["http:", "https:", "mailto:"]);
const HEADING_TAGS = Object.freeze({
  1: "h1",
  2: "h2",
  3: "h3",
});

function safeHref(value) {
  if (typeof value !== "string") return null;
  try {
    const url = new URL(value);
    return SAFE_LINK_PROTOCOLS.has(url.protocol) ? value : null;
  } catch {
    return null;
  }
}

function renderMarks(content, marks, key) {
  return (Array.isArray(marks) ? marks : []).reduceRight(
    (child, mark, index) => {
      switch (mark?.type) {
        case "bold":
          return createElement(
            "strong",
            { key: `${key}-mark-${index}` },
            child,
          );
        case "italic":
          return createElement("em", { key: `${key}-mark-${index}` }, child);
        case "code":
          return createElement("code", { key: `${key}-mark-${index}` }, child);
        case "link": {
          const href = safeHref(mark.attrs?.href);
          return href
            ? createElement(
                "a",
                {
                  href,
                  key: `${key}-mark-${index}`,
                  rel: "noopener noreferrer",
                  target: "_blank",
                },
                child,
              )
            : child;
        }
        default:
          return child;
      }
    },
    content,
  );
}

function renderNode(node, key) {
  if (!node || typeof node !== "object") return null;
  const children = Array.isArray(node.content)
    ? node.content.map((child, index) => renderNode(child, `${key}-${index}`))
    : [];

  switch (node.type) {
    case "doc":
      return createElement(Fragment, { key }, children);
    case "paragraph":
      return createElement("p", { key }, children);
    case "heading": {
      const tag = HEADING_TAGS[node.attrs?.level];
      return tag ? createElement(tag, { key }, children) : children;
    }
    case "text":
      return renderMarks(
        typeof node.text === "string" ? node.text : "",
        node.marks,
        key,
      );
    case "bulletList":
      return createElement("ul", { key }, children);
    case "orderedList":
      return createElement("ol", { key }, children);
    case "listItem":
      return createElement("li", { key }, children);
    case "blockquote":
      return createElement("blockquote", { key }, children);
    case "codeBlock":
      return createElement(
        "pre",
        { key },
        createElement("code", null, children),
      );
    case "hardBreak":
      return createElement("br", { key });
    case "horizontalRule":
      return createElement("hr", { key });
    default:
      return createElement(Fragment, { key }, children);
  }
}

export function LessonDocument({ document, className }) {
  if (!document || typeof document !== "object" || document.type !== "doc") {
    return null;
  }
  return createElement(
    "div",
    { className },
    renderNode(document, "lesson-document"),
  );
}
