import { useEffect, useState } from "react";
import {
  getReferralConfig,
  getReferralHistory,
  getReferralSummary,
  sendReferralInvitation,
} from "./referralApi";

const statusLabels = {
  PENDING: "Pending",
  REGISTERED: "Registered",
  REWARDED: "Rewarded",
  EXPIRED: "Expired",
  CANCELLED: "Cancelled",
};

export function ReferralModal({ open, onClose }) {
  const [email, setEmail] = useState("");
  const [summary, setSummary] = useState(null);
  const [history, setHistory] = useState([]);
  const [config, setConfig] = useState(null);
  const [loading, setLoading] = useState(false);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  useEffect(() => {
    if (!open) return;
    let active = true;
    Promise.resolve().then(() => {
      if (!active) return;
      setLoading(true);
      setError("");
      return Promise.all([getReferralSummary(), getReferralHistory(), getReferralConfig()])
        .then(([nextSummary, nextHistory, nextConfig]) => {
          if (!active) return;
          setSummary(nextSummary);
          setHistory(nextHistory?.content ?? []);
          setConfig(nextConfig);
        })
        .catch((requestError) => {
          if (active) setError(requestError?.message || "Could not load referral details.");
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    });
    return () => {
      active = false;
    };
  }, [open]);

  if (!open) return null;

  async function handleSubmit(event) {
    event.preventDefault();
    setError("");
    setSuccess("");
    setSending(true);
    try {
      await sendReferralInvitation(email.trim());
      setEmail("");
      setSuccess("Invitation sent successfully.");
      const [nextSummary, nextHistory] = await Promise.all([
        getReferralSummary(),
        getReferralHistory(),
      ]);
      setSummary(nextSummary);
      setHistory(nextHistory?.content ?? []);
    } catch (requestError) {
      setError(requestError?.message || "Could not send the invitation.");
    } finally {
      setSending(false);
    }
  }

  return (
    <div
      aria-labelledby="referral-modal-title"
      aria-modal="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      role="dialog"
    >
      <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl">
        <div className="flex items-start justify-between gap-4">
          <div>
            <h2 className="text-2xl font-bold text-[#171717]" id="referral-modal-title">
              Refer &amp; Earn
            </h2>
            <p className="mt-1 text-sm text-neutral-600">
              Invite a friend to PeerGeek and earn GeekCoins when they verify their email.
            </p>
          </div>
          <button
            aria-label="Close Refer and Earn"
            className="rounded-lg p-2 text-xl text-neutral-500 hover:bg-neutral-100"
            onClick={onClose}
            type="button"
          >
            ×
          </button>
        </div>

        {config && (
          <p className="mt-4 rounded-lg bg-amber-50 p-3 text-sm text-amber-900">
            Earn {config.inviterReward} GeekCoins; your friend receives {config.newUserReward} GeekCoins.
          </p>
        )}

        <form className="mt-5 flex flex-col gap-3 sm:flex-row" onSubmit={handleSubmit}>
          <label className="sr-only" htmlFor="referral-email">Friend&apos;s email</label>
          <input
            className="min-w-0 flex-1 rounded-lg border border-neutral-300 px-3 py-2 focus:border-[#A31C1B] focus:outline-none focus:ring-1 focus:ring-[#A31C1B]"
            id="referral-email"
            onChange={(event) => setEmail(event.target.value)}
            placeholder="friend@example.com"
            required
            type="email"
            value={email}
          />
          <button
            className="rounded-lg bg-[#A31C1B] px-4 py-2 font-semibold text-white hover:bg-[#881716] disabled:opacity-60"
            disabled={sending}
            type="submit"
          >
            {sending ? "Sending…" : "Send Invitation"}
          </button>
        </form>

        {loading && <p className="mt-4 text-sm text-neutral-600">Loading referral details…</p>}
        {error && <p className="mt-4 text-sm text-red-700" role="alert">{error}</p>}
        {success && <p className="mt-4 text-sm text-green-700" role="status">{success}</p>}

        {summary && (
          <div className="mt-6 grid grid-cols-2 gap-3 sm:grid-cols-4">
            {[
              ["Total invitations", summary.totalInvitations],
              ["Pending referrals", summary.pendingReferrals],
              ["Successful referrals", summary.successfulReferrals],
              ["GeekCoins earned", summary.totalCoinsEarned],
            ].map(([label, value]) => (
              <div className="rounded-lg bg-neutral-50 p-3" key={label}>
                <p className="text-xs text-neutral-500">{label}</p>
                <p className="mt-1 text-lg font-bold text-[#171717]">{value}</p>
              </div>
            ))}
          </div>
        )}

        <section className="mt-6" aria-labelledby="referral-history-title">
          <h3 className="text-sm font-semibold uppercase tracking-wide text-neutral-500" id="referral-history-title">
            Referral history
          </h3>
          {history.length === 0 ? (
            <p className="mt-3 text-sm text-neutral-600">No invitations yet.</p>
          ) : (
            <div className="mt-3 divide-y rounded-lg border border-neutral-200">
              {history.map((item) => (
                <div className="flex items-center justify-between gap-3 p-3 text-sm" key={item.id}>
                  <div>
                    <p className="font-medium text-[#171717]">{item.invitedEmail}</p>
                    <p className="text-xs text-neutral-500">
                      {new Date(item.createdAt).toLocaleDateString()}
                    </p>
                  </div>
                  <span className="rounded-full bg-neutral-100 px-2.5 py-1 text-xs font-semibold text-neutral-700">
                    {statusLabels[item.status] || item.status}
                  </span>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
