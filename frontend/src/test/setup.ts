import '@testing-library/jest-dom/vitest'

// jsdom gaps used by UI libraries (input-otp, radix).
document.elementFromPoint ??= () => null
globalThis.ResizeObserver ??= class {
  observe() {}
  unobserve() {}
  disconnect() {}
}
