// 商城渠道：形态 + 结算方式，是「一套内核三种形态」的唯一开关
//
// 后端强约束：WELFARE 只能配 POINTS，其余形态不得用 POINTS。
// 页面据此联动（选福利自动切积分并锁死），避免让用户提交一张必然被拒的表单。
import { useCallback, useEffect, useState } from 'react';
import {
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import {
  changeChannelStatus,
  createChannel,
  pageChannels,
  pageFreightTemplate,
  updateChannel,
  type ChannelSavePayload,
  type ChannelView,
  type FreightTemplateView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  CURRENCY_LABEL,
  ENABLE_STATUS_COLOR,
  ENABLE_STATUS_LABEL,
  MODE_COLOR,
  MODE_LABEL,
  SETTLEMENT_LABEL,
  formatDateTime,
} from '../../utils/dict';

/** 运费模板下拉上限：模板是低频配置，一次取全量比做远程搜索划算 */
const TEMPLATE_PAGE_SIZE = 100;

/** 形态下拉选项（取值以 /mall/meta 为准，这里只是中文文案） */
const MODE_OPTIONS = [
  { value: 'RETAIL', label: MODE_LABEL.RETAIL },
  { value: 'B2B', label: MODE_LABEL.B2B },
  { value: 'WELFARE', label: MODE_LABEL.WELFARE },
];

/** 结算方式下拉选项 */
const SETTLEMENT_OPTIONS = [
  { value: 'ONLINE', label: SETTLEMENT_LABEL.ONLINE },
  { value: 'CREDIT', label: SETTLEMENT_LABEL.CREDIT },
  { value: 'POINTS', label: SETTLEMENT_LABEL.POINTS },
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

export default function ChannelPage() {
  const [rows, setRows] = useState<ChannelView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState({ current: 1, size: 10 });
  const [filter, setFilter] = useState<{ keyword?: string; mode?: string; status?: string }>({});
  const [editing, setEditing] = useState<ChannelView | null>(null);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<ChannelSavePayload>();
  const [templates, setTemplates] = useState<FreightTemplateView[]>([]);
  const mode = Form.useWatch('mode', form);

  const canManage = hasPermission('sys:mall:channel:manage');

  /** 分页查询 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const result = await pageChannels({
        keyword: filter.keyword?.trim() || undefined,
        mode: filter.mode,
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

  // 运费模板只用于下拉选项，加载失败不该挡住渠道列表
  useEffect(() => {
    void (async () => {
      try {
        const result = await pageFreightTemplate({ current: 1, size: TEMPLATE_PAGE_SIZE, status: 'ACTIVE' });
        setTemplates(result.records ?? []);
      } catch {
        setTemplates([]);
      }
    })();
  }, []);

  /** 打开新建 */
  const openCreate = () => {
    setEditing(null);
    form.setFieldsValue({
      channelCode: '',
      channelName: '',
      mode: 'RETAIL',
      settlementType: 'ONLINE',
      freightTemplateId: undefined,
      remark: '',
    });
    setModalOpen(true);
  };

  /** 打开编辑 */
  const openEdit = (row: ChannelView) => {
    setEditing(row);
    form.setFieldsValue({
      channelCode: row.channelCode,
      channelName: row.channelName,
      mode: row.mode,
      settlementType: row.settlementType,
      freightTemplateId: row.freightTemplateId,
      remark: row.remark,
    });
    setModalOpen(true);
  };

  /** 形态变更：福利强制积分，其余形态不得选积分 */
  const handleModeChange = (value: string) => {
    if (value === 'WELFARE') {
      form.setFieldValue('settlementType', 'POINTS');
      return;
    }
    if (form.getFieldValue('settlementType') === 'POINTS') {
      form.setFieldValue('settlementType', 'ONLINE');
    }
  };

  /** 提交 */
  const submit = async () => {
    const values = await form.validateFields();
    setSaving(true);
    try {
      if (editing) {
        await updateChannel(editing.id, values);
        message.success('渠道已更新');
      } else {
        await createChannel(values);
        message.success('渠道已创建');
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
  const toggleStatus = async (row: ChannelView) => {
    const target = row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE';
    try {
      await changeChannelStatus(row.id, target);
      message.success(target === 'ACTIVE' ? '已启用' : '已停业');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<ChannelView>['columns'] = [
    { title: '渠道编码', dataIndex: 'channelCode', width: 160 },
    { title: '渠道名称', dataIndex: 'channelName' },
    {
      title: '形态',
      dataIndex: 'mode',
      width: 110,
      render: (value: string) => <Tag color={MODE_COLOR[value]}>{MODE_LABEL[value] ?? value}</Tag>,
    },
    {
      title: '结算方式',
      dataIndex: 'settlementType',
      width: 120,
      render: (value: string) => SETTLEMENT_LABEL[value] ?? value,
    },
    {
      title: '币种',
      dataIndex: 'currencyType',
      width: 100,
      render: (value: string) => CURRENCY_LABEL[value] ?? value,
    },
    {
      title: '运费模板',
      dataIndex: 'freightTemplateId',
      width: 150,
      render: (value?: number) =>
        value === undefined || value === null
          ? '不收运费'
          : templates.find((item) => item.id === value)?.templateName ?? `#${value}`,
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
      width: 160,
      render: (_, row) =>
        canManage ? (
          <Space size="small">
            <Button type="link" size="small" onClick={() => openEdit(row)}>
              编辑
            </Button>
            <Button type="link" size="small" onClick={() => void toggleStatus(row)}>
              {row.status === 'ACTIVE' ? '停业' : '启用'}
            </Button>
          </Space>
        ) : null,
    },
  ];

  return (
    <Card
      title="商城渠道"
      extra={
        <Space wrap>
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
            placeholder="形态"
            style={{ width: 140 }}
            options={MODE_OPTIONS}
            onChange={(value?: string) => {
              setFilter((prev) => ({ ...prev, mode: value }));
              setPage((prev) => ({ ...prev, current: 1 }));
            }}
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 110 }}
            options={[
              { value: 'ACTIVE', label: '启用' },
              { value: 'DISABLED', label: '停业' },
            ]}
            onChange={(value?: string) => {
              setFilter((prev) => ({ ...prev, status: value }));
              setPage((prev) => ({ ...prev, current: 1 }));
            }}
          />
          {canManage && (
            <Button type="primary" onClick={openCreate}>
              新建渠道
            </Button>
          )}
        </Space>
      }
    >
      <Table<ChannelView>
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
        title={editing ? '编辑渠道' : '新建渠道'}
        open={modalOpen}
        confirmLoading={saving}
        onCancel={() => setModalOpen(false)}
        onOk={submit}
        destroyOnClose
      >
        <Typography.Paragraph type="secondary">
          形态决定这一单按哪种价目计价：零售用零售价，经销缺经销价时回落零售价，福利必须配积分价且只能用积分结算。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            label="渠道编码"
            name="channelCode"
            rules={[{ required: true, message: '请填写渠道编码' }]}
          >
            <Input maxLength={64} placeholder="如 CH-RETAIL-01" />
          </Form.Item>
          <Form.Item
            label="渠道名称"
            name="channelName"
            rules={[{ required: true, message: '请填写渠道名称' }]}
          >
            <Input maxLength={128} placeholder="如 官方零售商城" />
          </Form.Item>
          <Form.Item label="形态" name="mode" rules={[{ required: true, message: '请选择形态' }]}>
            <Select options={MODE_OPTIONS} onChange={handleModeChange} />
          </Form.Item>
          <Form.Item
            label="结算方式"
            name="settlementType"
            rules={[{ required: true, message: '请选择结算方式' }]}
          >
            <Select
              options={SETTLEMENT_OPTIONS.filter((item) =>
                mode === 'WELFARE' ? item.value === 'POINTS' : item.value !== 'POINTS',
              )}
            />
          </Form.Item>
          <Form.Item
            label="运费模板"
            name="freightTemplateId"
            extra={mode === 'WELFARE' ? '积分商城不收运费，保存时会被清空' : '留空表示不收运费'}
          >
            <Select
              allowClear
              disabled={mode === 'WELFARE'}
              placeholder="不收运费"
              options={templates.map((item) => ({ value: item.id, label: item.templateName }))}
            />
          </Form.Item>
          <Form.Item label="备注" name="remark">
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
}
