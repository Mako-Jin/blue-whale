// 论坛子应用根组件：左侧版块树 + 右侧列表/详情
//
// 基座以 activeRule=/forum 挂载；页内导航用本地状态 + history.replaceState，
// 不引 react-router（子应用只有两级视图，路由库反而增加 qiankun 下的 basename 麻烦）。

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Empty, Spin, Tag, Typography } from 'antd';
import { fetchBoards, fetchMyExp, type BoardItem } from './api/forum';
import ThreadListPage from './pages/ThreadListPage';
import ThreadDetailPage from './pages/ThreadDetailPage';
import AdminPage from './pages/AdminPage';
import HotPage from './pages/HotPage';
import CollectionsPage from './pages/CollectionsPage';
import DiscoveryPage from './pages/DiscoveryPage';
import ProfilePage from './pages/ProfilePage';

const { Text } = Typography;

interface ViewState {
  kind: 'list' | 'thread' | 'admin' | 'hot' | 'collections' | 'discovery' | 'profile';
  boardId?: string;
  threadId?: string;
}

export default function App() {
  const [boards, setBoards] = useState<BoardItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [expTotal, setExpTotal] = useState<number | null>(null);
  const [view, setView] = useState<ViewState>({ kind: 'list' });

  useEffect(() => {
    let cancelled = false;
    fetchBoards()
      .then((data) => {
        if (cancelled) {
          return;
        }
        setBoards(data);
        setError(null);
        const first = data.find((board) => board.parentId == null) ?? data[0];
        if (first) {
          setView({ kind: 'list', boardId: first.id });
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '无法加载版块');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /** 我的论坛经验：拿不到就隐藏，不打扰主流程 */
  useEffect(() => {
    let cancelled = false;
    fetchMyExp()
      .then((data) => {
        if (!cancelled) {
          setExpTotal(data.total);
        }
      })
      .catch(() => {
        // 经验是附属能力，读取失败静默
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /** 大区（根版块）及其子版，扁平数据前端组树 */
  const tree = useMemo(() => {
    const roots = boards.filter((board) => board.parentId == null);
    return roots.map((root) => ({
      root,
      children: boards.filter((board) => board.parentId === root.id),
    }));
  }, [boards]);

  const activeBoard = useMemo(
    () => boards.find((board) => board.id === view.boardId),
    [boards, view.boardId],
  );

  const openBoard = useCallback((boardId: string) => {
    setView({ kind: 'list', boardId });
  }, []);

  const openThread = useCallback((threadId: string) => {
    setView((prev) => ({ kind: 'thread', boardId: prev.boardId, threadId }));
  }, []);

  const backToList = useCallback(() => {
    setView((prev) => ({ kind: 'list', boardId: prev.boardId }));
  }, []);

  const openAdmin = useCallback(() => {
    setView((prev) => ({ kind: 'admin', boardId: prev.boardId }));
  }, []);

  const openHot = useCallback(() => {
    setView((prev) => ({ kind: 'hot', boardId: prev.boardId }));
  }, []);

  const openCollections = useCallback(() => {
    setView((prev) => ({ kind: 'collections', boardId: prev.boardId }));
  }, []);

  const openDiscovery = useCallback(() => {
    setView((prev) => ({ kind: 'discovery', boardId: prev.boardId }));
  }, []);

  const openProfile = useCallback(() => {
    setView((prev) => ({ kind: 'profile', boardId: prev.boardId }));
  }, []);

  if (loading) {
    return (
      <div className="forum-loading">
        <Spin tip="正在连接鲸议台…" />
      </div>
    );
  }

  return (
    <div className="forum-layout">
      <aside className="forum-sidebar">
        <div className="forum-brand">鲸议台</div>
        <ul className="forum-nav">
          {tree.map(({ root, children }) => (
            <li key={root.id}>
              <button
                type="button"
                className={root.id === activeBoard?.id ? 'is-active' : undefined}
                onClick={() => openBoard(root.id)}
              >
                {root.name}
                {root.auditMode === 1 ? <Tag color="orange">先审后发</Tag> : null}
                {root.postPolicy === 'READONLY' ? <Tag>只读</Tag> : null}
              </button>
              {children.length > 0 ? (
                <ul className="forum-subnav">
                  {children.map((child) => (
                    <li key={child.id}>
                      <button
                        type="button"
                        className={child.id === activeBoard?.id ? 'is-active' : undefined}
                        onClick={() => openBoard(child.id)}
                      >
                        {child.name}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </li>
          ))}
        </ul>
        <div className="forum-slogan">
          <button
            type="button"
            className={view.kind === 'hot' ? 'is-active' : undefined}
            onClick={openHot}
          >
            鲸榜
          </button>
          <button
            type="button"
            className={view.kind === 'discovery' ? 'is-active' : undefined}
            onClick={openDiscovery}
          >
            发现
          </button>
          <button
            type="button"
            className={view.kind === 'profile' ? 'is-active' : undefined}
            onClick={openProfile}
          >
            我的
          </button>
          <button
            type="button"
            className={view.kind === 'collections' ? 'is-active' : undefined}
            onClick={openCollections}
          >
            我的收藏
          </button>
          <button
            type="button"
            className={view.kind === 'admin' ? 'is-active' : undefined}
            onClick={openAdmin}
          >
            管理台
          </button>
          <Text type="secondary">论坛经验 {expTotal ?? '—'} · 人发帖 · 内容沉淀 · 知识复用</Text>
        </div>
      </aside>

      <main className="forum-content">
        {error ? (
          <Alert
            type="error"
            showIcon
            message="论坛服务暂不可用"
            description={`${error}（请确认 whale-forum 已启动、网关已配置 /forum/** 路由）`}
          />
        ) : view.kind === 'admin' ? (
          <AdminPage />
        ) : view.kind === 'hot' ? (
          <HotPage onOpenThread={openThread} />
        ) : view.kind === 'discovery' ? (
          <DiscoveryPage boards={boards} onOpenThread={openThread} />
        ) : view.kind === 'collections' ? (
          <CollectionsPage onOpenThread={openThread} />
        ) : view.kind === 'profile' ? (
          <ProfilePage boards={boards} onOpenThread={openThread} />
        ) : view.kind === 'thread' && view.threadId ? (
          <ThreadDetailPage threadId={view.threadId} onBack={backToList} />
        ) : activeBoard ? (
          <ThreadListPage board={activeBoard} onOpenThread={openThread} />
        ) : (
          <Empty description="暂无可见版块，请联系管理员配置版块权限" />
        )}
      </main>
    </div>
  );
}
