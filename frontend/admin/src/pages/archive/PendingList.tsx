// 待整理页：复用档案检索页并默认筛选 itemStatus=0

import ArchiveItemList from './ItemList';

export default function ArchivePendingList() {
  return <ArchiveItemList defaultItemStatus={0} />;
}
