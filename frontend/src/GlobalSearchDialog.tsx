import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import axios from 'axios'
import { CalendarDays, CarFront, Search, Sparkles, UserRound, X } from 'lucide-react'
import { api } from './api'

type SearchResults = {
  customers: { id: number; name: string; phone: string; email: string | null }[]
  vehicles: { id: number; customerName: string; registrationNo: string; type: string }[]
  bookings: {
    id: number
    customerName: string
    registrationNo: string
    serviceName: string
    date: string
    startTime: string
    status: string
  }[]
  services: { id: number; name: string; description: string | null; price: number; active: boolean }[]
}

export type SearchSection = 'Customers' | 'Bookings' | 'Services'
export type SearchRequest = { query: string; key: number; section: SearchSection }

export function GlobalSearchDialog({
  onClose,
  onSelect,
}: {
  onClose: () => void
  onSelect: (section: SearchSection, query: string) => void
}) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResults | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const normalizedQuery = query.trim()

  useEffect(() => {
    if (normalizedQuery.length < 2) {
      setResults(null)
      setError('')
      setLoading(false)
      return
    }

    let cancelled = false
    const timeout = window.setTimeout(() => {
      setLoading(true)
      setError('')
      api.get<SearchResults>('/search', { params: { q: normalizedQuery } })
        .then((response) => {
          if (!cancelled) setResults(response.data)
        })
        .catch((requestError: unknown) => {
          if (cancelled) return
          setResults(null)
          if (axios.isAxiosError(requestError) && requestError.response?.status) {
            setError(`Search failed (HTTP ${requestError.response.status}). Please try again.`)
          } else {
            setError('Could not reach the WashCare API. Check that it is running and try again.')
          }
        })
        .finally(() => {
          if (!cancelled) setLoading(false)
        })
    }, 220)

    return () => {
      cancelled = true
      window.clearTimeout(timeout)
    }
  }, [normalizedQuery])

  const hasResults = Boolean(results
    && (results.customers.length > 0 || results.vehicles.length > 0 || results.bookings.length > 0 || results.services.length > 0))

  return (
    <div
      className="search-overlay"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <section aria-label="Search WashCare" aria-modal="true" className="search-dialog" role="dialog">
        <div className="search-dialog-input">
          <Search aria-hidden="true" size={19} />
          <input
            autoFocus
            maxLength={100}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="Search customers, bookings, vehicles or services…"
            value={query}
          />
          <button aria-label="Close search" className="icon-button" onClick={onClose} type="button"><X size={16} /></button>
        </div>
        <div aria-live="polite" className="search-dialog-results">
          {normalizedQuery.length < 2 ? (
            <div className="search-hint">Type at least 2 characters to search your workspace.</div>
          ) : loading ? (
            <div className="search-hint">Searching…</div>
          ) : error ? (
            <div className="search-message search-message-error" role="alert">{error}</div>
          ) : !hasResults ? (
            <div className="search-hint">No matches found for “{normalizedQuery}”. Try a name, phone, booking ID, vehicle plate or service.</div>
          ) : (
            <>
              {results?.customers.length ? (
                <SearchGroup icon={<UserRound size={15} />} label="Customers">
                  {results.customers.map((customer) => (
                    <SearchResult
                      key={customer.id}
                      detail={[customer.phone, customer.email].filter(Boolean).join(' · ')}
                      title={customer.name}
                      onClick={() => onSelect('Customers', normalizedQuery)}
                    />
                  ))}
                </SearchGroup>
              ) : null}
              {results?.vehicles.length ? (
                <SearchGroup icon={<CarFront size={15} />} label="Vehicles">
                  {results.vehicles.map((vehicle) => (
                    <SearchResult
                      key={vehicle.id}
                      detail={`${vehicle.customerName} · ${vehicle.type.toLowerCase()}`}
                      title={vehicle.registrationNo}
                      onClick={() => onSelect('Customers', vehicle.customerName)}
                    />
                  ))}
                </SearchGroup>
              ) : null}
              {results?.bookings.length ? (
                <SearchGroup icon={<CalendarDays size={15} />} label="Bookings">
                  {results.bookings.map((booking) => (
                    <SearchResult
                      key={booking.id}
                      detail={`#${booking.id} · ${booking.customerName} · ${booking.registrationNo} · ${booking.serviceName} · ${booking.date} ${booking.startTime.slice(0, 5)} · ${booking.status.replaceAll('_', ' ')}`}
                      title={`Booking #${booking.id}`}
                      onClick={() => onSelect('Bookings', normalizedQuery)}
                    />
                  ))}
                </SearchGroup>
              ) : null}
              {results?.services.length ? (
                <SearchGroup icon={<Sparkles size={15} />} label="Services">
                  {results.services.map((service) => (
                    <SearchResult
                      key={service.id}
                      detail={`${service.description || 'Wash service'} · ${new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(service.price)}${service.active ? '' : ' · Paused'}`}
                      title={service.name}
                      onClick={() => onSelect('Services', normalizedQuery)}
                    />
                  ))}
                </SearchGroup>
              ) : null}
            </>
          )}
        </div>
        <div className="search-dialog-footer"><span><kbd>Esc</kbd> to close</span><span>Search is limited to records you can access.</span></div>
      </section>
    </div>
  )
}

function SearchGroup({ children, icon, label }: { children: ReactNode; icon: ReactNode; label: string }) {
  return (
    <section className="search-result-group">
      <h2>{icon}{label}</h2>
      {children}
    </section>
  )
}

function SearchResult({
  detail,
  onClick,
  title,
}: {
  detail: string
  onClick: () => void
  title: string
}) {
  return (
    <button className="search-result" onClick={onClick} type="button">
      <strong>{title}</strong>
      <span>{detail}</span>
    </button>
  )
}
