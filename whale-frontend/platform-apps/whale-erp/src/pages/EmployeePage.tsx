// ERP 人力资源页：组织单元、员工档案、入转调离异动
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  CHANGE_TYPE_LABEL,
  HR_STATUS_LABEL,
  createEmployee,
  createEmploymentChange,
  createHrOrg,
  effectiveEmploymentChange,
  hrStatusColor,
  listHrOrgs,
  pageEmployees,
  pageEmploymentChanges,
  updateEmployee,
  updateHrOrg,
  type EmployeeView,
  type EmploymentChangeView,
  type OrgUnitView,
} from '../api/hr';
import { hasPermission } from '../utils/permission';
import { money } from '../utils/scm';

/** 组织表单值 */
interface OrgFormValues {
  code: string;
  name: string;
  parentCode?: string;
  manager?: string;
  remark?: string;
}

/** 员工建档表单值 */
interface EmployeeFormValues {
  employeeNo: string;
  name: string;
  gender?: string;
  phone?: string;
  email?: string;
  orgCode: string;
  position?: string;
  hireDate: Dayjs;
  baseSalary?: number;
  allowance?: number;
  socialBase?: number;
  userId?: number;
  remark?: string;
}

/** 员工编辑表单值 */
interface EmployeeEditFormValues {
  name: string;
  gender?: string;
  phone?: string;
  email?: string;
  position?: string;
  baseSalary?: number;
  allowance?: number;
  socialBase?: number;
  userId?: number;
  remark?: string;
}

/** 异动表单值 */
interface ChangeFormValues {
  employeeNo: string;
  changeType: string;
  effectiveDate: Dayjs;
  toOrgCode?: string;
  toPosition?: string;
  toSalary?: number;
  remark?: string;
}

/** 组织表单弹窗 */
function OrgModal({
  open,
  initial,
  orgs,
  onCancel,
  onSaved,
}: {
  open: boolean;
  initial: OrgUnitView | null;
  orgs: OrgUnitView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<OrgFormValues>();

  useEffect(() => {
    if (!open) return;
    if (initial) {
      form.setFieldsValue({
        code: initial.code,
        name: initial.name,
        parentCode: initial.parentCode,
        manager: initial.manager,
        remark: initial.remark,
      });
    } else {
      form.resetFields();
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    if (initial) {
      await updateHrOrg(initial.code, values);
      message.success('组织已更新');
    } else {
      await createHrOrg(values);
      message.success('组织已创建');
    }
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑组织' : '新增组织'}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Form.Item
          name="code"
          label="组织编码"
          rules={[{ required: true, message: '请输入组织编码' }]}
        >
          <Input disabled={initial !== null} maxLength={32} />
        </Form.Item>
        <Form.Item
          name="name"
          label="组织名称"
          rules={[{ required: true, message: '请输入组织名称' }]}
        >
          <Input maxLength={64} />
        </Form.Item>
        <Form.Item name="parentCode" label="上级组织">
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            options={orgs
              .filter((org) => org.code !== initial?.code)
              .map((org) => ({ value: org.code, label: `${org.code} ${org.name}` }))}
          />
        </Form.Item>
        <Form.Item name="manager" label="负责人">
          <Input maxLength={32} />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 员工建档弹窗 */
function EmployeeModal({
  open,
  orgs,
  onCancel,
  onSaved,
}: {
  open: boolean;
  orgs: OrgUnitView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<EmployeeFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ hireDate: dayjs(), baseSalary: 0, allowance: 0, socialBase: 0 });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createEmployee({
      employeeNo: values.employeeNo,
      name: values.name,
      gender: values.gender,
      phone: values.phone,
      email: values.email,
      orgCode: values.orgCode,
      position: values.position,
      hireDate: values.hireDate.format('YYYY-MM-DD'),
      baseSalary: Number(values.baseSalary ?? 0),
      allowance: Number(values.allowance ?? 0),
      socialBase: Number(values.socialBase ?? 0),
      userId: values.userId,
      remark: values.remark,
    });
    message.success('员工档案已创建');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title="新增员工档案"
      width={720}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item
            name="employeeNo"
            label="员工编号"
            rules={[{ required: true, message: '请输入员工编号' }]}
          >
            <Input maxLength={32} style={{ width: 200 }} />
          </Form.Item>
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input maxLength={32} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="gender" label="性别">
            <Select
              allowClear
              style={{ width: 100 }}
              options={[
                { value: 'MALE', label: '男' },
                { value: 'FEMALE', label: '女' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="orgCode"
            label="所属组织"
            rules={[{ required: true, message: '请选择组织' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              style={{ width: 220 }}
              options={orgs
                .filter((org) => org.status === 'ENABLED')
                .map((org) => ({ value: org.code, label: `${org.code} ${org.name}` }))}
            />
          </Form.Item>
          <Form.Item name="position" label="岗位">
            <Input maxLength={32} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item
            name="hireDate"
            label="入职日期"
            rules={[{ required: true, message: '请选择入职日期' }]}
          >
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
          <Form.Item name="baseSalary" label="月基本工资">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="allowance" label="固定津贴">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="socialBase" label="社保基数">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="userId" label="平台账号 ID">
            <InputNumber min={1} precision={0} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input maxLength={32} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input maxLength={64} style={{ width: 220 }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 员工编辑弹窗 */
function EmployeeEditModal({
  open,
  initial,
  onCancel,
  onSaved,
}: {
  open: boolean;
  initial: EmployeeView | null;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<EmployeeEditFormValues>();

  useEffect(() => {
    if (!open || !initial) return;
    form.setFieldsValue({
      name: initial.name,
      gender: initial.gender,
      phone: initial.phone,
      email: initial.email,
      position: initial.position,
      baseSalary: initial.baseSalary,
      allowance: initial.allowance,
      socialBase: initial.socialBase,
      userId: initial.userId,
      remark: initial.remark,
    });
  }, [open, initial, form]);

  const handleOk = async () => {
    if (!initial) return;
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await updateEmployee(initial.employeeNo, {
      ...values,
      baseSalary: Number(values.baseSalary ?? 0),
      allowance: Number(values.allowance ?? 0),
      socialBase: Number(values.socialBase ?? 0),
    });
    message.success('员工档案已更新');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? `编辑员工 ${initial.name}` : '编辑员工'}
      width={640}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]}>
            <Input maxLength={32} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="gender" label="性别">
            <Select
              allowClear
              style={{ width: 100 }}
              options={[
                { value: 'MALE', label: '男' },
                { value: 'FEMALE', label: '女' },
              ]}
            />
          </Form.Item>
          <Form.Item name="position" label="岗位">
            <Input maxLength={32} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="baseSalary" label="月基本工资">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="allowance" label="固定津贴">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="socialBase" label="社保基数">
            <InputNumber min={0} precision={2} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="userId" label="平台账号 ID">
            <InputNumber min={1} precision={0} style={{ width: 140 }} />
          </Form.Item>
          <Form.Item name="phone" label="手机号">
            <Input maxLength={32} style={{ width: 180 }} />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input maxLength={64} style={{ width: 220 }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 异动弹窗 */
function ChangeModal({
  open,
  orgs,
  employees,
  onCancel,
  onSaved,
}: {
  open: boolean;
  orgs: OrgUnitView[];
  employees: EmployeeView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<ChangeFormValues>();
  const changeType = Form.useWatch('changeType', form);

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ changeType: 'TRANSFER', effectiveDate: dayjs() });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createEmploymentChange({
      employeeNo: values.employeeNo,
      changeType: values.changeType,
      effectiveDate: values.effectiveDate.format('YYYY-MM-DD'),
      toOrgCode: values.toOrgCode,
      toPosition: values.toPosition,
      toSalary: values.toSalary === undefined ? undefined : Number(values.toSalary),
      remark: values.remark,
    });
    message.success('异动单已创建（草稿）');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title="新增员工异动"
      width={640}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item
            name="employeeNo"
            label="员工"
            rules={[{ required: true, message: '请选择员工' }]}
          >
            <Select
              showSearch
              optionFilterProp="label"
              style={{ width: 240 }}
              options={employees.map((employee) => ({
                value: employee.employeeNo,
                label: `${employee.employeeNo} ${employee.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item name="changeType" label="异动类型" rules={[{ required: true }]}>
            <Select
              style={{ width: 140 }}
              options={Object.entries(CHANGE_TYPE_LABEL).map(([value, label]) => ({
                value,
                label,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="effectiveDate"
            label="生效日期"
            rules={[{ required: true, message: '请选择生效日期' }]}
          >
            <DatePicker format="YYYY-MM-DD" />
          </Form.Item>
        </Space>
        {changeType !== 'RESIGN' ? (
          <Space size="middle" wrap>
            <Form.Item name="toOrgCode" label="目标组织">
              <Select
                allowClear
                showSearch
                optionFilterProp="label"
                style={{ width: 240 }}
                options={orgs
                  .filter((org) => org.status === 'ENABLED')
                  .map((org) => ({ value: org.code, label: `${org.code} ${org.name}` }))}
              />
            </Form.Item>
            <Form.Item name="toPosition" label="目标岗位">
              <Input maxLength={32} style={{ width: 180 }} />
            </Form.Item>
            <Form.Item name="toSalary" label="目标月薪">
              <InputNumber min={0} precision={2} style={{ width: 140 }} />
            </Form.Item>
          </Space>
        ) : null}
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 人力资源页 */
export default function EmployeePage() {
  const [orgs, setOrgs] = useState<OrgUnitView[]>([]);
  const [employees, setEmployees] = useState<EmployeeView[]>([]);
  const [changes, setChanges] = useState<EmploymentChangeView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [version, setVersion] = useState(0);
  const [orgModalOpen, setOrgModalOpen] = useState(false);
  const [editingOrg, setEditingOrg] = useState<OrgUnitView | null>(null);
  const [employeeModalOpen, setEmployeeModalOpen] = useState(false);
  const [editingEmployee, setEditingEmployee] = useState<EmployeeView | null>(null);
  const [changeModalOpen, setChangeModalOpen] = useState(false);

  const loadOrgs = useCallback(async () => {
    setOrgs(await listHrOrgs({ size: 200 }));
  }, []);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [employeePage, changePage] = await Promise.all([
        pageEmployees({ size: 50, keyword }),
        pageEmploymentChanges({ size: 50, keyword }),
      ]);
      setEmployees(employeePage.records);
      setChanges(changePage.records);
    } finally {
      setLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    void loadOrgs();
    void load();
  }, [loadOrgs, load, version]);

  const handleEffective = async (id: number) => {
    await effectiveEmploymentChange(id);
    message.success('异动已生效并回写员工档案');
    setVersion((value) => value + 1);
  };

  const orgColumns: TableProps<OrgUnitView>['columns'] = [
    { title: '组织编码', dataIndex: 'code', key: 'code', width: 140 },
    { title: '组织名称', dataIndex: 'name', key: 'name' },
    { title: '上级组织', dataIndex: 'parentCode', key: 'parentCode', width: 140 },
    { title: '负责人', dataIndex: 'manager', key: 'manager', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => (
        <Tag color={hrStatusColor(String(value))}>{HR_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:erp:hr:org:update') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditingOrg(record);
              setOrgModalOpen(true);
            }}
          >
            编辑
          </Button>
        ) : null,
    },
  ];

  const employeeColumns: TableProps<EmployeeView>['columns'] = [
    { title: '员工编号', dataIndex: 'employeeNo', key: 'employeeNo', width: 130 },
    { title: '姓名', dataIndex: 'name', key: 'name', width: 110 },
    { title: '组织', dataIndex: 'orgCode', key: 'orgCode', width: 130 },
    { title: '岗位', dataIndex: 'position', key: 'position', width: 120 },
    { title: '入职日期', dataIndex: 'hireDate', key: 'hireDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => (
        <Tag color={hrStatusColor(String(value))}>{HR_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    { title: '基本工资', dataIndex: 'baseSalary', key: 'baseSalary', width: 120, render: money },
    { title: '津贴', dataIndex: 'allowance', key: 'allowance', width: 110, render: money },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:erp:hr:employee:update') ? (
          <Button
            type="link"
            size="small"
            onClick={() => {
              setEditingEmployee(record);
              setEmployeeModalOpen(true);
            }}
          >
            编辑
          </Button>
        ) : null,
    },
  ];

  const changeColumns: TableProps<EmploymentChangeView>['columns'] = [
    { title: '异动单号', dataIndex: 'changeNo', key: 'changeNo', width: 170 },
    { title: '员工编号', dataIndex: 'employeeNo', key: 'employeeNo', width: 130 },
    {
      title: '类型',
      dataIndex: 'changeType',
      key: 'changeType',
      width: 100,
      render: (value) => CHANGE_TYPE_LABEL[String(value)] ?? value,
    },
    { title: '生效日期', dataIndex: 'effectiveDate', key: 'effectiveDate', width: 120 },
    { title: '原组织', dataIndex: 'fromOrgCode', key: 'fromOrgCode', width: 120 },
    { title: '目标组织', dataIndex: 'toOrgCode', key: 'toOrgCode', width: 120 },
    { title: '原月薪', dataIndex: 'fromSalary', key: 'fromSalary', width: 110, render: money },
    { title: '目标月薪', dataIndex: 'toSalary', key: 'toSalary', width: 110, render: money },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value) => (
        <Tag color={hrStatusColor(String(value))}>{HR_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 110,
      render: (_, record) =>
        record.status === 'DRAFT' && hasPermission('sys:erp:hr:employee:change') ? (
          <Popconfirm title="确认使该异动单生效？" onConfirm={() => handleEffective(record.id)}>
            <Button type="link" size="small">
              生效
            </Button>
          </Popconfirm>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Input.Search
        allowClear
        placeholder="员工编号 / 姓名 / 异动单号"
        style={{ width: 280 }}
        onSearch={(value) => setKeyword(value)}
      />
      <Tabs
        items={[
          {
            key: 'org',
            label: '组织单元',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {hasPermission('sys:erp:hr:org:add') ? (
                  <Button
                    type="primary"
                    onClick={() => {
                      setEditingOrg(null);
                      setOrgModalOpen(true);
                    }}
                  >
                    新增组织
                  </Button>
                ) : null}
                <Table<OrgUnitView>
                  rowKey="id"
                  size="small"
                  loading={loading}
                  columns={orgColumns}
                  dataSource={orgs}
                  pagination={false}
                />
              </Space>
            ),
          },
          {
            key: 'employee',
            label: '员工档案',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {hasPermission('sys:erp:hr:employee:add') ? (
                  <Button type="primary" onClick={() => setEmployeeModalOpen(true)}>
                    新增员工
                  </Button>
                ) : null}
                <Table<EmployeeView>
                  rowKey="id"
                  size="small"
                  loading={loading}
                  columns={employeeColumns}
                  dataSource={employees}
                  pagination={false}
                />
              </Space>
            ),
          },
          {
            key: 'change',
            label: '入转调离',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                {hasPermission('sys:erp:hr:employee:change') ? (
                  <Button type="primary" onClick={() => setChangeModalOpen(true)}>
                    新增异动
                  </Button>
                ) : null}
                <Table<EmploymentChangeView>
                  rowKey="id"
                  size="small"
                  loading={loading}
                  columns={changeColumns}
                  dataSource={changes}
                  pagination={false}
                />
              </Space>
            ),
          },
        ]}
      />
      <OrgModal
        open={orgModalOpen}
        initial={editingOrg}
        orgs={orgs}
        onCancel={() => setOrgModalOpen(false)}
        onSaved={() => {
          setOrgModalOpen(false);
          setVersion((value) => value + 1);
        }}
      />
      <EmployeeModal
        open={employeeModalOpen}
        orgs={orgs}
        onCancel={() => setEmployeeModalOpen(false)}
        onSaved={() => {
          setEmployeeModalOpen(false);
          setVersion((value) => value + 1);
        }}
      />
      <EmployeeEditModal
        open={editingEmployee !== null}
        initial={editingEmployee}
        onCancel={() => setEditingEmployee(null)}
        onSaved={() => {
          setEditingEmployee(null);
          setVersion((value) => value + 1);
        }}
      />
      <ChangeModal
        open={changeModalOpen}
        orgs={orgs}
        employees={employees}
        onCancel={() => setChangeModalOpen(false)}
        onSaved={() => {
          setChangeModalOpen(false);
          setVersion((value) => value + 1);
        }}
      />
    </Space>
  );
}
