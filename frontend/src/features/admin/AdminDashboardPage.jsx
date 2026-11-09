import { useState, useEffect } from "react";
import { useSearchParams } from "react-router-dom";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { Button } from "../../shared/components/Button";
import { EmptyState } from "../../shared/components/EmptyState";
import { ErrorState } from "../../shared/components/ErrorState";
import { Spinner } from "../../shared/components/Spinner";
import { useAuthStore } from "../auth/authStore";
import { AdminCommunityPanel } from "./AdminCommunityPanel";
import { AdminCertificatesPanel } from "./AdminCertificatesPanel";
import {
  getActivities,
  getAdminCourses,
  getAdminCourseById,
  updateAdminCourseStatus,
  getAdminReports,
  getAdminUsers,
  updateAdminUserStatus,
  getDashboardSummary,
  updateAdminReportStatus,
} from "./adminApi";

function formatDateTime(isoString) {
  if (!isoString) return "—";
  try {
    const date = new Date(isoString);
    return date.toLocaleString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
      hour: "2-digit",
      minute: "2-digit",
    });
  } catch {
    return isoString;
  }
}

function formatRelative(isoString) {
  if (!isoString) return "—";
  try {
    const diff = Date.now() - new Date(isoString).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return "Just now";
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    if (days < 30) return `${days}d ago`;
    return formatDateTime(isoString);
  } catch {
    return isoString;
  }
}

function StatusBadge({ status }) {
  const styles = {
    PENDING: "bg-amber-100 text-amber-800 border-amber-200",
    IN_REVIEW: "bg-blue-100 text-blue-800 border-blue-200",
    RESOLVED: "bg-emerald-100 text-emerald-800 border-emerald-200",
    DISMISSED: "bg-surface-200 text-ink-700 border-surface-300",
    PUBLISHED: "bg-emerald-100 text-emerald-800 border-emerald-200",
    DRAFT: "bg-amber-100 text-amber-800 border-amber-200",
    UNPUBLISHED: "bg-surface-200 text-ink-700 border-surface-300",
    ADMIN: "bg-purple-100 text-purple-800 border-purple-200",
    USER: "bg-slate-100 text-slate-700 border-slate-200",
  };
  const fallback = "bg-surface-100 text-ink-700 border-surface-200";

  return (
    <span
      className={`inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-semibold ${
        styles[status] ?? fallback
      }`}
    >
      {status ? status.replace(/_/g, " ") : "UNKNOWN"}
    </span>
  );
}

function ActivityIcon({ action }) {
  if (!action) return <span className="text-ink-400">⚡</span>;
  if (action.includes("USER") || action.includes("AUTH")) {
    return (
      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-purple-100 text-purple-700 text-sm font-bold">
        👤
      </span>
    );
  }
  if (action.includes("REPORT")) {
    return (
      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-amber-100 text-amber-700 text-sm font-bold">
        ⚠️
      </span>
    );
  }
  if (action.includes("REVIEW")) {
    return (
      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-yellow-100 text-yellow-700 text-sm font-bold">
        ⭐
      </span>
    );
  }
  if (action.includes("ENROLL")) {
    return (
      <span className="flex h-8 w-8 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 text-sm font-bold">
        🎓
      </span>
    );
  }
  return (
    <span className="flex h-8 w-8 items-center justify-center rounded-full bg-brand-100 text-brand-700 text-sm font-bold">
      📖
    </span>
  );
}

export function AdminDashboardPage() {
  const queryClient = useQueryClient();
  const currentUser = useAuthStore((state) => state.user);
  const [searchParams, setSearchParams] = useSearchParams();
  const tabFromUrl = searchParams.get("tab");
  const validTabs = ["activities", "reports", "users", "courses", "community"];
  const [activeTab, setActiveTabState] = useState(
    validTabs.includes(tabFromUrl) ? tabFromUrl : "activities"
  );

  useEffect(() => {
    const currentTabParam = searchParams.get("tab");
    if (currentTabParam && validTabs.includes(currentTabParam)) {
      setActiveTabState(currentTabParam);
    }
  }, [searchParams]);

  const setActiveTab = (tab) => {
    setActiveTabState(tab);
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.set("tab", tab);
        return next;
      },
      { replace: true }
    );
  };

  // Activities state
  const [activityPage, setActivityPage] = useState(0);

  // Reports state
  const [reportStatus, setReportStatus] = useState("ALL");
  const [reportQuery, setReportQuery] = useState("");
  const [reportPage, setReportPage] = useState(0);
  const [selectedReport, setSelectedReport] = useState(null);
  const [modalStatus, setModalStatus] = useState("");
  const [modalNotes, setModalNotes] = useState("");

  // Users state
  const [userRole, setUserRole] = useState("ALL");
  const [userMode, setUserMode] = useState("ALL");
  const [userEnabled, setUserEnabled] = useState("ALL");
  const [userQuery, setUserQuery] = useState("");
  const [userPage, setUserPage] = useState(0);

  // User detail and moderation modals
  const [selectedUserForDetails, setSelectedUserForDetails] = useState(null);
  const [selectedUserForBlock, setSelectedUserForBlock] = useState(null);
  const [userBlockReason, setUserBlockReason] = useState("");

  // Courses state
  const [courseStatus, setCourseStatus] = useState("ALL");
  const [courseQuery, setCourseQuery] = useState("");
  const [coursePage, setCoursePage] = useState(0);

  // Course details & moderation modal
  const [selectedCourseForModeration, setSelectedCourseForModeration] = useState(null);
  const [courseModerationStatus, setCourseModerationStatus] = useState("PUBLISHED");
  const [courseModerationReason, setCourseModerationReason] = useState("");
  const [moderationReportId, setModerationReportId] = useState(null);
  const [courseLoadingId, setCourseLoadingId] = useState(null);

  // Summary Query
  const {
    data: summary,
    isLoading: summaryLoading,
    error: summaryError,
    refetch: refetchSummary,
  } = useQuery({
    queryKey: ["admin", "summary"],
    queryFn: getDashboardSummary,
  });

  // Activities Query
  const {
    data: activitiesData,
    isLoading: activitiesLoading,
    error: activitiesError,
    refetch: refetchActivities,
  } = useQuery({
    queryKey: ["admin", "activities", activityPage],
    queryFn: () => getActivities({ page: activityPage, size: 20 }),
    enabled: activeTab === "activities",
  });

  // Reports Query
  const {
    data: reportsData,
    isLoading: reportsLoading,
    error: reportsError,
    refetch: refetchReports,
  } = useQuery({
    queryKey: ["admin", "reports", reportStatus, reportQuery, reportPage],
    queryFn: () =>
      getAdminReports({
        status: reportStatus,
        query: reportQuery,
        page: reportPage,
        size: 15,
      }),
    enabled: activeTab === "reports",
  });

  // Users Query
  const {
    data: usersData,
    isLoading: usersLoading,
    error: usersError,
    refetch: refetchUsers,
  } = useQuery({
    queryKey: ["admin", "users", userRole, userMode, userEnabled, userQuery, userPage],
    queryFn: () =>
      getAdminUsers({
        role: userRole,
        mode: userMode,
        enabled: userEnabled,
        query: userQuery,
        page: userPage,
        size: 15,
      }),
    enabled: activeTab === "users",
  });

  // Courses Query
  const {
    data: coursesData,
    isLoading: coursesLoading,
    error: coursesError,
    refetch: refetchCourses,
  } = useQuery({
    queryKey: ["admin", "courses", courseStatus, courseQuery, coursePage],
    queryFn: () =>
      getAdminCourses({
        status: courseStatus,
        query: courseQuery,
        page: coursePage,
        size: 15,
      }),
    enabled: activeTab === "courses",
  });

  // Mutation for updating report status
  const updateReportMutation = useMutation({
    mutationFn: ({ reportId, status, resolutionNotes }) =>
      updateAdminReportStatus(reportId, { status, resolutionNotes }),
    onSuccess: () => {
      setSelectedReport(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "reports"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "summary"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "activities"] });
    },
  });

  // Mutation for updating user status (block/unblock)
  const updateUserStatusMutation = useMutation({
    mutationFn: ({ userId, enabled, reason }) =>
      updateAdminUserStatus(userId, { enabled, reason }),
    onSuccess: (updatedUser) => {
      setSelectedUserForBlock(null);
      setUserBlockReason("");
      if (selectedUserForDetails && selectedUserForDetails.id === updatedUser.id) {
        setSelectedUserForDetails(updatedUser);
      }
      queryClient.invalidateQueries({ queryKey: ["admin", "users"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "summary"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "activities"] });
    },
  });

  // Mutation for updating course status (moderation)
  const updateCourseStatusMutation = useMutation({
    mutationFn: ({ courseId, status, reason, reportId }) =>
      updateAdminCourseStatus(courseId, { status, reason, reportId }),
    onSuccess: () => {
      setSelectedCourseForModeration(null);
      setCourseModerationReason("");
      setModerationReportId(null);
      queryClient.invalidateQueries({ queryKey: ["admin", "courses"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "summary"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "activities"] });
      queryClient.invalidateQueries({ queryKey: ["admin", "reports"] });
    },
  });

  function handleCardClick(cardType) {
    if (cardType === "TOTAL_USERS") {
      setActiveTab("users");
      setUserRole("ALL");
      setUserMode("ALL");
      setUserEnabled("ALL");
      setUserQuery("");
      setUserPage(0);
    } else if (cardType === "STUDENTS") {
      setActiveTab("users");
      setUserRole("ALL");
      setUserMode("STUDENT");
      setUserEnabled("ALL");
      setUserQuery("");
      setUserPage(0);
    } else if (cardType === "TEACHERS") {
      setActiveTab("users");
      setUserRole("ALL");
      setUserMode("INSTRUCTOR");
      setUserEnabled("ALL");
      setUserQuery("");
      setUserPage(0);
    } else if (cardType === "TOTAL_COURSES") {
      setActiveTab("courses");
      setCourseStatus("ALL");
      setCourseQuery("");
      setCoursePage(0);
    } else if (cardType === "PUBLISHED_COURSES") {
      setActiveTab("courses");
      setCourseStatus("PUBLISHED");
      setCourseQuery("");
      setCoursePage(0);
    } else if (cardType === "OPEN_ISSUES") {
      setActiveTab("reports");
      setReportStatus("OPEN");
      setReportQuery("");
      setReportPage(0);
    }
  }

  function handleOpenCourseModeration(course, reportId = null) {
    setSelectedCourseForModeration(course);
    setCourseModerationStatus(
      course.status === "PUBLISHED" ? "UNPUBLISHED" : "PUBLISHED"
    );
    setCourseModerationReason("");
    setModerationReportId(reportId);
  }

  async function handleInspectCourseFromReport(courseId, reportId) {
    try {
      setCourseLoadingId(courseId);
      const course = await getAdminCourseById(courseId);
      setSelectedReport(null);
      handleOpenCourseModeration(course, reportId);
    } catch (err) {
      console.error("Failed to load course details for moderation:", err);
    } finally {
      setCourseLoadingId(null);
    }
  }

  function handleOpenReport(report) {
    setSelectedReport(report);
    setModalStatus(report.status);
    setModalNotes(report.resolutionNotes || "");
  }

  function handleSaveReportStatus() {
    if (!selectedReport) return;
    updateReportMutation.mutate({
      reportId: selectedReport.id,
      status: modalStatus,
      resolutionNotes: modalNotes,
    });
  }

  function handleConfirmUserStatusToggle() {
    if (!selectedUserForBlock) return;
    updateUserStatusMutation.mutate({
      userId: selectedUserForBlock.id,
      enabled: !selectedUserForBlock.enabled,
      reason: userBlockReason.trim() || undefined,
    });
  }

  function handleConfirmCourseModeration() {
    if (!selectedCourseForModeration) return;
    updateCourseStatusMutation.mutate({
      courseId: selectedCourseForModeration.id,
      status: courseModerationStatus,
      reason: courseModerationReason.trim() || undefined,
      reportId: moderationReportId || undefined,
    });
  }

  function handleRefreshAll() {
    refetchSummary();
    if (activeTab === "activities") refetchActivities();
    if (activeTab === "reports") refetchReports();
    if (activeTab === "users") refetchUsers();
    if (activeTab === "courses") refetchCourses();
  }

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between border-b border-surface-200 pb-5">
        <div>
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-bold tracking-tight text-ink-900">
              Admin Monitoring Dashboard
            </h1>
            <span className="rounded-md bg-purple-100 px-2.5 py-0.5 text-xs font-semibold text-purple-800">
              Admin Only
            </span>
          </div>
          <p className="mt-1 text-sm text-ink-600">
            Real-time visibility into platform users, courses, reported issues,
            and system activities.
          </p>
        </div>
        <Button
          className="self-start sm:self-auto"
          onClick={handleRefreshAll}
          variant="secondary"
        >
          <span className="mr-1.5">↻</span> Refresh Data
        </Button>
      </div>

      {/* Summary Cards */}
      {summaryLoading ? (
        <div className="py-8">
          <Spinner label="Loading dashboard metrics" />
        </div>
      ) : summaryError ? (
        <ErrorState
          description="Failed to load administrative summary statistics."
          title="Could not load summary metrics"
        />
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          {/* Total Users */}
          <button
            aria-label="View all registered users"
            className="group rounded-xl border border-surface-200 bg-white p-5 shadow-sm text-left transition-all duration-150 hover:-translate-y-0.5 hover:shadow-md hover:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 cursor-pointer w-full"
            onClick={() => handleCardClick("TOTAL_USERS")}
            type="button"
          >
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-ink-500 group-hover:text-brand-700 transition-colors">
              <span>Total Users</span>
              <span className="opacity-0 group-hover:opacity-100 transition-opacity text-brand-600 font-bold">
                →
              </span>
            </div>
            <div className="mt-2 text-2xl font-bold text-ink-900">
              {summary?.totalUsers ?? 0}
            </div>
            <div className="mt-1 text-xs text-ink-500">
              {summary?.totalStudents ?? 0} students ·{" "}
              {summary?.totalInstructors ?? 0} teachers
            </div>
          </button>

          {/* Students */}
          <button
            aria-label="View students directory"
            className="group rounded-xl border border-surface-200 bg-white p-5 shadow-sm text-left transition-all duration-150 hover:-translate-y-0.5 hover:shadow-md hover:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 cursor-pointer w-full"
            onClick={() => handleCardClick("STUDENTS")}
            type="button"
          >
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-ink-500 group-hover:text-brand-700 transition-colors">
              <span>Students</span>
              <span className="opacity-0 group-hover:opacity-100 transition-opacity text-brand-600 font-bold">
                →
              </span>
            </div>
            <div className="mt-2 text-2xl font-bold text-brand-700">
              {summary?.totalStudents ?? 0}
            </div>
            <div className="mt-1 text-xs text-ink-500">Active learners</div>
          </button>

          {/* Teachers */}
          <button
            aria-label="View teachers and instructors directory"
            className="group rounded-xl border border-surface-200 bg-white p-5 shadow-sm text-left transition-all duration-150 hover:-translate-y-0.5 hover:shadow-md hover:border-purple-400 focus:outline-none focus:ring-2 focus:ring-purple-500 focus:ring-offset-2 cursor-pointer w-full"
            onClick={() => handleCardClick("TEACHERS")}
            type="button"
          >
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-ink-500 group-hover:text-purple-700 transition-colors">
              <span>Teachers</span>
              <span className="opacity-0 group-hover:opacity-100 transition-opacity text-purple-600 font-bold">
                →
              </span>
            </div>
            <div className="mt-2 text-2xl font-bold text-purple-700">
              {summary?.totalInstructors ?? 0}
            </div>
            <div className="mt-1 text-xs text-ink-500">Course authors</div>
          </button>

          {/* Total Courses */}
          <button
            aria-label="View all platform courses"
            className="group rounded-xl border border-surface-200 bg-white p-5 shadow-sm text-left transition-all duration-150 hover:-translate-y-0.5 hover:shadow-md hover:border-brand-400 focus:outline-none focus:ring-2 focus:ring-brand-500 focus:ring-offset-2 cursor-pointer w-full"
            onClick={() => handleCardClick("TOTAL_COURSES")}
            type="button"
          >
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-ink-500 group-hover:text-brand-700 transition-colors">
              <span>Total Courses</span>
              <span className="opacity-0 group-hover:opacity-100 transition-opacity text-brand-600 font-bold">
                →
              </span>
            </div>
            <div className="mt-2 text-2xl font-bold text-ink-900">
              {summary?.totalCourses ?? 0}
            </div>
            <div className="mt-1 text-xs text-ink-500">
              {summary?.publishedCourses ?? 0} published
            </div>
          </button>

          {/* Published Courses */}
          <button
            aria-label="View published courses"
            className="group rounded-xl border border-surface-200 bg-white p-5 shadow-sm text-left transition-all duration-150 hover:-translate-y-0.5 hover:shadow-md hover:border-emerald-400 focus:outline-none focus:ring-2 focus:ring-emerald-500 focus:ring-offset-2 cursor-pointer w-full"
            onClick={() => handleCardClick("PUBLISHED_COURSES")}
            type="button"
          >
            <div className="flex items-center justify-between text-xs font-medium uppercase tracking-wider text-ink-500 group-hover:text-emerald-700 transition-colors">
              <span>Published Courses</span>
              <span className="opacity-0 group-hover:opacity-100 transition-opacity text-emerald-600 font-bold">
                →
              </span>
            </div>
            <div className="mt-2 text-2xl font-bold text-emerald-700">
              {summary?.publishedCourses ?? 0}
            </div>
            <div className="mt-1 text-xs text-ink-500">Live on platform</div>
          </button>

          {/* Open Course Issues */}
          <button
            aria-label="View open course issue reports"
            className={`group rounded-xl border p-5 shadow-sm text-left transition-all duration-150 hover:-translate-y-0.5 hover:shadow-md focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2 cursor-pointer w-full ${
              (summary?.openReportsCount ?? 0) > 0
                ? "border-amber-300 bg-amber-50/50 hover:border-amber-400"
                : "border-surface-200 bg-white hover:border-brand-400"
            }`}
            onClick={() => handleCardClick("OPEN_ISSUES")}
            type="button"
          >
            <div className="flex items-center justify-between">
              <span className="text-xs font-medium uppercase tracking-wider text-ink-500 group-hover:text-amber-800 transition-colors">
                Open Issues
              </span>
              {(summary?.openReportsCount ?? 0) > 0 ? (
                <span className="rounded-full bg-amber-200 px-2 py-0.5 text-[10px] font-bold text-amber-900">
                  Needs Review
                </span>
              ) : (
                <span className="opacity-0 group-hover:opacity-100 transition-opacity text-amber-600 font-bold text-xs">
                  →
                </span>
              )}
            </div>
            <div
              className={`mt-2 text-2xl font-bold ${
                (summary?.openReportsCount ?? 0) > 0
                  ? "text-amber-800"
                  : "text-ink-900"
              }`}
            >
              {summary?.openReportsCount ?? 0}
            </div>
            <div className="mt-1 text-xs text-ink-500">Pending or in review</div>
          </button>
        </div>
      )}

      {/* Navigation Tabs */}
      <div className="border-b border-surface-200">
        <nav
          aria-label="Admin dashboard sections"
          className="-mb-px flex gap-6 overflow-x-auto"
        >
          <button
            className={`whitespace-nowrap border-b-2 pb-3 text-sm font-semibold transition-colors ${
              activeTab === "activities"
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-ink-600 hover:border-surface-300 hover:text-ink-900"
            }`}
            onClick={() => setActiveTab("activities")}
            type="button"
          >
            Recent Activities
            {summary?.totalActivitiesCount !== undefined && (
              <span className="ml-2 rounded-full bg-surface-100 px-2 py-0.5 text-xs text-ink-600">
                {summary.totalActivitiesCount}
              </span>
            )}
          </button>

          <button
            className={`whitespace-nowrap border-b-2 pb-3 text-sm font-semibold transition-colors ${
              activeTab === "reports"
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-ink-600 hover:border-surface-300 hover:text-ink-900"
            }`}
            onClick={() => setActiveTab("reports")}
            type="button"
          >
            Course Issues
            {(summary?.openReportsCount ?? 0) > 0 && (
              <span className="ml-2 rounded-full bg-amber-100 px-2 py-0.5 text-xs font-bold text-amber-800">
                {summary.openReportsCount}
              </span>
            )}
          </button>

          <button
            className={`whitespace-nowrap border-b-2 pb-3 text-sm font-semibold transition-colors ${
              activeTab === "users"
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-ink-600 hover:border-surface-300 hover:text-ink-900"
            }`}
            onClick={() => setActiveTab("users")}
            type="button"
          >
            User Monitoring
          </button>

          <button
            className={`whitespace-nowrap border-b-2 pb-3 text-sm font-semibold transition-colors ${
              activeTab === "courses"
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-ink-600 hover:border-surface-300 hover:text-ink-900"
            }`}
            onClick={() => setActiveTab("courses")}
            type="button"
          >
            Course Overview
          </button>

          <button
            className={`whitespace-nowrap border-b-2 pb-3 text-sm font-semibold transition-colors ${
              activeTab === "community"
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-ink-600 hover:border-surface-300 hover:text-ink-900"
            }`}
            onClick={() => setActiveTab("community")}
            type="button"
          >
            Community Moderation
          </button>

          <button
            className={`whitespace-nowrap border-b-2 pb-3 text-sm font-semibold transition-colors ${
              activeTab === "certificates"
                ? "border-brand-600 text-brand-700"
                : "border-transparent text-ink-600 hover:border-surface-300 hover:text-ink-900"
            }`}
            onClick={() => setActiveTab("certificates")}
            type="button"
          >
            Certificates
          </button>
        </nav>
      </div>

      {/* TAB 1: RECENT ACTIVITY FEED */}
      {activeTab === "activities" && (
        <section aria-labelledby="activities-heading" className="space-y-4">
          <div className="flex items-center justify-between">
            <h2
              className="text-lg font-semibold text-ink-900"
              id="activities-heading"
            >
              Application Activity Feed
            </h2>
            <span className="text-xs text-ink-500">
              Sorted newest first · Real-time event log
            </span>
          </div>

          {activitiesLoading ? (
            <div className="py-12">
              <Spinner label="Loading recent activity records" />
            </div>
          ) : activitiesError ? (
            <ErrorState
              description="Failed to load activity logs from server."
              title="Unable to load activities"
            />
          ) : activitiesData?.content?.length === 0 ? (
            <EmptyState
              description="No audit activity records have been logged yet."
              title="No activities recorded"
            />
          ) : (
            <div className="rounded-xl border border-surface-200 bg-white shadow-sm overflow-hidden">
              <ul className="divide-y divide-surface-200">
                {activitiesData.content.map((act) => (
                  <li
                    className="flex items-start gap-4 p-4 transition-colors hover:bg-surface-50"
                    key={act.id}
                  >
                    <div className="mt-0.5 shrink-0">
                      <ActivityIcon action={act.action} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="font-semibold text-ink-900 text-sm">
                            {act.title}
                          </span>
                          <span className="text-xs text-ink-500 font-medium">
                            by {act.actorName}
                          </span>
                          {act.actorRole && (
                            <StatusBadge status={act.actorRole} />
                          )}
                        </div>
                        <time
                          className="shrink-0 text-xs text-ink-500"
                          dateTime={act.createdAt}
                          title={formatDateTime(act.createdAt)}
                        >
                          {formatRelative(act.createdAt)}
                        </time>
                      </div>
                      <p className="mt-1 text-sm text-ink-700">
                        {act.description}
                      </p>
                      {act.details && Object.keys(act.details).length > 0 && (
                        <div className="mt-2 text-xs text-ink-500">
                          <span className="font-mono bg-surface-100 rounded px-1.5 py-0.5">
                            {act.entityType}: {act.entityId || "—"}
                          </span>
                        </div>
                      )}
                    </div>
                  </li>
                ))}
              </ul>

              {/* Activities Pagination */}
              <div className="flex items-center justify-between border-t border-surface-200 bg-surface-50 px-4 py-3">
                <span className="text-xs text-ink-600">
                  Page {activitiesData.page + 1} of{" "}
                  {Math.max(1, activitiesData.totalPages)} (
                  {activitiesData.totalElements} total events)
                </span>
                <div className="flex gap-2">
                  <Button
                    disabled={activityPage === 0}
                    onClick={() => setActivityPage((prev) => Math.max(0, prev - 1))}
                    variant="secondary"
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={
                      activityPage >= (activitiesData.totalPages || 1) - 1
                    }
                    onClick={() => setActivityPage((prev) => prev + 1)}
                    variant="secondary"
                  >
                    Next
                  </Button>
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {/* TAB 2: COURSE ISSUES */}
      {activeTab === "reports" && (
        <section aria-labelledby="reports-heading" className="space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <h2
              className="text-lg font-semibold text-ink-900"
              id="reports-heading"
            >
              Reported Course Issues
            </h2>

            {/* Filter controls */}
            <div className="flex flex-wrap items-center gap-3">
              <input
                aria-label="Search reports by course title"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm placeholder:text-ink-400 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setReportQuery(e.target.value);
                  setReportPage(0);
                }}
                placeholder="Search by course…"
                type="search"
                value={reportQuery}
              />

              <select
                aria-label="Filter reports by status"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setReportStatus(e.target.value);
                  setReportPage(0);
                }}
                value={reportStatus}
              >
                <option value="ALL">All Statuses</option>
                <option value="OPEN">Open Issues (Pending & In Review)</option>
                <option value="PENDING">Pending</option>
                <option value="IN_REVIEW">In Review</option>
                <option value="RESOLVED">Resolved</option>
                <option value="DISMISSED">Dismissed</option>
              </select>
            </div>
          </div>

          {reportsLoading ? (
            <div className="py-12">
              <Spinner label="Loading reported course issues" />
            </div>
          ) : reportsError ? (
            <ErrorState
              description="Failed to load course issue reports from server."
              title="Unable to load reports"
            />
          ) : reportsData?.content?.length === 0 ? (
            <EmptyState
              description="No course issue reports match your filter criteria."
              title="No reports found"
            />
          ) : (
            <div className="rounded-xl border border-surface-200 bg-white shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-surface-200 bg-surface-50 text-xs font-semibold uppercase text-ink-500">
                    <tr>
                      <th className="px-4 py-3">ID</th>
                      <th className="px-4 py-3">Course</th>
                      <th className="px-4 py-3">Reported By</th>
                      <th className="px-4 py-3">Category & Summary</th>
                      <th className="px-4 py-3">Date</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-200 text-ink-800">
                    {reportsData.content.map((report) => (
                      <tr
                        className="transition-colors hover:bg-surface-50"
                        key={report.id}
                      >
                        <td className="px-4 py-3 font-mono text-xs text-ink-500">
                          #{report.id}
                        </td>
                        <td className="px-4 py-3 font-medium text-ink-900 max-w-[180px] truncate">
                          {report.courseTitle}
                        </td>
                        <td className="px-4 py-3">
                          <div className="font-medium">{report.reporterName}</div>
                          <div className="text-xs text-ink-500 truncate max-w-[140px]">
                            {report.reporterEmail}
                          </div>
                        </td>
                        <td className="px-4 py-3 max-w-[240px]">
                          <div className="font-semibold text-xs text-ink-700">
                            {report.category
                              ? report.category.replace(/_/g, " ")
                              : "ISSUE"}
                          </div>
                          <p className="line-clamp-2 text-xs text-ink-600 mt-0.5">
                            {report.details}
                          </p>
                        </td>
                        <td
                          className="px-4 py-3 text-xs text-ink-500 whitespace-nowrap"
                          title={formatDateTime(report.createdAt)}
                        >
                          {formatRelative(report.createdAt)}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <StatusBadge status={report.status} />
                        </td>
                        <td className="px-4 py-3 text-right whitespace-nowrap">
                          <Button
                            className="!min-h-8 !px-3 !py-1 text-xs"
                            onClick={() => handleOpenReport(report)}
                            variant="secondary"
                          >
                            Review
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Reports Pagination */}
              <div className="flex items-center justify-between border-t border-surface-200 bg-surface-50 px-4 py-3">
                <span className="text-xs text-ink-600">
                  Page {reportsData.page + 1} of{" "}
                  {Math.max(1, reportsData.totalPages)} (
                  {reportsData.totalElements} total reports)
                </span>
                <div className="flex gap-2">
                  <Button
                    disabled={reportPage === 0}
                    onClick={() => setReportPage((prev) => Math.max(0, prev - 1))}
                    variant="secondary"
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={reportPage >= (reportsData.totalPages || 1) - 1}
                    onClick={() => setReportPage((prev) => prev + 1)}
                    variant="secondary"
                  >
                    Next
                  </Button>
                </div>
              </div>
            </div>
          )}

          {/* Report Detail & Moderation Modal */}
          <Dialog.Root
            onOpenChange={(open) => !open && setSelectedReport(null)}
            open={Boolean(selectedReport)}
          >
            <Dialog.Portal>
              <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40 backdrop-blur-xs" />
              <Dialog.Content
                aria-describedby="report-dialog-description"
                className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,36rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto"
              >
                <div className="flex items-start justify-between border-b border-surface-200 pb-3">
                  <div>
                    <Dialog.Title className="text-lg font-bold text-ink-900">
                      Report #{selectedReport?.id} Details
                    </Dialog.Title>
                    <div className="mt-1 flex items-center gap-2">
                      <span className="text-xs text-ink-500">
                        Course:{" "}
                        <strong className="text-ink-800">
                          {selectedReport?.courseTitle}
                        </strong>
                      </span>
                    </div>
                  </div>
                  <StatusBadge status={selectedReport?.status} />
                </div>

                <div
                  className="mt-4 space-y-4 text-sm"
                  id="report-dialog-description"
                >
                  <div className="grid grid-cols-2 gap-4 rounded-lg bg-surface-50 p-3 text-xs">
                    <div>
                      <span className="text-ink-500 block">Reported By:</span>
                      <span className="font-semibold text-ink-800">
                        {selectedReport?.reporterName} (
                        {selectedReport?.reporterEmail})
                      </span>
                    </div>
                    <div>
                      <span className="text-ink-500 block">Report Date:</span>
                      <span className="font-semibold text-ink-800">
                        {formatDateTime(selectedReport?.createdAt)}
                      </span>
                    </div>
                    <div className="col-span-2">
                      <span className="text-ink-500 block">Issue Category:</span>
                      <span className="font-semibold text-ink-800">
                        {selectedReport?.category?.replace(/_/g, " ")}
                      </span>
                    </div>
                  </div>

                  <div>
                    <span className="text-xs font-semibold text-ink-700 block mb-1">
                      Report Description
                    </span>
                    <div className="rounded-md border border-surface-200 bg-surface-50/50 p-3 text-ink-800 whitespace-pre-wrap">
                      {selectedReport?.details}
                    </div>
                  </div>

                  {selectedReport?.resolvedBy && (
                    <div className="rounded-lg border border-emerald-200 bg-emerald-50/50 p-3 text-xs">
                      <div className="font-semibold text-emerald-900">
                        Previously Moderated by {selectedReport.resolverName || "Admin"}
                      </div>
                      <div className="text-ink-600 mt-0.5">
                        Date: {formatDateTime(selectedReport.resolvedAt)}
                      </div>
                      {selectedReport.resolutionNotes && (
                        <div className="mt-1.5 text-ink-800 font-medium">
                          Notes: {selectedReport.resolutionNotes}
                        </div>
                      )}
                    </div>
                  )}

                  {/* Linked Course Card & Moderation Action */}
                  <div className="rounded-lg border border-surface-200 bg-surface-50 p-3.5 flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3">
                    <div>
                      <div className="text-xs font-semibold text-ink-900">
                        Associated Course: {selectedReport?.courseTitle}
                      </div>
                      <div className="text-xs text-ink-500 mt-0.5">
                        Course ID #{selectedReport?.courseId}
                      </div>
                    </div>
                    <Button
                      type="button"
                      variant="secondary"
                      className="!min-h-8 !px-3 !py-1 text-xs shrink-0 font-medium"
                      disabled={courseLoadingId === selectedReport?.courseId}
                      onClick={() =>
                        handleInspectCourseFromReport(
                          selectedReport.courseId,
                          selectedReport.id
                        )
                      }
                    >
                      {courseLoadingId === selectedReport?.courseId
                        ? "Loading Course…"
                        : "🛡️ Inspect & Moderate Course"}
                    </Button>
                  </div>

                  {/* Moderation Controls */}
                  <div className="border-t border-surface-200 pt-4 space-y-3">
                    <h3 className="font-semibold text-ink-900">
                      Update Report Status
                    </h3>

                    <div>
                      <label
                        className="block text-xs font-medium text-ink-700 mb-1"
                        htmlFor="report-status-select"
                      >
                        Status
                      </label>
                      <select
                        className="w-full rounded-md border border-surface-300 bg-white px-3 py-2 text-sm text-ink-800 focus:border-brand-500 focus:outline-none"
                        id="report-status-select"
                        onChange={(e) => setModalStatus(e.target.value)}
                        value={modalStatus}
                      >
                        <option value="PENDING">PENDING (Open)</option>
                        <option value="IN_REVIEW">IN REVIEW</option>
                        <option value="RESOLVED">RESOLVED</option>
                        <option value="DISMISSED">DISMISSED</option>
                      </select>
                    </div>

                    <div>
                      <label
                        className="block text-xs font-medium text-ink-700 mb-1"
                        htmlFor="report-notes-textarea"
                      >
                        Resolution Notes
                      </label>
                      <textarea
                        className="w-full rounded-md border border-surface-300 bg-white p-2.5 text-sm text-ink-800 placeholder:text-ink-400 focus:border-brand-500 focus:outline-none"
                        id="report-notes-textarea"
                        onChange={(e) => setModalNotes(e.target.value)}
                        placeholder="Add resolution or investigation notes…"
                        rows={3}
                        value={modalNotes}
                      />
                    </div>
                  </div>
                </div>

                <div className="mt-6 flex justify-end gap-3 border-t border-surface-200 pt-4">
                  <Dialog.Close asChild>
                    <Button variant="secondary">Cancel</Button>
                  </Dialog.Close>
                  <Button
                    disabled={updateReportMutation.isPending}
                    onClick={handleSaveReportStatus}
                  >
                    {updateReportMutation.isPending
                      ? "Saving…"
                      : "Save Status"}
                  </Button>
                </div>
              </Dialog.Content>
            </Dialog.Portal>
          </Dialog.Root>
        </section>
      )}

      {/* TAB 3: USER MONITORING */}
      {activeTab === "users" && (
        <section aria-labelledby="users-heading" className="space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <h2 className="text-lg font-semibold text-ink-900" id="users-heading">
              Registered Users Directory
            </h2>

            {/* Filter controls */}
            <div className="flex flex-wrap items-center gap-3">
              <input
                aria-label="Search users by name or email"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm placeholder:text-ink-400 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setUserQuery(e.target.value);
                  setUserPage(0);
                }}
                placeholder="Search name or email…"
                type="search"
                value={userQuery}
              />

              {/* Mode filter: distinguishes Students from Teachers */}
              <select
                aria-label="Filter users by mode"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setUserMode(e.target.value);
                  setUserPage(0);
                }}
                value={userMode}
              >
                <option value="ALL">All Modes (Learners & Authors)</option>
                <option value="STUDENT">Students (Active Learners)</option>
                <option value="INSTRUCTOR">Teachers (Course Authors)</option>
              </select>

              {/* Status filter: Active vs Disabled/Blocked */}
              <select
                aria-label="Filter users by account status"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setUserEnabled(e.target.value);
                  setUserPage(0);
                }}
                value={userEnabled}
              >
                <option value="ALL">All Account Statuses</option>
                <option value="true">Active Accounts Only</option>
                <option value="false">Blocked / Disabled Only</option>
              </select>

              {/* Role filter */}
              <select
                aria-label="Filter users by role"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setUserRole(e.target.value);
                  setUserPage(0);
                }}
                value={userRole}
              >
                <option value="ALL">All Roles</option>
                <option value="USER">User (Regular)</option>
                <option value="ADMIN">Admin</option>
              </select>
            </div>
          </div>

          {usersLoading ? (
            <div className="py-12">
              <Spinner label="Loading user directory" />
            </div>
          ) : usersError ? (
            <ErrorState
              description="Failed to load user list from server."
              title="Unable to load users"
            />
          ) : usersData?.content?.length === 0 ? (
            <EmptyState
              description="No registered users match your search criteria."
              title="No users found"
            />
          ) : (
            <div className="rounded-xl border border-surface-200 bg-white shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-surface-200 bg-surface-50 text-xs font-semibold uppercase text-ink-500">
                    <tr>
                      <th className="px-4 py-3">ID</th>
                      <th className="px-4 py-3">User</th>
                      <th className="px-4 py-3">Role</th>
                      <th className="px-4 py-3">Mode</th>
                      <th className="px-4 py-3">Email Verified</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Registered</th>
                      <th className="px-4 py-3 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-200 text-ink-800">
                    {usersData.content.map((u) => (
                      <tr
                        className="transition-colors hover:bg-surface-50"
                        key={u.id}
                      >
                        <td className="px-4 py-3 font-mono text-xs text-ink-500">
                          #{u.id}
                        </td>
                        <td className="px-4 py-3">
                          <div className="font-semibold text-ink-900">
                            {u.displayName}
                          </div>
                          <div className="text-xs text-ink-500">{u.email}</div>
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <StatusBadge status={u.role} />
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap text-xs font-medium text-ink-700">
                          {u.lastMode === "INSTRUCTOR"
                            ? "Teacher / Author"
                            : "Student / Learner"}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          {u.emailVerified ? (
                            <span className="inline-flex items-center gap-1 text-xs text-emerald-700 font-medium">
                              ✓ Verified
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 text-xs text-amber-700 font-medium">
                              ⏳ Pending
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          {u.enabled ? (
                            <span className="inline-flex items-center rounded-full bg-emerald-50 px-2 py-0.5 text-xs font-medium text-emerald-700">
                              Active
                            </span>
                          ) : (
                            <span className="inline-flex items-center rounded-full bg-red-50 px-2 py-0.5 text-xs font-medium text-red-700">
                              Blocked
                            </span>
                          )}
                        </td>
                        <td
                          className="px-4 py-3 text-xs text-ink-500 whitespace-nowrap"
                          title={formatDateTime(u.createdAt)}
                        >
                          {formatDateTime(u.createdAt)}
                        </td>
                        <td className="px-4 py-3 text-right whitespace-nowrap space-x-2">
                          <Button
                            className="!min-h-8 !px-2.5 !py-1 text-xs"
                            onClick={() => setSelectedUserForDetails(u)}
                            variant="secondary"
                          >
                            Details
                          </Button>
                          <Button
                            className="!min-h-8 !px-2.5 !py-1 text-xs"
                            disabled={u.id === currentUser?.id || u.role === "ADMIN"}
                            onClick={() => {
                              setSelectedUserForBlock(u);
                              setUserBlockReason("");
                            }}
                            title={
                              u.id === currentUser?.id
                                ? "You cannot block yourself"
                                : u.role === "ADMIN"
                                ? "Administrators cannot be blocked"
                                : u.enabled
                                ? "Block user account"
                                : "Unblock user account"
                            }
                            variant={u.enabled ? "danger" : "secondary"}
                          >
                            {u.enabled ? "Block" : "Unblock"}
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Users Pagination */}
              <div className="flex items-center justify-between border-t border-surface-200 bg-surface-50 px-4 py-3">
                <span className="text-xs text-ink-600">
                  Page {usersData.page + 1} of{" "}
                  {Math.max(1, usersData.totalPages)} ({usersData.totalElements}{" "}
                  total registered users)
                </span>
                <div className="flex gap-2">
                  <Button
                    disabled={userPage === 0}
                    onClick={() => setUserPage((prev) => Math.max(0, prev - 1))}
                    variant="secondary"
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={userPage >= (usersData.totalPages || 1) - 1}
                    onClick={() => setUserPage((prev) => prev + 1)}
                    variant="secondary"
                  >
                    Next
                  </Button>
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {/* TAB 4: COURSE OVERVIEW */}
      {activeTab === "courses" && (
        <section aria-labelledby="courses-heading" className="space-y-4">
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <h2
              className="text-lg font-semibold text-ink-900"
              id="courses-heading"
            >
              Platform Course Monitoring
            </h2>

            {/* Filter controls */}
            <div className="flex flex-wrap items-center gap-3">
              <input
                aria-label="Search courses by title"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm placeholder:text-ink-400 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setCourseQuery(e.target.value);
                  setCoursePage(0);
                }}
                placeholder="Search course title…"
                type="search"
                value={courseQuery}
              />

              <select
                aria-label="Filter courses by publication status"
                className="rounded-md border border-surface-300 bg-white px-3 py-1.5 text-sm text-ink-800 focus:border-brand-500 focus:outline-none"
                onChange={(e) => {
                  setCourseStatus(e.target.value);
                  setCoursePage(0);
                }}
                value={courseStatus}
              >
                <option value="ALL">All Publication States</option>
                <option value="PUBLISHED">Published</option>
                <option value="DRAFT">Draft</option>
                <option value="UNPUBLISHED">Unpublished</option>
              </select>
            </div>
          </div>

          {coursesLoading ? (
            <div className="py-12">
              <Spinner label="Loading course list" />
            </div>
          ) : coursesError ? (
            <ErrorState
              description="Failed to load courses from server."
              title="Unable to load courses"
            />
          ) : coursesData?.content?.length === 0 ? (
            <EmptyState
              description="No courses match your filter criteria."
              title="No courses found"
            />
          ) : (
            <div className="rounded-xl border border-surface-200 bg-white shadow-sm overflow-hidden">
              <div className="overflow-x-auto">
                <table className="w-full text-left text-sm">
                  <thead className="border-b border-surface-200 bg-surface-50 text-xs font-semibold uppercase text-ink-500">
                    <tr>
                      <th className="px-4 py-3">ID</th>
                      <th className="px-4 py-3">Course Title</th>
                      <th className="px-4 py-3">Instructor</th>
                      <th className="px-4 py-3">Category</th>
                      <th className="px-4 py-3">Status</th>
                      <th className="px-4 py-3">Open Issues</th>
                      <th className="px-4 py-3">Active Learners</th>
                      <th className="px-4 py-3">Created</th>
                      <th className="px-4 py-3 text-right">Action</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-surface-200 text-ink-800">
                    {coursesData.content.map((c) => (
                      <tr
                        className="transition-colors hover:bg-surface-50"
                        key={c.id}
                      >
                        <td className="px-4 py-3 font-mono text-xs text-ink-500">
                          #{c.id}
                        </td>
                        <td className="px-4 py-3">
                          <div className="font-semibold text-ink-900">
                            {c.title}
                          </div>
                          {c.difficulty && (
                            <span className="text-[11px] text-ink-500">
                              Level: {c.difficulty}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3">
                          <div className="font-medium text-ink-900">
                            {c.instructorName}
                          </div>
                          <div className="text-xs text-ink-500">
                            {c.instructorEmail}
                          </div>
                        </td>
                        <td className="px-4 py-3 text-xs text-ink-700 whitespace-nowrap">
                          {c.categoryName || "General"}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <StatusBadge status={c.status} />
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          {(c.openReportsCount ?? 0) > 0 ? (
                            <span className="inline-flex items-center rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800">
                              ⚠️ {c.openReportsCount} open
                            </span>
                          ) : (
                            <span className="text-xs text-ink-400">0</span>
                          )}
                        </td>
                        <td className="px-4 py-3 whitespace-nowrap">
                          <span className="inline-flex items-center rounded-full bg-brand-50 px-2.5 py-0.5 text-xs font-semibold text-brand-700">
                            {c.enrollmentCount} enrolled
                          </span>
                        </td>
                        <td
                          className="px-4 py-3 text-xs text-ink-500 whitespace-nowrap"
                          title={formatDateTime(c.createdAt)}
                        >
                          {formatDateTime(c.createdAt)}
                        </td>
                        <td className="px-4 py-3 text-right whitespace-nowrap">
                          <Button
                            className="!min-h-8 !px-3 !py-1 text-xs"
                            onClick={() => handleOpenCourseModeration(c)}
                            variant="secondary"
                          >
                            Manage & Moderate
                          </Button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              {/* Courses Pagination */}
              <div className="flex items-center justify-between border-t border-surface-200 bg-surface-50 px-4 py-3">
                <span className="text-xs text-ink-600">
                  Page {coursesData.page + 1} of{" "}
                  {Math.max(1, coursesData.totalPages)} (
                  {coursesData.totalElements} total courses)
                </span>
                <div className="flex gap-2">
                  <Button
                    disabled={coursePage === 0}
                    onClick={() => setCoursePage((prev) => Math.max(0, prev - 1))}
                    variant="secondary"
                  >
                    Previous
                  </Button>
                  <Button
                    disabled={coursePage >= (coursesData.totalPages || 1) - 1}
                    onClick={() => setCoursePage((prev) => prev + 1)}
                    variant="secondary"
                  >
                    Next
                  </Button>
                </div>
              </div>
            </div>
          )}
        </section>
      )}

      {/* TAB 5: GLOBAL COMMUNITY MODERATION */}
      {activeTab === "community" && <AdminCommunityPanel />}

      {/* TAB 6: CERTIFICATE MANAGEMENT */}
      {activeTab === "certificates" && <AdminCertificatesPanel />}

      {/* User Details Modal */}
      <Dialog.Root
        onOpenChange={(open) => !open && setSelectedUserForDetails(null)}
        open={Boolean(selectedUserForDetails)}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40 backdrop-blur-xs" />
          <Dialog.Content
            aria-describedby="user-details-description"
            className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,34rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto"
          >
            <div className="flex items-start justify-between border-b border-surface-200 pb-3">
              <div>
                <Dialog.Title className="text-lg font-bold text-ink-900">
                  User Account Details
                </Dialog.Title>
                <div className="text-xs text-ink-500 mt-0.5">
                  User ID #{selectedUserForDetails?.id}
                </div>
              </div>
              <StatusBadge status={selectedUserForDetails?.role} />
            </div>

            <div className="mt-4 space-y-4 text-sm" id="user-details-description">
              <div className="grid grid-cols-2 gap-4 rounded-lg bg-surface-50 p-4 text-xs">
                <div>
                  <span className="text-ink-500 block">Display Name</span>
                  <span className="font-semibold text-ink-900 text-sm">
                    {selectedUserForDetails?.displayName}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Email Address</span>
                  <span
                    className="font-semibold text-ink-900 text-sm truncate block"
                    title={selectedUserForDetails?.email}
                  >
                    {selectedUserForDetails?.email}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Account Role</span>
                  <span className="font-medium text-ink-800">
                    {selectedUserForDetails?.role}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Active Mode</span>
                  <span className="font-medium text-ink-800">
                    {selectedUserForDetails?.lastMode === "INSTRUCTOR"
                      ? "Teacher / Author"
                      : "Student / Learner"}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Email Verification</span>
                  <span
                    className={`font-semibold ${
                      selectedUserForDetails?.emailVerified
                        ? "text-emerald-700"
                        : "text-amber-700"
                    }`}
                  >
                    {selectedUserForDetails?.emailVerified
                      ? "✓ Verified"
                      : "⏳ Pending Verification"}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Account Status</span>
                  <span
                    className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-semibold ${
                      selectedUserForDetails?.enabled
                        ? "bg-emerald-100 text-emerald-800"
                        : "bg-red-100 text-red-800"
                    }`}
                  >
                    {selectedUserForDetails?.enabled
                      ? "Active / Enabled"
                      : "Blocked / Disabled"}
                  </span>
                </div>
                <div className="col-span-2">
                  <span className="text-ink-500 block">Registration Date</span>
                  <span
                    className="font-medium text-ink-800"
                    title={formatDateTime(selectedUserForDetails?.createdAt)}
                  >
                    {formatDateTime(selectedUserForDetails?.createdAt)}
                  </span>
                </div>
              </div>

              {selectedUserForDetails?.id === currentUser?.id ? (
                <div className="rounded-lg bg-blue-50 border border-blue-200 p-3 text-xs text-blue-800">
                  ℹ️ This is your administrator account. Self-blocking is prohibited.
                </div>
              ) : selectedUserForDetails?.role === "ADMIN" ? (
                <div className="rounded-lg bg-purple-50 border border-purple-200 p-3 text-xs text-purple-800">
                  🛡️ Administrator accounts cannot be blocked from this view.
                </div>
              ) : (
                <div className="rounded-lg border border-surface-200 p-3.5 flex items-center justify-between">
                  <div>
                    <div className="text-xs font-semibold text-ink-900">
                      Account Access Moderation
                    </div>
                    <div className="text-xs text-ink-500">
                      {selectedUserForDetails?.enabled
                        ? "Account is active. Blocking will immediately revoke all sessions."
                        : "Account is blocked. Unblocking will restore application login access."}
                    </div>
                  </div>
                  <Button
                    className="!min-h-8 !px-3 !py-1 text-xs shrink-0"
                    onClick={() => {
                      const userToBlock = selectedUserForDetails;
                      setSelectedUserForDetails(null);
                      setSelectedUserForBlock(userToBlock);
                      setUserBlockReason("");
                    }}
                    variant={selectedUserForDetails?.enabled ? "danger" : "secondary"}
                  >
                    {selectedUserForDetails?.enabled ? "Block Account" : "Unblock Account"}
                  </Button>
                </div>
              )}
            </div>

            <div className="mt-6 flex justify-end border-t border-surface-200 pt-4">
              <Dialog.Close asChild>
                <Button variant="secondary">Close</Button>
              </Dialog.Close>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* User Block/Unblock Confirmation Modal */}
      <Dialog.Root
        onOpenChange={(open) => !open && setSelectedUserForBlock(null)}
        open={Boolean(selectedUserForBlock)}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40 backdrop-blur-xs" />
          <Dialog.Content
            aria-describedby="user-block-dialog-description"
            className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,32rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl"
          >
            <div className="flex items-start justify-between border-b border-surface-200 pb-3">
              <Dialog.Title className="text-lg font-bold text-ink-900">
                {selectedUserForBlock?.enabled
                  ? "Confirm Account Block / Suspension"
                  : "Confirm Account Access Restoration"}
              </Dialog.Title>
            </div>

            <div className="mt-4 space-y-4 text-sm" id="user-block-dialog-description">
              <p className="text-ink-700">
                Are you sure you want to{" "}
                <strong>{selectedUserForBlock?.enabled ? "block" : "unblock"}</strong> the
                account of{" "}
                <strong className="text-ink-900">{selectedUserForBlock?.displayName}</strong> (
                <span className="font-mono text-xs">{selectedUserForBlock?.email}</span>)?
              </p>

              {selectedUserForBlock?.enabled ? (
                <div className="rounded-lg bg-red-50 border border-red-200 p-3 text-xs text-red-800 space-y-1">
                  <div className="font-semibold">⚠️ Immediate Access Revocation:</div>
                  <ul className="list-disc pl-4 space-y-0.5">
                    <li>All active sessions and refresh tokens will be immediately revoked.</li>
                    <li>The user will be immediately rejected on all protected platform requests.</li>
                    <li>This moderation action will be recorded in the audit log.</li>
                  </ul>
                </div>
              ) : (
                <div className="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800">
                  ✓ Unblocking will restore this user's ability to log in and access authorized platform features.
                </div>
              )}

              <div>
                <label
                  className="block text-xs font-semibold text-ink-700 mb-1"
                  htmlFor="user-block-reason"
                >
                  Moderation Reason or Internal Note
                </label>
                <textarea
                  className="w-full rounded-md border border-surface-300 bg-white p-2.5 text-sm text-ink-800 placeholder:text-ink-400 focus:border-brand-500 focus:outline-none"
                  id="user-block-reason"
                  maxLength={500}
                  onChange={(e) => setUserBlockReason(e.target.value)}
                  placeholder="Record justification (recorded in platform audit logs)…"
                  rows={3}
                  value={userBlockReason}
                />
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-3 border-t border-surface-200 pt-4">
              <Dialog.Close asChild>
                <Button disabled={updateUserStatusMutation.isPending} variant="secondary">
                  Cancel
                </Button>
              </Dialog.Close>
              <Button
                disabled={updateUserStatusMutation.isPending}
                onClick={handleConfirmUserStatusToggle}
                variant={selectedUserForBlock?.enabled ? "danger" : "primary"}
              >
                {updateUserStatusMutation.isPending
                  ? "Processing…"
                  : selectedUserForBlock?.enabled
                  ? "Block Account"
                  : "Unblock Account"}
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      {/* Course Details & Moderation Modal */}
      <Dialog.Root
        onOpenChange={(open) => !open && setSelectedCourseForModeration(null)}
        open={Boolean(selectedCourseForModeration)}
      >
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-40 bg-black/40 backdrop-blur-xs" />
          <Dialog.Content
            aria-describedby="course-moderation-description"
            className="fixed left-1/2 top-1/2 z-50 w-[min(92vw,36rem)] -translate-x-1/2 -translate-y-1/2 rounded-xl bg-white p-6 shadow-xl max-h-[90vh] overflow-y-auto"
          >
            <div className="flex items-start justify-between border-b border-surface-200 pb-3">
              <div>
                <Dialog.Title className="text-lg font-bold text-ink-900">
                  Course Moderation & Status
                </Dialog.Title>
                <div className="text-xs text-ink-500 mt-0.5">
                  Course ID #{selectedCourseForModeration?.id}
                </div>
              </div>
              <StatusBadge status={selectedCourseForModeration?.status} />
            </div>

            <div className="mt-4 space-y-4 text-sm" id="course-moderation-description">
              {moderationReportId && (
                <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-xs text-amber-900 flex flex-col gap-0.5">
                  <span className="font-semibold">
                    📋 Moderating in response to Report #{moderationReportId}.
                  </span>
                  <span className="text-[11px] text-amber-700">
                    The report status will remain unchanged until you explicitly resolve it in the Course Issues tab.
                  </span>
                </div>
              )}

              <div className="grid grid-cols-2 gap-3 rounded-lg bg-surface-50 p-3.5 text-xs">
                <div className="col-span-2">
                  <span className="text-ink-500 block">Course Title</span>
                  <span className="font-bold text-ink-900 text-sm">
                    {selectedCourseForModeration?.title}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Instructor</span>
                  <span className="font-semibold text-ink-900">
                    {selectedCourseForModeration?.instructorName}
                  </span>
                  <span className="text-ink-500 block text-[11px] truncate">
                    {selectedCourseForModeration?.instructorEmail}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Category</span>
                  <span className="font-medium text-ink-800">
                    {selectedCourseForModeration?.categoryName || "General"}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Active Learners</span>
                  <span className="font-semibold text-brand-700">
                    {selectedCourseForModeration?.enrollmentCount ?? 0} enrolled
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Associated Open Issues</span>
                  <span
                    className={`font-semibold ${
                      (selectedCourseForModeration?.openReportsCount ?? 0) > 0
                        ? "text-amber-800"
                        : "text-emerald-700"
                    }`}
                  >
                    {selectedCourseForModeration?.openReportsCount ?? 0} reports
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Created On</span>
                  <span className="text-ink-700">
                    {formatDateTime(selectedCourseForModeration?.createdAt)}
                  </span>
                </div>
                <div>
                  <span className="text-ink-500 block">Published On</span>
                  <span className="text-ink-700">
                    {formatDateTime(selectedCourseForModeration?.publishedAt)}
                  </span>
                </div>
              </div>

              {/* Status transition control */}
              <div className="border-t border-surface-200 pt-4 space-y-3">
                <h3 className="font-semibold text-ink-900">
                  Update Publication / Moderation Status
                </h3>

                <div>
                  <label
                    className="block text-xs font-medium text-ink-700 mb-1"
                    htmlFor="course-status-select"
                  >
                    Target Lifecycle Status
                  </label>
                  <select
                    className="w-full rounded-md border border-surface-300 bg-white px-3 py-2 text-sm text-ink-800 focus:border-brand-500 focus:outline-none"
                    id="course-status-select"
                    onChange={(e) => setCourseModerationStatus(e.target.value)}
                    value={courseModerationStatus}
                  >
                    <option value="PUBLISHED">PUBLISHED (Discoverable & Accessible)</option>
                    <option value="UNPUBLISHED">
                      UNPUBLISHED (Suspended / Hidden from Catalog)
                    </option>
                    <option value="DRAFT">DRAFT (Under Construction)</option>
                  </select>
                </div>

                {courseModerationStatus === "UNPUBLISHED" && (
                  <div className="rounded-lg bg-amber-50 border border-amber-200 p-3 text-xs text-amber-800">
                    ⚠️ <strong>Unpublishing Course:</strong> The course will be removed from
                    public discovery and catalog. Existing learner enrollments and progress
                    remain safely preserved in the database.
                  </div>
                )}

                {courseModerationStatus === "PUBLISHED" && (
                  <div className="rounded-lg bg-emerald-50 border border-emerald-200 p-3 text-xs text-emerald-800">
                    ✓ <strong>Publishing / Restoring Course:</strong> The course will be
                    synchronized back to public discovery and made accessible to students.
                  </div>
                )}

                <div>
                  <label
                    className="block text-xs font-medium text-ink-700 mb-1"
                    htmlFor="course-mod-reason"
                  >
                    Moderation Reason / Internal Audit Note
                  </label>
                  <textarea
                    className="w-full rounded-md border border-surface-300 bg-white p-2.5 text-sm text-ink-800 placeholder:text-ink-400 focus:border-brand-500 focus:outline-none"
                    id="course-mod-reason"
                    maxLength={500}
                    onChange={(e) => setCourseModerationReason(e.target.value)}
                    placeholder="Document reason for unpublishing or restoring (recorded in audit logs)…"
                    rows={3}
                    value={courseModerationReason}
                  />
                </div>
              </div>
            </div>

            <div className="mt-6 flex justify-end gap-3 border-t border-surface-200 pt-4">
              <Dialog.Close asChild>
                <Button disabled={updateCourseStatusMutation.isPending} variant="secondary">
                  Cancel
                </Button>
              </Dialog.Close>
              <Button
                disabled={
                  updateCourseStatusMutation.isPending ||
                  courseModerationStatus === selectedCourseForModeration?.status
                }
                onClick={handleConfirmCourseModeration}
              >
                {updateCourseStatusMutation.isPending
                  ? "Applying…"
                  : "Apply Status Change"}
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}
