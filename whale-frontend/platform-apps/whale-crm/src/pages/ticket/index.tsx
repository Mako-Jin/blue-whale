// 售后服务工单：列表 / 建单 / 分派 / 处理 / 暂停恢复 / 升级 / 解决 / 关闭 / 重开 / 取消
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
  Timeline,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { hasPermission } from '../../utils/permission';
import {
  assignTicket,
  cancelTicket,
  closeTicket,
  commentTicket,
  createTicket,
  escalateTicket,
  getTicket,
  pageTickets,
  pauseTicket,
  processTicket,
  reopenTicket,
  resolveTicket,
  resumeTicket,
  type TicketCreateInput,
  type TicketHandleInput,
  type TicketPageQuery,
  type TicketPriority,
  type TicketView,
} from '../../api/service';
import {
  TICKET_ACTION_LABEL,
  TICKET_PRIORITY_COLOR,
  TICKET_PRIORITY_LABEL,
  TICKET_PRIORITY_OPTIONS,
  TICKET_STATUS_COLOR,
  TICKET_STATUS_LABEL,
  TICKET_STATUS_OPTIONS,
  formatDateTime,
} from '../../utils/dict';

/** 工单处理动作 */
type ActionType =
  | 'assign'
  | 'process'
  | 'comment'
  | 'pause'
  | 'resume'
  | 'escalate'
  | 'resolve'
  | 'close'
  | 'reopen'
  | 'cancel';

/** 动作弹窗标题 */
const ACTION_TITLE: Record<ActionType, string> = {
  assign: '分派工单',
  process: '记录处理进展',
  comment: '追加备注',
  pause: '暂停工单（等待客户）',
  resume: '恢复工单',
  escalate: '升级优先级',
  resolve: '标记已解决',
  close: '关闭工单',
  reopen: '重开工单',
  cancel: '取消工单',
};

/** 动作表单值 */
interface ActionFormValues {
  assigneeId?: number;
  assigneeName?: string;
  content?: string;
  solution?: string;
  reason?: string;
}

/** 筛选条件 */
interface TicketFilterValues {
  keyword?: string;
  status?: string;
  priority?: string;
  customerId?: number | null;
  assigneeId?: number | null;
}

/** 建单表单值 */
interface TicketFormValues {
  customerId: number;
  contactId?: number | null;
  contactName?: string;
  contractId?: number | null;
  orderId?: number | null;
  productCode?: string;
  serialNo?: string;
  category?: string;
  priority: TicketPriority;
  title: string;
  description?: string;
  assigneeId?: number | null;
  assigneeName?: string;
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 可展示的错误信息
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败，请稍后重试';
}

interface TicketFormModalProps {
  open: boolean;
  onCancel: () => void;
  onSaved: () => void;
}

/** 新建工单弹窗 */
function TicketFormModal({ open, onCancel, onSaved }: TicketFormModalProps) {
  const [form] = Form.useForm<TicketFormValues>();
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue({ priority: 'P3' });
    }
  }, [open, form]);

  const handleOk = async () => {
    setSaving(true);
    try {
      const values = await form.validateFields();
      const payload: TicketCreateInput = {
        customerId: values.customerId,
        contactId: values.contactId ?? null,
        contactName: values.contactName || undefined,
        contractId: values.contractId ?? null,
        orderId: values.orderId ?? null,
        productCode: values.productCode || undefined,
        serialNo: values.serialNo || undefined,
        category: values.category || undefined,
        priority: values.priority,
        title: values.title,
        description: values.description,
        assigneeId: values.assigneeId ?? null,
        assigneeName: values.assigneeName || undefined,
      };
      const created = await createTicket(payload);
      message.success(`工单已创建：${created.ticketNo}`);
      onSaved();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal
      open={open}
      title="新建售后工单"
      width={820}
      confirmLoading={saving}
      okText="创建"
      cancelText="取消"
      onOk={() => void handleOk()}
      onCancel={onCancel}
      destroyOnClose
    >
      <Form form={form} layout="vertical" preserve={false}>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item
            name="customerId"
            label="客户 ID"
            rules={[{ required: true, message: '请输入客户 ID' }]}
            style={{ flex: 1 }}
          >
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="contactId" label="联系人 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="contactName" label="联系人姓名" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="contractId" label="合同台账 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="orderId" label="商务订单 ID" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="productCode" label="产品编码" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="serialNo" label="序列号 / 设备号" style={{ flex: 1 }}>
            <Input maxLength={128} />
          </Form.Item>
        </Space>
        <Space size={12} style={{ display: 'flex' }}>
          <Form.Item name="priority" label="优先级" rules={[{ required: true, message: '请选择优先级' }]} style={{ flex: 1 }}>
            <Select options={TICKET_PRIORITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="category" label="问题分类" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="assigneeId" label="处理人 ID（可选）" style={{ flex: 1 }}>
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="assigneeName" label="处理人姓名" style={{ flex: 1 }}>
            <Input maxLength={64} />
          </Form.Item>
        </Space>
        <Form.Item name="title" label="工单标题" rules={[{ required: true, message: '请输入工单标题' }]}>
          <Input maxLength={200} showCount />
        </Form.Item>
        <Form.Item name="description" label="问题描述">
          <Input.TextArea rows={4} maxLength={2000} showCount />
        </Form.Item>
      </Form>
    </Modal>
  );
}

interface TicketDetailDrawerProps {
  ticketId: number | null;
  onClose: () => void;
}

/** 工单详情抽屉（含处理时间线） */
function TicketDetailDrawer({ ticketId, onClose }: TicketDetailDrawerProps) {
  const [detail, setDetail] = useState<TicketView | null>(null);

  useEffect(() => {
    if (!ticketId) {
      setDetail(null);
      return;
    }
    let active = true;
    getTicket(ticketId)
      .then((data) => {
        if (active) {
          setDetail(data);
        }
      })
      .catch((error: unknown) => {
        message.error(errorText(error));
      });
    return () => {
      active = false;
    };
  }, [ticketId]);

  return (
    <Drawer
      open={Boolean(ticketId)}
      title={detail ? `工单详情：${detail.ticketNo}` : '工单详情'}
      width={820}
      onClose={onClose}
    >
      {detail && (
        <>
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="工单号">{detail.ticketNo}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={TICKET_STATUS_COLOR[detail.status as string]}>
                {TICKET_STATUS_LABEL[detail.status as string] ?? detail.status}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="优先级">
              <Tag color={TICKET_PRIORITY_COLOR[detail.priority as string]}>
                {TICKET_PRIORITY_LABEL[detail.priority as string] ?? detail.priority}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="客户">{detail.customerName || detail.customerCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="联系人">{detail.contactName || '-'}</Descriptions.Item>
            <Descriptions.Item label="处理人">{detail.assigneeName || detail.assigneeId || '未分派'}</Descriptions.Item>
            <Descriptions.Item label="产品编码">{detail.productCode || '-'}</Descriptions.Item>
            <Descriptions.Item label="序列号">{detail.serialNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="ERP 订单号">{detail.erpOrderNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="问题分类">{detail.category || '-'}</Descriptions.Item>
            <Descriptions.Item label="响应截止">
              {formatDateTime(detail.responseDeadline)}
              {detail.responseOverdue && <Tag color="red" style={{ marginLeft: 8 }}>响应逾期</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="解决截止">
              {formatDateTime(detail.resolveDeadline)}
              {detail.resolveOverdue && <Tag color="red" style={{ marginLeft: 8 }}>解决逾期</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="首次响应">{formatDateTime(detail.firstRespondedAt)}</Descriptions.Item>
            <Descriptions.Item label="累计暂停">
              {detail.pausedSeconds ? `${detail.pausedSeconds} 秒` : '-'}
            </Descriptions.Item>
            <Descriptions.Item label="重开次数">{detail.reopenCount ?? 0}</Descriptions.Item>
            <Descriptions.Item label="解决时间">{formatDateTime(detail.resolvedAt)}</Descriptions.Item>
            <Descriptions.Item label="关闭时间">{formatDateTime(detail.closedAt)}</Descriptions.Item>
            <Descriptions.Item label="工单标题" span={2}>
              {detail.title}
            </Descriptions.Item>
            <Descriptions.Item label="问题描述" span={2}>
              {detail.description || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="解决方案" span={2}>
              {detail.solution || '-'}
            </Descriptions.Item>
          </Descriptions>
          <Typography.Text strong style={{ display: 'block', margin: '16px 0 8px' }}>
            处理时间线
          </Typography.Text>
          <Timeline
            items={(detail.actions ?? []).map((action) => ({
              color: action.actionType === 'CLOSE' ? 'gray' : action.actionType === 'ESCALATE' ? 'red' : 'blue',
              children: (
                <Space direction="vertical" size={2}>
                  <Space size={8}>
                    <Tag>{TICKET_ACTION_LABEL[action.actionType] ?? action.actionType}</Tag>
                    <Typography.Text type="secondary">{formatDateTime(action.createdAt)}</Typography.Text>
                    <Typography.Text type="secondary">{action.operatorName || action.operatorId || '-'}</Typography.Text>
                  </Space>
                  <Typography.Text>{action.content || '-'}</Typography.Text>
                  {action.fromStatus && (
                    <Typography.Text type="secondary">
                      {TICKET_STATUS_LABEL[action.fromStatus] ?? action.fromStatus}
                      {' → '}
                      {TICKET_STATUS_LABEL[action.toStatus ?? ''] ?? action.toStatus ?? '-'}
                    </Typography.Text>
                  )}
                </Space>
              ),
            }))}
          />
          <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
            SLA 按工作时段推进；暂停期间不计入响应与解决时限，恢复后自动顺延截止时间。
          </Typography.Text>
        </>
      )}
    </Drawer>
  );
}

/** 售后服务工单页 */
export default function TicketPage() {
  const [filterForm] = Form.useForm<TicketFilterValues>();
  const [actionForm] = Form.useForm<ActionFormValues>();
  const [rows, setRows] = useState<TicketView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [filters, setFilters] = useState<TicketPageQuery>({});
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [detailId, setDetailId] = useState<number | null>(null);
  const [actionTarget, setActionTarget] = useState<TicketView | null>(null);
  const [actionType, setActionType] = useState<ActionType | null>(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageTickets({ ...filters, current: page.current, size: page.size });
      setRows(result.records);
      setTotal(Number(result.total));
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filters, page]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSearch = (values: TicketFilterValues) => {
    setFilters({
      keyword: values.keyword || undefined,
      status: values.status || undefined,
      priority: values.priority || undefined,
      customerId: values.customerId ?? null,
      assigneeId: values.assigneeId ?? null,
    });
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const handleReset = () => {
    filterForm.resetFields();
    setFilters({});
    setPage((prev) => ({ ...prev, current: 1 }));
  };

  const openAction = (row: TicketView, type: ActionType) => {
    setActionTarget(row);
    setActionType(type);
    actionForm.resetFields();
    if (type === 'assign') {
      actionForm.setFieldsValue({ assigneeId: row.assigneeId ?? undefined, assigneeName: row.assigneeName ?? undefined });
    } else if (type === 'close' || type === 'resolve') {
      actionForm.setFieldsValue({ solution: row.solution ?? undefined });
    }
  };

  const closeAction = () => {
    setActionTarget(null);
    setActionType(null);
  };

  const handleAction = async () => {
    if (!actionTarget || !actionType) {
      return;
    }
    try {
      const values = await actionForm.validateFields();
      const handleInput: TicketHandleInput = {
        content: values.content,
        solution: values.solution,
        reason: values.reason,
      };
      switch (actionType) {
        case 'assign':
          await assignTicket(actionTarget.id, {
            assigneeId: values.assigneeId as number,
            assigneeName: values.assigneeName,
          });
          break;
        case 'process':
          await processTicket(actionTarget.id, handleInput);
          break;
        case 'comment':
          await commentTicket(actionTarget.id, handleInput);
          break;
        case 'pause':
          await pauseTicket(actionTarget.id, handleInput);
          break;
        case 'resume':
          await resumeTicket(actionTarget.id, handleInput);
          break;
        case 'escalate':
          await escalateTicket(actionTarget.id, handleInput);
          break;
        case 'resolve':
          await resolveTicket(actionTarget.id, handleInput);
          break;
        case 'close':
          await closeTicket(actionTarget.id, handleInput);
          break;
        case 'reopen':
          await reopenTicket(actionTarget.id, handleInput);
          break;
        case 'cancel':
          await cancelTicket(actionTarget.id, handleInput);
          break;
        default:
          break;
      }
      message.success(`${ACTION_TITLE[actionType]}成功`);
      closeAction();
      void loadData();
    } catch (error) {
      if (error instanceof Error) {
        message.error(errorText(error));
      }
    }
  };

  const actionButton = (row: TicketView, type: ActionType, label: string, danger = false) => {
    if (type === 'resume' || type === 'escalate') {
      return (
        <Popconfirm
          title={ACTION_TITLE[type]}
          description="确认执行该操作？"
          onConfirm={() => {
            void (async () => {
              try {
                if (type === 'resume') {
                  await resumeTicket(row.id);
                } else {
                  await escalateTicket(row.id);
                }
                message.success(`${ACTION_TITLE[type]}成功`);
                void loadData();
              } catch (error) {
                message.error(errorText(error));
              }
            })();
          }}
        >
          <Button size="small" type="link" danger={danger}>
            {label}
          </Button>
        </Popconfirm>
      );
    }
    return (
      <Button size="small" type="link" danger={danger} onClick={() => openAction(row, type)}>
        {label}
      </Button>
    );
  };

  const columns: TableProps<TicketView>['columns'] = [
    { title: '工单号', dataIndex: 'ticketNo', width: 190, fixed: 'left' },
    {
      title: '优先级',
      dataIndex: 'priority',
      width: 100,
      render: (value?: string | null) => (
        <Tag color={TICKET_PRIORITY_COLOR[value ?? '']}>{TICKET_PRIORITY_LABEL[value ?? ''] ?? value ?? '-'}</Tag>
      ),
    },
    { title: '标题', dataIndex: 'title', width: 220, ellipsis: true },
    { title: '客户', dataIndex: 'customerName', width: 170, render: (value?: string | null) => value || '-' },
    { title: '处理人', dataIndex: 'assigneeName', width: 120, render: (value?: string | null) => value || '未分派' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 110,
      render: (value?: string | null) => (
        <Tag color={TICKET_STATUS_COLOR[value ?? '']}>{TICKET_STATUS_LABEL[value ?? ''] ?? value ?? '-'}</Tag>
      ),
    },
    {
      title: '响应截止',
      dataIndex: 'responseDeadline',
      width: 180,
      render: (value: string | null | undefined, row) => (
        <Space size={4}>
          {formatDateTime(value)}
          {row.responseOverdue && <Tag color="red">逾期</Tag>}
        </Space>
      ),
    },
    {
      title: '解决截止',
      dataIndex: 'resolveDeadline',
      width: 180,
      render: (value: string | null | undefined, row) => (
        <Space size={4}>
          {formatDateTime(value)}
          {row.resolveOverdue && <Tag color="red">逾期</Tag>}
        </Space>
      ),
    },
    {
      title: '操作',
      key: 'action',
      fixed: 'right',
      width: 300,
      render: (_, row) => {
        const status = row.status as string;
        const finished = status === 'CLOSED' || status === 'CANCELLED';
        return (
          <Space size={0} wrap>
            <Button size="small" type="link" onClick={() => setDetailId(row.id)}>
              详情
            </Button>
            {!finished && status !== 'RESOLVED' && hasPermission('sys:crm:ticket:assign') && actionButton(row, 'assign', '分派')}
            {!finished && status !== 'RESOLVED' && status !== 'WAITING_CUSTOMER'
              && hasPermission('sys:crm:ticket:handle') && actionButton(row, 'process', '处理')}
            {!finished && hasPermission('sys:crm:ticket:handle') && actionButton(row, 'comment', '备注')}
            {!finished && status !== 'RESOLVED' && status !== 'WAITING_CUSTOMER'
              && hasPermission('sys:crm:ticket:handle') && actionButton(row, 'pause', '暂停')}
            {status === 'WAITING_CUSTOMER' && hasPermission('sys:crm:ticket:handle') && actionButton(row, 'resume', '恢复')}
            {!finished && hasPermission('sys:crm:ticket:handle') && actionButton(row, 'escalate', '升级')}
            {!finished && status !== 'RESOLVED' && hasPermission('sys:crm:ticket:handle') && actionButton(row, 'resolve', '解决')}
            {status === 'RESOLVED' && hasPermission('sys:crm:ticket:handle') && actionButton(row, 'close', '关闭')}
            {(status === 'RESOLVED' || status === 'CLOSED') && hasPermission('sys:crm:ticket:handle')
              && actionButton(row, 'reopen', '重开')}
            {!finished && status !== 'RESOLVED' && hasPermission('sys:crm:ticket:handle')
              && actionButton(row, 'cancel', '取消', true)}
          </Space>
        );
      },
    },
  ];

  return (
    <Card
      title="售后工单"
      extra={
        hasPermission('sys:crm:ticket:create') && (
          <Button type="primary" onClick={() => setFormOpen(true)}>
            新建工单
          </Button>
        )
      }
    >
      <Form form={filterForm} layout="inline" onFinish={handleSearch} style={{ marginBottom: 16, rowGap: 12 }}>
        <Form.Item name="keyword">
          <Input placeholder="工单号 / 标题 / 客户" allowClear style={{ width: 200 }} />
        </Form.Item>
        <Form.Item name="status">
          <Select placeholder="状态" allowClear style={{ width: 140 }} options={TICKET_STATUS_OPTIONS} />
        </Form.Item>
        <Form.Item name="priority">
          <Select placeholder="优先级" allowClear style={{ width: 130 }} options={TICKET_PRIORITY_OPTIONS} />
        </Form.Item>
        <Form.Item name="customerId">
          <InputNumber min={1} precision={0} placeholder="客户 ID" style={{ width: 120 }} />
        </Form.Item>
        <Form.Item name="assigneeId">
          <InputNumber min={1} precision={0} placeholder="处理人 ID" style={{ width: 120 }} />
        </Form.Item>
        <Form.Item>
          <Space>
            <Button type="primary" htmlType="submit">
              查询
            </Button>
            <Button onClick={handleReset}>重置</Button>
          </Space>
        </Form.Item>
      </Form>
      <Table<TicketView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={rows}
        scroll={{ x: 1600 }}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
      <Typography.Text type="secondary" style={{ display: 'block', marginTop: 12 }}>
        D7 口径：P1 2h/8h、P2 4h/24h、P3 8h/72h；7×8 工作时段（09:00–18:00）。逾期为实时派生，不落定时任务。
      </Typography.Text>

      <TicketFormModal
        open={formOpen}
        onCancel={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          void loadData();
        }}
      />
      <TicketDetailDrawer ticketId={detailId} onClose={() => setDetailId(null)} />

      <Modal
        open={actionType !== null}
        title={actionType ? ACTION_TITLE[actionType] : '工单操作'}
        okText="确定"
        cancelText="取消"
        onOk={() => void handleAction()}
        onCancel={closeAction}
        destroyOnClose
      >
        <Form form={actionForm} layout="vertical" preserve={false}>
          {actionType === 'assign' && (
            <>
              <Form.Item name="assigneeId" label="处理人 ID" rules={[{ required: true, message: '请输入处理人 ID' }]}>
                <InputNumber min={1} precision={0} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item name="assigneeName" label="处理人姓名">
                <Input maxLength={64} />
              </Form.Item>
            </>
          )}
          {(actionType === 'process' || actionType === 'comment') && (
            <Form.Item
              name="content"
              label={actionType === 'process' ? '处理内容' : '备注内容'}
              rules={[{ required: true, message: '请输入内容' }]}
            >
              <Input.TextArea rows={3} maxLength={2000} showCount />
            </Form.Item>
          )}
          {actionType === 'pause' && (
            <Form.Item name="reason" label="暂停原因" rules={[{ required: true, message: '请输入暂停原因' }]}>
              <Input.TextArea rows={2} maxLength={500} showCount />
            </Form.Item>
          )}
          {actionType === 'resume' && (
            <Form.Item name="content" label="恢复说明（可选）">
              <Input maxLength={500} />
            </Form.Item>
          )}
          {actionType === 'escalate' && (
            <Form.Item name="content" label="升级说明（可选）">
              <Input maxLength={500} />
            </Form.Item>
          )}
          {(actionType === 'resolve' || actionType === 'close') && (
            <Form.Item
              name="solution"
              label="解决方案"
              rules={[{ required: true, message: '请输入解决方案' }]}
            >
              <Input.TextArea rows={3} maxLength={2000} showCount />
            </Form.Item>
          )}
          {(actionType === 'reopen' || actionType === 'cancel') && (
            <Form.Item
              name="reason"
              label={actionType === 'reopen' ? '重开原因' : '取消原因'}
              rules={[{ required: true, message: '请输入原因' }]}
            >
              <Input.TextArea rows={2} maxLength={500} showCount />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </Card>
  );
}
