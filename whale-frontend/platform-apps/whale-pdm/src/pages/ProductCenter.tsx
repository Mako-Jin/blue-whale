// 产品结构页：保留既有 PDM 产品列表与表单，作为 /pdm/product 入口

import { useState } from 'react';
import List from './List';
import FormModal from './Form';
import type { PdmProduct } from '../api/pdm';

export default function ProductCenter() {
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<PdmProduct | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  return (
    <>
      <List
        refreshKey={refreshKey}
        onCreate={() => {
          setEditing(null);
          setFormOpen(true);
        }}
        onEdit={(item) => {
          setEditing(item);
          setFormOpen(true);
        }}
      />
      <FormModal
        open={formOpen}
        initial={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={() => {
          setFormOpen(false);
          setEditing(null);
          setRefreshKey((key) => key + 1);
        }}
      />
    </>
  );
}
