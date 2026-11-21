import axios from 'axios'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api/v1'
const ACCESS_TOKEN_KEY = 'washcare.accessToken'

let accessToken: string | null = sessionStorage.getItem(ACCESS_TOKEN_KEY)

export const api = axios.create({ baseURL: API_URL })

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

export function setAccessToken(token: string | null) {
  accessToken = token
  if (token) {
    sessionStorage.setItem(ACCESS_TOKEN_KEY, token)
  } else {
    sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  }
}

export function getAccessToken() {
  return accessToken
}
