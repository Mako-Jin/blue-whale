// 我的待办 + 审批页：分页待办列表 + 审批抽屉（表单可编辑保存 + 意见 + 同意/驳回）
// 另提供转办 / 委派 / 回退 / 处理完成（resolve）/ 加签操作，入参经 Modal + Form 收集。
// 契约层：../../api/workflow（待办/审批/实例）、../../api/form（表单定义/数据），本文件不改动契约层。
// 雪花 ID 全链路只用 string；审批前若有未保存的表单修改先 PUT /form/data/{id}。

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  AutoComplete,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Spin,
  Table,
  Tag,
  Typography,
  type TableColumnsType,
} from 'antd';
import { FormRenderer, type WhaleFormFieldConfig } from '@bluewhale/whale-ui';
import { formApi, formDataApi } from '../../api/form';
import { workflowApi, type ApprovalRequest, type Id, type ProcessTask } from '../../api/workflow';
import { parseDefinitionModel } from '../start/model';
import { serializeValues } from '../start/formValues';

/** 任务状态文案（0 待办 / 1 已同意 / 2 已驳回 / 3 已取消） */
const STATUS_TEXT: Record<number, string> = { 0: '待办', 1: '已同意', 2: '已驳回', 3: '已取消' };

/** 任务状态 Tag 颜色，未识别状态回落 default */
const STATUS_COLOR: Record<number, string> = {
  0: 'processing',
  1: 'success',
  2: 'error',
  3: 'default',
};

/** 状态过滤下拉项（清空由 allowClear 置 undefined → 查看全部） */
const STATUS_OPTIONS = [
  { value: 0, label: '待办' },
  { value: 1, label: '已同意' },
  { value: 2, label: '已驳回' },
  { value: 3, label: '已取消' },
];

/** 页面查询状态：受控分页 + 状态过滤（默认只看待办 status=0） */
interface QueryState {
  current: number;
  size: number;
  status?: number;
}

/** 所属实例摘要：任务行不自带定义编码/标题，按需补查实例详情 */
interface InstanceBrief {
  definitionCode: string;
  title: string;
}

/** 审批动作 */
type ApprovalAction = 'approve' | 'reject';

/** 任务办理动作（弹窗收集入参） */
type TaskAction = 'transfer' | 'delegate' | 'rollback' | 'resolve' | 'addSign';

/** 办理动作弹窗标题 */
const ACTION_TITLES: Record<TaskAction, string> = {
  transfer: '转办任务',
  delegate: '委派任务',
  rollback: '回退任务',
  resolve: '处理完成（交回委派人）',
  addSign: '加签',
};

/** 办理动作弹窗表单值 */
interface ActionFormValues {
  targetUser?: string;
  targetNodeKey?: string;
  comment?: string;
  assignees?: string;
}

/** 加签人文本解析：支持逗号/分号/空白/换行分隔，去空白并去重 */
const parseAssignees = (raw: string | undefined): string[] =>
  Array.from(
    new Set(
      (raw ?? '')
        .split(/[,，;；\s]+/)
        .map((item) => item.trim())
        .filter(Boolean),
    ),
  );

export default function TaskCenter() {
  const { message } = App.useApp();

  const [query, setQuery] = useState<QueryState>({ current: 1, size: 10, status: 0 });
  const [dataSource, setDataSource] = useState<ProcessTask[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  // instanceId -> 所属实例摘要（列表展示与抽屉共用，避免重复补查）
  const [instanceMap, setInstanceMap] = useState<Record<Id, InstanceBrief>>({});

  // 审批抽屉状态
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [activeTask, setActiveTask] = useState<ProcessTask | null>(null);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [formReady, setFormReady] = useState(false);
  const [formFields, setFormFields] = useState<WhaleFormFieldConfig[]>([]);
  const [formValues, setFormValues] = useState<Record<string, unknown>>({});
  const [formError, setFormError] = useState<string | null>(null);
  // 关联表单数据 ID（实例 businessKey）：编辑保存与审批前置保存都依赖它
  const [formDataId, setFormDataId] = useState<Id | null>(null);
  const [formDirty, setFormDirty] = useState(false);
  const [savingForm, setSavingForm] = useState(false);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

  // 转办 / 委派 / 回退 / 处理完成弹窗
  const [taskAction, setTaskAction] = useState<TaskAction | null>(null);
  const [actionForm] = Form.useForm<ActionFormValues>();
  const [actionSubmitting, setActionSubmitting] = useState(false);
  // 回退目标节点候选（来自流程定义审批节点；加载失败时仍可手填 key）
  const [rollbackOptions, setRollbackOptions] = useState<{ value: string; label: string }[]>([]);
  const [rollbackLoading, setRollbackLoading] = useState(false);

  /** 补查当前页待办所属实例摘要（单条失败静默降级为 '-'，不影响列表与审批） */
  const loadInstanceBriefs = useCallback(async (records: ProcessTask[]) => {
    const ids = Array.from(new Set(records.map((record) => record.instanceId)));
    const briefs = await Promise.all(
      ids.map(async (id) => {
        try {
          const instance = await workflowApi.instanceDetail(id);
          return [id, { definitionCode: instance.definitionCode, title: instance.title }] as const;
        } catch {
          return null;
        }
      }),
    );
    setInstanceMap((prev) => {
      const next = { ...prev };
      briefs.forEach((entry) => {
        if (entry) {
          next[entry[0]] = entry[1];
        }
      });
      return next;
    });
  }, []);

  /** 拉取待办分页列表（受控分页 + 状态过滤） */
  const loadList = useCallback(async () => {
    setLoading(true);
    try {
      const result = await workflowApi.taskPage({
        current: query.current,
        size: query.size,
        status: query.status,
      });
      const records = result.records ?? [];
      setDataSource(records);
      setTotal(result.total ?? 0);
      await loadInstanceBriefs(records);
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [query, message, loadInstanceBriefs]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  /**
   * 抽屉内加载表单：formId → 表单定义 fields；实例 businessKey → 表单数据 fields（键为 columnCode）。
   * 任一步缺失或失败即降级为「无表单数据」占位，**不阻塞审批提交**（F4-c）。
   */
  const loadForm = async (task: ProcessTask) => {
    setDrawerLoading(true);
    try {
      if (!task.formId) {
        setFormError('该任务未绑定表单');
        return;
      }
      const definition = await formApi.detail(task.formId);
      const fields = definition.fields ?? [];
      if (fields.length === 0) {
        setFormError('绑定表单未配置字段');
        return;
      }
      let businessKey: string | null | undefined = null;
      try {
        const instance = await workflowApi.instanceDetail(task.instanceId);
        if (instance) {
          setInstanceMap((prev) => ({
            ...prev,
            [task.instanceId]: { definitionCode: instance.definitionCode, title: instance.title },
          }));
          businessKey = instance.businessKey;
        }
      } catch {
        // 实例详情不可达：视为无关联数据，降级为占位
      }
      if (!businessKey) {
        setFormError('未关联表单数据');
        return;
      }
      const data = await formDataApi.detail(businessKey);
      setFormFields(fields);
      setFormValues(data?.fields ?? {});
      setFormDataId(businessKey);
      setFormDirty(false);
      setFormReady(true);
    } catch {
      setFormError('表单加载失败');
    } finally {
      setDrawerLoading(false);
    }
  };

  /** 打开审批（待办）/ 查看（非待办）抽屉 */
  const openDrawer = (record: ProcessTask) => {
    setActiveTask(record);
    setDrawerOpen(true);
    setComment(record.comment ?? '');
    setFormReady(false);
    setFormFields([]);
    setFormValues({});
    setFormDataId(null);
    setFormDirty(false);
    setFormError(null);
    void loadForm(record);
  };

  /** 关闭抽屉并复位当前任务 */
  const closeDrawer = () => {
    setDrawerOpen(false);
    setActiveTask(null);
  };

  /** 表单值变更：标记未保存（同意时会先保存再审批） */
  const handleFormChange = (next: Record<string, unknown>) => {
    setFormValues(next);
    setFormDirty(true);
  };

  /** 保存审批中编辑的表单（PUT /form/data/{id}），返回是否成功 */
  const saveForm = async (): Promise<boolean> => {
    if (!formDataId || formFields.length === 0) {
      return true;
    }
    setSavingForm(true);
    try {
      const dataJson = JSON.stringify(serializeValues(formFields, formValues));
      await formDataApi.update(formDataId, { dataJson });
      setFormDirty(false);
      message.success('表单已保存');
      return true;
    } catch (e) {
      message.error((e as Error).message);
      return false;
    } finally {
      setSavingForm(false);
    }
  };

  /** 提交审批：同意 / 驳回，comment 随 ApprovalRequest 提交；同意前先保存未提交的表单修改 */
  const handleApproval = async (action: ApprovalAction) => {
    if (!activeTask) {
      return;
    }
    setSubmitting(true);
    try {
      if (action === 'approve' && formDirty) {
        const saved = await saveForm();
        if (!saved) {
          return;
        }
      }
      const payload: ApprovalRequest = { comment: comment.trim() || undefined };
      if (action === 'approve') {
        await workflowApi.taskApprove(activeTask.id, payload);
      } else {
        await workflowApi.taskReject(activeTask.id, payload);
      }
      message.success(action === 'approve' ? '已同意' : '已驳回');
      closeDrawer();
      await loadList();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setSubmitting(false);
    }
  };

  /** 拉取回退候选节点：实例详情 → 定义详情 → 审批节点（USER_TASK / COUNTER_SIGN） */
  const loadRollbackNodes = async (task: ProcessTask) => {
    setRollbackLoading(true);
    try {
      const instance = await workflowApi.instanceDetail(task.instanceId);
      const definition = await workflowApi.definitionDetail(instance.definitionId);
      const parsed = parseDefinitionModel(definition.definitionJson);
      const options = (parsed?.nodes ?? [])
        .filter((node) => node.type === 'USER_TASK' || node.type === 'COUNTER_SIGN')
        .map((node) => ({ value: node.key, label: `${node.name || node.key}（${node.key}）` }));
      setRollbackOptions(options);
    } catch {
      // 定义不可达时清空候选，弹窗仍允许手填节点 key
      setRollbackOptions([]);
    } finally {
      setRollbackLoading(false);
    }
  };

  /** 打开办理弹窗（转办 / 委派 / 回退 / 处理完成） */
  const openTaskAction = (next: TaskAction) => {
    if (!activeTask) {
      return;
    }
    actionForm.resetFields();
    setTaskAction(next);
    if (next === 'rollback') {
      setRollbackOptions([]);
      void loadRollbackNodes(activeTask);
    }
  };

  /** 提交办理弹窗 */
  const submitTaskAction = async () => {
    if (!activeTask || !taskAction) {
      return;
    }
    let values: ActionFormValues;
    try {
      values = await actionForm.validateFields();
    } catch {
      return;
    }
    setActionSubmitting(true);
    try {
      switch (taskAction) {
        case 'transfer':
          await workflowApi.taskTransfer(activeTask.id, {
            targetUser: values.targetUser?.trim() ?? '',
          });
          message.success('已转办');
          break;
        case 'delegate':
          await workflowApi.taskDelegate(activeTask.id, {
            targetUser: values.targetUser?.trim() ?? '',
          });
          message.success('已委派');
          break;
        case 'rollback':
          await workflowApi.taskRollback(activeTask.id, {
            targetNodeKey: values.targetNodeKey?.trim() ?? '',
          });
          message.success('已回退');
          break;
        case 'resolve':
          await workflowApi.taskResolve(activeTask.id, {
            comment: values.comment?.trim() || undefined,
          });
          message.success('已处理完成，任务已交回委派人');
          break;
        case 'addSign': {
          const assignees = parseAssignees(values.assignees);
          if (assignees.length === 0) {
            message.warning('请输入加签人');
            return;
          }
          await workflowApi.taskAddSign(activeTask.id, { assignees });
          message.success('已加签');
          break;
        }
        default:
          break;
      }
      setTaskAction(null);
      closeDrawer();
      await loadList();
    } catch (e) {
      message.error((e as Error).message);
    } finally {
      setActionSubmitting(false);
    }
  };

  const columns: TableColumnsType<ProcessTask> = useMemo(
    () => [
      { title: '节点名称', dataIndex: 'nodeName', width: 160, render: (v: string | null) => v || '-' },
      { title: '审批人', dataIndex: 'assignee', width: 120, render: (v: string | null) => v || '-' },
      {
        title: '所属实例',
        key: 'instance',
        render: (_, record) => {
          const brief = instanceMap[record.instanceId];
          if (!brief) {
            return '-';
          }
          return (
            <Space direction="vertical" size={0}>
              <span>{brief.definitionCode}</span>
              <Typography.Text type="secondary">{brief.title}</Typography.Text>
            </Space>
          );
        },
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: number | undefined) => (
          <Tag color={STATUS_COLOR[value ?? -1] ?? 'default'}>
            {STATUS_TEXT[value ?? -1] ?? '-'}
          </Tag>
        ),
      },
      {
        title: '创建时间',
        dataIndex: 'createTime',
        width: 180,
        render: (v: string | undefined) => v || '-',
      },
      {
        title: '操作',
        key: 'action',
        width: 100,
        render: (_, record) => (
          <Button type="link" size="small" onClick={() => openDrawer(record)}>
            {record.status === 0 ? '审批' : '查看'}
          </Button>
        ),
      },
    ],
    [instanceMap],
  );

  const readOnly = activeTask !== null && activeTask.status !== 0;

  return (
    <div>
      <Card size="small" style={{ marginBottom: 12 }}>
        <Space>
          <span>状态</span>
          <Select
            value={query.status}
            allowClear
            placeholder="全部"
            options={STATUS_OPTIONS}
            style={{ width: 140 }}
            onChange={(value: number | undefined) =>
              setQuery((prev) => ({ ...prev, status: value, current: 1 }))
            }
          />
        </Space>
      </Card>

      <Table<ProcessTask>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={dataSource}
        onChange={(pagination) => {
          const nextSize = pagination.pageSize ?? query.size;
          setQuery((prev) =>
            nextSize !== prev.size
              ? { ...prev, size: nextSize, current: 1 }
              : { ...prev, current: pagination.current ?? 1 },
          );
        }}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          showTotal: (count) => `共 ${count} 条`,
        }}
      />

      <Drawer
        title="任务审批"
        width={720}
        open={drawerOpen}
        onClose={closeDrawer}
        footer={
          readOnly ? null : (
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                gap: 8,
                flexWrap: 'wrap',
              }}
            >
              <Space wrap>
                <Button onClick={() => openTaskAction('transfer')}>转办</Button>
                <Button onClick={() => openTaskAction('delegate')}>委派</Button>
                <Button onClick={() => openTaskAction('rollback')}>回退</Button>
                <Button onClick={() => openTaskAction('addSign')}>加签</Button>
                {activeTask?.delegateFrom ? (
                  <Button type="primary" ghost onClick={() => openTaskAction('resolve')}>
                    处理完成
                  </Button>
                ) : null}
              </Space>
              <Space>
                <Button onClick={() => void handleApproval('reject')} loading={submitting}>
                  驳回
                </Button>
                <Button
                  type="primary"
                  onClick={() => void handleApproval('approve')}
                  loading={submitting}
                >
                  同意
                </Button>
              </Space>
            </div>
          )
        }
      >
        <Descriptions
          column={1}
          bordered
          size="small"
          style={{ marginBottom: 16 }}
          items={[
            { key: 'nodeName', label: '节点名称', children: activeTask?.nodeName || '-' },
            { key: 'assignee', label: '审批人', children: activeTask?.assignee || '-' },
            { key: 'createTime', label: '创建时间', children: activeTask?.createTime || '-' },
            { key: 'comment', label: '已有意见', children: activeTask?.comment || '-' },
            ...(activeTask?.signType === 'COUNTER_SIGN'
              ? [{ key: 'signPolicy', label: '会签策略', children: activeTask?.signPolicy || 'ALL' }]
              : []),
            ...(activeTask?.signType === 'ADD_SIGN'
              ? [{ key: 'signType', label: '任务类型', children: '加签' }]
              : []),
            ...(activeTask?.ownerId && activeTask.ownerId !== activeTask.assignee
              ? [{ key: 'ownerId', label: '任务归属人', children: activeTask.ownerId }]
              : []),
            ...(activeTask?.delegateFrom
              ? [{ key: 'delegateFrom', label: '委派来源', children: activeTask.delegateFrom }]
              : []),
          ]}
        />

        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 8,
            gap: 8,
          }}
        >
          <Typography.Title level={5} style={{ margin: 0 }}>
            表单信息
          </Typography.Title>
          {readOnly || !formReady || !formDataId ? null : (
            <Space>
              {formDirty ? <Tag color="orange">有未保存修改</Tag> : null}
              <Button size="small" loading={savingForm} onClick={() => void saveForm()}>
                保存表单
              </Button>
            </Space>
          )}
        </div>
        <Spin spinning={drawerLoading}>
          {formReady ? (
            <FormRenderer
              fields={formFields}
              value={formValues}
              disabled={readOnly}
              onChange={readOnly ? undefined : handleFormChange}
            />
          ) : drawerLoading ? null : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={formError ?? '无表单数据'}
            />
          )}
        </Spin>

        {readOnly ? null : (
          <>
            <Typography.Title level={5} style={{ marginTop: 16 }}>
              审批意见
            </Typography.Title>
            <Input.TextArea
              rows={3}
              value={comment}
              onChange={(e) => setComment(e.target.value)}
              placeholder="请输入审批意见（可选）"
            />
          </>
        )}
      </Drawer>

      <Modal
        title={taskAction ? ACTION_TITLES[taskAction] : ''}
        open={taskAction !== null}
        onCancel={() => setTaskAction(null)}
        onOk={() => void submitTaskAction()}
        confirmLoading={actionSubmitting}
        okText="确定"
        cancelText="取消"
        forceRender
      >
        <Form<ActionFormValues> form={actionForm} layout="vertical" preserve={false}>
          {taskAction === 'transfer' || taskAction === 'delegate' ? (
            <Form.Item
              name="targetUser"
              label="目标人账号"
              rules={[{ required: true, message: '请输入目标人账号' }]}
            >
              <Input placeholder="工号或用户名" maxLength={64} />
            </Form.Item>
          ) : null}

          {taskAction === 'rollback' ? (
            <Form.Item
              name="targetNodeKey"
              label="回退目标节点"
              rules={[{ required: true, message: '请选择或输入目标节点 key' }]}
              extra={
                rollbackLoading
                  ? '节点列表加载中…'
                  : '可从流程定义中选择审批节点，也可直接输入节点 key'
              }
            >
              <AutoComplete
                options={rollbackOptions}
                optionFilterProp="label"
                placeholder="选择或输入节点 key"
              >
                <Input maxLength={64} />
              </AutoComplete>
            </Form.Item>
          ) : null}

          {taskAction === 'resolve' ? (
            <Form.Item name="comment" label="处理意见">
              <Input.TextArea rows={3} placeholder="可选" maxLength={255} />
            </Form.Item>
          ) : null}

          {taskAction === 'addSign' ? (
            <Form.Item
              name="assignees"
              label="加签人账号"
              rules={[{ required: true, message: '请输入加签人账号' }]}
              extra="多个账号用逗号或换行分隔，单次最多 10 人；加签人全部同意后节点才完成"
            >
              <Input.TextArea rows={3} placeholder="如：zhangsan, lisi" maxLength={700} />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>
    </div>
  );
}
