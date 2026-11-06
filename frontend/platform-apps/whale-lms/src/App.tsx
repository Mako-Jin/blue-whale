// 学习培训考试子应用根组件：课程管理 / 课程详情 / 我的学习 / 培训计划 / 题库 / 试卷
//
// M1 范围：课程 CRUD + 发布归档 + 章节课件 + 报名与进度（REQ-FUNC-087）。
// M2 范围：培训班 / 场次 / 必修分配 / 面授签到 / 讲师课时（REQ-FUNC-088）。
// M3-1 范围：题库（题目/选项）+ 试卷（手工加题 + 规则抽题）（REQ-FUNC-089）。
// 考试与判分（M3-2）/ 证书学分（M4）路由后续波次增量注册。

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import LmsLayout from './layout/LmsLayout';
import CourseDetailPage from './pages/CourseDetailPage';
import CourseListPage from './pages/CourseListPage';
import MyLearningPage from './pages/MyLearningPage';
import PaperDetailPage from './pages/PaperDetailPage';
import PaperListPage from './pages/PaperListPage';
import QuestionListPage from './pages/QuestionListPage';
import TrainingClassDetailPage from './pages/TrainingClassDetailPage';
import TrainingClassListPage from './pages/TrainingClassListPage';

export default function App() {
  return (
    <BrowserRouter basename="/lms">
      <Routes>
        <Route element={<LmsLayout />}>
          <Route index element={<Navigate to="/course" replace />} />
          <Route path="course" element={<CourseListPage />} />
          <Route path="course/:id" element={<CourseDetailPage />} />
          <Route path="training" element={<TrainingClassListPage />} />
          <Route path="training/:id" element={<TrainingClassDetailPage />} />
          <Route path="question" element={<QuestionListPage />} />
          <Route path="paper" element={<PaperListPage />} />
          <Route path="paper/:id" element={<PaperDetailPage />} />
          <Route path="my" element={<MyLearningPage />} />
          <Route path="*" element={<Navigate to="/course" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
