// 归档规则管理页：流程定义 → 归档门类映射，支持自动归档开关
// 写操作按钮按权限码 sys:archive:rule:manage 渲染

import {
  App,
  Button,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Tag,
  type TableProps,
} from 'antd';
import { useEffect, useMemo, useRef, useState } from 'react';
import {
  AuthButton,
  ProTable,
  SearchForm,
  usePermission,
  type ProTableRef,
} from '@bluewhale/whale-ui';
import {
  categoryTree,
  createRule,
  deleteRule,
  pageRules,
  updateRule,
  type ArchiveCategoryVO,
  type ArchiveRuleSaveRequest,
  type ArchiveRuleVO,
} from '../../api/archive';
import {
  SECURITY_OPTIONS,
  flattenCategories,
  formatDateTime,
  securityLabel,
  type CategoryOption,
} from './helpers';

/** 规则查询条件 */
type RuleQuery = { definitionCode?: string; enabled?: number };

/** 规则表单值 */
interface RuleFormValues {
  definitionCode: string;
  categoryId: string;
  retentionYears?: number;
  securityLevel: number;
  autoArchive: boolean;
  enabled: boolean;
}

/** 默认表单值 */
const DEFAULT_VALUES: RuleFormValues = {
  definitionCode: '',
  categoryId: '',
  securityLevel: 2,
  autoArchive: false,
  enabled: true,
};

export default function ArchiveRuleList() {
  const { message } = App.useApp();
  const { has } = usePermission();
  const tableRef = useRef<ProTableRef>(null);
  const [form] = Form.useForm<RuleFormValues>();
  const [query, setQuery] = useState<RuleQuery>({});
  const [categories, setCategories] = useState<ArchiveCategoryVO[]>([]);
  const [modalOpen, setModalOpen] = useState(false);
  const [saving, setSaving] = useState(false);
  const [editing, setEditing] = useState<ArchiveRuleVO | null>(null);

  useEffect(() => {
    categoryTree()
      .then(setCategories)
      .catch((error: unknown) => {
        message.error(error instanceof Error ? error.message : '门类加载失败');
      });
  }, [message]);

  const categoryOptions = useMemo<CategoryOption[]>(
    () => flattenCategories(categories),
    [categories],
  );

  const openCreate = () => {
    setEditing(null);
    form.setFieldsValue(DEFAULT_VALUES);
    setModalOpen(true);
  };

  const openEdit = (row: ArchiveRuleVO) => {
    setEditing(row);
    form.setFieldsValue({
      definitionCode: row.definitionCode,
      categoryId: row.categoryId ?? '',
      retentionYears: row.retentionYears,
      securityLevel: row.securityLevel ?? 2,
      autoArchive: row.autoArchive === 1,
      enabled: row.enabled === 1,
    });
    setModalOpen(true);
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const payload: ArchiveRuleSaveRequest = {
      definitionCode: values.definitionCode.trim(),
      categoryId: values.categoryId,
      retentionYears: values.retentionYears,
      securityLevel: values.securityLevel,
      autoArchive: values.autoArchive ? 1 : 0,
      enabled: values.enabled ? 1 : 0,
    };
    setSaving(true);
    try {
      if (editing) {
        await updateRule(editing.id, payload);
        message.success('规则已更新');
      } else {
        await createRule(payload);
        message.success('规则已创建');
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '保存失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: ArchiveRuleVO) => {
    try {
      await deleteRule(row.id);
      message.success('规则已删除');
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '删除失败');
    }
  };

  const columns: TableProps<ArchiveRuleVO>['columns'] = [
    { title: '流程定义编码', dataIndex: 'definitionCode' },
    {
      title: '归档门类',
      dataIndex: 'categoryName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '保管期限',
      dataIndex: 'retentionYears',
      width: 110,
      render: (value?: number) => (value ? `${value} 年` : '长期'),
    },
    {
      title: '密级',
      dataIndex: 'securityLevel',
      width: 90,
      render: (value?: number) => <Tag>{securityLabel(value)}</Tag>,
    },
    {
      title: '自动归档',
      dataIndex: 'autoArchive',
      width: 100,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'green' : 'default'}>{value === 1 ? '是' : '否'}</Tag>
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 90,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'green' : 'default'}>{value === 1 ? '启用' : '停用'}</Tag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:archive:rule:manage') && (
            <>
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                修改
              </Button>
              <Popconfirm title="确认删除该规则？" onConfirm={() => void handleDelete(row)}>
                <Button type="link" size="small" danger>
                  删除
                </Button>
              </Popconfirm>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <>
      <ProTable<ArchiveRuleVO, RuleQuery>
        ref={tableRef}
        rowKey="id"
        title="归档规则"
        columns={columns}
        request={pageRules}
        params={{ definitionCode: query.definitionCode, enabled: query.enabled }}
        onError={(error) => message.error(error instanceof Error ? error.message : '规则加载失败')}
        search={
          <SearchForm
            items={[
              {
                name: 'definitionCode',
                label: '流程定义',
                element: <Input allowClear placeholder="流程定义编码" />,
              },
              {
                name: 'enabled',
                label: '启用状态',
                element: (
                  <Select
                    allowClear
                    placeholder="全部"
                    options={[
                      { label: '启用', value: 1 },
                      { label: '停用', value: 0 },
                    ]}
                  />
                ),
              },
            ]}
            onSearch={(values) =>
              setQuery({
                definitionCode: (values.definitionCode as string) || undefined,
                enabled: values.enabled as number | undefined,
              })
            }
            onReset={() => setQuery({})}
          />
        }
        toolbar={
          <AuthButton perm="sys:archive:rule:manage" type="primary" onClick={openCreate}>
            新增规则
          </AuthButton>
        }
      />
      <Modal
        title={editing ? '修改归档规则' : '新增归档规则'}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void handleSubmit()}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="definitionCode"
            label="流程定义编码"
            rules={[{ required: true, message: '请输入流程定义编码' }]}
          >
            <Input placeholder="如 purchase_contract" disabled={Boolean(editing)} />
          </Form.Item>
          <Form.Item
            name="categoryId"
            label="归档门类"
            rules={[{ required: true, message: '请选择归档门类' }]}
          >
            <Select options={categoryOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="retentionYears" label="保管期限（年，留空表示长期）">
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="securityLevel" label="密级">
            <Select options={SECURITY_OPTIONS} />
          </Form.Item>
          <Form.Item name="autoArchive" label="命中即自动归档" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
