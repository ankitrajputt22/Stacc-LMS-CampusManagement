import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { FormattedText } from './FormattedText'
import type { TextFormat } from './textFormat'

function show(text: string, format: TextFormat = 'FORMATTED') {
  return render(
    <MemoryRouter>
      <FormattedText text={text} format={format} headingLevel={3} />
    </MemoryRouter>,
  )
}

describe('FormattedText', () => {
  it('shows plain text exactly as typed', () => {
    const { container } = show('**not bold**\n<b>not html</b>', 'PLAIN')

    expect(container).toHaveTextContent('**not bold** <b>not html</b>')
    expect(container.querySelector('b, strong')).toBeNull()
  })

  it('renders the formatting set with headings under the page’s own', async () => {
    show('# Costs\n\n- **Arrays** read in `O(1)`\n- lists\n\n| Op | Cost |\n| --- | --- |\n| read | fast |\n\n```\nx = 1\n```')

    expect(await screen.findByRole('heading', { level: 3, name: 'Costs' })).toBeInTheDocument()
    expect(screen.getAllByRole('listitem')).toHaveLength(2)
    expect(screen.getByText('Arrays').tagName).toBe('STRONG')
    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.getByText('x = 1').closest('pre')).not.toBeNull()
  })

  it('never renders HTML, images, or unsafe links', async () => {
    const { container } = show(
      'Safe [notes](https://example.org) and [course](/courses/42).\n\n' +
        '<script>alert(1)</script><img src=x onerror=alert(2)>\n\n' +
        '[bad](javascript:alert(3)) ![picture](https://example.org/x.png) <a href="https://evil.example">raw</a>',
    )

    const external = await screen.findByRole('link', { name: /notes/ })
    expect(external).toHaveAttribute('href', 'https://example.org')
    expect(external).toHaveAttribute('target', '_blank')
    expect(external).toHaveAttribute('rel', expect.stringContaining('noopener'))
    expect(screen.getByRole('link', { name: 'course' })).toHaveAttribute('href', '/courses/42')
    expect(screen.getAllByRole('link')).toHaveLength(2)
    expect(screen.getByText('bad').tagName).toBe('SPAN')
    expect(container.querySelector('script, img, [onerror]')).toBeNull()
    expect(container.innerHTML).not.toContain('javascript:')
    expect(container.innerHTML).not.toContain('evil.example')
  })

  it('draws maths without trusted commands', async () => {
    const { container } = show('Area is $\\pi r^2$ and $\\href{javascript:alert(1)}{click}$.')

    await screen.findByText(/Area is/)
    expect(container.querySelector('.katex')).not.toBeNull()
    // The refused \\href is shown as words, never as a link.
    expect(container.querySelector('a, [href]')).toBeNull()
  })
})
