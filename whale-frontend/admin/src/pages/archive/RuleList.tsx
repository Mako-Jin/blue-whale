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
import { useI18n } from '../../i18n';
import {
  enabledOptions,
  flattenCategories,
  formatDateTime,
  securityLabel,
  securityOptions,
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
  const { t } = useI18n();
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
        message.error(
          error instanceof Error ? error.message : t('archive.common.categoryLoadFailed'),
        );
      });
  }, [message, t]);

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
        message.success(t('archive.rule.msg.updated'));
      } else {
        await createRule(payload);
        message.success(t('archive.rule.msg.created'));
      }
      setModalOpen(false);
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.common.saveFailed'));
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (row: ArchiveRuleVO) => {
    try {
      await deleteRule(row.id);
      message.success(t('archive.rule.msg.deleted'));
      tableRef.current?.reload();
    } catch (error) {
      message.error(error instanceof Error ? error.message : t('archive.common.deleteFailed'));
    }
  };

  const columns: TableProps<ArchiveRuleVO>['columns'] = [
    { title: t('archive.rule.col.definitionCode'), dataIndex: 'definitionCode' },
    {
      title: t('archive.rule.col.category'),
      dataIndex: 'categoryName',
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: t('archive.common.retention'),
      dataIndex: 'retentionYears',
      width: 110,
      render: (value?: number) =>
        value ? t('archive.common.years', { years: value }) : t('archive.common.longTerm'),
    },
    {
      title: t('archive.common.securityLevel'),
      dataIndex: 'securityLevel',
      width: 90,
      render: (value?: number) => <Tag>{securityLabel(value, t)}</Tag>,
    },
    {
      title: t('archive.rule.col.autoArchive'),
      dataIndex: 'autoArchive',
      width: 100,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'green' : 'default'}>
          {value === 1 ? t('archive.common.yes') : t('archive.common.no')}
        </Tag>
      ),
    },
    {
      title: t('archive.common.enabled'),
      dataIndex: 'enabled',
      width: 90,
      render: (value?: number) => (
        <Tag color={value === 1 ? 'green' : 'default'}>
          {value === 1 ? t('archive.common.enabled') : t('archive.common.disabled')}
        </Tag>
      ),
    },
    {
      title: t('archive.rule.col.createTime'),
      dataIndex: 'createTime',
      width: 180,
      render: (value?: string) => formatDateTime(value),
    },
    {
      title: t('archive.common.action'),
      key: 'action',
      width: 140,
      render: (_, row) => (
        <Space size={4}>
          {has('sys:archive:rule:manage') && (
            <>
              <Button type="link" size="small" onClick={() => openEdit(row)}>
                {t('archive.common.edit')}
              </Button>
              <Popconfirm
                title={t('archive.rule.confirm.delete')}
                onConfirm={() => void handleDelete(row)}
              >
                <Button type="link" size="small" danger>
                  {t('archive.common.delete')}
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
        title={t('archive.rule.title')}
        columns={columns}
        request={pageRules}
        params={{ definitionCode: query.definitionCode, enabled: query.enabled }}
        onError={(error) =>
          message.error(error instanceof Error ? error.message : t('archive.rule.msg.loadFailed'))
        }
        search={
          <SearchForm
            items={[
              {
                name: 'definitionCode',
                label: t('archive.rule.search.definition'),
                element: (
                  <Input allowClear placeholder={t('archive.rule.search.definitionPlaceholder')} />
                ),
              },
              {
                name: 'enabled',
                label: t('archive.rule.search.enabledStatus'),
                element: (
                  <Select
                    allowClear
                    placeholder={t('archive.common.all')}
                    options={enabledOptions(t)}
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
            {t('archive.rule.action.create')}
          </AuthButton>
        }
      />
      <Modal
        title={editing ? t('archive.rule.modal.edit') : t('archive.rule.modal.create')}
        open={modalOpen}
        confirmLoading={saving}
        onOk={() => void handleSubmit()}
        onCancel={() => setModalOpen(false)}
        destroyOnClose
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="definitionCode"
            label={t('archive.rule.col.definitionCode')}
            rules={[{ required: true, message: t('archive.rule.form.definitionRequired') }]}
          >
            <Input
              placeholder={t('archive.rule.form.definitionPlaceholder')}
              disabled={Boolean(editing)}
            />
          </Form.Item>
          <Form.Item
            name="categoryId"
            label={t('archive.rule.col.category')}
            rules={[{ required: true, message: t('archive.rule.form.categoryRequired') }]}
          >
            <Select options={categoryOptions} showSearch optionFilterProp="label" />
          </Form.Item>
          <Form.Item name="retentionYears" label={t('archive.common.retentionYears')}>
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="securityLevel" label={t('archive.common.securityLevel')}>
            <Select options={securityOptions(t)} />
          </Form.Item>
          <Form.Item
            name="autoArchive"
            label={t('archive.rule.form.autoArchive')}
            valuePropName="checked"
          >
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label={t('archive.common.enabled')} valuePropName="checked">
            <Switch />
          </Form.Item>
        </Form>
      </Modal>
    </>
  );
}
