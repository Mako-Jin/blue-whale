// 优惠券：券模板（运营）与我的券（买家）两个视角
//
// 模板与用户券分两张表、两套页面，是因为它们回答的问题不同：
// 模板回答「能发什么券」，我的券回答「我手上有哪些、还能不能用」。
// 我的券必须显示不可用原因——券包里看得见、下单页找不到，是最典型的客服工单。
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  DatePicker,
  Form,
  Input,
  InputNumber,
  Modal,
  Select,
  Space,
  Table,
  Tabs,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import dayjs from 'dayjs';
import {
  changeCouponStatus,
  claimCoupon,
  createCoupon,
  pageCoupon,
  pageMyCoupons,
  updateCoupon,
  type CouponSaveBody,
  type CouponUserView,
  type CouponView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  COUPON_STATUS_LABEL,
  COUPON_TYPE_LABEL,
  COUPON_USER_STATUS_COLOR,
  COUPON_USER_STATUS_LABEL,
  ENABLE_STATUS_COLOR,
  formatCouponValue,
  formatDateTime,
  formatMoney,
} from '../../utils/dict';

/** 券类型下拉 */
const TYPE_OPTIONS = [
  { value: 'FULL_REDUCTION', label: COUPON_TYPE_LABEL.FULL_REDUCTION },
  { value: 'DISCOUNT', label: COUPON_TYPE_LABEL.DISCOUNT },
];

/** 表单里日期字段的结构（提交前转成字符串） */
interface CouponFormValues extends Omit<CouponSaveBody, 'validFrom' | 'validTo'> {
  validRange: [dayjs.Dayjs, dayjs.Dayjs];
}

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

export default function CouponPage() {
  return (
    <Card title="优惠券">
      <Tabs
        defaultActiveKey="template"
        items={[
          { key: 'template', label: '券模板', children: <CouponTemplateTab /> },
          { key: 'mine', label: '我的券', children: <MyCouponTab /> },
        ]}
      />
    </Card>
  );
}

/** 券模板管理 */
function CouponTemplateTab() {
  const [rows, setRows] = useState<CouponView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [filter, setFilter] = useState<{ keyword?: string; couponType?: string; status?: string }>({});
  const [editing, setEditing] = useState<CouponView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<CouponFormValues>();
  const couponType = Form.useWatch('couponType', form);

  const canManage = hasPermission('sys:mall:coupon:manage');
  const canClaim = hasPermission('sys:mall:coupon:claim');

  /** 分页查询 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageCoupon({
        keyword: filter.keyword?.trim() || undefined,
        couponType: filter.couponType,
        status: filter.status,
        current: page.current,
        size: page.size,
      });
      setRows(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [filter, page]);

  useEffect(() => {
    void load();
  }, [load]);

  /** 打开新建 */
  const openCreate = () => {
    setEditing(null);
    form.setFieldsValue({
      couponCode: '',
      couponName: '',
      couponType: 'FULL_REDUCTION',
      thresholdAmount: 100,
      faceAmount: 20,
      perUserLimit: 1,
      totalQty: 100,
      validRange: [dayjs(), dayjs().add(30, 'day')],
      remark: '',
    });
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (row: CouponView) => {
    setEditing(row);
    form.setFieldsValue({
      couponCode: row.couponCode,
      couponName: row.couponName,
      couponType: row.couponType,
      thresholdAmount: row.thresholdAmount,
      faceAmount: row.faceAmount,
      discountRate: row.discountRate,
      maxDiscountAmount: row.maxDiscountAmount,
      channelId: row.channelId,
      totalQty: row.totalQty,
      perUserLimit: row.perUserLimit,
      validRange: [dayjs(row.validFrom), dayjs(row.validTo)],
      status: row.status,
      remark: row.remark,
    });
    setModalOpen(true);
  };

  /** 提交 */
  const submit = async () => {
    const values = await form.validateFields();
    const body: CouponSaveBody = {
      ...values,
      validFrom: values.validRange[0].format('YYYY-MM-DD HH:mm:ss'),
      validTo: values.validRange[1].format('YYYY-MM-DD HH:mm:ss'),
    };
    setSaving(true);
    try {
      if (editing) {
        await updateCoupon(editing.id, body);
        message.success('优惠券已更新（已领出的券不受影响）');
      } else {
        await createCoupon(body);
        message.success('优惠券已创建');
      }
      setModalOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  /** 启停 */
  const toggleStatus = async (row: CouponView) => {
    const target = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await changeCouponStatus(row.id, target);
      message.success(target === 'ACTIVE' ? '已启用' : '已停用');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 领取 */
  const claim = async (row: CouponView) => {
    try {
      await claimCoupon(row.id);
      message.success('领取成功，可在「我的券」查看');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<CouponView>['columns'] = [
    { title: '券编码', dataIndex: 'couponCode', width: 140 },
    { title: '券名称', dataIndex: 'couponName' },
    {
      title: '类型',
      dataIndex: 'couponType',
      width: 100,
      render: (value: string) => COUPON_TYPE_LABEL[value] ?? value,
    },
    {
      title: '面额',
      width: 190,
      render: (_, row) => formatCouponValue(row),
    },
    {
      title: '门槛',
      dataIndex: 'thresholdAmount',
      width: 120,
      render: (value?: number) => (value ? `满 ${formatMoney(value)}` : '无门槛'),
    },
    {
      title: '发行 / 已领',
      width: 120,
      render: (_, row) =>
        row.totalQty ? `${row.issuedQty ?? 0} / ${row.totalQty}` : `${row.issuedQty ?? 0} / 不限量`,
    },
    { title: '限领', dataIndex: 'perUserLimit', width: 80 },
    {
      title: '有效期',
      width: 210,
      render: (_, row) => `${formatDateTime(row.validFrom)} ~ ${formatDateTime(row.validTo)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={ENABLE_STATUS_COLOR[value]}>{COUPON_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '操作',
      width: 200,
      render: (_, row) => (
        <Space size="small">
          {canClaim && row.status === 'ACTIVE' && (
            <Button type="link" size="small" onClick={() => void claim(row)}>
              领取
            </Button>
          )}
          {canManage && (
            <>
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                编辑
              </Button>
              <Button type="link" size="small" onClick={() => void toggleStatus(row)}>
                {row.status === 'ACTIVE' ? '停用' : '启用'}
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Input.Search
          allowClear
          placeholder="编码 / 名称"
          style={{ width: 200 }}
          onSearch={(value) => {
            setFilter((prev) => ({ ...prev, keyword: value }));
            setPage((prev) => ({ ...prev, current: 1 }));
          }}
        />
        <Select
          allowClear
          placeholder="券类型"
          style={{ width: 140 }}
          options={TYPE_OPTIONS}
          onChange={(value?: string) => {
            setFilter((prev) => ({ ...prev, couponType: value }));
            setPage((prev) => ({ ...prev, current: 1 }));
          }}
        />
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 110 }}
          options={[
            { value: 'ACTIVE', label: '启用' },
            { value: 'DISABLED', label: '停用' },
          ]}
          onChange={(value?: string) => {
            setFilter((prev) => ({ ...prev, status: value }));
            setPage((prev) => ({ ...prev, current: 1 }));
          }}
        />
        {canManage && (
          <Button type="primary" onClick={openCreate}>
            新建优惠券
          </Button>
        )}
      </Space>

      <Table<CouponView>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />

      <Modal
        title={editing ? '编辑优惠券' : '新建优惠券'}
        open={modalOpen}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        width={560}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          改券只影响之后的领取：已领出的券按领取时的面额与门槛结算。折扣券必须设封顶，否则高价订单上的优惠额没人敢认。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            label="券编码"
            name="couponCode"
            rules={[{ required: true, message: '请填写券编码' }]}
          >
            <Input maxLength={64} placeholder="如 C-NEW-100-20" />
          </Form.Item>
          <Form.Item
            label="券名称"
            name="couponName"
            rules={[{ required: true, message: '请填写券名称' }]}
          >
            <Input maxLength={128} placeholder="如 满 100 减 20" />
          </Form.Item>
          <Form.Item label="券类型" name="couponType" rules={[{ required: true, message: '请选择券类型' }]}>
            <Select options={TYPE_OPTIONS} />
          </Form.Item>
          <Form.Item label="使用门槛（元）" name="thresholdAmount">
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          {couponType === 'DISCOUNT' ? (
            <>
              <Form.Item
                label="折扣率（0.85 表示 85 折）"
                name="discountRate"
                rules={[{ required: true, message: '请填写折扣率' }]}
              >
                <InputNumber min={0.01} max={0.99} step={0.05} precision={4} style={{ width: '100%' }} />
              </Form.Item>
              <Form.Item
                label="折扣封顶（元）"
                name="maxDiscountAmount"
                rules={[{ required: true, message: '折扣券必须设置封顶' }]}
              >
                <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
              </Form.Item>
            </>
          ) : (
            <Form.Item
              label="满减面额（元）"
              name="faceAmount"
              rules={[{ required: true, message: '请填写满减面额' }]}
            >
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item label="发行量（0 表示不限量）" name="totalQty">
            <InputNumber min={0} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="每人限领张数" name="perUserLimit">
            <InputNumber min={1} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            label="有效期"
            name="validRange"
            rules={[{ required: true, message: '请选择有效期' }]}
          >
            <DatePicker.RangePicker showTime style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}

/** 我的券 */
function MyCouponTab() {
  const [rows, setRows] = useState<CouponUserView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [status, setStatus] = useState<string | undefined>();

  /** 分页查询 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageMyCoupons({ status, current: page.current, size: page.size });
      setRows(result.records ?? []);
      setTotal(result.total ?? 0);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [status, page]);

  useEffect(() => {
    void load();
  }, [load]);

  const columns: TableProps<CouponUserView>['columns'] = [
    { title: '券名称', dataIndex: 'couponName' },
    {
      title: '面额',
      width: 190,
      render: (_, row) => formatCouponValue(row),
    },
    {
      title: '门槛',
      dataIndex: 'thresholdAmount',
      width: 120,
      render: (value?: number) => (value ? `满 ${formatMoney(value)}` : '无门槛'),
    },
    {
      title: '有效期',
      width: 210,
      render: (_, row) => `${formatDateTime(row.validFrom)} ~ ${formatDateTime(row.validTo)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={COUPON_USER_STATUS_COLOR[value]}>{COUPON_USER_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '核销订单',
      dataIndex: 'orderNo',
      width: 180,
      render: (value?: string) => value ?? '—',
    },
  ];

  return (
    <>
      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          allowClear
          placeholder="状态"
          style={{ width: 130 }}
          options={[
            { value: 'UNUSED', label: '未使用' },
            { value: 'USED', label: '已使用' },
            { value: 'EXPIRED', label: '已过期' },
          ]}
          onChange={(value?: string) => {
            setStatus(value);
            setPage((prev) => ({ ...prev, current: 1 }));
          }}
        />
      </Space>

      <Table<CouponUserView>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: page.current,
          pageSize: page.size,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (current, size) => setPage({ current, size }),
        }}
      />
    </>
  );
}
