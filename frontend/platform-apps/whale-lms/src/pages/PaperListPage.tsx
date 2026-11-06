// 试卷列表页：分页 / 关键字检索 / 状态筛选 / 新建 / 发布 / 删除。
//
// 权限码与 migration-5.87-lms-m3a.sql 对齐（sys:lms:paper:*）。
// 口径：仅草稿可编辑题目与删除；发布需至少 1 题且及格分不超总分。

import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Table, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { hasPermission } from '../api/client';
import {
  createPaper,
  deletePaper,
  pagePapers,
  publishPaper,
  type PaperItem,
} from '../api/paper';

/** 状态展示映射 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  ARCHIVED: { color: 'orange', label: '已归档' },
};

/** 列表行 */
interface Row extends PaperItem {
  key: string;
}

/** 新建表单值 */
interface CreateValues {
  paperCode: string;
  title: string;
  durationMinutes: number;
  passScore?: number;
  remark?: string;
}

export default function PaperListPage() {
  const navigate = useNavigate();
  const [form] = Form.useForm<CreateValues>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [filters, setFilters] = useState<{ keyword?: string; status?: string }>({});
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);

  const canCreate = hasPermission('sys:lms:paper:create');
  const canPublish = hasPermission('sys:lms:paper:publish');
  const canDelete = hasPermission('sys:lms:paper:delete');

  const load = useCallback(async (page = current, query = filters) => {
    setLoading(true);
    try {
      const data = await pagePapers({ ...query, current: page, size: 10 });
      setRows((data.records ?? []).map((item) => ({ ...item, key: String(item.id) })));
      setTotal(data.total ?? 0);
      setCurrent(page);
    } finally {
      setLoading(false);
    }
  }, [current, filters]);

  useEffect(() => {
    void load(1, {});
    // 首次加载
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refresh = () => load(current, filters);

  const onCreate = async (values: CreateValues) => {
    setCreating(true);
    try {
      await createPaper({
        paperCode: values.paperCode,
        title: values.title,
        durationMinutes: values.durationMinutes,
        passScore: values.passScore,
        remark: values.remark,
      });
      message.success('试卷已创建（草稿态），请进入详情加题后发布');
      setCreateOpen(false);
      form.resetFields();
      await load(1, filters);
    } finally {
      setCreating(false);
    }
  };

  const onPublish = async (row: Row) => {
    await publishPaper(row.id);
    message.success('试卷已发布，题目已冻结');
    refresh();
  };

  const onDelete = async (row: Row) => {
    await deletePaper(row.id);
    message.success('试卷已删除');
    refresh();
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="试卷编码 / 名称"
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
          options={Object.entries(STATUS_TAG).map(([value, item]) => ({ value, label: item.label }))}
          onChange={(value) => {
            const next = { ...filters, status: value || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        {canCreate && (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建试卷
          </Button>
        )}
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, filters) }}
        columns={[
          { title: '编码', dataIndex: 'paperCode', width: 140 },
          {
            title: '试卷名称',
            dataIndex: 'title',
            render: (text: string, row) => <a onClick={() => navigate(`/paper/${row.id}`)}>{text}</a>,
          },
          { title: '时长(分钟)', dataIndex: 'durationMinutes', width: 110 },
          { title: '题数', dataIndex: 'questionCount', width: 80 },
          { title: '总分', dataIndex: 'totalScore', width: 90 },
          { title: '及格分', dataIndex: 'passScore', width: 90 },
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
            width: 220,
            render: (_, row) => (
              <Space size="small">
                <Button size="small" type="link" onClick={() => navigate(`/paper/${row.id}`)}>
                  详情
                </Button>
                {canPublish && row.status === 'DRAFT' && (
                  <Popconfirm title="确认发布？发布后题目冻结且不可再改" onConfirm={() => void onPublish(row)}>
                    <Button size="small" type="link">
                      发布
                    </Button>
                  </Popconfirm>
                )}
                {canDelete && row.status === 'DRAFT' && (
                  <Popconfirm title="确认删除试卷及其题目？" onConfirm={() => void onDelete(row)}>
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
        title="新建试卷"
        open={createOpen}
        confirmLoading={creating}
        onCancel={() => setCreateOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={onCreate} initialValues={{ durationMinutes: 60, passScore: 60 }}>
          <Form.Item name="paperCode" label="试卷编码" rules={[{ required: true, message: '请输入试卷编码' }]}>
            <Input placeholder="如 PAPER-2026-001（自动大写归一）" />
          </Form.Item>
          <Form.Item name="title" label="试卷名称" rules={[{ required: true, message: '请输入试卷名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="durationMinutes" label="考试时长（分钟）" rules={[{ required: true, message: '请输入时长' }]}>
            <InputNumber min={1} max={600} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="passScore" label="及格分" extra="总分由题目分值累加，发布时校验及格分不超总分">
            <InputNumber min={0} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={500} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
