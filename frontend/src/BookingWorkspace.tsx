import { useCallback, useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { ArrowRight, CalendarDays, CarFront, Clock3, RefreshCw, Search, X } from 'lucide-react'
import type { AuthUser } from './AuthScreen'
import { api } from './api'
import type { SearchRequest } from './GlobalSearchDialog'

type Service = { id: number; name: string; price: number; estimatedDurationMinutes: number; active: boolean }
type Customer = { id: number; name: string; phone: string }
type Vehicle = { id: number; customerId: number; registrationNo: string; type: string }
type Booking = {
  id: number
  customerId: number
  customerName: string
  vehicleId: number
  registrationNo: string
  serviceId: number
  serviceName: string
  date: string
  startTime: string
  endTime: string
  source: 'ONLINE' | 'WALK_IN'
  status: 'CONFIRMED' | 'CHECKED_IN' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED'
  priceSnapshot: number
  notes: string | null
}
type Availability = { availableSlots: string[] }
type Invoice = {
  id: number
  bookingId: number
  customerId: number
  invoiceNo: string
  totalAmount: number
  paidAmount: number
  balance: number
  customerLoyaltyPoints: number
  maxRedeemablePoints: number
  payments: { id: number; amount: number; method: string; paidAt: string; recordedBy: string }[]
}

function messageFor(error: unknown) {
  if (axios.isAxiosError(error)) {
    const body: unknown = error.response?.data
    if (typeof body === 'string' && body.trim()) {
      return body.length < 300 && !body.trimStart().startsWith('<') ? body : `The request failed (HTTP ${error.response?.status ?? 'unknown'}).`
    }
    if (body && typeof body === 'object') {
      const details = body as { detail?: unknown; message?: unknown; error?: unknown; errors?: unknown }
      for (const value of [details.detail, details.message, details.error]) {
        if (typeof value === 'string' && value.trim()) return value
      }
      if (Array.isArray(details.errors) && details.errors.length) {
        return details.errors.map((item) => typeof item === 'string' ? item : JSON.stringify(item)).join(' ')
      }
    }
    if (error.response?.status) {
      return `The request failed (HTTP ${error.response.status}). Check the API logs for details and try again.`
    }
    return 'Could not connect to the WashCare API. Check that it is running and try again.'
  }
  return 'Something went wrong. Please try again.'
}

function todayInShop() {
  const now = new Date()
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60_000)
  return local.toISOString().slice(0, 10)
}

function money(amount: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount)
}

export function BookingWorkspace({ user, searchRequest }: { user: AuthUser; searchRequest: SearchRequest | null }) {
  const staffMode = user.role !== 'CUSTOMER'
  const [bookings, setBookings] = useState<Booking[]>([])
  const [invoices, setInvoices] = useState<Invoice[]>([])
  const [services, setServices] = useState<Service[]>([])
  const [customers, setCustomers] = useState<Customer[]>([])
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [availability, setAvailability] = useState<string[]>([])
  const [date, setDate] = useState(todayInShop)
  const [serviceId, setServiceId] = useState('')
  const [customerId, setCustomerId] = useState('')
  const [vehicleId, setVehicleId] = useState('')
  const [startTime, setStartTime] = useState('')
  const [notes, setNotes] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [showForm, setShowForm] = useState(false)
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [paymentInvoiceId, setPaymentInvoiceId] = useState<number | null>(null)
  const [paymentAmount, setPaymentAmount] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('CASH')
  const [paymentKey, setPaymentKey] = useState('')
  const [savingPayment, setSavingPayment] = useState(false)
  const [redemptionInvoiceId, setRedemptionInvoiceId] = useState<number | null>(null)
  const [redemptionPoints, setRedemptionPoints] = useState('')
  const [bookingSearch, setBookingSearch] = useState('')

  useEffect(() => {
    if (searchRequest) setBookingSearch(searchRequest.query)
  }, [searchRequest])

  const loadBookings = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const response = await api.get<Booking[]>(staffMode ? '/bookings' : '/bookings/me')
      setBookings(response.data)
    } catch (requestError) {
      setError(messageFor(requestError))
    } finally {
      setLoading(false)
    }
  }, [staffMode])

  const loadInvoices = useCallback(async () => {
    try {
      const response = await api.get<Invoice[]>(staffMode ? '/invoices' : '/invoices/me')
      setInvoices(response.data)
    } catch (requestError) {
      setError(messageFor(requestError))
    }
  }, [staffMode])

  useEffect(() => {
    void loadInvoices()
    void Promise.all([
      api.get<Service[]>('/services'),
      staffMode ? api.get<Customer[]>('/customers') : api.get<Vehicle[]>('/customers/me/vehicles'),
      loadBookings(),
    ]).then(([serviceResponse, peopleResponse]) => {
      setServices(serviceResponse.data)
      if (staffMode) {
        setCustomers(peopleResponse.data as Customer[])
      } else {
        setVehicles(peopleResponse.data as Vehicle[])
      }
      if (serviceResponse.data.length > 0) setServiceId(String(serviceResponse.data[0].id))
    }).catch((requestError: unknown) => {
      setError(messageFor(requestError))
    })
  }, [loadBookings, loadInvoices, staffMode])

  useEffect(() => {
    if (!staffMode || !customerId) {
      if (staffMode) setVehicles([])
      setVehicleId('')
      return
    }
    let cancelled = false
    api.get<Vehicle[]>(`/customers/${customerId}/vehicles`)
      .then((response) => {
        if (!cancelled) {
          setVehicles(response.data)
          setVehicleId('')
        }
      })
      .catch((requestError: unknown) => { if (!cancelled) setError(messageFor(requestError)) })
    return () => { cancelled = true }
  }, [customerId, staffMode])

  useEffect(() => {
    if (!serviceId || !date || !showForm) {
      setAvailability([])
      setStartTime('')
      return
    }
    let cancelled = false
    setAvailability([])
    setStartTime('')
    api.get<Availability>('/bookings/availability', { params: { date, serviceId } })
      .then((response) => { if (!cancelled) setAvailability(response.data.availableSlots) })
      .catch((requestError: unknown) => { if (!cancelled) setError(messageFor(requestError)) })
    return () => { cancelled = true }
  }, [date, serviceId, showForm])

  const normalizedBookingSearch = bookingSearch.trim().toLocaleLowerCase()
  const visibleBookings = bookings.filter((booking) => {
    const matchesStatus = statusFilter === 'ALL' || booking.status === statusFilter
    const matchesQuery = !normalizedBookingSearch || [
      String(booking.id),
      booking.customerName,
      booking.registrationNo,
      booking.serviceName,
      booking.date,
      booking.status,
    ].some((value) => value.toLocaleLowerCase().includes(normalizedBookingSearch))
    return matchesStatus && matchesQuery
  })

  async function createBooking(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const path = staffMode ? '/bookings/walk-in' : '/bookings'
      const payload = staffMode
        ? { customerId: Number(customerId), vehicleId: Number(vehicleId), serviceId: Number(serviceId), date, startTime, notes: notes || null }
        : { vehicleId: Number(vehicleId), serviceId: Number(serviceId), date, startTime, notes: notes || null }
      const response = await api.post<Booking>(path, payload)
      setBookings((current) => [response.data, ...current])
      setShowForm(false)
      setNotes('')
      setNotice(`Booking confirmed for ${response.data.date} at ${response.data.startTime.slice(0, 5)}.`)
      await loadBookings()
    } catch (requestError) {
      setError(messageFor(requestError))
    } finally {
      setSaving(false)
    }
  }

  async function cancelBooking(booking: Booking) {
    setError('')
    setNotice('')
    try {
      const response = await api.post<Booking>(`/bookings/${booking.id}/cancel`)
      setBookings((current) => current.map((item) => item.id === booking.id ? response.data : item))
      setNotice('Booking cancelled. The appointment time is available again.')
    } catch (requestError) {
      setError(messageFor(requestError))
    }
  }

  async function advanceBooking(booking: Booking) {
    const transitions: Partial<Record<Booking['status'], Booking['status']>> = {
      CONFIRMED: 'CHECKED_IN',
      CHECKED_IN: 'IN_PROGRESS',
      IN_PROGRESS: 'COMPLETED',
    }
    const status = transitions[booking.status]
    if (!status) return
    setError('')
    setNotice('')
    try {
      const response = await api.put<Booking>(`/bookings/${booking.id}/status`, { status })
      setBookings((current) => current.map((item) => item.id === booking.id ? response.data : item))
      setNotice(`Booking status updated to ${status.toLowerCase().replace('_', ' ')}.`)
      if (status === 'COMPLETED') await loadInvoices()
    } catch (requestError) {
      setError(messageFor(requestError))
    }
  }

  async function cancelShopBooking(booking: Booking) {
    setError('')
    setNotice('')
    try {
      const response = await api.put<Booking>(`/bookings/${booking.id}/status`, { status: 'CANCELLED' })
      setBookings((current) => current.map((item) => item.id === booking.id ? response.data : item))
      setNotice('Booking cancelled. The appointment time is available again.')
    } catch (requestError) {
      setError(messageFor(requestError))
    }
  }

  async function recordPayment(event: FormEvent<HTMLFormElement>, invoice: Invoice) {
    event.preventDefault()
    setSavingPayment(true)
    setError('')
    setNotice('')
    try {
      const response = await api.post<Invoice>(
        `/invoices/${invoice.id}/payments`,
        { amount: Number(paymentAmount), method: paymentMethod },
        { headers: { 'Idempotency-Key': paymentKey } },
      )
      setInvoices((current) => current.map((item) => item.id === invoice.id ? response.data : item))
      setPaymentInvoiceId(null)
      setPaymentKey('')
      setNotice(response.data.balance === 0 ? 'Invoice paid in full.' : 'Payment recorded against the invoice.')
    } catch (requestError) {
      setError(messageFor(requestError))
    } finally {
      setSavingPayment(false)
    }
  }

  async function redeemLoyaltyPoints(event: FormEvent<HTMLFormElement>, invoice: Invoice) {
    event.preventDefault()
    setSavingPayment(true)
    setError('')
    setNotice('')
    try {
      await api.post(`/loyalty/invoices/${invoice.id}/redeem`, { points: Number(redemptionPoints) })
      setRedemptionInvoiceId(null)
      setRedemptionPoints('')
      await loadInvoices()
      setNotice('Loyalty points applied to the invoice.')
    } catch (requestError) {
      setError(messageFor(requestError))
    } finally {
      setSavingPayment(false)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> BOOKING & SERVICE OPERATIONS</div>
          <h1>{staffMode ? 'The shop’s daily queue.' : 'Your next wash, your way.'}</h1>
          <p>{staffMode ? 'Online bookings and walk-ins, in one shared schedule.' : 'Pick a service, vehicle and available appointment time.'}</p>
        </div>
        <button className="primary-button" onClick={() => { setShowForm((current) => !current); setError(''); setNotice('') }}>
          <CalendarDays size={15} /> {staffMode ? 'Book a walk-in' : 'Book an appointment'}
        </button>
      </div>

      {error && <div className="api-error" role="alert"><span>{error}</span><button onClick={() => { void loadBookings(); void loadInvoices() }}><RefreshCw size={15} /> Try again</button></div>}
      {notice && <div className="success-notice" role="status">{notice}</div>}

      {showForm && (
        <form className="panel booking-form-panel" onSubmit={createBooking}>
          <div className="panel-heading"><div><h2>{staffMode ? 'Create walk-in booking' : 'Book an appointment'}</h2><p>Available times are checked again when the booking is saved.</p></div><button aria-label="Close booking form" className="icon-button" onClick={() => setShowForm(false)} type="button"><X size={16} /></button></div>
          <div className="booking-form-grid">
            {staffMode && <label>Customer<select onChange={(event) => setCustomerId(event.target.value)} required value={customerId}><option value="">Choose a customer</option>{customers.map((customer) => <option key={customer.id} value={customer.id}>{customer.name} · {customer.phone}</option>)}</select></label>}
            <label>Service<select onChange={(event) => setServiceId(event.target.value)} required value={serviceId}><option value="">Choose a service</option>{services.map((service) => <option disabled={!service.active} key={service.id} value={service.id}>{service.name} · {money(service.price)} · {service.estimatedDurationMinutes} min</option>)}</select></label>
            <label>Vehicle<select onChange={(event) => setVehicleId(event.target.value)} required value={vehicleId}><option value="">Choose a vehicle</option>{vehicles.map((vehicle) => <option key={vehicle.id} value={vehicle.id}>{vehicle.registrationNo} · {vehicle.type.toLowerCase()}</option>)}</select></label>
            <label>Date<input min={todayInShop()} onChange={(event) => setDate(event.target.value)} required type="date" value={date} /></label>
            <label>Available appointment<select disabled={availability.length === 0} onChange={(event) => setStartTime(event.target.value)} required value={startTime}><option value="">{availability.length ? 'Choose a time' : 'No times available'}</option>{availability.map((slot) => <option key={slot} value={slot}>{slot.slice(0, 5)}</option>)}</select></label>
            <label>Notes <span className="optional-label">Optional</span><input maxLength={500} onChange={(event) => setNotes(event.target.value)} value={notes} /></label>
          </div>
          {!staffMode && vehicles.length === 0 && <div className="form-hint">Add a vehicle in Customers before booking an appointment.</div>}
          <div className="booking-form-footer"><span><Clock3 size={14} /> Tue–Sun · 9:00 AM–6:00 PM · two vehicles at a time</span><button className="primary-button" disabled={saving || !startTime || !vehicleId} type="submit">{saving ? 'Booking…' : 'Confirm booking'} <ArrowRight size={15} /></button></div>
        </form>
      )}

      <section className="panel booking-list-panel">
        <div className="panel-heading">
          <div><h2>{staffMode ? 'Shop bookings' : 'Your appointments'}</h2><p>{bookings.length} {bookings.length === 1 ? 'booking' : 'bookings'} on record</p></div>
          <div className="booking-list-filters">
            <label className="customer-search booking-search"><Search size={14} /><input aria-label="Search bookings" onChange={(event) => setBookingSearch(event.target.value)} placeholder="Search bookings" value={bookingSearch} /></label>
            <label className="booking-filter">Show<select onChange={(event) => setStatusFilter(event.target.value)} value={statusFilter}><option value="ALL">All statuses</option><option value="CONFIRMED">Confirmed</option><option value="CHECKED_IN">Checked in</option><option value="IN_PROGRESS">In progress</option><option value="COMPLETED">Completed</option><option value="CANCELLED">Cancelled</option></select></label>
          </div>
        </div>
        {loading ? <div className="empty-state">Loading bookings…</div> : visibleBookings.length === 0 ? (
          <div className="crm-empty"><CalendarDays size={25} /><strong>{normalizedBookingSearch ? 'No matching bookings' : 'No bookings yet'}</strong><span>{normalizedBookingSearch ? 'Try another customer, vehicle, service, date or booking ID.' : staffMode ? 'New online bookings and walk-ins will appear here.' : 'Choose an available appointment time to book a wash.'}</span></div>
        ) : (
          <div className="booking-list">
            {visibleBookings.map((booking) => {
              const invoice = invoices.find((item) => item.bookingId === booking.id)
              return (
                <article className="booking-row" key={booking.id}>
                  <div className="booking-date-badge"><span>{new Intl.DateTimeFormat('en', { month: 'short' }).format(new Date(`${booking.date}T12:00:00`))}</span><strong>{booking.date.slice(-2)}</strong></div>
                  <div className="booking-details">
                    <strong>{booking.serviceName} <span>· {money(booking.priceSnapshot)}</span></strong>
                    <div><span>{staffMode ? booking.customerName : ''}</span><span><CarFront size={13} />{booking.registrationNo}</span><span><Clock3 size={13} />{booking.startTime.slice(0, 5)}–{booking.endTime.slice(0, 5)}</span></div>
                    {invoice && (
                      <div className="invoice-summary">
                        <span>{invoice.invoiceNo}</span>
                        <span>{invoice.balance === 0 ? 'Paid' : `${money(invoice.balance)} due`}</span>
                        {invoice.payments.map((payment) => (
                          <span key={payment.id}>{money(payment.amount)} {payment.method.toLowerCase()} · {new Intl.DateTimeFormat('en', { dateStyle: 'medium' }).format(new Date(payment.paidAt))}</span>
                        ))}
                      </div>
                    )}
                  </div>
                  <span className={`booking-status status-${booking.status.toLowerCase().replace('_', '-')}`}>{booking.status.toLowerCase().replace('_', ' ')}</span>
                  <div className="booking-actions">
                    {staffMode && ['CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS'].includes(booking.status) && (
                      <button className="secondary-button" onClick={() => void advanceBooking(booking)}>{booking.status === 'CONFIRMED' ? 'Check in' : booking.status === 'CHECKED_IN' ? 'Start wash' : 'Complete'}</button>
                    )}
                    {staffMode && ['CONFIRMED', 'CHECKED_IN'].includes(booking.status) && (
                      <button aria-label="Cancel booking" className="booking-cancel" onClick={() => void cancelShopBooking(booking)}><X size={15} /> Cancel</button>
                    )}
                    {!staffMode && booking.status === 'CONFIRMED' && new Date(`${booking.date}T${booking.startTime}`) > new Date() && (
                      <button aria-label="Cancel booking" className="booking-cancel" onClick={() => void cancelBooking(booking)}><X size={15} /> Cancel</button>
                    )}
                    {staffMode && invoice && invoice.balance > 0 && paymentInvoiceId !== invoice.id && (
                      <button
                        className="secondary-button"
                        onClick={() => {
                          setPaymentInvoiceId(invoice.id)
                          setPaymentAmount(String(invoice.balance.toFixed(2)))
                          setPaymentMethod('CASH')
                          setPaymentKey(crypto.randomUUID())
                        }}
                        type="button"
                      >
                        Record payment
                      </button>
                    )}
                    {staffMode && invoice && invoice.paidAmount === 0
                        && invoice.customerLoyaltyPoints > 0
                        && invoice.maxRedeemablePoints > 0
                        && redemptionInvoiceId !== invoice.id && (
                          <button
                            className="secondary-button"
                            onClick={() => {
                              setRedemptionInvoiceId(invoice.id)
                              setRedemptionPoints(String(Math.min(invoice.customerLoyaltyPoints, invoice.maxRedeemablePoints)))
                            }}
                            type="button"
                          >
                            Redeem points
                          </button>
                        )}
                  </div>
                  {staffMode && invoice && redemptionInvoiceId === invoice.id && (
                    <form className="payment-entry" onSubmit={(event) => void redeemLoyaltyPoints(event, invoice)}>
                        <strong>{invoice.customerLoyaltyPoints} points · redeem up to {Math.min(invoice.customerLoyaltyPoints, invoice.maxRedeemablePoints)} for this invoice</strong>
                        <label>Points to redeem<input max={Math.min(invoice.customerLoyaltyPoints, invoice.maxRedeemablePoints)} min="1" onChange={(event) => setRedemptionPoints(event.target.value)} required step="1" type="number" value={redemptionPoints} /></label>
                        <button className="primary-button" disabled={savingPayment} type="submit">{savingPayment ? 'Applying…' : 'Apply points'}</button>
                        <button className="icon-button" onClick={() => setRedemptionInvoiceId(null)} type="button">Cancel</button>
                    </form>
                  )}
                  {staffMode && invoice && paymentInvoiceId === invoice.id && (
                    <form className="payment-entry" onSubmit={(event) => void recordPayment(event, invoice)}>
                      <strong>{invoice.invoiceNo} · {money(invoice.balance)} outstanding</strong>
                      <label>Amount<input max={invoice.balance} min="0.01" onChange={(event) => setPaymentAmount(event.target.value)} required step="0.01" type="number" value={paymentAmount} /></label>
                      <label>Method<select onChange={(event) => setPaymentMethod(event.target.value)} value={paymentMethod}><option value="CASH">Cash</option><option value="UPI">UPI</option><option value="CARD">Card</option></select></label>
                      <button className="primary-button" disabled={savingPayment} type="submit">{savingPayment ? 'Recording…' : 'Save payment'}</button>
                      <button className="icon-button" onClick={() => setPaymentInvoiceId(null)} type="button">Cancel</button>
                      {invoice.payments.length > 0 && <span className="payment-history">Previous: {invoice.payments.map((payment) => `${money(payment.amount)} ${payment.method.toLowerCase()} by ${payment.recordedBy}`).join(' · ')}</span>}
                    </form>
                  )}
                </article>
              )
            })}
          </div>
        )}
      </section>
    </>
  )
}
