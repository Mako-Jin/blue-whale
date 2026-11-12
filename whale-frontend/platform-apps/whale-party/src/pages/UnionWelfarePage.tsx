// 节日福利（M-P3）：批次 → 发放（全量或指定名单）→ 按人签收 → 关闭。
//
// 发放与签收分两步：发放生成名单并快照标准金额，签收才是「钱到人手里」的凭证。
// 明细抽屉顶部用接口总数做已签/未签对账，不拿当前页条数充数——翻页时对账数字必须稳定。
// 关闭后不可再签收，因此关闭按钮带 Popconfirm 明确终态。

import {
  Button,
  Card,
  Descriptions,
  Drawer,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Radio,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import { useCallback, useEffect, useState } from 'react';
import {
  closeUnionWelfare,
  createUnionWelfare,
  issueUnionWelfare,
  pageUnionWelfareItems,
  pageUnionWelfares,
  signUnionWelfareItem,
  updateUnionWelfare,
  type UnionWelfareItemView,
  type UnionWelfareSaveCommand,
  type UnionWelfareView,
} from '../api/party';
import { hasPermission } from '../api/client';

/** 每页条数 */
const PAGE_SIZE = 10;

/** 状态选项 */
const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿' },
  { value: 'ISSUED', label: '已发放' },
  { value: 'CLOSED', label: '已关闭' },
];

/** 状态标签与颜色 */
const STATUS_LABEL: Record<string, string> = {
  DRAFT: '草稿',
  ISSUED: '已发放',
  CLOSED: '已关闭',
};

/** 状态颜色 */
const STATUS_COLOR: Record<string, string> = {
  DRAFT: 'default',
  ISSUED: 'green',
  CLOSED: 'gray',
};

/** 签收状态选项 */
const SIGN_OPTIONS = [
  { value: 'UNSIGNED', label: '未签收' },
  { value: 'SIGNED', label: '已签收' },
];

/** 发放表单值 */
interface IssueFormValues {
  scope: 'ALL' | 'PARTIAL';
  memberIds?: string;
}

/**
 * 逗号 / 空格分隔的会员 ID 转数组。
 *
 * @param text 输入文本
 * @returns 会员 ID 数组（去重后）
 */
function parseMemberIds(text?: string): number[] {
  if (!text) {
    return [];
  }
  const ids = text
    .split(/[,，\s]+/)
    .map((part) => Number(part))
    .filter((value) => Number.isFinite(value) && value > 0);
  return Array.from(new Set(ids));
}

/**
 * 格式化时间。
 *
 * @param value 时间文本
 * @returns 展示用时间
 */
function formatTime(value?: string | null): string {
  return value ? dayjs(value).format('YYYY-MM-DD HH:mm') : '—';
}

/** 节日福利页 */
export default function UnionWelfarePage() {
  const [rows, setRows] = useState<UnionWelfareView[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<string | undefined>();
  const [year, setYear] = useState<number | undefined>();
  const [loading, setLoading] = useState(false);

  const [editing, setEditing] = useState<UnionWelfareView | null>(null);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm<UnionWelfareSaveCommand>();

  const [issueTarget, setIssueTarget] = useState<UnionWelfareView | null>(null);
  const [issueOpen, setIssueOpen] = useState(false);
  const [issueForm] = Form.useForm<IssueFormValues>();
  const issueScope = Form.useWatch('scope', issueForm);

  const [detailTarget, setDetailTarget] = useState<UnionWelfareView | null>(null);
  const [items, setItems] = useState<UnionWelfareItemView[]>([]);
  const [itemsTotal, setItemsTotal] = useState(0);
  const [itemCurrent, setItemCurrent] = useState(1);
  const [signStatus, setSignStatus] = useState<string | undefined>();
  const [itemsLoading, setItemsLoading] = useState(false);
  const [signedTotal, setSignedTotal] = useState(0);
  const [unsignedTotal, setUnsignedTotal] = useState(0);

  const canManage = hasPermission('sys:union:welfare:manage');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageUnionWelfares({
        keyword: keyword || undefined,
        year,
        status,
        current,
        size: PAGE_SIZE,
      });
      setRows(page.records);
      setTotal(Number(page.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '福利批次加载失败');
    } finally {
      setLoading(false);
    }
  }, [current, keyword, status, year]);

  useEffect(() => {
    void load();
  }, [load]);

  const loadItems = useCallback(async () => {
    if (!detailTarget) {
      return;
    }
    setItemsLoading(true);
    try {
      const [page, signed, unsigned] = await Promise.all([
        pageUnionWelfareItems(detailTarget.id, {
          signStatus,
          current: itemCurrent,
          size: PAGE_SIZE,
        }),
        pageUnionWelfareItems(detailTarget.id, { signStatus: 'SIGNED', current: 1, size: 1 }),
        pageUnionWelfareItems(detailTarget.id, { signStatus: 'UNSIGNED', current: 1, size: 1 }),
      ]);
      setItems(page.records);
      setItemsTotal(Number(page.total));
      setSignedTotal(Number(signed.total));
      setUnsignedTotal(Number(unsigned.total));
    } catch (err) {
      message.error(err instanceof Error ? err.message : '福利明细加载失败');
    } finally {
      setItemsLoading(false);
    }
  }, [detailTarget, itemCurrent, signStatus]);

  useEffect(() => {
    void loadItems();
  }, [loadItems]);

  const openCreate = () => {
    setEditing(null);
    form.resetFields();
    form.setFieldsValue({ welfareYear: new Date().getFullYear(), standardAmount: 0 });
    setOpen(true);
  };

  const openEdit = (row: UnionWelfareView) => {
    setEditing(row);
    form.setFieldsValue({
      title: row.title,
      welfareYear: row.welfareYear,
      standardAmount: row.standardAmount,
      remark: row.remark ?? undefined,
    });
    setOpen(true);
  };

  const submitEditor = async () => {
    const values = await form.validateFields();
    const payload: UnionWelfareSaveCommand = {
      title: values.title,
      welfareYear: values.welfareYear,
      standardAmount: values.standardAmount,
      remark: values.remark ?? null,
    };
    try {
      if (editing) {
        await updateUnionWelfare(editing.id, payload);
        message.success('福利批次已更新');
      } else {
        await createUnionWelfare(payload);
        message.success('福利批次草稿已创建');
      }
      setOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '保存失败');
    }
  };

  const openIssue = (row: UnionWelfareView) => {
    setIssueTarget(row);
    issueForm.resetFields();
    issueForm.setFieldsValue({ scope: 'ALL' });
    setIssueOpen(true);
  };

  const submitIssue = async () => {
    if (!issueTarget) {
      return;
    }
    const values = await issueForm.validateFields();
    const ids = values.scope === 'PARTIAL' ? parseMemberIds(values.memberIds) : [];
    if (values.scope === 'PARTIAL' && ids.length === 0) {
      message.warning('未填写会员 ID，将按全量在册会员发放');
    }
    try {
      const view = await issueUnionWelfare(issueTarget.id, ids.length > 0 ? { memberIds: ids } : {});
      message.success(
        view.issueCount !== null && view.issueCount !== undefined
          ? `已发放，共 ${view.issueCount} 人`
          : '已发放',
      );
      setIssueOpen(false);
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '发放失败');
    }
  };

  const openDetail = (row: UnionWelfareView) => {
    setItemCurrent(1);
    setSignStatus(undefined);
    setDetailTarget(row);
  };

  const signItem = async (item: UnionWelfareItemView) => {
    try {
      await signUnionWelfareItem(item.id);
      message.success(`${item.memberName} 已签收`);
      await loadItems();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '签收失败');
    }
  };

  const close = async (row: UnionWelfareView) => {
    try {
      await closeUnionWelfare(row.id);
      message.success('批次已关闭，历史明细保留');
      await load();
    } catch (err) {
      message.error(err instanceof Error ? err.message : '关闭失败');
    }
  };

  const columns: ColumnsType<UnionWelfareView> = [
    { title: '批次编号', dataIndex: 'welfareNo', width: 130 },
    { title: '福利名称', dataIndex: 'title', ellipsis: true },
    { title: '年度', dataIndex: 'welfareYear', width: 80 },
    {
      title: '标准金额',
      dataIndex: 'standardAmount',
      width: 110,
      render: (value: number) => `¥${Number(value ?? 0).toFixed(2)}`,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (value: string) => (
        <Tag color={STATUS_COLOR[value] ?? 'default'}>{STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '发放人数',
      dataIndex: 'issueCount',
      width: 90,
      render: (value?: number | null) => (value === null || value === undefined ? '—' : `${value} 人`),
    },
    {
      title: '发放时间',
      dataIndex: 'issuedAt',
      width: 160,
      render: (value?: string | null) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      render: (_value, row) => (
        <Space size={4} wrap>
          {canManage && row.status === 'DRAFT' ? (
            <>
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                编辑
              </Button>
              <Button type="link" size="small" onClick={() => openIssue(row)}>
                发放
              </Button>
            </>
          ) : null}
          {row.status !== 'DRAFT' ? (
            <Button type="link" size="small" onClick={() => openDetail(row)}>
              明细
            </Button>
          ) : null}
          {canManage && row.status === 'ISSUED' ? (
            <Popconfirm
              title="关闭后不可再签收，历史明细保留，确认？"
              onConfirm={() => void close(row)}
            >
              <Button type="link" size="small" danger>
                关闭
              </Button>
            </Popconfirm>
          ) : null}
        </Space>
      ),
    },
  ];

  const itemColumns: ColumnsType<UnionWelfareItemView> = [
    { title: '姓名', dataIndex: 'memberName', width: 110 },
    { title: '工号', dataIndex: 'employeeNo', width: 110 },
    {
      title: '金额',
      dataIndex: 'amount',
      width: 100,
      render: (value: number) => `¥${Number(value ?? 0).toFixed(2)}`,
    },
    {
      title: '签收状态',
      dataIndex: 'signStatus',
      width: 100,
      render: (value: string) => (
        <Tag color={value === 'SIGNED' ? 'green' : 'orange'}>
          {value === 'SIGNED' ? '已签收' : '未签收'}
        </Tag>
      ),
    },
    {
      title: '签收时间',
      dataIndex: 'signedAt',
      width: 160,
      render: (value?: string | null) => formatTime(value),
    },
    {
      title: '操作',
      key: 'actions',
      width: 90,
      render: (_value, row) =>
        canManage && detailTarget?.status === 'ISSUED' && row.signStatus === 'UNSIGNED' ? (
          <Popconfirm title={`确认 ${row.memberName} 已签收？`} onConfirm={() => void signItem(row)}>
            <Button type="link" size="small">
              签收
            </Button>
          </Popconfirm>
        ) : (
          '—'
        ),
    },
  ];

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card
        title="节日福利"
        size="small"
        extra={
          <Space wrap>
            <Input.Search
              allowClear
              placeholder="批次编号 / 福利名称"
              style={{ width: 200 }}
              onSearch={(value) => {
                setCurrent(1);
                setKeyword(value.trim());
              }}
            />
            <Select
              allowClear
              placeholder="全部状态"
              style={{ width: 110 }}
              options={STATUS_OPTIONS}
              value={status}
              onChange={(value) => {
                setCurrent(1);
                setStatus(value);
              }}
            />
            <InputNumber
              placeholder="年度"
              min={2000}
              max={2100}
              style={{ width: 100 }}
              value={year ?? null}
              onChange={(value) => {
                setCurrent(1);
                setYear(typeof value === 'number' ? value : undefined);
              }}
            />
            {canManage ? (
              <Button type="primary" onClick={openCreate}>
                新建批次
              </Button>
            ) : null}
          </Space>
        }
      >
        <Table
          rowKey="id"
          size="small"
          loading={loading}
          columns={columns}
          dataSource={rows}
          scroll={{ x: 1100 }}
          pagination={{
            current,
            pageSize: PAGE_SIZE,
            total,
            showSizeChanger: false,
            onChange: setCurrent,
          }}
        />
      </Card>

      <Drawer
        title={editing ? '编辑福利批次' : '新建福利批次'}
        width={520}
        open={open}
        onClose={() => setOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setOpen(false)}>取消</Button>
            <Button type="primary" onClick={() => void submitEditor()}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="title"
            label="福利名称"
            rules={[{ required: true, message: '请输入福利名称' }]}
          >
            <Input maxLength={128} placeholder="如：2026 年中秋慰问品" />
          </Form.Item>
          <Form.Item
            name="welfareYear"
            label="福利年度"
            rules={[{ required: true, message: '请输入年度' }]}
          >
            <InputNumber min={2000} max={2100} precision={0} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item
            name="standardAmount"
            label="标准金额（发放时快照到明细）"
            rules={[{ required: true, message: '请输入标准金额' }]}
          >
            <InputNumber min={0} precision={2} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={3} maxLength={255} showCount />
          </Form.Item>
        </Form>
      </Drawer>

      <Modal
        title={`发放 · ${issueTarget?.title ?? ''}`}
        open={issueOpen}
        onCancel={() => setIssueOpen(false)}
        onOk={() => void submitIssue()}
        okText="确认发放"
        destroyOnClose
      >
        <Form form={issueForm} layout="vertical" preserve={false}>
          <Form.Item name="scope" label="发放范围" rules={[{ required: true }]}>
            <Radio.Group
              options={[
                { value: 'ALL', label: '全量在册会员' },
                { value: 'PARTIAL', label: '指定会员' },
              ]}
            />
          </Form.Item>
          {issueScope === 'PARTIAL' ? (
            <Form.Item
              name="memberIds"
              label="会员 ID（逗号分隔）"
              extra="留空等同全量；重复生成名单会幂等跳过已有明细"
            >
              <Input.TextArea rows={3} placeholder="如：12,15,18" />
            </Form.Item>
          ) : null}
        </Form>
      </Modal>

      <Drawer
        title={`福利明细 · ${detailTarget?.title ?? ''}`}
        width={760}
        open={detailTarget !== null}
        onClose={() => setDetailTarget(null)}
      >
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Descriptions size="small" column={3}>
            <Descriptions.Item label="批次编号">{detailTarget?.welfareNo ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="年度">{detailTarget?.welfareYear ?? '—'}</Descriptions.Item>
            <Descriptions.Item label="标准金额">
              {detailTarget ? `¥${Number(detailTarget.standardAmount ?? 0).toFixed(2)}` : '—'}
            </Descriptions.Item>
          </Descriptions>
          <Space size={8} wrap>
            <Tag color="green">已签 {signedTotal}</Tag>
            <Tag color="orange">未签 {unsignedTotal}</Tag>
            <Typography.Text type="secondary">合计 {signedTotal + unsignedTotal} 人</Typography.Text>
            <Select
              allowClear
              placeholder="全部签收状态"
              style={{ width: 140 }}
              options={SIGN_OPTIONS}
              value={signStatus}
              onChange={(value) => {
                setItemCurrent(1);
                setSignStatus(value);
              }}
            />
          </Space>
          <Table
            rowKey="id"
            size="small"
            loading={itemsLoading}
            columns={itemColumns}
            dataSource={items}
            scroll={{ x: 700 }}
            pagination={{
              current: itemCurrent,
              pageSize: PAGE_SIZE,
              total: itemsTotal,
              showSizeChanger: false,
              onChange: setItemCurrent,
            }}
          />
        </Space>
      </Drawer>
    </Space>
  );
}
