// 协作会话管理 hook：provider 生命周期、协作编辑器挂载、leader 周期快照与协作保存路径

import { useCallback, useEffect, useRef, useState } from 'react';
import type { Dispatch, MutableRefObject, SetStateAction } from 'react';
import { App } from 'antd';
import { Editor } from '@tiptap/react';
import type { JSONContent } from '@tiptap/react';
import Collaboration from '@tiptap/extension-collaboration';
import CollaborationCursor from '@tiptap/extension-collaboration-cursor';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import Placeholder from '@tiptap/extension-placeholder';
import StarterKit from '@tiptap/starter-kit';
import Underline from '@tiptap/extension-underline';
import { docApi } from '../../api/doc';
import { getCurrentUser } from '../../api/client';
import type { DocPage } from '../../api/types';
import {
  DocCollabProvider,
  type CollabReadyPayload,
  type CollabStatus,
  type CollabUser,
} from '../../collab/DocCollabProvider';
import { EMPTY_DOC, SNAPSHOT_ACK_TIMEOUT_MS, collabColor, isPageEditable } from './editorShared';

/** 协作 hook 选项：容器与协作会话共享的页面编辑状态 */
interface UseDocCollabOptions {
  pageId: number;
  /** M1 基础编辑器实例（用于 schema 与协作开关判定） */
  editor: Editor | null;
  /** 标记本地修改（协作更新回调使用） */
  markChange: () => void;
  /** 协作失败回退 M1 编辑器时递增内容纪元 */
  bumpContentEpoch: () => void;
  pageRef: MutableRefObject<DocPage | null>;
  setPage: Dispatch<SetStateAction<DocPage | null>>;
  setSavedAt: Dispatch<SetStateAction<string | null>>;
  titleRef: MutableRefObject<string>;
  setTitle: Dispatch<SetStateAction<string>>;
  contentRef: MutableRefObject<JSONContent | null>;
  revisionRef: MutableRefObject<number>;
  dirtyRef: MutableRefObject<boolean>;
  setDirty: Dispatch<SetStateAction<boolean>>;
  savingRef: MutableRefObject<boolean>;
  setSaving: Dispatch<SetStateAction<boolean>>;
  changeSeqRef: MutableRefObject<number>;
  setChangeVersion: Dispatch<SetStateAction<number>>;
  treeChangeRef: MutableRefObject<() => void>;
}

/** 建立并维护页面实时协作会话（含协作模式下的保存路径） */
export function useDocCollab({
  pageId,
  editor,
  markChange,
  bumpContentEpoch,
  pageRef,
  setPage,
  setSavedAt,
  titleRef,
  setTitle,
  contentRef,
  revisionRef,
  dirtyRef,
  setDirty,
  savingRef,
  setSaving,
  changeSeqRef,
  setChangeVersion,
  treeChangeRef,
}: UseDocCollabOptions) {
  const { message } = App.useApp();
  const [collabActive, setCollabActive] = useState(false);
  const [collabStatus, setCollabStatus] = useState<CollabStatus>('closed');
  const [collabUsers, setCollabUsers] = useState<CollabUser[]>([]);
  const [collabLeader, setCollabLeader] = useState(false);
  const [collabSnapshotSeconds, setCollabSnapshotSeconds] = useState(12);
  const [collabEditor, setCollabEditor] = useState<Editor | null>(null);

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
  }, [changeSeqRef, titleRef]);

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
    [changeSeqRef, dirtyRef, pageRef, revisionRef, setChangeVersion, setDirty, setPage, setSavedAt, treeChangeRef],
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
  }, [contentRef]);

  /** WS 故障后回退 M1 REST 自动保存，保留协作期间已产生但未落库的正文 */
  const fallbackFromCollab = useCallback(
    (reason?: string) => {
      collabDisabledRef.current = true;
      const hadUnsaved = dirtyRef.current;
      const captured = detachCollab(true);
      setCollabStatus('failed');
      bumpContentEpoch();
      if (captured && hadUnsaved) {
        markChange();
      }
      if (reason) {
        message.warning(reason);
      }
    },
    [detachCollab, bumpContentEpoch, markChange, message, dirtyRef],
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
    [editor, fallbackFromCollab, markChange, contentRef, titleRef],
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
    [editor, mountCollabEditor, fallbackFromCollab, applyCollabSnapshot, markChange, dirtyRef, pageRef, revisionRef, setDirty, setTitle, titleRef],
  );

  /** 协作模式下的保存路径：leader 提交快照并等待确认 */
  const saveViaCollab = useCallback(async (): Promise<boolean> => {
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
  }, [message, sendCollabSnapshot, savingRef, setSaving]);

  /** 标题变更：协作中写入共享 Y.Text，否则标记本地修改 */
  const handleTitleChange = useCallback(
    (value: string) => {
      titleRef.current = value;
      setTitle(value);
      if (collabActiveRef.current && providerRef.current) {
        // 标题写入共享 Y.Text，远端改名与 leader 快照都以它为准
        providerRef.current.setTitle(value);
        return;
      }
      markChange();
    },
    [markChange, setTitle, titleRef],
  );

  // 页面切换或卸载时销毁协作会话
  useEffect(() => {
    return () => {
      detachCollab(false);
      collabDisabledRef.current = false;
    };
  }, [pageId, detachCollab]);

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
  }, [collabActive, collabLeader, collabSnapshotSeconds, sendCollabSnapshot, dirtyRef, savingRef]);

  return {
    collabActive,
    collabStatus,
    collabUsers,
    collabLeader,
    collabEditor,
    collabActiveRef,
    providerRef,
    collabEditorRef,
    startCollab,
    detachCollab,
    saveViaCollab,
    handleTitleChange,
  };
}
