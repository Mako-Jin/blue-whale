// 运费模板：渠道级计费规则，挂在渠道上（/mall/channel 的 freightTemplateId）
//
// 只支持三种能一眼算清的规则：全免 / 固定 / 满额包邮。按件数、按重量、
// 多模板累加都需要商品级模板与叠加计算，属于另一套模型，页面刻意不提供入口。
import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Form, Input, InputNumber, Modal, Select, Space, Table, Tag, Typography, message } from 'antd';
import type { TableProps } from 'antd';
import {
  changeFreightTemplateStatus,
  createFreightTemplate,
  pageFreightTemplate,
  updateFreightTemplate,
  type FreightTemplateSaveBody,
  type FreightTemplateView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  ENABLE_STATUS_COLOR,
  ENABLE_STATUS_LABEL,
  FREIGHT_CHARGE_TYPE_LABEL,
  formatDateTime,
  formatMoney,
} from '../../utils/dict';

/** 计费方式下拉选项 */
const CHARGE_TYPE_OPTIONS = [
  { value: 'FREE', label: FREIGHT_CHARGE_TYPE_LABEL.FREE },
  { value: 'FIXED', label: FREIGHT_CHARGE_TYPE_LABEL.FIXED },
  { value: 'THRESHOLD', label: FREIGHT_CHARGE_TYPE_LABEL.THRESHOLD },
];

/**
 * 读取错误文案。
 *
 * @param error 异常对象
 * @returns 文案
 */
function errorText(error: unknown): string {
  return error instanceof Error ? error.message : '操作失败';
}

export default function FreightTemplatePage() {
  const [rows, setRows] = useState<FreightTemplateView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [filter, setFilter] = useState<{ keyword?: string; chargeType?: string; status?: string }>({});
  const [editing, setEditing] = useState<FreightTemplateView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<FreightTemplateSaveBody>();
  const chargeType = Form.useWatch('chargeType', form);

  const canManage = hasPermission('sys:mall:freight:manage');

  /** 分页查询 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageFreightTemplate({
        keyword: filter.keyword?.trim() || undefined,
        chargeType: filter.chargeType,
        status: filter.status,
        current: page.current,
        size: page.size,
      });
      setRows(result.records ?? []);
      setTotal(Number(result.total ?? 0));
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
      templateName: '',
      chargeType: 'THRESHOLD',
      baseAmount: 12,
      freeThreshold: 99,
      status: 'ACTIVE',
      remark: '',
    });
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (row: FreightTemplateView) => {
    setEditing(row);
    form.setFieldsValue({
      templateName: row.templateName,
      chargeType: row.chargeType,
      baseAmount: row.baseAmount,
      freeThreshold: row.freeThreshold,
      status: row.status,
      remark: row.remark,
    });
    setModalOpen(true);
  };

  /** 提交 */
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateFreightTemplate(editing.id, values);
        message.success('运费模板已更新');
      } else {
        await createFreightTemplate(values);
        message.success('运费模板已创建');
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
  const toggleStatus = async (row: FreightTemplateView) => {
    const target = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await changeFreightTemplateStatus(row.id, target);
      message.success(target === 'ACTIVE' ? '已启用' : '已停用');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<FreightTemplateView>['columns'] = [
    { title: '模板名称', dataIndex: 'templateName' },
    {
      title: '计费方式',
      dataIndex: 'chargeType',
      width: 130,
      render: (value: string) => FREIGHT_CHARGE_TYPE_LABEL[value] ?? value,
    },
    {
      title: '运费金额',
      dataIndex: 'baseAmount',
      width: 120,
      render: (value: number | undefined, row) =>
        row.chargeType === 'FREE' ? '—' : formatMoney(value ?? 0),
    },
    {
      title: '包邮门槛',
      dataIndex: 'freeThreshold',
      width: 130,
      render: (value: number | undefined, row) =>
        row.chargeType === 'THRESHOLD' && value !== undefined ? `满 ${formatMoney(value)}` : '—',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={ENABLE_STATUS_COLOR[value]}>{ENABLE_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      width: 150,
      render: (_, row) =>
        canManage ? (
          <Space size="small">
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
            <Button type="link" size="small" onClick={() => void toggleStatus(row)}>
              {row.status === 'ACTIVE' ? '停用' : '启用'}
            </Button>
          </Space>
        ) : null,
    },
  ];

  return (
    <Card
      title="运费模板"
      extra={
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="模板名称"
            style={{ width: 180 }}
            onSearch={(value) => {
              setFilter((prev) => ({ ...prev, keyword: value }));
              setPage((prev) => ({ ...prev, current: 1 }));
            }}
          />
          <Select
            allowClear
            placeholder="计费方式"
            style={{ width: 140 }}
            options={CHARGE_TYPE_OPTIONS}
            onChange={(value?: string) => {
              setFilter((prev) => ({ ...prev, chargeType: value }));
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
              新建模板
            </Button>
          )}
        </Space>
      }
    >
      <Table<FreightTemplateView>
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
        title={editing ? '编辑运费模板' : '新建运费模板'}
        open={modalOpen}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          模板创建后在「商城渠道」里绑定。包邮门槛按商品总额（用券前）判定——用券后跌破门槛反而多付运费，是最容易被投诉的交互。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            label="模板名称"
            name="templateName"
            rules={[{ required: true, message: '请填写模板名称' }]}
          >
            <Input maxLength={64} placeholder="如 零售满 99 包邮" />
          </Form.Item>
          <Form.Item label="计费方式" name="chargeType" rules={[{ required: true, message: '请选择计费方式' }]}>
            <Select options={CHARGE_TYPE_OPTIONS} />
          </Form.Item>
          {chargeType !== 'FREE' && (
            <Form.Item
              label="运费金额（元）"
              name="baseAmount"
              rules={[{ required: true, message: '请填写运费金额' }]}
            >
              <InputNumber min={0} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          )}
          {chargeType === 'THRESHOLD' && (
            <Form.Item
              label="包邮门槛（元）"
              name="freeThreshold"
              rules={[{ required: true, message: '请填写包邮门槛' }]}
            >
              <InputNumber min={0.01} precision={2} style={{ width: '100%' }} />
            </Form.Item>
          )}
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
