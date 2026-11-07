// OA 协同 - 会议：会议室主数据、会议预约、参会人、纪要与行动项
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  Checkbox,
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
  Tabs,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  changeMeetingStatus,
  changeParticipantStatus,
  createMeeting,
  createRoom,
  deleteRoom,
  getMeeting,
  listRooms,
  pageMeetings,
  pageRooms,
  saveMeetingMinute,
  updateMeeting,
  updateRoom,
  type OaMeeting,
  type OaMeetingActionInput,
  type OaMeetingDetail,
  type OaMeetingInput,
  type OaMeetingParticipant,
  type OaMeetingParticipantInput,
  type OaMeetingRoom,
  type OaMeetingRoomInput,
  type OaMeetingStatus,
  type OaParticipantStatus,
} from '../../api/collab';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 会议状态文案 */
const MEETING_STATUS_META: Record<OaMeetingStatus, { text: string; color: string }> = {
  SCHEDULED: { text: '已预约', color: 'processing' },
  FINISHED: { text: '已结束', color: 'success' },
  CANCELLED: { text: '已取消', color: 'default' },
};

/** 参会状态文案 */
const PARTICIPANT_STATUS_META: Record<OaParticipantStatus, { text: string; color: string }> = {
  INVITED: { text: '待参会', color: 'processing' },
  ATTENDED: { text: '已出席', color: 'success' },
  ABSENT: { text: '缺席', color: 'error' },
};

/** 会议室表单值 */
interface RoomFormValues {
  name: string;
  location?: string;
  capacity?: number;
  status: string;
  remark?: string;
}

/** 参会人表单值 */
interface ParticipantFormValue {
  userId?: number;
  userName?: string;
  required?: number;
  remark?: string;
}

/** 会议表单值 */
interface MeetingFormValues {
  title: string;
  roomId?: number;
  organizer?: string;
  organizerId?: number;
  deptId?: number;
  timeRange: [Dayjs, Dayjs];
  agenda?: string;
  remark?: string;
  participants: ParticipantFormValue[];
}

/** 纪要表单值 */
interface MinuteFormValues {
  content: string;
  author?: string;
  actions?: {
    content: string;
    ownerId: number;
    ownerName?: string;
    dueDate?: Dayjs;
    createTask?: boolean;
  }[];
}

/** 时间格式化为展示文本 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

export default function CollabMeetingPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();

  const [rooms, setRooms] = useState<OaMeetingRoom[]>([]);
  const [roomTotal, setRoomTotal] = useState(0);
  const [roomPage, setRoomPage] = useState(1);
  const [roomKeyword, setRoomKeyword] = useState('');
  const [roomStatus, setRoomStatus] = useState<'ENABLED' | 'DISABLED' | ''>('');
  const [roomOptions, setRoomOptions] = useState<OaMeetingRoom[]>([]);

  const [meetings, setMeetings] = useState<OaMeeting[]>([]);
  const [meetingTotal, setMeetingTotal] = useState(0);
  const [meetingPage, setMeetingPage] = useState(1);
  const [meetingKeyword, setMeetingKeyword] = useState('');
  const [meetingStatus, setMeetingStatus] = useState<OaMeetingStatus | ''>('');
  const [loading, setLoading] = useState(false);

  const [roomModal, setRoomModal] = useState(false);
  const [editingRoom, setEditingRoom] = useState<OaMeetingRoom | null>(null);
  const [meetingModal, setMeetingModal] = useState(false);
  const [editingMeeting, setEditingMeeting] = useState<OaMeeting | null>(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detail, setDetail] = useState<OaMeetingDetail | null>(null);
  const [saving, setSaving] = useState(false);

  const [roomForm] = Form.useForm<RoomFormValues>();
  const [meetingForm] = Form.useForm<MeetingFormValues>();
  const [minuteForm] = Form.useForm<MinuteFormValues>();

  const canRoomManage = hasPermission('sys:oa:meeting:room:manage');
  const canManage = hasPermission('sys:oa:meeting:manage');

  const loadRooms = useCallback(async () => {
    try {
      const page = await pageRooms({
        current: roomPage,
        size: 10,
        keyword: roomKeyword.trim() || undefined,
        status: roomStatus,
      });
      setRooms(page.records);
      setRoomTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会议室查询失败');
      setRooms([]);
      setRoomTotal(0);
    }
  }, [roomPage, roomKeyword, roomStatus, message]);

  const loadRoomOptions = useCallback(async () => {
    try {
      setRoomOptions(await listRooms());
    } catch (error) {
      message.error(error instanceof Error ? error.message : '启用会议室查询失败');
    }
  }, [message]);

  const loadMeetings = useCallback(async () => {
    try {
      const page = await pageMeetings({
        current: meetingPage,
        size: 10,
        keyword: meetingKeyword.trim() || undefined,
        status: meetingStatus,
      });
      setMeetings(page.records);
      setMeetingTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会议查询失败');
      setMeetings([]);
      setMeetingTotal(0);
    }
  }, [meetingPage, meetingKeyword, meetingStatus, message]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([loadRooms(), loadRoomOptions(), loadMeetings()]);
    } finally {
      setLoading(false);
    }
  }, [loadRooms, loadRoomOptions, loadMeetings]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const fillMinuteForm = useCallback(
    (data: OaMeetingDetail) => {
      minuteForm.setFieldsValue({
        content: data.minute?.content,
        author: data.minute?.author ?? data.organizer,
        actions: data.actions.map((item) => ({
          content: item.content,
          ownerId: item.ownerId,
          ownerName: item.ownerName,
          dueDate: item.dueDate ? dayjs(item.dueDate) : undefined,
          createTask: Boolean(item.taskId),
        })),
      });
    },
    [minuteForm],
  );

  const loadDetail = useCallback(
    async (id: number) => {
      try {
        const data = await getMeeting(id);
        setDetail(data);
        fillMinuteForm(data);
        return data;
      } catch (error) {
        message.error(error instanceof Error ? error.message : '会议详情加载失败');
        return null;
      }
    },
    [message, fillMinuteForm],
  );

  const openRoom = (record: OaMeetingRoom | null) => {
    setEditingRoom(record);
    roomForm.resetFields();
    roomForm.setFieldsValue({
      name: record?.name,
      location: record?.location,
      capacity: record?.capacity ?? 10,
      status: record?.status ?? 'ENABLED',
      remark: record?.remark,
    });
    setRoomModal(true);
  };

  const saveRoom = async () => {
    const values = await roomForm.validateFields();
    const payload: OaMeetingRoomInput = {
      name: values.name.trim(),
      location: values.location?.trim() || undefined,
      capacity: values.capacity,
      status: values.status,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editingRoom) {
        await updateRoom(editingRoom.id, payload);
        message.success('会议室已更新');
      } else {
        await createRoom(payload);
        message.success('会议室已创建');
      }
      setRoomModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会议室保存失败');
    } finally {
      setSaving(false);
    }
  };

  const removeRoom = async (record: OaMeetingRoom) => {
    try {
      await deleteRoom(record.id);
      message.success('会议室已删除');
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会议室删除失败');
    }
  };

  const openMeeting = async (record: OaMeeting | null) => {
    setEditingMeeting(record);
    meetingForm.resetFields();
    let participants: ParticipantFormValue[] = [{ required: 1 }];
    if (record) {
      const data = await loadDetail(record.id);
      if (data) {
        participants = data.participants.length
          ? data.participants.map((item) => ({
              userId: item.userId,
              userName: item.userName,
              required: item.required ?? 1,
              remark: item.remark,
            }))
          : [{ required: 1 }];
      }
    }
    meetingForm.setFieldsValue({
      title: record?.title,
      roomId: record?.roomId,
      organizer: record?.organizer ?? user?.nickname ?? user?.username,
      organizerId: record?.organizerId ?? (typeof user?.userId === 'number' ? user.userId : undefined),
      deptId: record?.deptId,
      timeRange: record
        ? [dayjs(record.startTime), dayjs(record.endTime)]
        : [dayjs().hour(9).minute(0), dayjs().hour(10).minute(0)],
      agenda: record?.agenda,
      remark: record?.remark,
      participants,
    });
    setMeetingModal(true);
  };

  const saveMeeting = async () => {
    const values = await meetingForm.validateFields();
    const participants = (values.participants ?? [])
      .filter((item) => item?.userId !== undefined)
      .map<OaMeetingParticipantInput>((item) => ({
        userId: item.userId as number,
        userName: item.userName?.trim() || undefined,
        required: item.required ?? 1,
        remark: item.remark?.trim() || undefined,
      }));
    if (participants.length === 0) {
      message.error('请至少添加一名参会人');
      return;
    }
    const payload: OaMeetingInput = {
      title: values.title.trim(),
      roomId: values.roomId,
      organizer: values.organizer?.trim() || undefined,
      organizerId: values.organizerId,
      deptId: values.deptId,
      startTime: values.timeRange[0].format('YYYY-MM-DDTHH:mm:ss'),
      endTime: values.timeRange[1].format('YYYY-MM-DDTHH:mm:ss'),
      agenda: values.agenda?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
      participants,
    };
    setSaving(true);
    try {
      if (editingMeeting) {
        await updateMeeting(editingMeeting.id, payload);
        message.success('会议已更新');
      } else {
        await createMeeting(payload);
        message.success('会议已创建');
      }
      setMeetingModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会议保存失败');
    } finally {
      setSaving(false);
    }
  };

  const openDetail = async (record: OaMeeting) => {
    setDetailOpen(true);
    await loadDetail(record.id);
  };

  const changeStatus = async (record: OaMeeting, status: OaMeetingStatus) => {
    try {
      await changeMeetingStatus(record.id, status);
      message.success('会议状态已更新');
      await refresh();
      if (detailOpen && detail?.id === record.id) {
        await loadDetail(record.id);
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会议状态更新失败');
    }
  };

  const markParticipant = async (userId: number, status: OaParticipantStatus) => {
    if (!detail) {
      return;
    }
    try {
      await changeParticipantStatus(detail.id, userId, status);
      await loadDetail(detail.id);
      message.success('参会状态已更新');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '参会状态更新失败');
    }
  };

  const saveMinute = async () => {
    if (!detail) {
      return;
    }
    const values = await minuteForm.validateFields();
    const actions = (values.actions ?? [])
      .filter((item) => item?.content && item?.ownerId)
      .map<OaMeetingActionInput>((item) => ({
        content: item.content.trim(),
        ownerId: item.ownerId,
        ownerName: item.ownerName?.trim() || undefined,
        dueDate: item.dueDate?.format('YYYY-MM-DD'),
        createTask: Boolean(item.createTask),
      }));
    setSaving(true);
    try {
      const data = await saveMeetingMinute(detail.id, {
        content: values.content.trim(),
        author: values.author?.trim() || undefined,
        authorId: typeof user?.userId === 'number' ? user.userId : undefined,
        actions,
      });
      setDetail(data);
      fillMinuteForm(data);
      message.success('会议纪要已保存');
    } catch (error) {
      message.error(error instanceof Error ? error.message : '会议纪要保存失败');
    } finally {
      setSaving(false);
    }
  };

  const roomColumns: TableProps<OaMeetingRoom>['columns'] = [
    { title: '会议室', dataIndex: 'name', key: 'name', width: 180 },
    { title: '位置', dataIndex: 'location', key: 'location', ellipsis: true },
    { title: '容纳人数', dataIndex: 'capacity', key: 'capacity', width: 100, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={value === 'ENABLED' ? 'success' : 'default'}>
          {value === 'ENABLED' ? '启用' : '停用'}
        </Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          {canRoomManage ? (
            <Button type="link" size="small" onClick={() => openRoom(record)}>
              编辑
            </Button>
          ) : null}
          {canRoomManage ? (
            <Popconfirm title="确认删除该会议室？" onConfirm={() => void removeRoom(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const meetingColumns: TableProps<OaMeeting>['columns'] = [
    { title: '会议单号', dataIndex: 'code', key: 'code', width: 160 },
    { title: '主题', dataIndex: 'title', key: 'title', ellipsis: true },
    { title: '会议室', dataIndex: 'roomName', key: 'roomName', width: 130, render: (value?: string) => value ?? '-' },
    { title: '组织人', dataIndex: 'organizer', key: 'organizer', width: 110, render: (value?: string) => value ?? '-' },
    {
      title: '会议时间',
      key: 'time',
      width: 300,
      render: (_value, record) => `${timeText(record.startTime)} ~ ${timeText(record.endTime)}`,
    },
    { title: '参会人', dataIndex: 'participantCount', key: 'participantCount', width: 90, align: 'right' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaMeetingStatus) => {
        const meta = MEETING_STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 230,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {record.status === 'SCHEDULED' && canManage ? (
            <Button type="link" size="small" onClick={() => void openMeeting(record)}>
              编辑
            </Button>
          ) : null}
          {record.status === 'SCHEDULED' && canManage ? (
            <Button type="link" size="small" onClick={() => void changeStatus(record, 'FINISHED')}>
              结束
            </Button>
          ) : null}
          {record.status === 'SCHEDULED' && canManage ? (
            <Popconfirm title="确认取消该会议？" onConfirm={() => void changeStatus(record, 'CANCELLED')}>
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Tabs
        items={[
          {
            key: 'rooms',
            label: '会议室',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space wrap>
                  <Input
                    allowClear
                    placeholder="会议室名称/位置"
                    style={{ width: 200 }}
                    value={roomKeyword}
                    onChange={(event) => {
                      setRoomKeyword(event.target.value);
                      setRoomPage(1);
                    }}
                  />
                  <Select
                    allowClear
                    placeholder="状态"
                    style={{ width: 130 }}
                    value={roomStatus || undefined}
                    options={[
                      { label: '启用', value: 'ENABLED' },
                      { label: '停用', value: 'DISABLED' },
                    ]}
                    onChange={(value) => {
                      setRoomStatus((value as 'ENABLED' | 'DISABLED') ?? '');
                      setRoomPage(1);
                    }}
                  />
                  {canRoomManage ? (
                    <Button type="primary" onClick={() => openRoom(null)}>
                      新增会议室
                    </Button>
                  ) : null}
                  <Button onClick={() => void refresh()}>刷新</Button>
                </Space>
                <Table<OaMeetingRoom>
                  rowKey="id"
                  loading={loading}
                  columns={roomColumns}
                  dataSource={rooms}
                  scroll={{ x: 900 }}
                  pagination={{
                    current: roomPage,
                    pageSize: 10,
                    total: roomTotal,
                    onChange: (page) => setRoomPage(page),
                  }}
                />
              </Space>
            ),
          },
          {
            key: 'meetings',
            label: '会议',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space wrap>
                  <Input
                    allowClear
                    placeholder="单号/主题"
                    style={{ width: 200 }}
                    value={meetingKeyword}
                    onChange={(event) => {
                      setMeetingKeyword(event.target.value);
                      setMeetingPage(1);
                    }}
                  />
                  <Select
                    allowClear
                    placeholder="状态"
                    style={{ width: 130 }}
                    value={meetingStatus || undefined}
                    options={Object.entries(MEETING_STATUS_META).map(([value, meta]) => ({
                      label: meta.text,
                      value,
                    }))}
                    onChange={(value) => {
                      setMeetingStatus((value as OaMeetingStatus) ?? '');
                      setMeetingPage(1);
                    }}
                  />
                  {canManage ? (
                    <Button type="primary" onClick={() => void openMeeting(null)}>
                      新建会议
                    </Button>
                  ) : null}
                  <Button onClick={() => void refresh()}>刷新</Button>
                </Space>
                <Table<OaMeeting>
                  rowKey="id"
                  loading={loading}
                  columns={meetingColumns}
                  dataSource={meetings}
                  scroll={{ x: 1400 }}
                  pagination={{
                    current: meetingPage,
                    pageSize: 10,
                    total: meetingTotal,
                    onChange: (page) => setMeetingPage(page),
                  }}
                />
              </Space>
            ),
          },
        ]}
      />
      <Modal
        open={roomModal}
        title={editingRoom ? '编辑会议室' : '新增会议室'}
        onOk={() => void saveRoom()}
        confirmLoading={saving}
        onCancel={() => setRoomModal(false)}
        destroyOnClose
      >
        <Form form={roomForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="name"
              label="会议室名称"
              rules={[{ required: true, message: '请输入会议室名称' }]}
              style={{ flex: 2 }}
            >
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="capacity" label="容纳人数" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} max={1000} precision={0} />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ flex: 1 }}>
              <Select
                options={[
                  { label: '启用', value: 'ENABLED' },
                  { label: '停用', value: 'DISABLED' },
                ]}
              />
            </Form.Item>
          </Space>
          <Form.Item name="location" label="位置">
            <Input maxLength={128} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={meetingModal}
        title={editingMeeting ? '编辑会议' : '新建会议'}
        width={720}
        onOk={() => void saveMeeting()}
        confirmLoading={saving}
        onCancel={() => setMeetingModal(false)}
        destroyOnClose
      >
        <Form form={meetingForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="title"
              label="会议主题"
              rules={[{ required: true, message: '请输入会议主题' }]}
              style={{ flex: 2 }}
            >
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="roomId" label="会议室" style={{ flex: 1 }}>
              <Select
                allowClear
                options={roomOptions.map((item) => ({ label: item.name, value: item.id }))}
              />
            </Form.Item>
          </Space>
          <Form.Item name="timeRange" label="会议时间" rules={[{ required: true, message: '请选择会议时间' }]}>
            <DatePicker.RangePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="organizer" label="组织人" style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="organizerId" label="组织人ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item name="deptId" label="部门ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
          </Space>
          <Form.Item name="agenda" label="会议议程">
            <Input.TextArea rows={3} maxLength={1000} showCount />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
          <Form.Item label="参会人" required>
            <Form.List name="participants">
              {(fields, { add, remove }) => (
                <Space direction="vertical" style={{ width: '100%' }}>
                  {fields.map((field) => (
                    <Space key={field.key} size="small" style={{ display: 'flex' }}>
                      <Form.Item
                        name={[field.name, 'userId']}
                        rules={[{ required: true, message: '请输入用户ID' }]}
                        style={{ marginBottom: 0 }}
                      >
                        <InputNumber placeholder="用户ID" min={1} precision={0} style={{ width: 130 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'userName']} style={{ marginBottom: 0 }}>
                        <Input placeholder="姓名" maxLength={64} style={{ width: 140 }} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'required']} initialValue={1} style={{ marginBottom: 0 }}>
                        <Select
                          style={{ width: 100 }}
                          options={[
                            { label: '必到', value: 1 },
                            { label: '可选', value: 0 },
                          ]}
                        />
                      </Form.Item>
                      <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                        移除
                      </Button>
                    </Space>
                  ))}
                  <Button type="dashed" onClick={() => add({ required: 1 })}>
                    添加参会人
                  </Button>
                </Space>
              )}
            </Form.List>
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={detailOpen}
        width={760}
        title={detail ? `${detail.code} ${detail.title}` : '会议详情'}
        onClose={() => setDetailOpen(false)}
      >
        {detail ? (
          <Tabs
            items={[
              {
                key: 'info',
                label: '基本信息',
                children: (
                  <Descriptions column={2} bordered size="small">
                    <Descriptions.Item label="会议室">{detail.roomName ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label="组织人">{detail.organizer ?? '-'}</Descriptions.Item>
                    <Descriptions.Item label="开始时间">{timeText(detail.startTime)}</Descriptions.Item>
                    <Descriptions.Item label="结束时间">{timeText(detail.endTime)}</Descriptions.Item>
                    <Descriptions.Item label="状态">
                      <Tag color={MEETING_STATUS_META[detail.status]?.color ?? 'default'}>
                        {MEETING_STATUS_META[detail.status]?.text ?? detail.status}
                      </Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="参会人数">{detail.participantCount ?? 0}</Descriptions.Item>
                    <Descriptions.Item label="议程" span={2}>
                      {detail.agenda || '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="备注" span={2}>
                      {detail.remark || '-'}
                    </Descriptions.Item>
                  </Descriptions>
                ),
              },
              {
                key: 'participants',
                label: `参会人（${detail.participants.length}）`,
                children: (
                  <Table<OaMeetingParticipant>
                    rowKey="userId"
                    size="small"
                    pagination={false}
                    dataSource={detail.participants}
                    columns={[
                      { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 100 },
                      { title: '姓名', dataIndex: 'userName', key: 'userName', render: (value?: string) => value ?? '-' },
                      {
                        title: '必到',
                        dataIndex: 'required',
                        key: 'required',
                        width: 80,
                        render: (value?: number) => (value ? '是' : '否'),
                      },
                      {
                        title: '状态',
                        dataIndex: 'status',
                        key: 'status',
                        width: 100,
                        render: (value: OaParticipantStatus) => (
                          <Tag color={PARTICIPANT_STATUS_META[value]?.color ?? 'default'}>
                            {PARTICIPANT_STATUS_META[value]?.text ?? value}
                          </Tag>
                        ),
                      },
                      {
                        title: '操作',
                        key: 'action',
                        width: 140,
                        render: (_value, record) => (
                          <Space size={4}>
                            {canManage && detail.status !== 'CANCELLED' && record.status !== 'ATTENDED' ? (
                              <Button
                                type="link"
                                size="small"
                                onClick={() => void markParticipant(record.userId, 'ATTENDED')}
                              >
                                出席
                              </Button>
                            ) : null}
                            {canManage && detail.status !== 'CANCELLED' && record.status !== 'ABSENT' ? (
                              <Button
                                type="link"
                                size="small"
                                onClick={() => void markParticipant(record.userId, 'ABSENT')}
                              >
                                缺席
                              </Button>
                            ) : null}
                          </Space>
                        ),
                      },
                    ]}
                  />
                ),
              },
              {
                key: 'minute',
                label: '会议纪要',
                children: (
                  <Form form={minuteForm} layout="vertical">
                    <Form.Item
                      name="content"
                      label="纪要内容"
                      rules={[{ required: true, message: '请输入纪要内容' }]}
                    >
                      <Input.TextArea rows={5} maxLength={2000} showCount />
                    </Form.Item>
                    <Form.Item name="author" label="纪要作者">
                      <Input maxLength={64} />
                    </Form.Item>
                    <Form.Item label="行动项">
                      <Form.List name="actions">
                        {(fields, { add, remove }) => (
                          <Space direction="vertical" style={{ width: '100%' }}>
                            {fields.map((field) => (
                              <Space key={field.key} wrap size="small" style={{ display: 'flex' }}>
                                <Form.Item
                                  name={[field.name, 'content']}
                                  rules={[{ required: true, message: '请输入行动项' }]}
                                  style={{ marginBottom: 0 }}
                                >
                                  <Input placeholder="行动项内容" maxLength={255} style={{ width: 200 }} />
                                </Form.Item>
                                <Form.Item
                                  name={[field.name, 'ownerId']}
                                  rules={[{ required: true, message: '请输入负责人ID' }]}
                                  style={{ marginBottom: 0 }}
                                >
                                  <InputNumber placeholder="负责人ID" min={1} precision={0} style={{ width: 120 }} />
                                </Form.Item>
                                <Form.Item name={[field.name, 'ownerName']} style={{ marginBottom: 0 }}>
                                  <Input placeholder="负责人姓名" maxLength={64} style={{ width: 120 }} />
                                </Form.Item>
                                <Form.Item name={[field.name, 'dueDate']} style={{ marginBottom: 0 }}>
                                  <DatePicker placeholder="截止日期" style={{ width: 130 }} />
                                </Form.Item>
                                <Form.Item
                                  name={[field.name, 'createTask']}
                                  valuePropName="checked"
                                  style={{ marginBottom: 0 }}
                                >
                                  <Checkbox>生成任务</Checkbox>
                                </Form.Item>
                                <Button type="link" size="small" danger onClick={() => remove(field.name)}>
                                  移除
                                </Button>
                              </Space>
                            ))}
                            <Button
                              type="dashed"
                              onClick={() => add({ createTask: true })}
                              disabled={detail.status !== 'SCHEDULED'}
                            >
                              添加行动项
                            </Button>
                          </Space>
                        )}
                      </Form.List>
                    </Form.Item>
                    {canManage && detail.status === 'SCHEDULED' ? (
                      <Button type="primary" loading={saving} onClick={() => void saveMinute()}>
                        保存纪要
                      </Button>
                    ) : null}
                  </Form>
                ),
              },
            ]}
          />
        ) : null}
      </Drawer>
    </>
  );
}
