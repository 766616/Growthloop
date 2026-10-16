import { http } from './client'

const sid = (studentId) => (studentId ? { studentId } : undefined)

export const Auth = {
  login: (email, password) => http.post('/auth/login', { email, password }),
  register: (body) => http.post('/auth/register', body),
  me: () => http.get('/auth/me'),
  logout: () => http.post('/auth/logout'),
}

export const Catalog = {
  skills: () => http.get('/skills'),
  graph: () => http.get('/skills/graph'),
  goals: () => http.get('/career-goals'),
  resources: (skillId) => http.get('/resources', skillId ? { skillId } : undefined),
  mentors: () => http.get('/mentors'),
}

export const Student = {
  profile: () => http.get('/students/me'),
  update: (body) => http.put('/students/me', body),
  skills: () => http.get('/students/me/skills'),
  saveSkills: (skills) => http.put('/students/me/skills', { skills }),
  academics: () => http.get('/students/me/academics'),
  dashboard: () => http.get('/students/me/dashboard'),
  analysis: () => http.get('/students/me/analysis'),
  progress: () => http.get('/students/me/progress'),
}

export const AI = {
  health: () => http.get('/ai/health'),
  risk: (studentId) => http.post('/ai/risk-analysis', null, sid(studentId)),
  gap: (studentId) => http.post('/ai/skill-gap', null, sid(studentId)),
  match: (studentId) => http.post('/ai/peer-matching', null, sid(studentId)),
  regenerate: (studentId) => http.post('/ai/recommendations', null, sid(studentId)),
}

export const Roadmap = {
  mine: () => http.get('/roadmaps/me'),
  setStatus: (itemId, status) => http.patch(`/roadmaps/items/${itemId}`, { status }),
}

export const Recs = {
  mine: () => http.get('/recommendations/me'),
  matches: () => http.get('/matches/me'),
}

export const Sessions = {
  mine: () => http.get('/sessions'),
  create: (body) => http.post('/sessions', body),
  setStatus: (id, body) => http.patch(`/sessions/${id}/status`, body),
}

export const Feedback = {
  submit: (body) => http.post('/feedback', body),
  mine: () => http.get('/feedback/mine'),
}

export const Notifications = {
  list: () => http.get('/notifications'),
  read: (id) => http.patch(`/notifications/${id}/read`),
  readAll: () => http.patch('/notifications/read-all'),
}

export const Teacher = {
  dashboard: () => http.get('/teacher/dashboard'),
  students: () => http.get('/teacher/students'),
  student: (id) => http.get(`/teacher/students/${id}`),
  analytics: () => http.get('/teacher/analytics'),
}

export const Admin = {
  dashboard: () => http.get('/admin/dashboard'),
  feedback: () => http.get('/feedback/admin'),
  users: (role) => http.get('/admin/users', role ? { role } : undefined),
  createUser: (body) => http.post('/admin/users', body),
  updateUser: (id, body) => http.patch(`/admin/users/${id}`, body),
  deleteUser: (id) => http.del(`/admin/users/${id}`),
  students: () => http.get('/admin/students'),
  teachers: () => http.get('/admin/teachers'),
  assignTeacher: (studentId, teacherProfileId) => http.patch(`/admin/students/${studentId}/assign-teacher`, { teacherProfileId }),
  createSkill: (b) => http.post('/admin/skills', b),
  updateSkill: (id, b) => http.put(`/admin/skills/${id}`, b),
  deleteSkill: (id) => http.del(`/admin/skills/${id}`),
  createGoal: (b) => http.post('/admin/career-goals', b),
  updateGoal: (id, b) => http.put(`/admin/career-goals/${id}`, b),
  deleteGoal: (id) => http.del(`/admin/career-goals/${id}`),
  createResource: (b) => http.post('/admin/resources', b),
  updateResource: (id, b) => http.put(`/admin/resources/${id}`, b),
  deleteResource: (id) => http.del(`/admin/resources/${id}`),
  academics: (studentId) => http.get('/admin/academics', { studentId }),
  addAcademic: (b) => http.post('/admin/academics', b),
  deleteAcademic: (id) => http.del(`/admin/academics/${id}`),
  importCsv: (file) => http.upload('/admin/academics/import', file),
  retrain: () => http.post('/admin/ai/retrain'),
  analyzeAll: () => http.post('/admin/ai/analyze-all'),
}
