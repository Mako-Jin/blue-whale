// 档案同步状态页：游标水位、最近结果、待整理/失败统计，支持手动触发同步与失败重试
// 手动同步按钮按权限码 sys:archive:sync:run 渲染

import { Alert, App, Button, Card, Descriptions, Space, Statistic, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { AuthButton } from '@bluewhale/whale-ui';
import {
  runSync,
  syncStatus,
  type ArchiveSyncResultVO,
  type ArchiveSyncStatusVO,
} from '../../api/archive';
import { useI18n } from '../../i18n';
import { formatDateTime } from './helpers';

export default function ArchiveSyncStatus() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const [status, setStatus] = useState<ArchiveSyncStatusVO | null>(null);
  const [result, setResult] = useState<ArchiveSyncResultVO | null>(null);
  const [loading, setLoading] = useState(false);
  const [running, setRunning] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setStatus(await syncStatus());
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.sync.msg.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [message, t]);

  useEffect(() => {
    void load();
  }, [load]);

  const handleRun = async () => {
    setRunning(true);
    try {
      const outcome = await runSync();
      setResult(outcome);
      message.success(
        t('archive.sync.msg.completed', {
          pulled: outcome.pulled,
          archived: outcome.archived,
          pending: outcome.pending,
          failed: outcome.failed,
        }),
      );
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.sync.msg.failed'));
    } finally {
      setRunning(false);
    }
  };

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Card
        title={t('archive.sync.title')}
        loading={loading}
        extra={
          <Space>
            <Button onClick={() => void load()}>{t('archive.common.refresh')}</Button>
            <AuthButton
              perm="sys:archive:sync:run"
              type="primary"
              loading={running}
              onClick={() => void handleRun()}
            >
              {t('archive.sync.action.run')}
            </AuthButton>
          </Space>
        }
      >
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('archive.sync.label.enabled')}>
            {status ? (
              <Tag color={status.enabled ? 'green' : 'default'}>
                {status.enabled ? t('archive.sync.status.on') : t('archive.sync.status.off')}
              </Tag>
            ) : (
              '-'
            )}
          </Descriptions.Item>
          <Descriptions.Item label={t('archive.sync.label.cron')}>
            {status?.cron ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label={t('archive.sync.label.cursor')}>
            {formatDateTime(status?.cursorTime)}
          </Descriptions.Item>
          <Descriptions.Item label={t('archive.sync.label.lastRunAt')}>
            {formatDateTime(status?.lastRunAt)}
          </Descriptions.Item>
          <Descriptions.Item label={t('archive.sync.label.lastResult')} span={2}>
            {status?.lastResult ?? '-'}
          </Descriptions.Item>
        </Descriptions>
        <Space size={32} style={{ marginTop: 16 }}>
          <Statistic title={t('archive.sync.stats.archived')} value={status?.archivedCount ?? 0} />
          <Statistic title={t('archive.sync.stats.pending')} value={status?.pendingCount ?? 0} />
          <Statistic
            title={t('archive.sync.stats.failed')}
            value={status?.failedCount ?? 0}
            valueStyle={{ color: '#cf1322' }}
          />
        </Space>
      </Card>
      {result && (
        <Alert
          type={result.failed > 0 ? 'warning' : 'success'}
          showIcon
          message={t('archive.sync.result.title')}
          description={t('archive.sync.result.description', {
            pulled: result.pulled,
            archived: result.archived,
            pending: result.pending,
            skipped: result.skipped,
            failed: result.failed,
            cursor: formatDateTime(result.cursorTime),
            started: formatDateTime(result.startedAt),
            finished: formatDateTime(result.finishedAt),
          })}
        />
      )}
    </Space>
  );
}
