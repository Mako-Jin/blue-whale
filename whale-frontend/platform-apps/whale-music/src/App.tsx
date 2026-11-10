// 音乐子应用根组件：发现 / 曲库 / 歌单 / 搜索 / 上传作品 / 我的作品 / 歌曲详情 / 全局播放器

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import MusicLayout from './layout/MusicLayout';
import DiscoverPage from './pages/DiscoverPage';
import LibraryPage from './pages/LibraryPage';
import PlaylistDetailPage from './pages/PlaylistDetailPage';
import PlaylistListPage from './pages/PlaylistListPage';
import SearchPage from './pages/SearchPage';
import SongDetailPage from './pages/SongDetailPage';
import UploadPage from './pages/UploadPage';
import MySongsPage from './pages/MySongsPage';
import MyMusicPage from './pages/MyMusicPage';
import { PlayerProvider } from './player/PlayerContext';

export default function App() {
  return (
    <BrowserRouter basename="/music">
      <PlayerProvider>
        <Routes>
          <Route element={<MusicLayout />}>
            <Route index element={<Navigate to="/discover" replace />} />
            <Route path="discover" element={<DiscoverPage />} />
            <Route path="library" element={<LibraryPage />} />
            <Route path="playlist" element={<PlaylistListPage />} />
            <Route path="playlist/:id" element={<PlaylistDetailPage />} />
            <Route path="search" element={<SearchPage />} />
            <Route path="song/:id" element={<SongDetailPage />} />
            <Route path="upload" element={<UploadPage />} />
            <Route path="mine" element={<MySongsPage />} />
            <Route path="my-music" element={<MyMusicPage />} />
            <Route path="*" element={<Navigate to="/discover" replace />} />
          </Route>
        </Routes>
      </PlayerProvider>
    </BrowserRouter>
  );
}
