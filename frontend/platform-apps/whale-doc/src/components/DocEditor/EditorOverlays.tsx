// 编辑器覆盖层视图：信息面板、保存冲突弹窗、导入文件选择与共享/权限/AI 抽屉

import type { MutableRefObject } from 'react';
import { Button, Modal, Typography } from 'antd';
import type { DocPage } from '../../api/types';
import DocAclDrawer from '../DocAclDrawer';
import DocAiDrawer from '../DocAiDrawer';
import InfoPanel from '../InfoPanel';
import ShareDrawer from '../ShareDrawer';

const { Text } = Typography;

/** 覆盖层视图属性 */
interface EditorOverlaysProps {
  page: DocPage;
  infoOpen: boolean;
  onInfoClose: () => void;
  onReload: () => Promise<void>;
  onTreeChange: () => void;
  conflictOpen: boolean;
  onConflictClose: () => void;
  onLoadLatest: () => void;
  onOverwrite: () => void;
  importInputRef: MutableRefObject<HTMLInputElement | null>;
  onImportFile: (file: File) => void;
  shareOpen: boolean;
  onShareClose: () => void;
  aclOpen: boolean;
  onAclClose: () => void;
  onAclUpdated: () => void;
  aiOpen: boolean;
  onAiClose: () => void;
}

/** 编辑器覆盖层：信息面板 + 冲突弹窗 + 导入输入 + 抽屉集合 */
export default function EditorOverlays({
  page,
  infoOpen,
  onInfoClose,
  onReload,
  onTreeChange,
  conflictOpen,
  onConflictClose,
  onLoadLatest,
  onOverwrite,
  importInputRef,
  onImportFile,
  shareOpen,
  onShareClose,
  aclOpen,
  onAclClose,
  onAclUpdated,
  aiOpen,
  onAiClose,
}: EditorOverlaysProps) {
  return (
    <>
      <InfoPanel
        open={infoOpen}
        page={page}
        onClose={onInfoClose}
        onReload={onReload}
        onTreeChange={onTreeChange}
      />

      <Modal
        open={conflictOpen}
        title="内容已被他人更新"
        closable={false}
        maskClosable={false}
        footer={[
          <Button key="later" onClick={onConflictClose}>
            稍后处理
          </Button>,
          <Button key="latest" onClick={onLoadLatest}>
            载入最新版本
          </Button>,
          <Button key="overwrite" type="primary" danger onClick={onOverwrite}>
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
            onImportFile(file);
          }
        }}
      />

      <ShareDrawer pageId={page.id} open={shareOpen} onClose={onShareClose} />
      <DocAclDrawer
        pageId={page.id}
        pageTitle={page.title}
        open={aclOpen}
        onClose={onAclClose}
        onUpdated={onAclUpdated}
      />
      <DocAiDrawer open={aiOpen} onClose={onAiClose} pageTitle={page.title} />
    </>
  );
}
