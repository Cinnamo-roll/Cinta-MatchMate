import { ref } from 'vue';
import type { CardWsPayload } from '../models/card';

const HEARTBEAT_INTERVAL = 60_000;

let ws: WebSocket | null = null;
let reconnectTimer: ReturnType<typeof setTimeout> | null = null;
let heartbeatTimer: ReturnType<typeof setInterval> | null = null;
let manualClose = false;
let currentRoomId: number | null = null;

const listeners = new Set<(payload: CardWsPayload) => void>();
const connected = ref(false);

const clearHeartbeat = () => {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
};

const getWsUrl = (roomId: number) => {
  const base = import.meta.env.VITE_WS_BASE_URL?.replace(/\/$/, '') || '';
  const prefix = base || `${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}`;
  return `${prefix}/api/ws/card/${roomId}`;
};

const doConnect = (roomId: number) => {
  if (ws && ws.readyState === WebSocket.OPEN) {
    // 如果已经是同一个房间，不重连
    if (currentRoomId === roomId) return;
    // 房间变了，断开旧连接
    doDisconnect();
  }
  if (ws && ws.readyState === WebSocket.CONNECTING) return;

  manualClose = false;
  currentRoomId = roomId;
  ws = new WebSocket(getWsUrl(roomId));

  ws.onopen = () => {
    connected.value = true;
    clearHeartbeat();
    heartbeatTimer = setInterval(() => {
      if (ws?.readyState === WebSocket.OPEN) ws.send('ping');
    }, HEARTBEAT_INTERVAL);
  };

  ws.onmessage = (event: MessageEvent) => {
    try {
      const payload: CardWsPayload = JSON.parse(event.data);
      listeners.forEach((fn) => fn(payload));
    } catch {
      // ignore
    }
  };

  ws.onclose = () => {
    connected.value = false;
    clearHeartbeat();
    ws = null;
    if (!manualClose && currentRoomId != null) {
      reconnectTimer = setTimeout(() => doConnect(currentRoomId!), 3000);
    }
  };

  ws.onerror = () => ws?.close();
};

const doDisconnect = () => {
  manualClose = true;
  currentRoomId = null;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
  clearHeartbeat();
  ws?.close();
  ws = null;
  connected.value = false;
};

export const useCardWebSocket = () => {
  const connect = (roomId: number) => {
    doConnect(roomId);
  };

  const disconnect = () => {
    doDisconnect();
  };

  const onMessage = (callback: (payload: CardWsPayload) => void) => {
    listeners.add(callback);
    return () => {
      listeners.delete(callback);
    };
  };

  return { connect, disconnect, connected, onMessage };
};
