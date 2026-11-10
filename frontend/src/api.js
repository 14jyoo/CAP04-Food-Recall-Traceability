const BASE = import.meta.env.VITE_API_BASE || ''

async function req(method, path, body, isForm) {
  const opts = { method, headers: {} }
  if (body !== undefined) {
    if (isForm) opts.body = body
    else { opts.body = JSON.stringify(body); opts.headers['Content-Type'] = 'application/json' }
  }
  const r = await fetch(BASE + path, opts)
  const text = await r.text()
  const data = text ? JSON.parse(text) : null
  if (!r.ok) throw new Error((data && data.error) || `${r.status} ${r.statusText}`)
  return data
}

export const api = {
  kpis: () => req('GET', '/api/analytics/kpis'),
  supplierImpact: () => req('GET', '/api/analytics/supplier-impact'),
  ingestionStats: () => req('GET', '/api/analytics/ingestion'),
  feeds: () => req('GET', '/api/ingest/feeds'),
  runs: () => req('GET', '/api/ingest/runs'),
  ingest: (feed, file, sourceSystem) => {
    const fd = new FormData()
    fd.append('file', file)
    if (sourceSystem) fd.append('sourceSystem', sourceSystem)
    return req('POST', `/api/ingest/${feed}`, fd, true)
  },
  suppliers: () => req('GET', '/api/suppliers'),
  supplierLots: (q = '') => req('GET', `/api/supplier-lots?q=${encodeURIComponent(q)}`),
  internalLots: (q = '') => req('GET', `/api/internal-lots?q=${encodeURIComponent(q)}`),
  orders: (p = {}) => req('GET', `/api/orders?${new URLSearchParams(p)}`),
  simulate: (lotId) => req('GET', `/api/recalls/simulate/${lotId}`),
  createRecall: (body) => req('POST', '/api/recalls', body),
  recalls: () => req('GET', '/api/recalls'),
  recall: (id) => req('GET', `/api/recalls/${id}`),
  recompute: (id) => req('POST', `/api/recalls/${id}/recompute`),
  closeRecall: (id) => req('POST', `/api/recalls/${id}/close`),
  affected: (p = {}) => req('GET', `/api/affected-orders?${new URLSearchParams(p)}`),
  traceOrder: (orderNo) => req('GET', `/api/lineage/order/${encodeURIComponent(orderNo)}`),
  notifications: (recallId) => req('GET', `/api/notifications${recallId ? `?recallId=${recallId}` : ''}`),
  updateNotification: (id, status, note) => req('POST', `/api/notifications/${id}`, { status, note }),
  sendAll: (recallId) => req('POST', `/api/notifications/send-all/${recallId}`),
  reconciliation: (status = 'OPEN') => req('GET', `/api/reconciliation?status=${status}`),
  suggest: (id) => req('GET', `/api/reconciliation/${id}/suggest`),
  resolve: (id, resolveTo) => req('POST', `/api/reconciliation/${id}/resolve`, { resolveTo, resolvedBy: 'ui' }),
  ignore: (id) => req('POST', `/api/reconciliation/${id}/ignore?by=ui`),
  reset: () => req('POST', '/api/admin/reset'),
  verify: (body) => req('POST', '/api/admin/verify', body),
}
