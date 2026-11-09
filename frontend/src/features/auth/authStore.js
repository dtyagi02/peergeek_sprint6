import { create } from "zustand";
import {
  logoutRequest,
  loginRequest,
  refreshRequest,
  registerRequest,
} from "./authApi";
import {
  setAuthExpiredHandler,
  setAuthRecoveryHandler,
  setAuthTokenProvider,
} from "../../shared/api/client";

let refreshFlight;

export const useAuthStore = create((set, get) => ({
  user: null,
  accessToken: null,
  status: "unknown",

  setSession(session) {
    set({
      user: session.user,
      accessToken: session.accessToken,
      status: "authenticated",
    });
  },

  async login(values) {
    const session = await loginRequest(values);
    get().setSession(session);
    return session;
  },

  async register(values) {
    return await registerRequest(values);
  },

  async restoreSession() {
    if (get().status !== "unknown") return get().status === "authenticated";
    return restoreSession();
  },

  async recoverSession() {
    return restoreSession();
  },

  async logout() {
    try {
      await logoutRequest();
    } finally {
      set({ user: null, accessToken: null, status: "anonymous" });
    }
  },

  setMode(lastMode) {
    set((state) => ({
      user: state.user ? { ...state.user, lastMode } : null,
    }));
  },

  clearSession() {
    set({ user: null, accessToken: null, status: "anonymous" });
  },
}));

async function restoreSession() {
  if (!refreshFlight) {
    refreshFlight = refreshRequest()
      .then((session) => {
        useAuthStore.getState().setSession(session);
        return true;
      })
      .catch(() => {
        useAuthStore.getState().clearSession();
        return false;
      })
      .finally(() => {
        refreshFlight = undefined;
      });
  }
  return refreshFlight;
}

setAuthTokenProvider(async () => useAuthStore.getState().accessToken);
setAuthRecoveryHandler(async () => useAuthStore.getState().recoverSession());
setAuthExpiredHandler(() => useAuthStore.getState().clearSession());
