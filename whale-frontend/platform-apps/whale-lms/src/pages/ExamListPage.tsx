// 考试列表页：分页 / 筛选 / 新建 / 发布 / 结课 / 删除 + 学员进入作答。
//
// 口径与 migration-5.87-lms-m3b.sql 对齐（sys:lms:exam:*、sys:lms:attempt:*）。
// 考试只能引用「已发布」试卷：下拉只列已发布卷，服务端也会再校验一次。
// 学员端看不到草稿考试（后端按角色强制过滤 status=PUBLISHED）。

import {
  Button,
  Checkbox,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { hasPermission } from '../api/client';
import {
  closeExam,
  createExam,
  deleteExam,
  pageExams,
  publishExam,
  startAttempt,
  type ExamItem,
  type ExamSaveBody,
} from '../api/exam';
import { pagePapers, type PaperItem } from '../api/paper';

/** 状态展示映射 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  CLOSED: { color: 'orange', label: '已结课' },
};

/** 列表行 */
interface Row extends ExamItem {
  key: string;
}

/** 新建表单值 */
interface CreateValues {
  examCode: string;
  title: string;
  paperId: number;
  startTime: Dayjs;
  endTime: Dayjs;
  durationMinutes: number;
  maxAttempts: number;
  shuffle?: boolean;
  passScore?: number;
  switchLimit?: number;
  remark?: string;
}

/** 时间格式（后端 LocalDateTime） */
const TIME_FORMAT = 'YYYY-MM-DDTHH:mm:ss';

export default function ExamListPage() {
  const navigate = useNavigate();
  const [form] = Form.useForm<CreateValues>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [filters, setFilters] = useState<{ keyword?: string; status?: string }>({});
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [papers, setPapers] = useState<PaperItem[]>([]);

  const canCreate = hasPermission('sys:lms:exam:create');
  const canPublish = hasPermission('sys:lms:exam:publish');
  const canDelete = hasPermission('sys:lms:exam:delete');
  const canAttempt = hasPermission('sys:lms:attempt:start');

  const load = useCallback(
    async (page = current, query = filters) => {
      setLoading(true);
      try {
        const data = await pageExams({ ...query, current: page, size: 10 });
        setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
        setTotal(data.total ?? 0);
        setCurrent(page);
      } finally {
        setLoading(false);
      }
    },
    [current, filters],
  );

  useEffect(() => {
    void load(1, {});
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const openCreate = async () => {
    setCreateOpen(true);
    if (papers.length === 0) {
      const data = await pagePapers({ status: 'PUBLISHED', current: 1, size: 100 });
      setPapers(data.records ?? []);
    }
  };

  const onCreate = async (values: CreateValues) => {
    setCreating(true);
    try {
      const body: ExamSaveBody = {
        examCode: values.examCode,
        title: values.title,
        paperId: values.paperId,
        startTime: values.startTime.format(TIME_FORMAT),
        endTime: values.endTime.format(TIME_FORMAT),
        durationMinutes: values.durationMinutes,
        maxAttempts: values.maxAttempts,
        shuffle: values.shuffle,
        passScore: values.passScore,
        switchLimit: values.switchLimit,
        remark: values.remark,
      };
      await createExam(body);
      message.success('考试已创建（草稿态），确认时间窗后发布');
      setCreateOpen(false);
      form.resetFields();
      await load(1, filters);
    } finally {
      setCreating(false);
    }
  };

  const onPublish = async (row: Row) => {
    await publishExam(row.id);
    message.success('考试已发布，学员可在时间窗内作答');
    void load(current, filters);
  };

  const onClose = async (row: Row) => {
    await closeExam(row.id);
    message.success('考试已结课，不再接受新的作答');
    void load(current, filters);
  };

  const onDelete = async (row: Row) => {
    await deleteExam(row.id);
    message.success('考试已删除');
    void load(current, filters);
  };

  const onAttempt = async (row: Row) => {
    const attemptId = await startAttempt(row.id);
    navigate(`/attempt/${attemptId}`);
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="考试编码 / 名称"
          allowClear
          style={{ width: 240 }}
          onSearch={(keyword) => {
            const next = { ...filters, keyword: keyword || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        <Select
          placeholder="状态"
          allowClear
          style={{ width: 120 }}
          options={Object.entries(STATUS_TAG).map(([value, item]) => ({
            value,
            label: item.label,
          }))}
          onChange={(value) => {
            const next = { ...filters, status: value || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        {canCreate && (
          <Button type="primary" onClick={() => void openCreate()}>
            新建考试
          </Button>
        )}
        {hasPermission('sys:lms:attempt:submit') && (
          <Button onClick={() => navigate('/my-exam')}>我的考试</Button>
        )}
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, filters) }}
        columns={[
          { title: '编码', dataIndex: 'examCode', width: 140 },
          { title: '考试名称', dataIndex: 'title' },
          { title: '时长(分钟)', dataIndex: 'durationMinutes', width: 110 },
          { title: '可考次数', dataIndex: 'maxAttempts', width: 100 },
          { title: '及格分', dataIndex: 'passScore', width: 90 },
          {
            title: '时间窗',
            width: 300,
            render: (_, row) => (
              <span>
                {dayjs(row.startTime).format('YYYY-MM-DD HH:mm')} ~{' '}
                {dayjs(row.endTime).format('YYYY-MM-DD HH:mm')}
              </span>
            ),
          },
          {
            title: '状态',
            dataIndex: 'status',
            width: 100,
            render: (value: string) => {
              const tag = STATUS_TAG[value] ?? { color: 'default', label: value };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
          {
            title: '操作',
            width: 260,
            render: (_, row) => (
              <Space size="small">
                {canAttempt && row.status === 'PUBLISHED' && (
                  <Button size="small" type="link" onClick={() => void onAttempt(row)}>
                    开始作答
                  </Button>
                )}
                {canPublish && row.status === 'DRAFT' && (
                  <Popconfirm
                    title="确认发布？发布后配置不可修改"
                    onConfirm={() => void onPublish(row)}
                  >
                    <Button size="small" type="link">
                      发布
                    </Button>
                  </Popconfirm>
                )}
                {canPublish && row.status === 'PUBLISHED' && (
                  <Popconfirm
                    title="确认结课？结课后不再接受作答"
                    onConfirm={() => void onClose(row)}
                  >
                    <Button size="small" type="link">
                      结课
                    </Button>
                  </Popconfirm>
                )}
                {canDelete && row.status === 'DRAFT' && (
                  <Popconfirm title="确认删除考试？" onConfirm={() => void onDelete(row)}>
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

      <Modal
        title="新建考试"
        open={createOpen}
        width={620}
        confirmLoading={creating}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={onCreate}
          initialValues={{
            durationMinutes: 60,
            maxAttempts: 1,
            shuffle: true,
            passScore: 60,
            switchLimit: 0,
          }}
        >
          <Form.Item
            name="examCode"
            label="考试编码"
            rules={[{ required: true, message: '请输入考试编码' }]}
          >
            <Input placeholder="如 EXAM-2026-001（自动大写归一）" />
          </Form.Item>
          <Form.Item
            name="title"
            label="考试名称"
            rules={[{ required: true, message: '请输入考试名称' }]}
          >
            <Input />
          </Form.Item>
          <Form.Item
            name="paperId"
            label="试卷"
            rules={[{ required: true, message: '请选择试卷' }]}
            extra="只列已发布试卷；及格分不得高于试卷总分"
          >
            <Select
              placeholder="选择已发布试卷"
              options={papers.map((item) => ({
                value: item.id,
                label: `${item.title}（${item.questionCount} 题 / 总分 ${item.totalScore}）`,
              }))}
            />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="startTime"
              label="开考时间"
              rules={[{ required: true, message: '请选择开考时间' }]}
              style={{ flex: 1 }}
            >
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="endTime"
              label="截止时间"
              rules={[{ required: true, message: '请选择截止时间' }]}
              style={{ flex: 1 }}
            >
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="durationMinutes"
              label="单次时长（分钟）"
              rules={[{ required: true, message: '请输入时长' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={1} max={600} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item
              name="maxAttempts"
              label="可考次数"
              rules={[{ required: true, message: '请输入次数' }]}
              style={{ flex: 1 }}
            >
              <InputNumber min={1} max={20} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="passScore" label="及格分" style={{ flex: 1 }}>
              <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="switchLimit" label="切屏上限（0 不限）" style={{ flex: 1 }}>
              <InputNumber min={0} style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="shuffle" label="题序乱序" valuePropName="checked" style={{ flex: 1 }}>
              <Checkbox>按作答随机题序（判分不受影响）</Checkbox>
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
