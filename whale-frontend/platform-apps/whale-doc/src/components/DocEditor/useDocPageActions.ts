// 页面级业务操作 hook：发布/审批、导入导出、复制、模板、归档、删除、收藏

import { App } from 'antd';
import type { Dispatch, MutableRefObject, SetStateAction } from 'react';
import type { Editor, JSONContent } from '@tiptap/react';
import { pageApi } from '../../api/page';
import { starApi } from '../../api/star';
import { templateApi } from '../../api/template';
import { downloadBlob, transferApi, type ExportFormat } from '../../api/transfer';
import type { DocJson, DocPage } from '../../api/types';

/** 页面操作 hook 选项 */
interface UseDocPageActionsOptions {
  pageId: number;
  page: DocPage | null;
  approvalEnabled: boolean;
  /** 保存当前页面（发布/归档/导出前先落盘本地修改） */
  save: (force?: boolean) => Promise<boolean>;
  reload: () => Promise<void>;
  dirtyRef: MutableRefObject<boolean>;
  revisionRef: MutableRefObject<number>;
  treeChangeRef: MutableRefObject<() => void>;
  detachCollab: (captureContent: boolean) => boolean;
  collabEditorRef: MutableRefObject<Editor | null>;
  contentRef: MutableRefObject<JSONContent | null>;
  pageRef: MutableRefObject<DocPage | null>;
  setPage: Dispatch<SetStateAction<DocPage | null>>;
  setConflictOpen: Dispatch<SetStateAction<boolean>>;
  /** 导入文件选择框 */
  importInputRef: MutableRefObject<HTMLInputElement | null>;
  /** 页面删除后的回调，用于清理当前选中项 */
  onDeleted?: () => void;
}

/** 页面级操作动作集合（发布、导入导出、复制、模板、归档、删除、收藏） */
export function useDocPageActions({
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
}: UseDocPageActionsOptions) {
  const { message, modal } = App.useApp();

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
        collab && !collab.isDestroyed
          ? (collab.getJSON() as DocJson)
          : (contentRef.current as DocJson);
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

  /** 触发导入文件选择 */
  const triggerImport = () => {
    importInputRef.current?.click();
  };

  return {
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
  };
}
