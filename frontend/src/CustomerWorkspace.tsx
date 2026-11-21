import { useCallback, useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { ArrowRight, CarFront, Plus, RefreshCw, Search, UserRound, UsersRound } from 'lucide-react'
import type { AuthUser } from './AuthScreen'
import { api } from './api'
import type { SearchRequest } from './GlobalSearchDialog'

type Customer = {
  id: number
  name: string
  phone: string
  email: string | null
  notes: string | null
  registrationSource: 'ONLINE' | 'STAFF'
  active: boolean
}

type Vehicle = { id: number; customerId: number; registrationNo: string; type: string }
type VehicleType = 'HATCHBACK' | 'SEDAN' | 'SUV' | 'BIKE' | 'OTHER'

function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.detail ?? error.response?.data?.message ?? fallback
  }
  return fallback
}

export function CustomerWorkspace({ user, searchRequest }: { user: AuthUser; searchRequest: SearchRequest | null }) {
  const isCustomer = user.role === 'CUSTOMER'
  const [customers, setCustomers] = useState<Customer[]>([])
  const [selectedCustomerId, setSelectedCustomerId] = useState<number | null>(null)
  const [vehicles, setVehicles] = useState<Vehicle[]>([])
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [search, setSearch] = useState('')
  const [showCustomerForm, setShowCustomerForm] = useState(false)
  const [editingCustomerId, setEditingCustomerId] = useState<number | null>(null)
  const [showVehicleForm, setShowVehicleForm] = useState(false)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [email, setEmail] = useState('')
  const [notes, setNotes] = useState('')
  const [registrationNo, setRegistrationNo] = useState('')
  const [vehicleType, setVehicleType] = useState<VehicleType>('HATCHBACK')

  useEffect(() => {
    if (searchRequest) setSearch(searchRequest.query)
  }, [searchRequest])

  const loadData = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      if (isCustomer) {
        const [customerResponse, vehicleResponse] = await Promise.all([
          api.get<Customer>('/customers/me'),
          api.get<Vehicle[]>('/customers/me/vehicles'),
        ])
        setCustomers([customerResponse.data])
        setSelectedCustomerId(customerResponse.data.id)
        setVehicles(vehicleResponse.data)
      } else {
        const response = await api.get<Customer[]>('/customers', { params: search ? { q: search } : undefined })
        setCustomers(response.data)
        setSelectedCustomerId((current) => current && response.data.some((customer) => customer.id === current)
          ? current
          : response.data[0]?.id ?? null)
        setVehicles([])
      }
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not load customer records. Try again.'))
    } finally {
      setLoading(false)
    }
  }, [isCustomer, search])

  useEffect(() => {
    void loadData()
  }, [loadData])

  const selectedCustomer = customers.find((customer) => customer.id === selectedCustomerId) ?? null

  useEffect(() => {
    if (!selectedCustomerId || isCustomer) return
    let cancelled = false
    api.get<Vehicle[]>(`/customers/${selectedCustomerId}/vehicles`)
      .then((response) => { if (!cancelled) setVehicles(response.data) })
      .catch((requestError: unknown) => { if (!cancelled) setError(getErrorMessage(requestError, 'Could not load vehicles.')) })
    return () => { cancelled = true }
  }, [isCustomer, selectedCustomerId])

  async function createCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const response = await api.post<Customer>('/customers', { name, phone, email: email || null, notes: notes || null })
      setCustomers((current) => [...current, response.data].sort((left, right) => left.name.localeCompare(right.name)))
      setSelectedCustomerId(response.data.id)
      setName('')
      setPhone('')
      setEmail('')
      setNotes('')
      setShowCustomerForm(false)
      setNotice('Customer profile created.')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not create this customer. Please try again.'))
    } finally {
      setSaving(false)
    }
  }

  async function updateCustomer(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingCustomerId) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const response = await api.put<Customer>(`/customers/${editingCustomerId}`, { name, phone, email: email || null, notes: notes || null })
      setCustomers((current) => current.map((customer) => customer.id === response.data.id ? response.data : customer))
      setShowCustomerForm(false)
      setNotice('Customer profile updated.')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not update this customer. Please try again.'))
    } finally {
      setSaving(false)
    }
  }

  async function updateMyProfile(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const response = await api.put<Customer>('/customers/me', { name, phone, notes: notes || null })
      setCustomers([response.data])
      setShowCustomerForm(false)
      setNotice('Your profile was updated.')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not update your profile. Please try again.'))
    } finally {
      setSaving(false)
    }
  }

  async function createVehicle(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedCustomerId) return
    setSaving(true)
    setError('')
    setNotice('')
    try {
      const path = isCustomer
        ? '/customers/me/vehicles'
        : `/customers/${selectedCustomerId}/vehicles`
      const response = await api.post<Vehicle>(path, { registrationNo, type: vehicleType })
      setVehicles((current) => [...current, response.data].sort((left, right) => left.registrationNo.localeCompare(right.registrationNo)))
      setRegistrationNo('')
      setShowVehicleForm(false)
      setNotice('Vehicle added to the customer profile.')
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Could not add this vehicle. Please try again.'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> CUSTOMER RELATIONSHIPS</div>
          <h1>{isCustomer ? 'Your profile and vehicles.' : 'Know the people behind every wash.'}</h1>
          <p>{isCustomer ? 'Keep your contact details and vehicles up to date.' : 'Find a customer profile or add a walk-in visit.'}</p>
        </div>
        {!isCustomer ? (
          <button className="primary-button" onClick={() => { setEditingCustomerId(null); setName(''); setPhone(''); setEmail(''); setNotes(''); setShowCustomerForm((shown) => !shown); setError(''); setNotice('') }}>
            <Plus size={16} /> Add walk-in customer
          </button>
        ) : selectedCustomer ? (
          <button className="secondary-button" onClick={() => { setName(selectedCustomer.name); setPhone(selectedCustomer.phone); setEmail(selectedCustomer.email ?? ''); setNotes(selectedCustomer.notes ?? ''); setShowCustomerForm(true); setError(''); setNotice('') }}>
            <UserRound size={14} /> Edit profile
          </button>
        ) : null}
      </div>

      {error && <div className="api-error" role="alert"><span>{error}</span><button onClick={() => void loadData()}><RefreshCw size={15} /> Try again</button></div>}
      {notice && <div className="success-notice" role="status">{notice}</div>}

      {showCustomerForm && (
        <form className="panel crm-form-panel" onSubmit={isCustomer ? updateMyProfile : editingCustomerId ? updateCustomer : createCustomer}>
          <div className="panel-heading"><div><h2>{isCustomer ? 'Your customer profile' : editingCustomerId ? 'Update customer profile' : 'New walk-in customer'}</h2><p>{isCustomer ? 'Your account email is protected; contact the shop if it needs to change.' : editingCustomerId ? 'Update contact details and staff notes.' : 'This profile can be linked to an account after identity verification.'}</p></div></div>
          <div className="crm-form-grid">
            <label>Name<input autoComplete="name" maxLength={160} onChange={(event) => setName(event.target.value)} required value={name} /></label>
            <label>Phone<input autoComplete="tel" maxLength={32} onChange={(event) => setPhone(event.target.value)} pattern="[+0-9() .-]{7,32}" required value={phone} /></label>
            <label>Email <span className="optional-label">{isCustomer ? 'Login email' : 'Optional'}</span><input autoComplete="email" disabled={isCustomer} onChange={(event) => setEmail(event.target.value)} type="email" value={email} /></label>
            {!isCustomer && <label>Notes <span className="optional-label">Optional</span><input maxLength={1000} onChange={(event) => setNotes(event.target.value)} value={notes} /></label>}
          </div>
          <div className="form-actions"><button className="secondary-button" onClick={() => setShowCustomerForm(false)} type="button">Cancel</button><button className="primary-button" disabled={saving} type="submit">{saving ? 'Saving…' : isCustomer || editingCustomerId ? 'Save changes' : 'Create profile'} <ArrowRight size={15} /></button></div>
        </form>
      )}

      <div className={`crm-layout ${isCustomer ? 'crm-layout-single' : ''}`}>
        {!isCustomer && (
          <section className="panel crm-customer-panel">
            <div className="panel-heading">
              <div><h2>Customer profiles</h2><p>{customers.length} {customers.length === 1 ? 'person' : 'people'} in your shop</p></div>
              <button aria-label="Refresh customers" className="icon-button refresh-button" onClick={() => void loadData()}><RefreshCw size={16} /></button>
            </div>
            <label className="customer-search"><Search size={15} /><input aria-label="Search customers" onChange={(event) => setSearch(event.target.value)} placeholder="Search name, phone or email" value={search} /></label>
            {loading ? <div className="empty-state">Loading customer records…</div> : customers.length === 0 ? (
              <div className="crm-empty"><UsersRound size={25} /><strong>{search ? 'No matching customers' : 'No customer profiles yet'}</strong><span>{search ? 'Try another name, phone number or email.' : 'Create the first profile for your walk-in customers.'}</span></div>
            ) : (
              <div className="customer-list">
                {customers.map((customer) => (
                  <button aria-pressed={selectedCustomerId === customer.id} className={`customer-row ${selectedCustomerId === customer.id ? 'customer-row-selected' : ''}`} key={customer.id} onClick={() => { setSelectedCustomerId(customer.id); setShowCustomerForm(false); setError(''); setNotice('') }}>
                    <span className="customer-initial">{customer.name.slice(0, 1).toUpperCase()}</span>
                    <span className="customer-row-copy"><strong>{customer.name}</strong><small>{customer.phone}</small></span>
                    <ArrowRight size={15} />
                  </button>
                ))}
              </div>
            )}
          </section>
        )}

        <section className="panel crm-vehicles-panel">
          <div className="panel-heading">
            <div><h2>{isCustomer ? 'Your vehicles' : selectedCustomer?.name ?? 'Customer vehicles'}</h2><p>{isCustomer ? 'Registration and vehicle type only, as in version one.' : selectedCustomer ? `${selectedCustomer.phone}${selectedCustomer.email ? ` · ${selectedCustomer.email}` : ''}` : 'Choose a customer profile to view vehicles.'}</p></div>
            {selectedCustomer && <div className="customer-actions">
              {!isCustomer && <button className="text-button" onClick={() => { setEditingCustomerId(selectedCustomer.id); setName(selectedCustomer.name); setPhone(selectedCustomer.phone); setEmail(selectedCustomer.email ?? ''); setNotes(selectedCustomer.notes ?? ''); setShowCustomerForm(true); setError(''); setNotice('') }}><UserRound size={14} /> Edit profile</button>}
              <button className="text-button" onClick={() => { setShowVehicleForm((shown) => !shown); setError(''); setNotice('') }}><Plus size={15} /> Add vehicle</button>
            </div>}
          </div>
          {!loading && selectedCustomer && selectedCustomer.notes && <p className="customer-notes">Staff notes: {selectedCustomer.notes}</p>}
          {showVehicleForm && selectedCustomer && (
            <form className="vehicle-form" onSubmit={createVehicle}>
              <label>Registration number<input autoCapitalize="characters" maxLength={20} onChange={(event) => setRegistrationNo(event.target.value)} placeholder="e.g. KA 01 AB 1234" required value={registrationNo} /></label>
              <label>Vehicle type<select onChange={(event) => setVehicleType(event.target.value as VehicleType)} value={vehicleType}><option value="HATCHBACK">Hatchback</option><option value="SEDAN">Sedan</option><option value="SUV">SUV</option><option value="BIKE">Bike</option><option value="OTHER">Other</option></select></label>
              <button className="primary-button" disabled={saving} type="submit">{saving ? 'Adding…' : 'Save vehicle'}</button>
            </form>
          )}
          {!selectedCustomer && !loading ? (
            <div className="crm-empty"><UserRound size={25} /><strong>Select a customer</strong><span>Customer vehicle records will appear here.</span></div>
          ) : loading ? <div className="empty-state">Loading vehicles…</div> : vehicles.length === 0 ? (
            <div className="crm-empty"><CarFront size={25} /><strong>No vehicles on file</strong><span>Add a registration number and vehicle type to get started.</span></div>
          ) : (
            <div className="vehicle-list">
              {vehicles.map((vehicle) => (
                <div className="vehicle-row" key={vehicle.id}>
                  <div className="vehicle-icon"><CarFront size={18} /></div>
                  <div className="vehicle-copy"><strong>{vehicle.registrationNo}</strong><span>{vehicle.type.toLowerCase().replace('_', ' ')}</span></div>
                  <span className="status-badge status-active"><span />On file</span>
                </div>
              ))}
            </div>
          )}
        </section>
      </div>
    </>
  )
}
