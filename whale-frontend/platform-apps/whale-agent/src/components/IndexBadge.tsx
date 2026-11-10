// 索引状态徽标：绿=最新 / 黄=构建中 / 红=未就绪 + 手动重建（管理员可见）
// 构建中每 4s 轮询一次状态，直到 READY/FAILED

import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Button, Space, Tag, Tooltip } from 'antd';
import { getIndexStatus, rebuildIndex, type IndexStatus } from '../api/agent';

/** 构建中状态轮询间隔（毫秒） */
const POLL_INTERVAL_MILLIS = 4_000;

interface BadgeStyle {
  color: string;
  text: string;
}

/** 状态 → 徽标文案与颜色 */
function describe(status: IndexStatus | null): BadgeStyle {
  switch (status?.status) {
    case 'READY':
      return { color: 'green', text: `索引就绪 · ${status.chunkCount ?? 0} 块` };
    case 'BUILDING':
      return { color: 'gold', text: '索引构建中' };
    case 'FAILED':
      return { color: 'red', text: '索引构建失败' };
    case 'EMPTY':
      return { color: 'red', text: '索引未就绪' };
    default:
      return { color: 'default', text: '索引状态未知' };
  }
}

export default function IndexBadge({ canRebuild }: { canRebuild: boolean }) {
  const { message } = App.useApp();
  const [status, setStatus] = useState<IndexStatus | null>(null);
  const [rebuilding, setRebuilding] = useState(false);
  const timerRef = useRef<number | null>(null);

  const load = useCallback(async () => {
    try {
      setStatus(await getIndexStatus());
    } catch {
      // 状态查询失败不阻断工作台，保留上一次结果
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  // 构建中轮询：状态切换为 READY/FAILED 后自动停止
  useEffect(() => {
    if (status?.status !== 'BUILDING') {
      return;
    }
    timerRef.current = window.setInterval(() => void load(), POLL_INTERVAL_MILLIS);
    return () => {
      if (timerRef.current !== null) {
        window.clearInterval(timerRef.current);
        timerRef.current = null;
      }
    };
  }, [status?.status, load]);

  const handleRebuild = async () => {
    setRebuilding(true);
    try {
      const accepted = await rebuildIndex();
      message[accepted ? 'success' : 'warning'](
        accepted ? '已触发索引重建，完成后自动刷新' : '已有构建任务在执行，请稍候',
      );
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '触发重建失败');
    } finally {
      setRebuilding(false);
    }
  };

  const view = describe(status);
  const detail = status
    ? [
        `文件 ${status.fileCount ?? 0} / 块 ${status.chunkCount ?? 0}`,
        status.indexVersion ? `版本 ${status.indexVersion}` : '',
        status.buildMillis ? `耗时 ${status.buildMillis}ms` : '',
        status.lastBuildAt ? `最近构建 ${status.lastBuildAt}` : '',
      ]
        .filter(Boolean)
        .join(' · ')
    : '点击刷新以获取索引状态';

  return (
    <Space size={8}>
      <Tooltip title={detail}>
        <Tag color={view.color} className="agent-index-badge">
          {view.text}
        </Tag>
      </Tooltip>
      {canRebuild && (
        <Button size="small" loading={rebuilding} onClick={() => void handleRebuild()}>
          重建索引
        </Button>
      )}
    </Space>
  );
}
