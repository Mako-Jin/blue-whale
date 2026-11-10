// OA 补卡与异常页：补卡申请（提交/审批/驳回）与考勤异常（确认/豁免）
import { useCallback, useEffect, useState } from 'react';
import { App, Button, DatePicker, Form, Input, Modal, Select, Space, Table, Tabs, Tag } from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createCorrection,
  handleException,
  pageCorrections,
  pageExceptions,
  updateCorrectionStatus,
  type OaAttendanceCorrection,
  type OaAttendanceCorrectionStatus,
  type OaAttendanceException,
  type OaAttendanceExceptionStatus,
  type OaAttendanceExceptionType,
  type OaAttendancePunchType,
} from '../../api/attendance';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 补卡状态文案 */
const CORRECTION_META: Record<OaAttendanceCorrectionStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** 异常类型文案 */
const TYPE_META: Record<OaAttendanceExceptionType, string> = {
  LATE: '迟到',
  EARLY: '早退',
  ABSENT: '缺勤',
};

/** 异常状态文案 */
const EXCEPTION_META: Record<OaAttendanceExceptionStatus, { text: string; color: string }> = {
  PENDING: { text: '待处理', color: 'processing' },
  CONFIRMED: { text: '已确认', color: 'success' },
  WAIVED: { text: '已豁免', color: 'default' },
};

/** 补卡表单值 */
interface CorrectionFormValues {
  workDate: Dayjs;
  punchTime: Dayjs;
  punchType: OaAttendancePunchType;
  reason?: string;
  userName?: string;
}

export default function AttendanceCorrectionPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();

  const [corrections, setCorrections] = useState<OaAttendanceCorrection[]>([]);
  const [correctionTotal, setCorrectionTotal] = useState(0);
  const [correctionPage, setCorrectionPage] = useState(1);
  const [correctionStatus, setCorrectionStatus] = useState<OaAttendanceCorrectionStatus | ''>('');
  const [correctionRange, setCorrectionRange] = useState<[Dayjs, Dayjs] | null>(null);

  const [exceptions, setExceptions] = useState<OaAttendanceException[]>([]);
  const [exceptionTotal, setExceptionTotal] = useState(0);
  const [exceptionPage, setExceptionPage] = useState(1);
  const [exceptionType, setExceptionType] = useState<OaAttendanceExceptionType | ''>('');
  const [exceptionStatus, setExceptionStatus] = useState<OaAttendanceExceptionStatus | ''>('');

  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<CorrectionFormValues>();

  const canCreate = hasPermission('sys:oa:attendance:correction:edit');
  const canHandle = hasPermission('sys:oa:attendance:exception:handle');

  const loadCorrections = useCallback(async () => {
    try {
      const page = await pageCorrections({
        current: correctionPage,
        size: 10,
        status: correctionStatus,
        dateFrom: correctionRange?.[0].format('YYYY-MM-DD'),
        dateTo: correctionRange?.[1].format('YYYY-MM-DD'),
      });
      setCorrections(page.records);
      setCorrectionTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补卡申请查询失败');
      setCorrections([]);
      setCorrectionTotal(0);
    }
  }, [correctionPage, correctionStatus, correctionRange, message]);

  const loadExceptions = useCallback(async () => {
    try {
      const page = await pageExceptions({
        current: exceptionPage,
        size: 10,
        type: exceptionType,
        status: exceptionStatus,
      });
      setExceptions(page.records);
      setExceptionTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '考勤异常查询失败');
      setExceptions([]);
      setExceptionTotal(0);
    }
  }, [exceptionPage, exceptionType, exceptionStatus, message]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([loadCorrections(), loadExceptions()]);
    } finally {
      setLoading(false);
    }
  }, [loadCorrections, loadExceptions]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const openCreate = () => {
    form.resetFields();
    form.setFieldsValue({
      workDate: dayjs(),
      punchTime: dayjs(),
      punchType: 'IN',
      userName: user?.nickname ?? user?.username,
    });
    setModalOpen(true);
  };

  const handleCreate = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      await createCorrection({
        workDate: values.workDate.format('YYYY-MM-DD'),
        punchTime: values.punchTime.format('YYYY-MM-DDTHH:mm:ss'),
        punchType: values.punchType,
        reason: values.reason?.trim() || undefined,
        userName: values.userName?.trim() || undefined,
      });
      message.success('补卡申请已提交');
      setModalOpen(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补卡申请提交失败');
    } finally {
      setSaving(false);
    }
  };

  const changeCorrection = async (record: OaAttendanceCorrection, status: string, text: string) => {
    try {
      await updateCorrectionStatus(record.id, status);
      message.success(`补卡申请${text}`);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '补卡申请处理失败');
    }
  };

  const changeException = async (record: OaAttendanceException, status: string, text: string) => {
    try {
      await handleException(record.id, status);
      message.success(`考勤异常${text}`);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '考勤异常处理失败');
    }
  };

  const correctionColumns: TableProps<OaAttendanceCorrection>['columns'] = [
    { title: '补卡单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '姓名', dataIndex: 'userName', key: 'userName', width: 110 },
    { title: '补卡日期', dataIndex: 'workDate', key: 'workDate', width: 120 },
    {
      title: '补卡时间',
      dataIndex: 'punchTime',
      key: 'punchTime',
      width: 180,
      render: (value: string) => value?.replace('T', ' ') ?? '-',
    },
    { title: '类型', dataIndex: 'punchType', key: 'punchType', width: 80 },
    { title: '原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaAttendanceCorrectionStatus) => {
        const meta = CORRECTION_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_value, record) => {
        if (record.status !== 'PENDING' || !canHandle) {
          return '-';
        }
        return (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => void changeCorrection(record, 'APPROVED', '已通过')}>
              通过
            </Button>
            <Button type="link" size="small" danger onClick={() => void changeCorrection(record, 'REJECTED', '已驳回')}>
              驳回
            </Button>
          </Space>
        );
      },
    },
  ];

  const exceptionColumns: TableProps<OaAttendanceException>['columns'] = [
    { title: '日期', dataIndex: 'workDate', key: 'workDate', width: 120 },
    { title: '用户ID', dataIndex: 'userId', key: 'userId', width: 100 },
    { title: '姓名', dataIndex: 'userName', key: 'userName', width: 110 },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 90,
      render: (value: OaAttendanceExceptionType) => TYPE_META[value] ?? value,
    },
    { title: '说明', dataIndex: 'description', key: 'description', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaAttendanceExceptionStatus) => {
        const meta = EXCEPTION_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    { title: '处理人', dataIndex: 'handler', key: 'handler', width: 110 },
    {
      title: '操作',
      key: 'action',
      width: 170,
      render: (_value, record) => {
        if (record.status !== 'PENDING' || !canHandle) {
          return '-';
        }
        return (
          <Space size={4}>
            <Button type="link" size="small" onClick={() => void changeException(record, 'CONFIRMED', '已确认')}>
              确认
            </Button>
            <Button type="link" size="small" onClick={() => void changeException(record, 'WAIVED', '已豁免')}>
              豁免
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <>
      <Tabs
        items={[
          {
            key: 'corrections',
            label: '补卡申请',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space wrap>
                  <Select
                    allowClear
                    placeholder="状态"
                    style={{ width: 130 }}
                    value={correctionStatus || undefined}
                    options={Object.entries(CORRECTION_META).map(([value, meta]) => ({ label: meta.text, value }))}
                    onChange={(value) => {
                      setCorrectionStatus((value as OaAttendanceCorrectionStatus) ?? '');
                      setCorrectionPage(1);
                    }}
                  />
                  <DatePicker.RangePicker
                    value={correctionRange}
                    onChange={(value) => {
                      setCorrectionRange(value as [Dayjs, Dayjs] | null);
                      setCorrectionPage(1);
                    }}
                  />
                  {canCreate ? (
                    <Button type="primary" onClick={openCreate}>
                      提交补卡
                    </Button>
                  ) : null}
                  <Button onClick={() => void refresh()}>刷新</Button>
                </Space>
                <Table<OaAttendanceCorrection>
                  rowKey="id"
                  loading={loading}
                  columns={correctionColumns}
                  dataSource={corrections}
                  scroll={{ x: 1300 }}
                  pagination={{
                    current: correctionPage,
                    pageSize: 10,
                    total: correctionTotal,
                    onChange: (page) => setCorrectionPage(page),
                  }}
                />
              </Space>
            ),
          },
          {
            key: 'exceptions',
            label: '考勤异常',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space wrap>
                  <Select
                    allowClear
                    placeholder="异常类型"
                    style={{ width: 130 }}
                    value={exceptionType || undefined}
                    options={Object.entries(TYPE_META).map(([value, label]) => ({ label, value }))}
                    onChange={(value) => {
                      setExceptionType((value as OaAttendanceExceptionType) ?? '');
                      setExceptionPage(1);
                    }}
                  />
                  <Select
                    allowClear
                    placeholder="处理状态"
                    style={{ width: 130 }}
                    value={exceptionStatus || undefined}
                    options={Object.entries(EXCEPTION_META).map(([value, meta]) => ({ label: meta.text, value }))}
                    onChange={(value) => {
                      setExceptionStatus((value as OaAttendanceExceptionStatus) ?? '');
                      setExceptionPage(1);
                    }}
                  />
                  <Button onClick={() => void refresh()}>刷新</Button>
                </Space>
                <Table<OaAttendanceException>
                  rowKey="id"
                  loading={loading}
                  columns={exceptionColumns}
                  dataSource={exceptions}
                  scroll={{ x: 1100 }}
                  pagination={{
                    current: exceptionPage,
                    pageSize: 10,
                    total: exceptionTotal,
                    onChange: (page) => setExceptionPage(page),
                  }}
                />
              </Space>
            ),
          },
        ]}
      />
      <Modal
        open={modalOpen}
        title="提交补卡申请"
        onOk={() => void handleCreate()}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="workDate" label="补卡日期" rules={[{ required: true, message: '请选择补卡日期' }]} style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="punchTime" label="补卡时间" rules={[{ required: true, message: '请选择补卡时间' }]} style={{ flex: 1 }}>
              <DatePicker showTime style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="punchType" label="补卡类型" style={{ flex: 1 }}>
              <Select options={[{ label: '上班', value: 'IN' }, { label: '下班', value: 'OUT' }]} />
            </Form.Item>
          </Space>
          <Form.Item name="userName" label="姓名">
            <Input maxLength={64} placeholder="选填" />
          </Form.Item>
          <Form.Item name="reason" label="补卡原因">
            <Input.TextArea rows={3} maxLength={512} showCount placeholder="如忘打卡、设备故障等" />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
