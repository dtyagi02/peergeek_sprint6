import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, render, screen, fireEvent, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { WalletBalanceBadge } from "./WalletBalanceBadge";
import { MyWalletPage } from "./MyWalletPage";
import * as walletApi from "./walletApi";
import * as razorpayLoader from "./razorpayLoader";
import { useAuthStore } from "../auth/authStore";
import { slotRegistry } from "../../app/registry/slotRegistry";

vi.mock("./walletApi", () => ({
  getWalletBalance: vi.fn(),
  getRechargePackages: vi.fn(),
  createRechargeOrder: vi.fn(),
  verifyRechargePayment: vi.fn(),
  getWalletTransactions: vi.fn(),
}));

vi.mock("./razorpayLoader", () => ({
  loadRazorpay: vi.fn(),
}));

function renderWithClient(ui) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        {ui}
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const mockPackages = [
  {
    id: "pkg_100",
    name: "Starter",
    inrAmount: 100,
    amountPaise: 10000,
    currency: "INR",
    pgcAmount: 100,
    badge: null,
    description: "100 GeekCoins for learning",
  },
  {
    id: "pkg_500",
    name: "Popular",
    inrAmount: 500,
    amountPaise: 50000,
    currency: "INR",
    pgcAmount: 500,
    badge: "Popular",
    description: "500 GeekCoins pack",
  },
];

const mockTransactions = {
  content: [
    {
      id: 101,
      type: "RAZORPAY_TOPUP",
      typeLabel: "Recharge",
      amount: 500,
      balanceAfter: 650,
      reservedAfter: 0,
      referenceType: "RAZORPAY_PAYMENT",
      referenceId: "pay_test_abc123",
      description: "GeekCoins Recharge: 500 PGC (INR 500)",
      createdAt: "2026-11-04T10:00:00Z",
    },
  ],
  totalPages: 1,
  totalElements: 1,
  number: 0,
  size: 10,
};

describe("GeekCoins Wallet Feature", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    useAuthStore.setState({
      status: "authenticated",
      user: { id: 1, email: "user@test.local", displayName: "Tester", lastMode: "STUDENT" },
    });
  });

  afterEach(() => {
    cleanup();
  });

  describe("WalletBalanceBadge", () => {
    it("renders coin emoji and zero balance for newly registered or initialized user", async () => {
      walletApi.getWalletBalance.mockResolvedValueOnce({
        balance: 0.00,
        currency: "PGC",
        currencyCode: "PGC",
      });

      renderWithClient(<WalletBalanceBadge />);

      expect(screen.getByText("🪙")).toBeInTheDocument();
      const balanceEl = await screen.findByTestId("wallet-balance");
      expect(balanceEl).toHaveTextContent("0");
    });

    it("displays formatted numeric balance for positive coin balances", async () => {
      walletApi.getWalletBalance.mockResolvedValueOnce({
        balance: 1250.50,
        currency: "PGC",
        currencyCode: "PGC",
      });

      renderWithClient(<WalletBalanceBadge />);

      const balanceEl = await screen.findByTestId("wallet-balance");
      expect(balanceEl).toHaveTextContent("1,250.5");
      expect(screen.getByRole("status")).toHaveAttribute(
        "aria-label",
        "GeekCoins balance: 1,250.5 PGC",
      );
    });

    it("displays loading state while balance is in flight", () => {
      walletApi.getWalletBalance.mockReturnValue(new Promise(() => {}));

      renderWithClient(<WalletBalanceBadge />);

      expect(screen.getByText("🪙")).toBeInTheDocument();
      expect(screen.getByTestId("wallet-loading")).toBeInTheDocument();
    });

    it("displays graceful fallback on error without breaking navigation", async () => {
      walletApi.getWalletBalance.mockRejectedValueOnce(new Error("Network error"));

      renderWithClient(<WalletBalanceBadge />);

      const errorEl = await screen.findByTestId("wallet-error");
      expect(errorEl).toHaveTextContent("—");
      expect(screen.getByText("🪙")).toBeInTheDocument();
    });

    it("does not render badge when user is unauthenticated", () => {
      useAuthStore.setState({
        status: "anonymous",
        user: null,
      });

      const { container } = renderWithClient(<WalletBalanceBadge />);
      expect(container.firstChild).toBeNull();
    });
  });

  describe("MyWalletPage", () => {
    it("renders wallet balance, recharge packages, and transaction table", async () => {
      walletApi.getWalletBalance.mockResolvedValue({
        balance: 150.00,
        currency: "PGC",
        currencyCode: "PGC",
      });
      walletApi.getRechargePackages.mockResolvedValue(mockPackages);
      walletApi.getWalletTransactions.mockResolvedValue(mockTransactions);

      renderWithClient(<MyWalletPage />);

      expect(screen.getByText("My Wallet")).toBeInTheDocument();

      await waitFor(() => {
        expect(screen.getByTestId("wallet-page-balance")).toHaveTextContent("150");
      });

      expect(await screen.findByText("Starter")).toBeInTheDocument();
      expect(screen.getAllByText("Popular").length).toBeGreaterThanOrEqual(1);
      expect(screen.getByText("₹500")).toBeInTheDocument();

      const table = await screen.findByTestId("transactions-table");
      expect(table).toBeInTheDocument();
      expect(screen.getByText(/GeekCoins Recharge: 500 PGC/i)).toBeInTheDocument();
      expect(screen.getByText("+500 PGC")).toBeInTheDocument();
    });

    it("allows package selection and launches Razorpay checkout flow", async () => {
      walletApi.getWalletBalance.mockResolvedValue({ balance: 0.00, currency: "PGC" });
      walletApi.getRechargePackages.mockResolvedValue(mockPackages);
      walletApi.getWalletTransactions.mockResolvedValue({ content: [], totalPages: 0, totalElements: 0 });

      const mockOpen = vi.fn();
      const mockOn = vi.fn();
      let capturedOptions = null;

      function MockRazorpay(opts) {
        capturedOptions = opts;
        return {
          open: mockOpen,
          on: mockOn,
        };
      }
      razorpayLoader.loadRazorpay.mockResolvedValue(MockRazorpay);

      walletApi.createRechargeOrder.mockResolvedValue({
        orderId: 1,
        razorpayOrderId: "order_rzp_mock_123",
        amountPaise: 10000,
        inrAmount: 100,
        currency: "INR",
        pgcAmount: 100,
        keyId: "rzp_test_mock_key",
        packageId: "pkg_100",
        userEmail: "user@test.local",
        userName: "Tester",
      });

      walletApi.verifyRechargePayment.mockResolvedValue({
        success: true,
        orderId: 1,
        razorpayOrderId: "order_rzp_mock_123",
        razorpayPaymentId: "pay_rzp_mock_456",
        status: "CAPTURED",
        pgcAmount: 100,
        newBalance: 100,
        message: "Recharge successful! 100 GeekCoins credited to your wallet.",
      });

      renderWithClient(<MyWalletPage />);

      // Select Starter package
      const starterCard = await screen.findByTestId("package-card-pkg_100");
      fireEvent.click(starterCard);

      // Click Recharge button
      const rechargeBtn = screen.getByTestId("recharge-btn");
      fireEvent.click(rechargeBtn);

      await waitFor(() => {
        expect(walletApi.createRechargeOrder).toHaveBeenCalledWith("pkg_100");
        expect(razorpayLoader.loadRazorpay).toHaveBeenCalled();
        expect(mockOpen).toHaveBeenCalled();
      });

      expect(capturedOptions.key).toBe("rzp_test_mock_key");
      expect(capturedOptions.order_id).toBe("order_rzp_mock_123");

      // Simulate payment success handler callback from Razorpay
      await capturedOptions.handler({
        razorpay_order_id: "order_rzp_mock_123",
        razorpay_payment_id: "pay_rzp_mock_456",
        razorpay_signature: "mock_signature_hash",
      });

      expect(walletApi.verifyRechargePayment).toHaveBeenCalledWith({
        razorpayOrderId: "order_rzp_mock_123",
        razorpayPaymentId: "pay_rzp_mock_456",
        razorpaySignature: "mock_signature_hash",
      });

      await waitFor(() => {
        expect(screen.getByText(/100 GeekCoins credited to your wallet/i)).toBeInTheDocument();
      });
    });

    it("displays empty state when user has no transactions", async () => {
      walletApi.getWalletBalance.mockResolvedValue({ balance: 0.00, currency: "PGC" });
      walletApi.getRechargePackages.mockResolvedValue(mockPackages);
      walletApi.getWalletTransactions.mockResolvedValue({ content: [], totalPages: 0, totalElements: 0 });

      renderWithClient(<MyWalletPage />);

      expect(await screen.findByTestId("no-transactions")).toBeInTheDocument();
      expect(screen.getByText("No transactions recorded yet")).toBeInTheDocument();
    });
  });

  describe("Slot Contribution", () => {
    it("registers wallet-balance-action in header.actions before notification bell", () => {
      const headerActions = slotRegistry["header.actions"] || [];
      const walletSlot = headerActions.find((item) => item.id === "wallet-balance-action");

      expect(walletSlot).toBeDefined();
      expect(walletSlot.order).toBe(4);
    });
  });
});
