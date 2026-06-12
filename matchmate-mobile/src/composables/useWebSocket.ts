import { ref } from 'vue';
import type { WsPushPayload } from '../models/chat';

const HEARTBEAT_INTERVAL = 60_000;

let ws: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let manualClose = false;
const listeners = new Set<(payload: WsPushPayload) => void>();
const connected = ref(false);
let subscriberCount = 0;

const clearHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
};

const getWebSocketUrl = () => {
  const configuredBase = import.meta.env.VITE_WS_BASE_URL?.replace(/\/$/, '');
  if (configuredBase) return `${configuredBase}/api/ws/chat`;

  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
  return `${protocol}//${location.host}/api/ws/chat`;
};

const doConnect = () => {
  if (ws && (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING)) return;

  manualClose = false;
  ws = new WebSocket(getWebSocketUrl());

  ws.onopen = () => {
    connected.value = true;
    clearHeartbeat();
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) ws.send('ping');
    }, HEARTBEAT_INTERVAL);
  };

  ws.onmessage = (event: MessageEvent) => {
    try {
      const payload: WsPushPayload = JSON.parse(event.data);
      listeners.forEach((listener) => listener(payload));
    } catch {
      // Ignore malformed server messages.
    }
  };

  ws.onclose = () => {
    connected.value = false;
    clearHeartbeat();
    ws = null;
    if (!manualClose) reconnectTimer = setTimeout(doConnect, 3000);
  };

  ws.onerror = () => ws?.close();
};

const doDisconnect = () => {
  manualClose = true;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  clearHeartbeat();
  ws?.close();
  ws = null;
  connected.value = false;
};

export const useWebSocket = () => {
  const connect = () => {
    subscriberCount++;
    if (subscriberCount === 1) doConnect();
  };

  const disconnect = () => {
    subscriberCount--;
    if (subscriberCount <= 0) {
      subscriberCount = 0;
      doDisconnect();
    }
  };

  const onMessage = (callback: (payload: WsPushPayload) => void) => {
    listeners.add(callback);
    return () => listeners.delete(callback);
  };

  return { connect, disconnect, connected, onMessage };
};
