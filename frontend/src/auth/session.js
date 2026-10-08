const TOKEN_KEY = 'srm_token'
const USER_KEY = 'srm_username'
const ADMIN_KEY = 'srm_is_admin'

export function getToken() {
  return sessionStorage.getItem(TOKEN_KEY)
}

export function setAuth(token, username, isAdmin) {
  sessionStorage.setItem(TOKEN_KEY, token)
  if (username) {
    sessionStorage.setItem(USER_KEY, username)
  }
  if (isAdmin === 1 || isAdmin === '1') {
    sessionStorage.setItem(ADMIN_KEY, '1')
  } else {
    sessionStorage.removeItem(ADMIN_KEY)
  }
}

export function clearAuth() {
  sessionStorage.removeItem(TOKEN_KEY)
  sessionStorage.removeItem(USER_KEY)
  sessionStorage.removeItem(ADMIN_KEY)
}

export function getUsername() {
  return sessionStorage.getItem(USER_KEY)
}

export function getIsAdmin() {
  return sessionStorage.getItem(ADMIN_KEY) === '1'
}


