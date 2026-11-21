import { useEffect, useState, type FormEvent } from 'react'
import axios from 'axios'
import { CarFront, Clock3, Pencil, Plus, RefreshCw, Search, X } from 'lucide-react'
import { api } from './api'
import type { SearchRequest } from './GlobalSearchDialog'

export type CatalogService = {
  id: number
  name: string
  description: string | null
  price: number
  estimatedDurationMinutes: number
  active: boolean
}

type ServiceForm = {
  name: string
  description: string
  price: string
  estimatedDurationMinutes: string
  active: boolean
}

const emptyForm: ServiceForm = {
  name: '',
  description: '',
  price: '',
  estimatedDurationMinutes: '30',
  active: true,
}

function money(amount: number) {
  return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount)
}

function getErrorMessage(error: unknown) {
  if (axios.isAxiosError(error)) {
    return error.response?.data?.detail ?? error.response?.data?.message ?? 'Could not save this service. Try again.'
  }
  return 'Could not save this service. Try again.'
}

export function ServiceCatalogWorkspace({
  services,
  loading,
  onRefresh,
  searchRequest,
}: {
  services: CatalogService[]
  loading: boolean
  onRefresh: () => void
  searchRequest: SearchRequest | null
}) {
  const [editingId, setEditingId] = useState<number | null>(null)
  const [formOpen, setFormOpen] = useState(false)
  const [form, setForm] = useState<ServiceForm>(emptyForm)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [search, setSearch] = useState('')

  useEffect(() => {
    if (searchRequest) setSearch(searchRequest.query)
  }, [searchRequest])

  function openNewForm() {
    setFormOpen(true)
    setEditingId(null)
    setForm(emptyForm)
    setError('')
    setNotice('')
  }

  function openEditForm(service: CatalogService) {
    setFormOpen(true)
    setEditingId(service.id)
    setForm({
      name: service.name,
      description: service.description ?? '',
      price: String(service.price),
      estimatedDurationMinutes: String(service.estimatedDurationMinutes),
      active: service.active,
    })
    setError('')
    setNotice('')
  }

  async function saveService(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSaving(true)
    setError('')
    setNotice('')
    const payload = {
      name: form.name.trim(),
      description: form.description.trim() || null,
      price: Number(form.price),
      estimatedDurationMinutes: Number(form.estimatedDurationMinutes),
      active: form.active,
    }
    try {
      if (editingId === null) {
        await api.post('/services', payload)
      } else {
        await api.put(`/services/${editingId}`, payload)
      }
      setEditingId(null)
      setFormOpen(false)
      setForm(emptyForm)
      setNotice(editingId === null ? 'Service added to the catalog.' : 'Service details updated.')
      onRefresh()
    } catch (requestError) {
      setError(getErrorMessage(requestError))
    } finally {
      setSaving(false)
    }
  }

  function updateForm(field: keyof ServiceForm, value: string | boolean) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  const visibleServices = services.filter((service) => (
    `${service.name} ${service.description ?? ''}`.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())
  ))

  return (
    <>
      <div className="page-heading">
        <div>
          <div className="eyebrow"><span className="eyebrow-dot" /> OWNER · SERVICE CATALOG</div>
          <h1>Your services, your prices.</h1>
          <p>Update packages and estimated wash times. Existing bookings keep their quoted price.</p>
        </div>
      </div>
      <section className="panel catalog-management-panel">
      <div className="panel-heading catalog-management-heading">
        <div><h2>Service catalog</h2><p>Set the prices and estimated duration customers see when booking.</p></div>
        <div className="customer-actions">
          <label className="customer-search catalog-search"><Search size={14} /><input aria-label="Search services" onChange={(event) => setSearch(event.target.value)} placeholder="Search services" value={search} /></label>
          <button aria-label="Refresh service catalog" className="icon-button refresh-button" onClick={onRefresh}><RefreshCw size={16} /></button>
          {!formOpen && <button className="primary-button" onClick={openNewForm}><Plus size={15} /> Add service</button>}
        </div>
      </div>

      {error && <div className="api-error" role="alert"><span>{error}</span><button onClick={() => setError('')}><X size={14} /> Dismiss</button></div>}
      {notice && <div className="success-notice" role="status">{notice}</div>}

      {!formOpen && services.length === 0 && (
        <button aria-label="Add a new service" className="catalog-add-tile" onClick={openNewForm}>
          <span><Plus size={18} /></span><strong>Create a wash package</strong><small>Give customers a clear service, price and estimated time.</small>
        </button>
      )}

      {formOpen && (
        <form className="catalog-form" onSubmit={saveService}>
          <div className="catalog-form-title"><strong>{editingId === null ? 'New service' : 'Edit service'}</strong><button aria-label="Close service editor" className="icon-button" onClick={() => { setFormOpen(false); setEditingId(null) }} type="button"><X size={15} /></button></div>
          <div className="catalog-form-grid">
            <label>Service name<input autoFocus maxLength={120} onChange={(event) => updateForm('name', event.target.value)} required value={form.name} /></label>
            <label>Description <span className="optional-label">Optional</span><input maxLength={500} onChange={(event) => updateForm('description', event.target.value)} value={form.description} /></label>
            <label>Price (INR)<input min="0" onChange={(event) => updateForm('price', event.target.value)} required step="0.01" type="number" value={form.price} /></label>
            <label>Estimated duration (minutes)<input min="1" onChange={(event) => updateForm('estimatedDurationMinutes', event.target.value)} required step="1" type="number" value={form.estimatedDurationMinutes} /></label>
          </div>
          <label className="service-active-toggle"><input checked={form.active} onChange={(event) => updateForm('active', event.target.checked)} type="checkbox" /> Available for new bookings</label>
          <div className="form-actions"><button className="secondary-button" onClick={() => { setFormOpen(false); setEditingId(null) }} type="button">Cancel</button><button className="primary-button" disabled={saving} type="submit">{saving ? 'Saving…' : editingId === null ? 'Add service' : 'Save changes'}</button></div>
        </form>
      )}

      {loading ? <div className="empty-state">Loading the service catalog…</div> : visibleServices.length === 0 ? (
        <div className="crm-empty"><CarFront size={24} /><strong>{search.trim() ? 'No matching services' : 'No services yet'}</strong><span>{search.trim() ? 'Try another service name or description.' : 'Add your first wash package above.'}</span></div>
      ) : (
        <div className="catalog-service-list">
          {visibleServices.map((service) => (
            <article className="catalog-service-row" key={service.id}>
              <div className="service-icon service-icon-0"><CarFront size={18} /></div>
              <div className="catalog-service-copy"><strong>{service.name}</strong><span>{service.description || 'No description provided.'}</span></div>
              <div className="catalog-duration"><Clock3 size={14} />{service.estimatedDurationMinutes} min</div>
              <strong className="service-price">{money(service.price)}</strong>
              <span className={`status-badge ${service.active ? 'status-active' : 'status-inactive'}`}><span />{service.active ? 'Active' : 'Paused'}</span>
              <button aria-label={`Edit ${service.name}`} className="icon-button catalog-edit-button" onClick={() => openEditForm(service)}><Pencil size={14} /></button>
            </article>
          ))}
        </div>
      )}
      <p className="catalog-note">Changing a service price affects new bookings only; existing bookings retain their original price.</p>
      </section>
    </>
  )
}
