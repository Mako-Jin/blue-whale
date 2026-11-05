// 变更单（ECO）：列表 + 状态筛选 + 新建（选择已通过 ECR）+ 评估 + 任务维护 + 审批/执行/完成/关闭
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
import { pageChgRequests, type ChgRequest } from '../../api/change';
import {
  addChgTask,
  approveChgOrder,
  assessChgOrder,
  cancelChgOrder,
  closeChgOrder,
  completeChgOrder,
  completeChgTask,
  createChgOrder,
  deleteChgOrder,
  deleteChgTask,
  executeChgOrder,
  getChgOrder,
  pageChgOrders,
  rejectChgOrder,
  submitChgOrder,
  updateChgOrder,
  updateChgTask,
  type ChgOrder,
  type ChgOrderInput,
  type ChgOrderStatus,
  type ChgOrderTask,
  type ChgTaskInput,
  type ChgTaskStatus,
} from '../../api/changeOrder';
import { hasPermission } from '../../utils/permission';

/** 变更单状态文案与颜色 */
const STATUS_META: Record<ChgOrderStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  ASSESSING: { text: '评估中', color: 'processing' },
  APPROVING: { text: '审批中', color: 'gold' },
  APPROVED: { text: '已通过', color: 'green' },
  EXECUTING: { text: '执行中', color: 'blue' },
  COMPLETED: { text: '已完成', color: 'green' },
  CLOSED: { text: '已关闭', color: 'default' },
  REJECTED: { text: '已驳回', color: 'red' },
  CANCELLED: { text: '已取消', color: 'default' },
};

/** 状态筛选选项 */
const STATUS_OPTIONS = Object.entries(STATUS_META).map(([value, meta]) => ({
  value,
  label: meta.text,
}));

/** 任务状态文案 */
const TASK_STATUS_META: Record<ChgTaskStatus, { text: string; color: string }> = {
  PENDING: { text: '待完成', color: 'gold' },
  COMPLETED: { text: '已完成', color: 'green' },
};

/** 任务目标对象类型选项 */
const OBJECT_TYPE_OPTIONS = [
  { value: 'PART', label: '零件' },
  { value: 'BOM', label: 'BOM' },
  { value: 'DOCUMENT', label: '文档' },
];

/** 可编辑变更单的状态 */
const EDITABLE_STATUS = ['DRAFT', 'ASSESSING'];

/** 可取消变更单的状态 */
const CANCELLABLE_STATUS = ['DRAFT', 'ASSESSING', 'APPROVING', 'APPROVED'];

/** 可关闭变更单的状态 */
const CLOSABLE_STATUS = ['COMPLETED', 'REJECTED', 'CANCELLED'];

/** 变更单表单值 */
interface OrderFormValues {
  ecrId?: string;
  title: string;
  impactAssessment?: string;
  executePlan?: string;
}

/** 评估表单值 */
interface AssessFormValues {
  impactAssessment: string;
  executePlan?: string;
}

/** 任务表单值 */
interface TaskFormValues {
  taskName: string;
  taskType: string;
  targetType?: 'PART' | 'BOM' | 'DOCUMENT';
  targetId?: number;
  targetCode?: string;
  planDesc?: string;
  ownerId?: number;
  sortNo?: number;
}

/** 审批弹窗状态 */
interface ApproveState {
  order: ChgOrder;
  action: 'approve' | 'reject';
}

/** 任务完成弹窗状态 */
interface TaskCompleteState {
  order: ChgOrder;
  task: ChgOrderTask;
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

export default function ChangeOrderCenter() {
  const [filters, setFilters] = useState<{ ecoNo?: string; status?: ChgOrderStatus }>({});
  const [rows, setRows] = useState<ChgOrder[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [size, setSize] = useState(20);
  const [loading, setLoading] = useState(false);
  const [refreshKey, setRefreshKey] = useState(0);

  const [detail, setDetail] = useState<ChgOrder | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<ChgOrder | null>(null);
  const [orderForm] = Form.useForm<OrderFormValues>();
  const [ecrOptions, setEcrOptions] = useState<ChgRequest[]>([]);
  const [ecrLoading, setEcrLoading] = useState(false);

  const [assessOpen, setAssessOpen] = useState(false);
  const [assessOrder, setAssessOrder] = useState<ChgOrder | null>(null);
  const [assessForm] = Form.useForm<AssessFormValues>();

  const [taskOpen, setTaskOpen] = useState(false);
  const [taskOrder, setTaskOrder] = useState<ChgOrder | null>(null);
  const [taskEditing, setTaskEditing] = useState<ChgOrderTask | null>(null);
  const [taskForm] = Form.useForm<TaskFormValues>();

  const [approveState, setApproveState] = useState<ApproveState | null>(null);
  const [approveForm] = Form.useForm<{ comment?: string }>();

  const [taskCompleteState, setTaskCompleteState] = useState<TaskCompleteState | null>(null);
  const [taskCompleteForm] = Form.useForm<{ resultVersionNo: string }>();

  const canAdd = hasPermission('sys:pdm:change:order:add');
  const canEdit = hasPermission('sys:pdm:change:order:edit');
  const canDelete = hasPermission('sys:pdm:change:order:delete');
  const canSubmit = hasPermission('sys:pdm:change:order:submit');
  const canApprove = hasPermission('sys:pdm:change:order:approve');
  const canExecute = hasPermission('sys:pdm:change:order:execute');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageChgOrders({ current, size, ...filters });
      setRows(page.records);
      setTotal(page.total);
    } finally {
      setLoading(false);
    }
  }, [current, size, filters]);

  useEffect(() => {
    void load();
  }, [load, refreshKey]);

  const afterOrder = (updated: ChgOrder, text: string) => {
    message.success(text);
    setDetail(updated);
    setRefreshKey((key) => key + 1);
  };

  const refreshDetail = async (order: ChgOrder) => {
    setDetailLoading(true);
    try {
      setDetail(await getChgOrder(order.id));
    } finally {
      setDetailLoading(false);
    }
  };

  const openCreate = async () => {
    setEditing(null);
    orderForm.resetFields();
    setFormOpen(true);
    setEcrLoading(true);
    try {
      const page = await pageChgRequests({ current: 1, size: 200, status: 'APPROVED' });
      setEcrOptions(page.records);
    } finally {
      setEcrLoading(false);
    }
  };

  const openEdit = (order: ChgOrder) => {
    setEditing(order);
    setEcrOptions([]);
    orderForm.setFieldsValue({
      ecrId: order.ecrId,
      title: order.title,
      impactAssessment: order.impactAssessment,
      executePlan: order.executePlan,
    });
    setFormOpen(true);
  };

  const handleSave = async () => {
    const values = await orderForm.validateFields();
    const input: ChgOrderInput = {
      title: values.title,
      impactAssessment: values.impactAssessment,
      executePlan: values.executePlan,
    };
    if (editing) {
      afterOrder(await updateChgOrder(editing.id, input), '保存成功');
    } else {
      afterOrder(await createChgOrder({ ...input, ecrId: values.ecrId }), '变更单已创建');
    }
    setFormOpen(false);
  };

  const openAssess = (order: ChgOrder) => {
    setAssessOrder(order);
    assessForm.setFieldsValue({
      impactAssessment: order.impactAssessment,
      executePlan: order.executePlan,
    });
    setAssessOpen(true);
  };

  const handleAssess = async () => {
    if (!assessOrder) {
      return;
    }
    const values = await assessForm.validateFields();
    afterOrder(await assessChgOrder(assessOrder.id, values), '评估已保存');
    setAssessOpen(false);
  };

  const openTask = (order: ChgOrder, task: ChgOrderTask | null) => {
    setTaskOrder(order);
    setTaskEditing(task);
    if (task) {
      taskForm.setFieldsValue({
        taskName: task.taskName,
        taskType: task.taskType,
        targetType: task.targetType,
        targetId: task.targetId,
        targetCode: task.targetCode,
        planDesc: task.planDesc,
        ownerId: task.ownerId,
        sortNo: task.sortNo,
      });
    } else {
      taskForm.resetFields();
    }
    setTaskOpen(true);
  };

  const handleTaskSave = async () => {
    if (!taskOrder) {
      return;
    }
    const values = await taskForm.validateFields();
    const input: ChgTaskInput = { ...values };
    if (taskEditing) {
      afterOrder(await updateChgTask(taskOrder.id, taskEditing.id, input), '任务已保存');
    } else {
      afterOrder(await addChgTask(taskOrder.id, input), '任务已添加');
    }
    setTaskOpen(false);
  };

  const handleApprove = async () => {
    if (!approveState) {
      return;
    }
    const values = await approveForm.validateFields();
    if (approveState.action === 'reject') {
      afterOrder(await rejectChgOrder(approveState.order.id, values.comment ?? ''), '已驳回');
    } else {
      afterOrder(await approveChgOrder(approveState.order.id, values.comment), '审批通过');
    }
    setApproveState(null);
    approveForm.resetFields();
  };

  const handleTaskComplete = async () => {
    if (!taskCompleteState) {
      return;
    }
    const values = await taskCompleteForm.validateFields();
    afterOrder(
      await completeChgTask(
        taskCompleteState.order.id,
        taskCompleteState.task.id,
        values.resultVersionNo,
      ),
      '任务已完成',
    );
    setTaskCompleteState(null);
    taskCompleteForm.resetFields();
  };

  const columns: TableProps<ChgOrder>['columns'] = [
    { title: '变更单编号', dataIndex: 'ecoNo', key: 'ecoNo', width: 190 },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '来源 ECR',
      dataIndex: 'ecrId',
      key: 'ecrId',
      width: 140,
      render: (value?: string) => value ?? '-',
    },
    {
      title: '任务',
      dataIndex: 'tasks',
      key: 'tasks',
      width: 90,
      render: (value: ChgOrderTask[]) => `${value?.length ?? 0} 项`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: ChgOrderStatus) => {
        const meta = STATUS_META[value];
        return meta ? <Tag color={meta.color}>{meta.text}</Tag> : value;
      },
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170, render: formatTime },
    {
      title: '操作',
      key: 'action',
      width: 320,
      render: (_, record) => (
        <Space size={0} wrap>
          <Button type="link" onClick={() => void refreshDetail(record)}>
            详情
          </Button>
          {canEdit && EDITABLE_STATUS.includes(record.status) ? (
            <Button type="link" onClick={() => openEdit(record)}>
              编辑
            </Button>
          ) : null}
          {canEdit && record.status === 'DRAFT' ? (
            <Button type="link" onClick={() => openAssess(record)}>
              评估
            </Button>
          ) : null}
          {canSubmit && record.status === 'ASSESSING' ? (
            <Button type="link" onClick={async () => afterOrder(await submitChgOrder(record.id), '已提交审批')}>
              提交
            </Button>
          ) : null}
          {canApprove && record.status === 'APPROVING' ? (
            <Button
              type="link"
              onClick={() => {
                setApproveState({ order: record, action: 'approve' });
                approveForm.resetFields();
              }}
            >
              审批
            </Button>
          ) : null}
          {canExecute && record.status === 'APPROVED' ? (
            <Button type="link" onClick={async () => afterOrder(await executeChgOrder(record.id), '已开始执行')}>
              执行
            </Button>
          ) : null}
          {canExecute && record.status === 'EXECUTING' ? (
            <Button type="link" onClick={async () => afterOrder(await completeChgOrder(record.id), '变更单已完成')}>
              完成
            </Button>
          ) : null}
          {canEdit && CLOSABLE_STATUS.includes(record.status) ? (
            <Button type="link" onClick={async () => afterOrder(await closeChgOrder(record.id), '已关闭')}>
              关闭
            </Button>
          ) : null}
          {canDelete && record.status === 'DRAFT' ? (
            <Popconfirm
              title="确认删除该变更单？"
              onConfirm={async () => {
                await deleteChgOrder(record.id);
                message.success('删除成功');
                setDetail(null);
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

  const detailEditable = detail !== null && canEdit && EDITABLE_STATUS.includes(detail.status);
  const detailCanCancel = detail !== null && canEdit && CANCELLABLE_STATUS.includes(detail.status);

  return (
    <Card
      size="small"
      title="变更单（ECO）"
      extra={
        <Space>
          <Input.Search
            allowClear
            placeholder="变更单编号"
            style={{ width: 180 }}
            onSearch={(value) => {
              setCurrent(1);
              setFilters((prev) => ({ ...prev, ecoNo: value || undefined }));
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
            <Button type="primary" onClick={() => void openCreate()}>
              新建变更单
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

      <Drawer
        open={detail !== null}
        width={760}
        loading={detailLoading}
        title={detail ? `变更单 ${detail.ecoNo}` : '变更单详情'}
        onClose={() => setDetail(null)}
      >
        {detail ? (
          <Space direction="vertical" style={{ width: '100%' }} size="middle">
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="标题" span={2}>
                {detail.title}
              </Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={STATUS_META[detail.status]?.color}>{STATUS_META[detail.status]?.text}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="来源 ECR">{detail.ecrId}</Descriptions.Item>
              <Descriptions.Item label="影响对象">{detail.impactObjects?.length ?? 0} 项</Descriptions.Item>
              <Descriptions.Item label="创建时间">{formatTime(detail.createTime)}</Descriptions.Item>
              <Descriptions.Item label="影响评估" span={2}>
                {detail.impactAssessment || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="执行计划" span={2}>
                {detail.executePlan || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="审批意见" span={2}>
                {detail.approveComment || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Space wrap>
              {detailEditable ? (
                <Button onClick={() => openEdit(detail)}>编辑</Button>
              ) : null}
              {canEdit && detail.status === 'DRAFT' ? (
                <Button onClick={() => openAssess(detail)}>影响评估</Button>
              ) : null}
              {canSubmit && detail.status === 'ASSESSING' ? (
                <Button
                  type="primary"
                  onClick={async () => afterOrder(await submitChgOrder(detail.id), '已提交审批')}
                >
                  提交审批
                </Button>
              ) : null}
              {canApprove && detail.status === 'APPROVING' ? (
                <Button
                  type="primary"
                  onClick={() => {
                    setApproveState({ order: detail, action: 'approve' });
                    approveForm.resetFields();
                  }}
                >
                  审批
                </Button>
              ) : null}
              {canExecute && detail.status === 'APPROVED' ? (
                <Button type="primary" onClick={async () => afterOrder(await executeChgOrder(detail.id), '已开始执行')}>
                  执行
                </Button>
              ) : null}
              {canExecute && detail.status === 'EXECUTING' ? (
                <Button onClick={async () => afterOrder(await completeChgOrder(detail.id), '变更单已完成')}>
                  完成变更单
                </Button>
              ) : null}
              {detailCanCancel ? (
                <Button danger onClick={async () => afterOrder(await cancelChgOrder(detail.id), '已取消')}>
                  取消
                </Button>
              ) : null}
              {canEdit && CLOSABLE_STATUS.includes(detail.status) ? (
                <Button onClick={async () => afterOrder(await closeChgOrder(detail.id), '已关闭')}>
                  关闭
                </Button>
              ) : null}
            </Space>

            <Card
              size="small"
              title="变更任务"
              extra={
                detailEditable ? (
                  <Button type="link" onClick={() => openTask(detail, null)}>
                    新增任务
                  </Button>
                ) : null
              }
            >
              <Table
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={detail.tasks}
                columns={[
                  { title: '任务名称', dataIndex: 'taskName' },
                  { title: '类型', dataIndex: 'taskType', width: 100 },
                  {
                    title: '目标对象',
                    dataIndex: 'targetCode',
                    width: 130,
                    render: (value?: string) => value || '-',
                  },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    width: 90,
                    render: (value: ChgTaskStatus) => {
                      const meta = TASK_STATUS_META[value];
                      return meta ? <Tag color={meta.color}>{meta.text}</Tag> : value;
                    },
                  },
                  {
                    title: '结果版本',
                    dataIndex: 'resultVersionNo',
                    width: 100,
                    render: (value?: string) => value || '-',
                  },
                  {
                    title: '操作',
                    key: 'taskAction',
                    width: 200,
                    render: (_, task) => (
                      <Space size={0}>
                        {detailEditable ? (
                          <Button type="link" onClick={() => openTask(detail, task)}>
                            编辑
                          </Button>
                        ) : null}
                        {canExecute && detail.status === 'EXECUTING' && task.status === 'PENDING' ? (
                          <Button
                            type="link"
                            onClick={() => {
                              setTaskCompleteState({ order: detail, task });
                              taskCompleteForm.resetFields();
                            }}
                          >
                            完成
                          </Button>
                        ) : null}
                        {detailEditable ? (
                          <Popconfirm
                            title="确认删除该任务？"
                            onConfirm={async () =>
                              afterOrder(await deleteChgTask(detail.id, task.id), '任务已删除')
                            }
                          >
                            <Button type="link" danger>
                              删除
                            </Button>
                          </Popconfirm>
                        ) : null}
                      </Space>
                    ),
                  },
                ]}
              />
            </Card>
          </Space>
        ) : null}
      </Drawer>

      <Modal
        open={formOpen}
        title={editing ? `编辑变更单 ${editing.ecoNo}` : '新建变更单'}
        width={720}
        onOk={handleSave}
        onCancel={() => setFormOpen(false)}
        destroyOnClose
      >
        <Form form={orderForm} layout="vertical">
          <Form.Item
            name="ecrId"
            label="来源变更申请（仅已通过）"
            rules={[{ required: !editing, message: '请选择已通过的变更申请' }]}
          >
            <Select
              showSearch
              disabled={editing !== null}
              loading={ecrLoading}
              placeholder="选择已通过的 ECR"
              optionFilterProp="label"
              options={ecrOptions.map((item) => ({
                value: item.id,
                label: `${item.changeNo} · ${item.changeReason}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="title" label="变更标题" rules={[{ required: true, message: '请输入变更标题' }]}>
            <Input maxLength={200} placeholder="简要描述本次变更" />
          </Form.Item>
          <Form.Item name="impactAssessment" label="影响评估">
            <Input.TextArea rows={3} maxLength={2000} showCount placeholder="评估受影响的零件、BOM 与文档" />
          </Form.Item>
          <Form.Item name="executePlan" label="执行计划">
            <Input.TextArea rows={3} maxLength={2000} showCount placeholder="按任务说明整体执行顺序" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={assessOpen}
        title="变更单影响评估"
        onOk={handleAssess}
        onCancel={() => setAssessOpen(false)}
        destroyOnClose
      >
        <Form form={assessForm} layout="vertical">
          <Form.Item
            name="impactAssessment"
            label="影响评估"
            rules={[{ required: true, message: '请填写影响评估' }]}
          >
            <Input.TextArea rows={4} maxLength={2000} showCount placeholder="评估受影响的零件、BOM 与文档" />
          </Form.Item>
          <Form.Item name="executePlan" label="执行计划">
            <Input.TextArea rows={3} maxLength={2000} showCount placeholder="整体执行顺序与注意事项" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={taskOpen}
        title={taskEditing ? '修改变更任务' : '新增变更任务'}
        width={680}
        onOk={handleTaskSave}
        onCancel={() => setTaskOpen(false)}
        destroyOnClose
      >
        <Form form={taskForm} layout="vertical">
          <Form.Item name="taskName" label="任务名称" rules={[{ required: true, message: '请输入任务名称' }]}>
            <Input maxLength={200} placeholder="如：更新零件图并发布 V1.2" />
          </Form.Item>
          <Form.Item name="taskType" label="任务类型" rules={[{ required: true, message: '请输入任务类型' }]}>
            <Input maxLength={32} placeholder="如 DESIGN / NOTICE" />
          </Form.Item>
          <Space wrap>
            <Form.Item name="targetType" label="目标对象类型">
              <Select allowClear style={{ width: 140 }} options={OBJECT_TYPE_OPTIONS} placeholder="类型" />
            </Form.Item>
            <Form.Item name="targetId" label="目标对象ID">
              <InputNumber style={{ width: 180 }} min={1} placeholder="对象ID" />
            </Form.Item>
            <Form.Item name="targetCode" label="目标对象编码">
              <Input style={{ width: 180 }} maxLength={64} placeholder="对象编码" />
            </Form.Item>
            <Form.Item name="ownerId" label="负责人ID">
              <InputNumber style={{ width: 180 }} min={1} placeholder="负责人ID" />
            </Form.Item>
            <Form.Item name="sortNo" label="排序号">
              <InputNumber style={{ width: 140 }} min={0} placeholder="排序号" />
            </Form.Item>
          </Space>
          <Form.Item name="planDesc" label="执行说明">
            <Input.TextArea rows={3} maxLength={500} showCount placeholder="单任务执行步骤与交付物" />
          </Form.Item>
        </Form>
      </Modal>

      <Modal
        open={approveState !== null}
        title={approveState?.action === 'reject' ? '驳回变更单' : '审批变更单'}
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
          <Button danger onClick={() => setApproveState({ ...approveState, action: 'reject' })}>
            改为驳回
          </Button>
        ) : null}
      </Modal>

      <Modal
        open={taskCompleteState !== null}
        title={taskCompleteState ? `完成任务：${taskCompleteState.task.taskName}` : '完成任务'}
        onOk={handleTaskComplete}
        onCancel={() => {
          setTaskCompleteState(null);
          taskCompleteForm.resetFields();
        }}
        destroyOnClose
      >
        <Form form={taskCompleteForm} layout="vertical">
          <Form.Item
            name="resultVersionNo"
            label="结果版本号"
            rules={[
              { required: true, message: '请输入结果版本号' },
              {
                pattern: /^V\d+\.\d+$/,
                message: '版本号格式应为 V{主版本}.{次版本}，如 V1.2',
              },
            ]}
          >
            <Input maxLength={32} placeholder="如 V1.2；只登记新版本，不覆盖已发布版本" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
