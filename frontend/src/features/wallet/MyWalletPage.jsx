import { useState } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { getWalletBalance, getRechargePackages, createRechargeOrder, verifyRechargePayment, getWalletTransactions } from "./walletApi";
import { loadRazorpay } from "./razorpayLoader";
import { Spinner } from "../../shared/components/Spinner";

export function MyWalletPage() {
  const queryClient = useQueryClient();

  const [selectedPackageId, setSelectedPackageId] = useState("pkg_500");
  const [isProcessing, setIsProcessing] = useState(false);
  const [processingStage, setProcessingStage] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [errorMessage, setErrorMessage] = useState("");
  const [page, setPage] = useState(0);

  // 1. Wallet Balance
  const balanceQuery = useQuery({
    queryKey: ["wallet", "balance"],
    queryFn: getWalletBalance,
    staleTime: 10000,
  });

  // 2. Recharge Packages
  const packagesQuery = useQuery({
    queryKey: ["wallet", "packages"],
    queryFn: getRechargePackages,
    staleTime: 60000,
  });

  // 3. Transactions
  const transactionsQuery = useQuery({
    queryKey: ["wallet", "transactions", page],
    queryFn: () => getWalletTransactions(page, 10),
    staleTime: 10000,
  });

  const rawBalance = balanceQuery.data?.balance;
  const currencyCode = balanceQuery.data?.currencyCode || balanceQuery.data?.currency || "PGC";
  const numericBalance = rawBalance !== undefined && rawBalance !== null ? Number(rawBalance) : 0;
  const formattedBalance = numericBalance.toLocaleString(undefined, {
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  });

  const packages = packagesQuery.data || [];
  const selectedPackage = packages.find((p) => p.id === selectedPackageId) || packages[0];

  const transactions = transactionsQuery.data?.content || [];
  const totalPages = transactionsQuery.data?.totalPages || 0;
  const totalElements = transactionsQuery.data?.totalElements || 0;

  const handleRecharge = async () => {
    if (!selectedPackage) return;

    setErrorMessage("");
    setSuccessMessage("");
    setIsProcessing(true);
    setProcessingStage("Initializing checkout...");

    try {
      // 1. Create order on backend
      const order = await createRechargeOrder(selectedPackage.id);

      // 2. Load Razorpay Checkout SDK
      setProcessingStage("Opening Razorpay...");
      const Razorpay = await loadRazorpay();

      // 3. Configure Razorpay options
      const options = {
        key: order.keyId,
        amount: order.amountPaise,
        currency: order.currency || "INR",
        name: "PeerGeek",
        description: `Recharge ${order.pgcAmount} GeekCoins (${selectedPackage.name})`,
        order_id: order.razorpayOrderId,
        prefill: {
          name: order.userName || "",
          email: order.userEmail || "",
        },
        theme: {
          color: "#991b1b", // PeerGeek deep red
        },
        modal: {
          ondismiss: () => {
            setIsProcessing(false);
            setProcessingStage("");
          },
        },
        handler: async (response) => {
          setProcessingStage("Verifying payment...");
          try {
            const verification = await verifyRechargePayment({
              razorpayOrderId: response.razorpay_order_id,
              razorpayPaymentId: response.razorpay_payment_id,
              razorpaySignature: response.razorpay_signature,
            });

            // Invalidate wallet caches
            await queryClient.invalidateQueries({ queryKey: ["wallet", "balance"] });
            await queryClient.invalidateQueries({ queryKey: ["wallet", "transactions"] });

            setSuccessMessage(
              verification.message || `Successfully added ${verification.pgcAmount} GeekCoins to your wallet!`
            );
          } catch (verifyErr) {
            setErrorMessage(verifyErr.message || "Payment verification failed. Please contact support.");
          } finally {
            setIsProcessing(false);
            setProcessingStage("");
          }
        },
      };

      const rzp = new Razorpay(options);
      rzp.on("payment.failed", (response) => {
        setIsProcessing(false);
        setProcessingStage("");
        setErrorMessage(response.error?.description || "Payment failed or was cancelled.");
      });

      rzp.open();
    } catch (err) {
      setIsProcessing(false);
      setProcessingStage("");
      setErrorMessage(err.message || "Failed to start recharge. Please try again.");
    }
  };

  return (
    <div className="min-h-screen bg-neutral-950 text-neutral-100 py-10 px-4 sm:px-6 lg:px-8">
      <div className="max-w-5xl mx-auto space-y-8">
        {/* Header */}
        <div>
          <h1 className="text-3xl font-extrabold tracking-tight text-white sm:text-4xl">
            My Wallet
          </h1>
          <p className="mt-2 text-sm text-neutral-400">
            Manage your GeekCoins balance, recharge coins using Razorpay Test Mode, and track transaction history.
          </p>
        </div>

        {/* Feedback Banners */}
        {successMessage && (
          <div
            role="alert"
            className="rounded-xl border border-emerald-500/30 bg-emerald-950/40 p-4 text-emerald-200 flex items-start justify-between shadow-lg"
          >
            <div className="flex items-center gap-3">
              <span className="text-xl">✅</span>
              <p className="text-sm font-medium">{successMessage}</p>
            </div>
            <button
              onClick={() => setSuccessMessage("")}
              className="text-emerald-400 hover:text-emerald-200 text-sm font-bold ml-4"
              aria-label="Dismiss alert"
            >
              ✕
            </button>
          </div>
        )}

        {errorMessage && (
          <div
            role="alert"
            className="rounded-xl border border-red-500/30 bg-red-950/40 p-4 text-red-200 flex items-start justify-between shadow-lg"
          >
            <div className="flex items-center gap-3">
              <span className="text-xl">⚠️</span>
              <p className="text-sm font-medium">{errorMessage}</p>
            </div>
            <button
              onClick={() => setErrorMessage("")}
              className="text-red-400 hover:text-red-200 text-sm font-bold ml-4"
              aria-label="Dismiss alert"
            >
              ✕
            </button>
          </div>
        )}

        {/* Wallet Overview & Balance Card */}
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          <div className="md:col-span-2 rounded-2xl border border-neutral-800 bg-neutral-900/90 p-6 sm:p-8 relative overflow-hidden shadow-xl">
            <div className="absolute top-0 right-0 -mt-6 -mr-6 w-32 h-32 rounded-full bg-amber-500/10 blur-2xl pointer-events-none" />
            <div className="relative">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold tracking-wider uppercase text-neutral-400">
                  Available Balance
                </span>
                <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-400 bg-amber-500/10 px-2.5 py-0.5 rounded-full border border-amber-500/20">
                  🪙 1 PGC = ₹1.00 INR
                </span>
              </div>

              <div className="mt-4 flex items-baseline gap-3">
                <span className="text-5xl font-black tracking-tight text-white" data-testid="wallet-page-balance">
                  {balanceQuery.isLoading ? "..." : formattedBalance}
                </span>
                <span className="text-xl font-bold text-amber-400">{currencyCode}</span>
              </div>

              <p className="mt-3 text-xs text-neutral-400">
                Use GeekCoins to book 1-on-1 doubt sessions with course instructors and unlock premium learning sessions.
              </p>
            </div>
          </div>

          {/* Test Mode Info Card */}
          <div className="rounded-2xl border border-neutral-800 bg-neutral-900/60 p-6 flex flex-col justify-between">
            <div>
              <div className="flex items-center gap-2">
                <span className="inline-block w-2.5 h-2.5 rounded-full bg-emerald-400 animate-pulse" />
                <span className="text-xs font-semibold uppercase tracking-wider text-emerald-400">
                  Razorpay Test Mode
                </span>
              </div>
              <h3 className="mt-2 text-sm font-bold text-neutral-200">
                Safe Test Environment
              </h3>
              <p className="mt-2 text-xs text-neutral-400 leading-relaxed">
                Recharges use Razorpay Test credentials. You can use standard Razorpay test cards, NetBanking, or dummy UPI IDs. No real money will be charged.
              </p>
            </div>
            <div className="mt-4 text-[11px] text-neutral-500">
              Instant credit upon automated cryptographic signature verification.
            </div>
          </div>
        </div>

        {/* Recharge Section */}
        <div className="rounded-2xl border border-neutral-800 bg-neutral-900/90 p-6 sm:p-8 shadow-xl space-y-6">
          <div>
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <span>💳</span> Recharge GeekCoins
            </h2>
            <p className="mt-1 text-xs sm:text-sm text-neutral-400">
              Select a recharge package below to top up your wallet balance instantly.
            </p>
          </div>

          {/* Package Selection Grid */}
          {packagesQuery.isLoading ? (
            <div className="py-12 flex justify-center">
              <Spinner label="Loading recharge packages..." />
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
              {packages.map((pkg) => {
                const isSelected = selectedPackageId === pkg.id;
                return (
                  <div
                    key={pkg.id}
                    onClick={() => setSelectedPackageId(pkg.id)}
                    data-testid={`package-card-${pkg.id}`}
                    className={`relative cursor-pointer rounded-xl border p-5 transition-all duration-150 flex flex-col justify-between ${
                      isSelected
                        ? "border-red-600 bg-red-950/20 shadow-md shadow-red-900/20 ring-1 ring-red-600"
                        : "border-neutral-800 bg-neutral-900/40 hover:border-neutral-700 hover:bg-neutral-800/40"
                    }`}
                  >
                    {pkg.badge && (
                      <span className="absolute -top-2.5 right-3 px-2 py-0.5 rounded-full text-[10px] font-bold uppercase tracking-wider bg-red-600 text-white shadow-xs">
                        {pkg.badge}
                      </span>
                    )}

                    <div>
                      <div className="flex items-center justify-between">
                        <span className="text-sm font-bold text-neutral-300">{pkg.name}</span>
                        <div
                          className={`w-4 h-4 rounded-full border flex items-center justify-center ${
                            isSelected ? "border-red-500 bg-red-500" : "border-neutral-600"
                          }`}
                        >
                          {isSelected && <div className="w-1.5 h-1.5 rounded-full bg-white" />}
                        </div>
                      </div>

                      <div className="mt-4 flex items-baseline gap-1">
                        <span className="text-3xl font-black text-white">₹{pkg.inrAmount}</span>
                      </div>

                      <div className="mt-2 inline-flex items-center gap-1.5 rounded-md bg-neutral-800/80 px-2 py-1 text-xs font-semibold text-amber-300">
                        <span>🪙</span>
                        <span>{pkg.pgcAmount} PGC</span>
                      </div>

                      <p className="mt-3 text-xs text-neutral-400 leading-snug">
                        {pkg.description}
                      </p>
                    </div>
                  </div>
                );
              })}
            </div>
          )}

          {/* Action Button & Summary */}
          {selectedPackage && (
            <div className="pt-4 border-t border-neutral-800/80 flex flex-col sm:flex-row items-center justify-between gap-4">
              <div className="text-xs sm:text-sm text-neutral-300 text-center sm:text-left">
                Pay <strong className="text-white font-semibold">₹{selectedPackage.inrAmount} INR</strong> to receive{" "}
                <strong className="text-amber-300 font-semibold">{selectedPackage.pgcAmount} GeekCoins</strong>
              </div>

              <button
                type="button"
                onClick={handleRecharge}
                disabled={isProcessing || packagesQuery.isLoading}
                data-testid="recharge-btn"
                className="w-full sm:w-auto inline-flex items-center justify-center gap-2 rounded-xl bg-red-600 px-6 py-3 text-sm font-bold text-white shadow-lg hover:bg-red-700 active:bg-red-800 transition-colors disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                {isProcessing ? (
                  <>
                    <span className="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white border-t-transparent" />
                    <span>{processingStage || "Processing..."}</span>
                  </>
                ) : (
                  <>
                    <span>⚡</span>
                    <span>Recharge with Razorpay</span>
                  </>
                )}
              </button>
            </div>
          )}
        </div>

        {/* Transaction History Section */}
        <div className="rounded-2xl border border-neutral-800 bg-neutral-900/90 p-6 sm:p-8 shadow-xl space-y-6">
          <div className="flex items-center justify-between">
            <div>
              <h2 className="text-xl font-bold text-white flex items-center gap-2">
                <span>📜</span> Transaction History
              </h2>
              <p className="mt-1 text-xs sm:text-sm text-neutral-400">
                Detailed record of all GeekCoins credits, debits, reservations, and rewards.
              </p>
            </div>
            {totalElements > 0 && (
              <span className="text-xs text-neutral-500">
                Total {totalElements} transaction{totalElements === 1 ? "" : "s"}
              </span>
            )}
          </div>

          {transactionsQuery.isLoading ? (
            <div className="py-12 flex justify-center">
              <Spinner label="Loading transactions..." />
            </div>
          ) : transactions.length === 0 ? (
            <div className="py-12 text-center rounded-xl border border-dashed border-neutral-800 bg-neutral-950/40" data-testid="no-transactions">
              <span className="text-4xl">🪙</span>
              <h3 className="mt-3 text-sm font-bold text-neutral-300">No transactions recorded yet</h3>
              <p className="mt-1 text-xs text-neutral-500">
                Your wallet transaction history will appear here once you recharge or spend coins.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs sm:text-sm text-neutral-300" data-testid="transactions-table">
                <thead className="bg-neutral-950 text-neutral-400 uppercase text-[10px] tracking-wider border-b border-neutral-800">
                  <tr>
                    <th className="py-3 px-4">Date</th>
                    <th className="py-3 px-4">Type</th>
                    <th className="py-3 px-4">Description</th>
                    <th className="py-3 px-4 text-right">Amount</th>
                    <th className="py-3 px-4 text-right">Balance</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-neutral-800/60 font-mono">
                  {transactions.map((tx) => {
                    const isCredit =
                      tx.type === "RAZORPAY_TOPUP" ||
                      tx.type === "CREDIT" ||
                      tx.type === "REFERRAL_INVITER_REWARD" ||
                      tx.type === "REFERRAL_NEW_USER_REWARD" ||
                      tx.type === "RELEASE";

                    return (
                      <tr key={tx.id} className="hover:bg-neutral-800/40 transition-colors font-sans">
                        <td className="py-3.5 px-4 text-neutral-400 whitespace-nowrap text-xs">
                          {tx.createdAt
                            ? new Date(tx.createdAt).toLocaleDateString(undefined, {
                                month: "short",
                                day: "numeric",
                                year: "numeric",
                                hour: "2-digit",
                                minute: "2-digit",
                              })
                            : "—"}
                        </td>
                        <td className="py-3.5 px-4 whitespace-nowrap">
                          <span
                            className={`inline-block px-2 py-0.5 rounded text-[11px] font-semibold ${
                              tx.type === "RAZORPAY_TOPUP"
                                ? "bg-emerald-500/10 text-emerald-300 border border-emerald-500/20"
                                : tx.type.includes("REFERRAL")
                                  ? "bg-purple-500/10 text-purple-300 border border-purple-500/20"
                                  : tx.type === "DEBIT"
                                    ? "bg-red-500/10 text-red-300 border border-red-500/20"
                                    : "bg-neutral-800 text-neutral-300 border border-neutral-700"
                            }`}
                          >
                            {tx.typeLabel || tx.type}
                          </span>
                        </td>
                        <td className="py-3.5 px-4 text-neutral-200 font-sans max-w-xs truncate">
                          {tx.description || tx.referenceId || "Wallet activity"}
                        </td>
                        <td
                          className={`py-3.5 px-4 text-right font-bold whitespace-nowrap ${
                            isCredit ? "text-emerald-400" : "text-neutral-300"
                          }`}
                        >
                          {isCredit ? "+" : "-"}
                          {Number(tx.amount).toLocaleString(undefined, {
                            minimumFractionDigits: 0,
                            maximumFractionDigits: 2,
                          })}{" "}
                          PGC
                        </td>
                        <td className="py-3.5 px-4 text-right text-neutral-400 whitespace-nowrap text-xs">
                          {Number(tx.balanceAfter).toLocaleString(undefined, {
                            minimumFractionDigits: 0,
                            maximumFractionDigits: 2,
                          })}{" "}
                          PGC
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {/* Pagination */}
              {totalPages > 1 && (
                <div className="pt-4 flex items-center justify-between border-t border-neutral-800 text-xs text-neutral-400">
                  <span>
                    Page {page + 1} of {totalPages}
                  </span>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      disabled={page === 0}
                      onClick={() => setPage((p) => Math.max(0, p - 1))}
                      className="px-3 py-1.5 rounded-lg border border-neutral-800 bg-neutral-900 text-neutral-200 hover:bg-neutral-800 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                    >
                      Previous
                    </button>
                    <button
                      type="button"
                      disabled={page >= totalPages - 1}
                      onClick={() => setPage((p) => p + 1)}
                      className="px-3 py-1.5 rounded-lg border border-neutral-800 bg-neutral-900 text-neutral-200 hover:bg-neutral-800 disabled:opacity-40 disabled:cursor-not-allowed cursor-pointer"
                    >
                      Next
                    </button>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

