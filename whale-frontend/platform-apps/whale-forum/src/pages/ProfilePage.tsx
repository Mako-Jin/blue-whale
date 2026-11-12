// 我的：个人主页（F-07-01）+ 鲸阶（F-07-03）+ 禁言态（F-07-07）+ 我的内容（F-07-02）
//
// 三类数据各自分页、各自加载：切 Tab 才发请求，失败只影响当前 Tab，
// 不让「我的回复」接口抖动把整页打空。禁言文案只展示类型与到期时间，
// 处置原因属治理信息，后端压根不返回。

import { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  List,
  Pagination,
  Space,
  Tabs,
  Tag,
  Typography,
} from 'antd';
import {
  fetchMyFollows,
  fetchMyPosts,
  fetchMyThreads,
  fetchProfile,
  removeFollow,
  type BoardItem,
  type FollowItem,
  type MyPost,
  type Profile,
  type ThreadListItem,
} from '../api/forum';
import CollectionsPage from './CollectionsPage';

const { Text, Paragraph } = Typography;

/** 帖子状态中文名（列表页与「我的帖子」共用口径） */
const THREAD_STATUS_LABEL: Record<string, string> = {
  PUBLISHED: '已发布',
  SOLVED: '已解决',
  CLOSED: '已关闭',
  PENDING: '审核中',
  REMOVED: '已处置',
  OFFLINE: '已下架',
  DRAFT: '草稿',
};

interface Props {
  boards: BoardItem[];
  onOpenThread: (threadId: string) => void;
}

export default function ProfilePage({ boards, onOpenThread }: Props) {
  const [profile, setProfile] = useState<Profile | null>(null);
  const [profileError, setProfileError] = useState<string | null>(null);

  const [threads, setThreads] = useState<ThreadListItem[]>([]);
  const [threadTotal, setThreadTotal] = useState(0);
  const [threadPage, setThreadPage] = useState(1);
  const [threadLoading, setThreadLoading] = useState(false);

  const [posts, setPosts] = useState<MyPost[]>([]);
  const [postTotal, setPostTotal] = useState(0);
  const [postPage, setPostPage] = useState(1);
  const [postLoading, setPostLoading] = useState(false);

  const [follows, setFollows] = useState<FollowItem[]>([]);
  const [followTotal, setFollowTotal] = useState(0);
  const [followPage, setFollowPage] = useState(1);
  const [followLoading, setFollowLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchProfile()
      .then((data) => {
        if (!cancelled) {
          setProfile(data);
          setProfileError(null);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setProfileError(err instanceof Error ? err.message : '加载个人资料失败');
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const loadThreads = useCallback((page: number) => {
    setThreadLoading(true);
    fetchMyThreads(page, 20)
      .then((result) => {
        setThreads(result.records);
        setThreadTotal(Number(result.total));
      })
      .catch(() => {
        setThreads([]);
        setThreadTotal(0);
      })
      .finally(() => setThreadLoading(false));
  }, []);

  const loadPosts = useCallback((page: number) => {
    setPostLoading(true);
    fetchMyPosts(page, 20)
      .then((result) => {
        setPosts(result.records);
        setPostTotal(Number(result.total));
      })
      .catch(() => {
        setPosts([]);
        setPostTotal(0);
      })
      .finally(() => setPostLoading(false));
  }, []);

  const loadFollows = useCallback((page: number) => {
    setFollowLoading(true);
    fetchMyFollows(undefined, page, 20)
      .then((result) => {
        setFollows(result.records);
        setFollowTotal(Number(result.total));
      })
      .catch(() => {
        setFollows([]);
        setFollowTotal(0);
      })
      .finally(() => setFollowLoading(false));
  }, []);

  /** 取关：成功后重取当前页，避免本地删行后页码与总数错位 */
  const onUnfollow = async (item: FollowItem) => {
    try {
      await removeFollow(item.targetType === 'BOARD' ? 'BOARD' : 'USER', item.targetId);
      loadFollows(followPage);
    } catch {
      // 失败保持原样：状态以服务端为准，不本地乐观删除
    }
  };

  /** 关注对象的展示名：版块查本地版块树，作者没有跨服务通讯录，用占位 */
  const followLabel = (item: FollowItem) => {
    if (item.targetType === 'BOARD') {
      return boards.find((board) => board.id === item.targetId)?.name ?? `版块 #${item.targetId}`;
    }
    return `鲸友 #${item.targetId}`;
  };

  // 默认 Tab 是「我的帖子」：Tabs 的 onChange 首次不触发，这里主动加载
  useEffect(() => {
    loadThreads(1);
  }, [loadThreads]);

  const threadItems = (
    <>
      <List
        loading={threadLoading}
        dataSource={threads}
        locale={{ emptyText: <Empty description="还没有发过帖子" /> }}
        renderItem={(item) => (
          <List.Item>
            <Space size={8} wrap>
              {item.status !== 'PUBLISHED' ? (
                <Tag color={item.status === 'PENDING' ? 'orange' : 'default'}>
                  {THREAD_STATUS_LABEL[item.status] ?? item.status}
                </Tag>
              ) : null}
              {item.essence === 1 ? <Tag color="gold">金榜</Tag> : null}
              <Button type="link" style={{ padding: 0 }} onClick={() => onOpenThread(item.id)}>
                {item.title}
              </Button>
              <Text type="secondary">
                回复 {item.replyCount} · 浏览 {item.viewCount} · 投花 {item.likeCount}
              </Text>
              <Text type="secondary">{item.publishTime ?? item.lastReplyTime ?? ''}</Text>
            </Space>
          </List.Item>
        )}
      />
      {threadTotal > 20 ? (
        <Pagination
          style={{ marginTop: 12, textAlign: 'right' }}
          current={threadPage}
          pageSize={20}
          total={threadTotal}
          showSizeChanger={false}
          onChange={(page) => {
            setThreadPage(page);
            loadThreads(page);
          }}
        />
      ) : null}
    </>
  );

  const postItems = (
    <>
      <List
        loading={postLoading}
        dataSource={posts}
        locale={{ emptyText: <Empty description="还没有回复过帖子" /> }}
        renderItem={(item) => (
          <List.Item>
            <div>
              <Space size={8} wrap>
                {item.status !== 'PUBLISHED' ? (
                  <Tag>{THREAD_STATUS_LABEL[item.status] ?? item.status}</Tag>
                ) : null}
                <Button
                  type="link"
                  style={{ padding: 0 }}
                  onClick={() => onOpenThread(item.threadId)}
                >
                  {item.threadTitle}
                </Button>
                {item.floor ? <Text type="secondary">{item.floor} 楼</Text> : null}
                <Text type="secondary">投花 {item.likeCount}</Text>
                <Text type="secondary">{item.createTime ?? ''}</Text>
              </Space>
              <Paragraph type="secondary" style={{ margin: '4px 0 0' }} ellipsis>
                {item.summary ?? ''}
              </Paragraph>
            </div>
          </List.Item>
        )}
      />
      {postTotal > 20 ? (
        <Pagination
          style={{ marginTop: 12, textAlign: 'right' }}
          current={postPage}
          pageSize={20}
          total={postTotal}
          showSizeChanger={false}
          onChange={(page) => {
            setPostPage(page);
            loadPosts(page);
          }}
        />
      ) : null}
    </>
  );

  const followItems = (
    <>
      <List
        loading={followLoading}
        dataSource={follows}
        locale={{ emptyText: <Empty description="还没有结契关注" /> }}
        renderItem={(item) => (
          <List.Item
            actions={[
              <Button key="unfollow" size="small" onClick={() => onUnfollow(item)}>
                取关
              </Button>,
            ]}
          >
            <Space size={8} wrap>
              <Tag color={item.targetType === 'BOARD' ? 'blue' : 'default'}>
                {item.targetType === 'BOARD' ? '版块' : '鲸友'}
              </Tag>
              <Text>{followLabel(item)}</Text>
              <Text type="secondary">{item.createTime ?? ''}</Text>
            </Space>
          </List.Item>
        )}
      />
      {followTotal > 20 ? (
        <Pagination
          style={{ marginTop: 12, textAlign: 'right' }}
          current={followPage}
          pageSize={20}
          total={followTotal}
          showSizeChanger={false}
          onChange={(page) => {
            setFollowPage(page);
            loadFollows(page);
          }}
        />
      ) : null}
    </>
  );

  return (
    <div className="forum-page">
      {profileError ? (
        <Alert type="error" showIcon message="个人资料加载失败" description={profileError} />
      ) : null}

      {profile ? (
        <Card bordered={false}>
          <Space direction="vertical" size={12} style={{ width: '100%' }}>
            <Space size={12} wrap>
              <Text strong style={{ fontSize: 16 }}>
                鲸友 #{profile.userId}
              </Text>
              <Tag color="blue">
                Lv{profile.level} · {profile.levelName}
              </Tag>
              {profile.banned ? (
                <Tag color="red">{profile.banType === 'BAN' ? '封号中' : '禁言中'}</Tag>
              ) : null}
            </Space>
            {profile.banned ? (
              <Alert
                type="warning"
                showIcon
                message={
                  profile.banType === 'BAN'
                    ? '当前账号已被封号，暂时无法发帖回帖'
                    : '当前账号处于禁言状态，暂时无法发帖回帖'
                }
                description={
                  profile.banUntil ? `到期时间：${profile.banUntil}` : '永久有效，请联系管理员申诉'
                }
              />
            ) : null}
            <Descriptions size="small" column={{ xs: 2, sm: 3, md: 5 }}>
              <Descriptions.Item label="经验">{Number(profile.expTotal)}</Descriptions.Item>
              <Descriptions.Item label="下一级">
                {profile.nextLevelExp == null ? '已满级' : Number(profile.nextLevelExp)}
              </Descriptions.Item>
              <Descriptions.Item label="发帖">{Number(profile.threadCount)}</Descriptions.Item>
              <Descriptions.Item label="获花">{Number(profile.likeCount)}</Descriptions.Item>
              <Descriptions.Item label="精华">{Number(profile.essenceCount)}</Descriptions.Item>
            </Descriptions>
          </Space>
        </Card>
      ) : null}

      <Card bordered={false}>
        <Tabs
          items={[
            {
              key: 'threads',
              label: '我的帖子',
              children: threadItems,
            },
            {
              key: 'posts',
              label: '我的回复',
              children: postItems,
            },
            {
              key: 'follows',
              label: '我的关注',
              children: followItems,
            },
            {
              key: 'collections',
              label: '我的收藏',
              children: <CollectionsPage onOpenThread={onOpenThread} />,
            },
          ]}
          onChange={(key) => {
            if (key === 'threads' && threads.length === 0 && !threadLoading) {
              loadThreads(threadPage);
            }
            if (key === 'posts' && posts.length === 0 && !postLoading) {
              loadPosts(postPage);
            }
            if (key === 'follows' && follows.length === 0 && !followLoading) {
              loadFollows(followPage);
            }
          }}
        />
      </Card>
    </div>
  );
}
