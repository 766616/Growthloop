import { render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { Badge, Button, EmptyState, ProgressBar, Table } from '../components/ui'
import { errorMessage } from '../api/client'
import { toneOf, pct } from '../lib/format'
import GrowthLoopRing from '../components/GrowthLoopRing'
import SkillGraph from '../components/SkillGraph'

describe('ui components', () => {
  it('Badge renders a readable label for API enums', () => {
    render(<Badge value="IN_PROGRESS" />)
    expect(screen.getByText('In progress')).toBeInTheDocument()
  })

  it('Button fires click and disables while loading', () => {
    const fn = vi.fn()
    const { rerender } = render(<Button onClick={fn}>Save</Button>)
    fireEvent.click(screen.getByText('Save'))
    expect(fn).toHaveBeenCalledTimes(1)
    rerender(<Button loading onClick={fn}>Save</Button>)
    expect(screen.getByRole('button')).toBeDisabled()
  })

  it('ProgressBar clamps values', () => {
    render(<ProgressBar value={140} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '100')
  })

  it('Table shows empty message', () => {
    render(<Table columns={[{ key: 'a', label: 'A' }]} rows={[]} empty="Nothing" />)
    expect(screen.getByText('Nothing')).toBeInTheDocument()
  })

  it('EmptyState shows action', () => {
    render(<MemoryRouter><EmptyState title="No skills" action={<button>Add</button>} /></MemoryRouter>)
    expect(screen.getByText('Add')).toBeInTheDocument()
  })

  it('GrowthLoopRing labels all three signals', () => {
    render(<GrowthLoopRing roadmap={40} coverage={60} academics={70} />)
    expect(screen.getByRole('img')).toHaveAttribute('aria-label', 'Roadmap 40%, skills 60%, academics 70%')
  })

  it('SkillGraph draws nodes and prerequisites', () => {
    render(<SkillGraph nodes={[{ id: 1, name: 'Java' }, { id: 2, name: 'Spring Boot' }]} edges={[{ from: 1, to: 2 }]} status={{ 1: 'have', 2: 'missing' }} />)
    expect(screen.getByText('Java')).toBeInTheDocument()
    expect(screen.getByText('Spring Boot')).toBeInTheDocument()
  })
})

describe('helpers', () => {
  it('maps statuses to tones', () => {
    expect(toneOf('HIGH')).toBe('coral')
    expect(toneOf('COMPLETED')).toBe('sage')
  })
  it('formats percentages', () => {
    expect(pct(72.46, 1)).toBe('72.5%')
    expect(pct(null)).toBe('—')
  })
  it('extracts API error messages', () => {
    expect(errorMessage({ response: { data: { message: 'Email already registered' } } })).toBe('Email already registered')
    expect(errorMessage({})).toMatch(/Cannot reach/)
  })
})
