// 培训班详情页：班级信息 + 场次管理（草稿可加删）+ 必修分配 + 签到明细 + 讲师课时。
//
// 规则提示与后端一致：发布前需至少 1 个场次；发布后场次与头信息冻结；
// 学时台账由场次课时自动累加，合计等于各场次之和。

import {
  Button,
  DatePicker,
  Descriptions,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { hasPermission } from '../api/client';
import {
  addSession,
  assignLearners,
  changeTrainingClassStatus,
  checkin,
  lecturerHours,
  listCheckins,
  removeSession,
  supplementCheckin,
  trainingClassDetail,
  trainingCompletion,
  type CheckinItem,
  type CompletionItem,
  type LecturerHoursItem,
  type SessionItem,
  type TrainingClassDetail,
  type TrainingCompletion,
} from '../api/training';

/** 完成认定依据文案 */
const BASIS_TAG: Record<string, { color: string; label: string }> = {
  ATTENDANCE: { color: 'green', label: '全部签到' },
  COURSE_PROGRESS: { color: 'green', label: '课程学完' },
  ATTENDANCE_AND_COURSE: { color: 'green', label: '签到 + 课程' },
  ATTENDANCE_PENDING: { color: 'orange', label: '签到未齐' },
  COURSE_PROGRESS_PENDING: { color: 'orange', label: '课程未学完' },
  NO_BASIS: { color: 'default', label: '无法认定' },
};

/** 状态标签 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  CLOSED: { color: 'orange', label: '已结课' },
};

/** 场次表单值 */
interface SessionValues {
  title: string;
  range: [Dayjs, Dayjs];
  hours: number;
  location?: string;
  lecturer?: string;
}

/** 分配表单值 */
interface AssignValues {
  scopeType: string;
  scopeValue?: string;
  userIds: string;
}

/** 补签表单值 */
interface SupplementValues {
  userId: number;
  username?: string;
  remark: string;
}

export default function TrainingClassDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [sessionForm] = Form.useForm<SessionValues>();
  const [assignForm] = Form.useForm<AssignValues>();
  const [supplementForm] = Form.useForm<SupplementValues>();

  const [detail, setDetail] = useState<TrainingClassDetail | null>(null);
  const [hours, setHours] = useState<LecturerHoursItem[]>([]);
  const [checkins, setCheckins] = useState<CheckinItem[]>([]);
  const [completion, setCompletion] = useState<TrainingCompletion | null>(null);
  const [checkinSession, setCheckinSession] = useState<SessionItem | null>(null);
  const [sessionOpen, setSessionOpen] = useState(false);
  const [assignOpen, setAssignOpen] = useState(false);
  const [supplementOpen, setSupplementOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const canSession = hasPermission('sys:lms:training:session');
  const canAssign = hasPermission('sys:lms:training:assign');
  const canStatus = hasPermission('sys:lms:training:status');
  const canCheckin = hasPermission('sys:lms:training:checkin');
  const canCheckinManage = hasPermission('sys:lms:training:checkin:manage');
  const canCompletion = hasPermission('sys:lms:training:completion');

  const load = useCallback(async () => {
    const data = await trainingClassDetail(id);
    setDetail(data);
    if (hasPermission('sys:lms:training:hours')) {
      setHours(await lecturerHours(id));
    }
    if (hasPermission('sys:lms:training:completion') && data.status !== 'DRAFT') {
      setCompletion(await trainingCompletion(id));
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  const isDraft = detail?.status === 'DRAFT';

  const onAddSession = async (values: SessionValues) => {
    setBusy(true);
    try {
      await addSession(Number(id), {
        title: values.title,
        startTime: values.range[0].format('YYYY-MM-DDTHH:mm:ss'),
        endTime: values.range[1].format('YYYY-MM-DDTHH:mm:ss'),
        hours: values.hours,
        location: values.location,
        lecturer: values.lecturer,
      });
      message.success('场次已添加，讲师课时台账同步入账');
      setSessionOpen(false);
      sessionForm.resetFields();
      await load();
    } finally {
      setBusy(false);
    }
  };

  const onRemoveSession = async (sessionId: number) => {
    await removeSession(sessionId);
    message.success('场次已删除，课时台账同步扣减');
    await load();
  };

  const onAssign = async (values: AssignValues) => {
    const userIds = values.userIds
      .split(/[\s,;]+/)
      .map((raw) => Number(raw.trim()))
      .filter((value) => Number.isFinite(value) && value > 0);
    if (userIds.length === 0) {
      message.warning('请填写至少一个学员用户 ID');
      return;
    }
    setBusy(true);
    try {
      const result = await assignLearners(Number(id), {
        scopeType: values.scopeType,
        scopeValue: values.scopeValue,
        userIds,
      });
      message.success(`分配完成：新增 ${result.created} 人，已存在跳过 ${result.skipped} 人`);
      setAssignOpen(false);
      assignForm.resetFields();
      await load();
    } finally {
      setBusy(false);
    }
  };

  const onStatus = async (action: 'PUBLISH' | 'CLOSE') => {
    await changeTrainingClassStatus(Number(id), action);
    message.success(action === 'PUBLISH' ? '培训班已发布' : '培训班已结课');
    await load();
  };

  const onSelfCheckin = async (session: SessionItem) => {
    await checkin(session.id);
    message.success('签到成功');
  };

  const openCheckins = async (session: SessionItem | null) => {
    setCheckinSession(session);
    setCheckins(await listCheckins(id, session?.id));
  };

  const onSupplement = async (values: SupplementValues) => {
    if (!checkinSession) {
      return;
    }
    setBusy(true);
    try {
      await supplementCheckin(checkinSession.id, values);
      message.success('补签已记录（含操作人与原因）');
      setSupplementOpen(false);
      supplementForm.resetFields();
      await openCheckins(checkinSession);
      await load();
    } finally {
      setBusy(false);
    }
  };

  if (!detail) {
    return <Empty description="加载中…" />;
  }

  const statusTag = STATUS_TAG[detail.status] ?? { color: 'default', label: detail.status };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Button onClick={() => navigate('/training')}>返回列表</Button>
        <Typography.Title level={5} style={{ margin: 0 }}>
          {detail.title}
        </Typography.Title>
        <Tag color={statusTag.color}>{statusTag.label}</Tag>
        {canStatus && isDraft && (
          <Popconfirm
            title="确认发布？发布前需至少 1 个场次，发布后内容冻结"
            onConfirm={() => void onStatus('PUBLISH')}
          >
            <Button type="primary" size="small">
              发布
            </Button>
          </Popconfirm>
        )}
        {canStatus && detail.status === 'PUBLISHED' && (
          <Popconfirm
            title="确认结课？结课后不可再分配与签到"
            onConfirm={() => void onStatus('CLOSE')}
          >
            <Button size="small">结课</Button>
          </Popconfirm>
        )}
      </Space>

      <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="编码">{detail.classCode}</Descriptions.Item>
        <Descriptions.Item label="形式">{detail.mode}</Descriptions.Item>
        <Descriptions.Item label="讲师">{detail.lecturer ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="起止日期">
          {detail.startDate} ~ {detail.endDate}
        </Descriptions.Item>
        <Descriptions.Item label="地点">{detail.location ?? '-'}</Descriptions.Item>
        <Descriptions.Item label="已分配 / 计划">
          {detail.assignedCount ?? 0} / {detail.capacity ?? '不限'}
        </Descriptions.Item>
        <Descriptions.Item label="课时合计">{detail.totalHours ?? 0}</Descriptions.Item>
        <Descriptions.Item label="签到人次">{detail.checkinCount ?? 0}</Descriptions.Item>
        <Descriptions.Item label="场次">{detail.sessions?.length ?? 0}</Descriptions.Item>
      </Descriptions>

      <Space style={{ marginBottom: 8 }} wrap>
        <Typography.Title level={5} style={{ margin: 0 }}>
          场次
        </Typography.Title>
        {canSession && isDraft && (
          <Button size="small" type="primary" onClick={() => setSessionOpen(true)}>
            添加场次
          </Button>
        )}
        {canAssign && detail.status === 'PUBLISHED' && (
          <Button size="small" onClick={() => setAssignOpen(true)}>
            必修分配
          </Button>
        )}
        {canCheckinManage && (
          <Button size="small" onClick={() => void openCheckins(null)}>
            全班签到明细
          </Button>
        )}
      </Space>

      <Table<SessionItem>
        rowKey="id"
        size="small"
        dataSource={detail.sessions ?? []}
        pagination={false}
        columns={[
          { title: '序号', dataIndex: 'sessionNo', width: 70 },
          { title: '主题', dataIndex: 'title' },
          { title: '开始', dataIndex: 'startTime', width: 170 },
          { title: '结束', dataIndex: 'endTime', width: 170 },
          { title: '讲师', dataIndex: 'lecturer', width: 110 },
          { title: '课时', dataIndex: 'hours', width: 80 },
          {
            title: '操作',
            width: 200,
            render: (_, row) => (
              <Space size="small">
                {canCheckin && detail.status === 'PUBLISHED' && (
                  <Button size="small" type="link" onClick={() => void onSelfCheckin(row)}>
                    签到
                  </Button>
                )}
                {canCheckinManage && (
                  <Button size="small" type="link" onClick={() => void openCheckins(row)}>
                    签到明细
                  </Button>
                )}
                {canSession && isDraft && (
                  <Popconfirm
                    title="确认删除该场次？"
                    onConfirm={() => void onRemoveSession(row.id)}
                  >
                    <Button size="small" type="link" danger>
                      删除
                    </Button>
                  </Popconfirm>
                )}
              </Space>
            ),
          },
        ]}
      />

      <Typography.Title level={5} style={{ marginTop: 16 }}>
        讲师课时台账
      </Typography.Title>
      <Table<LecturerHoursItem>
        rowKey="lecturer"
        size="small"
        dataSource={hours}
        pagination={false}
        columns={[
          { title: '讲师', dataIndex: 'lecturer' },
          { title: '场次数', dataIndex: 'sessionCount', width: 100 },
          { title: '课时合计', dataIndex: 'totalHours', width: 120 },
        ]}
      />

      <Typography.Title level={5} style={{ marginTop: 16 }}>
        完成认定
        {completion && (
          <Typography.Text type="secondary" style={{ marginLeft: 8, fontWeight: 'normal' }}>
            已认定 {completion.completed} / {completion.total} 人，完成率{' '}
            {completion.completionRate}%
          </Typography.Text>
        )}
      </Typography.Title>
      <Table<CompletionItem>
        rowKey="userId"
        size="small"
        dataSource={completion?.items ?? []}
        pagination={false}
        locale={{ emptyText: canCompletion ? '暂无分配学员' : '无完成认定权限' }}
        columns={[
          { title: '学员ID', dataIndex: 'userId', width: 90 },
          { title: '用户名', dataIndex: 'username', width: 130 },
          {
            title: '签到',
            width: 110,
            render: (_, row) => `${row.sessionsAttended} / ${row.sessionsTotal}`,
          },
          {
            title: '课程进度',
            dataIndex: 'coursePercent',
            width: 110,
            render: (percent?: number) =>
              percent === undefined || percent === null ? '-' : `${percent}%`,
          },
          {
            title: '认定',
            dataIndex: 'completed',
            width: 90,
            render: (completed: boolean) =>
              completed ? <Tag color="green">已完成</Tag> : <Tag>未完成</Tag>,
          },
          {
            title: '依据',
            dataIndex: 'basis',
            render: (basis: string) => {
              const tag = BASIS_TAG[basis] ?? { color: 'default', label: basis };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
        ]}
      />

      <Typography.Title level={5} style={{ marginTop: 16 }}>
        签到明细{checkinSession ? `（场次 ${checkinSession.sessionNo}）` : '（全班）'}
        {canCheckinManage && checkinSession && (
          <Button size="small" type="link" onClick={() => setSupplementOpen(true)}>
            补签
          </Button>
        )}
      </Typography.Title>
      <Table<CheckinItem>
        rowKey="id"
        size="small"
        dataSource={checkins}
        pagination={false}
        columns={[
          { title: '场次ID', dataIndex: 'sessionId', width: 90 },
          { title: '学员ID', dataIndex: 'userId', width: 90 },
          { title: '用户名', dataIndex: 'username', width: 120 },
          {
            title: '方式',
            dataIndex: 'checkinType',
            width: 100,
            render: (type: string) =>
              type === 'SUPPLEMENT' ? (
                <Tag color="orange">补签</Tag>
              ) : (
                <Tag color="green">自助</Tag>
              ),
          },
          { title: '时间', dataIndex: 'checkinAt', width: 180 },
          { title: '操作人', dataIndex: 'operatorId', width: 90 },
          { title: '备注', dataIndex: 'remark' },
        ]}
      />

      <Modal
        title="添加场次"
        open={sessionOpen}
        confirmLoading={busy}
        onCancel={() => setSessionOpen(false)}
        onOk={() => sessionForm.submit()}
      >
        <Form form={sessionForm} layout="vertical" onFinish={onAddSession}>
          <Form.Item
            name="title"
            label="场次主题"
            rules={[{ required: true, message: '请输入场次主题' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="range"
            label="起止时间"
            rules={[{ required: true, message: '请选择起止时间' }]}
          >
            <DatePicker.RangePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="hours" label="课时" rules={[{ required: true, message: '请输入课时' }]}>
            <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="location" label="地点">
            <Input />
          </Form.Item>
          <Form.Item name="lecturer" label="场次讲师（留空继承班级讲师）">
            <Input />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="必修分配"
        open={assignOpen}
        confirmLoading={busy}
        onCancel={() => setAssignOpen(false)}
        onOk={() => assignForm.submit()}
      >
        <Form
          form={assignForm}
          layout="vertical"
          onFinish={onAssign}
          initialValues={{ scopeType: 'USER' }}
        >
          <Form.Item name="scopeType" label="分配范围（追溯标记）">
            <Select
              options={[
                { value: 'USER', label: '指定人员' },
                { value: 'DEPT', label: '按部门' },
                { value: 'ROLE', label: '按角色' },
                { value: 'ALL', label: '全员' },
              ]}
            />
          </Form.Item>
          <Form.Item name="scopeValue" label="范围取值（部门 / 角色编码）">
            <Input placeholder="指定人员时可留空" />
          </Form.Item>
          <Form.Item
            name="userIds"
            label="学员用户 ID"
            rules={[{ required: true, message: '请填写学员用户 ID' }]}
            extra="支持逗号 / 空格分隔；重复分配自动跳过，不会产生重复报名"
          >
            <Input.TextArea rows={3} placeholder="如 1001, 1002, 1003" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="补签"
        open={supplementOpen}
        confirmLoading={busy}
        onCancel={() => setSupplementOpen(false)}
        onOk={() => supplementForm.submit()}
      >
        <Form form={supplementForm} layout="vertical" onFinish={onSupplement}>
          <Form.Item
            name="userId"
            label="学员用户 ID"
            rules={[{ required: true, message: '请输入学员用户 ID' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="username" label="学员用户名">
            <Input />
          </Form.Item>
          <Form.Item
            name="remark"
            label="补签原因"
            rules={[{ required: true, message: '补签必须填写原因' }]}
          >
            <Input.TextArea rows={2} maxLength={200} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
