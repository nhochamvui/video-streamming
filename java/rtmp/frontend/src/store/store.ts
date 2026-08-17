import { configureStore } from '@reduxjs/toolkit'
import sessionReducer from './sessionSlice'
import streamsReducer from './streamsSlice'
import authReducer from './authSlice'

export const store = configureStore({
  reducer: {
    session: sessionReducer,
    streams: streamsReducer,
    auth: authReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch