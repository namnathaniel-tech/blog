/**
 * The ONLY place that calls fetch(). Every API module goes through `api()`.
 *
 * - Same-origin requests with credentials, so the session cookie (admin) and
 *   the signed guest cookie (readers) are always sent.
 * - Spring Security issues a readable XSRF-TOKEN cookie; we echo it back in the
 *   X-XSRF-TOKEN header on every state-changing request.
 * - Errors are RFC 9457 Problem Details (Spring's ProblemDetail) -> ApiError.
 */

export interface ProblemDetail {
  type?: string
  title?: string
  status: number
  detail?: string
  instance?: string
  /** Field-level validation errors, populated by GlobalExceptionHandler. */
  errors?: Record<string, string>
}

export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${problem.status}`)
    this.status = problem.status
    this.problem = problem
  }
}

const UNSAFE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE'])

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((c) => c.startsWith(`${name}=`))
    ?.split('=')[1]
}

export async function api<T>(path: string, init: RequestInit = {}): Promise<T> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')

  if (init.body && !(init.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }
  if (UNSAFE_METHODS.has(method)) {
    let token = readCookie('XSRF-TOKEN')
    if (!token) {
      // Spring issues the CSRF cookie on any response; make sure we have one before writing.
      await fetch('/api/public/meta', { credentials: 'same-origin' })
      token = readCookie('XSRF-TOKEN')
    }
    if (token) headers.set('X-XSRF-TOKEN', decodeURIComponent(token))
  }

  const res = await fetch(`/api${path}`, { ...init, method, headers, credentials: 'same-origin' })

  if (!res.ok) {
    let problem: ProblemDetail = { status: res.status, title: res.statusText }
    try {
      problem = { ...problem, ...(await res.json()) }
    } catch {
      /* non-JSON error body */
    }
    throw new ApiError(problem)
  }
  if (res.status === 204) return undefined as T
  return (await res.json()) as T
}
