import { apiRequest } from "../../shared/api/client";

export const sendReferralInvitation = (email) =>
  apiRequest("referrals/invite", { method: "POST", body: { email } });

export const getReferralSummary = () => apiRequest("referrals/summary");

export const getReferralHistory = () =>
  apiRequest("referrals/history?page=0&size=100");

export const getReferralConfig = () => apiRequest("referrals/config");
