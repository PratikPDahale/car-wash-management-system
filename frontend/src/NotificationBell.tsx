import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'
import axios from 'axios'
import { Bell, Check, CheckCheck, Clock3, Megaphone, Settings2, Trash2, X } from 'lucide-react'
import type { AuthUser } from './AuthScreen'
import { api } from './api'

type NotificationItem = {
  id: number
  category: string
  priority: 'NORMAL' | 'IMPORTANT'
  title: string
  message: string
  targetSection: string
  targetId: number | null
  createdAt: string
  readAt: string | null
}
type Inbox = { items: NotificationItem[]; unreadCount: number; limit: number }
type Preferences = {
  enabledCategories: string[]
  quietHoursStart: string | null
  quietHoursEnd: string | null
  globalMuted: boolean
}

const categories = [
  ['BOOKING', 'Bookings'],
  ['PAYMENT', 'Payments'],
  ['LOYALTY', 'Loyalty'],
  ['OFFER', 'Offers'],
  ['REMINDER', 'Reminders'],
  ['REVIEW', 'Reviews'],
  ['SYSTEM', 'Account and system'],
  ['ANNOUNCEMENT', 'Shop announcements'],
] as const

const destinations = new Set(['Bookings', 'Retention', 'Overview', 'Reports'])

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as { detail?: string; message?: string } | undefined
    return body?.detail ?? body?.message ?? (error.response?.status
      ? `Notifications request failed (HTTP ${error.response.status}).`
      : 'Could not reach the WashCare API. Check that it is running and try again.')
  }
  return 'Something went wrong while loading notifications.'
}

function timeAgo(value: string) {
  const date = new Date(value)
  const seconds = Math.max(0, Math.floor((Date.now() - date.getTime()) / 1000))
  if (seconds < 60) return 'Just now'
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} hr ago`
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(date)
}

export function NotificationBell({
  user,
  onNavigate,
}: {
  user: AuthUser
  onNavigate: (section: 'Bookings' | 'Retention' | 'Overview' | 'Reports') => void
}) {
  const [open, setOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [inbox, setInbox] = useState<Inbox | null>(null)
  const [preferences, setPreferences] = useState<Preferences | null>(null)
  const [filter, setFilter] = useState<'ALL' | 'UNREAD'>('ALL')
  const [showPreferences, setShowPreferences] = useState(false)
  const [announcementOpen, setAnnouncementOpen] = useState(false)
  const [announcementTitle, setAnnouncementTitle] = useState('')
  const [announcementMessage, setAnnouncementMessage] = useState('')
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const panelRef = useRef<HTMLDivElement>(null)

  const refresh = useCallback(async (showSpinner = true) => {
    if (showSpinner) setLoading(true)
    setError('')
    try {
      const response = await api.get<Inbox>('/notifications')
      setInbox(response.data)
      setUnreadCount(response.data.unreadCount)
      if (showSpinner) {
        const preferenceResponse = await api.get<Preferences>('/notifications/preferences')
        setPreferences(preferenceResponse.data)
      }
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      if (showSpinner) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void refresh(false)
    const interval = window.setInterval(() => { void refresh(false) }, 30_000)
    return () => window.clearInterval(interval)
  }, [refresh, user.id])

  useEffect(() => {
    if (!open) return
    function dismissOutside(event: PointerEvent) {
      if (event.target instanceof Node && !panelRef.current?.contains(event.target)) setOpen(false)
    }
    function dismissEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', dismissOutside)
    document.addEventListener('keydown', dismissEscape)
    return () => {
      document.removeEventListener('pointerdown', dismissOutside)
      document.removeEventListener('keydown', dismissEscape)
    }
  }, [open])

  async function markRead(item: NotificationItem, read: boolean) {
    setError('')
    try {
      await api.put(`/notifications/${item.id}/${read ? 'read' : 'unread'}`)
      await refresh(false)
    } catch (requestError) {
      setError(errorMessage(requestError))
    }
  }

  async function openNotification(item: NotificationItem) {
    if (!item.readAt) {
      try {
        await api.put(`/notifications/${item.id}/read`)
      } catch (requestError) {
        setError(errorMessage(requestError))
        return
      }
    }
    setOpen(false)
    if (destinations.has(item.targetSection)) {
      onNavigate(item.targetSection as 'Bookings' | 'Retention' | 'Overview' | 'Reports')
    }
    await refresh(false)
  }

  async function markAllRead() {
    setError('')
    try {
      await api.put('/notifications/read-all')
      await refresh(false)
    } catch (requestError) {
      setError(errorMessage(requestError))
    }
  }

  async function deleteNotification(item: NotificationItem) {
    setError('')
    try {
      await api.delete(`/notifications/${item.id}`)
      await refresh(false)
    } catch (requestError) {
      setError(errorMessage(requestError))
    }
  }

  async function savePreferences(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!preferences) return
    setSaving(true)
    setError('')
    try {
      const response = await api.put<Preferences>('/notifications/preferences', preferences)
      setPreferences(response.data)
      await refresh(false)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  async function sendAnnouncement(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    try {
      await api.post('/notifications/announcements', {
        title: announcementTitle.trim(),
        message: announcementMessage.trim(),
      })
      setAnnouncementTitle('')
      setAnnouncementMessage('')
      setAnnouncementOpen(false)
      await refresh(false)
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const visibleItems = (inbox?.items ?? []).filter((item) => filter === 'ALL' || !item.readAt)

  return (
    <div className="notification-wrapper" ref={panelRef}>
      <button
        aria-expanded={open}
        aria-label={unreadCount ? `Notifications, ${unreadCount} unread` : 'Notifications'}
        className="icon-button notification-button"
        onClick={() => {
          setOpen((current) => !current)
          if (!open) void refresh()
        }}
        type="button"
      >
        <Bell size={18} />
        {unreadCount > 0 && <span className="notification-count">{unreadCount > 99 ? '99+' : unreadCount}</span>}
      </button>

      {open && (
        <section aria-label="Notifications" className="notification-panel" role="dialog">
          <div className="notification-panel-heading">
            <div><h2>Notifications</h2><p>{unreadCount} unread</p></div>
            <div className="notification-panel-actions">
              <button aria-label="Notification preferences" className="icon-button" onClick={() => { setShowPreferences((value) => !value); setAnnouncementOpen(false) }} type="button"><Settings2 size={15} /></button>
              {user.role === 'OWNER' && <button aria-label="Send shop announcement" className="icon-button" onClick={() => { setAnnouncementOpen((value) => !value); setShowPreferences(false) }} type="button"><Megaphone size={15} /></button>}
              <button aria-label="Close notifications" className="icon-button" onClick={() => setOpen(false)} type="button"><X size={15} /></button>
            </div>
          </div>

          {error && <div className="notification-error" role="alert">{error}<button onClick={() => void refresh()}>Retry</button></div>}

          {showPreferences ? (
            <form className="notification-preferences" onSubmit={(event) => void savePreferences(event)}>
              <h3>Notification preferences</h3>
              {preferences ? (
                <>
                  <div className="notification-category-options">
                    {categories.map(([key, label]) => (
                      <label key={key}><input
                        checked={preferences.enabledCategories.includes(key)}
                        onChange={(event) => setPreferences((current) => current && ({
                          ...current,
                          enabledCategories: event.target.checked
                            ? [...new Set([...current.enabledCategories, key])]
                            : current.enabledCategories.filter((item) => item !== key),
                        }))}
                        type="checkbox"
                      />{label}</label>
                    ))}
                  </div>
                  <label className="notification-toggle"><input checked={preferences.globalMuted} onChange={(event) => setPreferences((current) => current && ({ ...current, globalMuted: event.target.checked }))} type="checkbox" />Mute non-essential notifications</label>
                  <div className="notification-quiet-hours">
                    <span><Clock3 size={13} /> Quiet hours</span>
                    <label>From<input onChange={(event) => setPreferences((current) => current && ({ ...current, quietHoursStart: event.target.value || null }))} type="time" value={preferences.quietHoursStart ?? ''} /></label>
                    <label>Until<input onChange={(event) => setPreferences((current) => current && ({ ...current, quietHoursEnd: event.target.value || null }))} type="time" value={preferences.quietHoursEnd ?? ''} /></label>
                  </div>
                  <p className="notification-preference-note">Notifications arriving during quiet hours are held until quiet hours end. Important alerts are not muted globally.</p>
                  <button className="primary-button" disabled={saving} type="submit">{saving ? 'Saving…' : 'Save preferences'}</button>
                </>
              ) : <div className="notification-empty">Loading preferences…</div>}
            </form>
          ) : announcementOpen ? (
            <form className="notification-announcement" onSubmit={(event) => void sendAnnouncement(event)}>
              <h3>Send an announcement</h3>
              <p>This will notify all customers and staff.</p>
              <label>Title<input maxLength={120} onChange={(event) => setAnnouncementTitle(event.target.value)} required value={announcementTitle} /></label>
              <label>Message<textarea maxLength={500} onChange={(event) => setAnnouncementMessage(event.target.value)} required rows={3} value={announcementMessage} /></label>
              <div><button className="secondary-button" onClick={() => setAnnouncementOpen(false)} type="button">Cancel</button><button className="primary-button" disabled={saving} type="submit">{saving ? 'Sending…' : 'Send announcement'}</button></div>
            </form>
          ) : (
            <>
              <div className="notification-list-toolbar">
                <div className="notification-filter">
                  <button aria-pressed={filter === 'ALL'} onClick={() => setFilter('ALL')} type="button">All</button>
                  <button aria-pressed={filter === 'UNREAD'} onClick={() => setFilter('UNREAD')} type="button">Unread</button>
                </div>
                <button className="notification-mark-all" disabled={!unreadCount} onClick={() => void markAllRead()} type="button"><CheckCheck size={13} /> Mark all read</button>
              </div>
              <div className="notification-list">
                {loading ? <div className="notification-empty">Loading notifications…</div> : visibleItems.length === 0 ? (
                  <div className="notification-empty"><Bell size={21} /><strong>{filter === 'UNREAD' ? 'You’re all caught up' : 'No notifications yet'}</strong><span>New shop activity will appear here.</span></div>
                ) : visibleItems.map((item) => (
                  <article className={`notification-item ${item.readAt ? 'notification-item-read' : 'notification-item-unread'}`} key={item.id}>
                    <button className="notification-item-content" onClick={() => void openNotification(item)} type="button">
                      <span className={`notification-priority notification-priority-${item.priority.toLowerCase()}`} />
                      <span className="notification-item-copy">
                        <strong>{item.title}</strong>
                        <span>{item.message}</span>
                        <small>{item.category.replaceAll('_', ' ').toLowerCase()} · {timeAgo(item.createdAt)}</small>
                      </span>
                    </button>
                    <div className="notification-item-controls">
                      <button aria-label={item.readAt ? 'Mark unread' : 'Mark read'} onClick={() => void markRead(item, !item.readAt)} type="button"><Check size={13} /></button>
                      <button aria-label="Delete notification" onClick={() => void deleteNotification(item)} type="button"><Trash2 size={13} /></button>
                    </div>
                  </article>
                ))}
              </div>
              <div className="notification-panel-footer">Showing the latest {inbox?.limit ?? 50}. Notifications are kept until deleted.</div>
            </>
          )}
        </section>
      )}
    </div>
  )
}
