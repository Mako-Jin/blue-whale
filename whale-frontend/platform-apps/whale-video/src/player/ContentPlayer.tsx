// 视频播放器：签发 / 刷新播放票据，自动续播，15 秒进度上报
//
// 视频字节由 /video/stream/{contentId}?ticket=... 提供，前端不接触 fileId。
// 票据在 refreshIn 秒后主动刷新，刷新前后通过本地进度续播，避免源切换导致回退。
// 快捷键：空格播放 / 暂停、←→ 快退 / 快进 5 秒、↑↓ 音量、F 全屏、M 静音。

import { useCallback, useEffect, useRef, useState } from 'react';
import type { CSSProperties } from 'react';
import { buildStreamUrl, createPlayTicket, reportProgress, type PlayTicket } from '../api/video';
import { formatDuration } from '../utils/videoDisplay';

/** 进度上报间隔（毫秒） */
const REPORT_INTERVAL_MS = 15000;

/** 快捷键快进 / 快退步长（毫秒） */
const SEEK_STEP_MS = 5000;

/** 快捷键音量步长 */
const VOLUME_STEP = 0.1;

export interface ContentPlayerProps {
  /** 内容 ID */
  contentId: number;
  /** 是否处于激活状态（滑播非当前页时暂停并停止刷新） */
  active?: boolean;
  /** feed 预取的票据（可选，命中且未过期时直接使用） */
  prefetchedTicket?: PlayTicket | null;
  /** 预取票据的获取时间（毫秒时间戳） */
  prefetchedAt?: number;
  /** 播放失败回调（如内容已下架） */
  onError?: (message: string) => void;
  /** 容器样式 */
  style?: CSSProperties;
  /** 画面缩放方式：短视频默认 cover，视频/电影建议 contain */
  fit?: 'cover' | 'contain';
}

/** 判断预取票据是否仍可用（预留 15 秒刷新余量） */
function isPrefetchedUsable(ticket: PlayTicket | null, fetchedAt: number): boolean {
  if (!ticket) {
    return false;
  }
  const ttlMs = Math.max(ticket.refreshIn - 15, 5) * 1000;
  return Date.now() - fetchedAt < ttlMs;
}

export default function ContentPlayer({
  contentId,
  active = true,
  prefetchedTicket,
  prefetchedAt,
  onError,
  style,
  fit = 'cover',
}: ContentPlayerProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const videoRef = useRef<HTMLVideoElement>(null);
  const progressRef = useRef(0);
  const lastReportRef = useRef(0);
  const prefetchedRef = useRef({
    ticket: prefetchedTicket ?? null,
    at: prefetchedAt ?? Date.now(),
  });

  const [ticket, setTicket] = useState<PlayTicket | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paused, setPaused] = useState(true);
  const [muted, setMuted] = useState(true);
  const [fullscreen, setFullscreen] = useState(false);
  const [progressPercent, setProgressPercent] = useState(0);
  const [currentMs, setCurrentMs] = useState(0);

  const onErrorRef = useRef(onError);
  onErrorRef.current = onError;

  useEffect(() => {
    prefetchedRef.current = { ticket: prefetchedTicket ?? null, at: prefetchedAt ?? Date.now() };
  }, [prefetchedTicket, prefetchedAt]);

  // 切换内容时重置本地进度与进度条
  useEffect(() => {
    progressRef.current = 0;
    lastReportRef.current = 0;
    setCurrentMs(0);
    setProgressPercent(0);
  }, [contentId]);

  /** 上报当前进度（immediate=false 时按 15 秒节流） */
  const report = useCallback(
    (immediate: boolean) => {
      const video = videoRef.current;
      if (!video) {
        return;
      }
      const progressMs = Math.round(video.currentTime * 1000);
      if (progressMs <= 0) {
        return;
      }
      if (!immediate && Math.abs(progressMs - lastReportRef.current) < REPORT_INTERVAL_MS) {
        return;
      }
      lastReportRef.current = progressMs;
      reportProgress({ contentId, progressMs, source: 'MY', device: 'WEB' }).catch(() => undefined);
    },
    [contentId],
  );

  // 激活时申请票据并按 refreshIn 周期刷新；失活时暂停并停止刷新
  useEffect(() => {
    setError(null);
    if (!active) {
      videoRef.current?.pause();
      return;
    }
    let cancelled = false;
    let timer: number | undefined;
    const scheduleRefresh = (current: PlayTicket) => {
      const delay = Math.max(current.refreshIn - 5, 10) * 1000;
      timer = window.setTimeout(() => {
        createPlayTicket(contentId)
          .then((next) => {
            if (cancelled) {
              return;
            }
            setTicket(next);
            scheduleRefresh(next);
          })
          .catch((reason: unknown) => {
            if (cancelled) {
              return;
            }
            const message = reason instanceof Error ? reason.message : '播放票据刷新失败';
            setError(message);
            onErrorRef.current?.(message);
          });
      }, delay);
    };
    const start = async () => {
      try {
        const prefetched = prefetchedRef.current;
        if (isPrefetchedUsable(prefetched.ticket, prefetched.at) && prefetched.ticket) {
          setTicket(prefetched.ticket);
          if (prefetched.ticket.progressMs) {
            progressRef.current = prefetched.ticket.progressMs;
          }
          scheduleRefresh(prefetched.ticket);
          return;
        }
        const issued = await createPlayTicket(contentId);
        if (cancelled) {
          return;
        }
        setTicket(issued);
        if (issued.progressMs) {
          progressRef.current = issued.progressMs;
        }
        scheduleRefresh(issued);
      } catch (reason) {
        if (cancelled) {
          return;
        }
        const message = reason instanceof Error ? reason.message : '播放票据申请失败';
        setError(message);
        onErrorRef.current?.(message);
      }
    };
    void start();
    return () => {
      cancelled = true;
      if (timer) {
        window.clearTimeout(timer);
      }
    };
  }, [active, contentId]);

  // 票据变化后自动播放（浏览器可能因未交互阻止，保留手动播放按钮）
  useEffect(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    if (!active || !ticket) {
      return;
    }
    video.play().catch(() => setPaused(true));
  }, [active, ticket]);

  const togglePlay = useCallback(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    if (video.paused) {
      video.play().catch(() => setPaused(true));
    } else {
      video.pause();
    }
  }, []);

  const toggleMute = useCallback(() => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    if (video.muted) {
      if (video.volume === 0) {
        video.volume = 0.5;
      }
      video.muted = false;
      setMuted(false);
    } else {
      video.muted = true;
      setMuted(true);
    }
  }, []);

  /** 相对当前播放位置快进 / 快退（毫秒，负数快退） */
  const seekBy = useCallback((deltaMs: number) => {
    const video = videoRef.current;
    if (!video || !Number.isFinite(video.duration) || video.duration <= 0) {
      return;
    }
    const next = Math.min(Math.max(video.currentTime + deltaMs / 1000, 0), video.duration - 0.5);
    video.currentTime = Math.max(next, 0);
  }, []);

  /** 调整音量（delta 为 -1 ~ 1），增大音量时自动取消静音 */
  const changeVolume = useCallback((delta: number) => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    const next = Math.min(Math.max(video.volume + delta, 0), 1);
    video.volume = next;
    if (next === 0) {
      video.muted = true;
      setMuted(true);
    } else if (video.muted) {
      video.muted = false;
      setMuted(false);
    }
  }, []);

  /** 切换容器全屏（优先使用浏览器原生全屏） */
  const toggleFullscreen = useCallback(() => {
    if (document.fullscreenElement) {
      void document.exitFullscreen().catch(() => undefined);
      return;
    }
    const container = containerRef.current;
    if (container) {
      void container.requestFullscreen().catch(() => undefined);
    }
  }, []);

  // 同步全屏状态（用户按 Esc 退出时也要回写按钮状态）
  useEffect(() => {
    const handler = () => setFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', handler);
    return () => document.removeEventListener('fullscreenchange', handler);
  }, []);

  // 键盘快捷键：仅当前播放器激活且焦点不在输入控件时生效
  useEffect(() => {
    if (!active) {
      return;
    }
    const handler = (event: KeyboardEvent) => {
      const target = event.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === 'INPUT' ||
          target.tagName === 'TEXTAREA' ||
          target.tagName === 'SELECT' ||
          target.isContentEditable)
      ) {
        return;
      }
      switch (event.key) {
        case ' ':
          event.preventDefault();
          togglePlay();
          break;
        case 'ArrowLeft':
          event.preventDefault();
          seekBy(-SEEK_STEP_MS);
          break;
        case 'ArrowRight':
          event.preventDefault();
          seekBy(SEEK_STEP_MS);
          break;
        case 'ArrowUp':
          event.preventDefault();
          changeVolume(VOLUME_STEP);
          break;
        case 'ArrowDown':
          event.preventDefault();
          changeVolume(-VOLUME_STEP);
          break;
        case 'f':
        case 'F':
          event.preventDefault();
          toggleFullscreen();
          break;
        case 'm':
        case 'M':
          toggleMute();
          break;
        default:
          break;
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [active, togglePlay, toggleMute, seekBy, changeVolume, toggleFullscreen]);

  const handleLoadedMetadata = () => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    if (progressRef.current > 0 && Number.isFinite(video.duration) && video.duration > 0) {
      video.currentTime = Math.min(progressRef.current / 1000, video.duration - 0.5);
    }
  };

  const handleTimeUpdate = () => {
    const video = videoRef.current;
    if (!video) {
      return;
    }
    progressRef.current = Math.round(video.currentTime * 1000);
    setCurrentMs(progressRef.current);
    if (video.duration > 0) {
      setProgressPercent(Math.min(video.currentTime / video.duration, 1));
    }
    report(false);
  };

  const handlePause = () => {
    setPaused(true);
    report(true);
  };

  const handleEnded = () => {
    setPaused(true);
    report(true);
  };

  return (
    <div
      ref={containerRef}
      style={{
        position: 'relative',
        width: '100%',
        height: '100%',
        background: '#000',
        overflow: 'hidden',
        ...style,
      }}
    >
      {ticket ? (
        <video
          ref={videoRef}
          src={buildStreamUrl(contentId, ticket.ticket)}
          muted={muted}
          playsInline
          preload="auto"
          style={{ width: '100%', height: '100%', objectFit: fit }}
          onLoadedMetadata={handleLoadedMetadata}
          onTimeUpdate={handleTimeUpdate}
          onPlay={() => setPaused(false)}
          onPause={handlePause}
          onEnded={handleEnded}
          onError={() => setError('视频加载失败，请稍后重试或切换下一条')}
        />
      ) : (
        <div style={centerStyle}>
          <span style={{ color: 'rgba(255,255,255,0.75)' }}>{error ?? '加载中…'}</span>
        </div>
      )}

      {error && ticket ? (
        <div style={{ ...centerStyle, top: 24, bottom: 'auto', left: 16, right: 16 }}>
          <span
            style={{
              color: '#fff',
              background: 'rgba(0,0,0,0.55)',
              padding: '6px 12px',
              borderRadius: 6,
            }}
          >
            {error}
          </span>
        </div>
      ) : null}

      <div style={bottomBarStyle}>
        <button
          type="button"
          style={controlStyle}
          onClick={togglePlay}
          aria-label={paused ? '播放' : '暂停'}
        >
          {paused ? '▶' : '⏸'}
        </button>
        <button
          type="button"
          style={controlStyle}
          onClick={toggleMute}
          aria-label={muted ? '取消静音' : '静音'}
        >
          {muted ? '🔇' : '🔊'}
        </button>
        <span style={timeStyle}>
          {formatDuration(currentMs)} / {formatDuration(ticket?.durationMs)}
        </span>
        <div style={progressTrackStyle}>
          <div style={{ ...progressBarStyle, width: `${Math.round(progressPercent * 100)}%` }} />
        </div>
        <button
          type="button"
          style={controlStyle}
          onClick={toggleFullscreen}
          aria-label={fullscreen ? '退出全屏' : '全屏'}
          title={fullscreen ? '退出全屏（F）' : '全屏（F）'}
        >
          {fullscreen ? '⛗' : '⛶'}
        </button>
      </div>
    </div>
  );
}

const centerStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
};

const bottomBarStyle: CSSProperties = {
  position: 'absolute',
  left: 0,
  right: 0,
  bottom: 0,
  padding: '12px 16px 10px',
  display: 'flex',
  alignItems: 'center',
  gap: 10,
  background: 'linear-gradient(180deg, rgba(0,0,0,0) 0%, rgba(0,0,0,0.6) 100%)',
};

const controlStyle: CSSProperties = {
  width: 32,
  height: 32,
  borderRadius: '50%',
  border: '1px solid rgba(255,255,255,0.5)',
  background: 'rgba(0,0,0,0.35)',
  color: '#fff',
  cursor: 'pointer',
  lineHeight: '28px',
  textAlign: 'center',
};

const timeStyle: CSSProperties = {
  color: '#fff',
  fontSize: 12,
  whiteSpace: 'nowrap',
};

const progressTrackStyle: CSSProperties = {
  flex: 1,
  height: 3,
  borderRadius: 2,
  background: 'rgba(255,255,255,0.3)',
  overflow: 'hidden',
};

const progressBarStyle: CSSProperties = {
  height: '100%',
  background: '#1677ff',
};
