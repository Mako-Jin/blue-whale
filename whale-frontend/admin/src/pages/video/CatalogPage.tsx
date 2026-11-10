// 视频运营 - 分类目录（M4）：系列 / 片单 / 创作者三个面板
// 权限码：系列走 sys:video:series:manage；片单与电影走 content:* / movie:manage；创作者走 creator:manage

import { Alert, Tabs } from 'antd';
import CollectionPanel from './catalog/CollectionPanel';
import CreatorPanel from './catalog/CreatorPanel';
import SeriesPanel from './catalog/SeriesPanel';

export default function CatalogPage() {
  return (
    <>
      <Alert
        type="info"
        showIcon
        message="分类目录运营"
        description="系列按集数组织视频；片单用于电影推荐，单片单最多 200 部；创作者由用户侧登记，运营仅维护资料与启停。"
        style={{ marginBottom: 16 }}
      />
      <Tabs
        defaultActiveKey="series"
        items={[
          { key: 'series', label: '系列管理', children: <SeriesPanel /> },
          { key: 'collections', label: '片单管理', children: <CollectionPanel /> },
          { key: 'creators', label: '创作者管理', children: <CreatorPanel /> },
        ]}
      />
    </>
  );
}
