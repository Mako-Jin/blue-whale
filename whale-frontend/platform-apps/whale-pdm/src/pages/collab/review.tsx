// 研发评审：设计/样机/转产评审，草稿可改，关闭写入结论
import { useCallback, useEffect, useState } from 'react';
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
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  closeReview,
  createReview,
  deleteReview,
  getReview,
  pageReviews,
  updateReview,
  type Review,
  type ReviewConclusion,
  type ReviewInput,
  type ReviewStatus,
  type ReviewType,
} from '../../api/collab';
import { hasPermission } from '../../utils/permission';

/** 评审类型文案 */
const TYPE_META: Record<ReviewType, string> = {
  DESIGN: '设计评审',
  PROTOTYPE: '样机评审',
  TRANSFER: '转产评审',
};

/** 评审状态文案与颜色 */
const STATUS_META: Record<ReviewStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  CLOSED: { text: '已关闭', color: 'green' },
};

/** 评审结论文案与颜色 */
const CONCLUSION_META: Record<ReviewConclusion, { text: string; color: string }> = {
  PASSED: { text: '通过', color: 'green' },
  CONDITIONAL: { text: '有条件通过', color: 'orange' },
  REJECTED: { text: '不通过', color: 'red' },
};

const TYPE_OPTIONS = (Object.keys(TYPE_META) as ReviewType[]).map((key) => ({
  value: key,
  label: TYPE_META[key],
}));

const CONCLUSION_OPTIONS = (Object.keys(CONCLUSION_META) as ReviewConclusion[]).map((key) => ({
  value: key,
  label: CONCLUSION_META[key].text,
}));

interface Filters {
  reviewType?: ReviewType;
  status?: ReviewStatus;
  keyword?: string;
}

export default function ReviewCenter() {
  const [filters, setFilters] = useState<Filters>({});
  const [rows, setRows] = useState<Review[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);

  const [detail, setDetail] = useState<Review | null>(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState<Review | null>(null);
  const [closeTarget, setCloseTarget] = useState<Review | null>(null);
  const [form] = Form.useForm<ReviewInput>();
  const [closeForm] = Form.useForm<{ conclusion: ReviewConclusion; conclusionComment?: string }>();

  const canAdd = hasPermission('sys:pdm:collab:review:add');
  const canEdit = hasPermission('sys:pdm:collab:review:edit');
  const canClose = hasPermission('sys:pdm:collab:review:close');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageReviews({ ...filters, current, size });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '评审加载失败');
    } finally {
      setLoading(false);
    }
  }, [filters, current, size]);

  useEffect(() => {
    void load();
  }, [load]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ reviewType: 'DESIGN' });
    setEditOpen(true);
  };

  const openEdit = (row: Review) => {
    setEditing(row);
    form.setFieldsValue({
      reviewType: row.reviewType,
      title: row.title,
      projectId: row.projectId,
      projectName: row.projectName,
      objectType: row.objectType,
      objectId: row.objectId,
      objectNo: row.objectNo,
      objectVersionNo: row.objectVersionNo,
      participants: row.participants,
      attachmentDocumentId: row.attachmentDocumentId,
      attachmentVersionNo: row.attachmentVersionNo,
      approvalInstanceId: row.approvalInstanceId,
    });
    setEditOpen(true);
  };

  const onSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editing) {
        await updateReview(editing.id, values);
      } else {
        await createReview(values);
      }
      message.success('已保存');
      setEditOpen(false);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  const onClose = async () => {
    if (!closeTarget) {
      return;
    }
    const values = await closeForm.validateFields();
    try {
      await closeReview(closeTarget.id, values.conclusion, values.conclusionComment);
      message.success('评审已关闭');
      setCloseTarget(null);
      closeForm.resetFields();
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '关闭失败');
    }
  };

  const onDelete = async (id: number) => {
    try {
      await deleteReview(id);
      message.success('已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<Review>['columns'] = [
    { title: '评审编号', dataIndex: 'reviewNo', width: 170 },
    {
      title: '主题',
      dataIndex: 'title',
      render: (value: string, row) => (
        <Space direction="vertical" size={0}>
          <a onClick={() => void getReview(row.id).then(setDetail)}>{value}</a>
          <span style={{ color: '#999' }}>{TYPE_META[row.reviewType]}</span>
        </Space>
      ),
    },
    { title: '对象', dataIndex: 'objectNo', width: 180, render: (v: string) => v || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: ReviewStatus) => (
        <Tag color={STATUS_META[value].color}>{STATUS_META[value].text}</Tag>
      ),
    },
    {
      title: '结论',
      dataIndex: 'conclusion',
      width: 130,
      render: (value: ReviewConclusion) =>
        value ? <Tag color={CONCLUSION_META[value].color}>{CONCLUSION_META[value].text}</Tag> : '-',
    },
    { title: '参与人', dataIndex: 'participants', ellipsis: true },
    {
      title: '操作',
      width: 200,
      render: (_, row) => (
        <Space size={4}>
          {canEdit && row.status === 'DRAFT' ? (
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
          ) : null}
          {canClose && row.status === 'DRAFT' ? (
            <Button
              type="link"
              size="small"
              onClick={() => {
                setCloseTarget(row);
                closeForm.resetFields();
              }}
            >
              关闭
            </Button>
          ) : null}
          {canEdit && row.status === 'DRAFT' ? (
            <Popconfirm title="删除该评审？" onConfirm={() => void onDelete(row.id)}>
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
    <Card
      title="研发评审"
      extra={
        canAdd ? (
          <Button type="primary" onClick={openCreate}>
            新建评审
          </Button>
        ) : null
      }
    >
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="评审类型"
          style={{ width: 140 }}
          options={TYPE_OPTIONS}
          onChange={(value) => setFilters((prev) => ({ ...prev, reviewType: value }))}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 120 }}
          options={Object.keys(STATUS_META).map((key) => ({
            value: key,
            label: STATUS_META[key as ReviewStatus].text,
          }))}
          onChange={(value) => setFilters((prev) => ({ ...prev, status: value }))}
        />
        <Input.Search
          placeholder="评审编号 / 主题 / 对象编号"
          allowClear
          style={{ width: 240 }}
          onSearch={(value) => {
            setCurrent(1);
            setFilters((prev) => ({ ...prev, keyword: value || undefined }));
          }}
        />
      </Space>
      <Table<Review>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current,
          pageSize: size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) => {
            setCurrent(page);
            setSize(pageSize);
          },
        }}
      />

      <Drawer title={detail?.reviewNo} width={520} open={detail !== null} onClose={() => setDetail(null)}>
        {detail ? (
          <Descriptions column={1} size="small" bordered>
            <Descriptions.Item label="主题">{detail.title}</Descriptions.Item>
            <Descriptions.Item label="类型">{TYPE_META[detail.reviewType]}</Descriptions.Item>
            <Descriptions.Item label="项目">{detail.projectName || detail.projectId || '-'}</Descriptions.Item>
            <Descriptions.Item label="对象">
              {detail.objectNo || '-'} {detail.objectVersionNo || ''}
            </Descriptions.Item>
            <Descriptions.Item label="参与人">{detail.participants || '-'}</Descriptions.Item>
            <Descriptions.Item label="状态">{STATUS_META[detail.status].text}</Descriptions.Item>
            <Descriptions.Item label="结论">
              {detail.conclusion ? CONCLUSION_META[detail.conclusion].text : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="结论说明">{detail.conclusionComment || '-'}</Descriptions.Item>
            <Descriptions.Item label="附件文档">
              {detail.attachmentDocumentId
                ? `${detail.attachmentDocumentId} / ${detail.attachmentVersionNo || '-'}`
                : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="关闭时间">{detail.closedAt || '-'}</Descriptions.Item>
          </Descriptions>
        ) : null}
      </Drawer>

      <Modal
        title={editing ? '编辑评审' : '新建评审'}
        open={editOpen}
        onCancel={() => setEditOpen(false)}
        onOk={() => void onSubmit()}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="reviewType" label="评审类型" rules={[{ required: true }]}>
            <Select options={TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item name="title" label="评审主题" rules={[{ required: true, message: '请输入主题' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="projectId" label="项目 ID">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="projectName" label="项目名称">
            <Input />
          </Form.Item>
          <Form.Item name="objectType" label="评审对象类型">
            <Select
              allowClear
              options={[
                { value: 'DOCUMENT', label: '图文档' },
                { value: 'BOM', label: 'BOM' },
                { value: 'ROUTE', label: '工艺路线' },
                { value: 'PART', label: '零件' },
              ]}
            />
          </Form.Item>
          <Form.Item name="objectId" label="评审对象 ID">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="objectNo" label="评审对象编号">
            <Input />
          </Form.Item>
          <Form.Item name="objectVersionNo" label="评审对象版本号">
            <Input placeholder="如 V1.0" />
          </Form.Item>
          <Form.Item name="participants" label="参与人">
            <Input placeholder="多人用英文逗号分隔" />
          </Form.Item>
          <Form.Item name="attachmentDocumentId" label="附件文档 ID">
            <InputNumber min={1} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="attachmentVersionNo" label="附件文档版本号">
            <Input placeholder="如 V1.0" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        title="关闭评审"
        open={closeTarget !== null}
        onCancel={() => setCloseTarget(null)}
        onOk={() => void onClose()}
        destroyOnClose
      >
        <Form form={closeForm} layout="vertical">
          <Form.Item name="conclusion" label="评审结论" rules={[{ required: true, message: '请选择结论' }]}>
            <Select options={CONCLUSION_OPTIONS} />
          </Form.Item>
          <Form.Item name="conclusionComment" label="结论说明">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
