import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ToastProvider } from './components/Toast'
import ProtectedRoute from './components/ProtectedRoute'
import AppLayout from './layouts/AppLayout'
import PublicLayout from './layouts/PublicLayout'
import { About, Home, Login, NotFound, Register } from './pages/public'
import * as S from './pages/student'
import * as T from './pages/teacher'
import * as A from './pages/admin'

export default function App() {
  return (
    <AuthProvider>
      <ToastProvider>
        <BrowserRouter>
          <Routes>
            <Route element={<PublicLayout />}>
              <Route path="/" element={<Home />} />
              <Route path="/about" element={<About />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
            </Route>

            <Route element={<ProtectedRoute roles={['STUDENT']} />}>
              <Route element={<AppLayout />}>
                <Route path="/student" element={<Navigate to="/student/dashboard" replace />} />
                <Route path="/student/dashboard" element={<S.StudentDashboard />} />
                <Route path="/student/profile" element={<S.StudentProfile />} />
                <Route path="/student/academics" element={<S.StudentAcademics />} />
                <Route path="/student/skills" element={<S.StudentSkills />} />
                <Route path="/student/skill-gap" element={<S.StudentSkillGap />} />
                <Route path="/student/roadmap" element={<S.StudentRoadmap />} />
                <Route path="/student/recommendations" element={<S.StudentRecommendations />} />
                <Route path="/student/mentors" element={<S.StudentMentors />} />
                <Route path="/student/sessions" element={<S.StudentSessions />} />
                <Route path="/student/feedback" element={<S.FeedbackPage />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute roles={['TEACHER']} />}>
              <Route element={<AppLayout />}>
                <Route path="/teacher" element={<Navigate to="/teacher/dashboard" replace />} />
                <Route path="/teacher/dashboard" element={<T.TeacherDashboard />} />
                <Route path="/teacher/students" element={<T.TeacherStudents />} />
                <Route path="/teacher/analytics" element={<T.TeacherAnalytics />} />
                <Route path="/teacher/sessions" element={<T.TeacherSessions />} />
              </Route>
            </Route>

            {/* student detail is shared by teachers and admins */}
            <Route element={<ProtectedRoute roles={['TEACHER', 'ADMIN']} />}>
              <Route element={<AppLayout />}>
                <Route path="/teacher/student/:id" element={<T.TeacherStudentDetail />} />
              </Route>
            </Route>

            <Route element={<ProtectedRoute roles={['ADMIN']} />}>
              <Route element={<AppLayout />}>
                <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
                <Route path="/admin/dashboard" element={<A.AdminDashboard />} />
                <Route path="/admin/users" element={<A.AdminUsers />} />
                <Route path="/admin/feedback" element={<A.AdminFeedback />} />
                <Route path="/admin/students" element={<A.AdminStudents />} />
                <Route path="/admin/teachers" element={<A.AdminTeachers />} />
                <Route path="/admin/skills" element={<A.AdminSkills />} />
                <Route path="/admin/career-goals" element={<A.AdminCareerGoals />} />
                <Route path="/admin/academics" element={<A.AdminAcademics />} />
                <Route path="/admin/resources" element={<A.AdminResources />} />
                <Route path="/admin/analytics" element={<A.AdminAnalytics />} />
              </Route>
            </Route>

            <Route element={<PublicLayout />}><Route path="*" element={<NotFound />} /></Route>
          </Routes>
        </BrowserRouter>
      </ToastProvider>
    </AuthProvider>
  )
}
