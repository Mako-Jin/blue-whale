// 音乐子应用根组件：曲库 / 上传作品 / 我的作品 / 歌曲详情

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import MusicLayout from './layout/MusicLayout';
import LibraryPage from './pages/LibraryPage';
import SongDetailPage from './pages/SongDetailPage';
import UploadPage from './pages/UploadPage';
import MySongsPage from './pages/MySongsPage';

export default function App() {
  return (
    <BrowserRouter basename="/music">
      <Routes>
        <Route element={<MusicLayout />}>
          <Route index element={<Navigate to="/library" replace />} />
          <Route path="library" element={<LibraryPage />} />
          <Route path="song/:id" element={<SongDetailPage />} />
          <Route path="upload" element={<UploadPage />} />
          <Route path="mine" element={<MySongsPage />} />
          <Route path="*" element={<Navigate to="/library" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
