// 图文档中心：目录树 + 文档列表 + 版本动作（上传/检出/检入/发布/回滚/下载）

import { useCallback, useEffect, useMemo, useState, type Key } from 'react';
import {
  Button,
  Card,
  Empty,
  Input,
  Popconfirm,
  Select,
  Space,
  Table,
  Tag,
  Tree,
  message,
} from 'antd';
import type { TableProps, TreeDataNode } from 'antd';
import {
  checkOut,
  deleteDocument,
  deleteFolder,
  exportDocuments,
  listFolders,
  pageDocuments,
  type AclResourceType,
  type DocFolder,
  type DocStatus,
  type DocType,
  type DocumentQuery,
  type PdmDocument,
} from '../../api/document';
import { getCurrentUserId, hasPermission } from '../../utils/permission';
import AclModal from './AclModal';
import DocumentForm from './DocumentForm';
import FolderModal from './FolderModal';
import UploadModal from './UploadModal';
import VersionDrawer from './VersionDrawer';
import {
  DOC_TYPE_LABEL,
  DOC_TYPE_OPTIONS,
  collectFolderIds,
  findFolder,
  flattenFolders,
  showError,
} from './utils';

/** 文档状态文案 */
const STATUS_LABEL: Record<DocStatus, { color: string; label: string }> = {
  DRAFT: { color: 'blue', label: '草稿' },
  RELEASED: { color: 'green', label: '已发布' },
};

/** 全部文档（根节点） */
const ALL_FOLDER_KEY = 0;

/** 以浏览器下载方式保存 Blob */
function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

/** 目录树数据装配（根节点固定为「全部文档」） */
function buildTreeData(folders: DocFolder[]): TreeDataNode[] {
  const mapFolder = (folder: DocFolder): TreeDataNode => ({
    key: folder.id,
    title: folder.name,
    children: (folder.children ?? []).map(mapFolder),
  });
  return [{ key: ALL_FOLDER_KEY, title: '全部文档', children: folders.map(mapFolder) }];
}

/** 收集树中全部节点 key（用于默认展开） */
function collectTreeKeys(folders: DocFolder[]): number[] {
  const keys: number[] = [ALL_FOLDER_KEY];
  const walk = (nodes: DocFolder[]) => {
    nodes.forEach((node) => {
      keys.push(node.id);
      walk(node.children ?? []);
    });
  };
  walk(folders);
  return keys;
}

export default function DocumentCenter() {
  const [folders, setFolders] = useState<DocFolder[]>([]);
  const [expandedKeys, setExpandedKeys] = useState<number[]>([ALL_FOLDER_KEY]);
  const [selectedFolderId, setSelectedFolderId] = useState<number>(ALL_FOLDER_KEY);
  const [documents, setDocuments] = useState<PdmDocument[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [loading, setLoading] = useState(false);
  const [keyword, setKeyword] = useState('');
  const [docType, setDocType] = useState<DocType | undefined>();
  const [status, setStatus] = useState<DocStatus | undefined>();
  const [refreshKey, setRefreshKey] = useState(0);

  const [documentFormOpen, setDocumentFormOpen] = useState(false);
  const [editingDocument, setEditingDocument] = useState<PdmDocument | null>(null);
  const [folderModalOpen, setFolderModalOpen] = useState(false);
  const [editingFolder, setEditingFolder] = useState<DocFolder | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [uploadMode, setUploadMode] = useState<'upload' | 'checkin'>('upload');
  const [uploadTarget, setUploadTarget] = useState<PdmDocument | null>(null);
  const [versionOpen, setVersionOpen] = useState(false);
  const [versionTarget, setVersionTarget] = useState<PdmDocument | null>(null);
  const [aclOpen, setAclOpen] = useState(false);
  const [aclResourceType, setAclResourceType] = useState<AclResourceType>('DOCUMENT');
  const [aclResourceId, setAclResourceId] = useState<number | undefined>();
  const [aclTitle, setAclTitle] = useState('');

  const currentUserId = getCurrentUserId();
  const canManageFolder = hasPermission('sys:pdm:folder:manage');

  const loadFolders = useCallback(async () => {
    try {
      const tree = await listFolders();
      setFolders(tree);
      setExpandedKeys(collectTreeKeys(tree));
    } catch (error) {
      showError(error);
    }
  }, []);

  const loadDocuments = useCallback(async () => {
    setLoading(true);
    try {
      const query: DocumentQuery = {
        current,
        size: pageSize,
        folderId: selectedFolderId === ALL_FOLDER_KEY ? undefined : selectedFolderId,
        keyword: keyword || undefined,
        docType,
        status,
      };
      const page = await pageDocuments(query);
      setDocuments(page.records ?? []);
      setTotal(Number(page.total ?? 0));
    } catch (error) {
      showError(error);
    } finally {
      setLoading(false);
    }
  }, [current, pageSize, selectedFolderId, keyword, docType, status]);

  useEffect(() => {
    void loadFolders();
  }, [loadFolders]);

  useEffect(() => {
    void loadDocuments();
  }, [loadDocuments, refreshKey]);

  const refreshAll = () => setRefreshKey((key) => key + 1);

  const handleSearch = () => {
    setCurrent(1);
    refreshAll();
  };

  const handleReset = () => {
    setKeyword('');
    setDocType(undefined);
    setStatus(undefined);
    setCurrent(1);
    refreshAll();
  };

  const handleExport = async () => {
    try {
      const result = await exportDocuments({
        folderId: selectedFolderId === ALL_FOLDER_KEY ? undefined : selectedFolderId,
        keyword: keyword || undefined,
        docType,
        status,
      });
      saveBlob(result.blob, result.filename || 'pdm-documents.csv');
      message.success('导出成功');
    } catch (error) {
      showError(error);
    }
  };

  const handleSelectFolder = (keys: Key[]) => {
    const key = keys[0];
    setSelectedFolderId(typeof key === 'number' ? key : Number(key ?? ALL_FOLDER_KEY));
    setCurrent(1);
  };

  const handleCreateDocument = () => {
    setEditingDocument(null);
    setDocumentFormOpen(true);
  };

  const handleEditDocument = (record: PdmDocument) => {
    setEditingDocument(record);
    setDocumentFormOpen(true);
  };

  const handleDeleteDocument = async (record: PdmDocument) => {
    try {
      await deleteDocument(record.id);
      message.success('图文档已删除');
      refreshAll();
    } catch (error) {
      showError(error);
    }
  };

  const handleCheckOut = async (record: PdmDocument) => {
    try {
      await checkOut(record.id);
      message.success('已检出，其他用户不可再上传版本');
      refreshAll();
    } catch (error) {
      showError(error);
    }
  };

  const openUpload = (record: PdmDocument, mode: 'upload' | 'checkin') => {
    setUploadTarget(record);
    setUploadMode(mode);
    setUploadOpen(true);
  };

  const openVersions = (record: PdmDocument) => {
    setVersionTarget(record);
    setVersionOpen(true);
  };

  const openAcl = (resourceType: AclResourceType, resourceId: number, title: string) => {
    setAclResourceType(resourceType);
    setAclResourceId(resourceId);
    setAclTitle(title);
    setAclOpen(true);
  };

  const handleCreateFolder = () => {
    setEditingFolder(null);
    setFolderModalOpen(true);
  };

  const handleEditFolder = () => {
    const folder = findFolder(folders, selectedFolderId);
    if (!folder) {
      message.warning('请先在左侧选择要编辑的目录');
      return;
    }
    setEditingFolder(folder);
    setFolderModalOpen(true);
  };

  const handleDeleteFolder = async () => {
    if (selectedFolderId === ALL_FOLDER_KEY) {
      message.warning('根目录不可删除');
      return;
    }
    try {
      await deleteFolder(selectedFolderId);
      message.success('目录已删除');
      setSelectedFolderId(ALL_FOLDER_KEY);
      await loadFolders();
      refreshAll();
    } catch (error) {
      showError(error);
    }
  };

  const folderOptions = useMemo(() => flattenFolders(folders), [folders]);
  const parentOptions = useMemo(() => {
    if (!editingFolder) {
      return folderOptions;
    }
    const excluded = collectFolderIds(editingFolder);
    return folderOptions.filter((option) => !excluded.has(option.value));
  }, [folderOptions, editingFolder]);

  const treeData = useMemo(() => buildTreeData(folders), [folders]);

  /** 中心区域标题：当前目录路径 */
  const selectedFolderName = useMemo(() => {
    if (selectedFolderId === ALL_FOLDER_KEY) {
      return '全部文档';
    }
    const names: string[] = [];
    const walk = (nodes: DocFolder[], trail: string[]) => {
      nodes.forEach((node) => {
        if (node.id === selectedFolderId) {
          names.push(...trail, node.name);
        }
        walk(node.children ?? [], [...trail, node.name]);
      });
    };
    walk(folders, []);
    return names.length > 0 ? names.join(' / ') : '文档列表';
  }, [folders, selectedFolderId]);

  const columns: TableProps<PdmDocument>['columns'] = [
    { title: '文档编号', dataIndex: 'docNo', key: 'docNo', width: 170 },
    { title: '标题', dataIndex: 'title', key: 'title', ellipsis: true },
    {
      title: '类型',
      dataIndex: 'docType',
      key: 'docType',
      width: 100,
      render: (value: DocType) => DOC_TYPE_LABEL[value] ?? value,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (value: DocStatus) => {
        const status = STATUS_LABEL[value] ?? { color: 'default', label: value };
        return <Tag color={status.color}>{status.label}</Tag>;
      },
    },
    {
      title: '最新版本',
      dataIndex: 'latestVersionNo',
      key: 'latestVersionNo',
      width: 100,
      render: (value?: string) => value ?? '-',
    },
    {
      title: '检出',
      key: 'checkedOut',
      width: 90,
      render: (_, record) =>
        record.checkedOutBy ? <Tag color="orange">已检出</Tag> : <Tag>可编辑</Tag>,
    },
    { title: '设计人', dataIndex: 'ownerName', key: 'ownerName', width: 100 },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 170 },
    {
      title: '操作',
      key: 'action',
      width: 340,
      render: (_, record) => (
        <Space size={0} wrap>
          <Button type="link" size="small" onClick={() => openVersions(record)}>
            版本
          </Button>
          {hasPermission('sys:pdm:doc:acl:list') ? (
            <Button
              type="link"
              size="small"
              onClick={() => openAcl('DOCUMENT', record.id, record.docNo)}
            >
              权限
            </Button>
          ) : null}
          {hasPermission('sys:pdm:doc:version') ? (
            <Button type="link" size="small" onClick={() => openUpload(record, 'upload')}>
              上传
            </Button>
          ) : null}
          {hasPermission('sys:pdm:doc:checkout') && !record.checkedOutBy ? (
            <Button type="link" size="small" onClick={() => void handleCheckOut(record)}>
              检出
            </Button>
          ) : null}
          {hasPermission('sys:pdm:doc:version') &&
          record.checkedOutBy !== undefined &&
          record.checkedOutBy === currentUserId ? (
            <Button type="link" size="small" onClick={() => openUpload(record, 'checkin')}>
              检入
            </Button>
          ) : null}
          {hasPermission('sys:pdm:doc:edit') ? (
            <Button type="link" size="small" onClick={() => handleEditDocument(record)}>
              编辑
            </Button>
          ) : null}
          {hasPermission('sys:pdm:doc:delete') ? (
            <Popconfirm
              title="删除图文档"
              description="存在已发布版本的文档不可删除，确认继续？"
              onConfirm={() => void handleDeleteDocument(record)}
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
    <Space direction="vertical" size="middle" style={{ width: '100%' }}>
      <Card
        size="small"
        title="目录"
        extra={
          <Space size={4}>
            {selectedFolderId !== ALL_FOLDER_KEY && hasPermission('sys:pdm:doc:acl:list') ? (
              <Button
                size="small"
                onClick={() => openAcl('FOLDER', selectedFolderId, selectedFolderName)}
              >
                目录权限
              </Button>
            ) : null}
            {canManageFolder ? (
              <>
                <Button size="small" type="primary" onClick={handleCreateFolder}>
                  新建目录
                </Button>
                <Button size="small" onClick={handleEditFolder}>
                  重命名/移动
                </Button>
                <Popconfirm
                  title="删除目录"
                  description="仅空目录（无子目录、无文档）可删除，确认继续？"
                  onConfirm={() => void handleDeleteFolder()}
                >
                  <Button size="small" danger>
                    删除目录
                  </Button>
                </Popconfirm>
              </>
            ) : null}
          </Space>
        }
      >
        {treeData.length > 0 ? (
          <Tree
            blockNode
            treeData={treeData}
            expandedKeys={expandedKeys}
            selectedKeys={[selectedFolderId]}
            onExpand={(keys) => setExpandedKeys(keys as number[])}
            onSelect={(keys) => handleSelectFolder(keys)}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无目录" />
        )}
      </Card>

      <Card
        size="small"
        title={selectedFolderName}
        extra={
          hasPermission('sys:pdm:doc:add') ? (
            <Button type="primary" size="small" onClick={handleCreateDocument}>
              新建图文档
            </Button>
          ) : null
        }
      >
        <Space wrap style={{ marginBottom: 12 }}>
          <Input
            allowClear
            placeholder="编号 / 标题 / 零件号 / 标签"
            style={{ width: 240 }}
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            onPressEnter={handleSearch}
          />
          <Select<DocType>
            allowClear
            placeholder="文档类型"
            style={{ width: 130 }}
            options={DOC_TYPE_OPTIONS}
            value={docType}
            onChange={(value) => setDocType(value)}
          />
          <Select<DocStatus>
            allowClear
            placeholder="状态"
            style={{ width: 120 }}
            options={[
              { value: 'DRAFT', label: '草稿' },
              { value: 'RELEASED', label: '已发布' },
            ]}
            value={status}
            onChange={(value) => setStatus(value)}
          />
          <Button type="primary" onClick={handleSearch}>
            查询
          </Button>
          <Button onClick={handleReset}>重置</Button>
          {hasPermission('sys:pdm:doc:export') ? (
            <Button onClick={() => void handleExport()}>导出</Button>
          ) : null}
        </Space>
        <Table<PdmDocument>
          rowKey="id"
          loading={loading}
          columns={columns}
          dataSource={documents}
          scroll={{ x: 1100 }}
          pagination={{
            current,
            pageSize,
            total,
            showSizeChanger: true,
            showTotal: (count) => `共 ${count} 条`,
            onChange: (page, size) => {
              setCurrent(page);
              setPageSize(size);
            },
          }}
        />
      </Card>

      <DocumentForm
        open={documentFormOpen}
        initial={editingDocument}
        folderOptions={folderOptions}
        defaultFolderId={selectedFolderId === ALL_FOLDER_KEY ? undefined : selectedFolderId}
        onCancel={() => setDocumentFormOpen(false)}
        onSaved={() => {
          setDocumentFormOpen(false);
          setEditingDocument(null);
          refreshAll();
        }}
      />

      <FolderModal
        open={folderModalOpen}
        initial={editingFolder}
        defaultParentId={selectedFolderId === ALL_FOLDER_KEY ? 0 : selectedFolderId}
        parentOptions={parentOptions}
        onCancel={() => setFolderModalOpen(false)}
        onSaved={async () => {
          setFolderModalOpen(false);
          setEditingFolder(null);
          await loadFolders();
          refreshAll();
        }}
      />

      <UploadModal
        open={uploadOpen}
        document={uploadTarget}
        mode={uploadMode}
        onCancel={() => setUploadOpen(false)}
        onDone={() => {
          setUploadOpen(false);
          refreshAll();
        }}
      />

      <AclModal
        open={aclOpen}
        resourceType={aclResourceType}
        resourceId={aclResourceId}
        title={aclTitle}
        onClose={() => setAclOpen(false)}
        onChanged={refreshAll}
      />

      <VersionDrawer
        open={versionOpen}
        document={versionTarget}
        onClose={() => setVersionOpen(false)}
        onChanged={refreshAll}
      />
    </Space>
  );
}
