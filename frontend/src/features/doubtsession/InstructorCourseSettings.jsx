import { useState, useEffect } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { ErrorState } from "../../shared/components/ErrorState";
import {
  getInstructorCourseSettings,
  updateInstructorCourseSettings,
} from "./doubtSessionApi";

export function InstructorCourseSettings({ courseId }) {
  const queryClient = useQueryClient();
  const [enabled, setEnabled] = useState(false);
  const [available, setAvailable] = useState(false);
  const [hourlyRate, setHourlyRate] = useState(600);
  const [saveSuccess, setSaveSuccess] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const {
    data: settings,
    isLoading,
    isError,
    error,
    refetch,
  } = useQuery({
    queryKey: ["instructor-doubt-settings", courseId],
    queryFn: () => getInstructorCourseSettings(courseId),
    enabled: !!courseId,
  });

  useEffect(() => {
    if (settings) {
      setEnabled(settings.enabled || false);
      setAvailable(settings.available || false);
      setHourlyRate(settings.hourlyRatePgc || 600);
    }
  }, [settings]);

  const updateMutation = useMutation({
    mutationFn: (data) => updateInstructorCourseSettings(courseId, data),
    onSuccess: (updated) => {
      setSaveSuccess(true);
      setErrorMessage(null);
      queryClient.setQueryData(["instructor-doubt-settings", courseId], updated);
      queryClient.invalidateQueries({ queryKey: ["course-doubt-availability", courseId] });
      setTimeout(() => setSaveSuccess(false), 3000);
    },
    onError: (err) => {
      setErrorMessage(err?.message || "Failed to update doubt clearing settings.");
      setSaveSuccess(false);
    },
  });

  const parsedRate = Number(hourlyRate);
  const isRateValid =
    String(hourlyRate).trim() !== "" &&
    Number.isInteger(parsedRate) &&
    parsedRate >= 1;

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!settings || !isRateValid || updateMutation.isPending) {
      return;
    }
    updateMutation.mutate({
      enabled,
      available,
      hourlyRatePgc: parsedRate,
    });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center p-8">
        <Spinner size="md" label="Loading doubt clearing settings..." />
      </div>
    );
  }

  if (isError) {
    return (
      <div
        data-testid="instructor-course-doubt-settings"
        className="rounded-2xl border border-surface-200 bg-white p-6 shadow-xs"
      >
        <div className="flex items-center justify-between border-b border-surface-200 pb-4">
          <div>
            <h3 className="text-base font-bold text-ink-900">
              Live Doubt-Clearing Settings
            </h3>
            <p className="mt-0.5 text-xs text-ink-500">
              Offer paid 1-to-1 live doubt-clearing sessions via LiveKit Cloud for this course.
            </p>
          </div>
          <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-xl text-indigo-600">
            🪙
          </span>
        </div>

        <div className="mt-6 space-y-4">
          <ErrorState
            title="Unable to load live doubt-clearing settings"
            description={
              error?.message ||
              "Could not load doubt-clearing settings for this course. Please ensure the course is published and the backend service is reachable."
            }
          />
          <div className="flex justify-end pt-2">
            <Button
              type="button"
              variant="secondary"
              onClick={() => refetch()}
            >
              Retry
            </Button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div
      data-testid="instructor-course-doubt-settings"
      className="rounded-2xl border border-surface-200 bg-white p-6 shadow-xs"
    >
      <div className="flex items-center justify-between border-b border-surface-200 pb-4">
        <div>
          <h3 className="text-base font-bold text-ink-900">
            Live Doubt-Clearing Settings
          </h3>
          <p className="mt-0.5 text-xs text-ink-500">
            Offer paid 1-to-1 live doubt-clearing sessions via LiveKit Cloud for this course.
          </p>
        </div>
        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-50 text-xl text-indigo-600">
          🪙
        </span>
      </div>

      <form onSubmit={handleSubmit} className="mt-5 space-y-5">
        {/* Enable / Disable Live Doubt Clearing */}
        <div className="flex items-center justify-between">
          <div>
            <label htmlFor="doubt-session-enable" className="text-sm font-semibold text-ink-900">
              Enable Live Doubt Sessions
            </label>
            <p className="text-xs text-ink-500">
              Allows enrolled learners to see the doubt clearing option and request sessions.
            </p>
          </div>
          <input
            id="doubt-session-enable"
            type="checkbox"
            checked={enabled}
            onChange={(e) => setEnabled(e.target.checked)}
            className="h-5 w-5 rounded-md border-surface-300 text-brand-600 focus:ring-brand-500"
          />
        </div>

        {/* Hourly Rate in GeekCoins */}
        <div className="space-y-1.5 border-t border-surface-100 pt-4">
          <label htmlFor="hourly-rate-input" className="text-sm font-semibold text-ink-900">
            Hourly Rate in GeekCoins (PGC/h)
          </label>
          <div className="relative max-w-xs">
            <span className="absolute inset-y-0 left-0 flex items-center pl-3 text-sm text-ink-400">
              🪙
            </span>
            <input
              id="hourly-rate-input"
              type="number"
              min="1"
              step="1"
              value={hourlyRate}
              disabled={updateMutation.isPending}
              onChange={(e) => setHourlyRate(e.target.value)}
              className="block w-full rounded-lg border border-surface-300 pl-9 pr-12 py-2 text-sm text-ink-900 focus:border-brand-500 focus:ring-1 focus:ring-brand-500 disabled:bg-surface-100 disabled:text-ink-400"
              placeholder="600"
            />
            <span className="absolute inset-y-0 right-0 flex items-center pr-3 text-xs font-semibold text-ink-500">
              PGC/h
            </span>
          </div>
          {!isRateValid && (
            <p className="text-xs font-medium text-red-600" role="alert">
              Hourly rate must be a positive whole number (at least 1 PGC/h).
            </p>
          )}
          <p className="text-xs text-ink-500">
            Default: 600 PGC/h. Students are billed proportionally for actual connected call minutes.
          </p>
        </div>

        {/* Live Availability Toggle */}
        <div className="flex items-center justify-between border-t border-surface-100 pt-4">
          <div>
            <label htmlFor="availability-toggle" className="text-sm font-semibold text-ink-900">
              I am Currently Available for Live Doubts
            </label>
            <p className="text-xs text-ink-500">
              Turn this ON when you are ready to receive live call requests.
            </p>
          </div>
          <input
            id="availability-toggle"
            type="checkbox"
            checked={available}
            disabled={!enabled || updateMutation.isPending}
            onChange={(e) => setAvailable(e.target.checked)}
            className="h-5 w-5 rounded-md border-surface-300 text-brand-600 focus:ring-brand-500 disabled:opacity-50"
          />
        </div>

        {/* Status Messages */}
        {saveSuccess && (
          <div className="rounded-lg bg-emerald-50 p-3 text-xs font-medium text-emerald-800 border border-emerald-200">
            ✓ Doubt clearing settings saved successfully!
          </div>
        )}

        {errorMessage && (
          <div className="rounded-lg bg-red-50 p-3 text-xs font-medium text-red-800 border border-red-200">
            {errorMessage}
          </div>
        )}

        <div className="flex justify-end pt-2">
          <Button
            type="submit"
            variant="primary"
            disabled={!settings || !isRateValid || updateMutation.isPending}
          >
            {updateMutation.isPending ? "Saving..." : "Save Settings"}
          </Button>
        </div>
      </form>
    </div>
  );
}
