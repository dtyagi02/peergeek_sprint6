import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useParams } from "react-router-dom";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { useToast } from "../../shared/components/useToast";
import {
  deleteThumbnail,
  getThumbnail,
  uploadCourseThumbnail,
} from "./resourcesApi";

export function CourseThumbnailManager() {
  const { courseId } = useParams();
  const queryClient = useQueryClient();
  const toast = useToast();
  const queryKey = ["course-thumbnail", courseId];
  const thumbnailQuery = useQuery({
    queryKey,
    queryFn: () => getThumbnail(courseId),
    retry: false,
    refetchInterval: (query) =>
      ["PENDING", "UPLOADED", "PROCESSING"].includes(query.state.data?.status)
        ? 2000
        : false,
  });

  const action = useMutation({
    mutationFn: async ({ file, remove }) => {
      if (remove) return deleteThumbnail(courseId);
      await uploadCourseThumbnail(courseId, file);
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey });
      toast.notify({
        title: "Thumbnail updated",
        description: "The course thumbnail has been saved.",
      });
    },
    onError: (error) =>
      toast.notify({
        title: "Thumbnail not updated",
        description: error.message,
      }),
  });

  async function upload(event) {
    const file = event.target.files?.[0];
    event.target.value = "";
    if (!file) return;
    action.mutate({ file });
  }

  if (thumbnailQuery.isPending)
    return <Spinner label="Loading course thumbnail" />;
  if (thumbnailQuery.isError && thumbnailQuery.error.status !== 404) {
    return (
      <p className="my-3 text-sm text-danger-700" role="alert">
        The course thumbnail could not be loaded.
      </p>
    );
  }

  const thumbnail = thumbnailQuery.data;

  return (
    <section
      aria-labelledby="course-thumbnail-title"
      className="my-4 rounded-lg border border-surface-200 bg-white p-4"
    >
      <h2 className="font-semibold" id="course-thumbnail-title">
        Course thumbnail
      </h2>
      {thumbnail?.url && (
        <img
          alt="Current course thumbnail"
          className="mt-3 max-h-48 rounded object-cover"
          src={thumbnail.url}
        />
      )}
      <p className="mt-2 text-sm text-ink-700">
        {thumbnail
          ? `Thumbnail status: ${thumbnail.status}`
          : "No thumbnail has been added."}
      </p>
      {thumbnail?.status === "FAILED" && (
        <p className="mt-1 text-sm text-danger-700" role="alert">
          The uploaded image could not be processed.
        </p>
      )}
      <div className="mt-3 flex flex-wrap gap-2">
        <label className="inline-flex min-h-10 cursor-pointer items-center justify-center rounded-md bg-brand-600 px-4 py-2 text-sm font-semibold text-white">
          {thumbnail ? "Replace thumbnail" : "Upload thumbnail"}
          <input
            accept="image/png,image/jpeg,image/webp,image/gif"
            className="sr-only"
            disabled={action.isPending}
            onChange={upload}
            type="file"
          />
        </label>
        {thumbnail && (
          <Button
            disabled={action.isPending}
            onClick={() => action.mutate({ remove: true })}
            variant="danger"
          >
            Remove thumbnail
          </Button>
        )}
      </div>
    </section>
  );
}
