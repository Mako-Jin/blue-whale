// OA 员工档案页：档案筛选查询、按 userId 幂等保存弹窗与删除
import { useCallback, useEffect, useState } from 'react';
import {
  App,
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  deleteProfile,
  pageProfiles,
  saveProfile,
  type EmployeeProfile,
  type EmployeeProfileSaveCommand,
  type EmploymentStatus,
} from '../../api/hr';
import { hasPermission } from '../../utils/permission';

/** 在职状态文案与颜色 */
const STATUS_META: Record<EmploymentStatus, { text: string; color: string }> = {
  ACTIVE: { text: '在职', color: 'green' },
  PROBATION: { text: '试用', color: 'blue' },
  REGULAR: { text: '正式', color: 'cyan' },
  LEFT: { text: '离职', color: 'default' },
};

/** 表单字段 */
interface FormValues {
  userId: number;
  employeeNo?: string;
  deptId?: number;
  hireDate?: Dayjs | null;
  regularDate?: Dayjs | null;
  birthday?: Dayjs | null;
  employmentStatus?: EmploymentStatus;
  leaveDate?: Dayjs | null;
  cultureVisible?: boolean;
  remark?: string;
}

/** 查询条件 */
interface Query {
  current: number;
  size: number;
  userId?: number;
  employeeNo: string;
  employmentStatus?: EmploymentStatus;
}

/** 日期字符串转 dayjs */
function toDayjs(value?: string): Dayjs | undefined {
  return value ? dayjs(value) : undefined;
}

export default function ProfileCenter() {
  const { message } = App.useApp();
  const [form] = Form.useForm<FormValues>();
  const [data, setData] = useState<EmployeeProfile[]>([]);
  const [total, setTotal] = useState(0);
  const [query, setQuery] = useState<Query>({ current: 1, size: 10, employeeNo: '' });
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<EmployeeProfile | null>(null);

  const canEdit = hasPermission('sys:oa:hr:profile:edit');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageProfiles({
        current: query.current,
        size: query.size,
        userId: query.userId,
        employeeNo: query.employeeNo || undefined,
        employmentStatus: query.employmentStatus,
      });
      setData(page.records);
      setTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '员工档案查询失败');
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
        userId: editing.userId,
        employeeNo: editing.employeeNo,
        deptId: editing.deptId,
        hireDate: toDayjs(editing.hireDate),
        regularDate: toDayjs(editing.regularDate),
        birthday: toDayjs(editing.birthday),
        employmentStatus: editing.employmentStatus,
        leaveDate: toDayjs(editing.leaveDate),
        cultureVisible: editing.cultureVisible,
        remark: editing.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ employmentStatus: 'ACTIVE', cultureVisible: true });
    }
  }, [modalOpen, editing, form]);

  const openCreate = () => {
    setEditing(null);
    setModalOpen(true);
  };

  const openEdit = (record: EmployeeProfile) => {
    setEditing(record);
    setModalOpen(true);
  };

  const handleOk = async () => {
    const values = await form.validateFields();
    const payload: EmployeeProfileSaveCommand = {
      userId: values.userId,
      employeeNo: values.employeeNo?.trim() || undefined,
      deptId: values.deptId ?? undefined,
      hireDate: values.hireDate?.format('YYYY-MM-DD'),
      regularDate: values.regularDate?.format('YYYY-MM-DD'),
      birthday: values.birthday?.format('YYYY-MM-DD'),
      employmentStatus: values.employmentStatus,
      leaveDate: values.leaveDate?.format('YYYY-MM-DD'),
      cultureVisible: values.cultureVisible,
      remark: values.remark?.trim() || undefined,
    };
    try {
      await saveProfile(payload);
      message.success('保存成功');
      setModalOpen(false);
      setEditing(null);
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    }
  };

  const remove = async (record: EmployeeProfile) => {
    try {
      await deleteProfile(record.userId);
      message.success('已删除');
      await load();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<EmployeeProfile>['columns'] = [
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 140 },
    {
      title: '工号',
      dataIndex: 'employeeNo',
      key: 'employeeNo',
      width: 140,
      render: (value?: string) => value || '-',
    },
    { title: '部门ID', dataIndex: 'deptId', key: 'deptId', width: 120, render: (value?: number) => value || '-' },
    { title: '入职', dataIndex: 'hireDate', key: 'hireDate', width: 120, render: (value?: string) => value || '-' },
    {
      title: '转正',
      dataIndex: 'regularDate',
      key: 'regularDate',
      width: 120,
      render: (value?: string) => value || '-',
    },
    { title: '生日', dataIndex: 'birthday', key: 'birthday', width: 120, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'employmentStatus',
      key: 'employmentStatus',
      width: 90,
      render: (value?: EmploymentStatus) => {
        if (!value) {
          return '-';
        }
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '文化墙展示',
      dataIndex: 'cultureVisible',
      key: 'cultureVisible',
      width: 110,
      render: (value?: boolean) => (
        <Tag color={value ? 'green' : 'default'}>{value ? '是' : '否'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => {
        if (!canEdit) {
          return null;
        }
        return (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => openEdit(record)}>
              编辑
            </Button>
            <Popconfirm title="确认删除该员工档案？" onConfirm={() => void remove(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <InputNumber
          placeholder="用户ID"
          style={{ width: 160 }}
          min={1}
          precision={0}
          value={query.userId}
          onChange={(value) =>
            setQuery((prev) => ({ ...prev, current: 1, userId: value ?? undefined }))
          }
        />
        <Input.Search
          allowClear
          placeholder="搜索工号"
          style={{ width: 200 }}
          onSearch={(value) =>
            setQuery((prev) => ({ ...prev, current: 1, employeeNo: value.trim() }))
          }
        />
        <Select
          allowClear
          placeholder="在职状态"
          style={{ width: 140 }}
          value={query.employmentStatus}
          options={Object.entries(STATUS_META).map(([value, meta]) => ({
            value,
            label: meta.text,
          }))}
          onChange={(value) =>
            setQuery((prev) => ({
              ...prev,
              current: 1,
              employmentStatus: value as EmploymentStatus | undefined,
            }))
          }
        />
        {canEdit ? (
          <Button type="primary" onClick={openCreate}>
            新增员工档案
          </Button>
        ) : null}
        <Button onClick={() => void load()}>刷新</Button>
      </Space>
      <Table<EmployeeProfile>
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
        title={editing ? '编辑员工档案' : '新增员工档案'}
        onOk={() => void handleOk()}
        onCancel={() => {
          setModalOpen(false);
          setEditing(null);
        }}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item
            name="userId"
            label="用户ID"
            rules={[{ required: true, message: '请输入用户ID' }]}
          >
            <InputNumber style={{ width: '100%' }} min={1} precision={0} disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item name="employeeNo" label="工号">
            <Input placeholder="选填" maxLength={64} />
          </Form.Item>
          <Form.Item name="deptId" label="部门ID">
            <InputNumber style={{ width: '100%' }} min={1} precision={0} placeholder="选填" />
          </Form.Item>
          <Form.Item name="hireDate" label="入职日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="regularDate" label="转正日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="birthday" label="生日">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="employmentStatus" label="在职状态">
            <Select
              options={Object.entries(STATUS_META).map(([value, meta]) => ({
                value,
                label: meta.text,
              }))}
            />
          </Form.Item>
          <Form.Item name="leaveDate" label="离职日期">
            <DatePicker style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="cultureVisible" label="文化墙展示" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </Space>
  );
}
