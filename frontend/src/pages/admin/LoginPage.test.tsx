import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { LoginPage } from './LoginPage'

type Handler = (init?: RequestInit) => Response

function mockApi(routes: Record<string, Handler>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const handler = routes[String(input)]
    if (!handler) throw new Error(`Unmocked fetch ${String(input)}`)
    return handler(init)
  })
}

const meta = (admin: boolean) => () =>
  new Response(JSON.stringify({ siteName: 'Blog', version: '1', admin }), { status: 200 })

function renderLogin() {
  const router = createMemoryRouter(
    [
      { path: '/admin/login', element: <LoginPage /> },
      { path: '/admin', element: <p>ADMIN HOME</p> },
    ],
    { initialEntries: ['/admin/login'] },
  )
  render(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  )
}

beforeEach(() => {
  document.cookie = 'XSRF-TOKEN=t'
})
afterEach(() => vi.restoreAllMocks())

describe('LoginPage', () => {
  it('moves from email to code step and shows the server message', async () => {
    const fetchMock = mockApi({
      '/api/public/meta': meta(false),
      '/api/auth/otp/request': () =>
        new Response(JSON.stringify({ message: 'If allowed, a code has been sent.' }), { status: 202 }),
    })
    renderLogin()

    fireEvent.change(await screen.findByLabelText('Email'), { target: { value: ' me@example.com ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Send code' }))

    expect(await screen.findByText('If allowed, a code has been sent.')).toBeInTheDocument()
    const call = fetchMock.mock.calls.find(([u]) => u === '/api/auth/otp/request')!
    expect(JSON.parse(String(call[1]!.body))).toEqual({ email: 'me@example.com' })
  })

  it('shows the server error for a bad recovery code', async () => {
    mockApi({
      '/api/public/meta': meta(false),
      '/api/auth/recovery': () =>
        new Response(JSON.stringify({ status: 401, detail: 'That code is invalid or has expired.' }), {
          status: 401,
        }),
    })
    renderLogin()

    fireEvent.click(await screen.findByRole('button', { name: 'Use a recovery code instead' }))
    fireEvent.change(screen.getByLabelText('Recovery code'), { target: { value: 'AAAA-BBBB-CCCC' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByText('That code is invalid or has expired.')).toBeInTheDocument()
  })

  it('redirects to /admin when already logged in', async () => {
    mockApi({ '/api/public/meta': meta(true) })
    renderLogin()
    await waitFor(() => expect(screen.getByText('ADMIN HOME')).toBeInTheDocument())
  })
})
