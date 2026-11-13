// OA 协同 - 工作台：汇总待办审批、未读公告、今日会议、到期任务与近期生日
import { useCallback, useEffect, useState } from 'react';
import { App, Button, Card, Col, Empty, Row, Space, Statistic, Tag } from 'antd';
import dayjs from 'dayjs';
import { getWorkbenchSummary, type OaWorkbenchSummary } from '../../api/workbench';

/** 任务状态文案 */
const TASK_STATUS_META: Record<string, { text: string; color: string }> = {
  TODO: { text: '待办', color: 'default' },
  DOING: { text: '进行中', color: 'processing' },
  DONE: { text: '已完成', color: 'success' },
  CANCELLED: { text: '已取消', color: 'warning' },
};

/** 任务优先级文案 */
const PRIORITY_META: Record<string, { text: string; color: string }> = {
  HIGH: { text: '高', color: 'error' },
  NORMAL: { text: '中', color: 'blue' },
  LOW: { text: '低', color: 'default' },
};

/** 时间格式化为时:分（空值或非法值显示 -） */
function clockText(value?: string): string {
  if (!value) {
    return '-';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '-';
  }
  return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
}

/** 日期格式化为月-日（空值或非法值显示 -） */
function monthDayText(value?: string): string {
  if (!value) {
    return '-';
  }
  const date = dayjs(value);
  return date.isValid() ? date.format('MM-DD') : '-';
}

/** 距今天数文案 */
function daysAheadText(days: number): string {
  return days === 0 ? '今天' : `${days} 天后`;
}

/** 空态提示 */
function emptyNode(description: string) {
  return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={description} />;
}

export default function CollabWorkbenchPage() {
  const { message } = App.useApp();

  const [summary, setSummary] = useState<OaWorkbenchSummary | null>(null);
  const [loading, setLoading] = useState(false);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      setSummary(await getWorkbenchSummary());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '工作台数据加载失败');
    } finally {
      setLoading(false);
    }
  }, [message]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const pendingApprovalCount = summary?.pendingApprovalCount ?? 0;
  const unreadNoticeCount = summary?.unreadNoticeCount ?? 0;
  const todayMeetings = summary?.todayMeetings ?? [];
  const dueTasks = summary?.dueTasks ?? [];
  const birthdays = summary?.birthdays ?? [];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <span style={{ fontSize: 18, fontWeight: 600 }}>工作台</span>
        <Button loading={loading} onClick={() => void loadData()}>
          刷新
        </Button>
      </div>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="待我审批"
              value={pendingApprovalCount}
              valueStyle={pendingApprovalCount === 0 ? { color: '#999' } : undefined}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12}>
          <Card>
            <Statistic
              title="未读公告"
              value={unreadNoticeCount}
              valueStyle={unreadNoticeCount === 0 ? { color: '#999' } : undefined}
            />
          </Card>
        </Col>
      </Row>
      <Row gutter={[16, 16]}>
        <Col xs={24} lg={12}>
          <Card title="今日会议" loading={loading}>
            {todayMeetings.length === 0 ? (
              emptyNode('今天没有会议')
            ) : (
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                {todayMeetings.map((meeting) => (
                  <div
                    key={meeting.id}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 12,
                    }}
                  >
                    <span
                      style={{
                        overflow: 'hidden',
                        whiteSpace: 'nowrap',
                        textOverflow: 'ellipsis',
                        fontWeight: 500,
                      }}
                    >
                      {meeting.title}
                    </span>
                    <span style={{ color: '#666', whiteSpace: 'nowrap' }}>
                      {clockText(meeting.startTime)}-{clockText(meeting.endTime)}
                    </span>
                    <span style={{ color: '#999', whiteSpace: 'nowrap' }}>
                      {meeting.roomName || '-'}
                    </span>
                  </div>
                ))}
              </Space>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="到期任务" loading={loading}>
            {dueTasks.length === 0 ? (
              emptyNode('暂无到期任务')
            ) : (
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                {dueTasks.map((task) => {
                  const overdue = dayjs(task.dueDate).isBefore(dayjs(), 'day');
                  const statusMeta = TASK_STATUS_META[task.status] ?? {
                    text: task.status,
                    color: 'default',
                  };
                  const priorityMeta = PRIORITY_META[task.priority] ?? {
                    text: task.priority,
                    color: 'default',
                  };
                  return (
                    <div
                      key={task.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 12,
                      }}
                    >
                      <span
                        style={{
                          overflow: 'hidden',
                          whiteSpace: 'nowrap',
                          textOverflow: 'ellipsis',
                          fontWeight: 500,
                        }}
                      >
                        {task.title}
                      </span>
                      <Space size={4} style={{ whiteSpace: 'nowrap' }}>
                        <span style={{ color: overdue ? '#cf1322' : '#999' }}>
                          {task.dueDate || '-'}
                        </span>
                        {overdue ? <Tag color="error">已逾期</Tag> : null}
                        <Tag color={priorityMeta.color}>{priorityMeta.text}</Tag>
                        <Tag color={statusMeta.color}>{statusMeta.text}</Tag>
                      </Space>
                    </div>
                  );
                })}
              </Space>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title="近期生日" loading={loading}>
            {birthdays.length === 0 ? (
              emptyNode('暂无生日')
            ) : (
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                {birthdays.map((item) => (
                  <div
                    key={item.userId}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'space-between',
                      gap: 12,
                    }}
                  >
                    <span style={{ fontWeight: 500 }}>{item.employeeNo || item.userId}</span>
                    <Space size={8} style={{ whiteSpace: 'nowrap' }}>
                      <span style={{ color: '#666' }}>{monthDayText(item.birthday)}</span>
                      <Tag color={item.daysAhead === 0 ? 'success' : 'default'}>
                        {daysAheadText(item.daysAhead)}
                      </Tag>
                    </Space>
                  </div>
                ))}
              </Space>
            )}
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
