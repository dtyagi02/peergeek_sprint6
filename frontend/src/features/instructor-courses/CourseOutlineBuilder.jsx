import { useState } from "react";
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
} from "@dnd-kit/core";
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";
import * as Dialog from "@radix-ui/react-dialog";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link } from "react-router-dom";
import { z } from "zod";
import { Button } from "../../shared/components/Button";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";
import { Spinner } from "../../shared/components/Spinner";
import { useToast } from "../../shared/components/useToast";
import {
  createLesson,
  createModule,
  deleteLesson,
  deleteModule,
  getOutline,
  moveLesson,
  reorderLessons,
  reorderModules,
  updateLesson,
  updateModule,
} from "./courseApi";

const moduleSchema = z.object({
  title: z.string().trim().min(1, "Module title is required").max(120),
  description: z.string().max(1000),
});
const lessonSchema = z.object({
  title: z.string().trim().min(1, "Lesson title is required").max(150),
  summary: z.string().max(500),
});

function sortableId(type, id) {
  return `${type}:${id}`;
}

function reorderLessonSnapshot(
  outline,
  lessonId,
  targetModuleId,
  targetPosition,
) {
  const modules = outline.modules.map((module) => ({
    ...module,
    lessons: [...module.lessons],
  }));
  const source = modules.find((module) =>
    module.lessons.some((lesson) => String(lesson.id) === String(lessonId)),
  );
  const target = modules.find(
    (module) => String(module.id) === String(targetModuleId),
  );
  if (!source || !target) return outline;
  const sourceIndex = source.lessons.findIndex(
    (lesson) => String(lesson.id) === String(lessonId),
  );
  const [lesson] = source.lessons.splice(sourceIndex, 1);
  lesson.position = targetPosition;
  target.lessons.splice(targetPosition, 0, lesson);
  source.lessons.forEach((item, index) => {
    item.position = index;
  });
  target.lessons.forEach((item, index) => {
    item.position = index;
  });
  return { ...outline, modules };
}

function Confirmation({ item, onClose, onConfirm, busy }) {
  const lessonCount = item?.lessons?.length ?? 0;
  return (
    <Dialog.Root
      open={Boolean(item)}
      onOpenChange={(open) => !open && onClose()}
    >
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <Dialog.Content
          aria-describedby="outline-delete-description"
          className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,30rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl"
        >
          <Dialog.Title className="text-lg font-semibold text-ink-900">
            Delete {item?.kind === "module" ? "module" : "lesson"}?
          </Dialog.Title>
          <Dialog.Description
            className="mt-2 text-sm text-ink-700"
            id="outline-delete-description"
          >
            {item?.kind === "module"
              ? `“${item.title}” and its ${lessonCount} lesson${lessonCount === 1 ? "" : "s"} (including saved text) will be deleted.`
              : `“${item?.title}” and its saved lesson text will be deleted.`}
          </Dialog.Description>
          <div className="mt-6 flex justify-end gap-3">
            <Dialog.Close asChild>
              <Button variant="secondary">Cancel</Button>
            </Dialog.Close>
            <Button disabled={busy} onClick={onConfirm} variant="danger">
              {busy ? "Deleting…" : "Delete"}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function InlineForm({
  schema,
  defaults,
  onSave,
  submitLabel,
  fields,
  disabled,
}) {
  const form = useForm({ defaultValues: defaults });
  const [expanded, setExpanded] = useState(false);
  const submit = form.handleSubmit((values) => {
    const result = schema.safeParse(values);
    if (!result.success) {
      result.error.issues.forEach((issue) => {
        form.setError(issue.path.join(".") || "title", {
          type: "validation",
          message: issue.message,
        });
      });
      return;
    }
    onSave(result.data, () => setExpanded(false));
  });
  return (
    <div className="mt-3">
      {!expanded ? (
        <Button
          disabled={disabled}
          onClick={() => {
            form.reset(defaults);
            setExpanded(true);
          }}
          variant="secondary"
        >
          {submitLabel}
        </Button>
      ) : (
        <form
          className="grid gap-2 rounded-md bg-surface-50 p-3 sm:grid-cols-[1fr_1fr_auto]"
          noValidate
          onSubmit={submit}
        >
          {fields.map((field) =>
            field.multiline ? (
              <label
                className="grid gap-1 text-xs font-medium"
                htmlFor={`inline-${field.name}`}
                key={field.name}
              >
                {field.label}
                <textarea
                  className="min-h-10 rounded border border-surface-300 px-2 py-2 text-sm"
                  id={`inline-${field.name}`}
                  maxLength={field.maxLength}
                  {...form.register(field.name)}
                />
              </label>
            ) : (
              <label
                className="grid gap-1 text-xs font-medium"
                htmlFor={`inline-${field.name}`}
                key={field.name}
              >
                {field.label}
                <input
                  className="min-h-10 rounded border border-surface-300 px-2 text-sm"
                  id={`inline-${field.name}`}
                  maxLength={field.maxLength}
                  {...form.register(field.name)}
                />
              </label>
            ),
          )}
          <div className="flex items-center gap-2">
            <Button disabled={disabled} type="submit">
              {disabled ? "Saving…" : "Save"}
            </Button>
            <Button
              onClick={() => setExpanded(false)}
              type="button"
              variant="secondary"
            >
              Cancel
            </Button>
          </div>
          {Object.values(form.formState.errors).map((error, index) => (
            <p
              className="text-xs text-danger-700"
              key={`${error.message}-${index}`}
              role="alert"
            >
              {error.message}
            </p>
          ))}
        </form>
      )}
    </div>
  );
}

function LessonEditDialog({ lesson, onClose, onSave, saving }) {
  const form = useForm({
    defaultValues: { title: lesson.title, summary: lesson.summary ?? "" },
  });
  const submit = form.handleSubmit((values) => {
    const result = lessonSchema.safeParse(values);
    if (!result.success) {
      result.error.issues.forEach((issue) =>
        form.setError(issue.path.join(".") || "title", {
          type: "validation",
          message: issue.message,
        }),
      );
      return;
    }
    onSave({ title: result.data.title, summary: result.data.summary || null });
  });
  return (
    <Dialog.Root open onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,32rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl">
          <Dialog.Title className="text-lg font-semibold">
            Edit lesson details
          </Dialog.Title>
          <form className="mt-5 grid gap-4" noValidate onSubmit={submit}>
            <label
              className="grid gap-1 text-sm font-medium"
              htmlFor="edit-lesson-title"
            >
              Title
              <input
                className="min-h-10 rounded-md border border-surface-300 px-3"
                id="edit-lesson-title"
                maxLength={150}
                {...form.register("title")}
              />
            </label>
            <label
              className="grid gap-1 text-sm font-medium"
              htmlFor="edit-lesson-summary"
            >
              Summary
              <textarea
                className="min-h-24 rounded-md border border-surface-300 px-3 py-2"
                id="edit-lesson-summary"
                maxLength={500}
                {...form.register("summary")}
              />
            </label>
            {Object.values(form.formState.errors).map((error) => (
              <p
                className="text-sm text-danger-700"
                key={error.message}
                role="alert"
              >
                {error.message}
              </p>
            ))}
            <div className="flex justify-end gap-3">
              <Button onClick={onClose} type="button" variant="secondary">
                Cancel
              </Button>
              <Button disabled={saving} type="submit">
                {saving ? "Saving…" : "Save details"}
              </Button>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function SortableModule({ module, modules, onDelete, onMove, busy, children }) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: sortableId("module", module.id),
    data: { type: "module", moduleId: module.id },
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : undefined,
  };
  const index = modules.findIndex((item) => item.id === module.id);
  return (
    <section
      className="rounded-lg border border-surface-200 bg-white p-4 shadow-sm"
      ref={setNodeRef}
      style={style}
    >
      <div className="flex flex-wrap items-center gap-2">
        <button
          aria-label={`Drag module ${module.title}`}
          className="cursor-grab touch-none rounded border border-surface-200 px-3 py-2 focus:outline-2 focus:outline-brand-600"
          {...attributes}
          {...listeners}
        >
          ↕
        </button>
        <div className="min-w-0 flex-1">
          <h3 className="font-semibold text-ink-900">{module.title}</h3>
          {module.description && (
            <p className="mt-1 text-sm text-ink-700">{module.description}</p>
          )}
        </div>
        <div className="flex gap-1">
          <Button
            aria-label={`Move ${module.title} up`}
            disabled={index === 0 || busy}
            onClick={() => onMove(index, -1)}
            variant="secondary"
          >
            ↑
          </Button>
          <Button
            aria-label={`Move ${module.title} down`}
            disabled={index === modules.length - 1 || busy}
            onClick={() => onMove(index, 1)}
            variant="secondary"
          >
            ↓
          </Button>
          <Button
            onClick={() => onDelete({ kind: "module", ...module })}
            variant="danger"
          >
            Delete
          </Button>
        </div>
      </div>
      {children}
    </section>
  );
}

function SortableLesson({
  lesson,
  module,
  modules,
  index,
  busy,
  onDelete,
  onReorder,
  onMoveTo,
  onEdit,
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({
    id: sortableId("lesson", lesson.id),
    data: { type: "lesson", lessonId: lesson.id, moduleId: module.id },
  });
  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : undefined,
  };
  const [targetModule, setTargetModule] = useState("");
  return (
    <li
      className="grid gap-2 rounded-md border border-surface-100 bg-white p-3 sm:grid-cols-[auto_1fr_auto]"
      ref={setNodeRef}
      style={style}
    >
      <button
        aria-label={`Drag lesson ${lesson.title}`}
        className="cursor-grab touch-none self-start rounded border border-surface-200 px-3 py-2 focus:outline-2 focus:outline-brand-600"
        {...attributes}
        {...listeners}
      >
        ↕
      </button>
      <div className="min-w-0">
        <Link
          className="font-medium text-brand-700 underline"
          to={`/instructor/courses/${modules.courseId}/lessons/${lesson.id}`}
        >
          {lesson.title}
        </Link>
        <p className="mt-1 text-xs text-ink-700">
          {lesson.wordCount} words ·{" "}
          {lesson.hasContent ? "content saved" : "no content yet"}
        </p>
        {lesson.summary && (
          <p className="mt-1 text-sm text-ink-700">{lesson.summary}</p>
        )}
      </div>
      <div className="flex flex-wrap items-start gap-1">
        <Button
          aria-label={`Move ${lesson.title} up`}
          disabled={index === 0 || busy}
          onClick={() => onReorder(index, -1)}
          variant="secondary"
        >
          ↑
        </Button>
        <Button
          aria-label={`Move ${lesson.title} down`}
          disabled={index === module.lessons.length - 1 || busy}
          onClick={() => onReorder(index, 1)}
          variant="secondary"
        >
          ↓
        </Button>
        {modules.items.length > 1 && (
          <label className="sr-only" htmlFor={`move-lesson-${lesson.id}`}>
            Move {lesson.title} to module
          </label>
        )}
        {modules.items.length > 1 && (
          <select
            className="min-h-10 max-w-40 rounded border border-surface-300 px-2 text-sm"
            id={`move-lesson-${lesson.id}`}
            onChange={(event) => {
              if (event.target.value) onMoveTo(Number(event.target.value));
              setTargetModule("");
            }}
            value={targetModule}
          >
            <option value="">Move to…</option>
            {modules.items
              .filter((item) => item.id !== module.id)
              .map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title}
                </option>
              ))}
          </select>
        )}
        <Button onClick={() => onEdit(lesson)} variant="secondary">
          Edit
        </Button>
        <Button
          onClick={() => onDelete({ kind: "lesson", ...lesson })}
          variant="danger"
        >
          Delete
        </Button>
      </div>
    </li>
  );
}

export function CourseOutlineBuilder({ course }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const courseId = String(course.id);
  const queryKey = ["course-outline", courseId];
  const outlineQuery = useQuery({
    queryKey,
    queryFn: () => getOutline(courseId),
  });
  const [deleteTarget, setDeleteTarget] = useState(null);
  const [editLessonTarget, setEditLessonTarget] = useState(null);
  const [moduleTitle, setModuleTitle] = useState("");
  const [moduleDescription, setModuleDescription] = useState("");
  const [newLesson, setNewLesson] = useState({});
  const sensors = useSensors(
    useSensor(PointerSensor, { activationConstraint: { distance: 6 } }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  );
  const invalidate = () => queryClient.invalidateQueries({ queryKey });
  const moduleMutation = useMutation({
    mutationFn: (body) => createModule(course.id, body),
    onSuccess: () => {
      setModuleTitle("");
      setModuleDescription("");
      invalidate();
    },
    onError: () =>
      toast.notify({
        title: "Module not added",
        description: "Please try again.",
      }),
  });
  const lessonMutation = useMutation({
    mutationFn: ({ moduleId, body }) => createLesson(moduleId, body),
    onSuccess: (_, variables) => {
      setNewLesson((values) => ({ ...values, [variables.moduleId]: "" }));
      invalidate();
    },
    onError: () =>
      toast.notify({
        title: "Lesson not added",
        description: "Please try again.",
      }),
  });
  const moduleEditMutation = useMutation({
    mutationFn: ({ id, body }) => updateModule(id, body),
    onSuccess: invalidate,
    onError: () =>
      toast.notify({
        title: "Module not updated",
        description: "Please try again.",
      }),
  });
  const lessonEditMutation = useMutation({
    mutationFn: ({ id, body }) => updateLesson(id, body),
    onSuccess: invalidate,
    onError: () =>
      toast.notify({
        title: "Lesson not updated",
        description: "Please try again.",
      }),
  });
  const moduleOrderMutation = useMutation({
    mutationFn: (ids) => reorderModules(course.id, ids),
    onMutate: async (ids) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData(queryKey);
      if (previous) {
        const byId = new Map(previous.modules.map((item) => [item.id, item]));
        queryClient.setQueryData(queryKey, {
          ...previous,
          modules: ids.map((id, position) => ({ ...byId.get(id), position })),
        });
      }
      return { previous };
    },
    onError: (_, __, context) => {
      if (context?.previous)
        queryClient.setQueryData(queryKey, context.previous);
      toast.notify({
        title: "Order not saved",
        description: "The previous module order was restored.",
      });
    },
    onSettled: invalidate,
  });
  const lessonOrderMutation = useMutation({
    mutationFn: ({ moduleId, ids }) => reorderLessons(moduleId, ids),
    onMutate: async ({ moduleId, ids }) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData(queryKey);
      if (previous) {
        queryClient.setQueryData(queryKey, {
          ...previous,
          modules: previous.modules.map((module) =>
            module.id === moduleId
              ? {
                  ...module,
                  lessons: ids.map((id, position) => ({
                    ...module.lessons.find((lesson) => lesson.id === id),
                    position,
                  })),
                }
              : module,
          ),
        });
      }
      return { previous };
    },
    onError: (_, __, context) => {
      if (context?.previous)
        queryClient.setQueryData(queryKey, context.previous);
      toast.notify({
        title: "Order not saved",
        description: "The previous lesson order was restored.",
      });
    },
    onSettled: invalidate,
  });
  const moveMutation = useMutation({
    mutationFn: ({ lessonId, targetModuleId, position }) =>
      moveLesson(lessonId, targetModuleId, position),
    onMutate: async ({ lessonId, targetModuleId, position }) => {
      await queryClient.cancelQueries({ queryKey });
      const previous = queryClient.getQueryData(queryKey);
      if (previous)
        queryClient.setQueryData(
          queryKey,
          reorderLessonSnapshot(previous, lessonId, targetModuleId, position),
        );
      return { previous };
    },
    onError: (_, __, context) => {
      if (context?.previous)
        queryClient.setQueryData(queryKey, context.previous);
      toast.notify({
        title: "Lesson not moved",
        description: "The previous outline was restored.",
      });
    },
    onSettled: invalidate,
  });
  const deleteMutation = useMutation({
    mutationFn: (target) =>
      target.kind === "module"
        ? deleteModule(target.id)
        : deleteLesson(target.id),
    onSuccess: () => {
      setDeleteTarget(null);
      invalidate();
      toast.notify({
        title: "Outline item deleted",
        description: "The outline order was updated.",
      });
    },
    onError: () =>
      toast.notify({
        title: "Delete failed",
        description: "Refresh the outline and try again.",
      }),
  });
  const busy =
    moduleOrderMutation.isPending ||
    lessonOrderMutation.isPending ||
    moveMutation.isPending;

  if (outlineQuery.isPending) return <Spinner label="Loading course outline" />;
  if (outlineQuery.isError) {
    return (
      <ErrorState
        description="The outline could not be loaded."
        title="Outline unavailable"
      />
    );
  }
  const outline = outlineQuery.data;

  function onDragEnd({ active, over }) {
    if (!over || active.id === over.id) return;
    const activeData = active.data.current;
    const overData = over.data.current;
    if (activeData?.type === "module" && overData?.type === "module") {
      const oldIndex = outline.modules.findIndex(
        (item) => item.id === activeData.moduleId,
      );
      const newIndex = outline.modules.findIndex(
        (item) => item.id === overData.moduleId,
      );
      moduleOrderMutation.mutate(
        arrayMove(outline.modules, oldIndex, newIndex).map((item) => item.id),
      );
      return;
    }
    if (activeData?.type !== "lesson") return;
    const targetModuleId =
      overData?.type === "module" ? overData.moduleId : overData?.moduleId;
    if (!targetModuleId) return;
    const source = outline.modules.find(
      (item) => item.id === activeData.moduleId,
    );
    const target = outline.modules.find((item) => item.id === targetModuleId);
    if (!source || !target) return;
    const targetLessonIndex =
      overData.type === "lesson"
        ? target.lessons.findIndex((item) => item.id === overData.lessonId)
        : target.lessons.length;
    let position =
      targetLessonIndex < 0 ? target.lessons.length : targetLessonIndex;
    if (source.id === target.id) {
      const sourceIndex = source.lessons.findIndex(
        (item) => item.id === activeData.lessonId,
      );
      if (sourceIndex < position) position -= 1;
    }
    moveMutation.mutate({
      lessonId: activeData.lessonId,
      targetModuleId,
      position,
    });
  }

  function moveLessonByButton(module, index, delta) {
    const next = arrayMove(module.lessons, index, index + delta);
    lessonOrderMutation.mutate({
      moduleId: module.id,
      ids: next.map((lesson) => lesson.id),
    });
  }

  function moveModuleByButton(index, delta) {
    const reordered = arrayMove(outline.modules, index, index + delta);
    moduleOrderMutation.mutate(reordered.map((module) => module.id));
  }

  return (
    <section aria-label="Course outline">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 className="text-2xl font-semibold text-ink-900">Outline</h2>
          <p className="mt-1 text-sm text-ink-700">
            Drag to reorder, or use the move buttons with a keyboard.
          </p>
        </div>
        <p className="text-sm text-ink-700" aria-live="polite">
          {outline.modules.length} modules ·{" "}
          {outline.modules.reduce(
            (sum, module) => sum + module.lessons.length,
            0,
          )}{" "}
          lessons
        </p>
      </div>
      <form
        className="mt-5 grid gap-2 rounded-lg border border-surface-200 bg-surface-50 p-4 sm:grid-cols-[1fr_1fr_auto]"
        noValidate
        onSubmit={(event) => {
          event.preventDefault();
          const parsed = moduleSchema.safeParse({
            title: moduleTitle,
            description: moduleDescription,
          });
          if (!parsed.success) {
            toast.notify({
              title: "Check module details",
              description: parsed.error.issues[0].message,
            });
            return;
          }
          moduleMutation.mutate(parsed.data);
        }}
      >
        <label
          className="grid gap-1 text-sm font-medium"
          htmlFor="new-module-title"
        >
          New module
          <input
            className="min-h-10 rounded-md border border-surface-300 bg-white px-3"
            id="new-module-title"
            maxLength={120}
            onChange={(event) => setModuleTitle(event.target.value)}
            placeholder="Module title"
            value={moduleTitle}
          />
        </label>
        <label
          className="grid gap-1 text-sm font-medium"
          htmlFor="new-module-description"
        >
          Description (optional)
          <input
            className="min-h-10 rounded-md border border-surface-300 bg-white px-3 font-normal"
            id="new-module-description"
            maxLength={1000}
            onChange={(event) => setModuleDescription(event.target.value)}
            placeholder="Short description"
            value={moduleDescription}
          />
        </label>
        <Button disabled={moduleMutation.isPending} type="submit">
          {moduleMutation.isPending ? "Adding…" : "Add module"}
        </Button>
      </form>
      {outline.modules.length === 0 ? (
        <div className="mt-5">
          <EmptyState
            description="Add your first module above."
            title="This outline is empty"
          />
        </div>
      ) : (
        <DndContext
          collisionDetection={closestCenter}
          onDragEnd={onDragEnd}
          sensors={sensors}
        >
          <SortableContext
            items={outline.modules.map((module) =>
              sortableId("module", module.id),
            )}
            strategy={verticalListSortingStrategy}
          >
            <div className="mt-5 grid gap-4">
              {outline.modules.map((module) => (
                <div key={module.id} data-course-module={module.id}>
                  <SortableModule
                    busy={busy || moduleMutation.isPending}
                    module={module}
                    modules={outline.modules}
                    onDelete={setDeleteTarget}
                    onMove={moveModuleByButton}
                  >
                    <InlineForm
                      defaults={{
                        title: module.title,
                        description: module.description ?? "",
                      }}
                      disabled={moduleEditMutation.isPending}
                      fields={[
                        {
                          name: "title",
                          label: "Module title",
                          maxLength: 120,
                        },
                        {
                          name: "description",
                          label: "Description",
                          maxLength: 1000,
                        },
                      ]}
                      onSave={(values, close) =>
                        moduleEditMutation.mutate(
                          {
                            id: module.id,
                            body: {
                              title: values.title,
                              description: values.description || null,
                            },
                          },
                          { onSuccess: close },
                        )
                      }
                      schema={moduleSchema}
                      submitLabel="Edit module"
                    />
                    <form
                      className="mt-4 flex flex-wrap gap-2"
                      noValidate
                      onSubmit={(event) => {
                        event.preventDefault();
                        const title = newLesson[module.id] ?? "";
                        const parsed = lessonSchema.safeParse({
                          title,
                          summary: "",
                        });
                        if (!parsed.success) {
                          toast.notify({
                            title: "Lesson title required",
                            description: parsed.error.issues[0].message,
                          });
                          return;
                        }
                        lessonMutation.mutate({
                          moduleId: module.id,
                          body: parsed.data,
                        });
                      }}
                    >
                      <label
                        className="sr-only"
                        htmlFor={`new-lesson-${module.id}`}
                      >
                        New lesson title
                      </label>
                      <input
                        className="min-h-10 min-w-48 flex-1 rounded-md border border-surface-300 px-3"
                        id={`new-lesson-${module.id}`}
                        maxLength={150}
                        onChange={(event) =>
                          setNewLesson((values) => ({
                            ...values,
                            [module.id]: event.target.value,
                          }))
                        }
                        placeholder="Add a lesson"
                        value={newLesson[module.id] ?? ""}
                      />
                      <Button disabled={lessonMutation.isPending} type="submit">
                        {lessonMutation.isPending ? "Adding…" : "Add lesson"}
                      </Button>
                    </form>
                    <SortableContext
                      items={module.lessons.map((lesson) =>
                        sortableId("lesson", lesson.id),
                      )}
                      strategy={verticalListSortingStrategy}
                    >
                      {module.lessons.length > 0 ? (
                        <ol className="mt-3 grid gap-2">
                          {module.lessons.map((lesson, lessonIndex) => (
                            <SortableLesson
                              busy={busy}
                              index={lessonIndex}
                              key={lesson.id}
                              lesson={lesson}
                              module={module}
                              modules={{ items: outline.modules, courseId }}
                              onDelete={setDeleteTarget}
                              onEdit={setEditLessonTarget}
                              onMoveTo={(targetModuleId) => {
                                const target = outline.modules.find(
                                  (candidate) =>
                                    candidate.id === targetModuleId,
                                );
                                if (target)
                                  moveMutation.mutate({
                                    lessonId: lesson.id,
                                    targetModuleId,
                                    position: target.lessons.length,
                                  });
                              }}
                              onReorder={(position, delta) =>
                                moveLessonByButton(module, position, delta)
                              }
                            />
                          ))}
                        </ol>
                      ) : (
                        <p className="mt-3 text-sm text-ink-700">
                          No lessons in this module yet.
                        </p>
                      )}
                    </SortableContext>
                  </SortableModule>
                </div>
              ))}
            </div>
          </SortableContext>
        </DndContext>
      )}
      <Confirmation
        busy={deleteMutation.isPending}
        item={deleteTarget}
        onClose={() => setDeleteTarget(null)}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
      />
      {editLessonTarget && (
        <LessonEditDialog
          lesson={editLessonTarget}
          onClose={() => setEditLessonTarget(null)}
          onSave={(body) =>
            lessonEditMutation.mutate(
              { id: editLessonTarget.id, body },
              { onSuccess: () => setEditLessonTarget(null) },
            )
          }
          saving={lessonEditMutation.isPending}
        />
      )}
      <p className="sr-only" aria-live="polite">
        {outline.modules.length} modules in this course.
      </p>
    </section>
  );
}
