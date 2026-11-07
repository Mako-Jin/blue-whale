// OA 协同 - 网盘：文件夹导航、文件上传/下载、目录授权与配额展示
import { useCallback, useEffect, useRef, useState } from 'react';
import {
  App,
  Breadcrumb,
  Button,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Popconfirm,
  Progress,
  Select,
  Space,
  Table,
  Tag,
  Upload,
} from 'antd';
import type { TableProps, UploadProps } from 'antd';
import { FileOutlined, FolderOutlined, UploadOutlined } from '@ant-design/icons';
import {
  createDriveFolder,
  deleteDriveFile,
  deleteDriveFolder,
  downloadDriveFile,
  getDriveQuota,
  listDriveAcls,
  listDriveFolders,
  pageDriveFiles,
  registerDriveFile,
  replaceDriveAcls,
  updateDriveFolder,
  type OaDriveAcl,
  type OaDriveAclEffect,
  type OaDriveAclPermission,
  type OaDriveFile,
  type OaDriveFolder,
  type OaDriveFolderInput,
  type OaDriveQuota,
  type OaDriveScope,
  type OaDriveSubjectType,
} from '../../api/drive';
import { uploadOaFile } from '../../api/file';

/** 文件列表分页大小 */
const PAGE_SIZE = 20;

/** 范围文案 */
const SCOPE_META: Record<OaDriveScope, { text: string; color: string }> = {
  PERSONAL: { text: '个人', color: 'blue' },
  DEPARTMENT: { text: '部门', color: 'purple' },
};

/** 授权主体类型选项 */
const SUBJECT_OPTIONS = [
  { label: '用户', value: 'USER' as OaDriveSubjectType },
  { label: '部门', value: 'DEPT' as OaDriveSubjectType },
];

/** 授权效果选项 */
const EFFECT_OPTIONS = [
  { label: '允许', value: 'ALLOW' as OaDriveAclEffect },
  { label: '拒绝', value: 'DENY' as OaDriveAclEffect },
];

/** 授权权限选项 */
const PERMISSION_OPTIONS = [
  { label: '只读', value: 'READ' as OaDriveAclPermission },
  { label: '读写', value: 'WRITE' as OaDriveAclPermission },
  { label: '管理', value: 'MANAGE' as OaDriveAclPermission },
];

/** 新建文件夹表单值 */
interface FolderFormValues {
  name: string;
  scope: OaDriveScope;
  deptId?: number;
  sortNo?: number;
}

/** 重命名表单值 */
interface RenameFormValues {
  name: string;
  sortNo?: number;
}

/** 授权表格行（key 仅用于前端行标识） */
interface AclRow extends OaDriveAcl {
  key: number;
}

/** 时间格式化为展示文本 */
function timeText(value?: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-';
}

/** 文件大小格式化 */
function formatSize(bytes?: number): string {
  if (bytes === undefined || bytes <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let unitIndex = 0;
  while (value >= 1024 && unitIndex < units.length - 1) {
    value /= 1024;
    unitIndex += 1;
  }
  const digits = unitIndex === 0 ? 0 : 1;
  return `${value.toFixed(digits)} ${units[unitIndex]}`;
}

export default function CollabDrivePage() {
  const { message } = App.useApp();

  const [parentId, setParentId] = useState(0);
  const [crumbs, setCrumbs] = useState<OaDriveFolder[]>([]);
  const [folders, setFolders] = useState<OaDriveFolder[]>([]);
  const [files, setFiles] = useState<OaDriveFile[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [quota, setQuota] = useState<OaDriveQuota | null>(null);
  const [loading, setLoading] = useState(false);
  const [uploading, setUploading] = useState(false);

  const [folderModal, setFolderModal] = useState(false);
  const [renameTarget, setRenameTarget] = useState<OaDriveFolder | null>(null);
  const [saving, setSaving] = useState(false);

  const [aclOpen, setAclOpen] = useState(false);
  const [aclTarget, setAclTarget] = useState<OaDriveFolder | null>(null);
  const [aclRows, setAclRows] = useState<AclRow[]>([]);
  const [aclSaving, setAclSaving] = useState(false);
  const aclSeq = useRef(0);

  const [folderForm] = Form.useForm<FolderFormValues>();
  const [renameForm] = Form.useForm<RenameFormValues>();

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [folderList, filePage, quotaData] = await Promise.all([
        listDriveFolders(parentId),
        parentId > 0
          ? pageDriveFiles({
              folderId: parentId,
              keyword: keyword.trim() || undefined,
              current: page,
              size: PAGE_SIZE,
            })
          : Promise.resolve({ records: [], total: 0, size: PAGE_SIZE, current: 1 }),
        getDriveQuota(),
      ]);
      setFolders(folderList);
      setFiles(filePage.records);
      setTotal(filePage.total);
      setQuota(quotaData);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setLoading(false);
    }
  }, [parentId, keyword, page, message]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const enterFolder = (folder: OaDriveFolder) => {
    setCrumbs((prev) => [...prev, folder]);
    setParentId(folder.id);
    setKeyword('');
    setPage(1);
  };

  const goToLevel = (level: number) => {
    const target = level === 0 ? 0 : crumbs[level - 1].id;
    setCrumbs(crumbs.slice(0, level));
    setParentId(target);
    setKeyword('');
    setPage(1);
  };

  const openCreateFolder = () => {
    folderForm.resetFields();
    folderForm.setFieldsValue({ scope: 'PERSONAL' });
    setFolderModal(true);
  };

  const saveFolder = async () => {
    const values = await folderForm.validateFields();
    const payload: OaDriveFolderInput = {
      name: values.name.trim(),
      parentId,
      scope: values.scope,
      deptId: values.scope === 'DEPARTMENT' ? values.deptId : undefined,
      sortNo: values.sortNo,
    };
    setSaving(true);
    try {
      await createDriveFolder(payload);
      message.success('文件夹已创建');
      setFolderModal(false);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setSaving(false);
    }
  };

  const openRename = (folder: OaDriveFolder) => {
    setRenameTarget(folder);
    renameForm.setFieldsValue({ name: folder.name, sortNo: folder.sortNo });
  };

  const saveRename = async () => {
    if (!renameTarget) {
      return;
    }
    const values = await renameForm.validateFields();
    setSaving(true);
    try {
      await updateDriveFolder(renameTarget.id, {
        name: values.name.trim(),
        sortNo: values.sortNo,
      });
      message.success('文件夹已重命名');
      setRenameTarget(null);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setSaving(false);
    }
  };

  const removeFolder = async (folder: OaDriveFolder) => {
    try {
      await deleteDriveFolder(folder.id);
      message.success('文件夹已删除');
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const removeFile = async (file: OaDriveFile) => {
    try {
      await deleteDriveFile(file.id);
      message.success('文件已删除');
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const download = async (file: OaDriveFile) => {
    try {
      await downloadDriveFile(file.id, file.fileName);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const uploadAndRegister = async (file: File) => {
    setUploading(true);
    try {
      const uploaded = await uploadOaFile(file);
      await registerDriveFile({ folderId: parentId, fileId: uploaded.id });
      message.success(`${file.name} 上传成功`);
      await loadData();
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setUploading(false);
    }
  };

  const beforeUpload: UploadProps['beforeUpload'] = (file) => {
    void uploadAndRegister(file);
    return false;
  };

  const openAcl = async (folder: OaDriveFolder) => {
    setAclTarget(folder);
    try {
      const entries = await listDriveAcls(folder.id);
      aclSeq.current = 0;
      setAclRows(
        entries.map((entry) => {
          aclSeq.current += 1;
          return { ...entry, key: aclSeq.current };
        }),
      );
      setAclOpen(true);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    }
  };

  const addAclRow = () => {
    aclSeq.current += 1;
    setAclRows((prev) => [
      ...prev,
      {
        key: aclSeq.current,
        subjectType: 'USER',
        subjectId: 0,
        effect: 'ALLOW',
        permission: 'READ',
      },
    ]);
  };

  const updateAclRow = (key: number, patch: Partial<OaDriveAcl>) => {
    setAclRows((prev) => prev.map((row) => (row.key === key ? { ...row, ...patch } : row)));
  };

  const removeAclRow = (key: number) => {
    setAclRows((prev) => prev.filter((row) => row.key !== key));
  };

  const saveAcls = async () => {
    if (!aclTarget) {
      return;
    }
    const invalid = aclRows.some((row) => row.subjectId <= 0);
    if (invalid) {
      message.error('请输入有效的主体ID');
      return;
    }
    const entries: OaDriveAcl[] = aclRows.map((row) => ({
      subjectType: row.subjectType,
      subjectId: row.subjectId,
      subjectName: row.subjectName?.trim() || undefined,
      effect: row.effect,
      permission: row.permission,
    }));
    setAclSaving(true);
    try {
      await replaceDriveAcls(aclTarget.id, entries);
      message.success('授权已保存');
      setAclOpen(false);
    } catch (error) {
      message.error(error instanceof Error ? error.message : '操作失败');
    } finally {
      setAclSaving(false);
    }
  };

  const quotaPercent =
    quota && quota.totalBytes > 0
      ? Math.min(100, Math.round((quota.usedBytes / quota.totalBytes) * 100))
      : 0;

  const breadcrumbItems = [
    { title: '全部文件', onClick: () => goToLevel(0) },
    ...crumbs.map((folder, index) => ({
      title: folder.name,
      onClick: () => goToLevel(index + 1),
    })),
  ];

  const aclColumns: TableProps<AclRow>['columns'] = [
    {
      title: '主体类型',
      dataIndex: 'subjectType',
      key: 'subjectType',
      width: 110,
      render: (value: OaDriveSubjectType, row) => (
        <Select
          value={value}
          style={{ width: '100%' }}
          options={SUBJECT_OPTIONS}
          onChange={(next: OaDriveSubjectType) => updateAclRow(row.key, { subjectType: next })}
        />
      ),
    },
    {
      title: '主体ID',
      dataIndex: 'subjectId',
      key: 'subjectId',
      width: 120,
      render: (value: number, row) => (
        <InputNumber
          min={1}
          precision={0}
          style={{ width: '100%' }}
          value={value > 0 ? value : undefined}
          onChange={(next) => updateAclRow(row.key, { subjectId: next ?? 0 })}
        />
      ),
    },
    {
      title: '主体名称',
      dataIndex: 'subjectName',
      key: 'subjectName',
      render: (value: string | undefined, row) => (
        <Input
          value={value}
          maxLength={64}
          placeholder="可选"
          onChange={(event) => updateAclRow(row.key, { subjectName: event.target.value })}
        />
      ),
    },
    {
      title: '效果',
      dataIndex: 'effect',
      key: 'effect',
      width: 100,
      render: (value: OaDriveAclEffect, row) => (
        <Select
          value={value}
          style={{ width: '100%' }}
          options={EFFECT_OPTIONS}
          onChange={(next: OaDriveAclEffect) => updateAclRow(row.key, { effect: next })}
        />
      ),
    },
    {
      title: '权限',
      dataIndex: 'permission',
      key: 'permission',
      width: 100,
      render: (value: OaDriveAclPermission, row) => (
        <Select
          value={value}
          style={{ width: '100%' }}
          options={PERMISSION_OPTIONS}
          onChange={(next: OaDriveAclPermission) => updateAclRow(row.key, { permission: next })}
        />
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 70,
      render: (_value, row) => (
        <Button type="link" size="small" danger onClick={() => removeAclRow(row.key)}>
          移除
        </Button>
      ),
    },
  ];

  const fileColumns: TableProps<OaDriveFile>['columns'] = [
    {
      title: '文件名',
      dataIndex: 'fileName',
      key: 'fileName',
      ellipsis: true,
      render: (value: string) => (
        <Space size={6}>
          <FileOutlined />
          <span>{value}</span>
        </Space>
      ),
    },
    {
      title: '大小',
      dataIndex: 'fileSize',
      key: 'fileSize',
      width: 110,
      render: (value?: number) => formatSize(value),
    },
    {
      title: '上传人',
      dataIndex: 'uploaderName',
      key: 'uploaderName',
      width: 120,
      render: (value: string | undefined, record) => value ?? record.uploaderId ?? '-',
    },
    {
      title: '上传时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (value?: string) => timeText(value),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_value, record) => (
        <Space size={4}>
          <Button type="link" size="small" onClick={() => void download(record)}>
            下载
          </Button>
          <Popconfirm
            title="确认删除该文件？"
            description="删除后不可恢复"
            onConfirm={() => void removeFile(record)}
          >
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <>
      <Space direction="vertical" size="middle" style={{ width: '100%' }}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            gap: 16,
            flexWrap: 'wrap',
          }}
        >
          <Breadcrumb items={breadcrumbItems} />
          <Space size={8}>
            <span>配额</span>
            <Progress percent={quotaPercent} size="small" style={{ width: 160, marginBottom: 0 }} />
            <span>
              已用 {formatSize(quota?.usedBytes)} / {formatSize(quota?.totalBytes)}
            </span>
          </Space>
        </div>
        <div style={{ display: 'flex', gap: 16, alignItems: 'flex-start' }}>
          <div
            style={{
              width: 300,
              flex: '0 0 300px',
              border: '1px solid #f0f0f0',
              borderRadius: 8,
              padding: 12,
              minHeight: 240,
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: 8 }}>文件夹</div>
            {folders.length === 0 ? (
              <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无文件夹" />
            ) : (
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                {folders.map((folder) => (
                  <div
                    key={folder.id}
                    onClick={() => enterFolder(folder)}
                    style={{
                      padding: '8px 10px',
                      border: '1px solid #f0f0f0',
                      borderRadius: 6,
                      cursor: 'pointer',
                    }}
                  >
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        gap: 8,
                      }}
                    >
                      <span
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: 6,
                          overflow: 'hidden',
                          whiteSpace: 'nowrap',
                          textOverflow: 'ellipsis',
                        }}
                      >
                        <FolderOutlined />
                        {folder.name}
                      </span>
                      <Tag
                        color={SCOPE_META[folder.scope]?.color ?? 'default'}
                        style={{ marginInlineEnd: 0 }}
                      >
                        {SCOPE_META[folder.scope]?.text ?? folder.scope}
                      </Tag>
                    </div>
                    <div style={{ fontSize: 12, color: '#999', marginTop: 2 }}>
                      所有者：{folder.ownerName ?? folder.ownerId}
                    </div>
                    {folder.manageable ? (
                      <Space
                        size={4}
                        style={{ marginTop: 2 }}
                        onClick={(event) => event.stopPropagation()}
                      >
                        <Button type="link" size="small" onClick={() => openRename(folder)}>
                          重命名
                        </Button>
                        <Button type="link" size="small" onClick={() => void openAcl(folder)}>
                          授权
                        </Button>
                        <Popconfirm
                          title="文件夹需为空才能删除"
                          description="确认删除该文件夹？"
                          onConfirm={() => void removeFolder(folder)}
                        >
                          <Button type="link" size="small" danger>
                            删除
                          </Button>
                        </Popconfirm>
                      </Space>
                    ) : null}
                  </div>
                ))}
              </Space>
            )}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
              <Space wrap>
                <Button type="primary" onClick={openCreateFolder}>
                  新建文件夹
                </Button>
                <Upload multiple showUploadList={false} beforeUpload={beforeUpload} disabled={parentId === 0}>
                  <Button icon={<UploadOutlined />} loading={uploading} disabled={parentId === 0}>
                    上传
                  </Button>
                </Upload>
                <Input.Search
                  allowClear
                  placeholder="文件名"
                  style={{ width: 220 }}
                  onSearch={(value) => {
                    setKeyword(value.trim());
                    setPage(1);
                  }}
                />
                <Button loading={loading} onClick={() => void loadData()}>
                  刷新
                </Button>
              </Space>
              <Table<OaDriveFile>
                rowKey="id"
                loading={loading}
                columns={fileColumns}
                dataSource={files}
                scroll={{ x: 900 }}
                pagination={{
                  current: page,
                  pageSize: PAGE_SIZE,
                  total,
                  onChange: (next) => setPage(next),
                }}
              />
            </Space>
          </div>
        </div>
      </Space>
      <Modal
        open={folderModal}
        title="新建文件夹"
        onOk={() => void saveFolder()}
        confirmLoading={saving}
        onCancel={() => setFolderModal(false)}
        destroyOnClose
      >
        <Form form={folderForm} layout="vertical">
          <Form.Item
            name="name"
            label="文件夹名称"
            rules={[{ required: true, message: '请输入文件夹名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="scope" label="范围" rules={[{ required: true, message: '请选择范围' }]}>
            <Select
              options={Object.entries(SCOPE_META).map(([value, meta]) => ({
                label: meta.text,
                value,
              }))}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate={(prev, next) => prev.scope !== next.scope}>
            {({ getFieldValue }) =>
              getFieldValue('scope') === 'DEPARTMENT' ? (
                <Form.Item
                  name="deptId"
                  label="部门ID"
                  rules={[{ required: true, message: '请输入部门ID' }]}
                >
                  <InputNumber style={{ width: '100%' }} min={1} precision={0} />
                </Form.Item>
              ) : null
            }
          </Form.Item>
          <Form.Item name="sortNo" label="排序号">
            <InputNumber style={{ width: '100%' }} min={0} precision={0} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={renameTarget !== null}
        title="重命名文件夹"
        onOk={() => void saveRename()}
        confirmLoading={saving}
        onCancel={() => setRenameTarget(null)}
        destroyOnClose
      >
        <Form form={renameForm} layout="vertical">
          <Form.Item
            name="name"
            label="文件夹名称"
            rules={[{ required: true, message: '请输入文件夹名称' }]}
          >
            <Input maxLength={64} />
          </Form.Item>
          <Form.Item name="sortNo" label="排序号">
            <InputNumber style={{ width: '100%' }} min={0} precision={0} />
          </Form.Item>
        </Form>
      </Modal>
      <Modal
        open={aclOpen}
        title={aclTarget ? `授权：${aclTarget.name}` : '目录授权'}
        width={760}
        onOk={() => void saveAcls()}
        confirmLoading={aclSaving}
        onCancel={() => setAclOpen(false)}
        destroyOnClose
      >
        <Space direction="vertical" size="middle" style={{ width: '100%' }}>
          <Button type="dashed" onClick={addAclRow}>
            添加授权条目
          </Button>
          <Table<AclRow>
            rowKey="key"
            size="small"
            pagination={false}
            columns={aclColumns}
            dataSource={aclRows}
            locale={{ emptyText: '暂无授权条目' }}
          />
        </Space>
      </Modal>
    </>
  );
}
