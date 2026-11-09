import { useEffect, useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import {
  Link,
  Navigate,
  useLocation,
  useNavigate,
  useSearchParams,
} from "react-router-dom";
import { z } from "zod";
import { ApiError } from "../../shared/api/ApiError";
import { Button } from "../../shared/components/Button";
import { FormField } from "../../shared/components/FormField";
import { useAuthStore } from "./authStore";
import {
  applyApiFieldErrors,
  safeReturnPath,
  validateForm,
} from "./zodValidation";
import {
  forgotPasswordRequest,
  resendVerificationOtpRequest,
  resetPasswordRequest,
  verifyEmailRequest,
  verifyResetOtpRequest,
} from "./authApi";

const emailField = z.string().trim().email("Enter a valid email address");
const loginSchema = z.object({
  email: emailField,
  password: z.string().min(8, "Password must be at least 8 characters"),
});
const RESERVED_USERNAMES = new Set([
  "admin",
  "administrator",
  "support",
  "system",
  "api",
  "root",
]);

const usernameField = z
  .string()
  .trim()
  .superRefine((val, ctx) => {
    if (!val) {
      ctx.addIssue({
        code: "custom",
        message: "Username is required",
      });
      return;
    }
    if (val.length < 3) {
      ctx.addIssue({
        code: "custom",
        message: "Username must be at least 3 characters",
      });
      return;
    }
    if (val.length > 30) {
      ctx.addIssue({
        code: "custom",
        message: "Username must be at most 30 characters",
      });
      return;
    }
    if (!/^[a-zA-Z]/.test(val)) {
      ctx.addIssue({
        code: "custom",
        message: "Username must start with a letter",
      });
      return;
    }
    if (!/^[a-zA-Z0-9_]+$/.test(val)) {
      ctx.addIssue({
        code: "custom",
        message: "Username can only contain letters, numbers, and underscores",
      });
      return;
    }
    if (RESERVED_USERNAMES.has(val.toLowerCase())) {
      ctx.addIssue({
        code: "custom",
        message: "This username is reserved. Please choose another username.",
      });
    }
  });

const registerSchema = z
  .object({
    displayName: z
      .string()
      .trim()
      .min(1, "Display name is required")
      .max(100, "Display name must be at most 100 characters"),
    username: usernameField,
    email: emailField,
    password: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .max(72, "Password must be at most 72 characters"),
    referralCode: z.string().trim().max(64, "Referral code is too long").optional(),
  })
  .superRefine((values, context) => {
    if (
      values.email &&
      values.password &&
      values.password.toLowerCase() === values.email.toLowerCase()
    ) {
      context.addIssue({
        code: "custom",
        path: ["password"],
        message: "Password must not be the same as your email address",
      });
    }
  });
const forgotSchema = z.object({ email: emailField });
const resetSchema = z
  .object({
    email: emailField,
    otp: z.string().regex(/^\d{6}$/, "Enter the six-digit code"),
    newPassword: z
      .string()
      .min(8, "Password must be at least 8 characters")
      .max(72, "Password must be at most 72 characters"),
  })
  .superRefine((values, context) => {
    if (
      values.email &&
      values.newPassword &&
      values.newPassword.toLowerCase() === values.email.toLowerCase()
    ) {
      context.addIssue({
        code: "custom",
        path: ["newPassword"],
        message: "Password must not be the same as your email address",
      });
    }
  });
const verifyEmailSchema = z.object({
  email: emailField,
  otp: z.string().regex(/^\d{6}$/, "Enter the six-digit code"),
});

function FieldForm({ title, description, children, onSubmit, form, message }) {
  return (
    <section aria-labelledby="auth-title" className="mx-auto max-w-md">
      <h1
        className="text-3xl font-bold tracking-tight text-ink-900"
        id="auth-title"
      >
        {title}
      </h1>
      {description && <p className="mt-2 text-ink-700">{description}</p>}
      {message && (
        <p aria-live="polite" className="mt-4 text-danger-600" role="alert">
          {message}
        </p>
      )}
      <FormProvider {...form}>
        <form className="mt-6 grid gap-5" noValidate onSubmit={onSubmit}>
          {children}
        </form>
      </FormProvider>
    </section>
  );
}

function useApiForm(schema, defaultValues) {
  const form = useForm({ defaultValues });
  const [message, setMessage] = useState("");
  const beginSubmit = async (values, action) => {
    setMessage("");
    form.clearErrors();
    const parsed = validateForm(schema, values, form.setError);
    if (!parsed) return;
    try {
      await action(parsed);
    } catch (error) {
      if (error instanceof ApiError) {
        applyApiFieldErrors(error, form.setError);
        setMessage(error.message);
      } else {
        setMessage("The request could not be completed. Please try again.");
      }
    }
  };
  return { form, message, setMessage, beginSubmit };
}

export function GuestRoute({ children }) {
  const status = useAuthStore((state) => state.status);
  if (status === "authenticated") return <Navigate replace to="/" />;
  return children;
}

export function LoginPage() {
  const { form, message, beginSubmit } = useApiForm(loginSchema, {
    email: "",
    password: "",
  });
  const [unverifiedEmail, setUnverifiedEmail] = useState("");
  const login = useAuthStore((state) => state.login);
  const navigate = useNavigate();
  const location = useLocation();
  const [searchParams] = useSearchParams();
  const returnTo = safeReturnPath(
    location.state?.from
      ? `${location.state.from.pathname}${location.state.from.search}${location.state.from.hash}`
      : searchParams.get("returnTo"),
  );

  const submit = form.handleSubmit((values) =>
    beginSubmit(values, async (valid) => {
      setUnverifiedEmail("");
      try {
        await login(valid);
        navigate(returnTo, { replace: true });
      } catch (error) {
        if (error instanceof ApiError && error.code === "EMAIL_NOT_VERIFIED") {
          setUnverifiedEmail(valid.email);
        }
        throw error;
      }
    }),
  );

  return (
    <FieldForm
      description="Sign in to continue to PeerGeek."
      form={form}
      message={message}
      onSubmit={submit}
      title="Log in"
    >
      <FormField autoComplete="email" label="Email" name="email" type="email" />
      <FormField
        autoComplete="current-password"
        label="Password"
        name="password"
        type="password"
      />
      {unverifiedEmail && (
        <div
          aria-live="polite"
          className="rounded-lg border border-amber-300 bg-amber-50 p-4 text-sm text-amber-900"
          role="alert"
        >
          <p className="font-semibold">Email verification required</p>
          <p className="mt-1">
            Please verify your email address before logging in.
          </p>
          <Link
            className="mt-2 inline-block font-semibold text-brand-700 underline"
            to={`/verify-email?email=${encodeURIComponent(unverifiedEmail)}`}
          >
            Verify your email &rarr;
          </Link>
        </div>
      )}
      <Button type="submit">Log in</Button>
      <p className="text-sm text-ink-700">
        <Link className="text-brand-700 underline" to="/forgot-password">
          Forgot your password?
        </Link>
      </p>
      <p className="text-sm text-ink-700">
        New to PeerGeek?{" "}
        <Link className="text-brand-700 underline" to="/register">
          Create an account
        </Link>
      </p>
    </FieldForm>
  );
}

export function RegisterPage() {
  const [searchParams] = useSearchParams();
  const { form, message, beginSubmit } = useApiForm(registerSchema, {
    displayName: "",
    username: "",
    email: searchParams.get("email") || "",
    password: "",
    referralCode: searchParams.get("ref") || "",
  });
  const register = useAuthStore((state) => state.register);
  const navigate = useNavigate();
  const submit = form.handleSubmit((values) =>
    beginSubmit(values, async (valid) => {
      await register({
        ...valid,
        username: valid.username.trim().toLowerCase(),
      });
      navigate(`/verify-email?email=${encodeURIComponent(valid.email)}&new=true`, {
        replace: true,
      });
    }),
  );

  return (
    <FieldForm
      description="Create one account to learn and teach."
      form={form}
      message={message}
      onSubmit={submit}
      title="Create your account"
    >
      <FormField
        autoComplete="name"
        label="Display name"
        name="displayName"
        type="text"
      />
      <FormField
        autoComplete="username"
        description="3–30 characters, letters, numbers, and underscores. Must start with a letter."
        label="Username"
        name="username"
        type="text"
      />
      <FormField autoComplete="email" label="Email" name="email" type="email" />
      <FormField
        autoComplete="new-password"
        label="Password"
        name="password"
        type="password"
      />
      <FormField
        description="Optional. Referral codes expire after seven days and are tied to the invited email."
        label="Have a referral code?"
        name="referralCode"
        type="text"
      />
      <Button disabled={form.formState.isSubmitting} type="submit">
        {form.formState.isSubmitting ? "Creating account…" : "Create account"}
      </Button>
      <p className="text-sm text-ink-700">
        Already have an account?{" "}
        <Link className="text-brand-700 underline" to="/login">
          Log in
        </Link>
      </p>
    </FieldForm>
  );
}

export function VerifyEmailPage() {
  const [searchParams] = useSearchParams();
  const emailParam = searchParams.get("email") || "";
  const isNew = searchParams.get("new") === "true";
  const { form, message, setMessage, beginSubmit } = useApiForm(
    verifyEmailSchema,
    {
      email: emailParam,
      otp: "",
    },
  );

  const [resendCooldown, setResendCooldown] = useState(60);
  const [resendStatus, setResendStatus] = useState(
    isNew ? "A verification code has been sent to your email." : "",
  );
  const [resendLoading, setResendLoading] = useState(false);
  const [verified, setVerified] = useState(false);
  const [expirySeconds, setExpirySeconds] = useState(600);

  useEffect(() => {
    if (resendCooldown <= 0) return;
    const timer = setInterval(() => {
      setResendCooldown((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [resendCooldown]);

  useEffect(() => {
    if (expirySeconds <= 0) return;
    const timer = setInterval(() => {
      setExpirySeconds((prev) => (prev > 0 ? prev - 1 : 0));
    }, 1000);
    return () => clearInterval(timer);
  }, [expirySeconds]);

  const handleResend = async () => {
    const email = form.getValues("email")?.trim();
    if (!email) {
      form.setError("email", {
        message: "Enter an email address to resend the code",
      });
      return;
    }
    setMessage("");
    setResendStatus("");
    setResendLoading(true);
    try {
      await resendVerificationOtpRequest({ email });
      setResendStatus("A new verification code has been sent to your email.");
      setResendCooldown(60);
      setExpirySeconds(600);
    } catch (error) {
      if (error instanceof ApiError) {
        applyApiFieldErrors(error, form.setError);
        setMessage(error.message);
      } else {
        setMessage("Could not resend verification code. Please try again.");
      }
    } finally {
      setResendLoading(false);
    }
  };

  const submit = form.handleSubmit((values) =>
    beginSubmit(values, async (valid) => {
      await verifyEmailRequest(valid);
      setVerified(true);
      setMessage("");
    }),
  );

  const formatExpiry = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? "0" : ""}${secs}`;
  };

  if (verified) {
    return (
      <section
        aria-live="polite"
        className="mx-auto max-w-md text-center"
        role="status"
      >
        <h1
          className="text-3xl font-bold tracking-tight text-ink-900"
          id="auth-title"
        >
          Email verified!
        </h1>
        <p className="mt-3 text-ink-700">
          Your account is now active and ready. You can log in with your
          credentials.
        </p>
        <div className="mt-6">
          <Link
            className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
            to="/login"
          >
            Continue to log in
          </Link>
        </div>
      </section>
    );
  }

  return (
    <FieldForm
      description="Enter the six-digit code sent to your email to activate your account."
      form={form}
      message={message}
      onSubmit={submit}
      title="Verify your email"
    >
      <FormField autoComplete="email" label="Email" name="email" type="email" />
      <FormField
        autoComplete="one-time-code"
        inputMode="numeric"
        label="Six-digit code"
        maxLength={6}
        name="otp"
        type="text"
      />

      <div className="flex items-center justify-between text-xs text-ink-600">
        <span>
          {expirySeconds > 0
            ? `Code expires in ${formatExpiry(expirySeconds)}`
            : "Code has expired. Request a new one."}
        </span>
        <button
          className="font-medium text-brand-700 hover:text-brand-600 disabled:cursor-not-allowed disabled:text-ink-400 disabled:hover:text-ink-400"
          disabled={resendCooldown > 0 || resendLoading}
          onClick={handleResend}
          type="button"
        >
          {resendCooldown > 0
            ? `Resend code in ${resendCooldown}s`
            : resendLoading
              ? "Sending..."
              : "Resend code"}
        </button>
      </div>

      {resendStatus && (
        <p aria-live="polite" className="text-xs text-emerald-600">
          {resendStatus}
        </p>
      )}

      <Button type="submit">Verify email</Button>

      <p className="text-sm text-ink-700">
        Already verified?{" "}
        <Link className="text-brand-700 underline" to="/login">
          Log in
        </Link>
      </p>
    </FieldForm>
  );
}

export function ForgotPasswordPage() {
  const { form, message, setMessage, beginSubmit } = useApiForm(forgotSchema, {
    email: "",
  });
  const [sent, setSent] = useState(false);
  const submit = form.handleSubmit((values) =>
    beginSubmit(values, async (valid) => {
      await forgotPasswordRequest(valid);
      setSent(true);
      setMessage("");
    }),
  );

  if (sent) {
    return (
      <section aria-live="polite" className="mx-auto max-w-md" role="status">
        <h1 className="text-3xl font-bold text-ink-900">Check your email</h1>
        <p className="mt-3 text-ink-700">
          If an account exists for that email, password reset instructions will
          be sent.
        </p>
        <Link
          className="mt-5 inline-block text-brand-700 underline"
          to={`/reset-password?email=${encodeURIComponent(form.getValues("email") || "")}`}
        >
          Enter your reset code
        </Link>
      </section>
    );
  }

  return (
    <FieldForm
      description="We will send a code if an account exists for the email."
      form={form}
      message={message}
      onSubmit={submit}
      title="Reset your password"
    >
      <FormField autoComplete="email" label="Email" name="email" type="email" />
      <Button type="submit">Send reset code</Button>
      <Link className="text-sm text-brand-700 underline" to="/login">
        Back to log in
      </Link>
    </FieldForm>
  );
}

export function ResetPasswordPage() {
  const [searchParams] = useSearchParams();
  const emailParam = searchParams.get("email") || "";
  const { form, message, setMessage, beginSubmit } = useApiForm(resetSchema, {
    email: emailParam,
    otp: "",
    newPassword: "",
  });
  const [resetSuccess, setResetSuccess] = useState(false);

  const submit = form.handleSubmit((values) =>
    beginSubmit(values, async (valid) => {
      // Step 1: Verify OTP and exchange for single-use SHA-256 resetToken
      const verifyRes = await verifyResetOtpRequest({
        email: valid.email,
        otp: valid.otp,
      });

      // Step 2: Use resetToken in memory to update password
      await resetPasswordRequest({
        email: valid.email,
        resetToken: verifyRes.resetToken,
        newPassword: valid.newPassword,
      });

      setResetSuccess(true);
      setMessage("");
    }),
  );

  if (resetSuccess) {
    return (
      <section
        aria-live="polite"
        className="mx-auto max-w-md text-center"
        role="status"
      >
        <h1 className="text-3xl font-bold text-ink-900" id="auth-title">
          Password updated!
        </h1>
        <p className="mt-3 text-ink-700">
          Your password has been reset successfully. You can now log in with your
          new password.
        </p>
        <div className="mt-6">
          <Link
            className="inline-flex items-center justify-center rounded-lg bg-brand-600 px-5 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500"
            to="/login"
          >
            Proceed to log in
          </Link>
        </div>
      </section>
    );
  }

  return (
    <FieldForm
      description="Enter the six-digit code from your email and choose a new password."
      form={form}
      message={message}
      onSubmit={submit}
      title="Choose a new password"
    >
      <FormField autoComplete="email" label="Email" name="email" type="email" />
      <FormField
        autoComplete="one-time-code"
        inputMode="numeric"
        label="Six-digit code"
        maxLength={6}
        name="otp"
        type="text"
      />
      <FormField
        autoComplete="new-password"
        label="New password"
        name="newPassword"
        type="password"
      />
      <Button type="submit">Update password</Button>
      <p className="text-sm text-ink-700">
        Didn't receive a code?{" "}
        <Link className="text-brand-700 underline" to="/forgot-password">
          Request a new code
        </Link>
      </p>
    </FieldForm>
  );
}
