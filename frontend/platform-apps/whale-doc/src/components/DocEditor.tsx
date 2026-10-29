// 页面编辑器：TipTap 正文、标题、自动保存与冲突处理、发布/归档、信息面板

import { useCallback, useEffect, useRef, useState } from 'react';
import {
  App,
  Avatar,
  Button,
  Divider,
  Dropdown,
  Input,
  Modal,
  Space,
  Spin,
  Tag,
  Tooltip,
  Typography,
  theme,
} from 'antd';
import {
  BoldOutlined,
  CheckCircleOutlined,
  CodeOutlined,
  CopyOutlined,
  DeleteOutlined,
  DownloadOutlined,
  HistoryOutlined,
  ImportOutlined,
  ItalicOutlined,
  LinkOutlined,
  LockOutlined,
  MenuOutlined,
  OrderedListOutlined,
  PictureOutlined,
  RedoOutlined,
  RobotOutlined,
  ShareAltOutlined,
  StrikethroughOutlined,
  UnderlineOutlined,
  UndoOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons';
import { Editor, EditorContent, useEditor, type JSONContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Collaboration from '@tiptap/extension-collaboration';
import CollaborationCursor from '@tiptap/extension-collaboration-cursor';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import Placeholder from '@tiptap/extension-placeholder';
import Underline from '@tiptap/extension-underline';
import { ApiError } from '@bluewhale/http';
import { docApi } from '../api/doc';
import { getCurrentUser, hasPermission } from '../api/client';
import { pageApi } from '../api/page';
import { starApi } from '../api/star';
import { templateApi } from '../api/template';
import { downloadBlob, transferApi, type ExportFormat } from '../api/transfer';
import type { DocJson, DocPage, DocPageStatus } from '../api/types';
import {
  DocCollabProvider,
  type CollabReadyPayload,
  type CollabStatus,
  type CollabUser,
} from '../collab/DocCollabProvider';
import { formatTime, parseDocJson } from '../utils/docContent';
import DocAclDrawer from './DocAclDrawer';
import DocAiDrawer from './DocAiDrawer';
import InfoPanel from './InfoPanel';
import ShareDrawer from './ShareDrawer';

const { Text, Title } = Typography;

/** 自动保存防抖间隔 */
const AUTOSAVE_MS = 2000;

/** 空文档 */
const EMPTY_DOC: JSONContent = { type: 'doc', content: [{ type: 'paragraph' }] };

/** 协作快照确认超时 */
const SNAPSHOT_ACK_TIMEOUT_MS = 8000;

/** 协作光标颜色（与后端 DocPresencePalette 一致） */
const COLLAB_COLORS = ['#2563eb', '#7c3aed', '#db2777', '#ea580c', '#16a34a', '#0891b2', '#ca8a04', '#dc2626'];

/** 按用户ID选择稳定的协作颜色 */
function collabColor(seed: number | string | undefined): string {
  const value = typeof seed === 'number' ? seed : String(seed ?? '').length;
  return COLLAB_COLORS[Math.abs(value) % COLLAB_COLORS.length];
}

/** 取名字首字作为头像文案 */
function avatarText(name: string): string {
  return (name || '?').slice(0, 1).toUpperCase();
}

/** 状态文案 */
const STATUS_LABEL: Record<DocPageStatus, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  ARCHIVED: '已归档',
  PENDING_APPROVAL: '待审批',
};

/** 状态颜色 */
const STATUS_COLOR: Record<DocPageStatus, string> = {
  DRAFT: 'gold',
  PUBLISHED: 'green',
  ARCHIVED: 'default',
  PENDING_APPROVAL: 'blue',
};

/** 页面是否可编辑（待审批与已归档只读） */
function isPageEditable(page: DocPage): boolean {
  return page.canEdit && page.status !== 'ARCHIVED' && page.status !== 'PENDING_APPROVAL';
}

/** 编辑器属性 */
interface DocEditorProps {
  pageId: number;
  onTreeChange: () => void;
  /** 页面删除后的回调，用于清理当前选中项 */
  onDeleted?: () => void;
}

export default function DocEditor({ pageId, onTreeChange, onDeleted }: DocEditorProps) {
  const { message, modal } = App.useApp();
  const { token } = theme.useToken();
  const [page, setPage] = useState<DocPage | null>(null);
  const [title, setTitle] = useState('');
  const [loading, setLoading] = useState(true);
  const [dirty, setDirty] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [changeVersion, setChangeVersion] = useState(0);
  const [contentEpoch, setContentEpoch] = useState(0);
  const [conflictOpen, setConflictOpen] = useState(false);
  const [infoOpen, setInfoOpen] = useState(false);
  const [collabActive, setCollabActive] = useState(false);
  const [collabStatus, setCollabStatus] = useState<CollabStatus>('closed');
  const [collabUsers, setCollabUsers] = useState<CollabUser[]>([]);
  const [collabLeader, setCollabLeader] = useState(false);
  const [collabSnapshotSeconds, setCollabSnapshotSeconds] = useState(12);
  const [collabEditor, setCollabEditor] = useState<Editor | null>(null);
  const [approvalEnabled, setApprovalEnabled] = useState(false);
  const [shareOpen, setShareOpen] = useState(false);
  const [aiOpen, setAiOpen] = useState(false);
  const [aclOpen, setAclOpen] = useState(false);

  const pageRef = useRef<DocPage | null>(null);
  const titleRef = useRef('');
  const contentRef = useRef<JSONContent | null>(null);
  const revisionRef = useRef(0);
  const dirtyRef = useRef(false);
  const savingRef = useRef(false);
  const changeSeqRef = useRef(0);
  const treeChangeRef = useRef(onTreeChange);
  /** 协作会话是否已就绪（用于保存分流与自动保存停用） */
  const collabActiveRef = useRef(false);
  /** 当前协作 provider */
  const providerRef = useRef<DocCollabProvider | null>(null);
  /** 绑定 Y.Doc 的第二个编辑器实例 */
  const collabEditorRef = useRef<Editor | null>(null);
  /** 手动保存等待 snapshot-ack 的 resolver */
  const ackResolverRef = useRef<((saved: boolean) => void) | null>(null);
  /** 最近一次快照发出时的本地变更序号 */
  const pendingSnapshotSeqRef = useRef<number | null>(null);
  /** 协作失败后不再自动重启，避免反复回退 */
  const collabDisabledRef = useRef(false);
  /** 导入文件选择框 */
  const importInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    treeChangeRef.current = onTreeChange;
  }, [onTreeChange]);

  /** 标记本地修改，触发防抖保存 */
  const markChange = useCallback(() => {
    changeSeqRef.current += 1;
    dirtyRef.current = true;
    setDirty(true);
    setChangeVersion((value) => value + 1);
  }, []);

  const editor = useEditor(
    {
      extensions: [
        StarterKit,
        Underline,
        Link.configure({ openOnClick: false, autolink: true }),
        Image,
        Placeholder.configure({ placeholder: '开始编写正文…' }),
      ],
      content: EMPTY_DOC,
      editable: false,
      onUpdate: ({ editor: instance }) => {
        contentRef.current = instance.getJSON();
        markChange();
      },
    },
    [markChange],
  );

  /** 应用服务端页面，重置本地编辑状态 */
  const applyPage = useCallback((loaded: DocPage) => {
    pageRef.current = loaded;
    setPage(loaded);
    titleRef.current = loaded.title;
    setTitle(loaded.title);
    revisionRef.current = loaded.revision;
    contentRef.current = parseDocJson(loaded.contentJson) as JSONContent | null;
    dirtyRef.current = false;
    setDirty(false);
    setSavedAt(loaded.updateTime ?? null);
    setContentEpoch((value) => value + 1);
  }, []);

  /** 拉取最新页面 */
  const reload = useCallback(async () => {
    setLoading(true);
    try {
      applyPage(await pageApi.detail(pageId));
    } catch (error) {
      message.error((error as Error).message);
    } finally {
      setLoading(false);
    }
  }, [pageId, applyPage, message]);

  useEffect(() => {
    void reload();
  }, [reload]);

  useEffect(() => {
    docApi.meta().then((meta) => setApprovalEnabled(meta.approvalEnabled)).catch(() => undefined);
  }, []);

  // 编辑器就绪或重新载入后写入正文并设置编辑态（协作期间由 collabEditor 承载正文）
  useEffect(() => {
    if (!editor || contentEpoch === 0 || collabActive) {
      return;
    }
    const current = pageRef.current;
    if (!current) {
      return;
    }
    editor.commands.setContent(contentRef.current ?? EMPTY_DOC, false);
    editor.setEditable(isPageEditable(current));
  }, [editor, contentEpoch, collabActive]);

  /** 让 leader 提交完整正文快照（异步确认由 save 或周期回调处理） */
  const sendCollabSnapshot = useCallback((): boolean => {
    const provider = providerRef.current;
    const active = collabEditorRef.current;
    if (!provider || !active || active.isDestroyed || !provider.isLeader()) {
      return false;
    }
    pendingSnapshotSeqRef.current = changeSeqRef.current;
    provider.sendSnapshot(provider.getTitle() || titleRef.current, active.getJSON());
    return true;
  }, []);

  /** 应用快照确认：刷新 revision/savedAt，并在自己的快照命中时清除未保存标记 */
  const applyCollabSnapshot = useCallback(
    (revision: number, savedAt: string | null, seq: number | null, skipped: boolean) => {
      revisionRef.current = revision;
      const current = pageRef.current;
      if (current) {
        const sharedTitle = providerRef.current?.getTitle();
        const updated: DocPage = {
          ...current,
          title: sharedTitle || current.title,
          revision,
          updateTime: savedAt ?? current.updateTime,
        };
        pageRef.current = updated;
        setPage(updated);
      }
      if (savedAt) {
        setSavedAt(savedAt);
      }
      const ownSnapshot = seq !== null && changeSeqRef.current === seq;
      // 非 leader 收到 leader 快照确认时，说明共享状态已落库；后续本地改动会重新置脏
      const peerSnapshot = seq === null && !providerRef.current?.isLeader();
      if (ownSnapshot || peerSnapshot) {
        dirtyRef.current = false;
        setDirty(false);
      } else {
        setChangeVersion((value) => value + 1);
      }
      if (!skipped) {
        treeChangeRef.current();
      }
    },
    [],
  );

  /** 销毁协作编辑器与 provider（可选保留当前正文供 M1 编辑器续用） */
  const detachCollab = useCallback((captureContent: boolean): boolean => {
    const active = collabEditorRef.current;
    collabEditorRef.current = null;
    let captured = false;
    if (active && !active.isDestroyed) {
      if (captureContent) {
        try {
          contentRef.current = active.getJSON();
          captured = true;
        } catch {
          // 编辑器异常时保留上一份本地正文
        }
      }
      active.destroy();
    }
    setCollabEditor(null);
    const provider = providerRef.current;
    providerRef.current = null;
    provider?.destroy();
    pendingSnapshotSeqRef.current = null;
    ackResolverRef.current?.(false);
    ackResolverRef.current = null;
    collabActiveRef.current = false;
    setCollabActive(false);
    setCollabLeader(false);
    setCollabUsers([]);
    return captured;
  }, []);

  /** WS 故障后回退 M1 REST 自动保存，保留协作期间已产生但未落库的正文 */
  const fallbackFromCollab = useCallback(
    (reason?: string) => {
      collabDisabledRef.current = true;
      const hadUnsaved = dirtyRef.current;
      const captured = detachCollab(true);
      setCollabStatus('failed');
      setContentEpoch((value) => value + 1);
      if (captured && hadUnsaved) {
        markChange();
      }
      if (reason) {
        message.warning(reason);
      }
    },
    [detachCollab, markChange, message],
  );

  /** 创建绑定 Y.Doc 的协作编辑器（首个客户端用 REST 正文 seed） */
  const mountCollabEditor = useCallback(
    (current: DocPage, provider: DocCollabProvider, payload: CollabReadyPayload) => {
      const schema = editor?.schema;
      if (!schema) {
        fallbackFromCollab('实时协作初始化失败');
        return;
      }
      if (payload.updates.length === 0 && !payload.isReconnect) {
        provider.seed(schema, contentRef.current ?? EMPTY_DOC, titleRef.current);
      }
      const user = getCurrentUser();
      const name = user?.nickname || user?.username || '访客';
      const color = collabColor(user?.userId);
      const built = new Editor({
        extensions: [
          // 协作模式由 Yjs UndoManager 接管撤销，需关闭本地 history
          StarterKit.configure({ history: false }),
          Underline,
          Link.configure({ openOnClick: false, autolink: true }),
          Image,
          Placeholder.configure({ placeholder: '开始编写正文…' }),
          Collaboration.configure({ document: provider.doc }),
          CollaborationCursor.configure({ provider, user: { name, color } }),
        ],
        editable: isPageEditable(current),
        onUpdate: () => markChange(),
      });
      collabEditorRef.current = built;
      setCollabEditor(built);
    },
    [editor, fallbackFromCollab, markChange],
  );

  /** 申请 ticket 并建立协作连接（仅在服务端开关开启且页面可编辑时执行） */
  const startCollab = useCallback(
    async (current: DocPage) => {
      if (collabDisabledRef.current || providerRef.current) {
        return;
      }
      if (!editor || current.pageType !== 'PAGE' || !isPageEditable(current)) {
        return;
      }
      let enabled = false;
      try {
        enabled = (await docApi.meta()).collabEnabled;
      } catch {
        return;
      }
      if (!enabled || collabDisabledRef.current || providerRef.current || pageRef.current?.id !== current.id) {
        return;
      }
      const user = getCurrentUser();
      const name = user?.nickname || user?.username || '访客';
      const provider = new DocCollabProvider(current.id, { name, color: collabColor(user?.userId) }, {
        onStatus: (status) => setCollabStatus(status),
        onReady: (payload) => {
          if (providerRef.current !== provider) {
            return;
          }
          revisionRef.current = payload.revision;
          setCollabSnapshotSeconds(payload.snapshotSeconds);
          setCollabLeader(payload.leader);
          setCollabUsers(payload.users);
          collabActiveRef.current = true;
          setCollabActive(true);
          // 标题通过共享 Y.Text 同步，优先采用协作会话中的最新标题
          const sharedTitle = provider.getTitle();
          if (sharedTitle) {
            titleRef.current = sharedTitle;
            setTitle(sharedTitle);
          }
          // 重连沿用已挂载的编辑器，仅首次 ready 创建协作编辑器
          if (!payload.isReconnect) {
            mountCollabEditor(current, provider, payload);
          }
          dirtyRef.current = false;
          setDirty(false);
          // 更新日志可能包含尚未落库的增量，leader 需要在下个周期补一次快照
          if (payload.updates.length > 0) {
            markChange();
          }
        },
        onPresence: (users) => setCollabUsers(users),
        onLeader: (leader) => setCollabLeader(leader),
        onTitle: (value) => {
          titleRef.current = value;
          setTitle(value);
          markChange();
        },
        onSnapshotAck: (revision, savedAt, skipped) => {
          const seq = pendingSnapshotSeqRef.current;
          pendingSnapshotSeqRef.current = null;
          applyCollabSnapshot(revision, savedAt, seq, skipped);
          ackResolverRef.current?.(true);
        },
        // 其他协作者的更新也需要让 leader 在周期快照中落库
        onRemoteUpdate: () => markChange(),
        onError: (text) => fallbackFromCollab(text),
      });
      providerRef.current = provider;
      await provider.connect();
    },
    [editor, mountCollabEditor, fallbackFromCollab, applyCollabSnapshot, markChange],
  );

  /** 保存页面正文与标题（协作中就由 leader 提交快照并等待确认） */
  const save = useCallback(
    async (force = false): Promise<boolean> => {
      const current = pageRef.current;
      if (!current || !isPageEditable(current)) {
        return false;
      }
      if (!force && !dirtyRef.current) {
        return true;
      }
      if (collabActiveRef.current) {
        const provider = providerRef.current;
        if (!provider) {
          return false;
        }
        if (!provider.isLeader()) {
          message.info('页面正在协作编辑中，内容将由当前主编辑者自动保存');
          return false;
        }
        if (savingRef.current) {
          return false;
        }
        savingRef.current = true;
        setSaving(true);
        const sent = sendCollabSnapshot();
        const saved = sent
          ? await new Promise<boolean>((resolve) => {
              const timer = window.setTimeout(() => {
                ackResolverRef.current = null;
                resolve(false);
              }, SNAPSHOT_ACK_TIMEOUT_MS);
              ackResolverRef.current = (ok) => {
                window.clearTimeout(timer);
                ackResolverRef.current = null;
                resolve(ok);
              };
            })
          : false;
        savingRef.current = false;
        setSaving(false);
        if (!saved) {
          message.warning('协作快照确认超时，将在下个周期自动重试');
        }
        return saved;
      }
      if (savingRef.current) {
        return false;
      }
      savingRef.current = true;
      setSaving(true);
      const seq = changeSeqRef.current;
      try {
        const result = await pageApi.save(current.id, {
          baseRevision: revisionRef.current,
          title: titleRef.current,
          contentJson: contentRef.current,
        });
        revisionRef.current = result.revision;
        const updated: DocPage = {
          ...current,
          title: titleRef.current,
          revision: result.revision,
          status: result.status,
          updateTime: result.savedAt ?? current.updateTime,
        };
        pageRef.current = updated;
        setPage(updated);
        setSavedAt(result.savedAt ?? null);
        if (changeSeqRef.current === seq) {
          dirtyRef.current = false;
          setDirty(false);
        } else {
          setChangeVersion((value) => value + 1);
        }
        treeChangeRef.current();
        return true;
      } catch (error) {
        if (error instanceof ApiError && error.code === 40901) {
          setConflictOpen(true);
        } else if (error instanceof ApiError && error.code === 40902) {
          message.info('页面正在协作编辑中，正在尝试加入协作…');
          await startCollab(current);
        } else {
          message.error((error as Error).message);
        }
        return false;
      } finally {
        savingRef.current = false;
        setSaving(false);
      }
    },
    [message, sendCollabSnapshot, startCollab],
  );

  // 协作期间正文由 leader 周期快照落库，停用 REST 防抖自动保存
  useEffect(() => {
    if (!dirty || !page || !isPageEditable(page) || collabActive) {
      return;
    }
    const timer = window.setTimeout(() => {
      void save();
    }, AUTOSAVE_MS);
    return () => window.clearTimeout(timer);
  }, [dirty, changeVersion, page, collabActive, save]);

  /** 参与协作的页面标识（用于在切换/发布/归档后重新判定是否开启协作） */
  const collabPageKey = page
    ? `${page.id}:${page.status}:${page.canEdit ? 1 : 0}:${page.pageType}`
    : '';

  // 页面切换或卸载时销毁协作会话
  useEffect(() => {
    return () => {
      detachCollab(false);
      collabDisabledRef.current = false;
    };
  }, [pageId, detachCollab]);

  // 可编辑页面在服务端开启协作时自动加入实时协作
  useEffect(() => {
    if (!collabPageKey || !editor) {
      return;
    }
    const current = pageRef.current;
    if (!current || current.pageType !== 'PAGE' || !isPageEditable(current)) {
      return;
    }
    void startCollab(current);
  }, [collabPageKey, editor, startCollab]);

  // leader 周期提交快照：本地或他人更新都会置脏，服务端正文未变时自动跳过
  useEffect(() => {
    if (!collabActive || !collabLeader) {
      return;
    }
    const interval = Math.max(5, collabSnapshotSeconds) * 1000;
    const timer = window.setInterval(() => {
      if (!dirtyRef.current || savingRef.current) {
        return;
      }
      sendCollabSnapshot();
    }, interval);
    return () => window.clearInterval(timer);
  }, [collabActive, collabLeader, collabSnapshotSeconds, sendCollabSnapshot]);

  const handleTitleChange = (value: string) => {
    titleRef.current = value;
    setTitle(value);
    if (collabActiveRef.current && providerRef.current) {
      // 标题写入共享 Y.Text，远端改名与 leader 快照都以它为准
      providerRef.current.setTitle(value);
      return;
    }
    markChange();
  };

  const loadLatest = async () => {
    setConflictOpen(false);
    await reload();
  };

  const overwrite = async () => {
    setConflictOpen(false);
    try {
      const latest = await pageApi.detail(pageId);
      revisionRef.current = latest.revision;
      await save(true);
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const publish = () => {
    if (!page) {
      return;
    }
    modal.confirm({
      title: approvalEnabled ? '提交发布审批？' : '发布当前页面？',
      content: approvalEnabled
        ? '审批期间页面只读，审批通过后自动发布。'
        : '发布后，公开空间中的该页面将对全员可读。',
      okText: approvalEnabled ? '提交审批' : '发布',
      onOk: async () => {
        if (dirtyRef.current && !(await save(true))) {
          return;
        }
        if (approvalEnabled) {
          await pageApi.submitApproval(page.id);
          message.success('已提交发布审批');
        } else {
          await pageApi.publish(page.id);
          message.success('页面已发布');
        }
        await reload();
        treeChangeRef.current();
      },
    });
  };

  /** 导出当前页面为 Markdown / HTML */
  const exportCurrent = async (format: ExportFormat) => {
    if (!page) {
      return;
    }
    try {
      if (dirtyRef.current && !(await save(true))) {
        return;
      }
      const blob = await transferApi.exportPage(page.id, format);
      downloadBlob(blob, `${page.slug || `doc-${page.id}`}.${format}`);
      message.success('导出成功');
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  /** 触发导入文件选择 */
  const triggerImport = () => {
    importInputRef.current?.click();
  };

  /** 导入 Markdown / HTML 覆盖正文 */
  const handleImportFile = async (file: File) => {
    if (!page) {
      return;
    }
    modal.confirm({
      title: '导入将覆盖当前正文',
      content: `确认使用「${file.name}」覆盖当前页面正文？`,
      okText: '导入',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          detachCollab(false);
          const result = await transferApi.importPage(page.id, file);
          message.success(`已导入，revision r${result.revision}`);
          await reload();
          treeChangeRef.current();
        } catch (error) {
          message.error((error as Error).message);
        }
      },
    });
  };

  /** 复制当前页面（含子页面） */
  const duplicatePage = () => {
    if (!page) {
      return;
    }
    modal.confirm({
      title: '复制当前页面？',
      content: '将复制页面及全部子页面到同一父节点下，附件引用会一并复制。',
      okText: '复制',
      onOk: async () => {
        try {
          await pageApi.duplicate(page.id, {
            spaceId: page.spaceId,
            parentId: page.parentId,
            includeChildren: true,
          });
          message.success('页面已复制');
          treeChangeRef.current();
        } catch (error) {
          message.error((error as Error).message);
        }
      },
    });
  };

  /** 将当前正文保存为模板 */
  const saveAsTemplate = async () => {
    if (!page) {
      return;
    }
    const name = window.prompt('模板名称', page.title);
    if (!name || !name.trim()) {
      return;
    }
    try {
      const collab = collabEditorRef.current;
      const contentJson =
        collab && !collab.isDestroyed ? (collab.getJSON() as DocJson) : (contentRef.current as DocJson);
      await templateApi.create({
        spaceId: page.spaceId,
        name: name.trim(),
        description: `由页面《${page.title}》保存`,
        pageType: 'PAGE',
        icon: page.icon ?? undefined,
        contentJson,
      });
      message.success('已保存为模板');
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  const archive = () => {
    if (!page) {
      return;
    }
    modal.confirm({
      title: '归档当前页面？',
      content: '归档后页面不可编辑，可随时取消归档。',
      okText: '归档',
      okButtonProps: { danger: true },
      onOk: async () => {
        if (dirtyRef.current && !(await save(true))) {
          return;
        }
        await pageApi.archive(page.id);
        detachCollab(false);
        message.success('页面已归档');
        await reload();
        treeChangeRef.current();
      },
    });
  };

  const restore = async () => {
    if (!page) {
      return;
    }
    await pageApi.restore(page.id);
    message.success('页面已取消归档');
    await reload();
    treeChangeRef.current();
  };

  const removePage = () => {
    if (!page) {
      return;
    }
    modal.confirm({
      title: `删除页面「${page.title}」？`,
      content: '页面及其子页面将移入回收站，可在回收站中恢复（默认保留 30 天）。',
      okText: '移入回收站',
      okButtonProps: { danger: true },
      onOk: async () => {
        detachCollab(false);
        await pageApi.remove(page.id);
        message.success('页面已移入回收站');
        treeChangeRef.current();
        onDeleted?.();
      },
    });
  };

  const toggleStar = async () => {
    if (!page) {
      return;
    }
    try {
      const result = await starApi.toggle(page.id);
      const updated = { ...page, starred: result.starred };
      pageRef.current = updated;
      setPage(updated);
      message.success(result.starred ? '已收藏' : '已取消收藏');
    } catch (error) {
      message.error((error as Error).message);
    }
  };

  if (loading && !page) {
    return (
      <div style={{ padding: 48, textAlign: 'center' }}>
        <Spin />
      </div>
    );
  }

  if (!page) {
    return <Text type="secondary">页面不存在或无权访问。</Text>;
  }

  const editable = isPageEditable(page);
  const readOnly = !editable;
  const isFolder = page.pageType === 'FOLDER';
  const canPublish = editable && hasPermission('sys:doc:page:publish');
  const canArchive = editable && hasPermission('sys:doc:page:archive');
  const canDelete = page.canManage && hasPermission('sys:doc:page:delete');
  const canShare = hasPermission('sys:doc:share:manage') && page.status !== 'ARCHIVED';
  const canAcl = page.canManage && hasPermission('sys:doc:page:acl');
  const canExport = hasPermission('sys:doc:export');
  const canImport = editable && !isFolder && hasPermission('sys:doc:import');
  const canDuplicate = editable && hasPermission('sys:doc:page:add');
  const canSaveTemplate = editable && hasPermission('sys:doc:template:manage');
  const canUseAi = !isFolder && hasPermission('sys:agent:chat');
  const exportItems = [
    { key: 'md', label: '导出 Markdown' },
    { key: 'html', label: '导出 HTML' },
  ];
  const activeEditor = collabActive ? collabEditor : editor;
  const collabLabel =
    collabStatus === 'reconnecting' ? '协作重连中' : collabLeader ? '协作中 · 主编辑' : '协作中';
  const saveText = saving
    ? collabActive
      ? '协作保存中…'
      : '保存中…'
    : dirty
      ? '有未保存更改'
      : `${collabActive ? '已同步' : '已保存'}${savedAt ? ` · ${formatTime(savedAt)}` : ''}`;

  return (
    <div className="doc-editor">
      <div className="doc-editor-breadcrumb">
        <Text type="secondary">
          {(page.breadcrumb ?? []).map((item) => item.title).join(' / ') || page.title}
        </Text>
      </div>

      <div className="doc-editor-title-row">
        <Input
          variant="borderless"
          value={title}
          readOnly={readOnly}
          onChange={(event) => handleTitleChange(event.target.value)}
          onBlur={() => {
            if (dirtyRef.current) {
              void save();
            }
          }}
          onPressEnter={(event) => event.currentTarget.blur()}
          placeholder="未命名页面"
          style={{ fontSize: 26, fontWeight: 600, paddingInline: 0 }}
        />
        <Space wrap size={8}>
          <Tag color={STATUS_COLOR[page.status]}>{STATUS_LABEL[page.status]}</Tag>
          {page.restricted && <Tag color="red">受限页面</Tag>}
          {page.approvalStatus === 'REJECTED' && <Tag color="red">审批驳回</Tag>}
          {editable && (
            <Button
              type="primary"
              icon={<CheckCircleOutlined />}
              loading={saving}
              onClick={() => void save(true)}
            >
              保存
            </Button>
          )}
          {canPublish && (
            <Button onClick={publish}>
              {approvalEnabled ? '提交审批' : page.status === 'PUBLISHED' ? '重新发布' : '发布'}
            </Button>
          )}
          {canShare && (
            <Button icon={<ShareAltOutlined />} onClick={() => setShareOpen(true)}>
              分享
            </Button>
          )}
          {canAcl && (
            <Button icon={<LockOutlined />} onClick={() => setAclOpen(true)}>
              页面权限
            </Button>
          )}
          {canExport && (
            <Dropdown
              menu={{
                items: exportItems,
                onClick: ({ key }) => void exportCurrent(key as ExportFormat),
              }}
            >
              <Button icon={<DownloadOutlined />}>导出</Button>
            </Dropdown>
          )}
          {canImport && (
            <Button icon={<ImportOutlined />} onClick={triggerImport}>
              导入
            </Button>
          )}
          {canDuplicate && (
            <Button icon={<CopyOutlined />} onClick={duplicatePage}>
              复制
            </Button>
          )}
          {canSaveTemplate && (
            <Button type="text" onClick={() => void saveAsTemplate()}>
              存为模板
            </Button>
          )}
          {canUseAi && (
            <Button type="text" icon={<RobotOutlined />} onClick={() => setAiOpen(true)}>
              AI 问答
            </Button>
          )}
          {canArchive && (
            <Button danger onClick={archive}>
              归档
            </Button>
          )}
          {page.canEdit && page.status === 'ARCHIVED' && hasPermission('sys:doc:page:archive') && (
            <Button icon={<HistoryOutlined />} onClick={() => void restore()}>
              取消归档
            </Button>
          )}
          {canDelete && (
            <Button danger icon={<DeleteOutlined />} onClick={removePage}>
              删除
            </Button>
          )}
          <Tooltip title={page.starred ? '取消收藏' : '收藏'}>
            <Button type="text" onClick={() => void toggleStar()}>
              {page.starred ? '★' : '☆'}
            </Button>
          </Tooltip>
          <Button type="text" icon={<MenuOutlined />} onClick={() => setInfoOpen(true)}>
            信息
          </Button>
        </Space>
      </div>

      <div className="doc-editor-status">
        <Space size={8} wrap align="center">
          <Text type="secondary">
            r{page.revision} · {saveText}
          </Text>
          {collabActive && <Tag color={collabStatus === 'reconnecting' ? 'gold' : 'blue'}>{collabLabel}</Tag>}
          {readOnly && <Tag>只读</Tag>}
          {collabActive && collabUsers.length > 0 && (
            <Avatar.Group size={24} max={{ count: 4 }}>
              {collabUsers.map((user) => (
                <Tooltip key={user.clientId} title={user.name}>
                  <Avatar style={{ backgroundColor: user.color, fontSize: 12 }}>{avatarText(user.name)}</Avatar>
                </Tooltip>
              ))}
            </Avatar.Group>
          )}
        </Space>
      </div>

      <Divider style={{ marginBlock: 12 }} />

      {isFolder ? (
        <div className="doc-editor-folder-hint">
          <Title level={5}>目录节点</Title>
          <Text type="secondary">目录用于组织页面层级，仅维护标题与排序；正文请在子页面中编写。</Text>
        </div>
      ) : (
        <>
          {!readOnly && <EditorToolbar editor={activeEditor} />}
          <div className="doc-editor-content" style={{ borderColor: token.colorBorderSecondary }}>
            {collabActive && !collabEditor ? (
              <div style={{ padding: 48, textAlign: 'center' }}>
                <Spin />
              </div>
            ) : (
              <EditorContent editor={activeEditor} />
            )}
          </div>
        </>
      )}

      <InfoPanel
        open={infoOpen}
        page={page}
        onClose={() => setInfoOpen(false)}
        onReload={reload}
        onTreeChange={() => treeChangeRef.current()}
      />

      <Modal
        open={conflictOpen}
        title="内容已被他人更新"
        closable={false}
        maskClosable={false}
        footer={[
          <Button key="later" onClick={() => setConflictOpen(false)}>
            稍后处理
          </Button>,
          <Button key="latest" onClick={() => void loadLatest()}>
            载入最新版本
          </Button>,
          <Button key="overwrite" type="primary" danger onClick={() => void overwrite()}>
            用我的内容覆盖
          </Button>,
        ]}
      >
        <Text>
          当前页面 revision 已变化（可能由他人或自动保存写入）。载入最新版本会丢弃本地修改；
          覆盖会用本地内容生成新的 revision。
        </Text>
      </Modal>

      <input
        ref={importInputRef}
        type="file"
        accept=".md,.markdown,.txt,.html,.htm"
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = '';
          if (file) {
            void handleImportFile(file);
          }
        }}
      />

      <ShareDrawer pageId={page.id} open={shareOpen} onClose={() => setShareOpen(false)} />
      <DocAclDrawer
        pageId={page.id}
        pageTitle={page.title}
        open={aclOpen}
        onClose={() => setAclOpen(false)}
        onUpdated={() => {
          void reload();
          treeChangeRef.current();
        }}
      />
      <DocAiDrawer open={aiOpen} onClose={() => setAiOpen(false)} pageTitle={page.title} />
    </div>
  );
}

/** 编辑器工具栏 */
function EditorToolbar({ editor }: { editor: Editor | null }) {
  if (!editor) {
    return null;
  }
  const setLink = () => {
    const previous = editor.getAttributes('link').href as string | undefined;
    const url = window.prompt('链接地址（留空移除链接）', previous ?? 'https://');
    if (url === null) {
      return;
    }
    if (!url) {
      editor.chain().focus().extendMarkRange('link').unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url }).run();
  };
  const setImage = () => {
    const url = window.prompt('图片地址');
    if (url) {
      editor.chain().focus().setImage({ src: url }).run();
    }
  };
  return (
    <Space wrap size={2} className="doc-editor-toolbar">
      <Tooltip title="撤销">
        <Button size="small" type="text" icon={<UndoOutlined />} disabled={!editor.can().undo()}
          aria-label="撤销" onClick={() => editor.chain().focus().undo().run()} />
      </Tooltip>
      <Tooltip title="重做">
        <Button size="small" type="text" icon={<RedoOutlined />} disabled={!editor.can().redo()}
          aria-label="重做" onClick={() => editor.chain().focus().redo().run()} />
      </Tooltip>
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('heading', { level: 1 }) ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}>H1</Button>
      <Button size="small" type={editor.isActive('heading', { level: 2 }) ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}>H2</Button>
      <Button size="small" type={editor.isActive('heading', { level: 3 }) ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}>H3</Button>
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('bold') ? 'primary' : 'text'} icon={<BoldOutlined />}
        aria-label="加粗" onClick={() => editor.chain().focus().toggleBold().run()} />
      <Button size="small" type={editor.isActive('italic') ? 'primary' : 'text'} icon={<ItalicOutlined />}
        aria-label="斜体" onClick={() => editor.chain().focus().toggleItalic().run()} />
      <Button size="small" type={editor.isActive('underline') ? 'primary' : 'text'} icon={<UnderlineOutlined />}
        aria-label="下划线" onClick={() => editor.chain().focus().toggleUnderline().run()} />
      <Button size="small" type={editor.isActive('strike') ? 'primary' : 'text'} icon={<StrikethroughOutlined />}
        aria-label="删除线" onClick={() => editor.chain().focus().toggleStrike().run()} />
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('bulletList') ? 'primary' : 'text'}
        icon={<UnorderedListOutlined />} aria-label="无序列表"
        onClick={() => editor.chain().focus().toggleBulletList().run()} />
      <Button size="small" type={editor.isActive('orderedList') ? 'primary' : 'text'}
        icon={<OrderedListOutlined />} aria-label="有序列表"
        onClick={() => editor.chain().focus().toggleOrderedList().run()} />
      <Button size="small" type={editor.isActive('blockquote') ? 'primary' : 'text'}
        onClick={() => editor.chain().focus().toggleBlockquote().run()}>引用</Button>
      <Button size="small" type={editor.isActive('codeBlock') ? 'primary' : 'text'} icon={<CodeOutlined />}
        aria-label="代码块" onClick={() => editor.chain().focus().toggleCodeBlock().run()} />
      <Divider type="vertical" />
      <Button size="small" type={editor.isActive('link') ? 'primary' : 'text'} icon={<LinkOutlined />}
        aria-label="链接" onClick={setLink} />
      <Button size="small" type="text" icon={<PictureOutlined />} aria-label="插入图片" onClick={setImage} />
      <Button size="small" type="text" icon={<UnorderedListOutlined />} aria-label="分隔线"
        onClick={() => editor.chain().focus().setHorizontalRule().run()} />
      <Button size="small" type="text" icon={<DeleteOutlined />} danger aria-label="清除格式"
        onClick={() => editor.chain().focus().clearNodes().run()} />
    </Space>
  );
}
