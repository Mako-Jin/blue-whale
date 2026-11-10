// 视频子应用根组件：首页 / 短视频（含详情）/ 视频（含详情与系列）/ 电影（含详情与片单）/ 上传 / 我的

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import VideoLayout from './layout/VideoLayout';
import CollectionDetailPage from './pages/CollectionDetailPage';
import HomePage from './pages/HomePage';
import MinePage from './pages/MinePage';
import MovieDetailPage from './pages/MovieDetailPage';
import MoviesPage from './pages/MoviesPage';
import SeriesDetailPage from './pages/SeriesDetailPage';
import ShortDetailPage from './pages/ShortDetailPage';
import ShortsPage from './pages/ShortsPage';
import UploadPage from './pages/UploadPage';
import VideoDetailPage from './pages/VideoDetailPage';
import VideosPage from './pages/VideosPage';

export default function App() {
  return (
    <BrowserRouter basename="/video">
      <Routes>
        <Route element={<VideoLayout />}>
          <Route index element={<Navigate to="/home" replace />} />
          <Route path="home" element={<HomePage />} />
          <Route path="shorts" element={<ShortsPage />} />
          <Route path="shorts/:id" element={<ShortDetailPage />} />
          <Route path="videos" element={<VideosPage />} />
          <Route path="videos/:id" element={<VideoDetailPage />} />
          <Route path="series/:id" element={<SeriesDetailPage />} />
          <Route path="movies" element={<MoviesPage />} />
          <Route path="movies/:id" element={<MovieDetailPage />} />
          <Route path="collections/:id" element={<CollectionDetailPage />} />
          <Route path="upload" element={<UploadPage />} />
          <Route path="mine" element={<MinePage />} />
          <Route path="*" element={<Navigate to="/home" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
