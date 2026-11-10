// 播放器组件测试：MockAudio 替身覆盖播放器上下文状态机与播放条渲染
// 覆盖：无曲目不渲染播放条 / 票据申请与音频加载 / 播放暂停切换 / 队列去重与上下曲边界
import { App } from 'antd';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import PlayerBar from './PlayerBar';
import { PlayerProvider, usePlayer, type PlayableTrack } from './PlayerContext';
import { requestPlayTicket } from '../api/music';

vi.mock('../api/music', () => ({
  requestPlayTicket: vi.fn(),
  reportPlayProgress: vi.fn(),
}));

/** jsdom 无真实媒体栈：以记录型替身替代 HTMLAudioElement */
class MockAudio {
  static instances: MockAudio[] = [];
  src = '';
  volume = 1;
  preload = '';
  paused = true;
  currentTime = 0;
  duration = NaN;
  play = vi.fn(async () => {
    this.paused = false;
    this.emit('play');
  });
  pause = vi.fn(() => {
    this.paused = true;
    this.emit('pause');
  });
  private listeners = new Map<string, (() => void)[]>();

  constructor() {
    MockAudio.instances.push(this);
  }

  addEventListener(type: string, listener: () => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  removeEventListener(type: string, listener: () => void) {
    this.listeners.set(
      type,
      (this.listeners.get(type) ?? []).filter((item) => item !== listener),
    );
  }

  private emit(type: string) {
    this.listeners.get(type)?.forEach((listener) => listener());
  }
}

vi.stubGlobal('Audio', MockAudio as unknown as typeof Audio);

const trackA: PlayableTrack = { id: 1, title: '晴天' };
const trackB: PlayableTrack = { id: 2, title: '七里香' };

const ticket = {
  ticket: 't-1',
  expiresIn: 120,
  songId: 1,
  title: '晴天',
  streamUrl: 'blob:stream-1',
  lyricText: '[00:01]第一句',
};

/** 上下文探针：暴露队列操作按钮与当前曲目 */
function PlayerControls() {
  const player = usePlayer();
  return (
    <div>
      <span data-testid="current">{player.current?.title ?? 'none'}</span>
      <span data-testid="queue-length">{player.queue.length}</span>
      <button onClick={() => player.play(trackB, [trackA, trackB, { id: 3, title: '夜曲' }])}>
        play-b
      </button>
      <button onClick={() => player.enqueue(trackA)}>enqueue-a</button>
      <button onClick={() => player.toggle()}>toggle</button>
      <button onClick={() => player.next()}>next</button>
      <button onClick={() => player.prev()}>prev</button>
    </div>
  );
}

function PlayerProbe() {
  return (
    <PlayerProvider>
      <PlayerControls />
    </PlayerProvider>
  );
}

/** 播放条 + 播放触发按钮（同一 Provider） */
function renderPlayerBar() {
  function Trigger() {
    const player = usePlayer();
    return (
      <button onClick={() => player.play(trackB, [trackA, trackB, { id: 3, title: '夜曲' }])}>
        play-b
      </button>
    );
  }
  return render(
    <App>
      <PlayerProvider>
        <Trigger />
        <PlayerBar />
      </PlayerProvider>
    </App>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  MockAudio.instances = [];
  (requestPlayTicket as Mock).mockResolvedValue(ticket);
});

describe('PlayerBar 播放条', () => {
  it('队列为空时不渲染播放条', () => {
    const { container } = renderPlayerBar();
    expect(container.querySelector('.music-player-bar')).toBeNull();
  });

  it('播放曲目后申请票据、加载音频并渲染曲目与控制按钮', async () => {
    renderPlayerBar();
    // 通过上下文触发播放
    fireEvent.click(screen.getByText('play-b'));

    const audio = MockAudio.instances[0];
    await waitFor(() => {
      expect(requestPlayTicket).toHaveBeenCalledWith(2);
      expect(audio.src).toBe('blob:stream-1');
      expect(audio.play).toHaveBeenCalled();
    });

    expect(screen.getByText('七里香')).toBeInTheDocument();
    expect(screen.getByText('队列（3）')).toBeInTheDocument();
    // play 事件已派发 → 显示暂停按钮（antd 两字按钮插空格，用正则匹配）
    expect(screen.getByRole('button', { name: /暂\s*停/ })).toBeInTheDocument();
  });

  it('点击暂停触发 audio.pause 并回到播放按钮', async () => {
    renderPlayerBar();
    fireEvent.click(screen.getByText('play-b'));

    const audio = MockAudio.instances[0];
    await waitFor(() => {
      expect(audio.play).toHaveBeenCalled();
    });

    fireEvent.click(screen.getByRole('button', { name: /暂\s*停/ }));
    expect(audio.pause).toHaveBeenCalled();
    await waitFor(() => {
      expect(screen.getByRole('button', { name: /播\s*放/ })).toBeInTheDocument();
    });
  });

  it('播放失败时提示且不进入播放态', async () => {
    (requestPlayTicket as Mock).mockRejectedValue(new Error('票据过期'));
    renderPlayerBar();
    fireEvent.click(screen.getByText('play-b'));

    await waitFor(() => {
      expect(screen.getByText('票据过期')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /播\s*放/ })).toBeInTheDocument();
  });
});

describe('PlayerContext 播放队列', () => {
  it('next 越过队尾不越界并暂停，prev 回退曲目', async () => {
    render(
      <App>
        <PlayerProbe />
      </App>,
    );
    fireEvent.click(screen.getByText('play-b'));
    expect(await screen.findByTestId('current')).toHaveTextContent('七里香');

    const audio = MockAudio.instances[0];
    fireEvent.click(screen.getByText('next'));
    expect(screen.getByTestId('current')).toHaveTextContent('夜曲');

    // 队尾再 next：保持当前曲目并暂停
    fireEvent.click(screen.getByText('next'));
    expect(screen.getByTestId('current')).toHaveTextContent('夜曲');
    expect(audio.pause).toHaveBeenCalled();

    fireEvent.click(screen.getByText('prev'));
    expect(screen.getByTestId('current')).toHaveTextContent('七里香');
  });

  it('enqueue 去重：空队列直接开播，重复追加不扩容', async () => {
    render(
      <App>
        <PlayerProbe />
      </App>,
    );
    fireEvent.click(screen.getByText('enqueue-a'));

    expect(await screen.findByTestId('current')).toHaveTextContent('晴天');
    expect(screen.getByTestId('queue-length')).toHaveTextContent('1');

    fireEvent.click(screen.getByText('enqueue-a'));
    expect(screen.getByTestId('queue-length')).toHaveTextContent('1');
  });
});
