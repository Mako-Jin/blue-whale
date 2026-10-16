import { BrowserRouter } from 'react-router-dom';

export default function App() {
  return (
    <BrowserRouter basename="/oa">
      <div style={{ padding: 24 }}>
        <h1>📋 Whale OA - OA子应用</h1>
        <p>我是 qiankun 子应用，基座激活 /oa 路由时挂载。</p>
      </div>
    </BrowserRouter>
  );
}
