// 音乐运营 - 歌手与专辑：歌手 / 专辑 / 标签三个子页签

import { Tabs } from 'antd';
import ArtistList from './ArtistList';
import AlbumList from './AlbumList';
import TagList from './TagList';
import { useI18n } from '../../i18n';

export default function CatalogPage() {
  const { t } = useI18n();

  return (
    <Tabs
      defaultActiveKey="artists"
      items={[
        { key: 'artists', label: t('music.artist.title'), children: <ArtistList /> },
        { key: 'albums', label: t('music.album.title'), children: <AlbumList /> },
        { key: 'tags', label: t('music.tag.title'), children: <TagList /> },
      ]}
    />
  );
}
