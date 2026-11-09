import { useEffect, useState } from "react";
import { useParams, Link } from "react-router-dom";
import { verifyCertificate } from "./certificateApi";
import { Spinner } from "../../shared/components/Spinner";

export function VerifyCertificatePage() {
  const { verificationCode } = useParams();
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    verifyCertificate(verificationCode)
      .then(setResult)
      .catch(() => setResult({ valid: false, status: null }))
      .finally(() => setLoading(false));
  }, [verificationCode]);

  if (loading) return <Spinner label="Verifying certificate..." />;

  const isRevoked = result?.status === "REVOKED";
  const isValid = result?.valid && result?.status === "ISSUED";

  return (
    <main className="mx-auto max-w-2xl px-4 py-12 sm:px-6">
      <section className="rounded-2xl border border-neutral-200 bg-white p-8 text-center shadow-sm">
        {isValid ? (
          <>
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100 text-emerald-700">
              <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
              </svg>
            </div>
            <p className="text-sm font-bold uppercase tracking-wider text-emerald-700">
              Verified Certificate
            </p>
            <h1 className="mt-4 text-2xl font-bold text-ink-900">PeerGeek Certificate of Completion</h1>
            <p className="mt-6 text-xl font-semibold text-ink-800">{result.studentName}</p>
            <p className="mt-2 text-base text-ink-600">has successfully completed</p>
            <p className="mt-1 text-lg font-medium text-[#a31c1b]">{result.courseTitle}</p>
            <p className="mt-4 text-sm text-ink-500">Instructor: {result.instructorName}</p>
            {result.issuedAt && (
              <p className="mt-1 text-xs text-ink-400">
                Issued on {new Date(result.issuedAt).toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" })}
              </p>
            )}
            <p className="mt-4 font-mono text-xs text-ink-400">ID: {result.certificateNumber}</p>
          </>
        ) : isRevoked ? (
          <>
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-red-100 text-red-700">
              <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
              </svg>
            </div>
            <p className="text-sm font-bold uppercase tracking-wider text-red-700">
              Certificate Revoked
            </p>
            <h1 className="mt-4 text-2xl font-bold text-ink-900">Certificate No Longer Valid</h1>
            <p className="mt-4 text-sm text-ink-600">
              This certificate for <span className="font-semibold">{result.courseTitle || "the course"}</span> originally issued to <span className="font-semibold">{result.studentName || "the student"}</span> has been revoked by platform administrators.
            </p>
            {result.revocationReason && (
              <div className="mt-4 rounded-lg bg-red-50 p-3 text-sm text-red-800">
                <span className="font-medium">Reason: </span>
                {result.revocationReason}
              </div>
            )}
            {result.revokedAt && (
              <p className="mt-3 text-xs text-ink-400">
                Revoked on {new Date(result.revokedAt).toLocaleDateString(undefined, { year: "numeric", month: "long", day: "numeric" })}
              </p>
            )}
            {result.certificateNumber && (
              <p className="mt-2 font-mono text-xs text-ink-400">ID: {result.certificateNumber}</p>
            )}
          </>
        ) : (
          <>
            <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-full bg-surface-100 text-ink-500">
              <svg className="h-8 w-8" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </div>
            <p className="text-sm font-bold uppercase tracking-wider text-red-700">
              Certificate Not Valid
            </p>
            <h1 className="mt-4 text-2xl font-bold text-ink-900">Verification Failed</h1>
            <p className="mt-4 text-sm text-ink-500">
              The verification code provided does not match any valid certificate in the PeerGeek registry. Please verify the link or QR code and try again.
            </p>
          </>
        )}

        <div className="mt-8 border-t border-neutral-100 pt-6">
          <Link to="/" className="text-sm font-semibold text-[#a31c1b] hover:underline">
            ← Return to PeerGeek Home
          </Link>
        </div>
      </section>
    </main>
  );
}
