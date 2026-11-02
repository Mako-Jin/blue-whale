// 全局播放条：播放 / 暂停、进度拖动、上一首 / 下一首、音量、播放队列与歌词抽屉
// 固定在子应用底部，不随路由切换重建（挂载在 MusicLayout 内）。

import { Button, Drawer, Empty, List, Popover, Slider, Space, Typography } from 'antd';
import { useMemo, useState } from 'react';
import { usePlayer } from './PlayerContext';
import { locateLyricLine, parseLyric } from './lyrics';

/** 秒转 mm:ss（超过 1 小时为 hh:mm:ss） */
function formatClock(seconds: number): string {
  const total = Math.max(Math.floor(seconds || 0), 0);
  const hour = Math.floor(total / 3600);
  const minute = Math.floor((total % 3600) / 60);
  const second = total % 60;
  const pad = (value: number) => String(value).padStart(2, '0');
  return hour > 0 ? `${pad(hour)}:${pad(minute)}:${pad(second)}` : `${pad(minute)}:${pad(second)}`;
}

export default function PlayerBar() {
  const player = usePlayer();
  const [lyricOpen, setLyricOpen] = useState(false);
  const [queueOpen, setQueueOpen] = useState(false);
  const lines = useMemo(() => parseLyric(player.lyricText), [player.lyricText]);
  const activeLine = locateLyricLine(lines, player.currentTime * 1000);

  if (!player.current) {
    return null;
  }

  const max = player.duration > 0 ? player.duration : 0;

  const queuePanel = (
    <div className="music-queue">
      <List
        size="small"
        dataSource={player.queue}
        locale={{ emptyText: '播放队列为空' }}
        renderItem={(item) => (
          <List.Item
            className={
              item.id === player.current?.id ? 'music-queue-item active' : 'music-queue-item'
            }
            onClick={() => player.play(item, player.queue)}
          >
            <Typography.Text ellipsis>{item.title}</Typography.Text>
          </List.Item>
        )}
      />
    </div>
  );

  return (
    <>
      <div className="music-player-bar">
        <div className="music-player-titles">
          <Typography.Text strong ellipsis>
            {player.current.title}
          </Typography.Text>
          {player.current.subtitle && (
            <Typography.Text type="secondary" ellipsis style={{ fontSize: 12 }}>
              {player.current.subtitle}
            </Typography.Text>
          )}
        </div>
        <Space size={8} className="music-player-controls">
          <Button size="small" onClick={player.prev} disabled={!player.queue.length}>
            上一首
          </Button>
          <Button size="small" type="primary" onClick={player.toggle}>
            {player.playing ? '暂停' : '播放'}
          </Button>
          <Button size="small" onClick={player.next} disabled={!player.queue.length}>
            下一首
          </Button>
        </Space>
        <div className="music-player-progress">
          <span className="music-player-time">{formatClock(player.currentTime)}</span>
          <Slider
            min={0}
            max={max || 1}
            value={Math.min(player.currentTime, max || 1)}
            tooltip={{ formatter: (value) => formatClock(Number(value)) }}
            onChange={player.seek}
            disabled={max <= 0}
            style={{ flex: 1, margin: '0 8px' }}
          />
          <span className="music-player-time">{formatClock(max)}</span>
        </div>
        <div className="music-player-extra">
          <Slider
            className="music-player-volume"
            min={0}
            max={1}
            step={0.05}
            value={player.volume}
            onChange={player.setVolume}
            tooltip={{ formatter: (value) => `${Math.round(Number(value) * 100)}%` }}
          />
          <Popover
            content={queuePanel}
            trigger="click"
            open={queueOpen}
            onOpenChange={setQueueOpen}
            placement="topRight"
          >
            <Button size="small">队列（{player.queue.length}）</Button>
          </Popover>
          <Button size="small" onClick={() => setLyricOpen(true)}>
            歌词
          </Button>
        </div>
      </div>
      <Drawer
        title="歌词"
        placement="right"
        width={360}
        open={lyricOpen}
        onClose={() => setLyricOpen(false)}
        destroyOnClose
      >
        {lines.length === 0 ? (
          <Empty description="暂无歌词" />
        ) : (
          <div className="music-lyric-lines">
            {lines.map((line, position) => (
              <div
                key={`${line.timeMs}-${position}`}
                className={position === activeLine ? 'music-lyric-line active' : 'music-lyric-line'}
                onClick={() => line.timeMs >= 0 && player.seek(line.timeMs / 1000)}
              >
                {line.text || '\u00A0'}
              </div>
            ))}
          </div>
        )}
      </Drawer>
    </>
  );
}
