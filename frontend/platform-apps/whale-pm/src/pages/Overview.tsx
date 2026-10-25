// 项目概览：工作项统计、进行中迭代与最近动态

import { useCallback, useEffect, useState } from 'react';
import { App, Card, Col, Descriptions, List, Row, Statistic, Tag, Typography } from 'antd';
import { itemApi, sprintApi, type Activity, type Sprint, type WorkItem } from '../api/pm';
import { formatDate, formatTime, sprintStatusColor, sprintStatusLabel, statusColor, typeLabel } from '../utils';
import { useWorkspace } from '../workspace-context';

export default function Overview() {
  const { project } = useWorkspace();
  const { message } = App.useApp();
  const [items, setItems] = useState<WorkItem[]>([]);
  const [sprint, setSprint] = useState<Sprint | null>(null);
  const [activities, setActivities] = useState<Activity[]>([]);

  const load = useCallback(async () => {
    try {
      const [itemPage, sprintPage, activityList] = await Promise.all([
        itemApi.page({ projectId: project.id, current: 1, size: 200 }),
        sprintApi.page({ projectId: project.id, current: 1, size: 20, status: 'ACTIVE' }),
        itemApi.projectActivities(project.id),
      ]);
      setItems(itemPage.records);
      setSprint(sprintPage.records[0] ?? null);
      setActivities(activityList.slice(0, 20));
    } catch (error) {
      message.error((error as Error).message);
    }
  }, [project.id, message]);

  useEffect(() => {
    void load();
  }, [load]);

  const done = items.filter((item) => item.statusCategory === 'DONE').length;
  const inProgress = items.filter((item) => item.statusCategory === 'IN_PROGRESS').length;
  const totalPoints = items.reduce((sum, item) => sum + Number(item.storyPoints ?? 0), 0);

  return (
    <div>
      <Row gutter={16}>
        <Col span={6}>
          <Card>
            <Statistic title="工作项总数" value={items.length} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="处理中" value={inProgress} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="已完成" value={done} />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic title="故事点合计" value={totalPoints} precision={2} />
          </Card>
        </Col>
      </Row>

      <Card title="进行中迭代" style={{ marginTop: 16 }}>
        {sprint ? (
          <Descriptions column={4} size="small">
            <Descriptions.Item label="迭代">{sprint.name}</Descriptions.Item>
            <Descriptions.Item label="区间">
              {formatDate(sprint.startDate)} ~ {formatDate(sprint.endDate)}
            </Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={sprintStatusColor(sprint.status)}>{sprintStatusLabel(sprint.status)}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="目标">{sprint.goal ?? '-'}</Descriptions.Item>
          </Descriptions>
        ) : (
          <Typography.Text type="secondary">当前没有进行中的迭代</Typography.Text>
        )}
      </Card>

      <Card title="最近动态" style={{ marginTop: 16 }}>
        <List
          size="small"
          dataSource={activities}
          locale={{ emptyText: '暂无动态' }}
          renderItem={(activity) => (
            <List.Item>
              <Typography.Text type="secondary" style={{ marginRight: 8 }}>
                {formatTime(activity.createTime)}
              </Typography.Text>
              <Tag color={statusColor('TODO')}>{activity.action}</Tag>
              {activity.field ? `${activity.field}: ${activity.oldValue ?? '-'} → ${activity.newValue ?? '-'}` : activity.newValue}
            </List.Item>
          )}
        />
        <Typography.Text type="secondary">
          共 {items.length} 个工作项，类型分布：{' '}
          {Array.from(new Set(items.map((item) => item.type)))
            .map((type) => `${typeLabel(type)} ${items.filter((item) => item.type === type).length}`)
            .join(' / ') || '-'}
        </Typography.Text>
      </Card>
    </div>
  );
}
