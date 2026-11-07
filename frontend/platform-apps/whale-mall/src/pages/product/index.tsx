// 商城商品：分页检索、新建/编辑（SPU + SKU + 三档价目）、上下架、删除
//
// 规格在界面上按「一段文本」填写，提交时包成 `{"规格": "..."}` 的 JSON——
// 后端 specJson 是自由结构，让用户手写 JSON 只会得到一堆语法错误。
// 价目按零售 / 经销 / 积分三档固定展示，留空即不提交该档（福利形态缺积分价会被后端拒绝）。
import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Button,
  Card,
  Drawer,
  Form,
  Input,
  InputNumber,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Typography,
  message,
} from 'antd';
import type { TableProps } from 'antd';
import { useNavigate } from 'react-router-dom';
import {
  changeProductStatus,
  createProduct,
  fetchCategoryTree,
  getProduct,
  pageProducts,
  removeProduct,
  updateProduct,
  type CategoryNode,
  type ProductQuery,
  type SkuPayload,
  type SpuSavePayload,
  type SpuView,
} from '../../api/mall';
import { hasPermission } from '../../utils/permission';
import {
  ENABLE_STATUS_OPTIONS,
  PRICE_TYPE_LABEL,
  SPU_STATUS_COLOR,
  SPU_STATUS_LABEL,
  SPU_STATUS_OPTIONS,
  formatDateTime,
  formatSpec,
} from '../../utils/dict';

/** SKU 表单行：三档价目平铺成三个字段，提交时再拼回 prices 数组 */
interface SkuFormRow {
  id?: number | null;
  skuCode: string;
  specText?: string;
  status?: string;
  sortNo?: number | null;
  retailPrice?: number | null;
  b2bPrice?: number | null;
  pointsPrice?: number | null;
}

/** 商品表单值 */
interface ProductFormValues {
  spuCode: string;
  spuName: string;
  categoryId: number;
  brand?: string;
  mainImage?: string;
  imagesText?: string;
  detail?: string;
  sortNo?: number | null;
  remark?: string;
  skus: SkuFormRow[];
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

/**
 * 类目树拍平为下拉选项（带层级缩进）。
 *
 * @param nodes 类目树
 * @param depth 深度
 * @returns 选项
 */
function categoryOptions(nodes: CategoryNode[], depth = 0): { value: number; label: string }[] {
  const options: { value: number; label: string }[] = [];
  nodes.forEach((node) => {
    options.push({ value: node.id, label: `${'　'.repeat(depth)}${node.categoryName}` });
    if (node.children && node.children.length > 0) {
      options.push(...categoryOptions(node.children, depth + 1));
    }
  });
  return options;
}

/**
 * 规格文本 → specJson（空文本返回 undefined）。
 *
 * @param text 规格文本
 * @returns JSON 字符串
 */
function toSpecJson(text?: string): string | undefined {
  const trimmed = text?.trim();
  return trimmed ? JSON.stringify({ 规格: trimmed }) : undefined;
}

export default function ProductPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<SpuView[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [query, setQuery] = useState<ProductQuery>({ current: 1, size: 10 });
  const [categoryTree, setCategoryTree] = useState<CategoryNode[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<ProductFormValues>();

  const canManage = hasPermission('sys:mall:product:manage');
  const canPublish = hasPermission('sys:mall:product:publish');

  /** 分页查询 */
  const load = useCallback(async () => {
    setLoading(true);
    try {
      const page = await pageProducts({
        keyword: query.keyword?.trim() || undefined,
        categoryId: query.categoryId ?? undefined,
        status: query.status,
        current: query.current,
        size: query.size,
      });
      setRows(page.records ?? []);
      setTotal(page.total ?? 0);
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setLoading(false);
    }
  }, [query]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    fetchCategoryTree()
      .then(setCategoryTree)
      .catch(() => setCategoryTree([]));
  }, []);

  const options = useMemo(() => categoryOptions(categoryTree), [categoryTree]);

  /** 打开新建 */
  const openCreate = () => {
    setEditingId(null);
    form.setFieldsValue({
      spuCode: '',
      spuName: '',
      categoryId: undefined,
      brand: '',
      mainImage: '',
      imagesText: '',
      detail: '',
      sortNo: 0,
      remark: '',
      skus: [{ skuCode: '', specText: '', status: 'ACTIVE', sortNo: 0 }],
    });
    setDrawerOpen(true);
  };

  /** 打开编辑（先取详情，权限 / 存在性由后端判定） */
  const openEdit = async (id: number) => {
    try {
      const detail = await getProduct(id);
      setEditingId(id);
      form.setFieldsValue({
        spuCode: detail.spuCode,
        spuName: detail.spuName,
        categoryId: detail.categoryId,
        brand: detail.brand,
        mainImage: detail.mainImage,
        imagesText: (detail.images ?? []).join('\n'),
        detail: detail.detail,
        sortNo: detail.sortNo,
        remark: detail.remark,
        skus: (detail.skus ?? []).map((sku) => ({
          id: sku.id,
          skuCode: sku.skuCode,
          specText: formatSpec(sku.specJson) === '-' ? '' : formatSpec(sku.specJson),
          status: sku.status,
          sortNo: sku.sortNo,
          retailPrice: sku.prices?.find((item) => item.priceType === 'RETAIL')?.price ?? null,
          b2bPrice: sku.prices?.find((item) => item.priceType === 'B2B')?.price ?? null,
          pointsPrice: sku.prices?.find((item) => item.priceType === 'POINTS')?.price ?? null,
        })),
      });
      setDrawerOpen(true);
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 保存（新建 / 编辑） */
  const submit = async () => {
    const values = await form.validateFields();
    const skus: SkuPayload[] = (values.skus ?? []).map((row) => {
      const prices = [
        { priceType: 'RETAIL', price: row.retailPrice },
        { priceType: 'B2B', price: row.b2bPrice },
        { priceType: 'POINTS', price: row.pointsPrice },
      ]
        .filter((item) => item.price !== null && item.price !== undefined)
        .map((item) => ({ priceType: item.priceType, price: Number(item.price) }));
      return {
        id: row.id ?? null,
        skuCode: row.skuCode,
        specJson: toSpecJson(row.specText),
        status: row.status,
        sortNo: row.sortNo ?? 0,
        prices,
      };
    });
    const payload: SpuSavePayload = {
      spuCode: values.spuCode,
      spuName: values.spuName,
      categoryId: values.categoryId,
      brand: values.brand,
      mainImage: values.mainImage,
      images: (values.imagesText ?? '')
        .split('\n')
        .map((item) => item.trim())
        .filter(Boolean),
      detail: values.detail,
      sortNo: values.sortNo ?? 0,
      remark: values.remark,
      skus,
    };
    setSaving(true);
    try {
      if (editingId) {
        await updateProduct(editingId, payload);
        message.success('商品已更新');
      } else {
        await createProduct(payload);
        message.success('商品已创建');
      }
      setDrawerOpen(false);
      await load();
    } catch (error) {
      message.error(errorText(error));
    } finally {
      setSaving(false);
    }
  };

  /** 上下架 */
  const toggleStatus = async (row: SpuView) => {
    const target = row.status === 'ON_SALE' ? 'OFF_SALE' : 'ON_SALE';
    try {
      await changeProductStatus(row.id, target);
      message.success(target === 'ON_SALE' ? '已上架' : '已下架');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  /** 删除 */
  const handleRemove = async (id: number) => {
    try {
      await removeProduct(id);
      message.success('商品已删除');
      await load();
    } catch (error) {
      message.error(errorText(error));
    }
  };

  const columns: TableProps<SpuView>['columns'] = [
    { title: '商品编码', dataIndex: 'spuCode', width: 140 },
    {
      title: '商品名称',
      dataIndex: 'spuName',
      render: (value: string, row) => (
        <Typography.Link onClick={() => navigate(`/product/${row.id}`)}>{value}</Typography.Link>
      ),
    },
    { title: '类目', dataIndex: 'categoryName', width: 140, render: (value?: string) => value || '-' },
    { title: '品牌', dataIndex: 'brand', width: 120, render: (value?: string) => value || '-' },
    {
      title: '状态',
      dataIndex: 'status',
      width: 100,
      render: (value: string) => (
        <Tag color={SPU_STATUS_COLOR[value]}>{SPU_STATUS_LABEL[value] ?? value}</Tag>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      width: 220,
      render: (_, row) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => navigate(`/product/${row.id}`)}>
            详情
          </Button>
          {canManage && (
            <Button type="link" size="small" onClick={() => void openEdit(row.id)}>
              编辑
            </Button>
          )}
          {canPublish && (
            <Button type="link" size="small" onClick={() => void toggleStatus(row)}>
              {row.status === 'ON_SALE' ? '下架' : '上架'}
            </Button>
          )}
          {canManage && (
            <Popconfirm title="删除商品" description="在售商品不可删除" onConfirm={() => handleRemove(row.id)}>
              <Button type="link" size="small" danger>
                删除
              </Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title="商城商品"
      extra={
        <Space wrap>
          <Input.Search
            allowClear
            placeholder="编码 / 名称"
            style={{ width: 200 }}
            onSearch={(value) => setQuery((prev) => ({ ...prev, keyword: value, current: 1 }))}
          />
          <Select
            allowClear
            showSearch
            optionFilterProp="label"
            placeholder="类目"
            style={{ width: 180 }}
            options={options}
            onChange={(value?: number) =>
              setQuery((prev) => ({ ...prev, categoryId: value ?? null, current: 1 }))
            }
          />
          <Select
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            options={SPU_STATUS_OPTIONS}
            onChange={(value?: string) => setQuery((prev) => ({ ...prev, status: value, current: 1 }))}
          />
          {canManage && (
            <Button type="primary" onClick={openCreate}>
              新建商品
            </Button>
          )}
        </Space>
      }
    >
      <Table<SpuView>
        rowKey="id"
        size="middle"
        loading={loading}
        columns={columns}
        dataSource={rows}
        pagination={{
          current: query.current,
          pageSize: query.size,
          total,
          showSizeChanger: true,
          showTotal: (value) => `共 ${value} 条`,
          onChange: (current, size) => setQuery((prev) => ({ ...prev, current, size })),
        }}
      />

      <Drawer
        title={editingId ? '编辑商品' : '新建商品'}
        width={860}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        extra={
          <Space>
            <Button onClick={() => setDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={saving} onClick={submit}>
              保存
            </Button>
          </Space>
        }
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item
              label="商品编码"
              name="spuCode"
              rules={[{ required: true, message: '请填写商品编码' }]}
              style={{ flex: 1 }}
            >
              <Input maxLength={64} placeholder="全局唯一，如 SP-1001" />
            </Form.Item>
            <Form.Item
              label="商品名称"
              name="spuName"
              rules={[{ required: true, message: '请填写商品名称' }]}
              style={{ flex: 2 }}
            >
              <Input maxLength={128} />
            </Form.Item>
          </Space>
          <Space size="middle" style={{ display: 'flex' }} align="start">
            <Form.Item
              label="所属类目"
              name="categoryId"
              rules={[{ required: true, message: '请选择类目' }]}
              style={{ flex: 1 }}
            >
              <Select showSearch optionFilterProp="label" options={options} />
            </Form.Item>
            <Form.Item label="品牌" name="brand" style={{ flex: 1 }}>
              <Input maxLength={64} />
            </Form.Item>
            <Form.Item label="排序" name="sortNo" style={{ width: 120 }}>
              <InputNumber min={0} max={9999} style={{ width: '100%' }} />
            </Form.Item>
          </Space>
          <Form.Item label="主图 URL" name="mainImage">
            <Input maxLength={512} placeholder="https://..." />
          </Form.Item>
          <Form.Item label="轮播图（每行一个 URL）" name="imagesText">
            <Input.TextArea rows={2} />
          </Form.Item>
          <Form.Item label="商品详情" name="detail">
            <Input.TextArea rows={3} maxLength={2000} />
          </Form.Item>

          <Typography.Title level={5} style={{ marginTop: 8 }}>
            规格与价目
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>
            价目留空即不提交该档；经销形态缺经销价会回落零售价，福利形态缺积分价会被后端直接拒绝。
          </Typography.Paragraph>

          <Form.List name="skus">
            {(fields, { add, remove }) => (
              <>
                {fields.map((field) => (
                  <Card key={field.key} size="small" style={{ marginBottom: 12 }}>
                    <Space size="middle" style={{ display: 'flex' }} align="start" wrap>
                      <Form.Item
                        label="SKU 编码"
                        name={[field.name, 'skuCode']}
                        rules={[{ required: true, message: '必填' }]}
                        style={{ width: 180 }}
                      >
                        <Input maxLength={64} placeholder="如 SKU-1001-BK" />
                      </Form.Item>
                      <Form.Item label="规格" name={[field.name, 'specText']} style={{ width: 200 }}>
                        <Input maxLength={64} placeholder="如 黑色 / M" />
                      </Form.Item>
                      <Form.Item label="状态" name={[field.name, 'status']} style={{ width: 110 }}>
                        <Select options={ENABLE_STATUS_OPTIONS} />
                      </Form.Item>
                      <Form.Item label={PRICE_TYPE_LABEL.RETAIL} name={[field.name, 'retailPrice']} style={{ width: 130 }}>
                        <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item label={PRICE_TYPE_LABEL.B2B} name={[field.name, 'b2bPrice']} style={{ width: 130 }}>
                        <InputNumber min={0} precision={2} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item label={PRICE_TYPE_LABEL.POINTS} name={[field.name, 'pointsPrice']} style={{ width: 130 }}>
                        <InputNumber min={0} precision={0} style={{ width: '100%' }} />
                      </Form.Item>
                      <Form.Item label=" ">
                        <Button danger onClick={() => remove(field.name)}>
                          删除规格
                        </Button>
                      </Form.Item>
                    </Space>
                    <Form.Item name={[field.name, 'id']} hidden>
                      <Input />
                    </Form.Item>
                  </Card>
                ))}
                <Button block type="dashed" onClick={() => add({ status: 'ACTIVE', sortNo: 0 })}>
                  添加规格
                </Button>
              </>
            )}
          </Form.List>

          <Form.Item label="备注" name="remark" style={{ marginTop: 16 }}>
            <Input.TextArea rows={2} maxLength={255} />
          </Form.Item>
        </Form>
      </Drawer>
    </Card>
  );
}
