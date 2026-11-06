// 学习培训考试子应用根组件：课程管理 / 课程详情 / 我的学习
//
// M1 范围：课程 CRUD + 发布归档 + 章节课件 + 报名与进度（REQ-FUNC-087）。
// 培训计划（M2）/ 题库考试（M3）/ 证书学分（M4）路由后续波次增量注册。

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import LmsLayout from './layout/LmsLayout';
import CourseDetailPage from './pages/CourseDetailPage';
import CourseListPage from './pages/CourseListPage';
import MyLearningPage from './pages/MyLearningPage';

export default function App() {
  return (
    <BrowserRouter basename="/lms">
      <Routes>
        <Route element={<LmsLayout />}>
          <Route index element={<Navigate to="/course" replace />} />
          <Route path="course" element={<CourseListPage />} />
          <Route path="course/:id" element={<CourseDetailPage />} />
          <Route path="my" element={<MyLearningPage />} />
          <Route path="*" element={<Navigate to="/course" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
