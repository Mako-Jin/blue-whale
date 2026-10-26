// 合同子应用根组件：合同列表 → 合同详情（相对方 / 版本 / 流程 / 签署）、印章管理、签署中心

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import ContractList from './pages/ContractList';
import ContractDetail from './pages/ContractDetail';
import SealList from './pages/SealList';
import SignCenter from './pages/SignCenter';

export default function App() {
  return (
    <BrowserRouter basename="/contract">
      <Routes>
        <Route path="/" element={<ContractList />} />
        <Route path="/seal" element={<SealList />} />
        <Route path="/sign" element={<SignCenter />} />
        <Route path="/:id" element={<ContractDetail />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
