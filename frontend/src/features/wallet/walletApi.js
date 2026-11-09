import { apiRequest } from "../../shared/api/client";

/**
 * Fetches the current authenticated user's GeekCoins wallet balance.
 * Returns { balance: number, currency: string, currencyCode: string }
 */
export async function getWalletBalance() {
  return apiRequest("wallet/balance");
}

/**
 * Fetches available top-up recharge packages from the backend.
 * Returns Array of { id, name, inrAmount, amountPaise, currency, pgcAmount, badge, description }
 */
export async function getRechargePackages() {
  return apiRequest("wallet/topup/packages");
}

/**
 * Creates a Razorpay recharge order on the backend for the selected package.
 * Returns { orderId, razorpayOrderId, amountPaise, inrAmount, currency, pgcAmount, keyId, packageId, userEmail, userName }
 */
export async function createRechargeOrder(packageId) {
  return apiRequest("wallet/topup/orders", {
    method: "POST",
    body: { packageId },
  });
}

/**
 * Verifies Razorpay payment signature on the backend and credits GeekCoins.
 * Returns { success, orderId, razorpayOrderId, razorpayPaymentId, status, pgcAmount, newBalance, message }
 */
export async function verifyRechargePayment({
  razorpayOrderId,
  razorpayPaymentId,
  razorpaySignature,
}) {
  return apiRequest("wallet/topup/verify", {
    method: "POST",
    body: {
      razorpayOrderId,
      razorpayPaymentId,
      razorpaySignature,
    },
  });
}

/**
 * Fetches paginated wallet transactions for the current authenticated user.
 * Returns Spring Page { content: [...], totalElements, totalPages, number, size, ... }
 */
export async function getWalletTransactions(page = 0, size = 10) {
  return apiRequest(`wallet/transactions?page=${page}&size=${size}`);
}
