// ERP 薪资页：批次新建、计算、审批（计提凭证）、发放（发放凭证）、结账与我的工资条
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  DatePicker,
  Form,
  Input,
  Modal,
  Popconfirm,
  Space,
  Table,
  Tag,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs, { type Dayjs } from 'dayjs';
import {
  HR_STATUS_LABEL,
  approvePayroll,
  calculatePayroll,
  cancelPayroll,
  closePayroll,
  createPayroll,
  getPayroll,
  hrStatusColor,
  myPayslips,
  pagePayrolls,
  payPayroll,
  type PayrollRunView,
  type PayslipView,
} from '../api/hr';
import { hasPermission } from '../utils/permission';
import { money, quantity } from '../utils/scm';

/** 新建薪资批次表单 */
interface PayrollFormValues {
  month: Dayjs;
  remark?: string;
}

/** 新建薪资批次弹窗 */
function PayrollModal({
  open,
  onCancel,
  onSaved,
}: {
  open: boolean;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form] = Form.useForm<PayrollFormValues>();

  useEffect(() => {
    if (!open) return;
    form.resetFields();
    form.setFieldsValue({ month: dayjs() });
  }, [open, form]);

  const handleOk = async () => {
    const values = await form.validateFields().catch(() => null);
    if (!values) return;
    await createPayroll({
      period: values.month.format('YYYY-MM'),
      remark: values.remark,
    });
    message.success('薪资批次已创建');
    onSaved();
  };

  return (
    <Modal open={open} title="新建薪资批次" onOk={handleOk} onCancel={onCancel} destroyOnHidden>
      <Form form={form} layout="vertical">
        <Form.Item
          name="month"
          label="薪资期间"
          rules={[{ required: true, message: '请选择薪资期间' }]}
        >
          <DatePicker picker="month" format="YYYY-MM" />
        </Form.Item>
        <Form.Item name="remark" label="备注">
          <Input.TextArea rows={2} maxLength={255} />
        </Form.Item>
      </Form>
    </Modal>
  );
}

/** 薪资页 */
export default function PayrollPage() {
  const [data, setData] = useState<PayrollRunView[]>([]);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [version, setVersion] = useState(0);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<PayrollRunView | null>(null);
  const [myOpen, setMyOpen] = useState(false);
  const [myData, setMyData] = useState<PayrollRunView[]>([]);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pagePayrolls({ size: 50, keyword, status });
      setData(page.records);
    } finally {
      setLoading(false);
    }
  }, [keyword, status]);

  useEffect(() => {
    void load();
  }, [load, version]);

  const act = async (action: (id: number) => Promise<PayrollRunView>, id: number, text: string) => {
    await action(id);
    message.success(text);
    setVersion((value) => value + 1);
  };

  const handleDetail = async (id: number) => {
    setDetail(await getPayroll(id));
  };

  const openMy = async () => {
    const page = await myPayslips({ size: 20 });
    setMyData(page.records);
    setMyOpen(true);
  };

  const columns: TableProps<PayrollRunView>['columns'] = [
    { title: '批次号', dataIndex: 'runNo', key: 'runNo', width: 170 },
    { title: '薪资期间', dataIndex: 'period', key: 'period', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value) => (
        <Tag color={hrStatusColor(String(value))}>{HR_STATUS_LABEL[String(value)] ?? value}</Tag>
      ),
    },
    { title: '人数', dataIndex: 'employeeCount', key: 'employeeCount', width: 80 },
    { title: '应发合计', dataIndex: 'totalGross', key: 'totalGross', width: 130, render: money },
    {
      title: '社保合计',
      dataIndex: 'totalSocialInsurance',
      key: 'totalSocialInsurance',
      width: 120,
      render: money,
    },
    {
      title: '公积金合计',
      dataIndex: 'totalHousingFund',
      key: 'totalHousingFund',
      width: 130,
      render: money,
    },
    { title: '个税合计', dataIndex: 'totalTax', key: 'totalTax', width: 120, render: money },
    { title: '实发合计', dataIndex: 'totalNet', key: 'totalNet', width: 130, render: money },
    {
      title: '操作',
      key: 'action',
      width: 280,
      render: (_, record) => (
        <Space size="small" wrap>
          <Button type="link" size="small" onClick={() => handleDetail(record.id)}>
            查看
          </Button>
          {record.status === 'DRAFT' && hasPermission('sys:erp:hr:payroll:calculate') ? (
            <Popconfirm
              title="将按在职员工与考勤重新生成工资条，确认计算？"
              onConfirm={() => act(calculatePayroll, record.id, '薪资已计算')}
            >
              <Button type="link" size="small">
                计算
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'CALCULATED' && hasPermission('sys:erp:hr:payroll:approve') ? (
            <Popconfirm
              title="审批后将生成计提凭证且明细不可修改，确认？"
              onConfirm={() => act(approvePayroll, record.id, '薪资已审批')}
            >
              <Button type="link" size="small">
                审批
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'APPROVED' && hasPermission('sys:erp:hr:payroll:pay') ? (
            <Popconfirm
              title="发放后将生成发放凭证，确认？"
              onConfirm={() => act(payPayroll, record.id, '薪资已发放')}
            >
              <Button type="link" size="small">
                发放
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'PAID' && hasPermission('sys:erp:hr:payroll:approve') ? (
            <Popconfirm
              title="确认结账该薪资批次？"
              onConfirm={() => act(closePayroll, record.id, '薪资批次已结账')}
            >
              <Button type="link" size="small">
                结账
              </Button>
            </Popconfirm>
          ) : null}
          {['DRAFT', 'CALCULATED'].includes(record.status) &&
          hasPermission('sys:erp:hr:payroll:create') ? (
            <Popconfirm
              title="确认取消该薪资批次？"
              onConfirm={() => act(cancelPayroll, record.id, '薪资批次已取消')}
            >
              <Button type="link" size="small" danger>
                取消
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const payslipColumns: TableProps<PayslipView>['columns'] = [
    { title: '员工编号', dataIndex: 'employeeNo', key: 'employeeNo', width: 120 },
    { title: '姓名', dataIndex: 'employeeName', key: 'employeeName', width: 100 },
    { title: '组织', dataIndex: 'orgCode', key: 'orgCode', width: 110 },
    { title: '基本工资', dataIndex: 'baseSalary', key: 'baseSalary', width: 110, render: money },
    { title: '津贴', dataIndex: 'allowance', key: 'allowance', width: 100, render: money },
    { title: '加班费', dataIndex: 'overtimePay', key: 'overtimePay', width: 100, render: money },
    {
      title: '请假扣款',
      dataIndex: 'leaveDeduction',
      key: 'leaveDeduction',
      width: 100,
      render: money,
    },
    {
      title: '缺勤扣款',
      dataIndex: 'absenceDeduction',
      key: 'absenceDeduction',
      width: 100,
      render: money,
    },
    { title: '应发', dataIndex: 'grossPay', key: 'grossPay', width: 110, render: money },
    {
      title: '社保',
      dataIndex: 'socialInsurance',
      key: 'socialInsurance',
      width: 100,
      render: money,
    },
    { title: '公积金', dataIndex: 'housingFund', key: 'housingFund', width: 100, render: money },
    { title: '个税', dataIndex: 'tax', key: 'tax', width: 100, render: money },
    { title: '实发', dataIndex: 'netPay', key: 'netPay', width: 110, render: money },
  ];

  return (
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Space wrap>
        <Input.Search
          allowClear
          placeholder="批次号 / 期间"
          style={{ width: 220 }}
          onSearch={(value) => setKeyword(value)}
        />
        <Button onClick={() => setStatus(undefined)} type={status ? 'default' : 'primary'}>
          全部
        </Button>
        {['DRAFT', 'CALCULATED', 'APPROVED', 'PAID', 'CLOSED', 'CANCELLED'].map((value) => (
          <Button
            key={value}
            onClick={() => setStatus(value)}
            type={status === value ? 'primary' : 'default'}
          >
            {HR_STATUS_LABEL[value] ?? value}
          </Button>
        ))}
        {hasPermission('sys:erp:hr:payroll:create') ? (
          <Button type="primary" onClick={() => setCreateOpen(true)}>
            新建薪资批次
          </Button>
        ) : null}
        {hasPermission('sys:erp:hr:payslip:list') ? (
          <Button onClick={() => void openMy()}>我的工资条</Button>
        ) : null}
      </Space>
      <Table<PayrollRunView>
        rowKey="id"
        loading={loading}
        columns={columns}
        dataSource={data}
        pagination={false}
        scroll={{ x: 1500 }}
      />
      <PayrollModal
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onSaved={() => {
          setCreateOpen(false);
          setVersion((value) => value + 1);
        }}
      />
      <Modal
        open={detail !== null}
        title={detail ? `薪资批次 ${detail.runNo}` : '薪资批次详情'}
        footer={null}
        width={1200}
        onCancel={() => setDetail(null)}
        destroyOnHidden
      >
        {detail ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Space size="large" wrap>
              <span>期间：{detail.period}</span>
              <span>状态：{HR_STATUS_LABEL[detail.status] ?? detail.status}</span>
              <span>人数：{quantity(detail.employeeCount)}</span>
              <span>应发：{money(detail.totalGross)}</span>
              <span>实发：{money(detail.totalNet)}</span>
            </Space>
            <Table<PayslipView>
              rowKey="id"
              size="small"
              columns={payslipColumns}
              dataSource={detail.payslips ?? []}
              pagination={false}
              scroll={{ x: 1400 }}
            />
          </Space>
        ) : null}
      </Modal>
      <Modal
        open={myOpen}
        title="我的工资条"
        footer={null}
        width={1100}
        onCancel={() => setMyOpen(false)}
        destroyOnHidden
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          {myData.map((run) => (
            <Space key={run.id} direction="vertical" size="small" style={{ width: '100%' }}>
              <span>
                {run.period}（{HR_STATUS_LABEL[run.status] ?? run.status}）实发{' '}
                {money(run.totalNet)}
              </span>
              <Table<PayslipView>
                rowKey="id"
                size="small"
                columns={payslipColumns}
                dataSource={run.payslips ?? []}
                pagination={false}
                scroll={{ x: 1400 }}
              />
            </Space>
          ))}
        </Space>
      </Modal>
    </Space>
  );
}
