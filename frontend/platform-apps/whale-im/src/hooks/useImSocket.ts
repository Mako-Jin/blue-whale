// WS 生命周期：挂载即建连，卸载即关闭；帧交给 useImData 处理

import { useEffect, useRef, useState } from 'react';
import { imApi } from '../api/im';
import { ImSocket, type ImFrame, type SocketState } from '../ws/client';

export function useImSocket(
  handleFrame: (frame: ImFrame) => void,
  refreshAfterReconnect: () => Promise<void>,
): SocketState {
  const [state, setState] = useState<SocketState>('connecting');
  const socketRef = useRef<ImSocket | null>(null);

  useEffect(() => {
    const socket = new ImSocket({
      getTicket: async () => (await imApi.wsTicket()).ticket,
      onFrame: handleFrame,
      onStateChange: setState,
      onReconnect: () => {
        void refreshAfterReconnect();
      },
    });
    socketRef.current = socket;
    socket.connect();
    return () => {
      socket.close();
      socketRef.current = null;
    };
  }, [handleFrame, refreshAfterReconnect]);

  return state;
}
