import { Button } from './components/Button'
import { StatusBadge } from './components/StatusBadge'

function App() {
  return (
    <main className="min-h-screen bg-canvas px-4 py-8 sm:px-6 lg:px-8 lg:py-12">
      <div className="mx-auto max-w-5xl">
        <header className="border-b border-line pb-7">
          <div className="flex items-center gap-3">
            <div
              aria-hidden="true"
              className="flex size-10 items-center justify-center rounded-control bg-stacc-primary text-sm font-bold tracking-wide text-white"
            >
              ST
            </div>
            <div>
              <p className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
                UI foundation preview
              </p>
              <h1 className="text-3xl font-bold tracking-tight text-ink sm:text-4xl">
                Stacc
              </h1>
            </div>
          </div>

          <p className="mt-5 max-w-2xl text-lg font-medium text-ink">
            Smart College Learning and Campus Management System
          </p>
          <p className="mt-2 max-w-2xl text-sm leading-6 text-ink-muted sm:text-base">
            The core visual foundation is ready for future Stacc modules. This
            specimen checks typography, controls, status states, and content
            surfaces only.
          </p>
        </header>

        <section className="mt-8" aria-labelledby="components-heading">
          <div className="flex flex-col gap-2 border-b border-line pb-4 sm:flex-row sm:items-end sm:justify-between">
            <div>
              <p className="text-xs font-semibold tracking-[0.08em] text-stacc-primary uppercase">
                Foundation 01
              </p>
              <h2
                id="components-heading"
                className="mt-1 text-xl font-semibold tracking-tight text-ink"
              >
                Core interface elements
              </h2>
            </div>
            <p className="text-sm text-ink-muted">
              Academic Precision · light interface
            </p>
          </div>

          <div className="mt-6 grid gap-6 lg:grid-cols-2">
            <section
              aria-labelledby="controls-heading"
              className="rounded-panel border border-line bg-surface p-5 sm:p-6"
            >
              <h3 id="controls-heading" className="text-base font-semibold text-ink">
                Controls
              </h3>
              <p className="mt-1 text-sm leading-5 text-ink-muted">
                Restrained actions and a clear, labeled input state.
              </p>

              <div className="mt-5 flex flex-col gap-3 sm:flex-row">
                <Button>Primary action</Button>
                <Button variant="secondary">Secondary action</Button>
              </div>

              <div className="mt-6">
                <label
                  htmlFor="college-id"
                  className="block text-sm font-medium text-ink"
                >
                  College ID example
                </label>
                <input
                  id="college-id"
                  name="college-id"
                  type="text"
                  placeholder="Enter college ID"
                  className="mt-2 h-11 w-full rounded-control border border-line-strong bg-surface px-3 text-sm text-ink outline-none placeholder:text-slate-400 focus:border-stacc-primary focus:ring-3 focus:ring-blue-100"
                />
                <p className="mt-2 text-xs leading-5 text-ink-muted">
                  Example control only. No information is submitted or stored.
                </p>
              </div>
            </section>

            <article className="rounded-panel border border-line bg-surface">
              <div className="flex items-center justify-between gap-4 border-b border-line px-5 py-4 sm:px-6">
                <div>
                  <p className="text-xs font-semibold tracking-[0.08em] text-ink-muted uppercase">
                    Information card
                  </p>
                  <h3 className="mt-1 text-base font-semibold text-ink">
                    Academic Precision
                  </h3>
                </div>
                <StatusBadge>Foundation ready</StatusBadge>
              </div>

              <div className="px-5 py-5 sm:px-6 sm:py-6">
                <p className="text-sm leading-6 text-ink-muted">
                  Stacc uses neutral reading surfaces, crisp structural borders,
                  compact radii, and collegiate blue for high-priority actions.
                </p>

                <dl className="mt-5 grid grid-cols-2 gap-x-4 gap-y-4 border-t border-line pt-5 text-sm">
                  <div>
                    <dt className="text-xs font-medium text-ink-muted">Canvas</dt>
                    <dd className="mt-1 font-semibold text-ink">Slate 50</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-ink-muted">Accent</dt>
                    <dd className="mt-1 font-semibold text-stacc-primary">
                      Collegiate blue
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-ink-muted">Radius</dt>
                    <dd className="mt-1 font-semibold text-ink">4–8px</dd>
                  </div>
                  <div>
                    <dt className="text-xs font-medium text-ink-muted">Depth</dt>
                    <dd className="mt-1 font-semibold text-ink">Border-led</dd>
                  </div>
                </dl>
              </div>
            </article>
          </div>
        </section>
      </div>
    </main>
  )
}

export default App
