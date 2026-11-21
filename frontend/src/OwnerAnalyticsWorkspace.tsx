import { useEffect, useState } from 'react'
import axios from 'axios'
import { Activity, CalendarDays, CarFront, RefreshCw, Star, UsersRound, Wallet } from 'lucide-react'
import { api } from './api'

type Analytics = {
  periodDays: number
  startDate: string
  endDate: string
  paidRevenue: number
  todayPaidRevenue: number
  bookingCount: number
  todayBookings: number
  customerProfiles: number
  completedServices: number
  uniqueCustomers: number
  repeatCustomers: number
  repeatCustomerRate: number
  reviewCount: number
  averageRating: number
  dailyRevenue: { date: string; revenue: number }[]
  popularServices: { serviceName: string; completedCount: number }[]
}

function errorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data as { detail?: string; message?: string } | undefined
    return data?.detail ?? data?.message ?? (error.response?.status
      ? `Analytics request failed (HTTP ${error.response.status}).`
      : 'Could not connect to the analytics service. Check that the API is running.')
  }
  return 'Something went wrong loading reports.'
}

function money(value: number) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(value)
}

function shortDate(value: string) {
  return new Intl.DateTimeFormat('en-IN', { day: 'numeric', month: 'short' })
    .format(new Date(`${value}T12:00:00`))
}

export function OwnerAnalyticsWorkspace() {
  const [periodDays, setPeriodDays] = useState(30)
  const [report, setReport] = useState<Analytics | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    api.get<Analytics>('/analytics/owner', { params: { days: periodDays } })
      .then((response) => { if (!cancelled) setReport(response.data) })
      .catch((requestError: unknown) => { if (!cancelled) setError(errorMessage(requestError)) })
      .finally(() => { if (!cancelled) setLoading(false) })
    return () => { cancelled = true }
  }, [periodDays, refreshKey])

  const maxRevenue = Math.max(1, ...(report?.dailyRevenue.map((point) => point.revenue) ?? []))
  const chartPoints = report?.dailyRevenue ?? []
  const chartPolyline = chartPoints.map((point, index) => {
    const x = chartPoints.length <= 1 ? 50 : index * 100 / (chartPoints.length - 1)
    const y = 92 - point.revenue / maxRevenue * 82
    return `${x},${y}`
  }).join(' ')
  const maxServiceCount = Math.max(1, ...(report?.popularServices.map((service) => service.completedCount) ?? []))

  return (
    <>
      <div className="page-heading analytics-page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> OWNER BUSINESS INTELLIGENCE</div>
          <h1>Know how the shop is doing.</h1>
          <p>Revenue, completed visits, repeat customers, and customer feedback in one view.</p>
        </div>
        <label className="analytics-period">Period
          <select onChange={(event) => setPeriodDays(Number(event.target.value))} value={periodDays}>
            <option value={30}>Last 30 days</option>
            <option value={90}>Last 90 days</option>
            <option value={365}>Last 12 months</option>
          </select>
        </label>
      </div>
      {error && <div className="api-error" role="alert"><span>{error}</span><button onClick={() => setRefreshKey((current) => current + 1)}><RefreshCw size={15} /> Retry</button></div>}
      {loading && !report ? <div className="panel analytics-loading">Loading shop analytics…</div> : report && (
        <>
          <p className="analytics-range">{shortDate(report.startDate)} – {shortDate(report.endDate)} · figures reflect the selected period</p>
          <section className="analytics-metrics-grid">
            <article className="analytics-metric"><span className="analytics-metric-icon metric-mint"><Wallet size={16} /></span><small>PAID REVENUE</small><strong>{money(report.paidRevenue)}</strong><span>Payments received</span></article>
            <article className="analytics-metric"><span className="analytics-metric-icon metric-blue"><CalendarDays size={16} /></span><small>COMPLETED VISITS</small><strong>{report.completedServices}</strong><span>of {report.bookingCount} bookings</span></article>
            <article className="analytics-metric"><span className="analytics-metric-icon metric-lavender"><UsersRound size={16} /></span><small>REPEAT CUSTOMERS</small><strong>{report.repeatCustomerRate.toFixed(0)}%</strong><span>{report.repeatCustomers} of {report.uniqueCustomers} completed-service customers</span></article>
            <article className="analytics-metric"><span className="analytics-metric-icon metric-peach"><Star size={16} /></span><small>AVERAGE RATING</small><strong>{report.reviewCount ? report.averageRating.toFixed(1) : '—'}<span className="analytics-out-of-five">{report.reviewCount ? ' / 5' : ''}</span></strong><span>{report.reviewCount} customer reviews</span></article>
          </section>
          <section className="analytics-content-grid">
            <article className="panel analytics-chart-panel">
              <div className="panel-heading"><div><h2><Activity size={15} /> Revenue trend</h2><p>Recorded payments by day</p></div><strong className="analytics-total">{money(report.paidRevenue)}</strong></div>
              {chartPoints.length === 0 ? <div className="analytics-empty">No paid invoices in this period yet.</div> : (
                <>
                  <div className="revenue-chart" role="img" aria-label={`Daily paid revenue over the last ${periodDays} days`}>
                    <div className="revenue-axis"><span>{money(maxRevenue)}</span><span>{money(maxRevenue / 2)}</span><span>₹0</span></div>
                    <svg preserveAspectRatio="none" viewBox="0 0 100 100">
                      <line x1="0" x2="100" y1="10" y2="10" />
                      <line x1="0" x2="100" y1="51" y2="51" />
                      <line x1="0" x2="100" y1="92" y2="92" />
                      {chartPoints.length > 1 && <polyline points={chartPolyline} />}
                      {chartPoints.map((point, index) => {
                        const x = chartPoints.length <= 1 ? 50 : index * 100 / (chartPoints.length - 1)
                        const y = 92 - point.revenue / maxRevenue * 82
                        return <circle key={point.date} cx={x} cy={y} r="1.5"><title>{shortDate(point.date)}: {money(point.revenue)}</title></circle>
                      })}
                    </svg>
                  </div>
                  <div className="revenue-chart-labels"><span>{shortDate(chartPoints[0].date)}</span><span>{shortDate(chartPoints[Math.floor((chartPoints.length - 1) / 2)].date)}</span><span>{shortDate(chartPoints[chartPoints.length - 1].date)}</span></div>
                  <div className="revenue-daily-list">
                    {chartPoints.slice(-5).reverse().map((point) => <div key={point.date}><span>{shortDate(point.date)}</span><strong>{money(point.revenue)}</strong></div>)}
                  </div>
                </>
              )}
            </article>
            <article className="panel analytics-services-panel">
              <div className="panel-heading"><div><h2><CarFront size={15} /> Popular services</h2><p>Completed visits by service</p></div></div>
              {report.popularServices.length === 0 ? <div className="analytics-empty">No completed services in this period yet.</div> : (
                <div className="popular-service-list">
                  {report.popularServices.map((service, index) => (
                    <div className="popular-service-row" key={service.serviceName}>
                      <div className="popular-service-title"><span>{index + 1}</span><strong>{service.serviceName}</strong><small>{service.completedCount}</small></div>
                      <div className="popular-service-track"><i style={{ width: `${service.completedCount / maxServiceCount * 100}%` }} /></div>
                    </div>
                  ))}
                </div>
              )}
              <div className="analytics-footnote">{report.uniqueCustomers} unique customers with completed visits during this period.</div>
            </article>
          </section>
        </>
      )}
    </>
  )
}
