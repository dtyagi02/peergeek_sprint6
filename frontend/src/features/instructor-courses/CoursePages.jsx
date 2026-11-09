import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import * as Select from "@radix-ui/react-select";
import * as Tabs from "@radix-ui/react-tabs";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../shared/api/ApiError";
import { Button } from "../../shared/components/Button";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";
import { FormField } from "../../shared/components/FormField";
import { Spinner } from "../../shared/components/Spinner";
import { useToast } from "../../shared/components/useToast";
import { Slot } from "../../app/registry/slots";
import { applyApiFieldErrors } from "../auth/zodValidation";
import { CourseOutlineBuilder } from "./CourseOutlineBuilder";
import { InstructorModeBanner } from "./InstructorModeBanner";
import { InstructorCourseSettings } from "../doubtsession/InstructorCourseSettings";
import {
  createCourse,
  deleteCourse,
  getCourse,
  listCategories,
  listCourses,
  updateCourse,
} from "./courseApi";

const titleSchema = z.string().trim().min(3).max(120);
const topicsSchema = z
  .array(z.string().trim().min(2).max(40))
  .max(10)
  .superRefine((topics, context) => {
    if (
      new Set(topics.map((topic) => topic.toLowerCase())).size !== topics.length
    ) {
      context.addIssue({
        code: "custom",
        message: "Topics must be unique.",
      });
    }
  });
const createSchema = z.object({
  title: titleSchema,
  description: z.string().max(5000),
  topics: topicsSchema,
});

function SelectField({
  label,
  value,
  onChange,
  options,
  placeholder = "Choose…",
}) {
  return (
    <div className="grid gap-2">
      <span className="text-sm font-medium text-ink-900">{label}</span>
      <Select.Root onValueChange={onChange} value={value || "none"}>
        <Select.Trigger
          aria-label={label}
          className="flex min-h-10 items-center justify-between rounded-md border border-surface-300 bg-white px-3 text-left text-sm"
        >
          <Select.Value placeholder={placeholder} />
          <Select.Icon aria-hidden="true">▾</Select.Icon>
        </Select.Trigger>
        <Select.Portal>
          <Select.Content className="z-50 max-h-64 overflow-auto rounded-md border border-surface-200 bg-white p-1 shadow-lg">
            <Select.Viewport>
              <Select.Item
                className="cursor-pointer rounded px-3 py-2 text-sm outline-none data-[highlighted]:bg-surface-100"
                value="none"
              >
                <Select.ItemText>{placeholder}</Select.ItemText>
              </Select.Item>
              {options.map((option) => (
                <Select.Item
                  className="cursor-pointer rounded px-3 py-2 text-sm outline-none data-[highlighted]:bg-surface-100"
                  key={option.value}
                  value={option.value}
                >
                  <Select.ItemText>{option.label}</Select.ItemText>
                </Select.Item>
              ))}
            </Select.Viewport>
          </Select.Content>
        </Select.Portal>
      </Select.Root>
    </div>
  );
}

function ConfirmDialog({
  open,
  onOpenChange,
  title,
  children,
  onConfirm,
  busy,
}) {
  return (
    <Dialog.Root onOpenChange={onOpenChange} open={open}>
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40" />
        <Dialog.Content
          aria-describedby="authoring-dialog-description"
          className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,30rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl"
        >
          <Dialog.Title className="text-lg font-semibold text-ink-900">
            {title}
          </Dialog.Title>
          <Dialog.Description
            className="mt-2 text-sm text-ink-700"
            id="authoring-dialog-description"
          >
            {children}
          </Dialog.Description>
          <div className="mt-6 flex justify-end gap-3">
            <Dialog.Close asChild>
              <Button variant="secondary">Cancel</Button>
            </Dialog.Close>
            <Button disabled={busy} onClick={onConfirm} variant="danger">
              {busy ? "Working…" : "Confirm"}
            </Button>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  );
}

function FieldErrors({ errors }) {
  return Object.keys(errors).length > 0 ? (
    <div aria-live="polite" className="text-sm text-danger-700" role="alert">
      {Object.values(errors).map((error, index) => (
        <p key={`${error.message}-${index}`}>{error.message}</p>
      ))}
    </div>
  ) : null;
}

export function CourseDashboardPage() {
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("");
  const [sort, setSort] = useState("updatedAt,desc");
  const [selected, setSelected] = useState(null);
  const queryClient = useQueryClient();
  const toast = useToast();
  const filters = { page, size: 20, query, status, sort };
  const coursesQuery = useQuery({
    queryKey: ["instructor-courses", filters],
    queryFn: () => listCourses(filters),
  });
  const removeCourse = useMutation({
    mutationFn: deleteCourse,
    onSuccess: () => {
      setSelected(null);
      queryClient.invalidateQueries({ queryKey: ["instructor-courses"] });
      toast.notify({
        title: "Draft deleted",
        description: "The course was moved to deleted state.",
      });
    },
    onError: () =>
      toast.notify({
        title: "Could not delete course",
        description: "Refresh the course list and try again.",
      }),
  });
  const data = coursesQuery.data;

  return (
    <section className="mx-auto max-w-5xl">
      <InstructorModeBanner />
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-sm font-medium text-brand-700">
            INSTRUCTOR WORKSPACE
          </p>
          <h1 className="mt-1 text-3xl font-bold text-ink-900">My Courses</h1>
        </div>
        <Link
          className="inline-flex min-h-10 items-center justify-center rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white hover:bg-brand-700"
          to="/instructor/courses/new"
        >
          New course
        </Link>
      </div>
      <div className="mt-6 grid gap-3 sm:grid-cols-[1fr_12rem_13rem]">
        <label className="grid gap-1 text-sm font-medium">
          Search by title
          <input
            className="min-h-10 rounded-md border border-surface-300 px-3 font-normal"
            onChange={(event) => {
              setPage(0);
              setQuery(event.target.value);
            }}
            placeholder="Search courses"
            value={query}
          />
        </label>
        <SelectField
          label="Status"
          onChange={(value) => {
            setPage(0);
            setStatus(value === "none" ? "" : value);
          }}
          options={[
            { value: "DRAFT", label: "Draft" },
            { value: "PUBLISHED", label: "Published" },
            { value: "UNPUBLISHED", label: "Unpublished" },
          ]}
          placeholder="All statuses"
          value={status}
        />
        <SelectField
          label="Sort courses"
          onChange={setSort}
          options={[
            { value: "updatedAt,desc", label: "Recently updated" },
            { value: "createdAt,desc", label: "Recently created" },
            { value: "title,asc", label: "Title A to Z" },
            { value: "title,desc", label: "Title Z to A" },
          ]}
          value={sort}
        />
      </div>
      {coursesQuery.isPending ? (
        <div className="py-10">
          <Spinner label="Loading courses" />
        </div>
      ) : coursesQuery.isError ? (
        <div className="mt-8">
          <ErrorState
            description="Your course list could not be loaded."
            title="Courses unavailable"
          />
          <Button
            className="mt-4"
            onClick={() => coursesQuery.refetch()}
            variant="secondary"
          >
            Try again
          </Button>
        </div>
      ) : data.content.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            description="Create a draft and start building its outline."
            title="No courses yet"
          />
        </div>
      ) : (
        <>
          <div className="mt-6 overflow-x-auto rounded-lg border border-surface-200 bg-white">
            <table className="w-full min-w-[42rem] text-left text-sm">
              <thead className="bg-surface-50 text-ink-700">
                <tr>
                  <th className="px-4 py-3 font-semibold">Course</th>
                  <th className="px-4 py-3 font-semibold">Status</th>
                  <th className="px-4 py-3 font-semibold">Structure</th>
                  <th className="px-4 py-3 font-semibold">Updated</th>
                  <th className="px-4 py-3">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((course) => (
                  <tr className="border-t border-surface-100" key={course.id}>
                    <td className="px-4 py-4">
                      <Link
                        className="font-semibold text-brand-700 underline"
                        to={`/instructor/courses/${course.id}`}
                      >
                        {course.title}
                      </Link>
                    </td>
                    <td className="px-4 py-4">
                      <span className="rounded-full bg-surface-100 px-2 py-1 text-xs font-semibold">
                        {course.status}
                      </span>
                    </td>
                    <td className="px-4 py-4">
                      {course.moduleCount} modules · {course.lessonCount}{" "}
                      lessons
                    </td>
                    <td className="px-4 py-4">
                      {new Intl.DateTimeFormat(undefined, {
                        dateStyle: "medium",
                        timeStyle: "short",
                      }).format(new Date(course.updatedAt))}
                    </td>
                    <td className="px-4 py-4 text-right space-x-2">
                      <Link
                        to={`/instructor/courses/${course.id}?tab=doubts`}
                        className="inline-flex items-center rounded-md border border-indigo-200 bg-indigo-50 px-2.5 py-1 text-xs font-semibold text-indigo-700 hover:bg-indigo-100"
                      >
                        💬 Live Doubts
                      </Link>
                      {course.status === "DRAFT" && (
                        <Button
                          onClick={() => setSelected(course)}
                          variant="secondary"
                        >
                          Delete
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div className="mt-4 flex items-center justify-between">
            <p aria-live="polite" className="text-sm text-ink-700">
              Page {data.page + 1} of {Math.max(1, data.totalPages)}
            </p>
            <div className="flex gap-2">
              <Button
                disabled={data.page === 0}
                onClick={() => setPage((value) => Math.max(0, value - 1))}
                variant="secondary"
              >
                Previous
              </Button>
              <Button
                disabled={data.page + 1 >= data.totalPages}
                onClick={() => setPage((value) => value + 1)}
                variant="secondary"
              >
                Next
              </Button>
            </div>
          </div>
        </>
      )}
      <ConfirmDialog
        busy={removeCourse.isPending}
        onConfirm={() => selected && removeCourse.mutate(selected.id)}
        onOpenChange={(open) => !open && setSelected(null)}
        open={selected !== null}
        title="Delete this draft?"
      >
        “{selected?.title}” will be hidden from your course list. You can only
        delete draft courses.
      </ConfirmDialog>
    </section>
  );
}

export function NewCoursePage() {
  const navigate = useNavigate();
  const toast = useToast();
  const categoriesQuery = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });
  const form = useForm({
    defaultValues: {
      title: "",
      description: "",
      categoryId: "",
      difficulty: "",
      topics: [],
    },
  });
  const [topicInput, setTopicInput] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [difficulty, setDifficulty] = useState("");
  const mutation = useMutation({
    mutationFn: createCourse,
    onSuccess: (course) => {
      toast.notify({
        title: "Draft created",
        description: "Add modules and lessons to your course.",
      });
      navigate(`/instructor/courses/${course.id}`);
    },
    onError: (error) => {
      if (error instanceof ApiError) applyApiFieldErrors(error, form.setError);
      form.setError("root.serverError", {
        type: "server",
        message:
          error instanceof ApiError
            ? error.message
            : "Could not create the course.",
      });
    },
  });

  function addTopic(event) {
    event.preventDefault();
    const topic = topicInput.trim();
    if (!topic) return;
    form.setValue("topics", [...form.getValues("topics"), topic], {
      shouldValidate: true,
    });
    setTopicInput("");
  }

  const submit = form.handleSubmit((values) => {
    form.clearErrors();
    const result = createSchema.safeParse(values);
    if (!result.success) {
      for (const issue of result.error.issues) {
        const path = issue.path.join(".");
        form.setError(path || "root.serverError", {
          type: "validation",
          message: issue.message,
        });
      }
      return;
    }
    mutation.mutate({
      ...result.data,
      categoryId: categoryId ? Number(categoryId) : null,
      difficulty: difficulty || null,
    });
  });

  return (
    <section className="mx-auto max-w-3xl">
      <InstructorModeBanner />
      <Link
        className="text-sm text-brand-700 underline"
        to="/instructor/courses"
      >
        ← My Courses
      </Link>
      <h1 className="mt-4 text-3xl font-bold text-ink-900">Create a course</h1>
      <p className="mt-2 text-sm text-ink-700">
        Start with a title. You can fill in other details later.
      </p>
      {categoriesQuery.isError && (
        <p role="alert" className="mt-4 text-danger-700">
          Categories could not be loaded.
        </p>
      )}
      <FormProvider {...form}>
        <form className="mt-6 grid gap-5" noValidate onSubmit={submit}>
          <FormField
            label="Course title"
            maxLength={120}
            name="title"
            required
          />
          <div className="grid gap-2">
            <label
              className="text-sm font-medium text-ink-900"
              htmlFor="course-description"
            >
              Description
            </label>
            <textarea
              className="min-h-32 rounded-md border border-surface-300 px-3 py-2"
              id="course-description"
              maxLength={5000}
              {...form.register("description")}
            />
          </div>
          <SelectField
            label="Category"
            onChange={(value) => setCategoryId(value === "none" ? "" : value)}
            options={(categoriesQuery.data ?? []).map((category) => ({
              value: String(category.id),
              label: category.name,
            }))}
            value={categoryId}
          />
          <SelectField
            label="Difficulty"
            onChange={(value) => setDifficulty(value === "none" ? "" : value)}
            options={[
              { value: "BEGINNER", label: "Beginner" },
              { value: "INTERMEDIATE", label: "Intermediate" },
              { value: "ADVANCED", label: "Advanced" },
            ]}
            value={difficulty}
          />
          <TopicEditor
            form={form}
            input={topicInput}
            onInput={setTopicInput}
            onAdd={addTopic}
          />
          <FieldErrors errors={form.formState.errors} />
          <div className="flex gap-3">
            <Button disabled={mutation.isPending} type="submit">
              {mutation.isPending ? "Creating…" : "Create draft"}
            </Button>
            <Link
              className="inline-flex min-h-10 items-center justify-center rounded-md border border-surface-200 bg-white px-4 py-2 text-sm font-semibold text-ink-900 hover:bg-surface-100"
              to="/instructor/courses"
            >
              Cancel
            </Link>
          </div>
        </form>
      </FormProvider>
    </section>
  );
}

function TopicEditor({ form, input, onInput, onAdd }) {
  const topics = useWatch({ control: form.control, name: "topics" });
  return (
    <div className="grid gap-2">
      <label
        className="text-sm font-medium text-ink-900"
        htmlFor="course-topics"
      >
        Topics (up to 10)
      </label>
      <div className="flex gap-2">
        <input
          className="min-h-10 flex-1 rounded-md border border-surface-300 px-3"
          id="course-topics"
          maxLength={40}
          onChange={(event) => onInput(event.target.value)}
          onKeyDown={(event) => event.key === "Enter" && onAdd(event)}
          value={input}
        />
        <Button
          disabled={topics.length >= 10}
          onClick={onAdd}
          type="button"
          variant="secondary"
        >
          Add topic
        </Button>
      </div>
      <ul aria-label="Course topics" className="flex flex-wrap gap-2">
        {topics.map((topic, index) => (
          <li
            className="flex items-center gap-2 rounded-full bg-surface-100 px-3 py-1 text-sm"
            key={`${topic}-${index}`}
          >
            {topic}
            <button
              aria-label={`Remove ${topic}`}
              className="font-bold text-ink-700"
              onClick={() =>
                form.setValue(
                  "topics",
                  topics.filter((_, itemIndex) => itemIndex !== index),
                  { shouldValidate: true },
                )
              }
              type="button"
            >
              ×
            </button>
          </li>
        ))}
      </ul>
      {form.formState.errors.topics && (
        <p className="text-sm text-danger-700" role="alert">
          {form.formState.errors.topics.message}
        </p>
      )}
    </div>
  );
}

export function CourseEditorPage() {
  const { courseId } = useParams();
  const [searchParams, setSearchParams] = useSearchParams();
  const tabParam = searchParams.get("tab");
  const selectedTab =
    tabParam === "outline" || tabParam === "doubts" ? tabParam : "details";
  const courseQuery = useQuery({
    queryKey: ["instructor-course", courseId],
    queryFn: () => getCourse(courseId),
  });
  if (courseQuery.isPending) return <Spinner label="Loading course" />;
  if (courseQuery.isError) {
    return (
      <ErrorState
        description="The course could not be loaded."
        title="Course unavailable"
      />
    );
  }
  return (
    <section className="mx-auto max-w-5xl">
      <InstructorModeBanner />
      <Link
        className="text-sm text-brand-700 underline"
        to="/instructor/courses"
      >
        ← My Courses
      </Link>
      <h1 className="mt-3 text-3xl font-bold text-ink-900">
        {courseQuery.data.title}
      </h1>
      <Slot name="course-editor.header-actions" />
      <p className="mt-2 text-sm text-ink-700">
        {courseQuery.data.status} · version {courseQuery.data.version}
      </p>
      <Tabs.Root
        className="mt-6"
        onValueChange={(value) =>
          setSearchParams(value === "details" ? {} : { tab: value })
        }
        value={selectedTab}
      >
        <Tabs.List
          aria-label="Course editor sections"
          className="flex gap-2 border-b border-surface-200"
        >
          <Tabs.Trigger
            className="border-b-2 border-transparent px-4 py-3 data-[state=active]:border-brand-600"
            value="details"
          >
            Details
          </Tabs.Trigger>
          <Tabs.Trigger
            className="border-b-2 border-transparent px-4 py-3 data-[state=active]:border-brand-600"
            value="outline"
          >
            Outline
          </Tabs.Trigger>
          <Tabs.Trigger
            className="border-b-2 border-transparent px-4 py-3 data-[state=active]:border-brand-600"
            value="doubts"
          >
            Doubt Sessions 💬
          </Tabs.Trigger>
        </Tabs.List>
        <Tabs.Content className="pt-6" value="details">
          <CourseDetails course={courseQuery.data} />
        </Tabs.Content>
        <Tabs.Content className="pt-6" value="outline">
          <CourseOutlineBuilder course={courseQuery.data} />
        </Tabs.Content>
        <Tabs.Content className="pt-6" value="doubts">
          <InstructorCourseSettings courseId={courseQuery.data.id} />
        </Tabs.Content>
      </Tabs.Root>
    </section>
  );
}

function CourseDetails({ course }) {
  const queryClient = useQueryClient();
  const toast = useToast();
  const categoriesQuery = useQuery({
    queryKey: ["categories"],
    queryFn: listCategories,
  });
  const [topicInput, setTopicInput] = useState("");
  const [conflictOpen, setConflictOpen] = useState(false);
  const form = useForm({
    defaultValues: {
      title: course.title,
      description: course.description ?? "",
      categoryId: course.category ? String(course.category.id) : "",
      difficulty: course.difficulty ?? "",
      topics: course.topics ?? [],
    },
  });
  useEffect(() => {
    form.reset({
      title: course.title,
      description: course.description ?? "",
      categoryId: course.category ? String(course.category.id) : "",
      difficulty: course.difficulty ?? "",
      topics: course.topics ?? [],
    });
  }, [course.version, course.title, course.description, course, form]);
  const mutation = useMutation({
    mutationFn: (body) => updateCourse(course.id, body),
    onSuccess: (updated) => {
      queryClient.setQueryData(
        ["instructor-course", String(course.id)],
        updated,
      );
      queryClient.invalidateQueries({ queryKey: ["instructor-courses"] });
      toast.notify({
        title: "Course saved",
        description: "Your course details are up to date.",
      });
    },
    onError: (error) => {
      if (error instanceof ApiError && error.status === 409)
        setConflictOpen(true);
      if (error instanceof ApiError) applyApiFieldErrors(error, form.setError);
      form.setError("root.serverError", {
        type: "server",
        message:
          error instanceof ApiError
            ? error.message
            : "Could not save course details.",
      });
    },
  });
  const categoryId = useWatch({ control: form.control, name: "categoryId" });
  const difficulty = useWatch({ control: form.control, name: "difficulty" });
  const topics = useWatch({ control: form.control, name: "topics" });
  function addTopic(event) {
    event.preventDefault();
    const next = topicInput.trim();
    if (next)
      form.setValue("topics", [...topics, next], { shouldValidate: true });
    setTopicInput("");
  }
  const submit = form.handleSubmit((values) => {
    form.clearErrors();
    const result = createSchema.safeParse(values);
    if (!result.success) {
      result.error.issues.forEach((issue) =>
        form.setError(issue.path.join(".") || "root.serverError", {
          type: "validation",
          message: issue.message,
        }),
      );
      return;
    }
    mutation.mutate({
      ...result.data,
      categoryId: categoryId ? Number(categoryId) : null,
      difficulty: difficulty || null,
      version: course.version,
    });
  });
  return (
    <>
      <Slot name="course-details.thumbnail" />
      <FormProvider {...form}>
        <form className="grid max-w-3xl gap-5" noValidate onSubmit={submit}>
          <FormField
            label="Course title"
            maxLength={120}
            name="title"
            required
          />
          <div className="grid gap-2">
            <label
              className="text-sm font-medium"
              htmlFor="course-details-description"
            >
              Description
            </label>
            <textarea
              className="min-h-36 rounded-md border border-surface-300 px-3 py-2"
              id="course-details-description"
              maxLength={5000}
              {...form.register("description")}
            />
          </div>
          {categoriesQuery.isError ? (
            <p role="alert" className="text-danger-700">
              Categories could not be loaded.
            </p>
          ) : (
            <SelectField
              label="Category"
              onChange={(value) =>
                form.setValue("categoryId", value === "none" ? "" : value, {
                  shouldDirty: true,
                })
              }
              options={(categoriesQuery.data ?? []).map((category) => ({
                value: String(category.id),
                label: category.name,
              }))}
              value={categoryId}
            />
          )}
          <SelectField
            label="Difficulty"
            onChange={(value) =>
              form.setValue("difficulty", value === "none" ? "" : value, {
                shouldDirty: true,
              })
            }
            options={[
              { value: "BEGINNER", label: "Beginner" },
              { value: "INTERMEDIATE", label: "Intermediate" },
              { value: "ADVANCED", label: "Advanced" },
            ]}
            value={difficulty}
          />
          <div className="grid gap-2">
            <label
              className="text-sm font-medium"
              htmlFor="details-topic-input"
            >
              Topics (up to 10)
            </label>
            <div className="flex gap-2">
              <input
                className="min-h-10 flex-1 rounded-md border border-surface-300 px-3"
                id="details-topic-input"
                maxLength={40}
                onChange={(event) => setTopicInput(event.target.value)}
                onKeyDown={(event) => event.key === "Enter" && addTopic(event)}
                value={topicInput}
              />
              <Button
                disabled={topics.length >= 10}
                onClick={addTopic}
                type="button"
                variant="secondary"
              >
                Add
              </Button>
            </div>
            <ul className="flex flex-wrap gap-2">
              {topics.map((topic, index) => (
                <li
                  className="flex items-center gap-2 rounded-full bg-surface-100 px-3 py-1 text-sm"
                  key={`${topic}-${index}`}
                >
                  {topic}
                  <button
                    aria-label={`Remove ${topic}`}
                    onClick={() =>
                      form.setValue(
                        "topics",
                        topics.filter((_, item) => item !== index),
                        { shouldValidate: true },
                      )
                    }
                    type="button"
                  >
                    ×
                  </button>
                </li>
              ))}
            </ul>
            {form.formState.errors.topics && (
              <p role="alert" className="text-sm text-danger-700">
                {form.formState.errors.topics.message}
              </p>
            )}
          </div>
          <FieldErrors errors={form.formState.errors} />
          <Button disabled={mutation.isPending} type="submit">
            {mutation.isPending ? "Saving…" : "Save details"}
          </Button>
        </form>
      </FormProvider>
      <ConfirmDialog
        onConfirm={() => {
          setConflictOpen(false);
          queryClient.invalidateQueries({
            queryKey: ["instructor-course", String(course.id)],
          });
        }}
        onOpenChange={setConflictOpen}
        open={conflictOpen}
        title="Course changed elsewhere"
      >
        Reload the latest course values before continuing your edits.
      </ConfirmDialog>
    </>
  );
}
