// 页面编辑器容器：页面加载、REST 保存与自动保存、冲突处理；工具栏/头部视图/协作会话/页面操作拆分至 DocEditor/ 子目录

import { useCallback, useEffect, useRef, useState } from 'react';
import { App, Divider, Spin, Typography, theme } from 'antd';
import { EditorContent, useEditor, type JSONContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Image from '@tiptap/extension-image';
import Link from '@tiptap/extension-link';
import Placeholder from '@tiptap/extension-placeholder';
import Underline from '@tiptap/extension-underline';
import { ApiError } from '@bluewhale/http';
import { docApi } from '../api/doc';
import { pageApi } from '../api/page';
import type { DocPage } from '../api/types';
import { parseDocJson } from '../utils/docContent';
import EditorHeader from './DocEditor/EditorHeader';
import EditorOverlays from './DocEditor/EditorOverlays';
import EditorToolbar from './DocEditor/EditorToolbar';
import { AUTOSAVE_MS, EMPTY_DOC, isPageEditable } from './DocEditor/editorShared';
import { useDocCollab } from './DocEditor/useDocCollab';
import { useDocPageActions } from './DocEditor/useDocPageActions';

const { Text, Title } = Typography;

/** 编辑器属性 */
interface DocEditorProps {
  pageId: number;
  onTreeChange: () => void;
  /** 页面删除后的回调，用于清理当前选中项 */
  onDeleted?: () => void;
}

export default function DocEditor({ pageId, onTreeChange, onDeleted }: DocEditorProps) {
  const { message } = App.useApp();
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

  const {
    collabActive,
    collabStatus,
    collabLeader,
    collabUsers,
    collabEditor,
    collabActiveRef,
    collabEditorRef,
    startCollab,
    detachCollab,
    saveViaCollab,
    handleTitleChange,
  } = useDocCollab({
    pageId,
    editor,
    markChange,
    // 协作失败回退 M1 编辑器时递增内容纪元
    bumpContentEpoch: useCallback(() => setContentEpoch((value) => value + 1), []),
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
  });

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

  /** 保存页面正文与标题（协作中由 leader 提交快照并等待确认） */
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
        return saveViaCollab();
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
    [message, saveViaCollab, startCollab, collabActiveRef],
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

  const {
    publish,
    exportCurrent,
    handleImportFile,
    duplicatePage,
    saveAsTemplate,
    archive,
    restore,
    removePage,
    toggleStar,
    loadLatest,
    overwrite,
    triggerImport,
  } = useDocPageActions({
    pageId,
    page,
    approvalEnabled,
    save,
    reload,
    dirtyRef,
    revisionRef,
    treeChangeRef,
    detachCollab,
    collabEditorRef,
    contentRef,
    pageRef,
    setPage,
    setConflictOpen,
    importInputRef,
    onDeleted,
  });

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
  const activeEditor = collabActive ? collabEditor : editor;

  return (
    <div className="doc-editor">
      <EditorHeader
        page={page}
        title={title}
        editable={editable}
        readOnly={readOnly}
        saving={saving}
        dirty={dirty}
        savedAt={savedAt}
        approvalEnabled={approvalEnabled}
        collabActive={collabActive}
        collabStatus={collabStatus}
        collabLeader={collabLeader}
        collabUsers={collabUsers}
        onTitleChange={handleTitleChange}
        onTitleBlur={() => {
          if (dirtyRef.current) {
            void save();
          }
        }}
        onSave={() => void save(true)}
        onPublish={publish}
        onShare={() => setShareOpen(true)}
        onAcl={() => setAclOpen(true)}
        onExport={(format) => void exportCurrent(format)}
        onImport={triggerImport}
        onDuplicate={duplicatePage}
        onSaveTemplate={() => void saveAsTemplate()}
        onAi={() => setAiOpen(true)}
        onArchive={archive}
        onRestore={() => void restore()}
        onDelete={removePage}
        onToggleStar={() => void toggleStar()}
        onInfo={() => setInfoOpen(true)}
      />

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

      <EditorOverlays
        page={page}
        infoOpen={infoOpen}
        onInfoClose={() => setInfoOpen(false)}
        onReload={reload}
        onTreeChange={() => treeChangeRef.current()}
        conflictOpen={conflictOpen}
        onConflictClose={() => setConflictOpen(false)}
        onLoadLatest={() => void loadLatest()}
        onOverwrite={() => void overwrite()}
        importInputRef={importInputRef}
        onImportFile={(file) => void handleImportFile(file)}
        shareOpen={shareOpen}
        onShareClose={() => setShareOpen(false)}
        aclOpen={aclOpen}
        onAclClose={() => setAclOpen(false)}
        onAclUpdated={() => {
          void reload();
          treeChangeRef.current();
        }}
        aiOpen={aiOpen}
        onAiClose={() => setAiOpen(false)}
      />
    </div>
  );
}
