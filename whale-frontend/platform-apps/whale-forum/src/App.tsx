// 论坛子应用根组件（M0）：左侧版块导航占位 + 右侧服务状态面板
//
// 基座以 activeRule=/forum 挂载本子应用。M0 只验证「子应用能挂载 + 能打通网关 +
// 枚举由服务端下发」这条链路；M1 起把版块树、帖子列表、详情页接到 pages/ 下。

import { useCallback, useEffect, useState } from 'react';
import { Alert, Card, Descriptions, Empty, Spin, Tag, Typography } from 'antd';
import { fetchForumMeta, type ForumMeta } from './api/forum';
import { hasPermission } from './api/client';

const { Paragraph, Text } = Typography;

type PageKey = 'boards' | 'collections' | 'manage';

/** 版块导航占位（M1 由 GET /forum/portal/boards 下发，仅返回有权限版块） */
const PAGES: Array<{ key: PageKey; label: string; permission?: string }> = [
  { key: 'boards', label: '版块', permission: 'sys:forum:browse' },
  { key: 'collections', label: '我的收藏', permission: 'sys:forum:browse' },
  { key: 'manage', label: '论坛管理', permission: 'sys:forum:manage:list' },
];

/** 从路径推断初始页（/forum/manage → manage），未知值回落版块页 */
function initialPage(): PageKey {
  const segment = window.location.pathname.split('/')[2] ?? '';
  const matched = PAGES.find((page) => page.key === segment);
  return matched?.key ?? 'boards';
}

export default function App() {
  const [active, setActive] = useState<PageKey>(initialPage);
  const [meta, setMeta] = useState<ForumMeta | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    fetchForumMeta()
      .then((data) => {
        if (!cancelled) {
          setMeta(data);
          setError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '无法读取论坛配置');
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

  const switchPage = useCallback((key: PageKey) => {
    setActive(key);
    window.history.replaceState(null, '', `/forum/${key}`);
  }, []);

  const visiblePages = PAGES.filter((page) => !page.permission || hasPermission(page.permission));
  const activePage = visiblePages.find((page) => page.key === active) ?? visiblePages[0];

  return (
    <div className="forum-layout">
      <aside className="forum-sidebar">
        <div className="forum-brand">鲸议台</div>
        <ul className="forum-nav">
          {visiblePages.map((page) => (
            <li key={page.key}>
              <button
                type="button"
                className={page.key === activePage?.key ? 'is-active' : undefined}
                onClick={() => switchPage(page.key)}
              >
                {page.label}
              </button>
            </li>
          ))}
        </ul>
        <div className="forum-slogan">
          <Text type="secondary">人发帖 · 内容沉淀 · 知识复用</Text>
        </div>
      </aside>

      <main className="forum-content">
        {loading ? (
          <div className="forum-loading">
            <Spin tip="正在连接鲸议台…" />
          </div>
        ) : error ? (
          <Alert
            type="error"
            showIcon
            message="论坛服务暂不可用"
            description={`${error}（请确认 whale-forum 已启动、网关已配置 /forum/** 路由）`}
          />
        ) : (
          <>
            <Card title={`${activePage?.label ?? '版块'} · M0 底座`} bordered={false}>
              <Paragraph>
                M0 已打通：服务骨架、V1/V2 迁移（8 张业务表）、网关路由
                <Text code>/forum/**</Text>、权限码头与枚举下发。版块树与帖子列表在 M1 接入。
              </Paragraph>
              {meta ? (
                <Descriptions column={2} size="small" bordered>
                  <Descriptions.Item label="服务">
                    {meta.service}（{meta.version}）
                  </Descriptions.Item>
                  <Descriptions.Item label="匿名发帖">
                    {meta.anonymousEnabled ? (
                      <Tag color="green">已启用</Tag>
                    ) : (
                      <Tag>未启用（HMAC 密钥未配置）</Tag>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="帖子类型">
                    {meta.threadTypes.map((type) => (
                      <Tag key={type}>{type}</Tag>
                    ))}
                  </Descriptions.Item>
                  <Descriptions.Item label="帖子状态">
                    {meta.threadStatuses.map((status) => (
                      <Tag key={status}>{status}</Tag>
                    ))}
                  </Descriptions.Item>
                  <Descriptions.Item label="版块可见性">
                    {meta.boardVisibilities.map((item) => (
                      <Tag key={item}>{item}</Tag>
                    ))}
                  </Descriptions.Item>
                  <Descriptions.Item label="审核模式">
                    {meta.auditModes.map((item) => (
                      <Tag key={item}>{item}</Tag>
                    ))}
                  </Descriptions.Item>
                  <Descriptions.Item label="单帖楼层上限">{meta.limits.maxPostsPerThread}</Descriptions.Item>
                  <Descriptions.Item label="发帖/回帖间隔">
                    {meta.limits.postIntervalSeconds}s / {meta.limits.replyIntervalSeconds}s
                  </Descriptions.Item>
                </Descriptions>
              ) : (
                <Empty description="未取得门面配置" />
              )}
            </Card>
          </>
        )}
      </main>
    </div>
  );
}
