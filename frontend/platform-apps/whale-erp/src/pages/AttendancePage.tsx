// ERP 考勤汇总页：按期间登记员工出勤、请假、加班与缺勤
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  pageAttendances,
  pageEmployees,
  saveAttendance,
  type AttendanceView,
  type EmployeeView,
} from '../api/hr';
import { hasPermission } from '../utils/permission';
import { quantity } from '../utils/scm';

/** 考勤表单值 */
interface AttendanceFormValues {
  employeeNo: string;
  month: Dayjs;
  workDays?: number;
  actualDays?: number;
  leaveHours?: number;
  overtimeHours?: number;
  absentDays?: number;
  remark?: string;
}

/** 考勤登记弹窗 */
function AttendanceModal({
  open,
  initial,
  employees,
  onCancel,
  onSaved,
}: {
  open: boolean;
  initial: AttendanceView | null;
  employees: EmployeeView[];
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<AttendanceFormValues>();

  useEffect(() => {
    if (!open) return;
    if (initial) {
      form.setFieldsValue({
        employeeNo: initial.employeeNo,
        month: dayjs(initial.period, 'YYYY-MM'),
        workDays: initial.workDays,
        actualDays: initial.actualDays,
        leaveHours: initial.leaveHours,
        overtimeHours: initial.overtimeHours,
        absentDays: initial.absentDays,
        remark: initial.remark,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        month: dayjs(),
        workDays: 21.75,
        actualDays: 21.75,
        leaveHours: 0,
        overtimeHours: 0,
        absentDays: 0,
      });
    }
  }, [open, initial, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await saveAttendance({
      employeeNo: values.employeeNo,
      period: values.month.format('YYYY-MM'),
      workDays: Number(values.workDays ?? 0),
      actualDays: Number(values.actualDays ?? 0),
      leaveHours: Number(values.leaveHours ?? 0),
      overtimeHours: Number(values.overtimeHours ?? 0),
      absentDays: Number(values.absentDays ?? 0),
      remark: values.remark,
    });
    message.success('考勤汇总已保存');
    onSaved();
  };

  return (
    <Modal
      open={open}
      title={initial ? '编辑考勤汇总' : '登记考勤汇总'}
      width={640}
      onOk={handleOk}
      onCancel={onCancel}
      destroyOnHidden
    >
      <Form form={form} layout="vertical">
        <Space size="middle" wrap>
          <Form.Item name="employeeNo" label="员工" rules={[{ required: true, message: '请选择员工' }]}>
            <Select
              showSearch
              allowClear
              disabled={initial !== null}
              optionFilterProp="label"
              placeholder="选择员工"
              style={{ width: 220 }}
              options={employees.map((employee) => ({
                value: employee.employeeNo,
                label: `${employee.employeeNo} ${employee.name}`,
              }))}
            />
          </Form.Item>
          <Form.Item
            name="month"
            label="考勤期间"
            rules={[{ required: true, message: '请选择考勤期间' }]}
          >
            <DatePicker picker="month" format="YYYY-MM" disabled={initial !== null} />
          </Form.Item>
          <Form.Item name="workDays" label="应出勤天数">
            <InputNumber min={0} precision={2} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="actualDays" label="实际出勤天数">
            <InputNumber min={0} precision={2} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="leaveHours" label="请假小时">
            <InputNumber min={0} precision={2} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="overtimeHours" label="加班小时">
            <InputNumber min={0} precision={2} style={{ width: 130 }} />
          </Form.Item>
          <Form.Item name="absentDays" label="缺勤天数">
            <InputNumber min={0} precision={2} style={{ width: 130 }} />
          </Form.Item>
        </Space>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 考勤汇总页 */
export default function AttendancePage() {
  const [data, setData] = useState<AttendanceView[]>([]);
  const [employees, setEmployees] = useState<EmployeeView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [month, setMonth] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [modalOpen, setModalOpen] = useState(false);
  const [editing, setEditing] = useState<AttendanceView | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageAttendances({ size: 50, keyword, period: month });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, month]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const openModal = async (record: AttendanceView | null) => {
    if (!employees.length) {
      const page = await pageEmployees({ size: 200, status: 'ACTIVE' });
      setEmployees(page.records);
    }
    setEditing(record);
    setModalOpen(true);
  };

  const columns: TableProps<AttendanceView>['columns'] = [
    { title: '员工编号', dataIndex: 'employeeNo', key: 'employeeNo', width: 130 },
    { title: '考勤期间', dataIndex: 'period', key: 'period', width: 110 },
    { title: '应出勤天数', dataIndex: 'workDays', key: 'workDays', width: 110, render: quantity },
    { title: '实际出勤天数', dataIndex: 'actualDays', key: 'actualDays', width: 120, render: quantity },
    { title: '请假小时', dataIndex: 'leaveHours', key: 'leaveHours', width: 100, render: quantity },
    { title: '加班小时', dataIndex: 'overtimeHours', key: 'overtimeHours', width: 100, render: quantity },
    { title: '缺勤天数', dataIndex: 'absentDays', key: 'absentDays', width: 100, render: quantity },
    { title: '备注', dataIndex: 'remark', key: 'remark' },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) =>
        hasPermission('sys:erp:hr:attendance:save') ? (
          <Button type="link" size="small" onClick={() => void openModal(record)}>
            编辑
          </Button>
        ) : null,
    },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="员工编号"
          style={{ width: 220 }}
          onSearch={(value) => setKeyword(value)}
        />
        <DatePicker
          picker="month"
          format="YYYY-MM"
          placeholder="考勤期间"
          value={month ? dayjs(month, 'YYYY-MM') : null}
          onChange={(value) => setMonth(value ? value.format('YYYY-MM') : undefined)}
        />
        {hasPermission('sys:erp:hr:attendance:save') ? (
          <Button type="primary" onClick={() => void openModal(null)}>
            登记考勤
          </Button>
        ) : null}
      </Space>
      <Table<AttendanceView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
      />
      <AttendanceModal
        open={modalOpen}
        initial={editing}
        employees={employees}
        onCancel={() => setModalOpen(false)}
        onSaved={() => {
          setModalOpen(false);
          setVersion((value) => value + 1);
        }}
      />
    </Space>
  );
}
