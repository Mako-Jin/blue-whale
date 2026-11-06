// 邮箱主界面：文件夹 / 邮件列表 / 阅读区 三栏。
//
// 两个刻意的取舍：
// 1. 文件夹与选中邮件写进 query string（`?folder=INBOX&id=12`），
//    这样在子应用内刷新、或从基座切走再切回时不会丢上下文。
// 2. 草稿箱有两个数据源——后端 `mail_draft`（本机草稿，写信框存的）与
//    同步回来的服务器 DRAFT 文件夹邮件。M0 没有 IMAP APPEND，本机草稿发不出去到服务器，
//    所以默认展示本机草稿，另给一个「服务器」页签避免数据看不见。

import {
  Button,
  Empty,
  Input,
  Pagination,
  Result,
  Segmented,
  Spin,
  Tag,
  Typography,
  message as toast,
} from 'antd';
import dayjs from 'dayjs';
import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  getMessage,
  getUnread,
  listAccounts,
  listFolders,
  markFlagged,
  markSeen,
  pageDrafts,
  pageMessages,
  removeDraft,
  removeMessage,
  syncAccount,
  type MailAccount,
  type MailDraft,
  type MailFolder,
  type MailMessage,
  type MailUnread,
} from '../api/mail';
import ComposeModal from '../components/ComposeModal';
import MailReader from '../components/MailReader';

/** 每页条数 */
const PAGE_SIZE = 20;

/** 列表时间显示：当天只显示时分，今年省略年份 */
function formatListTime(value?: string | null): string {
  if (!value) {
    return '';
  }
  const target = dayjs(value);
  if (!target.isValid()) {
    return '';
  }
  const now = dayjs();
  if (target.isSame(now, 'day')) {
    return target.format('HH:mm');
  }
  return target.isSame(now, 'year') ? target.format('MM-DD') : target.format('YYYY-MM-DD');
}

/** 三栏容器样式 */
const PANE_STYLE: CSSProperties = {
  background: '#fff',
  borderRadius: 8,
  border: '1px solid #f0f0f0',
  overflow: 'hidden',
  display: 'flex',
  flexDirection: 'column',
  minHeight: 0,
};

/** 邮箱主界面 */
export default function MailPage() {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const folderCode = searchParams.get('folder') ?? 'INBOX';
  const idParam = searchParams.get('id');
  const selectedId = idParam ? Number(idParam) : null;

  const [accounts, setAccounts] = useState<MailAccount[]>([]);
  const [accountId, setAccountId] = useState<number | undefined>();
  const [accountLoaded, setAccountLoaded] = useState(false);
  const [folders, setFolders] = useState<MailFolder[]>([]);
  const [unread, setUnread] = useState<MailUnread>({ total: 0, byFolder: {} });

  const [rows, setRows] = useState<MailMessage[]>([]);
  const [total, setTotal] = useState(0);
  const [current, setCurrent] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [listLoading, setListLoading] = useState(false);

  const [drafts, setDrafts] = useState<MailDraft[]>([]);
  const [draftTotal, setDraftTotal] = useState(0);
  const [draftSource, setDraftSource] = useState<'LOCAL' | 'SERVER'>('LOCAL');

  const [detail, setDetail] = useState<MailMessage | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [syncing, setSyncing] = useState(false);

  const [composeOpen, setComposeOpen] = useState(false);
  const [editingDraft, setEditingDraft] = useState<MailDraft | null>(null);
  const [replyTo, setReplyTo] = useState<MailMessage | null>(null);

  /** 是否处于「本机草稿」视图：草稿箱 + 本机来源 */
  const localDraftView = folderCode === 'DRAFT' && draftSource === 'LOCAL';

  const loadAccounts = useCallback(async () => {
    try {
      const list = await listAccounts();
      setAccounts(list);
      setAccountId((prev) => prev ?? list[0]?.id);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '邮箱账号加载失败');
    } finally {
      setAccountLoaded(true);
    }
  }, []);

  const loadFolders = useCallback(async () => {
    if (!accountId) {
      return;
    }
    try {
      const list = await listFolders(accountId);
      setFolders(list.filter((item) => item.folderType === 'SYSTEM'));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '文件夹加载失败');
    }
  }, [accountId]);

  const loadUnread = useCallback(async () => {
    try {
      setUnread(await getUnread());
    } catch {
      // 未读统计失败不打断主流程，列表本身仍有未读标记
    }
  }, []);

  const loadList = useCallback(async () => {
    if (!accountId) {
      return;
    }
    setListLoading(true);
    try {
      if (localDraftView) {
        const page = await pageDrafts({ current, size: PAGE_SIZE, accountId });
        setDrafts(page.records);
        setDraftTotal(page.total);
        setRows([]);
        setTotal(0);
      } else {
        const page = await pageMessages({
          accountId,
          folderCode,
          keyword: keyword || undefined,
          current,
          size: PAGE_SIZE,
        });
        setRows(page.records);
        setTotal(page.total);
        setDrafts([]);
      }
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '邮件列表加载失败');
      setRows([]);
      setDrafts([]);
    } finally {
      setListLoading(false);
    }
  }, [accountId, folderCode, keyword, current, localDraftView]);

  useEffect(() => {
    void loadAccounts();
  }, [loadAccounts]);

  useEffect(() => {
    if (!accountId) {
      return;
    }
    setCurrent(1);
    void loadFolders();
    void loadUnread();
  }, [accountId, loadFolders, loadUnread]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  useEffect(() => {
    if (!selectedId || localDraftView) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    void (async () => {
      try {
        const data = await getMessage(selectedId);
        if (cancelled) {
          return;
        }
        setDetail(data);
        if (data.seen !== 1) {
          // 打开即已读：只做乐观更新，失败不回滚（下次同步会纠正）
          setRows((prev) =>
            prev.map((row) => (row.id === data.id ? { ...row, seen: 1 } : row)),
          );
          await markSeen(data.id, true);
          void loadUnread();
        }
      } catch (err) {
        if (!cancelled) {
          toast.error(err instanceof Error ? err.message : '邮件详情加载失败');
        }
      } finally {
        if (!cancelled) {
          setDetailLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [selectedId, localDraftView, loadUnread]);

  /** 切换文件夹（写回 URL 并清空选中邮件） */
  const selectFolder = (code: string) => {
    setCurrent(1);
    setSearchParams({ folder: code });
  };

  /** 选中邮件（写回 URL） */
  const selectMessage = (id: number) => {
    setSearchParams({ folder: folderCode, id: String(id) });
  };

  /** 打开写信框 */
  const openCompose = (options?: { draft?: MailDraft; reply?: MailMessage }) => {
    setEditingDraft(options?.draft ?? null);
    setReplyTo(options?.reply ?? null);
    setComposeOpen(true);
  };

  /** 手动同步 */
  const handleSync = async () => {
    if (!accountId) {
      return;
    }
    setSyncing(true);
    try {
      const added = await syncAccount(accountId);
      toast.success(added > 0 ? `同步完成，新增 ${added} 封` : '同步完成，没有新邮件');
      await Promise.all([loadList(), loadFolders(), loadUnread()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '同步失败，请检查账号凭据与服务器连通性');
    } finally {
      setSyncing(false);
    }
  };

  /** 切换星标 */
  const handleToggleFlag = async (target: MailMessage) => {
    const next = target.flagged !== 1;
    try {
      await markFlagged(target.id, next);
      setDetail((prev) => (prev && prev.id === target.id ? { ...prev, flagged: next ? 1 : 0 } : prev));
      setRows((prev) => prev.map((row) => (row.id === target.id ? { ...row, flagged: next ? 1 : 0 } : row)));
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '星标操作失败');
    }
  };

  /** 切换已读 */
  const handleToggleSeen = async (target: MailMessage) => {
    const next = target.seen !== 1;
    try {
      await markSeen(target.id, next);
      setDetail((prev) => (prev && prev.id === target.id ? { ...prev, seen: next ? 1 : 0 } : prev));
      setRows((prev) => prev.map((row) => (row.id === target.id ? { ...row, seen: next ? 1 : 0 } : row)));
      void loadUnread();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '已读状态更新失败');
    }
  };

  /** 删除邮件（后端为移入回收站） */
  const handleDelete = async (target: MailMessage) => {
    try {
      await removeMessage(target.id);
      toast.success('已移入回收站');
      setDetail(null);
      setSearchParams({ folder: folderCode });
      await Promise.all([loadList(), loadFolders(), loadUnread()]);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '删除失败');
    }
  };

  /** 删除本机草稿 */
  const handleDeleteDraft = async (draft: MailDraft) => {
    try {
      await removeDraft(draft.id);
      toast.success('草稿已删除');
      await loadList();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : '草稿删除失败');
    }
  };

  const folderItems = useMemo(
    () =>
      folders.map((folder) => ({
        ...folder,
        badge: unread.byFolder[folder.folderCode] ?? folder.unreadCount ?? 0,
      })),
    [folders, unread],
  );

  if (accountLoaded && accounts.length === 0) {
    return (
      <Result
        status="info"
        title="还没有绑定邮箱账号"
        subTitle="绑定后即可在这里收发邮件。凭据不落库，只保存配置中心里的键名。"
        extra={
          <Button type="primary" onClick={() => navigate('/account')}>
            去绑定账号
          </Button>
        }
      />
    );
  }

  return (
    <div style={{ display: 'flex', gap: 12, width: '100%', minHeight: 0 }}>
      {/* 第一栏：文件夹 */}
      <div style={{ ...PANE_STYLE, width: 180, flex: '0 0 180px' }}>
        <div style={{ padding: 12, borderBottom: '1px solid #f0f0f0' }}>
          <Button type="primary" block onClick={() => openCompose()}>
            写邮件
          </Button>
        </div>
        <div style={{ flex: 1, overflow: 'auto', padding: 8 }}>
          {folderItems.length === 0 ? (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无文件夹" />
          ) : (
            folderItems.map((folder) => {
              const active = folder.folderCode === folderCode;
              return (
                <div
                  key={folder.id}
                  onClick={() => selectFolder(folder.folderCode)}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    padding: '8px 10px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    background: active ? '#e6f4ff' : 'transparent',
                    color: active ? '#1677ff' : '#1f2328',
                    fontWeight: active ? 600 : 400,
                    marginBottom: 2,
                  }}
                >
                  <span>{folder.name}</span>
                  {folder.badge > 0 ? <Tag color="red">{folder.badge}</Tag> : null}
                </div>
              );
            })
          )}
        </div>
        <div style={{ padding: 8, borderTop: '1px solid #f0f0f0' }}>
          <Button block loading={syncing} onClick={handleSync}>
            同步邮件
          </Button>
        </div>
      </div>

      {/* 第二栏：列表 */}
      <div style={{ ...PANE_STYLE, width: 360, flex: '0 0 360px' }}>
        <div style={{ padding: 8, borderBottom: '1px solid #f0f0f0' }}>
          <Input.Search
            allowClear
            placeholder="搜索主题 / 发件人 / 摘要"
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            onSearch={(value) => {
              setKeyword(value.trim());
              setCurrent(1);
            }}
            disabled={localDraftView}
          />
          {folderCode === 'DRAFT' ? (
            <Segmented
              block
              size="small"
              style={{ marginTop: 8 }}
              value={draftSource}
              onChange={(value) => {
                setCurrent(1);
                setDraftSource(value as 'LOCAL' | 'SERVER');
              }}
              options={[
                { label: '本机草稿', value: 'LOCAL' },
                { label: '服务器', value: 'SERVER' },
              ]}
            />
          ) : null}
        </div>

        <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
          {listLoading ? (
            <div style={{ padding: 40, textAlign: 'center' }}>
              <Spin />
            </div>
          ) : localDraftView ? (
            drafts.length === 0 ? (
              <Empty style={{ marginTop: 48 }} image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有本机草稿" />
            ) : (
              drafts.map((draft) => (
                <div
                  key={draft.id}
                  onClick={() => openCompose({ draft })}
                  style={{ padding: '10px 12px', borderBottom: '1px solid #f5f5f5', cursor: 'pointer' }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <Typography.Text strong ellipsis style={{ flex: 1 }}>
                      {draft.subject || '(无主题)'}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                      {formatListTime(draft.updateTime)}
                    </Typography.Text>
                  </div>
                  <div style={{ marginTop: 4, fontSize: 12, color: '#8c8c8c' }}>
                    <span style={{ marginRight: 8 }}>收件人：{draft.toAddrs || '(未填写)'}</span>
                    <Button
                      type="link"
                      size="small"
                      danger
                      style={{ padding: 0, height: 'auto', fontSize: 12 }}
                      onClick={(event) => {
                        event.stopPropagation();
                        void handleDeleteDraft(draft);
                      }}
                    >
                      删除
                    </Button>
                  </div>
                </div>
              ))
            )
          ) : rows.length === 0 ? (
            <Empty style={{ marginTop: 48 }} image={Empty.PRESENTED_IMAGE_SIMPLE} description="这个文件夹没有邮件" />
          ) : (
            rows.map((row) => {
              const active = row.id === selectedId;
              return (
                <div
                  key={row.id}
                  onClick={() => selectMessage(row.id)}
                  style={{
                    padding: '10px 12px',
                    borderBottom: '1px solid #f5f5f5',
                    cursor: 'pointer',
                    background: active ? '#e6f4ff' : 'transparent',
                    borderLeft: row.seen === 1 ? '3px solid transparent' : '3px solid #1677ff',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', gap: 8 }}>
                    <Typography.Text
                      strong={row.seen !== 1}
                      ellipsis
                      style={{ flex: 1, fontSize: 13 }}
                    >
                      {row.fromName || row.fromAddr || '(未知发件人)'}
                    </Typography.Text>
                    <Typography.Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                      {formatListTime(row.sentTime)}
                    </Typography.Text>
                  </div>
                  <div style={{ marginTop: 2 }}>
                    <Typography.Text ellipsis style={{ fontSize: 13, fontWeight: row.seen !== 1 ? 600 : 400 }}>
                      {row.subject || '(无主题)'}
                    </Typography.Text>
                  </div>
                  <div
                    style={{
                      marginTop: 2,
                      fontSize: 12,
                      color: '#8c8c8c',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                  >
                    {row.flagged === 1 ? <span style={{ color: '#faad14', marginRight: 4 }}>★</span> : null}
                    {row.hasAttachment === 1 ? <span style={{ marginRight: 4 }}>📎</span> : null}
                    {row.preview || ''}
                  </div>
                </div>
              );
            })
          )}
        </div>

        <div style={{ padding: '6px 8px', borderTop: '1px solid #f0f0f0', flex: '0 0 auto' }}>
          <Pagination
            size="small"
            simple
            current={current}
            pageSize={PAGE_SIZE}
            total={localDraftView ? draftTotal : total}
            onChange={setCurrent}
            style={{ textAlign: 'center' }}
          />
        </div>
      </div>

      {/* 第三栏：阅读区 */}
      <div style={{ ...PANE_STYLE, flex: 1, minWidth: 0 }}>
        {localDraftView ? (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%' }}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="点击左侧草稿继续编辑"
            />
          </div>
        ) : (
          <MailReader
            message={detail}
            loading={detailLoading}
            onToggleFlag={handleToggleFlag}
            onToggleSeen={handleToggleSeen}
            onDelete={handleDelete}
            onReply={(target) => openCompose({ reply: target })}
          />
        )}
      </div>

      <ComposeModal
        open={composeOpen}
        accounts={accounts}
        defaultAccountId={accountId}
        draft={editingDraft}
        replyTo={replyTo}
        onClose={() => setComposeOpen(false)}
        onChanged={() => {
          void loadList();
          void loadFolders();
          void loadUnread();
        }}
      />
    </div>
  );
}
