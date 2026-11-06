// 题库列表页：分页 / 关键字与条件筛选 / 新建与编辑（含选项）/ 启停 / 删除。
//
// 权限码与 migration-5.87-lms-m3a.sql 对齐（sys:lms:question:*）。
// 口径：草稿允许不完整；启用时后端强制校验选项与答案；被试卷引用的题不可删。

import { Button, Form, Input, InputNumber, Modal, Popconfirm, Select, Space, Switch, Table, Tag, message } from 'antd';
import { useCallback, useEffect, useState } from 'react';
import { hasPermission } from '../api/client';
import {
  changeQuestionStatus,
  createQuestion,
  deleteQuestion,
  pageQuestions,
  updateQuestion,
  type QuestionItem,
  type QuestionPayload,
} from '../api/question';

/** 题型展示映射 */
const TYPE_TAG: Record<string, { color: string; label: string }> = {
  SINGLE: { color: 'blue', label: '单选' },
  MULTIPLE: { color: 'geekblue', label: '多选' },
  JUDGE: { color: 'purple', label: '判断' },
  SHORT_ANSWER: { color: 'orange', label: '简答' },
};

/** 状态展示映射 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  ACTIVE: { color: 'green', label: '已启用' },
  DISABLED: { color: 'red', label: '已停用' },
};

/** 难度展示映射 */
const DIFFICULTY_LABEL: Record<string, string> = {
  EASY: '简单',
  NORMAL: '一般',
  HARD: '困难',
};

/** 列表行 */
interface Row extends QuestionItem {
  key: string;
}

/** 编辑表单值 */
interface QuestionValues {
  questionCode: string;
  category: string;
  type: string;
  difficulty?: string;
  tags?: string;
  stem: string;
  standardAnswer?: string;
  analysis?: string;
  defaultScore: number;
  options?: Array<{ optionKey: string; content: string }>;
  activate?: boolean;
}

export default function QuestionListPage() {
  const [form] = Form.useForm<QuestionValues>();
  const [loading, setLoading] = useState(false);
  const [rows, setRows] = useState<Row[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [filters, setFilters] = useState<{ keyword?: string; type?: string; status?: string }>({});
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Row | null>(null);
  const [saving, setSaving] = useState(false);

  const canCreate = hasPermission('sys:lms:question:create');
  const canUpdate = hasPermission('sys:lms:question:update');
  const canDelete = hasPermission('sys:lms:question:delete');

  const type = Form.useWatch('type', form);
  const needsOptions = type === 'SINGLE' || type === 'MULTIPLE';

  const load = useCallback(async (page = current, query = filters) => {
    setLoading(true);
    try {
      const data = await pageQuestions({ ...query, current: page, size: 10 });
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

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ type: 'SINGLE', difficulty: 'NORMAL', defaultScore: 2, activate: false });
    setEditOpen(true);
  };

  const openEdit = (row: Row) => {
    setEditing(row);
    form.setFieldsValue({
      questionCode: row.questionCode,
      category: row.category,
      type: row.type,
      difficulty: row.difficulty,
      tags: (row.tags ?? []).join(','),
      stem: row.stem,
      standardAnswer: row.standardAnswer,
      analysis: row.analysis,
      defaultScore: row.defaultScore,
      options: row.options?.map((item) => ({ optionKey: item.optionKey, content: item.content })),
      activate: row.status === 'ACTIVE',
    });
    setEditOpen(true);
  };

  const onSubmit = async (values: QuestionValues) => {
    const payload: QuestionPayload = {
      questionCode: values.questionCode,
      category: values.category,
      type: values.type,
      difficulty: values.difficulty,
      tags: values.tags ? values.tags.split(/[,，\s]+/).filter(Boolean) : [],
      stem: values.stem,
      standardAnswer: values.standardAnswer,
      analysis: values.analysis,
      defaultScore: values.defaultScore,
      options:
        values.type === 'SINGLE' || values.type === 'MULTIPLE'
          ? (values.options ?? []).map((item, index) => ({ ...item, sortNo: index + 1 }))
          : [],
      activate: values.activate,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateQuestion(editing.id, payload);
        message.success('题目已更新');
      } else {
        await createQuestion(payload);
        message.success(values.activate ? '题目已创建并启用' : '题目已保存为草稿');
      }
      setEditOpen(false);
      form.resetFields();
      await load(1, filters);
    } finally {
      setSaving(false);
    }
  };

  const onToggle = async (row: Row) => {
    const action = row.status === 'ACTIVE' ? 'DISABLE' : 'ACTIVATE';
    await changeQuestionStatus(row.id, action);
    message.success(action === 'ACTIVATE' ? '题目已启用' : '题目已停用');
    refresh();
  };

  const onDelete = async (row: Row) => {
    await deleteQuestion(row.id);
    message.success('题目已删除');
    refresh();
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Input.Search
          placeholder="题干 / 题目编码"
          allowClear
          style={{ width: 240 }}
          onSearch={(keyword) => {
            const next = { ...filters, keyword: keyword || undefined };
            setFilters(next);
            void load(1, next);
          }}
        />
        <Select
          placeholder="题型"
          allowClear
          style={{ width: 120 }}
          options={Object.entries(TYPE_TAG).map(([value, item]) => ({ value, label: item.label }))}
          onChange={(value) => {
            const next = { ...filters, type: value || undefined };
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
          <Button type="primary" onClick={openCreate}>
            新建题目
          </Button>
        )}
      </Space>

      <Table<Row>
        rowKey="key"
        loading={loading}
        dataSource={rows}
        pagination={{ current, total, pageSize: 10, onChange: (page) => void load(page, filters) }}
        columns={[
          { title: '编码', dataIndex: 'questionCode', width: 130 },
          {
            title: '题型',
            dataIndex: 'type',
            width: 90,
            render: (value: string) => {
              const tag = TYPE_TAG[value] ?? { color: 'default', label: value };
              return <Tag color={tag.color}>{tag.label}</Tag>;
            },
          },
          { title: '分类', dataIndex: 'category', width: 120 },
          {
            title: '难度',
            dataIndex: 'difficulty',
            width: 80,
            render: (value: string) => DIFFICULTY_LABEL[value] ?? value,
          },
          { title: '题干', dataIndex: 'stem', ellipsis: true },
          { title: '分值', dataIndex: 'defaultScore', width: 70 },
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
            width: 200,
            render: (_, row) => (
              <Space size="small">
                {canUpdate && (
                  <Button size="small" type="link" onClick={() => openEdit(row)}>
                    编辑
                  </Button>
                )}
                {canUpdate && row.status !== 'DISABLED' && (
                  <Popconfirm
                    title={row.status === 'ACTIVE' ? '确认停用？停用后不再参与抽题' : '确认启用？启用前会校验选项与答案'}
                    onConfirm={() => void onToggle(row)}
                  >
                    <Button size="small" type="link">
                      {row.status === 'ACTIVE' ? '停用' : '启用'}
                    </Button>
                  </Popconfirm>
                )}
                {canDelete && (
                  <Popconfirm title="确认删除该题？被试卷引用时会被拒绝" onConfirm={() => void onDelete(row)}>
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
        title={editing ? '编辑题目' : '新建题目'}
        open={editOpen}
        confirmLoading={saving}
        width={720}
        onCancel={() => setEditOpen(false)}
        onOk={() => form.submit()}
      >
        <Form form={form} layout="vertical" onFinish={onSubmit}>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item
              name="questionCode"
              label="题目编码"
              style={{ flex: 1 }}
              rules={[{ required: true, message: '请输入题目编码' }]}
            >
              <Input placeholder="如 Q-2026-001（自动大写归一）" />
            </Form.Item>
            <Form.Item name="category" label="分类" style={{ flex: 1 }} rules={[{ required: true, message: '请输入分类' }]}>
              <Input placeholder="如 安全生产" />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="type" label="题型" style={{ flex: 1 }} rules={[{ required: true, message: '请选择题型' }]}>
              <Select options={Object.entries(TYPE_TAG).map(([value, item]) => ({ value, label: item.label }))} />
            </Form.Item>
            <Form.Item name="difficulty" label="难度" style={{ flex: 1 }}>
              <Select
                options={[
                  { value: 'EASY', label: '简单' },
                  { value: 'NORMAL', label: '一般' },
                  { value: 'HARD', label: '困难' },
                ]}
              />
            </Form.Item>
            <Form.Item name="defaultScore" label="默认分值" style={{ width: 120 }} rules={[{ required: true, message: '请输入分值' }]}>
              <InputNumber min={0.1} step={0.5} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item name="stem" label="题干" rules={[{ required: true, message: '请输入题干' }]}>
            <Input.TextArea rows={3} maxLength={2000} />
          </Form.Item>
          {needsOptions && (
            <Form.List name="options">
              {(fields, { add, remove }) => (
                <Form.Item label="选项（键唯一，答案按选项键填写）">
                  {fields.map((field) => (
                    <Space key={field.key} style={{ display: 'flex', marginBottom: 8 }} align="baseline">
                      <Form.Item
                        name={[field.name, 'optionKey']}
                        rules={[{ required: true, message: '键' }]}
                        style={{ width: 80 }}
                      >
                        <Input placeholder="A" />
                      </Form.Item>
                      <Form.Item
                        name={[field.name, 'content']}
                        rules={[{ required: true, message: '内容' }]}
                        style={{ flex: 1, minWidth: 420 }}
                      >
                        <Input placeholder="选项内容" />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>
                        删除
                      </Button>
                    </Space>
                  ))}
                  <Button type="dashed" onClick={() => add({ optionKey: '', content: '' })} block>
                    添加选项
                  </Button>
                </Form.Item>
              )}
            </Form.List>
          )}
          <Form.Item
            name="standardAnswer"
            label={type === 'MULTIPLE' ? '标准答案（多个选项键用逗号分隔）' : '标准答案'}
            extra={type === 'JUDGE' ? '填 TRUE / FALSE（也接受 对 / 错）' : '草稿可留空，启用时必填'}
          >
            <Input placeholder={type === 'MULTIPLE' ? '如 A,C' : type === 'JUDGE' ? 'TRUE' : 'A'} />
          </Form.Item>
          <Form.Item name="analysis" label="答案解析">
            <Input.TextArea rows={2} maxLength={2000} />
          </Form.Item>
          <Form.Item name="tags" label="标签（逗号分隔）">
            <Input placeholder="如 安全,新员工" />
          </Form.Item>
          <Form.Item name="activate" label="保存后立即启用" valuePropName="checked" extra="启用会校验选项数量、答案归属与判断题取值">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
