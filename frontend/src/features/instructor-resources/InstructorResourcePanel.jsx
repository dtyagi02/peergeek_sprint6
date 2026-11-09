import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { useToast } from "../../shared/components/useToast";
import {
  addResourceLink,
  deleteResource,
  getResourceDownloadUrl,
  listExpectedResources,
  listResources,
  reorderResources,
  replaceExpectedResources,
  resourceTypeForFile,
  updateResource,
  uploadLessonResource,
} from "./resourcesApi";

const resourceTypes = ["PDF", "DOCUMENT", "IMAGE", "VIDEO", "LINK"];

export function InstructorResourcePanel() {
  const { lessonId } = useParams();
  const queryClient = useQueryClient();
  const toast = useToast();
  const [link, setLink] = useState({ title: "", url: "" });
  const [downloadUrls, setDownloadUrls] = useState({});
  const [downloadingId, setDownloadingId] = useState(null);
  const [expected, setExpected] = useState({
    resourceType: "PDF",
    minCount: 1,
    label: "",
  });
  const resourcesQuery = useQuery({
    queryKey: ["lesson-resources", lessonId],
    queryFn: () => listResources(lessonId),
    refetchInterval: (query) =>
      query.state.data?.some((resource) =>
        ["PENDING", "UPLOADED", "PROCESSING"].includes(resource.status),
      )
        ? 2000
        : false,
  });
  const expectedQuery = useQuery({
    queryKey: ["lesson-expected-resources", lessonId],
    queryFn: () => listExpectedResources(lessonId),
  });

  async function refresh() {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: ["lesson-resources", lessonId],
      }),
      queryClient.invalidateQueries({
        queryKey: ["lesson-expected-resources", lessonId],
      }),
    ]);
  }

  const action = useMutation({
    mutationFn: async (operation) => operation(),
    onSuccess: refresh,
    onError: (error) =>
      toast.notify({
        title: "Resource change failed",
        description: error.message,
      }),
  });

  async function addLink(event) {
    event.preventDefault();
    try {
      await action.mutateAsync(() => addResourceLink(lessonId, link));
    } catch {
      return;
    }
    setLink({ title: "", url: "" });
    toast.notify({
      title: "Link added",
      description: "The lesson resource is ready.",
    });
  }

  async function upload(event) {
    const file = event.target.files?.[0];
    if (!file) return;
    event.target.value = "";
    try {
      await uploadLessonResource(lessonId, file, resourceTypeForFile(file));
      await refresh();
      toast.notify({
        title: "Upload received",
        description: "The file is being checked before it becomes available.",
      });
    } catch (error) {
      toast.notify({ title: "Upload failed", description: error.message });
    }
  }

  async function addExpected(event) {
    event.preventDefault();
    const current = expectedQuery.data ?? [];
    if (current.some((item) => item.resourceType === expected.resourceType)) {
      toast.notify({
        title: "Expectation already exists",
        description: "Each resource type can be listed once.",
      });
      return;
    }
    action.mutate(() =>
      replaceExpectedResources(lessonId, [...current, expected]),
    );
    setExpected({ resourceType: "PDF", minCount: 1, label: "" });
  }

  function removeExpected(resourceType) {
    action.mutate(() =>
      replaceExpectedResources(
        lessonId,
        (expectedQuery.data ?? []).filter(
          (item) => item.resourceType !== resourceType,
        ),
      ),
    );
  }

  async function move(index, offset) {
    const reordered = [...(resourcesQuery.data ?? [])];
    const target = index + offset;
    if (target < 0 || target >= reordered.length) return;
    [reordered[index], reordered[target]] = [
      reordered[target],
      reordered[index],
    ];
    action.mutate(() =>
      reorderResources(
        lessonId,
        reordered.map((resource) => resource.id),
      ),
    );
  }

  async function loadDownloadUrl(resourceId) {
    setDownloadingId(resourceId);
    try {
      const result = await getResourceDownloadUrl(resourceId);
      setDownloadUrls((current) => ({ ...current, [resourceId]: result.url }));
    } catch (error) {
      toast.notify({
        title: "Download unavailable",
        description: error.message,
      });
    } finally {
      setDownloadingId(null);
    }
  }

  if (resourcesQuery.isPending || expectedQuery.isPending) {
    return <Spinner label="Loading lesson resources" />;
  }

  if (resourcesQuery.isError || expectedQuery.isError) {
    return (
      <section
        aria-labelledby="lesson-resources-title"
        className="my-6 rounded-lg border border-surface-200 p-5"
      >
        <h2 className="text-lg font-semibold" id="lesson-resources-title">
          Lesson resources
        </h2>
        <p className="mt-2 text-sm text-danger-700" role="alert">
          Resources could not be loaded. Refresh the page to try again.
        </p>
      </section>
    );
  }

  const resources = resourcesQuery.data ?? [];
  const expectations = expectedQuery.data ?? [];

  return (
    <section
      aria-labelledby="lesson-resources-title"
      className="my-6 rounded-lg border border-surface-200 bg-white p-5"
    >
      <h2 className="text-lg font-semibold" id="lesson-resources-title">
        Lesson resources
      </h2>
      <p className="mt-1 text-sm text-ink-700">
        Attach files or helpful links to this lesson.
      </p>
      <label className="mt-4 block text-sm font-medium">
        Upload a file
        <input
          accept=".pdf,.docx,.pptx,.xlsx,.txt,.csv,.png,.jpg,.jpeg,.webp,.gif,.mp4,.webm"
          className="mt-2 block w-full text-sm"
          onChange={upload}
          type="file"
        />
      </label>
      <form
        className="mt-4 grid gap-3 sm:grid-cols-[1fr_2fr_auto]"
        onSubmit={addLink}
      >
        <label className="text-sm font-medium">
          Link title
          <input
            className="mt-1 block w-full rounded border border-surface-200 p-2"
            maxLength={255}
            onChange={(event) =>
              setLink({ ...link, title: event.target.value })
            }
            required
            value={link.title}
          />
        </label>
        <label className="text-sm font-medium">
          URL
          <input
            className="mt-1 block w-full rounded border border-surface-200 p-2"
            onChange={(event) => setLink({ ...link, url: event.target.value })}
            required
            type="url"
            value={link.url}
          />
        </label>
        <Button className="self-end" disabled={action.isPending} type="submit">
          Add link
        </Button>
      </form>
      {resources.length === 0 ? (
        <p className="mt-5 text-sm text-ink-700">
          No resources have been added.
        </p>
      ) : (
        <ol
          aria-label="Attached resources"
          className="mt-5 divide-y divide-surface-200"
        >
          {resources.map((resource, index) => (
            <li
              className="flex flex-wrap items-center gap-3 py-3"
              key={resource.id}
            >
              <div className="min-w-0 flex-1">
                <p className="font-medium">{resource.title}</p>
                <p className="text-xs text-ink-700">
                  {resource.type} · {resource.status}
                  {resource.failureReason ? ` · ${resource.failureReason}` : ""}
                </p>
              </div>
              {resource.status === "READY" &&
                (resource.type === "LINK" ? (
                  <a
                    className="text-sm font-semibold text-brand-700 underline"
                    href={resource.externalUrl}
                    rel="noreferrer"
                    target="_blank"
                  >
                    Open link
                  </a>
                ) : downloadUrls[resource.id] ? (
                  <a
                    className="text-sm font-semibold text-brand-700 underline"
                    href={downloadUrls[resource.id]}
                    rel="noreferrer"
                    target="_blank"
                  >
                    Download
                  </a>
                ) : (
                  <Button
                    disabled={downloadingId === resource.id}
                    onClick={() => loadDownloadUrl(resource.id)}
                    variant="secondary"
                  >
                    {downloadingId === resource.id
                      ? "Preparing..."
                      : "Download"}
                  </Button>
                ))}
              <details className="relative">
                <summary className="cursor-pointer text-sm font-semibold text-brand-700 underline">
                  Edit
                </summary>
                <form
                  className="absolute right-0 z-10 mt-2 grid min-w-64 gap-3 rounded border border-surface-200 bg-white p-3 shadow-lg"
                  onSubmit={(event) => {
                    event.preventDefault();
                    const form = event.currentTarget;
                    const formData = new FormData(form);
                    const body = {
                      title: String(formData.get("title")).trim(),
                    };
                    if (resource.type === "LINK") {
                      body.url = String(formData.get("url")).trim();
                    }
                    action.mutate(() => updateResource(resource.id, body), {
                      onSuccess: () => {
                        form.closest("details").open = false;
                      },
                    });
                  }}
                >
                  <label className="text-sm font-medium">
                    Title
                    <input
                      className="mt-1 block w-full rounded border border-surface-200 p-2"
                      defaultValue={resource.title}
                      maxLength={255}
                      name="title"
                      required
                    />
                  </label>
                  {resource.type === "LINK" && (
                    <label className="text-sm font-medium">
                      URL
                      <input
                        className="mt-1 block w-full rounded border border-surface-200 p-2"
                        defaultValue={resource.externalUrl}
                        name="url"
                        required
                        type="url"
                      />
                    </label>
                  )}
                  <Button disabled={action.isPending} type="submit">
                    Save
                  </Button>
                </form>
              </details>
              <Button
                aria-label={`Move ${resource.title} up`}
                disabled={index === 0 || action.isPending}
                onClick={() => move(index, -1)}
                variant="secondary"
              >
                ↑
              </Button>
              <Button
                aria-label={`Move ${resource.title} down`}
                disabled={index === resources.length - 1 || action.isPending}
                onClick={() => move(index, 1)}
                variant="secondary"
              >
                ↓
              </Button>
              <Button
                aria-label={`Remove ${resource.title}`}
                disabled={action.isPending}
                onClick={() => action.mutate(() => deleteResource(resource.id))}
                variant="danger"
              >
                Remove
              </Button>
            </li>
          ))}
        </ol>
      )}
      <div className="mt-6 border-t border-surface-200 pt-5">
        <h3 className="font-semibold">Expected resources</h3>
        <p className="mt-1 text-sm text-ink-700">
          Tell learners what files this lesson should include.
        </p>
        {expectations.length > 0 && (
          <ul className="mt-3 space-y-2">
            {expectations.map((item) => (
              <li
                className="flex items-center justify-between gap-3 text-sm"
                key={item.resourceType}
              >
                <span>
                  {item.label || item.resourceType}: minimum {item.minCount}
                </span>
                <Button
                  aria-label={`Remove expected ${item.resourceType}`}
                  disabled={action.isPending}
                  onClick={() => removeExpected(item.resourceType)}
                  variant="secondary"
                >
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        )}
        <form
          className="mt-3 flex flex-wrap items-end gap-3"
          onSubmit={addExpected}
        >
          <label className="text-sm font-medium">
            Type
            <select
              className="mt-1 block rounded border border-surface-200 p-2"
              onChange={(event) =>
                setExpected({ ...expected, resourceType: event.target.value })
              }
              value={expected.resourceType}
            >
              {resourceTypes.map((type) => (
                <option key={type}>{type}</option>
              ))}
            </select>
          </label>
          <label className="text-sm font-medium">
            Minimum
            <input
              className="mt-1 block w-24 rounded border border-surface-200 p-2"
              max="20"
              min="1"
              onChange={(event) =>
                setExpected({
                  ...expected,
                  minCount: Number(event.target.value),
                })
              }
              required
              type="number"
              value={expected.minCount}
            />
          </label>
          <label className="text-sm font-medium">
            Label (optional)
            <input
              className="mt-1 block rounded border border-surface-200 p-2"
              maxLength={80}
              onChange={(event) =>
                setExpected({ ...expected, label: event.target.value })
              }
              value={expected.label}
            />
          </label>
          <Button disabled={action.isPending} type="submit">
            Add expectation
          </Button>
        </form>
      </div>
    </section>
  );
}
