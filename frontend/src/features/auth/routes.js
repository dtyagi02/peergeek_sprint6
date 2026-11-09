import { createElement } from "react";
import {
  ForgotPasswordPage,
  GuestRoute,
  LoginPage,
  RegisterPage,
  ResetPasswordPage,
  VerifyEmailPage,
} from "./AuthPages";

export default [
  {
    path: "/login",
    element: createElement(GuestRoute, null, createElement(LoginPage)),
    visibility: "public",
  },
  {
    path: "/register",
    element: createElement(GuestRoute, null, createElement(RegisterPage)),
    visibility: "public",
  },
  {
    path: "/verify-email",
    element: createElement(GuestRoute, null, createElement(VerifyEmailPage)),
    visibility: "public",
  },
  {
    path: "/forgot-password",
    element: createElement(GuestRoute, null, createElement(ForgotPasswordPage)),
    visibility: "public",
  },
  {
    path: "/reset-password",
    element: createElement(GuestRoute, null, createElement(ResetPasswordPage)),
    visibility: "public",
  },
];
