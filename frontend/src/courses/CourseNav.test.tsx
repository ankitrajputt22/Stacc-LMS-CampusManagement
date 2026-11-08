import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { CourseNav } from './CourseNav'

describe('CourseNav', () => {
  it('lists the course sections and marks the current one, Home only on the course home itself', () => {
    render(
      <MemoryRouter initialEntries={['/courses/42/pages/31']}>
        <CourseNav
          title="CS301"
          items={[
            { to: '/courses/42', label: 'Home', end: true },
            { to: '/courses/42/pages', label: 'Pages' },
            { to: '/courses/42/files', label: 'Files' },
          ]}
        />
      </MemoryRouter>,
    )

    // The phone menu and the wide-screen column hold the same links.
    const pages = screen.getAllByRole('link', { name: 'Pages' })
    expect(pages).toHaveLength(2)
    expect(pages[0]).toHaveAttribute('aria-current', 'page')
    expect(screen.getAllByRole('link', { name: 'Home' })[0]).not.toHaveAttribute('aria-current')
    expect(screen.getAllByRole('link', { name: 'Files' })[0]).toHaveAttribute('href', '/courses/42/files')
  })
})
