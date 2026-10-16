import { Outlet } from 'react-router-dom';

export default function MainLayout() {
  return (
    <div style={{ display: 'flex', height: '100vh' }}>
      {/* Sidebar 占位 */}
      <aside style={{ width: 220, background: '#001529', color: '#fff' }}>
        <div style={{ padding: 16, fontSize: 18, fontWeight: 600 }}>🐋 BlueWhale</div>
        {/* TODO: 菜单组件 */}
      </aside>

      <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
        {/* Header 占位 */}
        <header
          style={{
            height: 56,
            background: '#fff',
            borderBottom: '1px solid #f0f0f0',
            padding: '0 16px',
            display: 'flex',
            alignItems: 'center',
          }}
        >
          Header Area
        </header>

        {/* Content */}
        <main style={{ flex: 1, overflow: 'auto', background: '#f5f5f5' }}>
          <Outlet />
        </main>
      </div>
    </div>
  );
}
