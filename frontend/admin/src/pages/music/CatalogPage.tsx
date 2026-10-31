// 音乐运营 - 歌手与专辑：歌手 / 专辑 / 标签三个子页签

import { Tabs } from 'antd';
import ArtistList from './ArtistList';
import AlbumList from './AlbumList';
import TagList from './TagList';

export default function CatalogPage() {
  return (
    <Tabs
      defaultActiveKey="artists"
      items={[
        { key: 'artists', label: '歌手', children: <ArtistList /> },
        { key: 'albums', label: '专辑', children: <AlbumList /> },
        { key: 'tags', label: '标签', children: <TagList /> },
      ]}
    />
  );
}
