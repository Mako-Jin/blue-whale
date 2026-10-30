// 页面信息面板容器：评论未读角标轮询与页签编排（页签实现拆分至 InfoPanel/ 子目录）

import { useCallback, useEffect, useState } from 'react';
import { Badge, Modal, Tabs } from 'antd';
import { commentApi } from '../api/comment';
import type { DocPage } from '../api/types';
import AttachmentTab from './InfoPanel/AttachmentTab';
import CommentTab from './InfoPanel/CommentTab';
import VersionTab from './InfoPanel/VersionTab';
import { ActivityTab, BacklinkTab, DetailTab, TagTab } from './InfoPanel/OverviewTabs';

/** 信息面板属性 */
interface InfoPanelProps {
  open: boolean;
  page: DocPage;
  onClose: () => void;
  onReload: () => Promise<void>;
  onTreeChange: () => void;
}

export default function InfoPanel({ open, page, onClose, onReload, onTreeChange }: InfoPanelProps) {
  const [unread, setUnread] = useState(0);
  const handleRead = useCallback(() => setUnread(0), []);

  // 面板打开期间轮询评论未读数，用于页签角标（M2-7）
  useEffect(() => {
    if (!open) {
      return;
    }
    let cancelled = false;
    const load = async () => {
      try {
        const result = await commentApi.unread(page.id);
        if (!cancelled) {
          setUnread(result.unread);
        }
      } catch {
        // 无权读取或页面已删除时忽略角标
      }
    };
    void load();
    const timer = window.setInterval(() => void load(), 15000);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [open, page.id]);

  return (
    <Modal title="页面信息" open={open} onCancel={onClose} footer={null} width={760}>
      <Tabs
        items={[
          { key: 'detail', label: '详情', children: <DetailTab page={page} /> },
          { key: 'tag', label: '标签', children: <TagTab page={page} /> },
          {
            key: 'backlink',
            label: '反向链接',
            children: <BacklinkTab page={page} />,
          },
          { key: 'activity', label: '动态', children: <ActivityTab page={page} /> },
          {
            key: 'version',
            label: '版本历史',
            children: <VersionTab page={page} onReload={onReload} onTreeChange={onTreeChange} />,
          },
          { key: 'attachment', label: '附件', children: <AttachmentTab page={page} /> },
          {
            key: 'comment',
            label: (
              <Badge count={unread} size="small" offset={[10, 0]}>
                评论
              </Badge>
            ),
            children: <CommentTab page={page} onRead={handleRead} />,
          },
        ]}
      />
    </Modal>
  );
}
