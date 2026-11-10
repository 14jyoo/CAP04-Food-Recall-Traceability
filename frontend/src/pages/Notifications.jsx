import { useEffect, useState } from 'react'
import { api } from '../api'
import { Badge, Card, ErrorBox, Kpi, Table, fmtDate, statusTone, useLoad } from '../ui'

export default function Notifications({ params }) {
  const recalls = useLoad(api.recalls)
  const [recallId, setRecallId] = useState(params?.recallId || null)
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    if (!recallId && recalls.data?.length) setRecallId(recalls.data[0].id)
  }, [recalls.data, recallId])

  const load = () => recallId && api.recall(recallId).then(setDetail).catch(e => setError(e.message))
  useEffect(() => { load() }, [recallId]) // eslint-disable-line react-hooks/exhaustive-deps

  const act = async (fn) => { try { await fn(); await load(); recalls.reload() } catch (e) { setError(e.message) } }
  const r = detail?.recall
  const ns = detail?.notifications || []

  return (
    <>
      <ErrorBox error={error} />
      <Card title="Recall" actions={
        <select value={recallId || ''} onChange={e => setRecallId(Number(e.target.value))}>
          {(recalls.data || []).map(x => <option key={x.id} value={x.id}>{x.reference} · {x.supplierLot} · {x.status}</option>)}
        </select>}>
        {!r && <div className="empty">No recall selected.</div>}
        {r && (
          <>
            <div className="recall-head">
              <div>
                <h2>{r.reference} <Badge tone={statusTone(r.severity)}>{r.severity}</Badge> <Badge tone={statusTone(r.status)}>{r.status}</Badge></h2>
                <div className="muted">{r.reason} · lot {r.supplierLot.lotCodeRaw} from {r.supplierLot.supplier.canonicalCode} {r.supplierLot.supplier.name}</div>
              </div>
              <div className="actions">
                <button onClick={() => act(() => api.recompute(r.id))} title="Re-run lineage (after reconciliation)">Recompute scope</button>
                <button onClick={() => act(() => api.sendAll(r.id))} disabled={!ns.some(n => n.status === 'PENDING')}>Send all pending</button>
                <button className="secondary" onClick={() => act(() => api.closeRecall(r.id))} disabled={r.status === 'CLOSED'}>Close recall</button>
              </div>
            </div>
            <div className="kpis">
              <Kpi label="Order lines" value={r.orderLinesAffected} />
              <Kpi label="Customers" value={r.customersAffected} />
              <Kpi label="Finished batches" value={r.finishedBatchesAffected} />
              <Kpi label="Completeness" value={r.completenessVerified ? 'proven' : 'not proven'} tone={r.completenessVerified ? 'ok' : 'bad'} />
              <Kpi label="Time to lineage" value={detail.minutesToLineage != null ? `${detail.minutesToLineage} min` : '–'} />
              <Kpi label="Time to notify all" value={detail.minutesToAllNotified != null ? `${detail.minutesToAllNotified} min` : 'in progress'} />
              <Kpi label="Sent" value={`${ns.filter(n => n.status !== 'PENDING').length}/${ns.length}`} />
              <Kpi label="Acknowledged" value={`${ns.filter(n => n.status === 'ACKNOWLEDGED').length}/${ns.length}`} />
            </div>
          </>
        )}
      </Card>

      {r && (
        <Card title="Customer notifications">
          <Table rows={ns} rowKey={n => n.id} empty={r.status === 'SIMULATED' ? 'Simulation only - no notifications are created.' : 'No customers affected.'}
            columns={[
              { label: 'Customer', render: n => <>{n.customerCode}<div className="muted">{n.customerName}</div></> },
              { label: 'Contact', key: 'customerEmail' },
              { label: 'Lines', key: 'orderLinesAffected', num: true },
              { label: 'Status', render: n => <Badge tone={statusTone(n.status)}>{n.status}</Badge> },
              { label: 'Sent', render: n => fmtDate(n.sentAt) },
              { label: 'Acknowledged', render: n => fmtDate(n.acknowledgedAt) },
              { label: '', render: n => (
                <div className="row-actions">
                  {n.status === 'PENDING' && <button className="small" onClick={() => act(() => api.updateNotification(n.id, 'SENT'))}>Mark sent</button>}
                  {n.status !== 'ACKNOWLEDGED' && <button className="small" onClick={() => act(() => api.updateNotification(n.id, 'ACKNOWLEDGED', 'Customer confirmed stock quarantined'))}>Acknowledge</button>}
                  {n.status === 'PENDING' && <button className="small secondary" onClick={() => act(() => api.updateNotification(n.id, 'FAILED', 'Bounce'))}>Failed</button>}
                </div>) },
            ]} />
        </Card>
      )}

      {r && (
        <Card title="Impacted order lines with lineage path">
          <Table rows={detail.impacts} rowKey={i => i.id} columns={[
            { label: 'Order / line', render: i => <b>{i.orderLine.orderNo}/{i.orderLine.lineNo}</b> },
            { label: 'Customer', render: i => i.orderLine.customerCode },
            { label: 'SKU', render: i => i.orderLine.skuCode },
            { label: 'Finished lot', key: 'finishedLotCode' },
            { label: 'Path', render: i => <div className="paths small">{(i.lineagePath || '').split(' || ').map((p, k) => <div key={k}>{p}</div>)}</div> },
          ]} />
        </Card>
      )}
    </>
  )
}
