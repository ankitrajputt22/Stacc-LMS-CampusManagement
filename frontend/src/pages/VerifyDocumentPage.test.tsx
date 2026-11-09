import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import * as api from '../api/careerApi'
import { VerifyDocumentPage } from './VerifyDocumentPage'

vi.mock('../api/careerApi')

describe('VerifyDocumentPage', () => {
  it('checks a code from the address and shows only authenticity, title, and date', async () => {
    vi.mocked(api.verifyDocument).mockResolvedValue({
      authentic: true, message: 'Authentic: issued by the college through Stacc.', title: 'Bonafide certificate', issuedAt: '2026-11-13T09:00:00Z',
    })
    render(
      <MemoryRouter initialEntries={['/verify-document?code=D12-0A1B2C3D4E5F6071']}>
        <VerifyDocumentPage />
      </MemoryRouter>,
    )
    expect(screen.getByRole('textbox', { name: 'Code' })).toHaveValue('D12-0A1B2C3D4E5F6071')
    await userEvent.click(screen.getByRole('button', { name: 'Verify' }))
    expect(await screen.findByText('Authentic: issued by the college through Stacc.')).toBeInTheDocument()
    expect(api.verifyDocument).toHaveBeenCalledWith('D12-0A1B2C3D4E5F6071')
  })
})
