// 曲库：租户内公开作品列表（关键词 / 标签 / 排序筛选）+ 详情入口

import { App, Input, Select, Space, type TableProps } from 'antd';
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ProTable, SearchForm, type ProTableRef } from '@bluewhale/whale-ui';
import {
  listTags,
  pageArtists,
  pagePublicSongs,
  type ArtistView,
  type PublicSongView,
  type SongQuery,
  type TagView,
} from '../api/music';
import { formatDuration, renderTags } from '../utils/musicDisplay';
import { usePlayer } from '../player/PlayerContext';

const SORT_OPTIONS = [
  { value: 'NEW', label: '最新上架' },
  { value: 'HOT', label: '最热' },
];

export default function LibraryPage() {
  const { message } = App.useApp();
  const navigate = useNavigate();
  const tableRef = useRef<ProTableRef>(null);
  const player = usePlayer();

  const [keyword, setKeyword] = useState<string | undefined>(undefined);
  const [tagId, setTagId] = useState<number | undefined>(undefined);
  const [sortBy, setSortBy] = useState<'NEW' | 'HOT'>('NEW');
  const [tags, setTags] = useState<TagView[]>([]);
  const [artists, setArtists] = useState<ArtistView[]>([]);

  useEffect(() => {
    listTags()
      .then(setTags)
      .catch((error) => message.error(error instanceof Error ? error.message : '标签加载失败'));
    pageArtists({ current: 1, size: 200 })
      .then((result) => setArtists(result.records))
      .catch((error) => message.error(error instanceof Error ? error.message : '歌手加载失败'));
  }, [message]);

  const artistName = (artistId?: number) =>
    artists.find((item) => item.id === artistId)?.name ?? '-';

  const columns: TableProps<PublicSongView>['columns'] = [
    {
      title: '歌曲',
      dataIndex: 'title',
      render: (value: string, row) => (
        <div className="music-song-title">
          <span>{value}</span>
          {row.subtitle && <span className="music-song-subtitle">{row.subtitle}</span>}
        </div>
      ),
    },
    {
      title: '歌手',
      dataIndex: 'artistId',
      width: 160,
      render: (value: number | undefined) => artistName(value),
    },
    {
      title: '时长',
      dataIndex: 'durationMs',
      width: 100,
      render: (value: number | undefined) => formatDuration(value),
    },
    {
      title: '播放',
      dataIndex: 'playCount',
      width: 90,
      render: (value: number | undefined) => value ?? 0,
    },
    {
      title: '标签',
      dataIndex: 'tags',
      render: (value: TagView[] | undefined) => renderTags(value),
    },
    {
      title: '上架时间',
      dataIndex: 'publishedAt',
      width: 180,
      render: (value: string | undefined) => value ?? '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, row) => (
        <Space size={8}>
          <a onClick={() => player.play(row)}>播放</a>
          <a onClick={() => navigate(`/song/${row.id}`)}>详情</a>
        </Space>
      ),
    },
  ];

  return (
    <ProTable<PublicSongView, SongQuery>
      ref={tableRef}
      rowKey="id"
      title="公开曲库"
      columns={columns}
      request={pagePublicSongs}
      params={{ keyword, tagId, sortBy }}
      onError={(error) => message.error(error instanceof Error ? error.message : '曲库加载失败')}
      search={
        <SearchForm
          items={[
            {
              name: 'keyword',
              label: '关键词',
              element: <Input allowClear placeholder="歌曲标题 / 副标题" />,
            },
            {
              name: 'tagId',
              label: '标签',
              element: (
                <Select
                  allowClear
                  placeholder="全部"
                  style={{ width: 160 }}
                  options={tags.map((tag) => ({ value: tag.id, label: tag.name }))}
                />
              ),
            },
            {
              name: 'sortBy',
              label: '排序',
              element: <Select style={{ width: 140 }} options={SORT_OPTIONS} defaultValue="NEW" />,
            },
          ]}
          onSearch={(values) => {
            setKeyword((values.keyword as string) || undefined);
            setTagId((values.tagId as number) || undefined);
            setSortBy((values.sortBy as 'NEW' | 'HOT') || 'NEW');
          }}
          onReset={() => {
            setKeyword(undefined);
            setTagId(undefined);
            setSortBy('NEW');
          }}
        />
      }
    />
  );
}
