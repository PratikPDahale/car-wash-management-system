import { useCallback, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import axios from 'axios'
import {
  ArrowDownRight,
  ArrowRight,
  CalendarDays,
  CarFront,
  Check,
  ChevronDown,
  CircleHelp,
  Clock3,
  LayoutDashboard,
  LogOut,
  Menu,
  MessageSquareText,
  RefreshCw,
  Search,
  Settings2,
  ShieldCheck,
  Sparkles,
  UsersRound,
  Wallet,
  Waves,
  X,
} from 'lucide-react'
import { AuthScreen, type AuthUser } from './AuthScreen'
import { CustomerWorkspace } from './CustomerWorkspace'
import { BookingWorkspace } from './BookingWorkspace'
import { ServiceCatalogWorkspace } from './ServiceCatalogWorkspace'
import { RetentionWorkspace } from './RetentionWorkspace'
import { OwnerAnalyticsWorkspace } from './OwnerAnalyticsWorkspace'
import { api, getAccessToken, setAccessToken } from './api'
import { GlobalSearchDialog, type SearchRequest, type SearchSection } from './GlobalSearchDialog'
import { HelpSupportWorkspace } from './HelpSupportWorkspace'
import { NotificationBell } from './NotificationBell'

type Service = {
  id: number
  name: string
  description: string
  price: number
  estimatedDurationMinutes: number
  active: boolean
}

type Section = 'Overview' | 'Bookings' | 'Customers' | 'Services' | 'Retention' | 'Reports' | 'Settings' | 'Help'

const primaryNavigation: { label: Section; icon: typeof LayoutDashboard }[] = [
  { label: 'Overview', icon: LayoutDashboard },
  { label: 'Bookings', icon: CalendarDays },
  { label: 'Customers', icon: UsersRound },
  { label: 'Services', icon: CarFront },
  { label: 'Retention', icon: Sparkles },
  { label: 'Reports', icon: ArrowDownRight },
]

const roadmap = [
  { label: 'Customer and vehicle records', status: 'Complete' },
  { label: 'Bookings and the daily queue', status: 'Complete' },
  { label: 'Invoices and in-shop payments', status: 'Complete' },
  { label: 'Loyalty, offers, reminders and reviews', status: 'Complete' },
  { label: 'Owner analytics and deployment', status: 'Next up' },
]

const SESSION_USER_KEY = 'washcare.user'

function readStoredUser(): AuthUser | null {
  const stored = sessionStorage.getItem(SESSION_USER_KEY)
  if (!stored) return null
  try {
    const user = JSON.parse(stored) as AuthUser
    if (typeof user.id === 'number'
      && typeof user.name === 'string'
      && typeof user.email === 'string'
      && ['OWNER', 'STAFF', 'CUSTOMER'].includes(user.role)) {
      return user
    }
  } catch {
    sessionStorage.removeItem(SESSION_USER_KEY)
  }
  return null
}

function currency(value: number) {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency: 'INR',
    maximumFractionDigits: 0,
  }).format(value)
}

function App() {
  const [section, setSection] = useState<Section>('Overview')
  const [user, setUser] = useState<AuthUser | null>(() => readStoredUser())
  const [authReady, setAuthReady] = useState(false)
  const [services, setServices] = useState<Service[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [mobileNavigationOpen, setMobileNavigationOpen] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const [searchRequest, setSearchRequest] = useState<SearchRequest | null>(null)
  const searchShortcutLabel = /Mac|iPhone|iPad/.test(navigator.userAgent) ? '⌘ K' : 'Ctrl K'

  const loadServices = useCallback(async () => {
    setLoading(true)
    setError('')

    try {
      const endpoint = user?.role === 'OWNER' ? '/services/manage' : '/services'
      const response = await api.get<Service[]>(endpoint)
      setServices(response.data)
    } catch {
      setError('Could not reach the WashCare API. Start the backend and try again.')
    } finally {
      setLoading(false)
    }
  }, [user?.role])

  useEffect(() => {
    void loadServices()
  }, [loadServices])

  useEffect(() => {
    if (!getAccessToken()) {
      sessionStorage.removeItem(SESSION_USER_KEY)
      setUser(null)
      setAuthReady(true)
      return
    }

    api.get<AuthUser>('/auth/me')
      .then((response) => {
        setUser(response.data)
        sessionStorage.setItem(SESSION_USER_KEY, JSON.stringify(response.data))
      })
      .catch((requestError: unknown) => {
        if (axios.isAxiosError(requestError) && [401, 403].includes(requestError.response?.status ?? 0)) {
          setAccessToken(null)
          sessionStorage.removeItem(SESSION_USER_KEY)
          setUser(null)
        }
      })
      .finally(() => setAuthReady(true))
  }, [])

  const activeServices = useMemo(() => services.filter((service) => service.active), [services])
  const averagePrice = useMemo(() => {
    if (activeServices.length === 0) return 0
    return activeServices.reduce((total, service) => total + service.price, 0) / activeServices.length
  }, [activeServices])

  function navigateTo(nextSection: Section) {
    setSection(nextSection)
    setMobileNavigationOpen(false)
  }

  function selectSearchResult(nextSection: SearchSection, query: string) {
    setSearchRequest((current) => ({ query, section: nextSection, key: (current?.key ?? 0) + 1 }))
    navigateTo(nextSection)
    setSearchOpen(false)
  }

  useEffect(() => {
    function handleSearchShortcut(event: KeyboardEvent) {
      if ((event.metaKey || event.ctrlKey) && event.key.toLocaleLowerCase() === 'k') {
        event.preventDefault()
        setSearchOpen((open) => !open)
      } else if (event.key === 'Escape') {
        setSearchOpen(false)
      }
    }

    window.addEventListener('keydown', handleSearchShortcut)
    return () => window.removeEventListener('keydown', handleSearchShortcut)
  }, [])

  function authenticate(token: string, authenticatedUser: AuthUser) {
    setAccessToken(token)
    sessionStorage.setItem(SESSION_USER_KEY, JSON.stringify(authenticatedUser))
    setUser(authenticatedUser)
    setSection(authenticatedUser.role === 'CUSTOMER' ? 'Services' : 'Overview')
  }

  function signOut() {
    setAccessToken(null)
    sessionStorage.removeItem(SESSION_USER_KEY)
    setUser(null)
    setSection('Overview')
  }

  const navigation = user?.role === 'CUSTOMER'
    ? primaryNavigation.filter(({ label }) => ['Overview', 'Bookings', 'Customers', 'Services', 'Retention'].includes(label))
    : user?.role === 'STAFF'
      ? primaryNavigation.filter(({ label }) => label !== 'Reports')
      : primaryNavigation

  if (!authReady) {
    return <main className="auth-page"><div className="loading-state">Restoring your WashCare session…</div></main>
  }

  if (!user) {
    return <AuthScreen onAuthenticated={({ token, user: authenticatedUser }) => authenticate(token, authenticatedUser)} />
  }

  return (
    <div className="app-shell">
      {mobileNavigationOpen && (
        <button
          aria-label="Close navigation"
          className="mobile-scrim"
          onClick={() => setMobileNavigationOpen(false)}
        />
      )}

      <aside className={`sidebar ${mobileNavigationOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <div className="brand-mark"><Waves size={21} strokeWidth={2.4} /></div>
          <div>
            <strong>washcare</strong>
            <span>SHOP WORKSPACE</span>
          </div>
          <button
            aria-label="Close navigation"
            className="icon-button sidebar-close"
            onClick={() => setMobileNavigationOpen(false)}
          >
            <X size={18} />
          </button>
        </div>

        <div className="shop-switcher">
          <div className="shop-avatar">S</div>
          <span className="shop-name"><strong>Your car wash</strong><small>Single-shop account</small></span>
          <ChevronDown size={16} />
        </div>

        <div className="nav-caption">WORKSPACE</div>
        <nav className="main-navigation" aria-label="Main navigation">
          {navigation.map(({ label, icon: Icon }) => (
            <button
              aria-current={section === label ? 'page' : undefined}
              className={`nav-item ${section === label ? 'nav-item-active' : ''}`}
              key={label}
              onClick={() => navigateTo(label)}
            >
              <Icon size={18} strokeWidth={1.9} />
              <span>{label}</span>
              {label === 'Bookings' && <span className="nav-count">0</span>}
            </button>
          ))}
        </nav>

        <div className="nav-caption tools-caption">PREFERENCES</div>
        <nav className="main-navigation" aria-label="Preferences">
          {user.role !== 'CUSTOMER' && (
            <button className="nav-item" onClick={() => navigateTo('Settings')}>
              <Settings2 size={18} strokeWidth={1.9} /><span>Shop settings</span>
            </button>
          )}
          {user.role === 'CUSTOMER' && (
            <button aria-current={section === 'Help' ? 'page' : undefined} className={`nav-item ${section === 'Help' ? 'nav-item-active' : ''}`} onClick={() => navigateTo('Help')}>
              <CircleHelp size={18} strokeWidth={1.9} /><span>Help & support</span>
            </button>
          )}
        </nav>

        <div className="sidebar-spacer" />
        <div className="sidebar-note">
          <div className="note-icon"><ShieldCheck size={16} /></div>
          <strong>Your shop, in sync</strong>
          <p>Bookings, people and services—together in one place.</p>
          <button onClick={() => navigateTo('Services')}>Explore services <ArrowRight size={14} /></button>
        </div>
        <div className="profile-button">
          <div className="profile-avatar">{user.name.slice(0, 1).toUpperCase()}</div>
          <span><strong>{user.name}</strong><small>{user.role.toLowerCase()}</small></span>
          <button aria-label="Sign out" className="profile-logout" onClick={signOut}><LogOut size={16} /></button>
        </div>
      </aside>

      <main className="main-area">
        <header className="topbar">
          <button
            aria-label="Open navigation"
            className="icon-button mobile-menu"
            onClick={() => setMobileNavigationOpen(true)}
          >
            <Menu size={20} />
          </button>
          <div className="breadcrumbs"><span>Workspace</span><span className="breadcrumb-divider">/</span><strong>{section}</strong></div>
          <div className="topbar-actions">
            <button aria-label="Search anything" className="search-button" onClick={() => setSearchOpen(true)} type="button"><Search size={17} /><span>Search anything</span><kbd>{searchShortcutLabel}</kbd></button>
            <NotificationBell onNavigate={navigateTo} user={user} />
            <div className="topbar-divider" />
            <span className="today-label">{new Intl.DateTimeFormat('en-IN', { weekday: 'short', day: 'numeric', month: 'short' }).format(new Date())}</span>
          </div>
        </header>

        <div className="page-content">
          {section === 'Customers' ? (
            <CustomerWorkspace user={user} searchRequest={searchRequest?.section === 'Customers' ? searchRequest : null} />
          ) : section === 'Bookings' ? (
            <BookingWorkspace user={user} searchRequest={searchRequest?.section === 'Bookings' ? searchRequest : null} />
          ) : section === 'Retention' ? (
            <RetentionWorkspace user={user} />
          ) : section === 'Help' && user.role === 'CUSTOMER' ? (
            <HelpSupportWorkspace />
          ) : section === 'Reports' && user.role === 'OWNER' ? (
            <OwnerAnalyticsWorkspace />
          ) : section === 'Services' ? user.role === 'OWNER' ? (
            <ServiceCatalogWorkspace
              services={services}
              loading={loading}
              onRefresh={() => void loadServices()}
              searchRequest={searchRequest?.section === 'Services' ? searchRequest : null}
            />
          ) : (
            <Dashboard
              activeServices={activeServices}
              averagePrice={averagePrice}
              error={error}
              loading={loading}
              onNavigate={navigateTo}
              onRefresh={() => void loadServices()}
              section="Services"
              services={services}
              user={user}
              searchRequest={searchRequest?.section === 'Services' ? searchRequest : null}
            />
          ) : section === 'Overview' ? (
            <Dashboard
              activeServices={activeServices}
              averagePrice={averagePrice}
              error={error}
              loading={loading}
              onNavigate={navigateTo}
              onRefresh={() => void loadServices()}
              section="Overview"
              services={services}
              user={user}
              searchRequest={searchRequest?.section === 'Services' ? searchRequest : null}
            />
          ) : (
            <ComingSoon section={section} onNavigate={navigateTo} />
          )}
          <footer className="page-footer">
            <span>© {new Date().getFullYear()} WashCare CRM</span>
            <span className={`footer-status ${error ? 'footer-status-error' : ''}`}><span /> {error ? 'API not connected' : loading ? 'Connecting to API…' : 'API connected'}</span>
            <span>Made for the people who keep us moving.</span>
          </footer>
        </div>
      </main>
      {searchOpen && <GlobalSearchDialog onClose={() => setSearchOpen(false)} onSelect={selectSearchResult} />}
    </div>
  )
}

function Dashboard({
  activeServices,
  averagePrice,
  error,
  loading,
  onNavigate,
  onRefresh,
  section,
  services,
  user,
  searchRequest,
}: {
  activeServices: Service[]
  averagePrice: number
  error: string
  loading: boolean
  onNavigate: (section: Section) => void
  onRefresh: () => void
  section: 'Overview' | 'Services'
  services: Service[]
  user: AuthUser
  searchRequest: SearchRequest | null
}) {
  const title = section === 'Overview' ? 'A better day starts here.' : 'Your service menu.'
  const description = section === 'Overview'
    ? 'Your shop, your customers, your next great day.'
    : 'Keep your wash packages and pricing clear and up to date.'
  const [quickMetrics, setQuickMetrics] = useState<{
    todayPaidRevenue: number
    todayBookings: number
    customerProfiles: number
  } | null>(null)
  const [quickMetricsError, setQuickMetricsError] = useState('')
  const [serviceSearch, setServiceSearch] = useState('')

  useEffect(() => {
    if (searchRequest) setServiceSearch(searchRequest.query)
  }, [searchRequest])

  const visibleServices = services.filter((service) => (
    `${service.name} ${service.description}`.toLocaleLowerCase().includes(serviceSearch.trim().toLocaleLowerCase())
  ))
  const visibleActiveServices = activeServices.filter((service) => (
    `${service.name} ${service.description}`.toLocaleLowerCase().includes(serviceSearch.trim().toLocaleLowerCase())
  ))

  useEffect(() => {
    if (user.role !== 'OWNER' || section !== 'Overview') return
    api.get<{ todayPaidRevenue: number; todayBookings: number; customerProfiles: number }>('/analytics/owner', {
      params: { days: 30 },
    }).then((response) => {
      setQuickMetrics(response.data)
      setQuickMetricsError('')
    }).catch(() => {
      setQuickMetricsError('Daily shop metrics are temporarily unavailable. Open Reports to retry.')
    })
  }, [section, user.role])

  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> YOUR SHOP AT A GLANCE</div>
          <h1>{title}</h1>
          <p>{description}</p>
        </div>
        <div className="date-filter"><CalendarDays size={16} /> Today <ChevronDown size={15} /></div>
      </div>

      {error && (
        <div className="api-error" role="alert">
          <span>{error}</span>
          <button onClick={onRefresh}><RefreshCw size={15} /> Try again</button>
        </div>
      )}

      {user.role === 'OWNER' && section === 'Overview' ? (
        <>
          <section aria-label="Today's shop metrics" className="metrics-grid">
            <MetricCard icon={<CarFront size={18} />} label="Active services" value={loading ? '—' : String(activeServices.length)} footnote="Available to your customers" accent="mint" />
            <MetricCard icon={<Wallet size={18} />} label="Paid today" value={quickMetrics ? currency(quickMetrics.todayPaidRevenue) : '—'} footnote="Payments received today" accent="peach" />
            <MetricCard icon={<CalendarDays size={18} />} label="Bookings today" value={quickMetrics ? String(quickMetrics.todayBookings) : '—'} footnote="Appointments and walk-ins" accent="lavender" />
            <MetricCard icon={<UsersRound size={18} />} label="Customer profiles" value={quickMetrics ? String(quickMetrics.customerProfiles) : '—'} footnote="Profiles on record" accent="blue" />
          </section>
          {quickMetricsError && <div className="analytics-inline-error" role="status">{quickMetricsError}</div>}
        </>
      ) : (
        <section aria-label="Service overview" className="metrics-grid">
          <MetricCard icon={<CarFront size={18} />} label="Active services" value={loading ? '—' : String(activeServices.length)} footnote="Available to your customers" accent="mint" />
          <MetricCard icon={<Wallet size={18} />} label="Average service price" value={loading ? '—' : currency(averagePrice)} footnote="Across active packages" accent="peach" />
          <MetricCard icon={<Clock3 size={18} />} label="Bookings today" value="—" footnote="Shop operations dashboard" accent="lavender" />
          <MetricCard icon={<UsersRound size={18} />} label="Customer profiles" value="—" footnote="Customer overview" accent="blue" />
        </section>
      )}

      <section className="welcome-card">
        <div className="welcome-copy">
          <div className="welcome-tag"><Sparkles size={13} /> A LITTLE MORE CARE, EVERY DAY</div>
          <h2>Great service brings them back.<br /><span>Great relationships bring them home.</span></h2>
          <p>WashCare connects every visit to the people and vehicles behind it. Your shop's service menu is ready to go.</p>
          <button className="primary-button" onClick={() => onNavigate('Services')}>
            Explore your services <ArrowRight size={16} />
          </button>
        </div>
        <div className="welcome-art" aria-hidden="true">
          <div className="sun-orb" />
          <div className="art-sparkle sparkle-one">✳</div>
          <div className="art-sparkle sparkle-two">✦</div>
          <div className="car-illustration">
            <div className="car-window" />
            <div className="car-body" />
            <div className="car-wheel wheel-one" />
            <div className="car-wheel wheel-two" />
            <div className="car-shine" />
          </div>
          <div className="art-wave wave-one" />
          <div className="art-wave wave-two" />
        </div>
      </section>

      <div className="content-grid">
        <section className="panel services-panel">
          <div className="panel-heading">
            <div><h2>Service menu</h2><p>Your active wash packages</p></div>
            <button className="text-button" onClick={() => onNavigate('Services')}>View all <ArrowRight size={15} /></button>
          </div>
          {loading ? (
            <div className="empty-state">Loading your service menu…</div>
          ) : visibleActiveServices.length === 0 ? (
            <div className="empty-state">{serviceSearch ? 'No matching active services.' : error ? 'The service menu is unavailable until the API connects.' : 'No active services yet.'}</div>
          ) : (
            <div className="service-list">
              {visibleActiveServices.slice(0, 4).map((service, index) => (
                <div className="service-row" key={service.id}>
                  <div className={`service-icon service-icon-${index % 4}`}><CarFront size={18} /></div>
                  <div className="service-info"><strong>{service.name}</strong><span>{service.estimatedDurationMinutes} min · {service.description}</span></div>
                  <strong className="service-price">{currency(service.price)}</strong>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="panel roadmap-panel">
          <div className="panel-heading">
            <div><h2>What's taking shape</h2><p>Your CRM, built around the way you work</p></div>
            <div className="roadmap-badge">FOUNDATION</div>
          </div>
          <div className="roadmap-list">
            {roadmap.map((item) => (
              <div className="roadmap-row" key={item.label}>
                <div className={`roadmap-check ${item.status === 'Next up' ? 'roadmap-next' : ''}`}>{item.status === 'Next up' ? <ArrowRight size={13} /> : <Check size={13} />}</div>
                <span>{item.label}</span>
                <small className={item.status === 'Next up' ? 'roadmap-next-label' : ''}>{item.status}</small>
              </div>
            ))}
          </div>
          <div className="roadmap-note"><MessageSquareText size={15} /> Built in phases so every part works together.</div>
        </section>
      </div>

      {section === 'Services' && (
        <section aria-label="All services" className="panel all-services-panel">
          <div className="panel-heading">
            <div><h2>Available services</h2><p>{services.length} published packages</p></div>
            <div className="customer-actions">
              <label className="customer-search catalog-search"><Search size={14} /><input aria-label="Search services" onChange={(event) => setServiceSearch(event.target.value)} placeholder="Search services" value={serviceSearch} /></label>
              <button className="icon-button refresh-button" aria-label="Refresh services" onClick={onRefresh}><RefreshCw size={16} /></button>
            </div>
          </div>
          <ServiceTable loading={loading} services={visibleServices} />
        </section>
      )}

      <div className="bottom-note"><ShieldCheck size={16} /><span>Your customer data stays yours. Role-based access is part of the foundation.</span><a href="#security" onClick={(event) => event.preventDefault()}>Learn about security <ArrowRight size={13} /></a></div>
    </>
  )
}

function MetricCard({
  icon,
  label,
  value,
  footnote,
  accent,
}: {
  icon: ReactNode
  label: string
  value: string
  footnote: string
  accent: string
}) {
  return (
    <article className="metric-card">
      <div className="metric-topline"><span className={`metric-icon metric-${accent}`}>{icon}</span></div>
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      <div className="metric-footnote">{footnote}</div>
    </article>
  )
}

function ServiceTable({ loading, services }: { loading: boolean; services: Service[] }) {
  if (loading) return <div className="empty-state">Loading services…</div>
  if (services.length === 0) return <div className="empty-state">No services are available.</div>

  return (
    <div className="table-scroll">
      <table>
        <thead><tr><th>Service</th><th>Duration</th><th>Price</th><th>Status</th></tr></thead>
        <tbody>
          {services.map((service) => (
            <tr key={service.id}>
              <td><strong>{service.name}</strong><span className="table-description">{service.description}</span></td>
              <td>{service.estimatedDurationMinutes} min</td>
              <td className="table-price">{currency(service.price)}</td>
              <td><span className={`status-badge ${service.active ? 'status-active' : 'status-inactive'}`}><span />{service.active ? 'Active' : 'Inactive'}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function ComingSoon({ section, onNavigate }: { section: Exclude<Section, 'Overview' | 'Services'>; onNavigate: (section: Section) => void }) {
  const details: Record<Exclude<Section, 'Overview' | 'Services'>, { icon: typeof CalendarDays; description: string; phase: string }> = {
    Bookings: { icon: CalendarDays, description: 'Online appointments and walk-ins will share one clear, real-time shop queue.', phase: 'Phase 2 · Booking & scheduling' },
    Customers: { icon: UsersRound, description: 'Customer profiles and vehicle records will make every visit feel familiar.', phase: 'Phase 2 · Customer & vehicle records' },
    Retention: { icon: Sparkles, description: 'Loyalty, offers and thoughtful reminders will help turn a good wash into a regular visit.', phase: 'Phase 4 · Retention & feedback' },
    Reports: { icon: Wallet, description: 'Owner reports will make sales, service trends and returning customers easier to understand.', phase: 'Phase 5 · Billing & analytics' },
    Settings: { icon: Settings2, description: 'Set up opening hours, booking rules and the shop preferences that guide each visit.', phase: 'Shop configuration · Planned' },
    Help: { icon: CircleHelp, description: 'Help and support resources will be available here as WashCare takes shape.', phase: 'Help & support · Planned' },
  }
  const { icon: Icon, description, phase } = details[section]

  return (
    <section className="coming-soon">
      <div className="coming-icon"><Icon size={24} /></div>
      <div className="eyebrow"><span className="eyebrow-dot" /> WASHCARE CRM · IN PROGRESS</div>
      <h1>{section} are on the way.</h1>
      <p>{description}</p>
      <div className="coming-phase">{phase}</div>
      <button className="primary-button" onClick={() => onNavigate('Overview')}>Back to overview <ArrowRight size={16} /></button>
    </section>
  )
}

export default App
