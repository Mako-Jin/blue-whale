// 我的待办 + 审批页（F4-a~F4-d）：分页待办列表 + 审批抽屉（表单只读回显 + 意见 + 同意/驳回）
// 契约层：../../api/workflow（待办/审批/实例）、../../api/form（表单定义/数据），本文件不改动契约层。
// 冻结口径：审批表单只读回显（<FormRenderer disabled>），不做可编辑写回；雪花 ID 全链路只用 string。

import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  App,
  Button,
  Card,
  Descriptions,
  Drawer,
  Empty,
  Input,
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
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);

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
    setFormError(null);
    void loadForm(record);
  };

  /** 关闭抽屉并复位当前任务 */
  const closeDrawer = () => {
    setDrawerOpen(false);
    setActiveTask(null);
  };

  /** 提交审批：同意 / 驳回，comment 随 ApprovalRequest 提交 */
  const handleApproval = async (action: ApprovalAction) => {
    if (!activeTask) {
      return;
    }
    setSubmitting(true);
    try {
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
            <div style={{ textAlign: 'right' }}>
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
          ]}
        />

        <Typography.Title level={5}>表单信息</Typography.Title>
        <Spin spinning={drawerLoading}>
          {formReady ? (
            <FormRenderer fields={formFields} value={formValues} disabled />
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
    </div>
  );
}
