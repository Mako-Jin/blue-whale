// 视频运营 - 播放统计（M4）：内容量 / 播放量 / 审核积压 / 授权到期概览
// 权限码：sys:video:stat:view；授权到期窗口可调（1–90 天，默认 7 天）

import { Alert, App, Button, Card, Descriptions, InputNumber, Space, Statistic, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { fetchStatOverview, type StatOverview } from '../../api/video';
import { useI18n } from '../../i18n';

/** 授权到期提醒窗口默认值（天后端默认 7 天） */
const DEFAULT_EXPIRING_DAYS = 7;

/** 授权到期提醒窗口上限 */
const MAX_EXPIRING_DAYS = 90;

export default function StatPage() {
  const { message } = App.useApp();
  const { t } = useI18n();
  const [overview, setOverview] = useState<StatOverview | null>(null);
  const [expiringDays, setExpiringDays] = useState<number>(DEFAULT_EXPIRING_DAYS);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setOverview(await fetchStatOverview(expiringDays));
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('video.stat.msg.loadFailed'));
    } finally {
      setLoading(false);
    }
  }, [expiringDays, message, t]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Alert
        type="info"
        showIcon
        message={t('video.stat.alert.title')}
        description={t('video.stat.alert.description')}
      />

      <Card
        title={t('video.stat.card.content')}
        loading={loading}
        extra={
          <Space>
            <span>{t('video.stat.label.expiringWindow')}</span>
            <InputNumber
              min={1}
              max={MAX_EXPIRING_DAYS}
              precision={0}
              value={expiringDays}
              onChange={(value) => setExpiringDays(value ?? DEFAULT_EXPIRING_DAYS)}
              addonAfter={t('video.stat.unit.days')}
              style={{ width: 120 }}
            />
            <Button onClick={() => void load()}>{t('video.stat.action.refresh')}</Button>
          </Space>
        }
      >
        <Space size={48} wrap>
          <Statistic
            title={t('video.stat.card.totalContent')}
            value={overview?.totalContent ?? 0}
          />
          <Statistic
            title={t('video.stat.card.publishedContent')}
            value={overview?.publishedContent ?? 0}
            valueStyle={{ color: '#3f8600' }}
          />
          <Statistic
            title={t('video.stat.card.totalViews')}
            value={overview?.totalPlayCount ?? 0}
          />
          <Statistic
            title={t('video.stat.card.collections')}
            value={overview?.collectionCount ?? 0}
          />
          <Statistic title={t('video.stat.card.creators')} value={overview?.creatorCount ?? 0} />
        </Space>
      </Card>

      <Card title={t('video.stat.card.typeDistribution')} loading={loading}>
        <Space size={48} wrap>
          <Statistic
            title={t('video.stat.card.shortVideo')}
            value={overview?.shortVideoCount ?? 0}
          />
          <Statistic title={t('video.stat.card.video')} value={overview?.videoCount ?? 0} />
          <Statistic title={t('video.stat.card.movie')} value={overview?.movieCount ?? 0} />
        </Space>
      </Card>

      <Card title={t('video.stat.card.todo')} loading={loading}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label={t('video.stat.label.pendingComments')}>
            {overview?.pendingCommentCount && overview.pendingCommentCount > 0 ? (
              <Tag color="orange">
                {t('video.stat.tag.pendingComments', { count: overview.pendingCommentCount })}
              </Tag>
            ) : (
              <Tag color="green">{t('video.stat.tag.noBacklog')}</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label={t('video.stat.label.expiringLicenses', { days: expiringDays })}>
            {overview?.expiringLicenseCount && overview.expiringLicenseCount > 0 ? (
              <Tag color="red">
                {t('video.stat.tag.expiringLicenses', { count: overview.expiringLicenseCount })}
              </Tag>
            ) : (
              <Tag color="green">{t('video.stat.tag.noExpiring')}</Tag>
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}
