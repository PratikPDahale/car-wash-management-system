import { useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { BookOpen, CalendarDays, CarFront, CircleHelp, CreditCard, Mail, MessageSquareText, Search, ShieldCheck, Sparkles } from 'lucide-react'

type HelpCategory = 'All topics' | 'Account' | 'Bookings' | 'Payments' | 'Vehicles' | 'Rewards & feedback'
type HelpArticle = { category: HelpCategory; question: string; answer: string }
type HelpGuide = { category: HelpCategory; title: string; steps: string[] }
type HelpPolicy = { category: HelpCategory; icon: ReactNode; title: string; text: string }

const categories: HelpCategory[] = [
  'All topics',
  'Account',
  'Bookings',
  'Payments',
  'Vehicles',
  'Rewards & feedback',
]

const articles: HelpArticle[] = [
  {
    category: 'Account',
    question: 'How do I create an account or sign in?',
    answer: 'Choose Register on the sign-in screen to create a customer account, then use the same email and password to sign in. If you cannot sign in, check your email and password and try again. Password reset is not currently available in the app.',
  },
  {
    category: 'Account',
    question: 'Why am I asked to sign in again?',
    answer: 'Your sign-in is kept for the current browser tab. Signing out, closing the tab, or restarting the local demo API without a stable token secret may require you to sign in again.',
  },
  {
    category: 'Vehicles',
    question: 'How do I add or update a vehicle?',
    answer: 'Open Customers from the side navigation. Add a vehicle to your profile using its registration number and vehicle type. Check the registration number before saving; each vehicle can only be registered once.',
  },
  {
    category: 'Bookings',
    question: 'How do I book a wash?',
    answer: 'Open Bookings, choose Book an appointment, select an active service and one of your vehicles, then choose a date and available time. Available times can change before you confirm, so the app checks availability again when you submit.',
  },
  {
    category: 'Bookings',
    question: 'When can I cancel an appointment?',
    answer: 'You can cancel an eligible booking before its appointment start time. Once the appointment starts or the shop has checked the vehicle in or begun service, customer cancellation is no longer available. Contact the shop directly if you need help with a booking.',
  },
  {
    category: 'Bookings',
    question: 'Where can I see my booking status?',
    answer: 'Open Bookings to review your appointments and their current status. The shop updates a booking as it is checked in, started, completed, or cancelled.',
  },
  {
    category: 'Payments',
    question: 'When will my invoice be available?',
    answer: 'The shop creates an invoice when your service is marked complete. You can then view the invoice, balance, and payment history from Bookings.',
  },
  {
    category: 'Payments',
    question: 'How do I pay an invoice?',
    answer: 'Payments are recorded by shop staff in person. The shop currently supports cash, UPI, and card; ask staff to record your payment and confirm the remaining balance on your invoice.',
  },
  {
    category: 'Rewards & feedback',
    question: 'How do loyalty points work?',
    answer: 'You earn 1 point per ₹100 paid when an invoice is fully settled. For an eligible unpaid invoice, 100 points are worth ₹10 off, with the discount capped at 20% of the invoice subtotal. Ask shop staff to apply points before a payment is recorded.',
  },
  {
    category: 'Rewards & feedback',
    question: 'Are offers automatically applied?',
    answer: 'No. Current offers are shown in Retention, but are not automatically applied to invoices. Check the offer terms and ask shop staff whether an offer can be used for your visit.',
  },
  {
    category: 'Rewards & feedback',
    question: 'When can I leave a review?',
    answer: 'After a booking is completed, you can leave one 1–5 star review for that booking from Retention. You can see your submitted reviews there.',
  },
  {
    category: 'Rewards & feedback',
    question: 'How do return reminders work?',
    answer: 'An in-app reminder may appear 90 days after your latest completed service if you have not had a newer completed visit. Email and SMS reminders are not currently configured.',
  },
]

const guides: HelpGuide[] = [
  {
    category: 'Bookings',
    title: 'Book an appointment',
    steps: [
      'Open Bookings and select Book an appointment.',
      'Choose an active wash service and one of your saved vehicles.',
      'Pick a date and one of the available start times.',
      'Confirm the booking. If the slot has just been taken, choose another available time.',
    ],
  },
  {
    category: 'Vehicles',
    title: 'Manage your profile and vehicles',
    steps: [
      'Open Customers to view your customer profile.',
      'Use Edit profile to update your name, phone, or notes.',
      'Add a vehicle with its registration number and type before booking.',
      'If a registration number is rejected as already in use, verify the number or ask shop staff for help.',
    ],
  },
  {
    category: 'Payments',
    title: 'Check an invoice and payment history',
    steps: [
      'Open Bookings after the shop marks your service complete.',
      'Find the completed booking to see its invoice, paid amount, and remaining balance.',
      'Pay the shop in person and ask staff to record cash, UPI, or card payment.',
      'Refresh the page to see the updated payment history and any loyalty points earned after full settlement.',
    ],
  },
  {
    category: 'Rewards & feedback',
    title: 'View rewards, offers, and reviews',
    steps: [
      'Open Retention to see your loyalty balance, current offers, reminders, and reviews.',
      'Ask staff to apply eligible loyalty points before making a payment.',
      'Review offer eligibility with staff; offers are not automatically applied.',
      'After a booking is completed, submit one review for that visit.',
    ],
  },
]

const policies: HelpPolicy[] = [
  { category: 'Bookings', icon: <CalendarDays size={18} />, title: 'Shop hours and appointments', text: 'The current schedule is Tuesday through Sunday, 9:00 AM–6:00 PM. Appointment start times are offered in 30-minute intervals, with capacity for two vehicles at a time.' },
  { category: 'Bookings', icon: <ShieldCheck size={18} />, title: 'Cancellations', text: 'Eligible customer bookings can be cancelled before the appointment starts. Cancellation is unavailable after check-in or once service is in progress.' },
  { category: 'Payments', icon: <CreditCard size={18} />, title: 'Invoices and payments', text: 'An invoice is created when service is marked complete. Payments are recorded at the shop by staff; supported methods are cash, UPI, and card. No online payment gateway is available.' },
  { category: 'Vehicles', icon: <CarFront size={18} />, title: 'Service prices and vehicles', text: 'Your booking keeps the service price quoted when it was made, even if the catalog price later changes. A vehicle registration number can only be linked once.' },
  { category: 'Rewards & feedback', icon: <Sparkles size={18} />, title: 'Loyalty and offers', text: 'Points are awarded after full invoice settlement. One hundred points equal ₹10 off, up to 20% of the invoice subtotal, and must be applied before payment. Offers are informational and are not automatically applied.' },
  { category: 'Rewards & feedback', icon: <MessageSquareText size={18} />, title: 'Reviews and reminders', text: 'You may submit one 1–5 star review per completed booking. An in-app return reminder may be generated after 90 days without a newer completed visit.' },
]

export function HelpSupportWorkspace() {
  const [query, setQuery] = useState('')
  const [category, setCategory] = useState<HelpCategory>('All topics')
  const normalizedQuery = query.trim().toLocaleLowerCase()

  const visibleArticles = useMemo(() => articles.filter((article) => (
    (category === 'All topics' || article.category === category)
    && `${article.question} ${article.answer} ${article.category}`.toLocaleLowerCase().includes(normalizedQuery)
  )), [category, normalizedQuery])
  const visibleGuides = useMemo(() => guides.filter((guide) => (
    (category === 'All topics' || guide.category === category)
    && `${guide.title} ${guide.steps.join(' ')} ${guide.category}`.toLocaleLowerCase().includes(normalizedQuery)
  )), [category, normalizedQuery])
  const visiblePolicies = useMemo(() => policies.filter((policy) => (
    (category === 'All topics' || policy.category === category)
    &&
    `${policy.title} ${policy.text}`.toLocaleLowerCase().includes(normalizedQuery)
  )), [category, normalizedQuery])
  const resultCount = visibleArticles.length + visibleGuides.length + visiblePolicies.length

  return (
    <>
      <div className="page-heading help-page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> CUSTOMER HELP CENTER</div>
          <h1>How can we help?</h1>
          <p>Find answers about your WashCare account, appointments, payments, and rewards.</p>
        </div>
      </div>

      <section aria-label="Search help articles" className="help-search-panel">
        <Search aria-hidden="true" size={18} />
        <input
          aria-label="Search help topics"
          onChange={(event) => setQuery(event.target.value)}
          placeholder="Search a question or topic"
          value={query}
        />
        <span>{resultCount} results</span>
      </section>

      <nav aria-label="Help topic categories" className="help-categories">
        {categories.map((item) => (
          <button
            aria-pressed={category === item}
            className={category === item ? 'help-category-active' : ''}
            key={item}
            onClick={() => setCategory(item)}
            type="button"
          >
            {item}
          </button>
        ))}
      </nav>

      <div className="help-content-grid">
        <section aria-labelledby="help-faq-title" className="panel help-panel">
          <div className="panel-heading">
            <div><h2 id="help-faq-title"><CircleHelp size={17} /> Frequently asked questions</h2><p>Answers to common account and shop questions.</p></div>
          </div>
          {visibleArticles.length ? (
            <div className="help-faq-list">
              {visibleArticles.map((article) => (
                <details className="help-faq-item" key={article.question}>
                  <summary>{article.question}<span>{article.category}</span></summary>
                  <p>{article.answer}</p>
                </details>
              ))}
            </div>
          ) : (
            <div className="help-empty"><CircleHelp size={22} /><strong>No matching questions</strong><span>Try a different search or topic.</span></div>
          )}
        </section>

        <section aria-labelledby="help-guides-title" className="panel help-panel">
          <div className="panel-heading">
            <div><h2 id="help-guides-title"><BookOpen size={17} /> Step-by-step guides</h2><p>Quick instructions for common tasks.</p></div>
          </div>
          {visibleGuides.length ? (
            <div className="help-guides-list">
              {visibleGuides.map((guide) => (
                <article className="help-guide" key={guide.title}>
                  <div className="help-guide-heading"><strong>{guide.title}</strong><span>{guide.category}</span></div>
                  <ol>{guide.steps.map((step) => <li key={step}>{step}</li>)}</ol>
                </article>
              ))}
            </div>
          ) : (
            <div className="help-empty"><BookOpen size={22} /><strong>No matching guides</strong><span>Try a different search or topic.</span></div>
          )}
        </section>

        <section aria-labelledby="help-policies-title" className="panel help-panel help-policy-panel">
          <div className="panel-heading">
            <div><h2 id="help-policies-title"><ShieldCheck size={17} /> Shop policies</h2><p>Current booking and service rules.</p></div>
          </div>
          {visiblePolicies.length ? (
            <div className="help-policy-grid">
              {visiblePolicies.map((policy) => (
                <article className="help-policy" key={policy.title}>
                  <span>{policy.icon}</span>
                  <div><strong>{policy.title}</strong><p>{policy.text}</p></div>
                </article>
              ))}
            </div>
          ) : (
            <div className="help-empty"><ShieldCheck size={22} /><strong>No matching policies</strong><span>Try a different search.</span></div>
          )}
        </section>

        <section aria-labelledby="help-contact-title" className="help-contact-card">
          <div className="help-contact-icon"><Mail size={18} /></div>
          <div>
            <h2 id="help-contact-title">Contact the shop</h2>
            <p>Direct support email and phone details have not been published yet. Please check back here for shop contact information.</p>
          </div>
        </section>
      </div>
    </>
  )
}
