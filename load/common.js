import http from 'k6/http'
import { check } from 'k6'

export const BASE = `${__ENV.BASE_URL || 'http://localhost:8080'}/api/v1`
const PASSWORD = __ENV.STACC_TEST_PASSWORD

/** Signs in through the one login and returns headers carrying the session token for API clients. */
export function signIn(loginId) {
  const response = http.post(`${BASE}/auth/login`, JSON.stringify({ loginId, password: PASSWORD }), {
    headers: { 'Content-Type': 'application/json' },
  })
  check(response, { 'signed in': (r) => r.status === 200 })
  const token = response.cookies.STACC_SESSION && response.cookies.STACC_SESSION[0] ? response.cookies.STACC_SESSION[0].value : null
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' } }
}

function failedChecks(group, path, out) {
  for (const check of group.checks || []) {
    if (check.fails > 0) {
      out.push(`${path}${check.name}: ${check.fails} failed, ${check.passes} passed`)
    }
  }
  for (const child of group.groups || []) {
    failedChecks(child, `${path}${child.name} › `, out)
  }
  return out
}

/**
 * The run's result as GitHub annotations: a notice with the request timings, and an error for each crossed threshold
 * and failed check, so CI shows them on the run's page. Use as `export const handleSummary = annotate('name')`.
 */
export function annotate(name) {
  return (data) => {
    const duration = data.metrics.http_req_duration ? data.metrics.http_req_duration.values : {}
    const requests = data.metrics.http_reqs ? data.metrics.http_reqs.values.count : 0
    const fixed = (value) => (value === undefined ? '?' : value.toFixed(0))
    const lines = [
      `::notice title=${name}::${requests} requests; p50 ${fixed(duration.med)} ms, p95 ${fixed(duration['p(95)'])} ms, max ${fixed(duration.max)} ms`,
    ]
    for (const [metric, result] of Object.entries(data.metrics)) {
      for (const [threshold, outcome] of Object.entries(result.thresholds || {})) {
        if (!outcome.ok) {
          lines.push(`::error title=${name}::Threshold crossed: ${metric} ${threshold} (${JSON.stringify(result.values)})`)
        }
      }
    }
    for (const failed of failedChecks(data.root_group, '', [])) {
      lines.push(`::error title=${name}::Check failed: ${failed}`)
    }
    return { stdout: `${lines.join('\n')}\n` }
  }
}
