// OA 请假管理页：复用列表与新增/编辑弹窗，组合成独立路由页面
import { useState } from 'react';
import List from '../List';
import FormModal from '../Form';
import type { OaLeave } from '../../api/oa';

export default function LeaveCenter() {
  const [formOpen, setFormOpen] = useState(false);
  const [editing, setEditing] = useState<OaLeave | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const openCreate = () => {
    setEditing(null);
    setFormOpen(true);
  };

  const handleEdit = (leave: OaLeave) => {
    setEditing(leave);
    setFormOpen(true);
  };

  const handleSaved = () => {
    setFormOpen(false);
    setEditing(null);
    setRefreshKey((key) => key + 1);
  };

  return (
    <>
      <List refreshKey={refreshKey} onCreate={openCreate} onEdit={handleEdit} />
      <FormModal
        open={formOpen}
        initial={editing}
        onCancel={() => setFormOpen(false)}
        onSaved={handleSaved}
      />
    </>
  );
}
