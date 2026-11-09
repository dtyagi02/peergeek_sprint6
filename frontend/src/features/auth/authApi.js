import { apiRequest } from "../../shared/api/client";

export const loginRequest = (values) =>
  apiRequest("auth/login", { method: "POST", body: values });

export const registerRequest = (values) =>
  apiRequest("auth/register", { method: "POST", body: values });

export const verifyEmailRequest = (values) =>
  apiRequest("auth/verify-email", { method: "POST", body: values });

export const resendVerificationOtpRequest = (values) =>
  apiRequest("auth/resend-verification-otp", { method: "POST", body: values });

export const refreshRequest = () =>
  apiRequest("auth/refresh", { method: "POST" });

export const logoutRequest = () =>
  apiRequest("auth/logout", { method: "POST" });

export const forgotPasswordRequest = (values) =>
  apiRequest("auth/forgot-password", { method: "POST", body: values });

export const verifyResetOtpRequest = (values) =>
  apiRequest("auth/verify-reset-otp", { method: "POST", body: values });

export const resetPasswordRequest = (values) =>
  apiRequest("auth/reset-password", { method: "POST", body: values });
