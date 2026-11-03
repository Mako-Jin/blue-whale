// 视频运营 - 播放统计（M4）：内容量 / 播放量 / 审核积压 / 授权到期概览
// 权限码：sys:video:stat:view；授权到期窗口可调（1–90 天，默认 7 天）

import { Alert, App, Button, Card, Descriptions, InputNumber, Space, Statistic, Tag } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { fetchStatOverview, type StatOverview } from '../../api/video';

/** 授权到期提醒窗口默认值（天后端默认 7 天） */
const DEFAULT_EXPIRING_DAYS = 7;

/** 授权到期提醒窗口上限 */
const MAX_EXPIRING_DAYS = 90;

export default function StatPage() {
  const { message } = App.useApp();
  const [overview, setOverview] = useState<StatOverview | null>(null);
  const [expiringDays, setExpiringDays] = useState<number>(DEFAULT_EXPIRING_DAYS);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setOverview(await fetchStatOverview(expiringDays));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '统计概览加载失败');
    } finally {
      setLoading(false);
    }
  }, [expiringDays, message]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <Space direction="vertical" style={{ width: '100%' }} size={16}>
      <Alert
        type="info"
        showIcon
        message="运营统计概览"
        description="内容量含草稿与已下线；播放量为全部内容播放次数总和；待审核评论与授权到期数量用于日常运营巡检。"
      />

      <Card
        title="内容与播放"
        loading={loading}
        extra={
          <Space>
            <span>授权到期窗口</span>
            <InputNumber
              min={1}
              max={MAX_EXPIRING_DAYS}
              precision={0}
              value={expiringDays}
              onChange={(value) => setExpiringDays(value ?? DEFAULT_EXPIRING_DAYS)}
              addonAfter="天"
              style={{ width: 120 }}
            />
            <Button onClick={() => void load()}>刷新</Button>
          </Space>
        }
      >
        <Space size={48} wrap>
          <Statistic title="内容总数" value={overview?.totalContent ?? 0} />
          <Statistic title="已发布内容" value={overview?.publishedContent ?? 0} valueStyle={{ color: '#3f8600' }} />
          <Statistic title="播放次数合计" value={overview?.totalPlayCount ?? 0} />
          <Statistic title="片单总数" value={overview?.collectionCount ?? 0} />
          <Statistic title="创作者总数" value={overview?.creatorCount ?? 0} />
        </Space>
      </Card>

      <Card title="内容类型分布" loading={loading}>
        <Space size={48} wrap>
          <Statistic title="短视频" value={overview?.shortVideoCount ?? 0} />
          <Statistic title="视频" value={overview?.videoCount ?? 0} />
          <Statistic title="电影" value={overview?.movieCount ?? 0} />
        </Space>
      </Card>

      <Card title="运营待办" loading={loading}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="待审核评论">
            {overview?.pendingCommentCount && overview.pendingCommentCount > 0 ? (
              <Tag color="orange">{overview.pendingCommentCount} 条待处理</Tag>
            ) : (
              <Tag color="green">暂无积压</Tag>
            )}
          </Descriptions.Item>
          <Descriptions.Item label={`授权 ${expiringDays} 天内到期（含已到期）`}>
            {overview?.expiringLicenseCount && overview.expiringLicenseCount > 0 ? (
              <Tag color="red">{overview.expiringLicenseCount} 部需处理</Tag>
            ) : (
              <Tag color="green">暂无临期授权</Tag>
            )}
          </Descriptions.Item>
        </Descriptions>
      </Card>
    </Space>
  );
}
