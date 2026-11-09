import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ReferralModal } from "./ReferralModal";
import * as referralApi from "./referralApi";
import { RegisterPage } from "../auth/AuthPages";
import { useAuthStore } from "../auth/authStore";

vi.mock("./referralApi", () => ({
  getReferralConfig: vi.fn(),
  getReferralHistory: vi.fn(),
  getReferralSummary: vi.fn(),
  sendReferralInvitation: vi.fn(),
}));

describe("Refer & Earn", () => {
  beforeEach(() => {
    referralApi.getReferralConfig.mockResolvedValue({
      inviterReward: 125,
      newUserReward: 75,
      expiryDays: 10,
    });
    referralApi.getReferralHistory.mockResolvedValue({
      content: [{
        id: 1,
        invitedEmail: "friend@example.com",
        status: "REWARDED",
        createdAt: "2026-01-02T00:00:00Z",
      }],
    });
    referralApi.getReferralSummary.mockResolvedValue({
      totalInvitations: 3,
      pendingReferrals: 1,
      successfulReferrals: 2,
      totalCoinsEarned: 250,
    });
    useAuthStore.setState({
      status: "anonymous",
      user: null,
      register: vi.fn().mockResolvedValue({}),
    });
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("loads configured rewards and history, sends an invitation, and closes", async () => {
    referralApi.sendReferralInvitation.mockResolvedValue({});
    const user = userEvent.setup();
    const onClose = vi.fn();

    render(<ReferralModal open onClose={onClose} />);

    expect(await screen.findByText(/Earn 125 GeekCoins/)).toBeInTheDocument();
    expect(screen.getByText("Rewarded")).toBeInTheDocument();
    expect(screen.getByText("250")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Friend's email"), "new@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invitation" }));

    await waitFor(() => {
      expect(referralApi.sendReferralInvitation).toHaveBeenCalledWith("new@example.com");
    });
    expect(await screen.findByText("Invitation sent successfully.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Close Refer and Earn" }));
    expect(onClose).toHaveBeenCalledOnce();
  });

  it("shows invitation errors without hiding the modal", async () => {
    referralApi.sendReferralInvitation.mockRejectedValue(new Error("Duplicate invitation"));
    const user = userEvent.setup();

    render(<ReferralModal open onClose={vi.fn()} />);
    await user.type(screen.getByLabelText("Friend's email"), "friend@example.com");
    await user.click(screen.getByRole("button", { name: "Send Invitation" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Duplicate invitation");
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("prefills referral code and invited email from an invitation URL", () => {
    render(
      <MemoryRouter initialEntries={["/register?ref=PG-ABC12345&email=friend%40example.com"]}>
        <RegisterPage />
      </MemoryRouter>,
    );

    expect(screen.getByLabelText("Email")).toHaveValue("friend@example.com");
    expect(screen.getByLabelText("Have a referral code?")).toHaveValue("PG-ABC12345");
  });
});
