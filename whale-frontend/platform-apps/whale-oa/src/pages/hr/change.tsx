// OA 人员异动页：入转调离返聘申请、审批提交、执行与离职清单
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  createChange,
  doneChangeTask,
  executeChange,
  getChange,
  pageChanges,
  submitChange,
  updateChange,
  type EmployeeChangeDetail,
  type EmployeeChangeListItem,
  type EmployeeChangeSaveCommand,
  type EmployeeChangeStatus,
  type EmployeeChangeTask,
  type EmployeeChangeType,
  type EmploymentStatus,
} from '../../api/hr';
import { hasPermission } from '../../utils/permission';

/** 异动类型文案 */
const TYPE_META: Record<EmployeeChangeType, string> = {
  ONBOARD: '入职',
  REGULAR: '转正',
  TRANSFER: '调岗',
  LEAVE: '离职',
  REHIRE: '返聘',
};

/** 异动状态文案与颜色 */
const STATUS_META: Record<EmployeeChangeStatus, { text: string; color: string }> = {
  DRAFT: { text: '草稿', color: 'default' },
  SUBMITTED: { text: '审批中', color: 'processing' },
  APPROVED: { text: '待执行', color: 'blue' },
  REJECTED: { text: '已驳回', color: 'red' },
  EXECUTED: { text: '已执行', color: 'green' },
  EXEC_FAILED: { text: '执行失败', color: 'orange' },
};

/** 在职状态文案 */
const EMPLOYMENT_META: Record<EmploymentStatus, string> = {
  ACTIVE: '在职',
  PROBATION: '试用',
  REGULAR: '正式',
  LEFT: '离职',
};

/** 表单字段 */
interface FormValues {
  changeType: EmployeeChangeType;
  userId?: number;
  employeeName: string;
  username?: string;
  fromDeptId?: number;
  toDeptId?: number;
  fromStatus?: EmploymentStatus;
  toStatus?: EmploymentStatus;
  effectiveDate: Dayjs;
  reason?: string;
  remark?: string;
}

/** 查询条件 */
interface Query {
  current: number;
  size: number;
  keyword: string;
  changeType?: EmployeeChangeType;
  status?: EmployeeChangeStatus;
}

/** 日期字符串转 dayjs */
function toDayjs(value?: string): Dayjs | undefined {
  return value ? dayjs(value) : undefined;
}

export default function EmployeeChangePage() {
  const { message, modal } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [data, setData] = useState<EmployeeChangeListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<Query>({ current: 1, size: 10, keyword: '' });
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<EmployeeChangeDetail | null>(null);
  const [detail, setDetail] = useState<EmployeeChangeDetail | null>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const canManage = hasPermission('sys:oa:hr:change:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageChanges({
        current: query.current,
        size: query.size,
        keyword: query.keyword || undefined,
        changeType: query.changeType,
        status: query.status,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '人员异动查询失败');
      setData([]);
      setTotal(0);
    } finally {
      setLoading(false);
    }
  }, [query, message]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    if (!modalOpen) {
      return;
    }
    if (editing) {
      form.setFieldsValue({
        changeType: editing.changeType,
        userId: editing.userId,
        employeeName: editing.employeeName,
        username: editing.username,
        fromDeptId: editing.fromDeptId,
        toDeptId: editing.toDeptId,
        fromStatus: editing.fromStatus,
        toStatus: editing.toStatus,
        effectiveDate: toDayjs(editing.effectiveDate) ?? dayjs(),
        reason: editing.reason,
        remark: editing.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ changeType: 'TRANSFER', effectiveDate: dayjs() });
    }
  }, [modalOpen, editing, form]);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = async (record: EmployeeChangeListItem) => {
    try {
      const change = await getChange(record.id);
      setEditing(change);
      setModalOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '详情加载失败');
    }
  };

  const openDetail = async (record: EmployeeChangeListItem) => {
    try {
      setDetail(await getChange(record.id));
      setDrawerOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '详情加载失败');
    }
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: EmployeeChangeSaveCommand = {
      changeType: values.changeType,
      userId: values.userId ?? undefined,
      employeeName: values.employeeName.trim(),
      username: values.username?.trim() || undefined,
      fromDeptId: values.fromDeptId ?? undefined,
      toDeptId: values.toDeptId ?? undefined,
      fromStatus: values.fromStatus,
      toStatus: values.toStatus,
      effectiveDate: values.effectiveDate.format('YYYY-MM-DD'),
      reason: values.reason?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
    };
    try {
      if (editing) {
        await updateChange(editing.id, payload);
      } else {
        await createChange(payload);
      }
      message.success('保存成功');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  const submit = async (record: EmployeeChangeListItem) => {
    try {
      await submitChange(record.id);
      message.success('已提交审批');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '提交失败');
    }
  };

  const execute = async (record: EmployeeChangeListItem) => {
    try {
      const result = await executeChange(record.id);
      if (result.status === 'EXEC_FAILED') {
        message.error(result.message || '执行失败');
      } else {
        message.success(result.message || '执行成功');
      }
      if (result.temporaryPassword) {
        modal.info({
          title: '入职账号临时口令',
          content: `临时口令：${result.temporaryPassword}，仅本次显示，请提示员工首次登录后修改。`,
        });
      }
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '执行失败');
    }
  };

  const finishTask = async (changeId: number, task: EmployeeChangeTask) => {
    try {
      await doneChangeTask(changeId, task.id);
      message.success('任务已完成');
      setDetail(await getChange(changeId));
    } catch (error) {
      message.error(error instanceof Error ? error.message : '任务完成失败');
    }
  };

  const columns: TableProps<EmployeeChangeListItem>['columns'] = [
    { title: '单号', dataIndex: 'changeNo', key: 'changeNo', width: 170 },
    {
      title: '类型',
      dataIndex: 'changeType',
      key: 'changeType',
      width: 90,
      render: (value: EmployeeChangeType) => TYPE_META[value] ?? value,
    },
    { title: '员工', dataIndex: 'employeeName', key: 'employeeName', width: 120 },
    {
      title: '生效日期',
      dataIndex: 'effectiveDate',
      key: 'effectiveDate',
      width: 120,
      render: (value?: string) => value || '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: EmployeeChangeStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '申请人',
      dataIndex: 'applicantName',
      key: 'applicantName',
      width: 110,
      render: (value?: string) => value || '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 240,
      render: (_value, record) => (
        <Space size={4} wrap>
          <Button type="link" size="small" onClick={() => void openDetail(record)}>
            详情
          </Button>
          {canManage ? (
            <>
              {record.status === 'DRAFT' || record.status === 'REJECTED' || record.status === 'EXEC_FAILED' ? (
                <Button type="link" size="small" onClick={() => void openEdit(record)}>
                  编辑
                </Button>
              ) : null}
              {record.status === 'DRAFT' || record.status === 'REJECTED' ? (
                <Button type="link" size="small" onClick={() => void submit(record)}>
                  提交审批
                </Button>
              ) : null}
              {record.status === 'APPROVED' || record.status === 'EXEC_FAILED' ? (
                <Button type="link" size="small" onClick={() => void execute(record)}>
                  执行
                </Button>
              ) : null}
            </>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="搜索单号/员工/账号"
          style={{ width: 220 }}
          onSearch={(value) => setQuery((prev) => ({ ...prev, current: 1, keyword: value.trim() }))}
        />
        <Select
          allowClear
          placeholder="异动类型"
          style={{ width: 130 }}
          value={query.changeType}
          options={Object.entries(TYPE_META).map(([value, label]) => ({ value, label }))}
          onChange={(value) =>
            setQuery((prev) => ({ ...prev, current: 1, changeType: value as EmployeeChangeType | undefined }))
          }
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          value={query.status}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({ value, label: meta.text }))}
          onChange={(value) =>
            setQuery((prev) => ({ ...prev, current: 1, status: value as EmployeeChangeStatus | undefined }))
          }
        />
        {canManage ? (
          <Button type="primary" onClick={openCreate}>
            新建异动
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<EmployeeChangeListItem>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          onChange: (page, pageSize) =>
            setQuery((prev) => ({ ...prev, current: page, size: pageSize })),
        }}
      />
      <Modal
        open={modalOpen}
        title={editing ? '编辑人员异动' : '新建人员异动'}
        width={640}
        onOk={() => void handleOk()}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="changeType" label="异动类型" rules={[{ required: true, message: '请选择异动类型' }]}>
            <Select
              options={Object.entries(TYPE_META).map(([value, label]) => ({ value, label }))}
              disabled={Boolean(editing)}
            />
          </Form.Item>
          <Form.Item name="employeeName" label="员工姓名" rules={[{ required: true, message: '请输入员工姓名' }]}>
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="userId" label="员工用户ID">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="入职建号时选填" />
          </Form.Item>
          <Form.Item name="username" label="登录账号">
            <Input maxLength={64} placeholder="入职未建号时填写" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="fromDeptId" label="原部门ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
            </Form.Item>
            <Form.Item name="toDeptId" label="目标部门ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="调岗必填" />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="fromStatus" label="原在职状态" style={{ flex: 1 }}>
              <Select
                allowClear
                options={Object.entries(EMPLOYMENT_META).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
            <Form.Item name="toStatus" label="目标在职状态" style={{ flex: 1 }}>
              <Select
                allowClear
                placeholder="留空按类型默认"
                options={Object.entries(EMPLOYMENT_META).map(([value, label]) => ({ value, label }))}
              />
            </Form.Item>
          </Space>
          <Form.Item name="effectiveDate" label="生效日期" rules={[{ required: true, message: '请选择生效日期' }]}>
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="reason" label="异动原因">
            <Input.TextArea rows={2} maxLength={512} showCount placeholder="选填" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
      <Drawer
        open={drawerOpen}
        width={640}
        title={detail ? `${detail.changeNo} ${detail.changeTypeLabel ?? ''}` : '异动详情'}
        onClose={() => setDrawerOpen(false)}
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions column={2} size="small" bordered>
              <Descriptions.Item label="员工">{detail.employeeName}</Descriptions.Item>
              <Descriptions.Item label="状态">{STATUS_META[detail.status]?.text ?? detail.status}</Descriptions.Item>
              <Descriptions.Item label="生效日期">{detail.effectiveDate || '-'}</Descriptions.Item>
              <Descriptions.Item label="申请人">{detail.applicantName || '-'}</Descriptions.Item>
              <Descriptions.Item label="原部门">{detail.fromDeptId ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="目标部门">{detail.toDeptId ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="原状态">{detail.fromStatus ? EMPLOYMENT_META[detail.fromStatus] : '-'}</Descriptions.Item>
              <Descriptions.Item label="目标状态">{detail.toStatus ? EMPLOYMENT_META[detail.toStatus] : '-'}</Descriptions.Item>
              <Descriptions.Item label="原因" span={2}>{detail.reason || '-'}</Descriptions.Item>
              <Descriptions.Item label="执行结果" span={2}>{detail.executeResult || '-'}</Descriptions.Item>
            </Descriptions>
            {detail.tasks && detail.tasks.length > 0 ? (
              <Table<EmployeeChangeTask>
                rowKey="id"
                size="small"
                pagination={false}
                dataSource={detail.tasks}
                columns={[
                  { title: '任务', dataIndex: 'taskName', key: 'taskName' },
                  {
                    title: '状态',
                    dataIndex: 'status',
                    key: 'status',
                    width: 90,
                    render: (value?: string) => (
                      <Tag color={value === 'DONE' ? 'green' : 'default'}>
                        {value === 'DONE' ? '已完成' : '待办'}
                      </Tag>
                    ),
                  },
                  {
                    title: '操作',
                    key: 'action',
                    width: 100,
                    render: (_value, task) =>
                      canManage && task.status === 'PENDING' ? (
                        <Button type="link" size="small" onClick={() => void finishTask(detail.id, task)}>
                          完成
                        </Button>
                      ) : null,
                  },
                ]}
              />
            ) : null}
          </Space>
        ) : null}
      </Drawer>
    </Space>
  );
}
