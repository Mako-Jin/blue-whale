// 变更管理（ECR）：列表 + 状态筛选 + 新建/编辑 + 提交/审批/关闭
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
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
  approveChgRequest,
  closeChgRequest,
  createChgRequest,
  deleteChgRequest,
  pageChgRequests,
  rejectChgRequest,
  submitChgRequest,
  updateChgRequest,
  type ChgObjectType,
  type ChgRequest,
  type ChgRequestInput,
  type ChgRequestStatus,
} from '../../api/change';
import { hasPermission } from '../../utils/permission';

/** 状态文案与颜色 */
const STATUS_META: Record<ChgRequestStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  SUBMITTED: { text: '待审批', color: 'gold' },
  APPROVED: { text: '已通过', color: 'green' },
  REJECTED: { text: '已驳回', color: 'red' },
  CLOSED: { text: '已关闭', color: 'default' },
};

/** 状态筛选选项 */
const STATUS_OPTIONS = Object.entries(STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.text,
}));

/** 影响对象类型选项 */
const OBJECT_TYPE_OPTIONS: { value: ChgObjectType; label: string }[] = [
  { value: 'PART', label: '零件' },
  { value: 'BOM', label: 'BOM' },
  { value: 'DOCUMENT', label: '文档' },
];

/** 表单值 */
interface ChgFormValues {
  changeReason: string;
  changeScope?: string;
  impactObjects?: {
    objectType: ChgObjectType;
    objectId?: number;
    objectCode?: string;
    objectName?: string;
  }[];
}

/** 审批弹窗状态 */
interface ApproveState {
  record: ChgRequest;
  /** 通过或驳回 */
  action: 'approve' | 'reject';
}

/**
 * 时间展示：去掉 T 与毫秒。
 *
 * @param value 时间文本
 * @returns 展示文本
 */
function formatTime(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 19) : '-';
}

export default function ChangeCenter() {
  const [filters, setFilters] = useState<{ changeNo?: string; status?: ChgRequestStatus }>({});
  const [rows, setRows] = useState<ChgRequest[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ChgRequest | null>(null);
  const [form] = Form.useForm<ChgFormValues>();

  const [approveState, setApproveState] = useState<ApproveState | null>(null);
  const [approveForm] = Form.useForm<{ comment?: string }>();

  const canAdd = hasPermission('sys:pdm:change:add');
  const canEdit = hasPermission('sys:pdm:change:edit');
  const canDelete = hasPermission('sys:pdm:change:delete');
  const canSubmit = hasPermission('sys:pdm:change:submit');
  const canApprove = hasPermission('sys:pdm:change:approve');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageChgRequests({ current, size, ...filters });
      setRows(page.records);
      setTotal(page.total);
    } finally {
      setLoading(false);
    }
  }, [current, size, filters]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const openForm = (record: ChgRequest | null) => {
    setEditing(record);
    if (record) {
      form.setFieldsValue({
        changeReason: record.changeReason,
        changeScope: record.changeScope,
        impactObjects: record.impactObjects ?? [],
      });
    } else {
      form.resetFields();
    }
    setFormOpen(true);
  };

  const handleSave = async () => {
    const values = await form.validateFields();
    const input: ChgRequestInput = {
      changeReason: values.changeReason,
      changeScope: values.changeScope,
      impactObjects: (values.impactObjects ?? []).filter(
        (item) => item?.objectType && (item.objectId != null || item.objectCode),
      ),
    };
    await (editing ? updateChgRequest(editing.id, input) : createChgRequest(input));
    message.success('保存成功');
    setFormOpen(false);
    setRefreshKey((key) => key + 1);
  };

  const handleSubmit = async (record: ChgRequest) => {
    await submitChgRequest(record.id);
    message.success('已提交审批');
    setRefreshKey((key) => key + 1);
  };

  const handleApprove = async () => {
    if (!approveState) {
      return;
    }
    const values = await approveForm.validateFields();
    if (approveState.action === 'reject') {
      await rejectChgRequest(approveState.record.id, values.comment ?? '');
      message.success('已驳回');
    } else {
      await approveChgRequest(approveState.record.id, values.comment);
      message.success('审批通过');
    }
    setApproveState(null);
    approveForm.resetFields();
    setRefreshKey((key) => key + 1);
  };

  const columns: TableProps<ChgRequest>['columns'] = [
    { title: '申请编号', dataIndex: 'changeNo', key: 'changeNo', width: 190 },
    { title: '变更原因', dataIndex: 'changeReason', key: 'changeReason', ellipsis: true },
    { title: '变更范围', dataIndex: 'changeScope', key: 'changeScope', width: 150, ellipsis: true },
    {
      title: '影响对象',
      dataIndex: 'impactObjects',
      key: 'impactObjects',
      width: 100,
      render: (value: ChgRequest['impactObjects']) => `${value?.length ?? 0} 项`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: ChgRequestStatus) => {
        const meta = STATUS_META[value];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : value;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170, render: formatTime },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_, record) => (
        <Space size={0}>
          {canEdit && record.status === 'DRAFT' ? (
            <Button type="link" onClick={() => openForm(record)}>
              编辑
            </Button>
          ) : null}
          {canSubmit && record.status === 'DRAFT' ? (
            <Button type="link" onClick={() => void handleSubmit(record)}>
              提交
            </Button>
          ) : null}
          {canApprove && record.status === 'SUBMITTED' ? (
            <Button
              type="link"
              onClick={() => {
                setApproveState({ record, action: 'approve' });
                approveForm.resetFields();
              }}
            >
              审批
            </Button>
          ) : null}
          {canEdit && (record.status === 'APPROVED' || record.status === 'REJECTED') ? (
            <Button
              type="link"
              onClick={async () => {
                await closeChgRequest(record.id);
                message.success('已关闭');
                setRefreshKey((key) => key + 1);
              }}
            >
              关闭
            </Button>
          ) : null}
          {canDelete && record.status === 'DRAFT' ? (
            <Popconfirm
              title="确认删除该变更申请？"
              onConfirm={async () => {
                await deleteChgRequest(record.id);
                message.success('删除成功');
                setRefreshKey((key) => key + 1);
              }}
            >
              <Button type="link" danger>
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
      size="small"
      title="变更管理（ECR）"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="申请编号"
            style={{ width: 180 }}
            onSearch={(value) => {
              setCurrent(1);
              setFilters((prev) => ({ ...prev, changeNo: value || undefined }));
            }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            options={STATUS_OPTIONS}
            onChange={(value) => {
              setCurrent(1);
              setFilters((prev) => ({ ...prev, status: value }));
            }}
          />
          {canAdd ? (
            <Button type="primary" onClick={() => openForm(null)}>
              新建变更申请
            </Button>
          ) : null}
        </Space>
      }
    >
      <Table
        rowKey="id"
        size="small"
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

      <Modal
        open={formOpen}
        title={editing ? `编辑变更申请 ${editing.changeNo}` : '新建变更申请'}
        width={760}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="changeReason"
            label="变更原因"
            rules={[{ required: true, message: '请输入变更原因' }]}
          >
            <Input.TextArea rows={3} maxLength={1000} showCount placeholder="说明为什么要变更" />
          </Form.Item>
          <Form.Item name="changeScope" label="变更范围">
            <Input maxLength={500} placeholder="选填，如涉及的图号、批次或产线" />
          </Form.Item>
          <Form.List name="impactObjects">
            {(fields, { add, remove }) => (
              <Form.Item label="影响对象">
                <Space direction="vertical" style={{ width: '100%' }}>
                  {fields.map((field) => (
                    <Space key={field.key} align="baseline" wrap>
                      <Form.Item
                        name={[field.name, 'objectType']}
                        noStyle
                        rules={[{ required: true, message: '请选择类型' }]}
                      >
                        <Select style={{ width: 110 }} options={OBJECT_TYPE_OPTIONS} placeholder="类型" />
                      </Form.Item>
                      <Form.Item name={[field.name, 'objectId']} noStyle>
                        <InputNumber style={{ width: 140 }} placeholder="对象ID" min={1} />
                      </Form.Item>
                      <Form.Item name={[field.name, 'objectCode']} noStyle>
                        <Input style={{ width: 160 }} placeholder="对象编码" />
                      </Form.Item>
                      <Form.Item name={[field.name, 'objectName']} noStyle>
                        <Input style={{ width: 180 }} placeholder="对象名称" />
                      </Form.Item>
                      <Button type="link" danger onClick={() => remove(field.name)}>
                        移除
                      </Button>
                    </Space>
                  ))}
                  <Button type="dashed" onClick={() => add({ objectType: 'PART' })}>
                    添加影响对象
                  </Button>
                </Space>
              </Form.Item>
            )}
          </Form.List>
        </Form>
      </Modal>

      <Modal
        open={approveState !== null}
        title={approveState?.action === 'reject' ? '驳回变更申请' : '审批变更申请'}
        onOk={handleApprove}
        onCancel={() => {
          setApproveState(null);
          approveForm.resetFields();
        }}
        destroyOnClose
      >
        <Form form={approveForm} layout="vertical">
          <Form.Item
            name="comment"
            label="审批意见"
            rules={[
              {
                required: approveState?.action === 'reject',
                message: '驳回必须填写审批意见',
              },
            ]}
          >
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="选填（驳回必填）" />
          </Form.Item>
        </Form>
        {approveState?.action === 'approve' ? (
          <Space>
            <Button
              danger
              onClick={() => setApproveState({ ...approveState, action: 'reject' })}
            >
              改为驳回
            </Button>
          </Space>
        ) : null}
      </Modal>
    </Card>
  );
}
