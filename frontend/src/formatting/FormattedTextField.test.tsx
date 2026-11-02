import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { FormattedTextField } from './FormattedTextField'
import type { TextFormat } from './textFormat'

function Field() {
  const [value, setValue] = useState('## Week 1\n\nRead *chapter 2*.')
  const [format, setFormat] = useState<TextFormat>('FORMATTED')
  return (
    <MemoryRouter>
      <FormattedTextField id="body" label="Content" value={value} onChange={setValue} format={format}
        onFormatChange={setFormat} rows={4} maxLength={1000} />
    </MemoryRouter>
  )
}

describe('FormattedTextField', () => {
  it('previews formatted text and drops the preview for plain text', async () => {
    const user = userEvent.setup()
    render(<Field />)

    expect(screen.getByLabelText('Content')).toHaveValue('## Week 1\n\nRead *chapter 2*.')
    await user.click(screen.getByRole('tab', { name: 'Preview' }))
    expect(await screen.findByRole('heading', { name: 'Week 1' })).toBeInTheDocument()
    expect(screen.getByText('chapter 2').tagName).toBe('EM')

    await user.selectOptions(screen.getByLabelText('Text format'), 'PLAIN')
    expect(screen.queryByRole('tab')).toBeNull()
    expect(screen.getByLabelText('Content')).toHaveValue('## Week 1\n\nRead *chapter 2*.')
    expect(screen.getByText('Plain text. Line breaks are kept.')).toBeInTheDocument()
  })
})
