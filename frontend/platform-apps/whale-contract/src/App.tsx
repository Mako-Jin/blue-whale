// 合同子应用根组件：合同列表 → 合同详情（相对方 / 版本 / 流程记录）

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ContractList from './pages/ContractList';
import ContractDetail from './pages/ContractDetail';

export default function App() {
  return (
    <BrowserRouter basename="/contract">
      <Routes>
        <Route path="/" element={<ContractList />} />
        <Route path="/:id" element={<ContractDetail />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
