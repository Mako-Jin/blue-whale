// 短视频滑播：最新 / 热门 / 分类 feed（键集游标分页）+ 票据预取 + 点赞 / 收藏 / 评论

import { App, Button, Empty, Result, Select, Space, Spin, Tag, Typography } from 'antd';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  createPlayTickets,
  favoriteContent,
  fetchCategories,
  fetchShortDetail,
  fetchShortFeed,
  likeContent,
  type CategoryView,
  type FeedSort,
  type PlayTicket,
  type PublicContentView,
} from '../api/video';
import { hasPermission } from '../api/client';
import ContentComments from '../components/ContentComments';
import ContentPlayer from '../player/ContentPlayer';
import { formatCount } from '../utils/videoDisplay';

/** 每批条数 */
const PAGE_SIZE = 10;

/** 预取的票据及其获取时间 */
interface PrefetchedTicket {
  ticket: PlayTicket;
  at: number;
}

export default function ShortsPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const containerRef = useRef<HTMLDivElement>(null);
  const idsRef = useRef<Set<number>>(new Set());
  const cursorRef = useRef<string | undefined>(undefined);
  const hasMoreRef = useRef(true);
  const loadingRef = useRef(false);

  const [sort, setSort] = useState<FeedSort>('LATEST');
  const [categoryId, setCategoryId] = useState<number | undefined>(undefined);
  const [categories, setCategories] = useState<CategoryView[]>([]);
  const [records, setRecords] = useState<PublicContentView[]>([]);
  const [activeIndex, setActiveIndex] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [prefetched, setPrefetched] = useState<Record<number, PrefetchedTicket>>({});
  const [interactions, setInteractions] = useState<
    Record<number, { liked: boolean; favorited: boolean }>
  >({});
  const [commentTarget, setCommentTarget] = useState<number | null>(null);
  const [commentable, setCommentable] = useState<boolean | undefined>(undefined);

  const canBrowse = hasPermission('sys:video:browse');
  const canLike = hasPermission('sys:video:like');
  const canFavorite = hasPermission('sys:video:favorite');
  const canComment = hasPermission('sys:video:comment:create');

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    fetchCategories()
      .then((list) =>
        setCategories(
          list.filter(
            (item) => item.contentTypeScope === 'ALL' || item.contentTypeScope === 'SHORT_VIDEO',
          ),
        ),
      )
      .catch((error) => message.error(error instanceof Error ? error.message : '分类加载失败'));
  }, [canBrowse, message]);

  const loadFeed = useCallback(
    async (reset: boolean) => {
      if (loadingRef.current) {
        return;
      }
      if (!reset && !hasMoreRef.current) {
        return;
      }
      loadingRef.current = true;
      setLoading(true);
      try {
        const view = await fetchShortFeed({
          sort,
          categoryId,
          cursor: reset ? undefined : cursorRef.current,
          size: PAGE_SIZE,
        });
        if (reset) {
          idsRef.current = new Set<number>();
        }
        const fresh = view.records.filter((record) => !idsRef.current.has(record.id));
        fresh.forEach((record) => idsRef.current.add(record.id));
        setRecords((previous) => (reset ? fresh : [...previous, ...fresh]));
        cursorRef.current = view.nextCursor;
        hasMoreRef.current = view.hasMore;
        setHasMore(view.hasMore);
        if (reset) {
          setActiveIndex(0);
        }
        if (fresh.length > 0) {
          const batch = await createPlayTickets(
            fresh.slice(0, 20).map((record) => record.id),
          ).catch(() => ({
            tickets: [] as PlayTicket[],
            rejectedContentIds: [] as number[],
          }));
          const now = Date.now();
          setPrefetched((previous) => {
            const next = { ...previous };
            batch.tickets.forEach((ticket) => {
              next[ticket.contentId] = { ticket, at: now };
            });
            return next;
          });
        }
      } catch (error) {
        message.error(error instanceof Error ? error.message : '短视频加载失败');
      } finally {
        loadingRef.current = false;
        setLoading(false);
      }
    },
    [sort, categoryId, message],
  );

  useEffect(() => {
    if (!canBrowse) {
      return;
    }
    cursorRef.current = undefined;
    hasMoreRef.current = true;
    void loadFeed(true);
  }, [canBrowse, loadFeed]);

  // 滑动到倒数第二条时预加载下一批
  useEffect(() => {
    if (records.length > 0 && activeIndex >= records.length - 2) {
      void loadFeed(false);
    }
  }, [activeIndex, records.length, loadFeed]);

  // 观察当前可见的滑播项
  useEffect(() => {
    const container = containerRef.current;
    if (!container || records.length === 0) {
      return;
    }
    const observer = new IntersectionObserver(
      (entries) => {
        entries.forEach((entry) => {
          if (!entry.isIntersecting) {
            return;
          }
          const index = Number((entry.target as HTMLElement).dataset.slideIndex);
          if (Number.isFinite(index)) {
            setActiveIndex(index);
          }
        });
      },
      { root: container, threshold: 0.6 },
    );
    container
      .querySelectorAll('[data-slide-index]')
      .forEach((element) => observer.observe(element));
    return () => observer.disconnect();
  }, [records]);

  const toggleLike = async (content: PublicContentView) => {
    if (!canLike) {
      message.warning('暂无点赞权限');
      return;
    }
    const current = interactions[content.id]?.liked ?? false;
    try {
      await likeContent(content.id, !current);
      setInteractions((previous) => ({
        ...previous,
        [content.id]: { liked: !current, favorited: previous[content.id]?.favorited ?? false },
      }));
      setRecords((previous) =>
        previous.map((item) =>
          item.id === content.id
            ? { ...item, likeCount: Math.max((item.likeCount ?? 0) + (current ? -1 : 1), 0) }
            : item,
        ),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '点赞失败');
    }
  };

  const toggleFavorite = async (content: PublicContentView) => {
    if (!canFavorite) {
      message.warning('暂无收藏权限');
      return;
    }
    const current = interactions[content.id]?.favorited ?? false;
    try {
      await favoriteContent(content.id, !current);
      setInteractions((previous) => ({
        ...previous,
        [content.id]: { liked: previous[content.id]?.liked ?? false, favorited: !current },
      }));
      setRecords((previous) =>
        previous.map((item) =>
          item.id === content.id
            ? {
                ...item,
                favoriteCount: Math.max((item.favoriteCount ?? 0) + (current ? -1 : 1), 0),
              }
            : item,
        ),
      );
    } catch (error) {
      message.error(error instanceof Error ? error.message : '收藏失败');
    }
  };

  const openComments = (contentId: number) => {
    setCommentTarget(contentId);
    setCommentable(undefined);
    fetchShortDetail(contentId)
      .then((detail) => setCommentable(detail.commentable))
      .catch(() => setCommentable(false));
  };

  if (!canBrowse) {
    return (
      <Result
        status="403"
        title="暂无浏览权限"
        subTitle="浏览短视频需要 sys:video:browse 权限，请联系租户管理员开通"
        extra={
          <Button type="primary" onClick={() => navigate('/home')}>
            返回首页
          </Button>
        }
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 104px)' }}>
      <Space style={{ marginBottom: 12 }} wrap>
        <Select<FeedSort>
          value={sort}
          style={{ width: 120 }}
          options={[
            { value: 'LATEST', label: '最新' },
            { value: 'HOT', label: '热门' },
          ]}
          onChange={(value) => setSort(value)}
        />
        <Select<number>
          value={categoryId}
          allowClear
          placeholder="全部分类"
          style={{ width: 160 }}
          options={categories.map((category) => ({ value: category.id, label: category.name }))}
          onChange={(value) => setCategoryId(value)}
        />
        <Button onClick={() => void loadFeed(true)}>刷新</Button>
        {loading ? <Spin size="small" /> : null}
      </Space>

      <div
        ref={containerRef}
        style={{
          flex: 1,
          minHeight: 0,
          overflowY: 'auto',
          borderRadius: 12,
          background: '#000',
          scrollSnapType: 'y mandatory',
        }}
      >
        {records.length === 0 && !loading ? (
          <div
            style={{
              height: '100%',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Empty description="还没有短视频，去上传第一条吧" />
          </div>
        ) : null}

        {records.map((record, index) => {
          const prefetchedTicket = prefetched[record.id];
          return (
            <div
              key={record.id}
              data-slide-index={index}
              style={{
                position: 'relative',
                height: '100%',
                scrollSnapAlign: 'start',
                background: '#000',
              }}
            >
              <ContentPlayer
                contentId={record.id}
                active={index === activeIndex}
                prefetchedTicket={prefetchedTicket?.ticket}
                prefetchedAt={prefetchedTicket?.at}
                onError={(error) => message.warning(error)}
              />

              <div style={bottomInfoStyle}>
                <Space size={6} wrap>
                  <Typography.Text style={{ color: '#fff', fontWeight: 600 }}>
                    {record.title}
                  </Typography.Text>
                  {record.tags?.slice(0, 3).map((tag) => (
                    <Tag key={tag} color="blue">
                      {tag}
                    </Tag>
                  ))}
                </Space>
                {record.description ? (
                  <Typography.Paragraph
                    style={{ color: 'rgba(255,255,255,0.85)', margin: '6px 0 0' }}
                    ellipsis={{ rows: 2 }}
                  >
                    {record.description}
                  </Typography.Paragraph>
                ) : null}
                <Typography.Text style={{ color: 'rgba(255,255,255,0.65)', fontSize: 12 }}>
                  {record.creatorName || '创作者'} · {formatCount(record.playCount)} 次播放
                </Typography.Text>
              </div>

              <div style={actionColumnStyle}>
                <ActionButton
                  label="点赞"
                  icon={interactions[record.id]?.liked ? '❤️' : '🤍'}
                  value={formatCount(record.likeCount)}
                  onClick={() => void toggleLike(record)}
                />
                <ActionButton
                  label="收藏"
                  icon={interactions[record.id]?.favorited ? '⭐' : '☆'}
                  value={formatCount(record.favoriteCount)}
                  onClick={() => void toggleFavorite(record)}
                />
                <ActionButton
                  label="评论"
                  icon="💬"
                  value={formatCount(record.commentCount)}
                  onClick={() => {
                    if (!canComment) {
                      message.warning('暂无评论权限');
                      return;
                    }
                    openComments(record.id);
                  }}
                />
                <ActionButton
                  label="详情"
                  icon="ℹ️"
                  value="查看"
                  onClick={() => navigate(`/shorts/${record.id}`)}
                />
              </div>
            </div>
          );
        })}

        {hasMore ? (
          <div style={{ padding: 16, textAlign: 'center', background: '#000' }}>
            {loading ? <Spin /> : <Button onClick={() => void loadFeed(false)}>加载更多</Button>}
          </div>
        ) : null}
      </div>

      <ContentComments
        contentId={commentTarget}
        open={commentTarget !== null}
        commentable={commentable}
        onClose={() => setCommentTarget(null)}
      />
    </div>
  );
}

function ActionButton({
  label,
  icon,
  value,
  onClick,
}: {
  label: string;
  icon: string;
  value: string;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      aria-label={label}
      title={label}
      onClick={onClick}
      style={actionButtonStyle}
    >
      <span style={{ fontSize: 20, lineHeight: '24px' }}>{icon}</span>
      <span style={{ fontSize: 11 }}>{value}</span>
    </button>
  );
}

const bottomInfoStyle = {
  position: 'absolute',
  left: 16,
  right: 88,
  bottom: 56,
  color: '#fff',
} as const;

const actionColumnStyle = {
  position: 'absolute',
  right: 12,
  bottom: 72,
  display: 'flex',
  flexDirection: 'column',
  gap: 14,
} as const;

const actionButtonStyle = {
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  gap: 2,
  padding: '6px 10px',
  borderRadius: 10,
  border: '1px solid rgba(255,255,255,0.35)',
  background: 'rgba(0,0,0,0.35)',
  color: '#fff',
  cursor: 'pointer',
} as const;
