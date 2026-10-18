// Access token: kept in memory, mirrored to sessionStorage so a page refresh keeps
// the user signed in while closing the tab signs them out.
const KEY = 'erp.token'

let token: string | null = read()

function read(): string | null {
  try {
    return sessionStorage.getItem(KEY)
  } catch {
    return null
  }
}

export const tokenStore = {
  get: () => token,
  set(value: string) {
    token = value
    try {
      sessionStorage.setItem(KEY, value)
    } catch {
      // storage unavailable: memory only
    }
  },
  clear() {
    token = null
    try {
      sessionStorage.removeItem(KEY)
    } catch {
      // ignore
    }
  },
}
