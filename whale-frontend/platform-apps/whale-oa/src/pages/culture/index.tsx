// OA 企业文化：今日生日/周年祝福、文化动态浏览与发布
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  DatePicker,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createCultureEvent,
  createCultureWish,
  deleteCultureEvent,
  getCultureEvent,
  listBirthdays,
  pageCultureEvents,
  publishCultureEvent,
  updateCultureEvent,
  type BirthdayCelebration,
  type CultureEvent,
  type CultureEventInput,
  type CultureEventStatus,
  type CultureEventType,
} from '../../api/culture';
import { hasPermission } from '../../utils/permission';

/** 动态类型文案 */
const EVENT_TYPE_META: Record<CultureEventType, { text: string; color: string }> = {
  MILESTONE: { text: '里程碑', color: 'gold' },
  ACTIVITY: { text: '活动', color: 'blue' },
  NEWS: { text: '新闻', color: 'cyan' },
};

/** 动态状态文案 */
const EVENT_STATUS_META: Record<CultureEventStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  PUBLISHED: { text: '已发布', color: 'success' },
  ARCHIVED: { text: '已归档', color: 'purple' },
};

/** 动态表单值 */
interface EventFormValues {
  title: string;
  eventType: CultureEventType;
  eventDate: Dayjs;
  content?: string;
  remark?: string;
}

/** 祝福表单值 */
interface WishFormValues {
  content: string;
}

/** 时间展示 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 生日/周年卡片描述 */
function celebrationText(item: BirthdayCelebration): string {
  if (item.type === 'BIRTHDAY') {
    return `生日：${item.birthday || '-'}`;
  }
  return `入职 ${item.hireDate || '-'}（${item.years ?? 0} 周年）`;
}

export default function CulturePage() {
  const { message } = App.useApp();

  const [birthdays, setBirthdays] = useState<BirthdayCelebration[]>([]);
  const [events, setEvents] = useState<CultureEvent[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<CultureEventStatus | ''>('');
  const [eventType, setEventType] = useState<CultureEventType | ''>('');
  const [loading, setLoading] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<CultureEvent | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<EventFormValues>();

  const [wishTarget, setWishTarget] = useState<BirthdayCelebration | null>(null);
  const [wishing, setWishing] = useState(false);
  const [wishForm] = Form.useForm<WishFormValues>();

  const [detail, setDetail] = useState<CultureEvent | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);

  const canManage = hasPermission('sys:oa:culture:add');

  const loadEvents = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageCultureEvents({
        current,
        size: 10,
        keyword: keyword.trim() || undefined,
        status: status || undefined,
        eventType: eventType || undefined,
      });
      setEvents(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文化动态查询失败');
      setEvents([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, eventType, message]);

  useEffect(() => {
    void loadEvents();
  }, [loadEvents]);

  const loadBirthdays = useCallback(async () => {
    try {
      setBirthdays(await listBirthdays(dayjs().format('YYYY-MM-DD')));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '生日/周年查询失败');
      setBirthdays([]);
    }
  }, [message]);

  useEffect(() => {
    void loadBirthdays();
  }, [loadBirthdays]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ eventType: 'ACTIVITY', eventDate: dayjs() });
    setModalOpen(true);
  };

  const openEdit = (record: CultureEvent) => {
    setEditing(record);
    form.resetFields();
    form.setFieldsValue({
      title: record.title,
      eventType: record.eventType,
      eventDate: record.eventDate ? dayjs(record.eventDate) : dayjs(),
      content: record.content,
      remark: record.remark,
    });
    setModalOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const payload: CultureEventInput = {
      title: values.title.trim(),
      eventType: values.eventType,
      eventDate: values.eventDate.format('YYYY-MM-DD'),
      content: values.content?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateCultureEvent(editing.id, payload);
        message.success('文化动态已更新');
      } else {
        await createCultureEvent(payload);
        message.success('文化动态已创建');
      }
      setModalOpen(false);
      await loadEvents();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文化动态保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handlePublish = async (record: CultureEvent) => {
    try {
      await publishCultureEvent(record.id);
      message.success('文化动态已发布');
      await loadEvents();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文化动态发布失败');
    }
  };

  const handleDelete = async (record: CultureEvent) => {
    try {
      await deleteCultureEvent(record.id);
      message.success('文化动态已删除');
      await loadEvents();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文化动态删除失败');
    }
  };

  const openDetail = async (record: CultureEvent) => {
    setDetailOpen(true);
    setDetail(null);
    try {
      setDetail(await getCultureEvent(record.id));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '文化动态详情加载失败');
    }
  };

  const openWish = (item: BirthdayCelebration) => {
    setWishTarget(item);
    wishForm.resetFields();
  };

  const handleWish = async () => {
    if (!wishTarget) {
      return;
    }
    const values = await wishForm.validateFields();
    setWishing(true);
    try {
      await createCultureWish({
        wishType: wishTarget.type,
        targetUserId: wishTarget.userId,
        targetName: wishTarget.userName,
        content: values.content.trim(),
      });
      message.success('祝福已送达');
      setWishTarget(null);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '祝福发送失败');
    } finally {
      setWishing(false);
    }
  };

  const columns: TableProps<CultureEvent>['columns'] = [
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'eventType',
      key: 'eventType',
      width: 100,
      render: (value: CultureEventType) => {
        const meta = EVENT_TYPE_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '日期',
      dataIndex: 'eventDate',
      key: 'eventDate',
      width: 120,
      render: (value?: string) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: CultureEventStatus) => {
        const meta = EVENT_STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      key: 'publishTime',
      width: 160,
      render: (value?: string) => timeText(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {canManage && record.status === 'DRAFT' ? (
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canManage && record.status === 'DRAFT' ? (
            <Popconfirm title="确认发布该动态？" onConfirm={() => void handlePublish(record)}>
              <Button type="link" size="small">
                发布
              </Button>
            </Popconfirm>
          ) : null}
          {canManage && record.status !== 'PUBLISHED' ? (
            <Popconfirm title="确认删除该动态？" onConfirm={() => void handleDelete(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        size="small"
        title="今日生日 / 周年"
        extra={<span style={{ color: '#888' }}>{dayjs().format('YYYY-MM-DD')}</span>}
      >
        {birthdays.length > 0 ? (
          <Space wrap size="middle">
            {birthdays.map((item) => (
              <Card
                key={`${item.type}-${item.userId}`}
                size="small"
                style={{ width: 240 }}
                title={item.userName ?? String(item.userId)}
                extra={
                  <Tag color={item.type === 'BIRTHDAY' ? 'red' : 'blue'}>
                    {item.type === 'BIRTHDAY' ? '生日' : '周年'}
                  </Tag>
                }
                actions={[
                  <Button key="wish" type="link" size="small" onClick={() => openWish(item)}>
                    送祝福
                  </Button>,
                ]}
              >
                {celebrationText(item)}
              </Card>
            ))}
          </Space>
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="今日暂无生日或周年" />
        )}
      </Card>
      <Space wrap>
        <Input
          allowClear
          placeholder="动态标题"
          style={{ width: 200 }}
          value={keyword}
          onChange={(event) => {
            setKeyword(event.target.value);
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="类型"
          style={{ width: 120 }}
          value={eventType || undefined}
          options={Object.entries(EVENT_TYPE_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setEventType((value as CultureEventType) ?? '');
            setCurrent(1);
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          value={status || undefined}
          options={Object.entries(EVENT_STATUS_META).map(([value, meta]) => ({
            label: meta.text,
            value,
          }))}
          onChange={(value) => {
            setStatus((value as CultureEventStatus) ?? '');
            setCurrent(1);
          }}
        />
        {canManage ? (
          <Button type="primary" onClick={openCreate}>
            发布动态
          </Button>
        ) : null}
        <Button onClick={() => void loadEvents()}>刷新</Button>
      </Space>
      <Table<CultureEvent>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={events}
        scroll={{ x: 1000 }}
        pagination={{
          current,
          pageSize: 10,
          total,
          onChange: (page) => setCurrent(page),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑文化动态' : '发布文化动态'}
        width={640}
        onOk={() => void handleSave()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={128} />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="eventType"
              label="类型"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择类型' }]}
            >
              <Select
                options={Object.entries(EVENT_TYPE_META).map(([value, meta]) => ({
                  label: meta.text,
                  value,
                }))}
              />
            </Form.Item>
            <Form.Item
              name="eventDate"
              label="日期"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请选择日期' }]}
            >
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="content" label="正文">
            <Input.TextArea rows={5} maxLength={5000} showCount placeholder="选填" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={wishTarget !== null}
        title={wishTarget ? `送祝福：${wishTarget.userName ?? wishTarget.userId}` : '送祝福'}
        onOk={() => void handleWish()}
        confirmLoading={wishing}
        onCancel={() => setWishTarget(null)}
        destroyOnClose
      >
        <Form form={wishForm} layout="vertical">
          <Form.Item
            name="content"
            label="祝福内容"
            rules={[{ required: true, message: '请输入祝福内容' }]}
          >
            <Input.TextArea rows={4} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        width={640}
        title={detail?.title ?? '文化动态详情'}
        onClose={() => setDetailOpen(false)}
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions
              column={2}
              size="small"
              bordered
              items={[
                {
                  key: 'eventType',
                  label: '类型',
                  children: EVENT_TYPE_META[detail.eventType]?.text ?? detail.eventType,
                },
                {
                  key: 'status',
                  label: '状态',
                  children: EVENT_STATUS_META[detail.status]?.text ?? detail.status,
                },
                { key: 'eventDate', label: '日期', children: detail.eventDate || '-' },
                { key: 'publisher', label: '发布人', children: detail.publisherName || '-' },
                { key: 'publishTime', label: '发布时间', children: timeText(detail.publishTime) },
                { key: 'remark', label: '备注', children: detail.remark || '-' },
              ]}
            />
            <div>
              <div style={{ fontWeight: 600, marginBottom: 8 }}>正文</div>
              <div style={{ whiteSpace: 'pre-wrap' }}>{detail.content || '-'}</div>
            </div>
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
