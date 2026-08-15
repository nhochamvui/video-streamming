import { useEffect } from 'react'
import { useAppDispatch, useAppSelector } from '../../store/hooks'
import { createSession, sessionExpired } from '../../store/sessionSlice'
import { useCountdown, formatCountdown } from '../../hooks/useCountdown'
import { Button } from '../ui/Button'
import { Field } from '../ui/Field'
import { StatusMessage } from '../ui/StatusMessage'
import { CopyButton } from '../ui/CopyButton'

export function CreateSessionCard() {
  const dispatch = useAppDispatch()
  const { status, error, session, expiresAt } = useAppSelector((state) => state.session)
  const remaining = useCountdown(expiresAt)

  const busy = status === 'loading'
  const expired = session !== null && expiresAt !== null && Date.now() >= expiresAt

  useEffect(() => {
    if (expired) {
      dispatch(sessionExpired())
    }
  }, [expired, dispatch])

  const handleCreate = () => {
    void dispatch(createSession())
  }

  const statusType = status === 'error' ? 'error' : status === 'success' ? 'ok' : undefined
  const statusMessage =
    status === 'error'
      ? error
      : status === 'success' && session
        ? 'Stream key created. Start publishing before it expires.'
        : ''

  return (
    <div>
      <Button onClick={handleCreate} disabled={busy}>
        {status === 'success' ? 'Generate new' : 'Create stream session'}
      </Button>
      <StatusMessage message={statusMessage} type={statusType} />

      {session ? (
        <div className="result" aria-live="polite">
          <Field label="Server URL" value={session.serverUrl} action={<CopyButton value={session.serverUrl} disabled={expired} />} />
          <Field label="Stream Key" value={session.streamKey} action={<CopyButton value={session.streamKey} disabled={expired} />} />
          <Field
            label="Playback URL"
            value={session.playbackUrl}
            action={
              expired ? null : (
                <a
                  className="button-link"
                  href={session.playbackUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                >
                  Open
                </a>
              )
            }
          />
          <Field
            label="Expires In"
            value={formatCountdown(remaining)}
            action={
              <Button variant="secondary" onClick={handleCreate} disabled={busy}>
                Generate new
              </Button>
            }
          />
        </div>
      ) : null}

      {expired ? (
        <StatusMessage
          message="This stream key has expired. Generate a new session before publishing."
          type="error"
        />
      ) : null}
    </div>
  )
}