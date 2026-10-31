// 播放器全局状态：播放队列、当前曲目、进度、音量与歌词
// 音频直连 /music/stream/{songId}?ticket=...（浏览器自动发起 Range 请求），
// 播放期间按 15 秒间隔上报进度，切歌 / 暂停 / 播放结束时再次上报。

import { App } from 'antd';
import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { reportPlayProgress, requestPlayTicket } from '../api/music';

/** 可播放曲目（用户侧歌曲视图的子集） */
export interface PlayableTrack {
  id: number;
  title: string;
  subtitle?: string;
  artistId?: number;
  coverFileId?: number;
  durationMs?: number;
}

/** 播放器上下文 */
interface PlayerContextValue {
  /** 当前播放队列 */
  queue: PlayableTrack[];
  /** 当前曲目 */
  current: PlayableTrack | null;
  /** 是否正在播放 */
  playing: boolean;
  /** 当前进度（秒） */
  currentTime: number;
  /** 当前曲目总时长（秒） */
  duration: number;
  /** 音量（0-1） */
  volume: number;
  /** 歌词原文（LRC 或纯文本） */
  lyricText: string | null;
  /** 播放指定曲目；传入队列时以队列播放（默认单曲队列） */
  play: (track: PlayableTrack, queue?: PlayableTrack[]) => void;
  /** 追加曲目到播放队列（队列为空时直接开始播放） */
  enqueue: (track: PlayableTrack) => void;
  /** 播放 / 暂停 */
  toggle: () => void;
  /** 下一首 */
  next: () => void;
  /** 上一首 */
  prev: () => void;
  /** 跳转到指定秒数 */
  seek: (seconds: number) => void;
  /** 设置音量（0-1） */
  setVolume: (volume: number) => void;
}

/** 进度上报间隔（毫秒） */
const REPORT_INTERVAL_MS = 15000;

const PlayerContext = createContext<PlayerContextValue | null>(null);

/** 播放器状态 Provider */
export function PlayerProvider({ children }: { children: ReactNode }) {
  const { message } = App.useApp();
  const [queue, setQueue] = useState<PlayableTrack[]>([]);
  const [index, setIndex] = useState(-1);
  const [playing, setPlaying] = useState(false);
  const [currentTime, setCurrentTime] = useState(0);
  const [duration, setDuration] = useState(0);
  const [volume, setVolumeState] = useState(1);
  const [lyricText, setLyricText] = useState<string | null>(null);

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const loadedTrackRef = useRef<number | null>(null);
  const currentRef = useRef<PlayableTrack | null>(null);

  const current = index >= 0 ? queue[index] ?? null : null;

  useEffect(() => {
    currentRef.current = current;
  }, [current]);

  /** 上报进度（失败静默，不打断播放） */
  const report = useCallback((songId: number, progressMs: number) => {
    if (!Number.isFinite(progressMs) || progressMs <= 0) {
      return;
    }
    reportPlayProgress({ songId, progressMs, source: 'MY', device: 'WEB' }).catch(() => undefined);
  }, []);

  // 音频实例：仅创建一次，组件卸载时释放
  useEffect(() => {
    const audio = new Audio();
    audio.preload = 'metadata';
    audioRef.current = audio;
    const onTimeUpdate = () => setCurrentTime(audio.currentTime);
    const onLoadedMetadata = () => setDuration(Number.isFinite(audio.duration) ? audio.duration : 0);
    const onPlay = () => setPlaying(true);
    const onPause = () => setPlaying(false);
    audio.addEventListener('timeupdate', onTimeUpdate);
    audio.addEventListener('loadedmetadata', onLoadedMetadata);
    audio.addEventListener('play', onPlay);
    audio.addEventListener('pause', onPause);
    return () => {
      audio.removeEventListener('timeupdate', onTimeUpdate);
      audio.removeEventListener('loadedmetadata', onLoadedMetadata);
      audio.removeEventListener('play', onPlay);
      audio.removeEventListener('pause', onPause);
      audio.pause();
      audioRef.current = null;
    };
  }, []);

  // 切歌：申请票据 → 设置流地址（浏览器按需发起 Range 请求）
  useEffect(() => {
    if (!current) {
      return;
    }
    const audio = audioRef.current;
    if (!audio) {
      return;
    }
    if (loadedTrackRef.current === current.id) {
      return;
    }
    let cancelled = false;
    loadedTrackRef.current = current.id;
    setLyricText(null);
    setCurrentTime(0);
    setDuration(current.durationMs ? current.durationMs / 1000 : 0);
    requestPlayTicket(current.id)
      .then((ticket) => {
        if (cancelled || !audioRef.current) {
          return;
        }
        setLyricText(ticket.lyricText ?? null);
        audio.src = ticket.streamUrl;
        audio.volume = volume;
        return audio.play();
      })
      .catch((error) => {
        if (cancelled) {
          return;
        }
        loadedTrackRef.current = null;
        setPlaying(false);
        message.error(error instanceof Error ? error.message : '播放失败，请稍后重试');
      });
    return () => {
      cancelled = true;
    };
  }, [current, message, volume]);

  // 播放期间定时上报进度
  useEffect(() => {
    if (!current) {
      return;
    }
    const songId = current.id;
    const timer = window.setInterval(() => {
      const audio = audioRef.current;
      if (!audio || audio.paused) {
        return;
      }
      report(songId, Math.round(audio.currentTime * 1000));
    }, REPORT_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [current, report]);

  // 暂停 / 结束 / 组件卸载时补报一次进度
  useEffect(() => {
    if (!current) {
      return;
    }
    const songId = current.id;
    const audio = audioRef.current;
    if (!audio) {
      return;
    }
    const onPause = () => report(songId, Math.round(audio.currentTime * 1000));
    audio.addEventListener('pause', onPause);
    return () => {
      audio.removeEventListener('pause', onPause);
      report(songId, Math.round(audio.currentTime * 1000));
    };
  }, [current, report]);

  const play = useCallback((track: PlayableTrack, list?: PlayableTrack[]) => {
    const nextQueue = list && list.length ? list : [track];
    const nextIndex = Math.max(nextQueue.findIndex((item) => item.id === track.id), 0);
    setQueue(nextQueue);
    setIndex(nextIndex);
    const audio = audioRef.current;
    if (!audio) {
      return;
    }
    if (loadedTrackRef.current === track.id && audio.src) {
      audio.play().catch(() => setPlaying(false));
    }
  }, []);

  const enqueue = useCallback((track: PlayableTrack) => {
    setQueue((list) => {
      if (!list.length) {
        return [track];
      }
      if (list.some((item) => item.id === track.id)) {
        return list;
      }
      return [...list, track];
    });
    setIndex((value) => (value < 0 ? 0 : value));
  }, []);

  const toggle = useCallback(() => {
    const audio = audioRef.current;
    if (!audio || !audio.src) {
      return;
    }
    if (audio.paused) {
      audio.play().catch(() => setPlaying(false));
    } else {
      audio.pause();
    }
  }, []);

  const next = useCallback(() => {
    setIndex((value) => {
      if (value < 0 || value + 1 >= queue.length) {
        const audio = audioRef.current;
        if (audio) {
          audio.pause();
        }
        return value;
      }
      return value + 1;
    });
  }, [queue.length]);

  const prev = useCallback(() => {
    setIndex((value) => (value > 0 ? value - 1 : value));
  }, []);

  const seek = useCallback((seconds: number) => {
    const audio = audioRef.current;
    if (!audio) {
      return;
    }
    audio.currentTime = Math.max(seconds, 0);
    setCurrentTime(audio.currentTime);
  }, []);

  const setVolume = useCallback((value: number) => {
    const clamped = Math.min(Math.max(value, 0), 1);
    setVolumeState(clamped);
    if (audioRef.current) {
      audioRef.current.volume = clamped;
    }
  }, []);

  return (
    <PlayerContext.Provider
      value={{ queue, current, playing, currentTime, duration, volume, lyricText, play,
        enqueue, toggle, next, prev, seek, setVolume }}
    >
      {children}
    </PlayerContext.Provider>
  );
}

/** 读取播放器上下文，必须在 {@link PlayerProvider} 内使用 */
// eslint-disable-next-line react-refresh/only-export-components -- Provider 与 hook 同文件导出，保持播放模块入口聚合
export function usePlayer(): PlayerContextValue {
  const context = useContext(PlayerContext);
  if (!context) {
    throw new Error('usePlayer 必须在 PlayerProvider 内使用');
  }
  return context;
}
