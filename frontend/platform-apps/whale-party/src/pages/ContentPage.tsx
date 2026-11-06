// 党建内容（M-P2）：草稿 → 待审 → 发布 → 下线 的先审后发管理。
//
// 页面不在「发布态编辑」后直接提示成功：后端会把内容退回待审，
// 这里同步给出明确提示，避免作者以为改完就上线了。
// 审核按钮只对持有 sys:party:content:audit 的角色展示，支部委员可投稿但无权自审。

import {
  Button,
  Card,
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
  Timeline,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { useCallback, useEffect, useState } from 'react';
import {
  approveContent,
  branchTree,
  createContent,
  getContent,
  listContentAudits,
  listContentReads,
  offlineContent,
  pageContent,
  rejectContent,
  submitContent,
  updateContent,
  type BranchTreeView,
  type ContentAuditView,
  type ContentReadView,
  type ContentSaveCommand,
  type ContentView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 状态选项 */
const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'PENDING', label: '待审' },
  { value: 'PUBLISHED', label: '已发布' },
  { value: 'OFFLINE', label: '已下线' },
];

/** 可见范围选项 */
const VISIBILITY_OPTIONS = [
  { value: 'ALL_MEMBERS', label: '全体党员' },
  { value: 'BRANCHES', label: '指定支部' },
  { value: 'ALL_STAFF', label: '全员' },
];

/** 状态颜色 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  PENDING: 'orange',
  PUBLISHED: 'green',
  OFFLINE: 'gray',
};

/** 内容表单值 */
interface ContentFormValues {
  category: string;
  title: string;
  summary?: string;
  coverFileId?: number | null;
  body?: string;
  attachments?: string;
  visibility: string;
  branchIds?: number[];
  remark?: string;
}

/**
 * 把支部树拍平成下拉选项。
 *
 * @param nodes 树节点
 * @param depth 当前层级
 * @returns 选项列表
 */
function flattenBranches(nodes: BranchTreeView[], depth = 0): Array<{ value: number; label: string }> {
  return nodes.flatMap((node) => [
    { value: node.id, label: `${'　'.repeat(depth)}${node.branchCode} ${node.branchName}` },
    ...flattenBranches(node.children ?? [], depth + 1),
  ]);
}

/**
 * 逗号分隔的文件 ID 转数组。
 *
 * @param text 输入文本
 * @returns 文件 ID 数组
 */
function parseFileIds(text?: string): number[] {
  if (!text) {
    return [];
  }
  return text
    .split(/[,，\s]+/)
    .map((part) => Number(part))
    .filter((value) => Number.isFinite(value) && value > 0);
}

/** 党建内容管理页 */
export default function ContentPage() {
  const [rows, setRows] = useState<ContentView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [loading, setLoading] = useState(false);
  const [branchOptions, setBranchOptions] = useState<Array<{ value: number; label: string }>>([]);

  const [editing, setEditing] = useState<ContentView | null>(null);
  const [editorOpen, setEditorOpen] = useState(false);
  const [form] = Form.useForm<ContentFormValues>();
  const visibility = Form.useWatch('visibility', form);

  const [auditTarget, setAuditTarget] = useState<ContentView | null>(null);
  const [audits, setAudits] = useState<ContentAuditView[]>([]);
  const [auditOpen, setAuditOpen] = useState(false);

  const [readsTarget, setReadsTarget] = useState<ContentView | null>(null);
  const [reads, setReads] = useState<ContentReadView[]>([]);
  const [readsOpen, setReadsOpen] = useState(false);

  const [rejectTarget, setRejectTarget] = useState<ContentView | null>(null);
  const [rejectForm] = Form.useForm<{ opinion: string }>();

  const canAudit = hasPermission('sys:party:content:audit');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageContent({
        keyword: keyword || undefined,
        status,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(page.total);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '党建内容加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    branchTree()
      .then((tree) => setBranchOptions(flattenBranches(tree)))
      .catch(() => setBranchOptions([]));
  }, []);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ visibility: 'ALL_MEMBERS', branchIds: [] });
    setEditorOpen(true);
  };

  const openEdit = (row: ContentView) => {
    if (row.status === 'PENDING') {
      message.warning('待审内容不可编辑，请等待审核结果');
      return;
    }
    if (row.status === 'OFFLINE') {
      message.warning('已下线内容不可编辑');
      return;
    }
    if (row.status === 'PUBLISHED') {
      message.warning('保存后内容将退回待审，需重新审核后才能再次发布');
    }
    setEditing(row);
    form.setFieldsValue({
      category: row.category,
      title: row.title,
      summary: row.summary ?? undefined,
      coverFileId: row.coverFileId ?? undefined,
      body: row.body ?? undefined,
      attachments: (row.attachmentFileIds ?? []).join(','),
      visibility: row.visibility,
      branchIds: row.branchIds ?? [],
      remark: row.remark ?? undefined,
    });
    setEditorOpen(true);
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    const payload: ContentSaveCommand = {
      category: values.category,
      title: values.title,
      summary: values.summary ?? null,
      coverFileId: values.coverFileId ?? null,
      body: values.body ?? null,
      attachmentFileIds: parseFileIds(values.attachments),
      visibility: values.visibility,
      branchIds: values.visibility === 'BRANCHES' ? values.branchIds ?? [] : [],
      remark: values.remark ?? null,
    };
    try {
      if (editing) {
        const view = await updateContent(editing.id, payload);
        message.success(view.status === 'PENDING' ? '已保存，内容退回待审' : '已保存');
      } else {
        await createContent(payload);
        message.success('草稿已创建');
      }
      setEditorOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const submitForAudit = async (row: ContentView) => {
    try {
      await submitContent(row.id);
      message.success('已提交审核');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '提交失败，请检查正文');
    }
  };

  const approve = async (row: ContentView) => {
    try {
      await approveContent(row.id);
      message.success('已发布');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '审核失败');
    }
  };

  const reject = async () => {
    if (!rejectTarget) {
      return;
    }
    const values = await rejectForm.validateFields();
    try {
      await rejectContent(rejectTarget.id, values.opinion);
      message.success('已退回');
      setRejectTarget(null);
      rejectForm.resetFields();
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '退回失败');
    }
  };

  const offline = async (row: ContentView) => {
    try {
      await offlineContent(row.id);
      message.success('已下线，历史与阅读数保留');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '下线失败');
    }
  };

  const openAudits = async (row: ContentView) => {
    try {
      setAuditTarget(row);
      setAudits(await listContentAudits(row.id));
      setAuditOpen(true);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '审核轨迹加载失败');
    }
  };

  const openReads = async (row: ContentView) => {
    try {
      setReadsTarget(row);
      setReads(await listContentReads(row.id));
      setReadsOpen(true);
    } catch (err) {
      message.error(err instanceof Error ? err.message : '阅读记录加载失败');
    }
  };

  const preview = async (row: ContentView) => {
    try {
      const detail = await getContent(row.id);
      Modal.info({
        title: detail.title,
        width: 720,
        content: (
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="分类">{detail.category}</Descriptions.Item>
              <Descriptions.Item label="范围">{detail.visibilityLabel}</Descriptions.Item>
              <Descriptions.Item label="作者">{detail.authorName ?? '—'}</Descriptions.Item>
              <Descriptions.Item label="阅读人数">{detail.readCount}</Descriptions.Item>
            </Descriptions>
            <Typography.Paragraph style={{ whiteSpace: 'pre-wrap', marginBottom: 0 }}>
              {detail.body || '（暂无正文）'}
            </Typography.Paragraph>
          </Space>
        ),
      });
    } catch (err) {
      message.error(err instanceof Error ? err.message : '详情加载失败');
    }
  };

  const columns: ColumnsType<ContentView> = [
    { title: '标题', dataIndex: 'title', ellipsis: true },
    { title: '分类', dataIndex: 'category', width: 110 },
    { title: '范围', dataIndex: 'visibilityLabel', width: 100 },
    { title: '作者', dataIndex: 'authorName', width: 100, render: (value) => value ?? '—' },
    {
      title: '阅读',
      dataIndex: 'readCount',
      width: 80,
      render: (value: number) => `${value ?? 0} 人`,
    },
    {
      title: '状态',
      dataIndex: 'statusLabel',
      width: 90,
      render: (_value, row) => <Tag color={STATUS_COLOR[row.status] ?? 'default'}>{row.statusLabel}</Tag>,
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 160,
      render: (value) => value ?? '—',
    },
    {
      title: '操作',
      key: 'actions',
      width: 260,
      render: (_value, row) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => void preview(row)}>
            查看
          </Button>
          <Button type="link" size="small" onClick={() => openEdit(row)}>
            编辑
          </Button>
          {row.status === 'DRAFT' ? (
            <Button type="link" size="small" onClick={() => void submitForAudit(row)}>
              提交审核
            </Button>
          ) : null}
          {row.status === 'PENDING' && canAudit ? (
            <>
              <Button type="link" size="small" onClick={() => void approve(row)}>
                通过
              </Button>
              <Button
                type="link"
                size="small"
                danger
                onClick={() => {
                  setRejectTarget(row);
                  rejectForm.resetFields();
                }}
              >
                退回
              </Button>
            </>
          ) : null}
          {row.status === 'PUBLISHED' && canAudit ? (
            <Popconfirm title="下线后读者不可见，历史与阅读数保留" onConfirm={() => void offline(row)}>
              <Button type="link" size="small" danger>
                下线
              </Button>
            </Popconfirm>
          ) : null}
          <Button type="link" size="small" onClick={() => void openAudits(row)}>
            轨迹
          </Button>
          <Button type="link" size="small" onClick={() => void openReads(row)}>
            阅读
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="党建内容"
        size="small"
        extra={
          <Space>
            <Input.Search
              allowClear
              placeholder="标题或分类关键字"
              style={{ width: 220 }}
              onSearch={(value) => {
                setCurrent(1);
                setKeyword(value.trim());
              }}
            />
            <Select
              allowClear
              placeholder="状态"
              style={{ width: 120 }}
              options={STATUS_OPTIONS}
              value={status}
              onChange={(value) => {
                setCurrent(1);
                setStatus(value);
              }}
            />
            <Button type="primary" onClick={openCreate}>
              新建内容
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1100 }}
          pagination={{
            current,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: setCurrent,
          }}
        />
      </Card>

      <Drawer
        title={editing ? '编辑党建内容' : '新建党建内容'}
        width={760}
        open={editorOpen}
        onClose={() => setEditorOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setEditorOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submitEditor()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item name="title" label="标题" rules={[{ required: true, message: '请输入标题' }]}>
            <Input maxLength={200} showCount />
          </Form.Item>
          <Form.Item name="category" label="分类" rules={[{ required: true, message: '请输入分类' }]}>
            <Input maxLength={64} placeholder="如：理论学习 / 支部动态 / 通知公告" />
          </Form.Item>
          <Form.Item name="summary" label="摘要">
            <Input.TextArea maxLength={500} rows={2} showCount />
          </Form.Item>
          <Form.Item name="coverFileId" label="封面文件 ID（可选）">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="body" label="正文（草稿可空，提交审核前必填）">
            <Input.TextArea rows={10} maxLength={65000} showCount />
          </Form.Item>
          <Form.Item name="attachments" label="附件文件 ID（逗号分隔，最多 10 个）">
            <Input placeholder="如：101,102" />
          </Form.Item>
          <Form.Item name="visibility" label="可见范围" rules={[{ required: true }]}>
            <Select options={VISIBILITY_OPTIONS} />
          </Form.Item>
          {visibility === 'BRANCHES' ? (
            <Form.Item
              name="branchIds"
              label="指定支部"
              rules={[{ required: true, message: '请至少选择一个支部' }]}
            >
              <Select mode="multiple" options={branchOptions} showSearch optionFilterProp="label" />
            </Form.Item>
          ) : null}
          <Form.Item name="remark" label="备注">
            <Input maxLength={255} />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title={`退回 · ${rejectTarget?.title ?? ''}`}
        open={rejectTarget !== null}
        onCancel={() => setRejectTarget(null)}
        onOk={() => void reject()}
        okText="确认退回"
        destroyOnClose
      >
        <Form form={rejectForm} layout="vertical" preserve={false}>
          <Form.Item
            name="opinion"
            label="审核意见（必填）"
            rules={[{ required: true, message: '退回必须填写审核意见' }]}
          >
            <Input.TextArea rows={4} maxLength={1000} showCount placeholder="说明需要修改的内容" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title={`审核轨迹 · ${auditTarget?.title ?? ''}`}
        open={auditOpen}
        footer={null}
        width={640}
        onCancel={() => setAuditOpen(false)}
      >
        <Timeline
          items={audits.map((audit) => ({
            color: audit.action === 'REJECT' ? 'red' : audit.action === 'APPROVE' ? 'green' : 'blue',
            children: (
              <Space direction="vertical" size={2}>
                <Typography.Text strong>{audit.actionLabel}</Typography.Text>
                <Typography.Text type="secondary">
                  {audit.operatorName ?? '—'} · {audit.createTime ?? '—'}
                  {audit.fromStatus ? ` · ${audit.fromStatus} → ${audit.toStatus ?? ''}` : ''}
                </Typography.Text>
                {audit.opinion ? <Typography.Text>意见：{audit.opinion}</Typography.Text> : null}
              </Space>
            ),
          }))}
        />
      </Modal>

      <Modal
        title={`阅读记录 · ${readsTarget?.title ?? ''}`}
        open={readsOpen}
        footer={null}
        width={560}
        onCancel={() => setReadsOpen(false)}
      >
        <Table
          rowKey="id"
          size="small"
          pagination={false}
          dataSource={reads}
          locale={{ emptyText: '尚无人阅读' }}
          columns={[
            { title: '阅读人', dataIndex: 'userName', render: (value) => value ?? '—' },
            { title: '最近阅读时间', dataIndex: 'readAt', render: (value) => value ?? '—' },
          ]}
        />
      </Modal>
    </Space>
  );
}
