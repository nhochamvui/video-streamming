import { configureStore } from '@reduxjs/toolkit'
import sessionReducer from './sessionSlice'
import streamsReducer from './streamsSlice'

export const store = configureStore({
  reducer: {
    session: sessionReducer,
    streams: streamsReducer,
  },
})

export type RootState = ReturnType<typeof store.getState>
export type AppDispatch = typeof store.dispatch