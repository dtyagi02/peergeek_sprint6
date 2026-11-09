import { Client } from "@stomp/stompjs";
import { useEffect, useRef, useState, useCallback } from "react";
import { useAuthStore } from "../auth/authStore";

/**
 * Valid STOMP connection states
 */
export const StompConnectionStatus = {
  CONNECTING: "connecting",
  CONNECTED: "connected",
  RECONNECTING: "reconnecting",
  DISCONNECTED: "disconnected",
};

/**
 * STOMP client using @stomp/stompjs over native WebSocket.
 * Configured for Spring Boot STOMP broker specifications.
 */
export class CommunityStompClient {
  constructor(urlProvider = null, tokenProvider = null) {
    this.urlProvider = urlProvider;
    this.tokenProvider = tokenProvider;
    this.status = StompConnectionStatus.DISCONNECTED;
    this.statusListeners = new Set();
    this.subscriptions = new Map(); // subId -> { destination, callback, onReconnect, hasConnectedBefore, stompSub }
    this.subIdCounter = 0;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.client = null;
    this.isManualRetrying = false;
  }

  setStatus(newStatus) {
    if (this.status !== newStatus) {
      this.status = newStatus;
      for (const listener of this.statusListeners) {
        try {
          listener(newStatus);
        } catch (e) {
          console.error("[STOMP] Status listener error:", e);
        }
      }
    }
  }

  addStatusListener(listener) {
    this.statusListeners.add(listener);
    try {
      listener(this.status);
    } catch {}
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  getWsUrl() {
    if (typeof this.urlProvider === "function") {
      return this.urlProvider();
    }
    if (typeof window === "undefined") {
      return "ws://localhost:8080/ws";
    }
    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
    const host = window.location.host;
    return `${protocol}//${host}/ws`;
  }

  getToken() {
    if (typeof this.tokenProvider === "function") {
      return this.tokenProvider();
    }
    const authState = useAuthStore.getState();
    return authState.accessToken || authState.token;
  }

  get isConnected() {
    return this.status === StompConnectionStatus.CONNECTED;
  }

  initClient() {
    if (this.client) return this.client;

    this.client = new Client({
      brokerURL: this.getWsUrl(),
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      reconnectDelay: 2000,
      beforeConnect: () => {
        const token = this.getToken();
        this.client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {};
      },
      onConnect: () => {
        this.reconnectAttempts = 0;
        this.isManualRetrying = false;
        this.setStatus(StompConnectionStatus.CONNECTED);
        // Resubscribe all active subscriptions
        for (const [subId, sub] of this.subscriptions.entries()) {
          this.doSubscribe(subId, sub);
        }
      },
      onDisconnect: () => {
        this.setStatus(StompConnectionStatus.DISCONNECTED);
      },
      onStompError: (frame) => {
        console.warn("[STOMP] Protocol error:", frame.headers?.message);
        this.handleDrop();
      },
      onWebSocketClose: () => {
        this.handleDrop();
      },
      onWebSocketError: (err) => {
        console.warn("[STOMP] WebSocket error:", err);
        this.handleDrop();
      },
    });

    return this.client;
  }

  handleDrop() {
    if (this.subscriptions.size > 0) {
      this.reconnectAttempts++;
      if (this.reconnectAttempts > this.maxReconnectAttempts) {
        this.setStatus(StompConnectionStatus.DISCONNECTED);
        if (this.client) {
          try {
            this.client.deactivate();
          } catch {}
        }
        return;
      }
      this.setStatus(StompConnectionStatus.RECONNECTING);
    } else {
      this.setStatus(StompConnectionStatus.DISCONNECTED);
    }
  }

  connect() {
    const token = this.getToken();
    if (!token) {
      this.setStatus(StompConnectionStatus.DISCONNECTED);
      return;
    }

    if (this.status === StompConnectionStatus.CONNECTED) {
      return;
    }

    const client = this.initClient();
    if (this.reconnectAttempts > 0) {
      this.setStatus(StompConnectionStatus.RECONNECTING);
    } else {
      this.setStatus(StompConnectionStatus.CONNECTING);
    }

    try {
      if (!client.active) {
        client.activate();
      }
    } catch {
      this.handleDrop();
    }
  }

  retry() {
    this.reconnectAttempts = 0;
    this.isManualRetrying = true;
    this.setStatus(StompConnectionStatus.CONNECTING);
    const client = this.initClient();
    try {
      if (client.active) {
        client.deactivate().then(() => {
          client.activate();
        }).catch(() => {
          client.activate();
        });
      } else {
        client.activate();
      }
    } catch {
      this.handleDrop();
    }
  }

  disconnect() {
    this.reconnectAttempts = 0;
    this.isManualRetrying = false;
    for (const sub of this.subscriptions.values()) {
      if (sub.stompSub) {
        try {
          sub.stompSub.unsubscribe();
        } catch {}
        sub.stompSub = null;
      }
    }
    if (this.client && this.client.active) {
      try {
        this.client.deactivate();
      } catch {}
    }
    this.setStatus(StompConnectionStatus.DISCONNECTED);
  }

  subscribe(destination, callback, onReconnect) {
    const subId = `sub-${++this.subIdCounter}`;
    const subRecord = {
      destination,
      callback,
      onReconnect,
      hasConnectedBefore: false,
      stompSub: null,
    };
    this.subscriptions.set(subId, subRecord);

    if (this.status === StompConnectionStatus.CONNECTED) {
      this.doSubscribe(subId, subRecord);
    } else {
      this.connect();
    }

    return () => {
      const sub = this.subscriptions.get(subId);
      if (sub?.stompSub) {
        try {
          sub.stompSub.unsubscribe();
        } catch {}
      }
      this.subscriptions.delete(subId);
      if (this.subscriptions.size === 0) {
        this.disconnect();
      }
    };
  }

  doSubscribe(subId, subRecord) {
    if (!this.client || !this.client.connected) return;

    if (subRecord.stompSub) {
      try {
        subRecord.stompSub.unsubscribe();
      } catch {}
    }

    try {
      subRecord.stompSub = this.client.subscribe(subRecord.destination, (message) => {
        let data = message.body;
        try {
          data = JSON.parse(message.body);
        } catch {}
        if (typeof subRecord.callback === "function") {
          subRecord.callback(data, message.headers);
        }
      });

      if (subRecord.hasConnectedBefore && typeof subRecord.onReconnect === "function") {
        try {
          subRecord.onReconnect();
        } catch (err) {
          console.error("[STOMP] onReconnect callback error:", err);
        }
      }
      subRecord.hasConnectedBefore = true;
    } catch (err) {
      console.warn("[STOMP] subscribe error:", err);
    }
  }

  send(destination, payload, headers = {}) {
    if (!this.client || !this.client.connected) {
      throw new Error("STOMP client is not connected");
    }
    const body = typeof payload === "string" ? payload : JSON.stringify(payload);
    this.client.publish({
      destination,
      body,
      headers: {
        "content-type": "application/json",
        ...headers,
      },
    });
  }
}

// Global shared client instance
let sharedClientInstance = null;

export function getCommunityStompClient() {
  if (!sharedClientInstance) {
    sharedClientInstance = new CommunityStompClient(
      null,
      () => useAuthStore.getState().accessToken || useAuthStore.getState().token
    );
  }
  return sharedClientInstance;
}

/**
 * React hook for subscribing to a STOMP topic with automatic reconnect and message recovery.
 */
export function useCommunityStompSubscription({
  destination,
  onMessage,
  onReconnect,
  enabled = true,
}) {
  const client = getCommunityStompClient();
  const [status, setStatus] = useState(() => (enabled ? client.status : StompConnectionStatus.DISCONNECTED));

  const onMessageRef = useRef(onMessage);
  const onReconnectRef = useRef(onReconnect);
  onMessageRef.current = onMessage;
  onReconnectRef.current = onReconnect;

  useEffect(() => {
    if (!enabled) {
      setStatus(StompConnectionStatus.DISCONNECTED);
      return;
    }

    const unsub = client.addStatusListener((newStatus) => {
      setStatus(newStatus);
    });

    return () => {
      unsub();
    };
  }, [client, enabled]);

  useEffect(() => {
    if (!enabled || !destination) return;

    const unsubscribe = client.subscribe(
      destination,
      (data, headers) => {
        if (onMessageRef.current) {
          onMessageRef.current(data, headers);
        }
      },
      () => {
        if (onReconnectRef.current) {
          onReconnectRef.current();
        }
      }
    );

    return () => {
      unsubscribe();
    };
  }, [client, destination, enabled]);

  const retry = useCallback(() => {
    client.retry();
  }, [client]);

  return {
    status,
    isConnected: status === StompConnectionStatus.CONNECTED,
    retry,
  };
}

/**
 * React hook to listen to STOMP connection status and trigger retry.
 */
export function useCommunityStompStatus() {
  const client = getCommunityStompClient();
  const [status, setStatus] = useState(() => client.status);

  useEffect(() => {
    return client.addStatusListener(setStatus);
  }, [client]);

  const retry = useCallback(() => {
    client.retry();
  }, [client]);

  return {
    status,
    isConnected: status === StompConnectionStatus.CONNECTED,
    retry,
  };
}
