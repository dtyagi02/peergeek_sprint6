import { useState } from "react";
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query";
import * as Dialog from "@radix-ui/react-dialog";
import { Button } from "../../shared/components/Button";
import { Spinner } from "../../shared/components/Spinner";
import { EmptyState } from "../../shared/components/EmptyState";
import {
  getAdminCertificates,
  revokeAdminCertificate,
  retryAdminCertificate,
  reconcileAdminCertificates,
} from "../certificate/certificateApi";

export function AdminCertificatesPanel() {
  const queryClient = useQueryClient();
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [searchTerm, setSearchTerm] = useState("");
  const [debouncedSearch, setDebouncedSearch] = useState("");
  const [page, setPage] = useState(0);

  // Revoke Modal State
  const [revokeTarget, setRevokeTarget] = useState(null);
  const [revocationReason, setRevocationReason] = useState("");
  const [revokeError, setRevokeError] = useState("");

  // Reconcile Banner
  const [reconcileResult, setReconcileResult] = useState(null);

  const { data, isLoading, isError, error, refetch } = useQuery({
    queryKey: ["admin-certificates", { status: statusFilter, search: debouncedSearch, page }],
    queryFn: () => getAdminCertificates({ status: statusFilter, search: debouncedSearch, page, size: 20 }),
  });

  const revokeMutation = useMutation({
    mutationFn: ({ id, reason }) => revokeAdminCertificate(id, reason),
    onSuccess: () => {
      setRevokeTarget(null);
      setRevocationReason("");
      setRevokeError("");
      queryClient.invalidateQueries({ queryKey: ["admin-certificates"] });
    },
    onError: (err) => {
      setRevokeError(err.message || "Failed to revoke certificate.");
    },
  });

  const retryMutation = useMutation({
    mutationFn: (id) => retryAdminCertificate(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["admin-certificates"] });
    },
  });

  const reconcileMutation = useMutation({
    mutationFn: () => reconcileAdminCertificates(),
    onSuccess: (res) => {
      setReconcileResult(res);
      queryClient.invalidateQueries({ queryKey: ["admin-certificates"] });
    },
  });

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    setDebouncedSearch(searchTerm);
    setPage(0);
  };

  const handleOpenRevoke = (cert) => {
    setRevokeTarget(cert);
    setRevocationReason("");
    setRevokeError("");
  };

  const handleConfirmRevoke = () => {
    if (!revocationReason.trim()) {
      setRevokeError("Please enter a revocation reason.");
      return;
    }
    revokeMutation.mutate({ id: revokeTarget.id, reason: revocationReason.trim() });
  };

  const certificates = data?.content || [];
  const totalPages = data?.totalPages || 0;

  return (
    <div className="space-y-6">
      {/* Header and Controls */}
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-xl font-bold text-ink-900">Certificate Management</h2>
          <p className="mt-1 text-sm text-ink-500">
            Monitor, audit, verify, and revoke course completion certificates.
          </p>
        </div>
        <Button
          type="button"
          onClick={() => reconcileMutation.mutate()}
          disabled={reconcileMutation.isPending}
          className="self-start sm:self-auto"
        >
          {reconcileMutation.isPending ? "Reconciling..." : "Run Batch Reconciliation"}
        </Button>
      </div>

      {reconcileResult && (
        <div className="flex items-center justify-between rounded-lg bg-emerald-50 p-4 text-sm text-emerald-800">
          <div>
            <p className="font-semibold">Reconciliation Completed:</p>
            <p className="mt-0.5 text-xs">
              Examined {reconcileResult.totalExamined} active enrollments · {reconcileResult.newlyIssued} new certificates issued · {reconcileResult.alreadyIssued} already valid · {reconcileResult.ineligible} not yet eligible.
            </p>
          </div>
          <button type="button" onClick={() => setReconcileResult(null)} className="ml-4 font-bold text-emerald-700">✕</button>
        </div>
      )}

      {/* Filters and Search Bar */}
      <div className="flex flex-col gap-3 rounded-xl border border-neutral-200 bg-white p-4 shadow-sm sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-wrap items-center gap-2">
          {["ALL", "ISSUED", "PENDING", "FAILED", "REVOKED"].map((st) => (
            <button
              key={st}
              type="button"
              onClick={() => {
                setStatusFilter(st);
                setPage(0);
              }}
              className={`rounded-lg px-3 py-1.5 text-xs font-semibold transition-colors ${
                statusFilter === st
                  ? "bg-[#a31c1b] text-white"
                  : "bg-surface-100 text-ink-600 hover:bg-surface-200"
              }`}
            >
              {st}
            </button>
          ))}
        </div>

        <form onSubmit={handleSearchSubmit} className="flex gap-2">
          <input
            type="text"
            placeholder="Search student, course, ID..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="rounded-lg border border-neutral-300 px-3 py-1.5 text-xs text-ink-800 focus:border-[#a31c1b] focus:outline-none"
          />
          <Button type="submit" size="sm" variant="secondary">
            Search
          </Button>
        </form>
      </div>

      {/* Table view */}
      {isLoading ? (
        <Spinner label="Loading certificates..." />
      ) : isError ? (
        <div className="rounded-xl border border-red-200 bg-red-50 p-6 text-center text-sm text-red-700">
          {error?.message || "Failed to load certificates."}
          <div className="mt-3">
            <Button size="sm" variant="secondary" onClick={() => refetch()}>
              Try again
            </Button>
          </div>
        </div>
      ) : certificates.length === 0 ? (
        <EmptyState
          title="No certificates found"
          description="No certificates match the selected filter or search criteria."
        />
      ) : (
        <div className="overflow-x-auto rounded-xl border border-neutral-200 bg-white shadow-sm">
          <table className="min-w-full divide-y divide-neutral-200 text-left text-sm">
            <thead className="bg-neutral-50 text-xs font-semibold text-ink-500">
              <tr>
                <th className="px-4 py-3">Certificate ID</th>
                <th className="px-4 py-3">Student</th>
                <th className="px-4 py-3">Course</th>
                <th className="px-4 py-3">Status</th>
                <th className="px-4 py-3">Issued Date</th>
                <th className="px-4 py-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-neutral-200 text-ink-700">
              {certificates.map((cert) => (
                <tr key={cert.id} className="hover:bg-neutral-50/50">
                  <td className="px-4 py-3 font-mono text-xs font-medium text-ink-900">
                    {cert.certificateNumber}
                  </td>
                  <td className="px-4 py-3 font-medium text-ink-900">{cert.studentName}</td>
                  <td className="px-4 py-3 max-w-xs truncate" title={cert.courseTitle}>
                    {cert.courseTitle}
                  </td>
                  <td className="px-4 py-3">
                    {cert.status === "ISSUED" && (
                      <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800">
                        ISSUED
                      </span>
                    )}
                    {cert.status === "PENDING" && (
                      <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-800">
                        PENDING
                      </span>
                    )}
                    {cert.status === "FAILED" && (
                      <span className="rounded-full bg-amber-100 px-2 py-0.5 text-xs font-semibold text-amber-800" title={cert.failureReason || ""}>
                        FAILED
                      </span>
                    )}
                    {cert.status === "REVOKED" && (
                      <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800" title={cert.revocationReason || ""}>
                        REVOKED
                      </span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-ink-500">
                    {cert.issuedAt ? new Date(cert.issuedAt).toLocaleDateString() : "—"}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex items-center justify-end gap-2">
                      {cert.status === "FAILED" && (
                        <button
                          type="button"
                          onClick={() => retryMutation.mutate(cert.id)}
                          disabled={retryMutation.isPending}
                          className="text-xs font-semibold text-amber-700 hover:underline"
                        >
                          Retry
                        </button>
                      )}
                      {cert.status === "ISSUED" && (
                        <button
                          type="button"
                          onClick={() => handleOpenRevoke(cert)}
                          className="text-xs font-semibold text-red-600 hover:underline"
                        >
                          Revoke
                        </button>
                      )}
                      {cert.status === "REVOKED" && (
                        <span className="text-xs italic text-ink-400">Revoked</span>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-neutral-200 px-4 py-3">
              <span className="text-xs text-ink-500">
                Page {page + 1} of {totalPages}
              </span>
              <div className="flex gap-2">
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={page === 0}
                  onClick={() => setPage((p) => Math.max(0, p - 1))}
                >
                  Previous
                </Button>
                <Button
                  size="sm"
                  variant="secondary"
                  disabled={page >= totalPages - 1}
                  onClick={() => setPage((p) => p + 1)}
                >
                  Next
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Revocation Modal */}
      <Dialog.Root open={!!revokeTarget} onOpenChange={(open) => !open && setRevokeTarget(null)}>
        <Dialog.Portal>
          <Dialog.Overlay className="fixed inset-0 z-50 bg-black/40 backdrop-blur-sm" />
          <Dialog.Content className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-white p-6 shadow-xl focus:outline-none">
            <Dialog.Title className="text-lg font-bold text-ink-900">
              Revoke Certificate
            </Dialog.Title>
            <Dialog.Description className="mt-2 text-sm text-ink-500">
              Are you sure you want to revoke certificate{" "}
              <span className="font-mono font-semibold text-ink-800">
                {revokeTarget?.certificateNumber}
              </span>{" "}
              issued to <span className="font-semibold text-ink-800">{revokeTarget?.studentName}</span>?
              This action is permanent and cannot be undone.
            </Dialog.Description>

            {revokeError && (
              <p className="mt-3 rounded-lg bg-red-50 p-2 text-xs text-red-700">{revokeError}</p>
            )}

            <div className="mt-4">
              <label htmlFor="revoke-reason-input" className="block text-xs font-semibold text-ink-700">
                Revocation Reason <span className="text-red-500">*</span>
              </label>
              <textarea
                id="revoke-reason-input"
                rows={3}
                value={revocationReason}
                onChange={(e) => setRevocationReason(e.target.value)}
                placeholder="e.g. Course requirements not legitimately completed / plagiarism."
                className="mt-1 w-full rounded-lg border border-neutral-300 p-2 text-sm text-ink-800 focus:border-red-500 focus:outline-none"
              />
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <Button
                variant="secondary"
                type="button"
                onClick={() => setRevokeTarget(null)}
                disabled={revokeMutation.isPending}
              >
                Cancel
              </Button>
              <Button
                type="button"
                className="bg-red-600 hover:bg-red-700 text-white"
                onClick={handleConfirmRevoke}
                disabled={revokeMutation.isPending}
              >
                {revokeMutation.isPending ? "Revoking..." : "Confirm Revocation"}
              </Button>
            </div>
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>
    </div>
  );
}

