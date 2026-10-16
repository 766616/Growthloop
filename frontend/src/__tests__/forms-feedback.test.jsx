import { act, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react'
import { useState } from 'react'
import { MemoryRouter } from 'react-router-dom'
import { Admin, Auth, Catalog, Feedback as FeedbackApi, Notifications, Student } from '../api/endpoints'
import { http, storage } from '../api/client'
import { ToastProvider } from '../components/Toast'
import { Modal, Input } from '../components/ui'
import Navbar from '../components/Navbar'
import { AdminFeedback, AdminSkills } from '../pages/admin'
import { FeedbackPage, StudentSkillGap, StudentSkills } from '../pages/student'
import { useAsync } from '../lib/useAsync'
import { AuthProvider, useAuth } from '../context/AuthContext'

function renderApp(ui) {
  return render(<ToastProvider><MemoryRouter>{ui}</MemoryRouter></ToastProvider>)
}

describe('form focus and refreshed data', () => {
  it('polls backend data every 60 seconds and clears the poll on unmount', async () => {
    const load = vi.fn().mockResolvedValueOnce('initial').mockResolvedValue('refreshed')
    const setInterval = vi.spyOn(window, 'setInterval')
    const clearInterval = vi.spyOn(window, 'clearInterval')
    const { result, unmount } = renderHook(() => useAsync(load, []))
    try {
      await waitFor(() => expect(result.current.data).toBe('initial'))
      expect(setInterval).toHaveBeenCalledWith(expect.any(Function), 60_000)
      expect(load).toHaveBeenCalledTimes(1)

      await act(async () => {
        setInterval.mock.calls.find(([, delay]) => delay === 60_000)[0]()
        await Promise.resolve()
      })
      expect(load).toHaveBeenCalledTimes(2)
      expect(result.current.data).toBe('refreshed')

      unmount()
      expect(clearInterval).toHaveBeenCalledWith(setInterval.mock.results.find((_, index) => setInterval.mock.calls[index][1] === 60_000).value)
    } finally {
      setInterval.mockRestore()
      clearInterval.mockRestore()
    }
  })

  it('keeps typing focus in a modal when an inline onClose callback changes', () => {
    function Editor() {
      const [value, setValue] = useState('')
      const [open, setOpen] = useState(true)
      return <Modal open={open} onClose={() => setOpen(false)} title="Edit record">
        <Input aria-label="Record name" value={value} onChange={(e) => setValue(e.target.value)} />
      </Modal>
    }

    render(<Editor />)
    const input = screen.getByRole('textbox', { name: 'Record name' })
    input.focus()
    for (const character of 'Multi character value') {
      fireEvent.change(input, { target: { value: input.value + character } })
      expect(input).toHaveFocus()
    }
    expect(input).toHaveValue('Multi character value')
    expect(input).toHaveFocus()
  })

  it('preserves an open edit form during reconnect refresh and saves the edited record', async () => {
    let skills = [{ id: 7, name: 'Java', category: 'Programming', difficulty: 'BEGINNER', estimatedHours: 4, prerequisiteIds: [], prerequisites: [] }]
    vi.spyOn(Catalog, 'skills').mockImplementation(async () => skills)
    const update = vi.spyOn(Admin, 'updateSkill').mockImplementation(async (id, body) => {
      skills = [{ ...skills[0], ...body, id }]
      return skills[0]
    })

    renderApp(<AdminSkills />)
    await screen.findByText('Java')
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }))
    const name = screen.getByRole('textbox', { name: 'Name' })
    name.focus()
    fireEvent.change(name, { target: { value: 'Java foundations' } })
    expect(name).toHaveFocus()

    window.dispatchEvent(new Event('online'))
    await waitFor(() => expect(Catalog.skills).toHaveBeenCalledTimes(4))
    expect(name).toHaveValue('Java foundations')
    document.dispatchEvent(new Event('visibilitychange'))
    await waitFor(() => expect(Catalog.skills).toHaveBeenCalledTimes(6))
    expect(name).toHaveValue('Java foundations')

    fireEvent.click(screen.getByRole('button', { name: 'Save skill' }))
    await waitFor(() => expect(update).toHaveBeenCalledWith(7, expect.objectContaining({ name: 'Java foundations' })))
    await screen.findByText('Java foundations')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })
})

describe('student skill-gap page', () => {
  it('renders saved analysis even when optional collections are missing', async () => {
    const originalResizeObserver = globalThis.ResizeObserver
    globalThis.ResizeObserver = class {
      observe() {}
      unobserve() {}
      disconnect() {}
    }
    vi.spyOn(Student, 'analysis').mockResolvedValue({
      skillGap: { coveragePercent: 50, recommendedNext: ['Java'], existing: null, partial: undefined, missing: [] },
    })
    vi.spyOn(Student, 'profile').mockResolvedValue({
      careerGoal: { name: 'Backend Developer', skills: [{ skillId: 7, name: 'Java', requiredLevel: 'INTERMEDIATE' }] },
      skills: [],
    })
    vi.spyOn(Catalog, 'graph').mockResolvedValue({ nodes: null, edges: undefined })

    renderApp(<StudentSkillGap />)

    try {
      expect(await screen.findByText('Skill gap')).toBeInTheDocument()
      expect(screen.getByText('0 have · 0 partial · 0 missing')).toBeInTheDocument()
      expect(screen.getByText('No skills to show yet.')).toBeInTheDocument()
    } finally {
      globalThis.ResizeObserver = originalResizeObserver
    }
  })

  it('shows and retries profile loading errors instead of leaving a blank page', async () => {
    const profile = vi.spyOn(Student, 'profile')
      .mockRejectedValueOnce(new Error('profile unavailable'))
      .mockResolvedValue({ careerGoal: { name: 'Backend Developer', skills: [] }, skills: [] })
    vi.spyOn(Student, 'analysis').mockResolvedValue({ skillGap: null })
    vi.spyOn(Catalog, 'graph').mockResolvedValue({ nodes: [], edges: [] })

    renderApp(<StudentSkillGap />)

    expect(await screen.findByText('This section could not load')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(await screen.findByText('Skill gap')).toBeInTheDocument()
    expect(profile).toHaveBeenCalledTimes(2)
  })
})

describe('student skills page', () => {
  it('loads, updates and saves skill levels', async () => {
    vi.spyOn(Catalog, 'skills').mockResolvedValue([
      { id: 7, name: 'Java', category: 'Programming' },
      { id: 8, name: 'SQL', category: 'Database' },
    ])
    vi.spyOn(Student, 'skills').mockResolvedValue([
      { skillId: 7, name: 'Java', category: 'Programming', proficiency: 'BEGINNER' },
    ])
    const save = vi.spyOn(Student, 'saveSkills').mockResolvedValue([])

    renderApp(<StudentSkills />)

    expect(await screen.findByText('My skills')).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: 'Advanced' }))
    fireEvent.click(screen.getByRole('button', { name: 'Save skills' }))
    await waitFor(() => expect(save).toHaveBeenCalledWith([{ skillId: 7, proficiency: 'ADVANCED' }]))
  })
})

describe('notification refresh between accounts', () => {
  it('reloads notifications when the authenticated user changes', async () => {
    storage.clear()
    vi.spyOn(Auth, 'login').mockResolvedValue({
      token: 'teacher-token',
      user: { id: 22, fullName: 'Teacher User', role: 'TEACHER' },
    })
    const list = vi.spyOn(Notifications, 'list')
      .mockResolvedValueOnce({ items: [], unread: 0 })
      .mockResolvedValue({ items: [{ id: 91, title: 'New mentoring request', message: 'A student requested help.', read: false }], unread: 1 })

    function SignInTeacher() {
      const { login } = useAuth()
      return <button onClick={() => login('teacher@example.test', 'password')}>Sign in as teacher</button>
    }

    render(
      <MemoryRouter>
        <AuthProvider>
          <SignInTeacher />
          <Navbar />
        </AuthProvider>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Sign in as teacher' }))
    await waitFor(() => expect(list).toHaveBeenCalledTimes(2))
    fireEvent.click(screen.getByRole('button', { name: 'Notifications' }))
    expect(await screen.findByText('A student requested help.')).toBeInTheDocument()
  })
})

describe('feedback views', () => {
  it('uses the role-scoped backend feedback endpoints', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue([])
    await FeedbackApi.mine()
    await Admin.feedback()
    expect(get).toHaveBeenNthCalledWith(1, '/feedback/mine')
    expect(get).toHaveBeenNthCalledWith(2, '/feedback/admin')
  })

  it('shows student feedback in separate received and given lists', async () => {
    vi.spyOn(FeedbackApi, 'mine').mockResolvedValue({
      averageRatingReceived: 4.5,
      received: [{ id: 1, from: { fullName: 'Mentor One' }, to: { fullName: 'Student One' }, sessionTopic: 'Java', rating: 5, comment: 'Great progress', createdAt: '2026-10-14T10:00:00Z' }],
      given: [{ id: 2, from: { fullName: 'Student One' }, to: { fullName: 'Mentor Two' }, sessionTopic: 'Algorithms', rating: 4, comment: 'Helpful', createdAt: '2026-10-13T10:00:00Z' }],
    })

    renderApp(<FeedbackPage />)
    expect(await screen.findByText('Received')).toBeInTheDocument()
    expect(screen.getByText('From Mentor One on "Java"')).toBeInTheDocument()
    expect(screen.getByText('To Mentor Two on "Algorithms"')).toBeInTheDocument()
  })

  it('loads the distinct platform-wide feedback view for admins', async () => {
    vi.spyOn(Admin, 'feedback').mockResolvedValue([
      { id: 3, from: { fullName: 'Learner' }, to: { fullName: 'Mentor' }, session: { id: 42, topic: 'Databases', date: '2026-10-14', time: '09:30', status: 'COMPLETED' }, rating: 5, helpfulness: 4, comment: 'Useful', topicsCovered: 'Indexes', progressUpdate: 'Can optimize queries', createdAt: '2026-10-15T10:00:00Z' },
      { id: 4, from: { fullName: 'Student Two' }, to: { fullName: 'Mentor Two' }, session: { id: 43, topic: 'Algorithms', status: 'COMPLETED' }, rating: 3, helpfulness: 3, comment: 'Another session', createdAt: '2026-10-14T10:00:00Z' },
    ])

    renderApp(<AdminFeedback />)
    expect(await screen.findByText('Platform feedback')).toBeInTheDocument()
    expect(screen.getByLabelText('Search student or mentor')).toBeInTheDocument()
    expect(screen.getByLabelText('Search session')).toBeInTheDocument()
    expect(screen.getByLabelText('Rating')).toBeInTheDocument()
    expect(screen.getByText('Learner')).toBeInTheDocument()
    expect(screen.getByText('Mentor')).toBeInTheDocument()
    expect(screen.getByText('Useful')).toBeInTheDocument()
    expect(screen.getByText('42')).toBeInTheDocument()
    expect(screen.getByText('Indexes')).toBeInTheDocument()
    expect(screen.getByText('Can optimize queries')).toBeInTheDocument()
    expect(screen.getAllByText(/14 Oct/)).toHaveLength(2)
    fireEvent.change(screen.getByLabelText('Search student or mentor'), { target: { value: 'Learner' } })
    expect(screen.getByText('Useful')).toBeInTheDocument()
    expect(screen.queryByText('Another session')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Search student or mentor'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Search session'), { target: { value: '43' } })
    expect(screen.getByText('Another session')).toBeInTheDocument()
    expect(screen.queryByText('Useful')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('Search session'), { target: { value: '' } })
    fireEvent.change(screen.getByLabelText('Rating'), { target: { value: '5' } })
    expect(screen.getByText('Useful')).toBeInTheDocument()
    expect(screen.queryByText('Another session')).not.toBeInTheDocument()
    expect(Admin.feedback).toHaveBeenCalledTimes(1)
  })
})
