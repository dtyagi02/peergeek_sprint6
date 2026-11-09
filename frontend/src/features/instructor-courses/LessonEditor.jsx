import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { EditorContent, useEditor } from "@tiptap/react";
import { Link as TipTapLink } from "@tiptap/extension-link";
import StarterKit from "@tiptap/starter-kit";
import * as Dialog from "@radix-ui/react-dialog";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../shared/api/ApiError";
import { Button } from "../../shared/components/Button";
import { useToast } from "../../shared/components/useToast";
import { getLessonContent, updateLesson, saveLessonContent } from "./courseApi";
import { InstructorModeBanner } from "./InstructorModeBanner";

const metadataSchema = z.object({
  title: z.string().trim().min(1, "Lesson title is required").max(150),
  summary: z.string().max(500),
});
const allowedProtocols = new Set(["http:", "https:", "mailto:"]);
const blankDocument = { type: "doc", content: [{ type: "paragraph" }] };

function EditorLink() {
  return TipTapLink.extend({
    addAttributes() {
      return {
        href: {
          default: null,
          parseHTML: (element) => element.getAttribute("href"),
        },
      };
    },
  }).configure({
    autolink: false,
    linkOnPaste: false,
    openOnClick: false,
    isAllowedUri: (value) => {
      try {
        return allowedProtocols.has(new URL(value).protocol);
      } catch {
        return false;
      }
    },
  });
}

const editorExtensions = [
  StarterKit.configure({
    link: false,
    strike: false,
    underline: false,
    trailingNode: false,
    dropcursor: false,
    heading: { levels: [1, 2, 3] },
  }),
  EditorLink(),
];

function wordCountFromText(text) {
  return [...text.matchAll(/[\p{L}\p{N}]+(?:['’][\p{L}\p{N}]+)*/gu)].length;
}

function SaveStatus({ state }) {
  const label = {
    saved: "Saved",
    dirty: "Unsaved changes",
    saving: "Saving…",
    error: "Save failed",
  }[state];
  return (
    <p aria-live="polite" className="text-sm text-ink-700" role="status">
      {label}
    </p>
  );
}

function Toolbar({ editor }) {
  if (!editor) return null;
  const actions = [
    [
      "Heading 1",
      () => editor.chain().focus().toggleHeading({ level: 1 }).run(),
      editor.isActive("heading", { level: 1 }),
    ],
    [
      "Heading 2",
      () => editor.chain().focus().toggleHeading({ level: 2 }).run(),
      editor.isActive("heading", { level: 2 }),
    ],
    [
      "Heading 3",
      () => editor.chain().focus().toggleHeading({ level: 3 }).run(),
      editor.isActive("heading", { level: 3 }),
    ],
    [
      "Bold",
      () => editor.chain().focus().toggleBold().run(),
      editor.isActive("bold"),
    ],
    [
      "Italic",
      () => editor.chain().focus().toggleItalic().run(),
      editor.isActive("italic"),
    ],
    [
      "Inline code",
      () => editor.chain().focus().toggleCode().run(),
      editor.isActive("code"),
    ],
    [
      "Bullet list",
      () => editor.chain().focus().toggleBulletList().run(),
      editor.isActive("bulletList"),
    ],
    [
      "Ordered list",
      () => editor.chain().focus().toggleOrderedList().run(),
      editor.isActive("orderedList"),
    ],
    [
      "Blockquote",
      () => editor.chain().focus().toggleBlockquote().run(),
      editor.isActive("blockquote"),
    ],
    [
      "Code block",
      () => editor.chain().focus().toggleCodeBlock().run(),
      editor.isActive("codeBlock"),
    ],
    [
      "Horizontal rule",
      () => editor.chain().focus().setHorizontalRule().run(),
      false,
    ],
    ["Undo", () => editor.chain().focus().undo().run(), false],
    ["Redo", () => editor.chain().focus().redo().run(), false],
  ];
  function addLink() {
    const href = window.prompt("Enter an http, https, or mailto link");
    if (!href) return;
    try {
      if (!allowedProtocols.has(new URL(href).protocol)) return;
      editor.chain().focus().setLink({ href }).run();
    } catch {
      return;
    }
  }
  return (
    <div
      aria-label="Lesson formatting"
      className="flex flex-wrap gap-2"
      role="toolbar"
    >
      {actions.map(([label, action, pressed]) => (
        <button
          aria-label={label}
          aria-pressed={
            label.startsWith("Heading") ||
            [
              "Bold",
              "Italic",
              "Inline code",
              "Bullet list",
              "Ordered list",
              "Blockquote",
              "Code block",
            ].includes(label)
              ? pressed
              : undefined
          }
          className={`min-h-10 rounded border px-3 text-sm ${pressed ? "border-brand-600 bg-brand-50 text-brand-700" : "border-surface-300 bg-white text-ink-900"} focus:outline-2 focus:outline-brand-600`}
          key={label}
          onClick={action}
          type="button"
        >
          {label}
        </button>
      ))}
      <button
        aria-label="Add link"
        className="min-h-10 rounded border border-surface-300 bg-white px-3 text-sm"
        onClick={addLink}
        type="button"
      >
        Link
      </button>
    </div>
  );
}

function ConflictDialog({ open, onReload, onOverwrite, onClose, busy }) {
  return (
    <Dialog.Root open={open} onOpenChange={(value) => !value && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,34rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
          <Dialog.Title className="text-lg font-semibold text-ink-900">
            Lesson changed elsewhere
          </Dialog.Title>
          <Dialog.Description className="mt-2 text-sm text-ink-700">
            Choose whether to load the latest content or replace it with your
            current version.
          </Dialog.Description>
          <div className="mt-6 flex flex-wrap justify-end gap-3">
            <Button disabled={busy} onClick={onReload} variant="secondary">
              Reload latest
            </Button>
            <Button disabled={busy} onClick={onOverwrite}>
              {busy ? "Saving…" : "Keep my version and overwrite"}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

export function LessonEditor({
  courseId,
  initialContent,
  lesson,
  lessonId,
  onSaved,
  queryClient,
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const toast = useToast();
  const savedDocument = useRef(initialContent.document ?? blankDocument);
  const latestVersion = useRef(initialContent.version);
  const suppressUpdate = useRef(false);
  const dirtyRef = useRef(false);
  const [contentDirty, setContentDirty] = useState(false);
  const [contentState, setContentState] = useState("saved");
  const [wordCount, setWordCount] = useState(initialContent.wordCount ?? 0);
  const [conflictOpen, setConflictOpen] = useState(false);
  const [latestContent, setLatestContent] = useState(null);
  const [pendingHref, setPendingHref] = useState(null);
  const editor = useEditor({
    extensions: editorExtensions,
    content: initialContent.document ?? blankDocument,
    immediatelyRender: false,
    onUpdate: ({ editor: currentEditor }) => {
      const current = currentEditor.getJSON();
      setWordCount(wordCountFromText(currentEditor.getText()));
      if (!suppressUpdate.current) {
        const dirty =
          JSON.stringify(current) !== JSON.stringify(savedDocument.current);
        setContentDirty(dirty);
        setContentState(dirty ? "dirty" : "saved");
      }
    },
    editorProps: {
      attributes: {
        "aria-label": "Lesson content",
        class:
          "min-h-[24rem] max-w-none rounded-b-lg border border-t-0 border-surface-300 p-4 outline-none focus:ring-2 focus:ring-brand-600",
      },
    },
  });
  const metadataForm = useForm({
    defaultValues: { title: lesson.title, summary: lesson.summary ?? "" },
  });
  const metadataMutation = useMutation({
    mutationFn: (body) => updateLesson(lessonId, body),
    onSuccess: (updated) => {
      metadataForm.reset({
        title: updated.title,
        summary: updated.summary ?? "",
      });
      queryClient.invalidateQueries({ queryKey: ["course-outline", courseId] });
      toast.notify({
        title: "Lesson details saved",
        description: "Your title and summary are updated.",
      });
    },
    onError: () =>
      toast.notify({
        title: "Details not saved",
        description: "Check your connection and try again.",
      }),
  });
  const contentMutation = useMutation({
    mutationFn: (body) => saveLessonContent(lessonId, body),
    onMutate: () => setContentState("saving"),
    onSuccess: (saved) => {
      savedDocument.current = saved.document;
      latestVersion.current = saved.version;
      dirtyRef.current = false;
      setContentDirty(false);
      setContentState("saved");
      setWordCount(saved.wordCount);
      onSaved(saved);
    },
    onError: async (error) => {
      if (error instanceof ApiError && error.status === 409) {
        try {
          const latest = await queryClient.fetchQuery({
            queryKey: ["lesson-content", lessonId],
            queryFn: () => getLessonContent(lessonId),
            staleTime: 0,
          });
          setLatestContent(latest);
          setConflictOpen(true);
        } catch {
          toast.notify({
            title: "Could not reload latest lesson",
            description: "Your local edits are still available.",
          });
        }
      }
      setContentState("error");
    },
  });
  const metadataDirty = metadataForm.formState.isDirty;
  const hasUnsavedChanges = contentDirty || metadataDirty;
  const outlineHref = `/instructor/courses/${courseId}?tab=outline`;
  const metadataSubmit = metadataForm.handleSubmit((values) => {
    const result = metadataSchema.safeParse(values);
    if (!result.success) {
      result.error.issues.forEach((issue) =>
        metadataForm.setError(issue.path.join(".") || "title", {
          type: "validation",
          message: issue.message,
        }),
      );
      return;
    }
    metadataMutation.mutate({
      title: result.data.title,
      summary: result.data.summary || null,
    });
  });

  const saveContent = useCallback(() => {
    if (!editor || !dirtyRef.current) return;
    contentMutation.mutate({
      document: editor.getJSON(),
      baseVersion: latestVersion.current,
    });
  }, [contentMutation, editor]);

  useEffect(() => {
    if (!editor) return undefined;
    const handleKeyDown = (event) => {
      if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        saveContent();
      }
    };
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [editor, saveContent]);

  useEffect(() => {
    if (!hasUnsavedChanges) return undefined;
    dirtyRef.current = true;
    const beforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", beforeUnload);
    return () => window.removeEventListener("beforeunload", beforeUnload);
  }, [hasUnsavedChanges]);

  useEffect(() => {
    if (!hasUnsavedChanges) dirtyRef.current = false;
  }, [hasUnsavedChanges]);

  useEffect(() => {
    const current = `${location.pathname}${location.search}${location.hash}`;
    const locationRef = { current };
    const popstate = () => {
      if (
        dirtyRef.current &&
        !window.confirm("You have unsaved lesson changes. Leave this page?")
      ) {
        navigate(locationRef.current, { replace: true });
      }
    };
    window.addEventListener("popstate", popstate);
    return () => window.removeEventListener("popstate", popstate);
  }, [location, navigate]);

  const savedAtLabel = useMemo(
    () =>
      initialContent.updatedAt
        ? new Date(initialContent.updatedAt).toLocaleString()
        : "Not saved yet",
    [initialContent.updatedAt],
  );

  function interceptNavigation(event) {
    const anchor = event.target.closest("a[href]");
    if (
      !anchor ||
      !hasUnsavedChanges ||
      event.defaultPrevented ||
      event.button !== 0
    )
      return;
    if (
      anchor.target ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey
    )
      return;
    event.preventDefault();
    setPendingHref(anchor.getAttribute("href"));
  }

  function setSavedEditorDocument(document, version, count, updatedAt) {
    if (!editor) return;
    suppressUpdate.current = true;
    editor.commands.setContent(document);
    suppressUpdate.current = false;
    savedDocument.current = document;
    latestVersion.current = version;
    dirtyRef.current = false;
    setContentDirty(false);
    setWordCount(count);
    setContentState("saved");
    if (updatedAt)
      queryClient.setQueryData(["lesson-content", lessonId], {
        contentFormat: "tiptap-json-v1",
        document,
        version,
        wordCount: count,
        updatedAt,
      });
  }

  function reloadLatest() {
    if (!latestContent) return;
    setSavedEditorDocument(
      latestContent.document,
      latestContent.version,
      latestContent.wordCount,
      latestContent.updatedAt,
    );
    setConflictOpen(false);
    setLatestContent(null);
  }

  function overwriteWithLocal() {
    if (!latestContent || !editor) return;
    latestVersion.current = latestContent.version;
    setConflictOpen(false);
    contentMutation.mutate({
      document: editor.getJSON(),
      baseVersion: latestContent.version,
    });
    setLatestContent(null);
  }

  return (
    <section className="mx-auto max-w-5xl" onClickCapture={interceptNavigation}>
      <InstructorModeBanner />
      <Link className="text-sm text-brand-700 underline" to={outlineHref}>
        ← Back to course outline
      </Link>
      <div className="mt-4 flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm text-ink-700">Lesson editor</p>
          <h1 className="mt-1 text-3xl font-bold text-ink-900">
            {lesson.title}
          </h1>
        </div>
        <div className="flex items-center gap-4">
          <SaveStatus state={contentState} />
          <Button
            disabled={contentMutation.isPending || !contentDirty}
            onClick={saveContent}
          >
            Save content
          </Button>
        </div>
      </div>
      <p className="mt-2 text-sm text-ink-700">
        {wordCount} words · Last saved: {savedAtLabel} · Ctrl/Cmd+S to save
      </p>
      <form
        className="mt-6 grid gap-4 rounded-lg border border-surface-200 bg-white p-4"
        noValidate
        onSubmit={metadataSubmit}
      >
        <label
          className="grid gap-1 text-sm font-medium"
          htmlFor="lesson-title"
        >
          Lesson title
          <input
            className="min-h-10 rounded-md border border-surface-300 px-3"
            id="lesson-title"
            maxLength={150}
            {...metadataForm.register("title")}
          />
        </label>
        <label
          className="grid gap-1 text-sm font-medium"
          htmlFor="lesson-summary"
        >
          Summary
          <textarea
            className="min-h-20 rounded-md border border-surface-300 px-3 py-2"
            id="lesson-summary"
            maxLength={500}
            {...metadataForm.register("summary")}
          />
        </label>
        {Object.values(metadataForm.formState.errors).map((error) => (
          <p
            className="text-sm text-danger-700"
            key={error.message}
            role="alert"
          >
            {error.message}
          </p>
        ))}
        <div>
          <Button
            disabled={metadataMutation.isPending || !metadataDirty}
            type="submit"
            variant="secondary"
          >
            {metadataMutation.isPending
              ? "Saving details…"
              : "Save title and summary"}
          </Button>
        </div>
      </form>
      <div className="mt-6">
        <Toolbar editor={editor} />
        <EditorContent editor={editor} />
      </div>
      <p className="mt-2 text-xs text-ink-700">
        Lesson content is text only. Images and embedded resources are not
        supported.
      </p>
      <Dialog.Root
        open={Boolean(pendingHref)}
        onOpenChange={(open) => !open && setPendingHref(null)}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,30rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
            <Dialog.Title className="text-lg font-semibold">
              Discard unsaved changes?
            </Dialog.Title>
            <Dialog.Description className="mt-2 text-sm text-ink-700">
              Your lesson edits have not been saved.
            </Dialog.Description>
            <div className="mt-6 flex justify-end gap-3">
              <Button onClick={() => setPendingHref(null)} variant="secondary">
                Stay here
              </Button>
              <Button
                onClick={() => {
                  const href = pendingHref;
                  setPendingHref(null);
                  dirtyRef.current = false;
                  navigate(href);
                }}
                variant="danger"
              >
                Leave page
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
      <ConflictDialog
        busy={contentMutation.isPending}
        onClose={() => setConflictOpen(false)}
        onOverwrite={overwriteWithLocal}
        onReload={reloadLatest}
        open={conflictOpen}
      />
    </section>
  );
}
