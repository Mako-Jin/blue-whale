// 学习培训考试子应用根组件：课程 / 培训 / 题库 / 试卷 / 考试 / 判分 / 证书 / 我的
//
// M1 范围：课程 CRUD + 发布归档 + 章节课件 + 报名与进度（REQ-FUNC-087）。
// M2 范围：培训班 / 场次 / 必修分配 / 面授签到 / 讲师课时（REQ-FUNC-088）。
// M3-1 范围：题库（题目/选项）+ 试卷（手工加题 + 规则抽题）。
// M3-2 范围：考试（时间窗/乱序）+ 作答 + 交卷判分 + 人工判分 + 成绩版本（REQ-FUNC-089）。
// M4-1 范围：证书模板 + 发证 / 撤销 / 换发 + 我的证书（REQ-FUNC-090）。
// M4-2 范围：学分规则 + 记账 + 台账 + 年度汇总（REQ-FUNC-090）。
// 集成与看板（M5–M6）路由后续波次增量注册。

import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import LmsLayout from './layout/LmsLayout';
import CertificateListPage from './pages/CertificateListPage';
import CertTemplatePage from './pages/CertTemplatePage';
import CourseDetailPage from './pages/CourseDetailPage';
import CourseListPage from './pages/CourseListPage';
import CreditPage from './pages/CreditPage';
import ExamAttemptPage from './pages/ExamAttemptPage';
import ExamListPage from './pages/ExamListPage';
import GradePage from './pages/GradePage';
import MyCertificatePage from './pages/MyCertificatePage';
import MyCreditPage from './pages/MyCreditPage';
import MyExamPage from './pages/MyExamPage';
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
          <Route path="exam" element={<ExamListPage />} />
          <Route path="attempt/:attemptId" element={<ExamAttemptPage />} />
          <Route path="grade" element={<GradePage />} />
          <Route path="my-exam" element={<MyExamPage />} />
          <Route path="cert" element={<CertTemplatePage />} />
          <Route path="certificate" element={<CertificateListPage />} />
          <Route path="my-cert" element={<MyCertificatePage />} />
          <Route path="credit" element={<CreditPage />} />
          <Route path="my-credit" element={<MyCreditPage />} />
          <Route path="my" element={<MyLearningPage />} />
          <Route path="*" element={<Navigate to="/course" replace />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
