// OA 协同 - 日程：月视图、共享/公开可见性、共享名单与临近提醒（会议按参会人同步展示）
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  App,
  Button,
  Calendar,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tooltip,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createCalendar,
  deleteCalendar,
  getCalendar,
  listCalendarReminders,
  pageCalendars,
  replaceCalendarShares,
  updateCalendar,
  type OaCalendarDetail,
  type OaCalendarEntry,
  type OaCalendarInput,
  type OaCalendarShare,
  type OaCalendarVisibility,
} from '../../api/calendar';
import { pageMeetings, type OaMeeting } from '../../api/collab';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 可见性文案 */
const VISIBILITY_META: Record<OaCalendarVisibility, { text: string; color: string }> = {
  PRIVATE: { text: '私密', color: 'default' },
  SHARED: { text: '共享', color: 'purple' },
  PUBLIC: { text: '公开', color: 'blue' },
};

/** 提醒选项（分钟） */
const REMIND_OPTIONS = [
  { label: '不提醒', value: 0 },
  { label: '提前 5 分钟', value: 5 },
  { label: '提前 10 分钟', value: 10 },
  { label: '提前 15 分钟', value: 15 },
  { label: '提前 30 分钟', value: 30 },
  { label: '提前 1 小时', value: 60 },
  { label: '提前 1 天', value: 1440 },
];

/** 日程表单值 */
interface CalendarFormValues {
  title: string;
  description?: string;
  location?: string;
  timeRange: [Dayjs, Dayjs];
  allDay?: number;
  visibility: OaCalendarVisibility;
  remindMinutes?: number;
}

/** 共享表单值 */
interface ShareFormValues {
  users?: { userId?: number; userName?: string }[];
}

/** 月视图事件（个人日程 + 会议同步） */
interface DayEvent {
  key: string;
  dateKey: string;
  title: string;
  timeText: string;
  meeting: boolean;
  entry?: OaCalendarEntry;
}

/** 时间格式化为展示文本 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

export default function CollabCalendarPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();
  const userId = typeof user?.userId === 'number' ? user.userId : undefined;

  const [month, setMonth] = useState(dayjs());
  const [selected, setSelected] = useState(dayjs());
  const [entries, setEntries] = useState<OaCalendarEntry[]>([]);
  const [meetings, setMeetings] = useState<OaMeeting[]>([]);
  const [reminders, setReminders] = useState<OaCalendarEntry[]>([]);
  const [keyword, setKeyword] = useState('');
  const [visibility, setVisibility] = useState<OaCalendarVisibility | ''>('');
  const [loading, setLoading] = useState(false);

  const [modal, setModal] = useState(false);
  const [editing, setEditing] = useState<OaCalendarEntry | null>(null);
  const [shareModal, setShareModal] = useState(false);
  const [shareTarget, setShareTarget] = useState<OaCalendarEntry | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<OaCalendarDetail | null>(null);
  const [saving, setSaving] = useState(false);

  const [form] = Form.useForm<CalendarFormValues>();
  const [shareForm] = Form.useForm<ShareFormValues>();

  const canManage = hasPermission('sys:oa:calendar:manage');
  const canReminder = hasPermission('sys:oa:calendar:reminder');
  const canMeeting = hasPermission('sys:oa:meeting:list');

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const dateFrom = month.startOf('month').format('YYYY-MM-DD');
      const dateTo = month.endOf('month').format('YYYY-MM-DD');
      const [page, reminderList] = await Promise.all([
        pageCalendars({
          current: 1,
          size: 200,
          keyword: keyword.trim() || undefined,
          visibility,
          dateFrom,
          dateTo,
        }),
        canReminder ? listCalendarReminders(30) : Promise.resolve([]),
      ]);
      setEntries(page.records);
      setReminders(reminderList);
      if (canMeeting && userId !== undefined) {
        const meetingPage = await pageMeetings({
          current: 1,
          size: 200,
          participantId: userId,
          dateFrom,
          dateTo,
        });
        setMeetings(meetingPage.records.filter((item) => item.status !== 'CANCELLED'));
      } else {
        setMeetings([]);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '日程加载失败');
      setEntries([]);
      setMeetings([]);
      setReminders([]);
    } finally {
      setLoading(false);
    }
  }, [month, keyword, visibility, canReminder, canMeeting, userId, message]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const dayEvents = useMemo<DayEvent[]>(() => {
    const fromEntries = entries.map<DayEvent>((entry) => ({
      key: `calendar-${entry.id}`,
      dateKey: dayjs(entry.startTime).format('YYYY-MM-DD'),
      title: entry.title,
      timeText: entry.allDay ? '全天' : `${dayjs(entry.startTime).format('HH:mm')} - ${dayjs(entry.endTime).format('HH:mm')}`,
      meeting: false,
      entry,
    }));
    const fromMeetings = meetings.map<DayEvent>((meeting) => ({
      key: `meeting-${meeting.id}`,
      dateKey: dayjs(meeting.startTime).format('YYYY-MM-DD'),
      title: `[会议] ${meeting.title}`,
      timeText: `${dayjs(meeting.startTime).format('HH:mm')} - ${dayjs(meeting.endTime).format('HH:mm')}`,
      meeting: true,
    }));
    return [...fromEntries, ...fromMeetings].sort((left, right) => left.timeText.localeCompare(right.timeText));
  }, [entries, meetings]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    const base = selected.hour(9).minute(0).second(0);
    form.setFieldsValue({
      timeRange: [base, base.add(1, 'hour')],
      visibility: 'PRIVATE',
      remindMinutes: 0,
    });
    setModal(true);
  };

  const openEdit = (entry: OaCalendarEntry) => {
    setEditing(entry);
    form.resetFields();
    form.setFieldsValue({
      title: entry.title,
      description: entry.description,
      location: entry.location,
      timeRange: [dayjs(entry.startTime), dayjs(entry.endTime)],
      allDay: entry.allDay ?? 0,
      visibility: entry.visibility,
      remindMinutes: entry.remindMinutes ?? 0,
    });
    setModal(true);
  };

  const save = async () => {
    const values = await form.validateFields();
    const payload: OaCalendarInput = {
      title: values.title.trim(),
      description: values.description?.trim() || undefined,
      location: values.location?.trim() || undefined,
      startTime: values.timeRange[0].format('YYYY-MM-DDTHH:mm:ss'),
      endTime: values.timeRange[1].format('YYYY-MM-DDTHH:mm:ss'),
      allDay: values.allDay ?? 0,
      visibility: values.visibility,
      remindMinutes: values.remindMinutes ?? 0,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateCalendar(editing.id, payload);
        message.success('日程已更新');
      } else {
        await createCalendar(payload);
        message.success('日程已创建');
      }
      setModal(false);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '日程保存失败');
    } finally {
      setSaving(false);
    }
  };

  const remove = async () => {
    if (!detail) {
      return;
    }
    try {
      await deleteCalendar(detail.id);
      message.success('日程已删除');
      setDetailOpen(false);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '日程删除失败');
    }
  };

  const openDetail = async (entry: OaCalendarEntry) => {
    setDetailOpen(true);
    try {
      setDetail(await getCalendar(entry.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '日程详情加载失败');
    }
  };

  const openShare = async (entry: OaCalendarEntry) => {
    setShareTarget(entry);
    shareForm.resetFields();
    try {
      const data = await getCalendar(entry.id);
      shareForm.setFieldsValue({
        users: (data.shares ?? []).map((item) => ({ userId: item.userId, userName: item.userName })),
      });
      setShareModal(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '共享名单加载失败');
    }
  };

  const saveShares = async () => {
    if (!shareTarget) {
      return;
    }
    const values = await shareForm.validateFields();
    const users: OaCalendarShare[] = (values.users ?? [])
      .filter((item) => item?.userId !== undefined)
      .map((item) => ({ userId: item.userId as number, userName: item.userName?.trim() || undefined }));
    setSaving(true);
    try {
      await replaceCalendarShares(shareTarget.id, users);
      message.success('共享名单已更新');
      setShareModal(false);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '共享名单保存失败');
    } finally {
      setSaving(false);
    }
  };

  const shareColumns: TableProps<OaCalendarShare>['columns'] = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 100 },
    { title: '姓名', dataIndex: 'userName', key: 'userName', render: (value?: string) => value ?? '-' },
  ];

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        {reminders.length > 0 ? (
          <Alert
            type="info"
            showIcon
            message={`未来 30 分钟有 ${reminders.length} 项日程提醒`}
            description={reminders
              .map((item) => `${dayjs(item.startTime).format('HH:mm')} ${item.title}`)
              .join('；')}
          />
        ) : null}
        <Space wrap>
          <Input
            allowClear
            placeholder="标题/地点/描述"
            style={{ width: 200 }}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
          />
          <Select
            allowClear
            placeholder="可见性"
            style={{ width: 130 }}
            value={visibility || undefined}
            options={Object.entries(VISIBILITY_META).map(([value, meta]) => ({ label: meta.text, value }))}
            onChange={(value) => setVisibility((value as OaCalendarVisibility) ?? '')}
          />
          {canManage ? (
            <Button type="primary" onClick={openCreate}>
              新建日程
            </Button>
          ) : null}
          <Button loading={loading} onClick={() => void loadData()}>
            刷新
          </Button>
        </Space>
        <Calendar
          value={selected}
          onSelect={(value) => {
            setSelected(value);
            if (value.month() !== month.month() || value.year() !== month.year()) {
              setMonth(value);
            }
          }}
          onPanelChange={(value) => {
            setSelected(value);
            setMonth(value);
          }}
          cellRender={(value, info) => {
            if (info.type !== 'date') {
              return info.originNode;
            }
            const list = dayEvents.filter((item) => item.dateKey === value.format('YYYY-MM-DD'));
            if (list.length === 0) {
              return info.originNode;
            }
            return (
              <div>
                {info.originNode}
                {list.slice(0, 2).map((item) => (
                  <Tooltip key={item.key} title={`${item.timeText} ${item.title}`}>
                    <div
                      onClick={(event) => {
                        event.stopPropagation();
                        if (item.entry) {
                          void openDetail(item.entry);
                        } else {
                          message.info('会议日程请在会议管理中查看');
                        }
                      }}
                      style={{
                        fontSize: 12,
                        lineHeight: '18px',
                        marginTop: 2,
                        padding: '0 4px',
                        overflow: 'hidden',
                        whiteSpace: 'nowrap',
                        textOverflow: 'ellipsis',
                        color: item.meeting ? '#1677ff' : '#389e0d',
                        cursor: 'pointer',
                      }}
                    >
                      {item.title}
                    </div>
                  </Tooltip>
                ))}
                {list.length > 2 ? (
                  <div style={{ fontSize: 12, color: '#999', padding: '0 4px' }}>+{list.length - 2}</div>
                ) : null}
              </div>
            );
          }}
        />
      </Space>
      <Modal
        open={modal}
        title={editing ? '编辑日程' : '新建日程'}
        width={640}
        onOk={() => void save()}
        confirmLoading={saving}
        onCancel={() => setModal(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="日程标题" rules={[{ required: true, message: '请输入日程标题' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="timeRange" label="时间" rules={[{ required: true, message: '请选择时间' }]}>
            <DatePicker.RangePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="visibility" label="可见性" style={{ flex: 1 }}>
              <Select
                options={Object.entries(VISIBILITY_META).map(([value, meta]) => ({ label: meta.text, value }))}
              />
            </Form.Item>
            <Form.Item name="remindMinutes" label="提醒" style={{ flex: 1 }}>
              <Select options={REMIND_OPTIONS} />
            </Form.Item>
            <Form.Item name="allDay" label="全天" style={{ flex: 1 }}>
              <Select
                options={[
                  { label: '否', value: 0 },
                  { label: '是', value: 1 },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="location" label="地点">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={4} maxLength={2000} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={shareModal}
        title="维护共享名单"
        onOk={() => void saveShares()}
        confirmLoading={saving}
        onCancel={() => setShareModal(false)}
        destroyOnClose
      >
        <Form form={shareForm} layout="vertical">
          <Form.Item label="共享用户">
            <Form.List name="users">
              {(fields, { add, remove }) => (
                <Space direction="vertical" style={{ width: '100%' }}>
                  {fields.map((field) => (
                    <Space key={field.key} size="small" style={{ display: 'flex' }}>
                      <Form.Item
                        name={[field.name, 'userId']}
                        rules={[{ required: true, message: '请输入用户ID' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <InputNumber placeholder="用户ID" min={1} precision={0} style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'userName']} style={{ marginBottom: 0 }}>
                        <Input placeholder="姓名" maxLength={64} style={{ width: 160 }} />
                      </Form.Item>
                      <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                        移除
                      </Button>
                    </Space>
                  ))}
                  <Button type="dashed" onClick={() => add()}>
                    添加共享用户
                  </Button>
                </Space>
              )}
            </Form.List>
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        width={560}
        title={detail?.title ?? '日程详情'}
        onClose={() => setDetailOpen(false)}
        extra={
          detail && detail.editable && canManage ? (
            <Space>
              <Button size="small" onClick={() => openEdit(detail)}>
                编辑
              </Button>
              <Button size="small" onClick={() => void openShare(detail)}>
                共享
              </Button>
              <Popconfirm title="确认删除该日程？" onConfirm={() => void remove()}>
                <Button size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </Space>
          ) : null
        }
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={2} bordered size="small">
              <Descriptions.Item label="时间" span={2}>
                {timeText(detail.startTime)} ~ {timeText(detail.endTime)}
              </Descriptions.Item>
              <Descriptions.Item label="可见性">
                <Tag color={VISIBILITY_META[detail.visibility]?.color ?? 'default'}>
                  {VISIBILITY_META[detail.visibility]?.text ?? detail.visibility}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="所有者">{detail.ownerName ?? detail.ownerId}</Descriptions.Item>
              <Descriptions.Item label="地点" span={2}>
                {detail.location || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {detail.description || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="提醒" span={2}>
                {detail.remindMinutes ? `提前 ${detail.remindMinutes} 分钟` : '不提醒'}
              </Descriptions.Item>
            </Descriptions>
            {detail.editable ? (
              <Table<OaCalendarShare>
                rowKey="userId"
                size="small"
                pagination={false}
                columns={shareColumns}
                dataSource={detail.shares ?? []}
                locale={{ emptyText: '未设置共享名单' }}
              />
            ) : null}
          </Space>
        ) : null}
      </Drawer>
    </>
  );
}
