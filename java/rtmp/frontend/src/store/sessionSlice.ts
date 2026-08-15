import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { createStreamSession } from '../api/session'
import type { StreamSession } from '../api/types'

interface SessionState {
  status: 'idle' | 'loading' | 'success' | 'error'
  error: string
  session: StreamSession | null
  expiresAt: number | null
}

const initialState: SessionState = {
  status: 'idle',
  error: '',
  session: null,
  expiresAt: null,
}

export const createSession = createAsyncThunk('session/create', async () => {
  return createStreamSession()
})

const sessionSlice = createSlice({
  name: 'session',
  initialState,
  reducers: {
    sessionExpired(state) {
      state.status = 'idle'
      state.error = ''
      state.session = null
      state.expiresAt = null
    },
    resetSession(state) {
      state.status = 'idle'
      state.error = ''
      state.session = null
      state.expiresAt = null
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(createSession.pending, (state) => {
        state.status = 'loading'
        state.error = ''
      })
      .addCase(createSession.fulfilled, (state, action) => {
        state.status = 'success'
        state.session = action.payload
        state.expiresAt = Date.now() + action.payload.expiresInSeconds * 1000
      })
      .addCase(createSession.rejected, (state, action) => {
        state.status = 'error'
        state.error = action.error.message ?? 'Could not create stream session'
      })
  },
})

export const { sessionExpired, resetSession } = sessionSlice.actions
export default sessionSlice.reducer