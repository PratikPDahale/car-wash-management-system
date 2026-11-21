import { useState, type FormEvent } from 'react'
import axios from 'axios'
import { ArrowRight, ShieldCheck, Waves } from 'lucide-react'
import { api } from './api'

export type AuthUser = {
  id: number
  name: string
  email: string
  role: 'OWNER' | 'STAFF' | 'CUSTOMER'
}

type AuthResponse = { token: string; user: AuthUser }
type Mode = 'login' | 'register'

export function AuthScreen({ onAuthenticated }: { onAuthenticated: (response: AuthResponse) => void }) {
  const [mode, setMode] = useState<Mode>('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [phone, setPhone] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [submitting, setSubmitting] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setError('')
    setSubmitting(true)
    try {
      const response = mode === 'login'
        ? await api.post<AuthResponse>('/auth/login', { email, password })
        : await api.post<AuthResponse>('/auth/register', { name, email, phone, password })
      onAuthenticated(response.data)
    } catch (requestError) {
      if (axios.isAxiosError(requestError)) {
        setError(requestError.response?.data?.detail ?? requestError.response?.data?.message ?? 'Could not connect to WashCare. Check that the API is running and try again.')
      } else {
        setError('Something went wrong. Please try again.')
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand">
          <div className="brand-mark"><Waves size={21} strokeWidth={2.4} /></div>
          <div><strong>washcare</strong><span>SHOP WORKSPACE</span></div>
        </div>
        <div className="auth-eyebrow"><span className="eyebrow-dot" /> YOUR SHOP, IN SYNC</div>
        <h1>{mode === 'login' ? 'Welcome back.' : 'Your next great wash starts here.'}</h1>
        <p className="auth-description">
          {mode === 'login'
            ? 'Sign in to your WashCare workspace.'
            : 'Create a customer account to keep your visits and vehicles together.'}
        </p>

        <div className="auth-tabs" role="tablist" aria-label="Account access">
          <button aria-selected={mode === 'login'} role="tab" onClick={() => { setMode('login'); setError('') }}>Sign in</button>
          <button aria-selected={mode === 'register'} role="tab" onClick={() => { setMode('register'); setError('') }}>Create account</button>
        </div>

        <form className="auth-form" onSubmit={submit}>
          {mode === 'register' && (
            <>
              <label htmlFor="auth-name">Full name</label>
              <input autoComplete="name" id="auth-name" maxLength={160} onChange={(event) => setName(event.target.value)} placeholder="Your name" required value={name} />
            </>
          )}
          <label htmlFor="auth-email">Email address</label>
          <input autoComplete="email" id="auth-email" onChange={(event) => setEmail(event.target.value)} placeholder="you@example.com" required type="email" value={email} />
          {mode === 'register' && (
            <>
              <label htmlFor="auth-phone">Phone number</label>
              <input autoComplete="tel" id="auth-phone" onChange={(event) => setPhone(event.target.value)} pattern="[+0-9() .-]{7,32}" placeholder="+91 98765 43210" required value={phone} />
            </>
          )}
          <label htmlFor="auth-password">Password</label>
          <input autoComplete={mode === 'login' ? 'current-password' : 'new-password'} id="auth-password" minLength={mode === 'register' ? 10 : undefined} onChange={(event) => setPassword(event.target.value)} placeholder={mode === 'register' ? 'At least 10 characters' : 'Enter your password'} required type="password" value={password} />

          {error && <div className="form-error" role="alert">{error}</div>}
          <button className="primary-button auth-submit" disabled={submitting} type="submit">
            {submitting ? 'Please wait…' : mode === 'login' ? 'Sign in to WashCare' : 'Create customer account'} <ArrowRight size={16} />
          </button>
        </form>

        <div className="auth-security"><ShieldCheck size={16} /><span>Your information is protected and visible only to your account.</span></div>
        <p className="auth-footnote">Shop owner? Sign in with the owner account configured for this shop.</p>
      </section>
    </main>
  )
}
