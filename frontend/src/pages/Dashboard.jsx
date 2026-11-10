import { api } from '../api'
import { Badge, Card, ErrorBox, Kpi, Table, fmtDate, statusTone, useLoad } from '../ui'

function Bars({ rows, value, label, color = '#2563eb' }) {
  const max = Math.max(1, ...rows.map(value))
  return (
    <div className="bars">
      {rows.map((r, i) => (
        <div className="bar-row" key={i}>
          <span className="bar-label" title={label(r)}>{label(r)}</span>
          <span className="bar-track"><span className="bar-fill" style={{ width: `${(100 * value(r)) / max}%`, background: color }} /></span>
          <span className="bar-val">{value(r)}</span>
        </div>
      ))}
    </div>
  )
}

export default function Dashboard({ go }) {
  const k = useLoad(api.kpis)
  const rec = useLoad(api.recalls)
  const sup = useLoad(api.supplierImpact)
  const ing = useLoad(api.ingestionStats)
  const d = k.data || {}

  return (
    <>
      <ErrorBox error={k.error || rec.error} />
      <div className="kpis">
        <Kpi label="Suppliers" value={d.suppliers} hint={`${d.supplierLots ?? 0} supplier lots`} />
        <Kpi label="Internal lots" value={d.internalLots} hint={`${d.finishedBatches ?? 0} finished batches`} />
        <Kpi label="Dispatched order lines" value={d.orderLines} hint={`${d.edges ?? 0} lineage edges`} />
        <Kpi label="Recalls" value={d.recalls} hint={`${d.openRecalls ?? 0} open`} tone={d.openRecalls ? 'warn' : ''} />
        <Kpi label="Unmatched records" value={d.unmatchedOpen} hint={`${d.unmatchedResolved ?? 0} resolved`} tone={d.unmatchedOpen ? 'bad' : 'ok'} />
        <Kpi label="Avg lineage traversal" value={d.avgTraversalMillis != null ? `${d.avgTraversalMillis} ms` : '–'} />
        <Kpi label="Avg time to notify all" value={d.avgMinutesToFullNotification != null ? `${d.avgMinutesToFullNotification} min` : '–'} />
        <Kpi label="Recalls with proven completeness" value={`${d.verifiedRecallPct ?? 0}%`} tone={d.recalls && d.verifiedRecallPct < 100 ? 'warn' : ''} />
      </div>

      <Card title="Recall events" actions={<button onClick={() => go('recall')}>+ Simulate a recall</button>}>
        <Table rows={rec.data} empty="No recalls yet - open the Recall simulator and pick a supplier lot."
          onRow={r => go('notifications', { recallId: r.id })} rowKey={r => r.id}
          columns={[
            { label: 'Ref', render: r => <b>{r.reference}</b> },
            { label: 'Supplier lot', render: r => <>{r.supplier}<div className="muted">{r.supplierLot} · {r.ingredient}</div></> },
            { label: 'Severity', render: r => <Badge tone={statusTone(r.severity)}>{r.severity}</Badge> },
            { label: 'Status', render: r => <Badge tone={statusTone(r.status)}>{r.status}</Badge> },
            { label: 'Batches', key: 'batches', num: true },
            { label: 'Order lines', key: 'orderLines', num: true },
            { label: 'Customers', key: 'customers', num: true },
            { label: 'Completeness', render: r => r.verified ? <Badge tone="ok">proven</Badge> : <Badge tone="bad">not proven</Badge> },
            { label: 'Notified', render: r => `${r.notificationsSent}/${r.notificationsTotal}` },
            { label: 'Time to notify', render: r => (r.minutesToAllNotified != null ? `${r.minutesToAllNotified} min` : '–') },
            { label: 'Created', render: r => fmtDate(r.createdAt) },
          ]} />
      </Card>

      <div className="grid2">
        <Card title="Supplier recall exposure (order lines affected)">
          {sup.data && <Bars rows={sup.data} value={r => r.orderLinesAffected} label={r => `${r.supplierCode} ${r.supplierName}`} color="#dc2626" />}
          <Table rows={sup.data} columns={[
            { label: 'Supplier', render: r => `${r.supplierCode} · ${r.supplierName}` },
            { label: 'Lots', key: 'supplierLots', num: true },
            { label: 'Recalls', key: 'recalls', num: true },
            { label: 'Customers hit', key: 'customersAffected', num: true },
            { label: 'Open unmatched', render: r => r.openUnmatched ? <Badge tone="bad">{r.openUnmatched}</Badge> : 0 },
          ]} />
        </Card>
        <Card title="Ingestion quality by feed">
          <Table rows={ing.data} columns={[
            { label: 'Feed', key: 'feedType' },
            { label: 'Rows', key: 'read', num: true },
            { label: 'Accepted', key: 'accepted', num: true },
            { label: 'Unmatched', render: r => r.unmatched ? <Badge tone="bad">{r.unmatched}</Badge> : 0, num: true },
            { label: 'Rejected', render: r => r.rejected ? <Badge tone="warn">{r.rejected}</Badge> : 0, num: true },
            { label: 'Duplicate', key: 'duplicate', num: true },
            { label: 'Accept rate', render: r => `${r.read ? Math.round((100 * r.accepted) / r.read) : 0}%`, num: true },
          ]} />
        </Card>
      </div>
    </>
  )
}
