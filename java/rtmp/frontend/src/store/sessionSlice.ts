import { createAsyncThunk, createSlice } from '@reduxjs/toolkit'
import { ApiError } from '../api/client'
import { createStreamSession } from '../api/session'
import type { StreamSession } from '../api/types'

interface SessionState {
  status: 'idle' | 'loading' | 'success' | 'error'
  error: string
  statusCode: number | null
  session: StreamSession | null
  expiresAt: number | null
}

const initialState: SessionState = {
  status: 'idle',
  error: '',
  statusCode: null,
  session: null,
  expiresAt: null,
}

export const createSession = createAsyncThunk('session/create', async (_, { rejectWithValue }) => {
  try {
    return await createStreamSession()
  } catch (error) {
    return rejectWithValue({
      status: error instanceof ApiError ? error.status : null,
      message: error instanceof Error ? error.message : 'Could not create stream session',
    })
  }
})

const sessionSlice = createSlice({
  name: 'session',
  initialState,
  reducers: {
    sessionExpired(state) {
      state.status = 'idle'
      state.error = ''
      state.statusCode = null
      state.session = null
      state.expiresAt = null
    },
    resetSession(state) {
      state.status = 'idle'
      state.error = ''
      state.statusCode = null
      state.session = null
      state.expiresAt = null
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(createSession.pending, (state) => {
        state.status = 'loading'
        state.error = ''
        state.statusCode = null
      })
      .addCase(createSession.fulfilled, (state, action) => {
        state.status = 'success'
        state.session = action.payload
        state.statusCode = null
        state.expiresAt = Date.now() + action.payload.expiresInSeconds * 1000
      })
      .addCase(createSession.rejected, (state, action) => {
        const payload = action.payload as { status: number | null; message: string } | undefined
        state.status = 'error'
        state.statusCode = payload?.status ?? null
        state.error = payload?.message ?? action.error.message ?? 'Could not create stream session'
      })
  },
})

export const { sessionExpired, resetSession } = sessionSlice.actions
export default sessionSlice.reducer