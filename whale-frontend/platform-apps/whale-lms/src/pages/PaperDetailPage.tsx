// 试卷详情页：题目清单 + 规则抽题（可复现）+ 手工加题 + 发布 / 归档。
//
// 抽题口径与后端一致：不填 seed 时用试卷编码派生，重复生成得到同一套题；
// 填新 seed 才重新洗牌。候选不足时后端直接报错，不会少抽补齐。
// 发布后题目冻结（仅草稿可增删题目）。

import {
  Button,
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
import { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { hasPermission } from '../api/client';
import {
  addPaperQuestion,
  archivePaper,
  generatePaper,
  paperDetail,
  publishPaper,
  removePaperQuestion,
  type PaperDetail,
  type PaperQuestionItem,
} from '../api/paper';

/** 状态展示映射 */
const STATUS_TAG: Record<string, { color: string; label: string }> = {
  DRAFT: { color: 'default', label: '草稿' },
  PUBLISHED: { color: 'green', label: '已发布' },
  ARCHIVED: { color: 'orange', label: '已归档' },
};

/** 题型展示映射 */
const TYPE_LABEL: Record<string, string> = {
  SINGLE: '单选',
  MULTIPLE: '多选',
  JUDGE: '判断',
  SHORT_ANSWER: '简答',
};

/** 抽题表单值 */
interface GenerateValues {
  category?: string;
  type?: string;
  difficulty?: string;
  count: number;
  score?: number;
  seed?: string;
}

/** 加题表单值 */
interface AddValues {
  questionId: number;
  score?: number;
}

export default function PaperDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const [generateForm] = Form.useForm<GenerateValues>();
  const [addForm] = Form.useForm<AddValues>();

  const [detail, setDetail] = useState<PaperDetail | null>(null);
  const [generateOpen, setGenerateOpen] = useState(false);
  const [addOpen, setAddOpen] = useState(false);
  const [busy, setBusy] = useState(false);

  const canUpdate = hasPermission('sys:lms:paper:update');
  const canPublish = hasPermission('sys:lms:paper:publish');

  const load = useCallback(async () => {
    setDetail(await paperDetail(id));
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!detail) {
    return <Empty description="加载中…" />;
  }

  const isDraft = detail.status === 'DRAFT';
  const statusTag = STATUS_TAG[detail.status] ?? { color: 'default', label: detail.status };

  const onGenerate = async (values: GenerateValues) => {
    setBusy(true);
    try {
      const result = await generatePaper(Number(id), {
        seed: values.seed || undefined,
        rules: [
          {
            category: values.category || undefined,
            type: values.type || undefined,
            difficulty: values.difficulty || undefined,
            count: values.count,
            score: values.score || undefined,
          },
        ],
      });
      message.success(
        `已抽 ${result.picked} 道，当前共 ${result.questionCount} 题、总分 ${result.totalScore}（seed=${result.seed}）`,
      );
      setGenerateOpen(false);
      generateForm.resetFields();
      await load();
    } finally {
      setBusy(false);
    }
  };

  const onAdd = async (values: AddValues) => {
    setBusy(true);
    try {
      await addPaperQuestion(Number(id), { questionId: values.questionId, score: values.score });
      message.success('题目已加入试卷');
      setAddOpen(false);
      addForm.resetFields();
      await load();
    } finally {
      setBusy(false);
    }
  };

  const onRemove = async (questionId: number) => {
    await removePaperQuestion(Number(id), questionId);
    message.success('题目已移出，题序已重排');
    await load();
  };

  const onPublish = async () => {
    await publishPaper(Number(id));
    message.success('试卷已发布，题目已冻结');
    await load();
  };

  const onArchive = async () => {
    await archivePaper(Number(id));
    message.success('试卷已归档');
    await load();
  };

  return (
    <div style={{ background: '#fff', padding: 16, borderRadius: 8 }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Button onClick={() => navigate('/paper')}>返回列表</Button>
        <Typography.Title level={5} style={{ margin: 0 }}>
          {detail.title}
        </Typography.Title>
        <Tag color={statusTag.color}>{statusTag.label}</Tag>
        {canUpdate && isDraft && (
          <>
            <Button size="small" onClick={() => setAddOpen(true)}>
              手工加题
            </Button>
            <Button size="small" type="primary" onClick={() => setGenerateOpen(true)}>
              规则抽题
            </Button>
          </>
        )}
        {canPublish && isDraft && (
          <Popconfirm title="确认发布？发布后题目冻结且不可再改" onConfirm={() => void onPublish()}>
            <Button size="small" type="primary">
              发布
            </Button>
          </Popconfirm>
        )}
        {canPublish && detail.status === 'PUBLISHED' && (
          <Popconfirm title="确认归档？归档后只读" onConfirm={() => void onArchive()}>
            <Button size="small">归档</Button>
          </Popconfirm>
        )}
      </Space>

      <Descriptions bordered size="small" column={3} style={{ marginBottom: 16 }}>
        <Descriptions.Item label="编码">{detail.paperCode}</Descriptions.Item>
        <Descriptions.Item label="时长">{detail.durationMinutes} 分钟</Descriptions.Item>
        <Descriptions.Item label="及格分">{detail.passScore}</Descriptions.Item>
        <Descriptions.Item label="题数">{detail.questionCount}</Descriptions.Item>
        <Descriptions.Item label="总分">{detail.totalScore}</Descriptions.Item>
        <Descriptions.Item label="抽题种子">{detail.seed ?? '-'}</Descriptions.Item>
      </Descriptions>

      <Typography.Title level={5} style={{ marginTop: 8 }}>
        题目清单
      </Typography.Title>
      <Table<PaperQuestionItem>
        rowKey="questionId"
        size="small"
        dataSource={detail.questions ?? []}
        pagination={false}
        locale={{ emptyText: '暂无题目，可用规则抽题或手工加题' }}
        columns={[
          { title: '题序', dataIndex: 'sortNo', width: 70 },
          { title: '题目编码', dataIndex: 'questionCode', width: 140 },
          {
            title: '题型',
            dataIndex: 'type',
            width: 90,
            render: (value?: string) => (value ? (TYPE_LABEL[value] ?? value) : '-'),
          },
          { title: '题干', dataIndex: 'stem', ellipsis: true },
          { title: '分值', dataIndex: 'score', width: 80 },
          {
            title: '来源',
            dataIndex: 'source',
            width: 90,
            render: (value: string) =>
              value === 'RULE' ? <Tag color="blue">抽题</Tag> : <Tag>手工</Tag>,
          },
          {
            title: '操作',
            width: 90,
            render: (_, row) =>
              canUpdate && isDraft ? (
                <Popconfirm title="确认移出该题？" onConfirm={() => void onRemove(row.questionId)}>
                  <Button size="small" type="link" danger>
                    移出
                  </Button>
                </Popconfirm>
              ) : null,
          },
        ]}
      />

      <Modal
        title="规则抽题（同一 seed 可复现）"
        open={generateOpen}
        confirmLoading={busy}
        onCancel={() => setGenerateOpen(false)}
        onOk={() => generateForm.submit()}
      >
        <Form
          form={generateForm}
          layout="vertical"
          onFinish={onGenerate}
          initialValues={{ count: 5 }}
        >
          <Form.Item name="category" label="分类（留空不限）">
            <Input placeholder="如 安全生产" />
          </Form.Item>
          <Form.Item name="type" label="题型（留空不限）">
            <Select
              allowClear
              options={Object.entries(TYPE_LABEL).map(([value, label]) => ({ value, label }))}
            />
          </Form.Item>
          <Form.Item name="difficulty" label="难度（留空不限）">
            <Select
              allowClear
              options={[
                { value: 'EASY', label: '简单' },
                { value: 'NORMAL', label: '一般' },
                { value: 'HARD', label: '困难' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="count"
            label="抽取数量"
            rules={[{ required: true, message: '请输入数量' }]}
          >
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="score" label="每题分值（留空取题目默认分值）">
            <InputNumber min={0.1} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="seed"
            label="抽题种子（留空用试卷编码）"
            extra="换种子会重新洗牌；保持留空则重复生成结果一致"
          >
            <Input placeholder="如 2026-FINAL" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="手工加题"
        open={addOpen}
        confirmLoading={busy}
        onCancel={() => setAddOpen(false)}
        onOk={() => addForm.submit()}
      >
        <Form form={addForm} layout="vertical" onFinish={onAdd}>
          <Form.Item
            name="questionId"
            label="题目 ID"
            rules={[{ required: true, message: '请输入题目 ID' }]}
          >
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="score" label="本题分值（留空取题目默认分值）">
            <InputNumber min={0.1} step={0.5} style={{ width: '100%' }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
