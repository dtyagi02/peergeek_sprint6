import { useEffect, useState } from "react";
import { getCertificateDownloadUrl, getMyCertificates, reconcileCertificates, retryCertificate } from "./certificateApi";
import { Spinner } from "../../shared/components/Spinner";

export function CertificatePage() {
  const [certificates, setCertificates] = useState([]);
  const [loading, setLoading] = useState(true);
  const [reconciling, setReconciling] = useState(false);
  const [error, setError] = useState("");
  const [bannerMessage, setBannerMessage] = useState("");

  const loadData = async (triggerReconcile = false) => {
    try {
      setError("");
      if (triggerReconcile) {
        setReconciling(true);
        try {
          const summary = await reconcileCertificates();
          if (summary && summary.newlyIssued > 0) {
            setBannerMessage(`Issued ${summary.newlyIssued} new certificate(s) for your completed courses!`);
          } else if (summary && summary.failed > 0) {
            setError(`Certificate generation failed for ${summary.failed} eligible course(s). Please try again.`);
          }
        } catch (reconcileErr) {
          setError(reconcileErr.message || "Failed to check for new certificates.");
        }
      }
      const data = await getMyCertificates();
      setCertificates(data || []);
    } catch (err) {
      setError(err.message || "Unable to load certificates.");
    } finally {
      setLoading(false);
      setReconciling(false);
    }
  };

  useEffect(() => {
    loadData(true);
  }, []);

  async function handleDownload(certificate) {
    try {
      const url = await getCertificateDownloadUrl(certificate.id);
      window.open(url, "_blank", "noopener,noreferrer");
    } catch (err) {
      setError(err.message || "Unable to download certificate.");
    }
  }

  async function handleRetry(certificateId) {
    try {
      await retryCertificate(certificateId);
      await loadData(false);
    } catch (err) {
      setError(err.message || "Unable to retry certificate generation.");
    }
  }

  if (loading) return <Spinner label="Loading certificates..." />;

  return (
    <main className="mx-auto w-full max-w-4xl px-4 py-8 sm:px-6">
      <header className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-ink-900">My Certificates</h1>
          <p className="mt-1 text-sm text-ink-500">Certificates of completion earned by finishing PeerGeek courses.</p>
        </div>
        <button
          type="button"
          onClick={() => loadData(true)}
          disabled={reconciling}
          className="inline-flex items-center gap-2 rounded-lg border border-neutral-300 bg-white px-4 py-2 text-sm font-medium text-ink-700 shadow-sm hover:bg-neutral-50 disabled:opacity-60"
        >
          {reconciling ? (
            <>
              <span className="h-4 w-4 animate-spin rounded-full border-2 border-ink-500 border-t-transparent" />
              Checking courses...
            </>
          ) : (
            <>
              <svg className="h-4 w-4 text-ink-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
              </svg>
              Check for New Certificates
            </>
          )}
        </button>
      </header>

      {bannerMessage && (
        <div className="mb-4 flex items-center justify-between rounded-lg bg-emerald-50 p-4 text-sm text-emerald-800">
          <span>{bannerMessage}</span>
          <button type="button" onClick={() => setBannerMessage("")} className="text-emerald-600 hover:text-emerald-900 font-bold ml-2">✕</button>
        </div>
      )}

      {error && (
        <div className="mb-4 flex items-center justify-between rounded-lg bg-red-50 p-4 text-sm text-red-700">
          <span>{error}</span>
          <button
            type="button"
            onClick={() => loadData(true)}
            className="ml-4 rounded-md bg-red-100 px-3 py-1 font-semibold text-red-800 hover:bg-red-200"
          >
            Retry
          </button>
        </div>
      )}

      {certificates.length === 0 ? (
        <div className="rounded-xl border border-neutral-200 bg-white p-12 text-center text-sm text-ink-500 shadow-sm">
          <div className="mx-auto mb-3 flex h-12 w-12 items-center justify-center rounded-full bg-neutral-100 text-ink-400">
            <svg className="h-6 w-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <p className="font-medium text-ink-700">No certificates earned yet</p>
          <p className="mt-1 text-ink-500">
            Complete all required lessons in a course to automatically receive your certificate.
          </p>
        </div>
      ) : (
        <div className="grid gap-4">
          {certificates.map((certificate) => (
            <article key={certificate.id} className="rounded-xl border border-neutral-200 bg-white p-5 shadow-sm">
              <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
                <div className="space-y-1">
                  <div className="flex items-center gap-2">
                    <h2 className="text-lg font-semibold text-ink-900">{certificate.courseTitle}</h2>
                    {certificate.status === "ISSUED" && (
                      <span className="rounded-full bg-emerald-100 px-2 py-0.5 text-xs font-semibold text-emerald-800">
                        Issued
                      </span>
                    )}
                    {certificate.status === "PENDING" && (
                      <span className="rounded-full bg-blue-100 px-2 py-0.5 text-xs font-semibold text-blue-800">
                        Generating
                      </span>
                    )}
                    {certificate.status === "FAILED" && (
                      <span className="rounded-full bg-red-100 px-2 py-0.5 text-xs font-semibold text-red-800">
                        Failed
                      </span>
                    )}
                    {certificate.status === "REVOKED" && (
                      <span className="rounded-full bg-neutral-200 px-2 py-0.5 text-xs font-semibold text-ink-600">
                        Revoked
                      </span>
                    )}
                  </div>
                  <p className="text-sm text-ink-500">Instructor: {certificate.instructorName}</p>
                  <div className="flex flex-wrap items-center gap-2 pt-1 text-xs text-ink-400">
                    <span>ID: {certificate.certificateNumber || "—"}</span>
                    <span>·</span>
                    <span>Issued: {certificate.issuedAt ? new Date(certificate.issuedAt).toLocaleDateString() : "—"}</span>
                    {certificate.verificationCode && (
                      <>
                        <span>·</span>
                        <a
                          href={`/certificates/verify/${certificate.verificationCode}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="font-medium text-brand-600 hover:underline"
                        >
                          Verify Certificate
                        </a>
                      </>
                    )}
                  </div>
                  {certificate.status === "PENDING" && (
                    <p className="text-xs font-medium text-blue-600">Generation in progress — check back shortly.</p>
                  )}
                  {certificate.status === "FAILED" && (
                    <p className="text-xs font-medium text-red-600">Generation failed. Click retry to generate again.</p>
                  )}
                  {certificate.status === "REVOKED" && (
                    <p className="text-xs font-medium text-neutral-500">This certificate has been revoked.</p>
                  )}
                </div>

                <div className="flex items-center gap-3">
                  {certificate.status === "ISSUED" && (
                    <button
                      type="button"
                      onClick={() => handleDownload(certificate)}
                      className="inline-flex items-center gap-1.5 rounded-lg bg-[#a31c1b] px-4 py-2 text-sm font-semibold text-white shadow-sm hover:bg-[#8a1716]"
                    >
                      <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 16v1a3 3 0 003 3h10a3 3 0 003-3v-1m-4-4l-4 4m0 0l-4-4m4 4V4" />
                      </svg>
                      Download PDF
                    </button>
                  )}
                  {certificate.status === "FAILED" && (
                    <button
                      type="button"
                      onClick={() => handleRetry(certificate.id)}
                      className="rounded-lg border border-amber-500 bg-amber-50 px-4 py-2 text-sm font-semibold text-amber-800 hover:bg-amber-100"
                    >
                      Retry Generation
                    </button>
                  )}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}
    </main>
  );
}
