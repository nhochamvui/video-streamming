import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { fetchAuthStatus, login as loginApi, logout as logoutApi } from '../api/auth'

interface AuthState {
  status: 'checking' | 'authenticated' | 'unauthenticated'
  username: string | null
  error: string
}

const initialState: AuthState = {
  status: 'checking',
  username: null,
  error: '',
}

export const checkAuth = createAsyncThunk('auth/check', async () => {
  return fetchAuthStatus()
})

export const login = createAsyncThunk('auth/login', async (credentials: { username: string; password: string }) => {
  return loginApi(credentials.username, credentials.password)
})

export const logout = createAsyncThunk('auth/logout', async () => {
  return logoutApi()
})

const authSlice = createSlice({
  name: 'auth',
  initialState,
  reducers: {
    resetAuth(state) {
      state.status = 'unauthenticated'
      state.username = null
      state.error = ''
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(checkAuth.fulfilled, (state, action) => {
        state.status = action.payload.authenticated ? 'authenticated' : 'unauthenticated'
        state.username = action.payload.username
        state.error = ''
      })
      .addCase(checkAuth.rejected, (state) => {
        state.status = 'unauthenticated'
        state.username = null
      })
      .addCase(login.pending, (state) => {
        state.status = 'checking'
        state.error = ''
      })
      .addCase(login.fulfilled, (state, action) => {
        state.status = 'authenticated'
        state.username = action.payload.username
        state.error = ''
      })
      .addCase(login.rejected, (state, action) => {
        state.status = 'unauthenticated'
        state.username = null
        state.error = action.error.message ?? 'Login failed'
      })
      .addCase(logout.fulfilled, (state) => {
        state.status = 'unauthenticated'
        state.username = null
        state.error = ''
      })
  },
})

export const { resetAuth } = authSlice.actions
export default authSlice.reducer