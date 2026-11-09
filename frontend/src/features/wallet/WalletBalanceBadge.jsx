import { Link } from "react-router-dom";
import { useQuery } from "@tanstack/react-query";
import { useAuthStore } from "../auth/authStore";
import { getWalletBalance } from "./walletApi";

export function WalletBalanceBadge() {
  const isAuthenticated = useAuthStore(
    (state) => state.status === "authenticated",
  );

  const walletQuery = useQuery({
    queryKey: ["wallet", "balance"],
    queryFn: getWalletBalance,
    enabled: isAuthenticated,
    staleTime: 30000,
    refetchInterval: 60000,
    refetchIntervalInBackground: false,
    refetchOnWindowFocus: true,
  });

  if (!isAuthenticated) return null;

  const { data, isLoading, isError } = walletQuery;

  const rawBalance = data?.balance;
  const currencyCode = data?.currencyCode || data?.currency || "PGC";
  const numericBalance =
    rawBalance !== undefined && rawBalance !== null
      ? Number(rawBalance)
      : null;

  const formattedBalance =
    numericBalance !== null && !Number.isNaN(numericBalance)
      ? numericBalance.toLocaleString(undefined, {
          minimumFractionDigits: 0,
          maximumFractionDigits: 2,
        })
      : "0";

  return (
    <Link
      to="/wallet"
      aria-label={`GeekCoins balance: ${isLoading ? "loading" : isError ? "unavailable" : `${formattedBalance} ${currencyCode}`}`}
      className="inline-flex items-center gap-1.5 rounded-full border border-amber-500/30 bg-neutral-800/80 px-2.5 py-1 text-xs font-medium text-neutral-200 shadow-xs transition-colors hover:border-amber-500/60 hover:bg-neutral-800 select-none cursor-pointer"
      role="status"
      title={
        isLoading
          ? "Loading GeekCoins balance…"
          : isError
            ? "Could not load GeekCoins balance"
            : `${formattedBalance} GeekCoins (${currencyCode}) — Click to view wallet & recharge`
      }
    >
      <span aria-hidden="true" className="text-sm leading-none drop-shadow-xs">
        🪙
      </span>

      {isLoading ? (
        <span
          aria-hidden="true"
          className="inline-block h-3.5 w-6 animate-pulse rounded bg-neutral-700"
          data-testid="wallet-loading"
        />
      ) : isError ? (
        <span
          aria-hidden="true"
          className="text-neutral-400 font-normal"
          data-testid="wallet-error"
        >
          —
        </span>
      ) : (
        <span
          className="font-semibold text-amber-200 tracking-tight"
          data-testid="wallet-balance"
        >
          {formattedBalance}
        </span>
      )}

      <span className="sr-only">GeekCoins ({currencyCode})</span>
    </Link>
  );
}
