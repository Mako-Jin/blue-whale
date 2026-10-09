import { Outlet } from 'react-router-dom';
import './BasicLayout.less';

export default function BasicLayout() {
  return (
    <div className="basic-layout">
      <header className="basic-layout__header">
        <span className="basic-layout__brand">BlueWhale</span>
        <span className="basic-layout__subtitle">平台门户</span>
      </header>
      <main className="basic-layout__main">
        <Outlet />
      </main>
    </div>
  );
}
