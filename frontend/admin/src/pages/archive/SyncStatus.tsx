// 档案同步状态页：游标水位、最近结果、待整理/失败统计，支持手动触发同步与失败重试
// 手动同步按钮按权限码 sys:archive:sync:run 渲染

import { Alert, App, Button, Card, Descriptions, Space, Statistic, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { AuthButton } from '@bluewhale/whale-ui';
import { runSync, syncStatus, type ArchiveSyncResultVO, type ArchiveSyncStatusVO } from '../../api/archive';
import { formatDateTime } from './helpers';

export default function ArchiveSyncStatus() {
  const { message } = App.useApp();
  const [status, setStatus] = useState<ArchiveSyncStatusVO | null>(null);
  const [result, setResult] = useState<ArchiveSyncResultVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setStatus(await syncStatus());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '同步状态加载失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRun = async () => {
    setRunning(true);
    try {
      const outcome = await runSync();
      setResult(outcome);
      message.success(
        `同步完成：拉取 ${outcome.pulled}，归档 ${outcome.archived}，待整理 ${outcome.pending}，失败 ${outcome.failed}`,
      );
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '同步失败');
    } finally {
      setRunning(false);
    }
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card
        title="同步运行状态"
        loading={loading}
        extra={
          <Space>
            <Button onClick={() => void load()}>刷新</Button>
            <AuthButton
              perm="sys:archive:sync:run"
              type="primary"
              loading={running}
              onClick={() => void handleRun()}
            >
              立即同步
            </AuthButton>
          </Space>
        }
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="定时同步">
            {status ? (
              <Tag color={status.enabled ? 'green' : 'default'}>{status.enabled ? '已开启' : '已关闭'}</Tag>
            ) : (
              '-'
            )}
          </Descriptions.Item>
          <Descriptions.Item label="执行表达式">{status?.cron ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="同步游标（办结时间水位）">
            {formatDateTime(status?.cursorTime)}
          </Descriptions.Item>
          <Descriptions.Item label="最近一次运行">
            {formatDateTime(status?.lastRunAt)}
          </Descriptions.Item>
          <Descriptions.Item label="最近结果" span={2}>
            {status?.lastResult ?? '-'}
          </Descriptions.Item>
        </Descriptions>
        <Space size={32} style={{ marginTop: 16 }}>
          <Statistic title="已归档条目" value={status?.archivedCount ?? 0} />
          <Statistic title="待整理条目" value={status?.pendingCount ?? 0} />
          <Statistic title="失败待重试" value={status?.failedCount ?? 0} valueStyle={{ color: '#cf1322' }} />
        </Space>
      </Card>
      {result && (
        <Alert
          type={result.failed > 0 ? 'warning' : 'success'}
          showIcon
          message="本轮同步结果"
          description={`拉取 ${result.pulled} 条；自动归档 ${result.archived} 条；待整理 ${result.pending} 条；跳过 ${result.skipped} 条；失败 ${result.failed} 条；游标 ${formatDateTime(
            result.cursorTime,
          )}；耗时 ${formatDateTime(result.startedAt)} → ${formatDateTime(result.finishedAt)}`}
        />
      )}
    </Space>
  );
}
