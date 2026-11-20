// 视频运营 - 分类目录（M4）：系列 / 片单 / 创作者三个面板
// 权限码：系列走 sys:video:series:manage；片单与电影走 content:* / movie:manage；创作者走 creator:manage

import { Alert, Tabs } from 'antd';
import CollectionPanel from './catalog/CollectionPanel';
import CreatorPanel from './catalog/CreatorPanel';
import SeriesPanel from './catalog/SeriesPanel';
import { useI18n } from '../../i18n';

export default function CatalogPage() {
  const { t } = useI18n();
  return (
    <>
      <Alert
        type="info"
        showIcon
        message={t('video.catalog.alert.title')}
        description={t('video.catalog.alert.description')}
        style={{ marginBottom: 16 }}
      />
      <Tabs
        defaultActiveKey="series"
        items={[
          { key: 'series', label: t('video.catalog.tab.series'), children: <SeriesPanel /> },
          {
            key: 'collections',
            label: t('video.catalog.tab.collections'),
            children: <CollectionPanel />,
          },
          { key: 'creators', label: t('video.catalog.tab.creators'), children: <CreatorPanel /> },
        ]}
      />
    </>
  );
}
