const TOKEN_KEY = 'srm_token'
const USER_KEY = 'srm_username'

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setAuth(token, username) {
  sessionStorage.setItem(TOKEN_KEY, token)
  if (username) {
    sessionStorage.setItem(USER_KEY, username)
  }
}

export function clearAuth() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
}

export function getUsername() {
  return sessionStorage.getItem(USER_KEY)
}


