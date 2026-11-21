import { useCallback, useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { Gift, MessageSquareText, RefreshCw, Sparkles, Star } from 'lucide-react'
import type { AuthUser } from './AuthScreen'
import { api } from './api'

type Loyalty = {
  customerId: number
  balance: number
  transactions: { id: number; pointsDelta: number; type: string; reason: string; createdAt: string }[]
}
type Offer = {
  id: number
  name: string
  description: string | null
  discountType: 'PERCENT' | 'FIXED'
  discountValue: number
  startsAt: string
  endsAt: string
  eligibility: string | null
  active: boolean
}
type Review = {
  id: number
  bookingId: number
  customerName: string
  serviceName: string
  rating: number
  comment: string | null
  createdAt: string
  serviceDate: string
}
type Booking = { id: number; serviceName: string; date: string; status: string }
type Reminder = { id: number; serviceName: string; scheduledAt: string; status: string; channel: string }
type OfferForm = Omit<Offer, 'id' | 'startsAt' | 'endsAt'> & { startsAt: string; endsAt: string }

const blankOffer: OfferForm = {
  name: '',
  description: '',
  discountType: 'PERCENT',
  discountValue: 10,
  startsAt: '',
  endsAt: '',
  eligibility: '',
  active: true,
}

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { detail?: string; message?: string } | undefined
    return data?.detail ?? data?.message ?? (error.response?.status
      ? `Request failed (HTTP ${error.response.status}). Try again.`
      : 'Could not connect to the WashCare API. Check that it is running and try again.')
  }
  return 'Something went wrong. Please try again.'
}

function dateTimeForInput(value: string) {
  return value ? value.slice(0, 16) : ''
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('en-IN', { dateStyle: 'medium' }).format(new Date(value))
}

function offerValue(offer: Offer) {
  if (offer.discountType === 'PERCENT') return `${offer.discountValue}% off`
  return `${new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR' }).format(offer.discountValue)} off`
}

export function RetentionWorkspace({ user }: { user: AuthUser }) {
  const isCustomer = user.role === 'CUSTOMER'
  const isOwner = user.role === 'OWNER'
  const [loyalty, setLoyalty] = useState<Loyalty | null>(null)
  const [offers, setOffers] = useState<Offer[]>([])
  const [reviews, setReviews] = useState<Review[]>([])
  const [bookings, setBookings] = useState<Booking[]>([])
  const [reminders, setReminders] = useState<Reminder[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [editingOfferId, setEditingOfferId] = useState<number | null>(null)
  const [offerForm, setOfferForm] = useState<OfferForm>(blankOffer)
  const [reviewingBookingId, setReviewingBookingId] = useState<number | null>(null)
  const [rating, setRating] = useState(5)
  const [comment, setComment] = useState('')

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const offerRequest = api.get<Offer[]>(isOwner ? '/offers/manage' : '/offers')
      const reviewRequest = api.get<Review[]>(isCustomer ? '/reviews/me' : '/reviews')
      if (isCustomer) {
        const [loyaltyResponse, offerResponse, reviewResponse, bookingResponse, reminderResponse] = await Promise.all([
          api.get<Loyalty>('/loyalty/me'),
          offerRequest,
          reviewRequest,
          api.get<Booking[]>('/bookings/me'),
          api.get<Reminder[]>('/reminders/me'),
        ])
        setLoyalty(loyaltyResponse.data)
        setOffers(offerResponse.data)
        setReviews(reviewResponse.data)
        setBookings(bookingResponse.data)
        setReminders(reminderResponse.data)
      } else {
        const [offerResponse, reviewResponse] = await Promise.all([offerRequest, reviewRequest])
        setOffers(offerResponse.data)
        setReviews(reviewResponse.data)
      }
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setLoading(false)
    }
  }, [isCustomer, isOwner])

  useEffect(() => {
    void loadData()
  }, [loadData])

  function startEditing(offer: Offer) {
    setEditingOfferId(offer.id)
    setOfferForm({
      ...offer,
      startsAt: dateTimeForInput(offer.startsAt),
      endsAt: dateTimeForInput(offer.endsAt),
    })
    setNotice('')
    setError('')
  }

  async function saveOffer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    const payload = {
      ...offerForm,
      description: offerForm.description || null,
      eligibility: offerForm.eligibility || null,
      discountValue: Number(offerForm.discountValue),
    }
    try {
      if (editingOfferId) {
        await api.put(`/offers/${editingOfferId}`, payload)
        setNotice('Offer updated.')
      } else {
        await api.post('/offers', payload)
        setNotice('Offer created.')
      }
      setEditingOfferId(null)
      setOfferForm(blankOffer)
      await loadData()
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  async function submitReview(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!reviewingBookingId) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      await api.post('/reviews', { bookingId: reviewingBookingId, rating, comment: comment || null })
      setReviewingBookingId(null)
      setRating(5)
      setComment('')
      setNotice('Thank you. Your review has been submitted.')
      await loadData()
    } catch (requestError) {
      setError(errorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  const reviewedBookingIds = new Set(reviews.map((review) => review.bookingId))

  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> CUSTOMER RETENTION & FEEDBACK</div>
          <h1>{isCustomer ? 'Good visits are worth remembering.' : 'Build lasting customer relationships.'}</h1>
          <p>{isCustomer ? 'Your rewards, offers, service reminders, and feedback.' : 'Review customer feedback and keep current offers up to date.'}</p>
        </div>
        <button className="secondary-button" onClick={() => void loadData()}><RefreshCw size={14} /> Refresh</button>
      </div>
      {error && <div className="api-error" role="alert"><span>{error}</span><button onClick={() => void loadData()}><RefreshCw size={15} /> Try again</button></div>}
      {notice && <div className="success-notice" role="status">{notice}</div>}

      {isCustomer && (
        <section className="retention-summary-grid">
          <article className="retention-points-card">
            <div className="retention-icon"><Sparkles size={18} /></div>
            <span>YOUR LOYALTY BALANCE</span>
            <strong>{loyalty?.balance ?? 0} points</strong>
            <small>Earn 1 point for every ₹100 paid. Redeem 100 points for ₹10 off.</small>
          </article>
          <article className="retention-reminder-card">
            <div className="retention-icon"><Gift size={18} /></div>
            <span>IN-APP REMINDERS</span>
            <strong>{reminders.filter((reminder) => reminder.status === 'SENT').length} ready</strong>
            <small>We’ll remind you after 90 days without a newer completed visit.</small>
          </article>
        </section>
      )}

      {isOwner && (
        <section className="panel retention-offer-panel">
          <div className="panel-heading">
            <div><h2>{editingOfferId ? 'Edit offer' : 'Create an offer'}</h2><p>Offers are displayed to customers and are not automatically applied to invoices.</p></div>
            {editingOfferId && <button className="secondary-button" onClick={() => { setEditingOfferId(null); setOfferForm(blankOffer) }} type="button">Cancel edit</button>}
          </div>
          <form className="retention-offer-form" onSubmit={(event) => void saveOffer(event)}>
            <label>Offer name<input maxLength={120} onChange={(event) => setOfferForm((current) => ({ ...current, name: event.target.value }))} required value={offerForm.name} /></label>
            <label>Discount type<select onChange={(event) => setOfferForm((current) => ({ ...current, discountType: event.target.value as OfferForm['discountType'] }))} value={offerForm.discountType}><option value="PERCENT">Percentage</option><option value="FIXED">Fixed amount</option></select></label>
            <label>Discount value<input max={offerForm.discountType === 'PERCENT' ? 100 : undefined} min="0.01" onChange={(event) => setOfferForm((current) => ({ ...current, discountValue: Number(event.target.value) }))} required step="0.01" type="number" value={offerForm.discountValue} /></label>
            <label>Starts at<input onChange={(event) => setOfferForm((current) => ({ ...current, startsAt: event.target.value }))} required type="datetime-local" value={offerForm.startsAt} /></label>
            <label>Ends at<input onChange={(event) => setOfferForm((current) => ({ ...current, endsAt: event.target.value }))} required type="datetime-local" value={offerForm.endsAt} /></label>
            <label>Eligibility <span className="optional-label">Optional</span><input maxLength={500} onChange={(event) => setOfferForm((current) => ({ ...current, eligibility: event.target.value }))} value={offerForm.eligibility ?? ''} /></label>
            <label className="retention-offer-wide">Description <span className="optional-label">Optional</span><input maxLength={500} onChange={(event) => setOfferForm((current) => ({ ...current, description: event.target.value }))} value={offerForm.description ?? ''} /></label>
            <label className="service-active-toggle"><input checked={offerForm.active} onChange={(event) => setOfferForm((current) => ({ ...current, active: event.target.checked }))} type="checkbox" /> Active</label>
            <button className="primary-button" disabled={saving} type="submit">{saving ? 'Saving…' : editingOfferId ? 'Save changes' : 'Create offer'}</button>
          </form>
        </section>
      )}

      <section className="panel retention-panel">
        <div className="panel-heading"><div><h2><Gift size={16} /> {isOwner ? 'Offer management' : 'Current offers'}</h2><p>{isOwner ? 'Offers are visible to customers only while active and within their date range.' : 'These offers are for information and are not automatically applied to invoices.'}</p></div></div>
        {loading ? <div className="empty-state">Loading offers…</div> : offers.length === 0 ? (
          <div className="crm-empty"><Gift size={24} /><strong>No offers to show</strong><span>{isOwner ? 'Create an offer above to share it with customers.' : 'Check back later for a shop offer.'}</span></div>
        ) : (
          <div className="retention-offer-list">
            {offers.map((offer) => (
              <article className="retention-offer-card" key={offer.id}>
                <span className="retention-offer-discount">{offerValue(offer)}</span>
                <div><strong>{offer.name}</strong><p>{offer.description || 'A WashCare offer for our customers.'}</p>
                  <small>{formatDate(offer.startsAt)} – {formatDate(offer.endsAt)}{offer.eligibility ? ` · ${offer.eligibility}` : ''}</small>
                  {isOwner && <small>{offer.active ? 'Active' : 'Paused'} · {new Date(offer.endsAt) < new Date() ? 'Expired' : 'Date range'}</small>}
                </div>
                {isOwner && <button className="secondary-button" onClick={() => startEditing(offer)} type="button">Edit</button>}
              </article>
            ))}
          </div>
        )}
      </section>

      {isCustomer && (
        <>
          <section className="panel retention-panel">
            <div className="panel-heading"><div><h2><Gift size={16} /> Points activity</h2><p>Points are awarded after an invoice is fully paid.</p></div></div>
            {loyalty?.transactions.length ? (
              <div className="retention-activity-list">{loyalty.transactions.map((item) => (
                <article key={item.id}><span className={`retention-points-delta ${item.pointsDelta < 0 ? 'points-spent' : ''}`}>{item.pointsDelta > 0 ? '+' : ''}{item.pointsDelta}</span><div><strong>{item.type === 'EARN' ? 'Points earned' : item.type === 'REDEEM' ? 'Points redeemed' : 'Points adjusted'}</strong><small>{item.reason}</small></div><time>{formatDate(item.createdAt)}</time></article>
              ))}</div>
            ) : <div className="crm-empty"><Sparkles size={24} /><strong>No points activity yet</strong><span>Points will appear after an eligible invoice is paid in full.</span></div>}
          </section>
          <section className="panel retention-panel">
            <div className="panel-heading"><div><h2><RefreshCw size={16} /> Return-visit reminders</h2><p>In-app only; generated 90 days after your latest completed service if you have not returned.</p></div></div>
            {reminders.length ? <div className="retention-activity-list">{reminders.map((reminder) => (
              <article key={reminder.id}><span className="retention-reminder-icon"><RefreshCw size={15} /></span><div><strong>Time for another {reminder.serviceName.toLowerCase()}?</strong><small>{reminder.status === 'SENT' ? 'Available in your WashCare account' : 'Scheduled reminder'}</small></div><time>{formatDate(reminder.scheduledAt)}</time></article>
            ))}</div> : <div className="crm-empty"><RefreshCw size={24} /><strong>No reminders right now</strong><span>We’ll check again after a completed service.</span></div>}
          </section>
          <section className="panel retention-panel">
            <div className="panel-heading"><div><h2><MessageSquareText size={16} /> Your service reviews</h2><p>Share feedback about a completed visit. One review is allowed per booking.</p></div></div>
            <div className="retention-review-list">
              {bookings.filter((booking) => booking.status === 'COMPLETED').map((booking) => {
                const review = reviews.find((item) => item.bookingId === booking.id)
                return <article className="retention-review-row" key={booking.id}>
                  <div><strong>{booking.serviceName}</strong><small>Visit on {formatDate(booking.date)}</small></div>
                  {review ? <div className="retention-stars" aria-label={`${review.rating} out of 5 stars`}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}{review.comment && <small>{review.comment}</small>}</div>
                    : reviewingBookingId === booking.id ? (
                      <form className="retention-review-form" onSubmit={(event) => void submitReview(event)}>
                        <label>Rating<select onChange={(event) => setRating(Number(event.target.value))} value={rating}><option value={5}>5 — Excellent</option><option value={4}>4 — Good</option><option value={3}>3 — Average</option><option value={2}>2 — Needs improvement</option><option value={1}>1 — Poor</option></select></label>
                        <label>Comment <span className="optional-label">Optional</span><input maxLength={1000} onChange={(event) => setComment(event.target.value)} value={comment} /></label>
                        <button className="primary-button" disabled={saving} type="submit">{saving ? 'Sending…' : 'Submit review'}</button>
                        <button className="secondary-button" onClick={() => setReviewingBookingId(null)} type="button">Cancel</button>
                      </form>
                    ) : <button className="secondary-button" onClick={() => { setReviewingBookingId(booking.id); setRating(5); setComment('') }} type="button"><Star size={14} /> Leave a review</button>}
                </article>
              })}
              {bookings.filter((booking) => booking.status === 'COMPLETED').length === 0 && <div className="crm-empty"><MessageSquareText size={24} /><strong>No completed services yet</strong><span>You can review a service after your visit is completed.</span></div>}
              {bookings.filter((booking) => booking.status === 'COMPLETED').length > 0 && bookings.filter((booking) => booking.status === 'COMPLETED').every((booking) => reviewedBookingIds.has(booking.id)) && <div className="retention-review-thanks">Thanks for sharing feedback about all your completed visits.</div>}
            </div>
          </section>
        </>
      )}

      {!isCustomer && (
        <section className="panel retention-panel">
          <div className="panel-heading"><div><h2><MessageSquareText size={16} /> Customer feedback</h2><p>Reviews customers submitted for completed services.</p></div></div>
          {loading ? <div className="empty-state">Loading reviews…</div> : reviews.length === 0 ? (
            <div className="crm-empty"><MessageSquareText size={24} /><strong>No reviews yet</strong><span>Customer feedback will appear here after a completed visit.</span></div>
          ) : <div className="retention-review-list">{reviews.map((review) => (
            <article className="retention-review-row" key={review.id}>
              <div><strong>{review.customerName} · {review.serviceName}</strong><small>Visit {formatDate(review.serviceDate)} · Review {formatDate(review.createdAt)}</small></div>
              <div className="retention-stars" aria-label={`${review.rating} out of 5 stars`}>{'★'.repeat(review.rating)}{'☆'.repeat(5 - review.rating)}{review.comment && <small>{review.comment}</small>}</div>
            </article>
          ))}</div>}
        </section>
      )}
    </>
  )
}
