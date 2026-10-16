import { BrowserRouter } from 'react-router-dom';

export default function App() {
  return (
    <BrowserRouter basename="/portal">
      <div style={{ padding: 24 }}>
        <h1>🐋 Whale Portal - 门户子应用</h1>
        <p>我是 qiankun 子应用，基座激活 /portal 路由时挂载。</p>
      </div>
    </BrowserRouter>
  );
}
