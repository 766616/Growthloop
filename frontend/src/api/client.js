import axios from 'axios'

const TOKEN_KEY = 'growthloop.token'
const USER_KEY = 'growthloop.user'

export const storage = {
  get token() { try { return localStorage.getItem(TOKEN_KEY) } catch { return null } },
  get user() { try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch { return null } },
  save(token, user) { localStorage.setItem(TOKEN_KEY, token); localStorage.setItem(USER_KEY, JSON.stringify(user)) },
  clear() { localStorage.removeItem(TOKEN_KEY); localStorage.removeItem(USER_KEY) },
}

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
  timeout: 45000,
})

api.interceptors.request.use((config) => {
  const token = storage.token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let onUnauthorized = () => {}
export const setUnauthorizedHandler = (fn) => { onUnauthorized = fn }

api.interceptors.response.use(
  (res) => res,
  (err) => {
    const status = err.response?.status
    const url = err.config?.url || ''
    if (status === 401 && !url.includes('/auth/login')) onUnauthorized()
    return Promise.reject(err)
  },
)

/** Human-readable message from an API error (backend ApiResponse or network failure). */
export function errorMessage(err, fallback = 'Something went wrong. Try again.') {
  if (!err) return fallback
  if (!err.response) return 'Cannot reach the GrowthLoop server. Check that the backend is running.'
  const body = err.response.data || {}
  if (body.errors && typeof body.errors === 'object' && !Array.isArray(body.errors)) {
    const first = Object.entries(body.errors)[0]
    if (first) return `${body.message || 'Invalid input'}: ${first[0]} ${first[1]}`
  }
  return body.message || fallback
}

export const isAiDown = (err) => err?.response?.status === 503

// Resolve to the `data` field of the backend ApiResponse envelope
const unwrap = (p) => p.then((r) => r.data?.data)
export const http = {
  get: (url, params) => unwrap(api.get(url, { params })),
  post: (url, body, params) => unwrap(api.post(url, body, { params })),
  put: (url, body) => unwrap(api.put(url, body)),
  patch: (url, body) => unwrap(api.patch(url, body)),
  del: (url) => unwrap(api.delete(url)),
  upload: (url, file) => {
    const fd = new FormData()
    fd.append('file', file)
    return unwrap(api.post(url, fd, { headers: { 'Content-Type': 'multipart/form-data' } }))
  },
}
