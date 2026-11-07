// OA 用车管理页：用车申请（派车/回车）、车辆主数据、司机主数据
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
  Table,
  Tabs,
  Tag,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import type { Dayjs } from 'dayjs';
import {
  createBooking,
  createDriver,
  createVehicle,
  deleteBooking,
  deleteDriver,
  deleteVehicle,
  dispatchBooking,
  pageBookings,
  pageDrivers,
  pageVehicles,
  returnVehicle,
  updateBooking,
  updateBookingStatus,
  updateDriver,
  updateVehicle,
  type OaAdministrativeStatus,
  type OaDriver,
  type OaDriverInput,
  type OaVehicle,
  type OaVehicleBooking,
  type OaVehicleBookingInput,
  type OaVehicleInput,
} from '../../api/administrative';
import { getCurrentUser } from '../../api/client';
import { hasPermission } from '../../utils/permission';

/** 审批状态文案 */
const STATUS_META: Record<OaAdministrativeStatus, { text: string; color: string }> = {
  PENDING: { text: '待审批', color: 'processing' },
  APPROVED: { text: '已通过', color: 'success' },
  REJECTED: { text: '已驳回', color: 'error' },
  CANCELLED: { text: '已撤销', color: 'default' },
};

/** 用车申请表单值 */
interface BookingFormValues {
  applicant: string;
  applicantId?: number;
  deptId?: number;
  timeRange: [Dayjs, Dayjs];
  origin?: string;
  destination?: string;
  reason?: string;
  remark?: string;
}

/** 车辆表单值 */
interface VehicleFormValues {
  plateNo: string;
  vehicleType?: string;
  seats?: number;
  status: string;
  currentMileage?: number;
  purchaseDate?: Dayjs;
  remark?: string;
}

/** 司机表单值 */
interface DriverFormValues {
  name: string;
  phone?: string;
  licenseNo: string;
  licenseType?: string;
  status: string;
  remark?: string;
}

/** 时间格式化为展示文本 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

export default function VehicleAdminPage() {
  const { message } = App.useApp();
  const user = getCurrentUser();

  const [bookings, setBookings] = useState<OaVehicleBooking[]>([]);
  const [bookingTotal, setBookingTotal] = useState(0);
  const [bookingPage, setBookingPage] = useState(1);
  const [bookingKeyword, setBookingKeyword] = useState('');
  const [bookingStatus, setBookingStatus] = useState<OaAdministrativeStatus | ''>('');

  const [vehicles, setVehicles] = useState<OaVehicle[]>([]);
  const [drivers, setDrivers] = useState<OaDriver[]>([]);
  const [loading, setLoading] = useState(false);

  const [bookingModal, setBookingModal] = useState(false);
  const [editingBooking, setEditingBooking] = useState<OaVehicleBooking | null>(null);
  const [vehicleModal, setVehicleModal] = useState(false);
  const [editingVehicle, setEditingVehicle] = useState<OaVehicle | null>(null);
  const [driverModal, setDriverModal] = useState(false);
  const [editingDriver, setEditingDriver] = useState<OaDriver | null>(null);
  const [dispatchModal, setDispatchModal] = useState(false);
  const [dispatchTarget, setDispatchTarget] = useState<OaVehicleBooking | null>(null);
  const [returnModal, setReturnModal] = useState(false);
  const [returnTarget, setReturnTarget] = useState<OaVehicleBooking | null>(null);
  const [saving, setSaving] = useState(false);

  const [bookingForm] = Form.useForm<BookingFormValues>();
  const [vehicleForm] = Form.useForm<VehicleFormValues>();
  const [driverForm] = Form.useForm<DriverFormValues>();
  const [dispatchForm] = Form.useForm<{ vehicleId: number; driverId: number }>();
  const [returnForm] = Form.useForm<{ startMileage: number; endMileage: number; fuelCost?: number; remark?: string }>();

  const canAdd = hasPermission('sys:oa:vehicle:add');
  const canEdit = hasPermission('sys:oa:vehicle:edit');
  const canDelete = hasPermission('sys:oa:vehicle:delete');
  const canBookingAdd = hasPermission('sys:oa:vehicle:booking:add');
  const canBookingEdit = hasPermission('sys:oa:vehicle:booking:edit');
  const canDispatch = hasPermission('sys:oa:vehicle:dispatch');
  const canReturn = hasPermission('sys:oa:vehicle:return');

  const loadBookings = useCallback(async () => {
    try {
      const page = await pageBookings({
        current: bookingPage,
        size: 10,
        keyword: bookingKeyword.trim() || undefined,
        status: bookingStatus,
      });
      setBookings(page.records);
      setBookingTotal(page.total);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用车申请查询失败');
      setBookings([]);
      setBookingTotal(0);
    }
  }, [bookingPage, bookingKeyword, bookingStatus, message]);

  const loadMasters = useCallback(async () => {
    try {
      const [vehiclePage, driverPage] = await Promise.all([
        pageVehicles({ current: 1, size: 100 }),
        pageDrivers({ current: 1, size: 100 }),
      ]);
      setVehicles(vehiclePage.records);
      setDrivers(driverPage.records);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '车辆/司机查询失败');
    }
  }, [message]);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      await Promise.all([loadBookings(), loadMasters()]);
    } finally {
      setLoading(false);
    }
  }, [loadBookings, loadMasters]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const openBooking = (record: OaVehicleBooking | null) => {
    setEditingBooking(record);
    bookingForm.resetFields();
    bookingForm.setFieldsValue({
      applicant: record?.applicant ?? (user?.nickname ?? user?.username ?? ''),
      applicantId: record?.applicantId ?? (typeof user?.userId === 'number' ? user.userId : undefined),
      deptId: record?.deptId,
      timeRange: record
        ? [dayjs(record.startTime), dayjs(record.endTime)]
        : [dayjs().hour(9).minute(0), dayjs().hour(18).minute(0)],
      origin: record?.origin,
      destination: record?.destination,
      reason: record?.reason,
      remark: record?.remark,
    });
    setBookingModal(true);
  };

  const saveBooking = async () => {
    const values = await bookingForm.validateFields();
    const payload: OaVehicleBookingInput = {
      applicant: values.applicant.trim(),
      applicantId: values.applicantId,
      deptId: values.deptId,
      startTime: values.timeRange[0].format('YYYY-MM-DDTHH:mm:ss'),
      endTime: values.timeRange[1].format('YYYY-MM-DDTHH:mm:ss'),
      origin: values.origin?.trim() || undefined,
      destination: values.destination?.trim() || undefined,
      reason: values.reason?.trim() || undefined,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editingBooking) {
        await updateBooking(editingBooking.id, payload);
        message.success('用车申请已更新');
      } else {
        await createBooking(payload);
        message.success('用车申请已创建并提交审批');
      }
      setBookingModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用车申请保存失败');
    } finally {
      setSaving(false);
    }
  };

  const cancelBooking = async (record: OaVehicleBooking) => {
    try {
      await updateBookingStatus(record.id, 'CANCELLED');
      message.success('用车申请已撤销');
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用车申请撤销失败');
    }
  };

  const removeBooking = async (record: OaVehicleBooking) => {
    try {
      await deleteBooking(record.id);
      message.success('用车申请已删除');
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '用车申请删除失败');
    }
  };

  const openDispatch = (record: OaVehicleBooking) => {
    setDispatchTarget(record);
    dispatchForm.resetFields();
    setDispatchModal(true);
  };

  const handleDispatch = async () => {
    if (!dispatchTarget) {
      return;
    }
    const values = await dispatchForm.validateFields();
    setSaving(true);
    try {
      await dispatchBooking(dispatchTarget.id, values);
      message.success('已派车');
      setDispatchModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '派车失败');
    } finally {
      setSaving(false);
    }
  };

  const openReturn = (record: OaVehicleBooking) => {
    setReturnTarget(record);
    returnForm.resetFields();
    const vehicle = vehicles.find((item) => item.id === record.vehicleId);
    returnForm.setFieldsValue({
      startMileage: Number(vehicle?.currentMileage ?? 0),
      endMileage: Number(vehicle?.currentMileage ?? 0),
      fuelCost: 0,
    });
    setReturnModal(true);
  };

  const handleReturn = async () => {
    if (!returnTarget) {
      return;
    }
    const values = await returnForm.validateFields();
    setSaving(true);
    try {
      await returnVehicle(returnTarget.id, values);
      message.success('回车登记成功');
      setReturnModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '回车登记失败');
    } finally {
      setSaving(false);
    }
  };

  const openVehicle = (record: OaVehicle | null) => {
    setEditingVehicle(record);
    vehicleForm.resetFields();
    vehicleForm.setFieldsValue({
      plateNo: record?.plateNo,
      vehicleType: record?.vehicleType,
      seats: record?.seats,
      status: record?.status ?? 'ENABLED',
      currentMileage: record?.currentMileage === undefined ? 0 : Number(record.currentMileage),
      purchaseDate: record?.purchaseDate ? dayjs(record.purchaseDate) : undefined,
      remark: record?.remark,
    });
    setVehicleModal(true);
  };

  const saveVehicle = async () => {
    const values = await vehicleForm.validateFields();
    const payload: OaVehicleInput = {
      plateNo: values.plateNo.trim(),
      vehicleType: values.vehicleType?.trim() || undefined,
      seats: values.seats,
      status: values.status,
      currentMileage: values.currentMileage,
      purchaseDate: values.purchaseDate?.format('YYYY-MM-DD'),
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editingVehicle) {
        await updateVehicle(editingVehicle.id, payload);
        message.success('车辆已更新');
      } else {
        await createVehicle(payload);
        message.success('车辆已创建');
      }
      setVehicleModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '车辆保存失败');
    } finally {
      setSaving(false);
    }
  };

  const openDriver = (record: OaDriver | null) => {
    setEditingDriver(record);
    driverForm.resetFields();
    driverForm.setFieldsValue({
      name: record?.name,
      phone: record?.phone,
      licenseNo: record?.licenseNo,
      licenseType: record?.licenseType,
      status: record?.status ?? 'ENABLED',
      remark: record?.remark,
    });
    setDriverModal(true);
  };

  const saveDriver = async () => {
    const values = await driverForm.validateFields();
    const payload: OaDriverInput = {
      name: values.name.trim(),
      phone: values.phone?.trim() || undefined,
      licenseNo: values.licenseNo.trim(),
      licenseType: values.licenseType?.trim() || undefined,
      status: values.status,
      remark: values.remark?.trim() || undefined,
    };
    setSaving(true);
    try {
      if (editingDriver) {
        await updateDriver(editingDriver.id, payload);
        message.success('司机已更新');
      } else {
        await createDriver(payload);
        message.success('司机已创建');
      }
      setDriverModal(false);
      await refresh();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '司机保存失败');
    } finally {
      setSaving(false);
    }
  };

  const bookingColumns: TableProps<OaVehicleBooking>['columns'] = [
    { title: '用车单号', dataIndex: 'code', key: 'code', width: 170 },
    { title: '申请人', dataIndex: 'applicant', key: 'applicant', width: 110 },
    {
      title: '用车时间',
      key: 'time',
      width: 300,
      render: (_value, record) => `${timeText(record.startTime)} ~ ${timeText(record.endTime)}`,
    },
    {
      title: '行程',
      key: 'route',
      ellipsis: true,
      render: (_value, record) => `${record.origin ?? '-'} → ${record.destination ?? '-'}`,
    },
    { title: '事由', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '车辆',
      dataIndex: 'vehicleId',
      key: 'vehicleId',
      width: 120,
      render: (value?: number) =>
        value ? vehicles.find((item) => item.id === value)?.plateNo ?? value : '-',
    },
    {
      title: '司机',
      dataIndex: 'driverId',
      key: 'driverId',
      width: 110,
      render: (value?: number) =>
        value ? drivers.find((item) => item.id === value)?.name ?? value : '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (value: OaAdministrativeStatus) => {
        const meta = STATUS_META[value] ?? { text: value, color: 'default' };
        return <Tag color={meta.color}>{meta.text}</Tag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 250,
      render: (_value, record) => (
        <Space size={4}>
          {record.status === 'PENDING' && canBookingEdit ? (
            <Button type="link" size="small" onClick={() => openBooking(record)}>
              编辑
            </Button>
          ) : null}
          {record.status === 'PENDING' && canBookingEdit ? (
            <Popconfirm title="确认撤销该用车申请？" onConfirm={() => void cancelBooking(record)}>
              <Button type="link" size="small">
                撤销
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'PENDING' && canBookingEdit ? (
            <Popconfirm title="确认删除该用车申请？" onConfirm={() => void removeBooking(record)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
          {record.status === 'APPROVED' && !record.vehicleId && canDispatch ? (
            <Button type="link" size="small" onClick={() => openDispatch(record)}>
              派车
            </Button>
          ) : null}
          {record.status === 'APPROVED' && record.vehicleId && canReturn ? (
            <Button type="link" size="small" onClick={() => openReturn(record)}>
              回车登记
            </Button>
          ) : null}
        </Space>
      ),
    },
  ];

  const vehicleColumns: TableProps<OaVehicle>['columns'] = [
    { title: '车牌号', dataIndex: 'plateNo', key: 'plateNo', width: 130 },
    { title: '车型', dataIndex: 'vehicleType', key: 'vehicleType', width: 120 },
    { title: '座位数', dataIndex: 'seats', key: 'seats', width: 90 },
    {
      title: '当前里程',
      dataIndex: 'currentMileage',
      key: 'currentMileage',
      width: 120,
      align: 'right',
      render: (value?: number) => Number(value ?? 0).toFixed(1),
    },
    { title: '购置日期', dataIndex: 'purchaseDate', key: 'purchaseDate', width: 120 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value?: string) => (
        <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          {canEdit ? (
            <Button type="link" size="small" onClick={() => openVehicle(record)}>
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Popconfirm
              title="确认删除该车辆？"
              onConfirm={async () => {
                try {
                  await deleteVehicle(record.id);
                  message.success('车辆已删除');
                  await refresh();
                } catch (error) {
                  message.error(error instanceof Error ? error.message : '车辆删除失败');
                }
              }}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const driverColumns: TableProps<OaDriver>['columns'] = [
    { title: '姓名', dataIndex: 'name', key: 'name', width: 110 },
    { title: '手机号', dataIndex: 'phone', key: 'phone', width: 140 },
    { title: '驾驶证号', dataIndex: 'licenseNo', key: 'licenseNo', width: 170 },
    { title: '准驾车型', dataIndex: 'licenseType', key: 'licenseType', width: 110 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value?: string) => (
        <Tag color={value === 'ENABLED' ? 'success' : 'default'}>{value === 'ENABLED' ? '启用' : '停用'}</Tag>
      ),
    },
    { title: '备注', dataIndex: 'remark', key: 'remark', ellipsis: true },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          {canEdit ? (
            <Button type="link" size="small" onClick={() => openDriver(record)}>
              编辑
            </Button>
          ) : null}
          {canDelete ? (
            <Popconfirm
              title="确认删除该司机？"
              onConfirm={async () => {
                try {
                  await deleteDriver(record.id);
                  message.success('司机已删除');
                  await refresh();
                } catch (error) {
                  message.error(error instanceof Error ? error.message : '司机删除失败');
                }
              }}
            >
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Tabs
        items={[
          {
            key: 'bookings',
            label: '用车申请',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space wrap>
                  <Input
                    allowClear
                    placeholder="单号/申请人"
                    style={{ width: 200 }}
                    value={bookingKeyword}
                    onChange={(event) => {
                      setBookingKeyword(event.target.value);
                      setBookingPage(1);
                    }}
                  />
                  <Select
                    allowClear
                    placeholder="状态"
                    style={{ width: 130 }}
                    value={bookingStatus || undefined}
                    options={Object.entries(STATUS_META).map(([value, meta]) => ({ label: meta.text, value }))}
                    onChange={(value) => {
                      setBookingStatus((value as OaAdministrativeStatus) ?? '');
                      setBookingPage(1);
                    }}
                  />
                  {canBookingAdd ? (
                    <Button type="primary" onClick={() => openBooking(null)}>
                      新建用车申请
                    </Button>
                  ) : null}
                  <Button onClick={() => void refresh()}>刷新</Button>
                </Space>
                <Table<OaVehicleBooking>
                  rowKey="id"
                  loading={loading}
                  columns={bookingColumns}
                  dataSource={bookings}
                  scroll={{ x: 1500 }}
                  pagination={{
                    current: bookingPage,
                    pageSize: 10,
                    total: bookingTotal,
                    onChange: (page) => setBookingPage(page),
                  }}
                />
              </Space>
            ),
          },
          {
            key: 'vehicles',
            label: '车辆',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  {canAdd ? (
                    <Button type="primary" onClick={() => openVehicle(null)}>
                      新增车辆
                    </Button>
                  ) : null}
                  <Button loading={loading} onClick={() => void refresh()}>
                    刷新
                  </Button>
                </Space>
                <Table<OaVehicle>
                  rowKey="id"
                  loading={loading}
                  columns={vehicleColumns}
                  dataSource={vehicles}
                  scroll={{ x: 1100 }}
                  pagination={false}
                />
              </Space>
            ),
          },
          {
            key: 'drivers',
            label: '司机',
            children: (
              <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space>
                  {canAdd ? (
                    <Button type="primary" onClick={() => openDriver(null)}>
                      新增司机
                    </Button>
                  ) : null}
                  <Button loading={loading} onClick={() => void refresh()}>
                    刷新
                  </Button>
                </Space>
                <Table<OaDriver>
                  rowKey="id"
                  loading={loading}
                  columns={driverColumns}
                  dataSource={drivers}
                  scroll={{ x: 1100 }}
                  pagination={false}
                />
              </Space>
            ),
          },
        ]}
      />
      <Modal
        open={bookingModal}
        title={editingBooking ? '编辑用车申请' : '新建用车申请'}
        width={640}
        onOk={() => void saveBooking()}
        confirmLoading={saving}
        onCancel={() => setBookingModal(false)}
        destroyOnClose
      >
        <Form form={bookingForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="applicant" label="申请人" rules={[{ required: true, message: '请输入申请人' }]} style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="applicantId" label="申请人ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
            <Form.Item name="deptId" label="部门ID" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
          </Space>
          <Form.Item name="timeRange" label="用车时间" rules={[{ required: true, message: '请选择用车时间' }]}>
            <DatePicker.RangePicker showTime style={{ width: '100%' }} format="YYYY-MM-DD HH:mm" />
          </Form.Item>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="origin" label="出发地" style={{ flex: 1 }}>
              <Input maxLength={128} />
            </Form.Item>
            <Form.Item name="destination" label="目的地" style={{ flex: 1 }}>
              <Input maxLength={128} />
            </Form.Item>
          </Space>
          <Form.Item name="reason" label="用车事由">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={dispatchModal}
        title="派车"
        onOk={() => void handleDispatch()}
        confirmLoading={saving}
        onCancel={() => setDispatchModal(false)}
        destroyOnClose
      >
        <Form form={dispatchForm} layout="vertical">
          <Form.Item name="vehicleId" label="车辆" rules={[{ required: true, message: '请选择车辆' }]}>
            <Select
              options={vehicles
                .filter((item) => item.status === 'ENABLED')
                .map((item) => ({ label: `${item.plateNo}（${item.seats ?? '-'}座）`, value: item.id }))}
            />
          </Form.Item>
          <Form.Item name="driverId" label="司机" rules={[{ required: true, message: '请选择司机' }]}>
            <Select
              options={drivers
                .filter((item) => item.status === 'ENABLED')
                .map((item) => ({ label: `${item.name}（${item.licenseType ?? '-'}）`, value: item.id }))}
            />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={returnModal}
        title="回车登记"
        onOk={() => void handleReturn()}
        confirmLoading={saving}
        onCancel={() => setReturnModal(false)}
        destroyOnClose
      >
        <Form form={returnForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="startMileage" label="出车里程" rules={[{ required: true, message: '请输入出车里程' }]} style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={1} />
            </Form.Item>
            <Form.Item name="endMileage" label="回车里程" rules={[{ required: true, message: '请输入回车里程' }]} style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={1} />
            </Form.Item>
            <Form.Item name="fuelCost" label="油费" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={2} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={vehicleModal}
        title={editingVehicle ? '编辑车辆' : '新增车辆'}
        onOk={() => void saveVehicle()}
        confirmLoading={saving}
        onCancel={() => setVehicleModal(false)}
        destroyOnClose
      >
        <Form form={vehicleForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="plateNo" label="车牌号" rules={[{ required: true, message: '请输入车牌号' }]} style={{ flex: 1 }}>
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item name="vehicleType" label="车型" style={{ flex: 1 }}>
              <Input maxLength={32} placeholder="如 轿车 / MPV" />
            </Form.Item>
            <Form.Item name="seats" label="座位数" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={1} precision={0} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="currentMileage" label="当前里程" style={{ flex: 1 }}>
              <InputNumber style={{ width: '100%' }} min={0} precision={1} />
            </Form.Item>
            <Form.Item name="purchaseDate" label="购置日期" style={{ flex: 1 }}>
              <DatePicker style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ flex: 1 }}>
              <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={driverModal}
        title={editingDriver ? '编辑司机' : '新增司机'}
        onOk={() => void saveDriver()}
        confirmLoading={saving}
        onCancel={() => setDriverModal(false)}
        destroyOnClose
      >
        <Form form={driverForm} layout="vertical">
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="name" label="姓名" rules={[{ required: true, message: '请输入姓名' }]} style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="phone" label="手机号" style={{ flex: 1 }}>
              <Input maxLength={32} />
            </Form.Item>
            <Form.Item name="status" label="状态" style={{ flex: 1 }}>
              <Select options={[{ label: '启用', value: 'ENABLED' }, { label: '停用', value: 'DISABLED' }]} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }}>
            <Form.Item name="licenseNo" label="驾驶证号" rules={[{ required: true, message: '请输入驾驶证号' }]} style={{ flex: 2 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item name="licenseType" label="准驾车型" style={{ flex: 1 }}>
              <Input maxLength={16} placeholder="如 C1" />
            </Form.Item>
          </Space>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
