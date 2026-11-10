import { useState } from 'react'
import { api } from '../api'
import { Badge, Card, Table, useLoad } from '../ui'

export default function MasterData({ go }) {
  const sup = useLoad(api.suppliers)
  const [q, setQ] = useState('')
  const lots = useLoad(() => api.supplierLots(q), [q])
  const internal = useLoad(() => api.internalLots(''), [])

  return (
    <>
      <Card title="Suppliers & code aliases">
        <Table rows={sup.data} rowKey={s => s.id} columns={[
          { label: 'Canonical', render: s => <b>{s.canonicalCode}</b> },
          { label: 'Name', key: 'name' },
          { label: 'Known as (aliases)', render: s => s.aliases.map(a => <Badge key={a} tone={a.includes('MANUAL') ? 'info' : 'neutral'}>{a}</Badge>) },
          { label: 'Lots', key: 'lots', num: true },
          { label: 'Contact', key: 'contactEmail' },
        ]} />
      </Card>
      <Card title="Supplier lots" actions={<input placeholder="filter…" value={q} onChange={e => setQ(e.target.value)} />}>
        <Table rows={lots.data} rowKey={l => l.id} onRow={l => go('recall', { lotId: l.id })} columns={[
          { label: 'Supplier', render: l => l.supplier.canonicalCode },
          { label: 'Lot (as supplied)', render: l => <b>{l.lotCodeRaw}</b> },
          { label: 'Key', render: l => <code>{l.lotCodeNormalized}</code> },
          { label: 'Ingredient', render: l => `${l.ingredientCode} · ${l.ingredientName || ''}` },
          { label: 'Qty', render: l => `${l.quantityReceived ?? ''} ${l.uom ?? ''}`, num: true },
          { label: 'Received', key: 'receivedDate' },
          { label: 'Source', render: l => <span className="muted small">{l.sourceSystem} · {l.sourceRecordId}</span> },
          { label: '', render: () => <span className="link">simulate ▸</span> },
        ]} />
      </Card>
      <Card title="Internal lots (ingredient, WIP, finished)">
        <Table rows={internal.data} rowKey={l => l.id} columns={[
          { label: 'Lot', render: l => <b>{l.lotCode}</b> },
          { label: 'Kind', render: l => <Badge tone={l.kind === 'FINISHED' ? 'info' : l.kind === 'WIP' ? 'warn' : 'neutral'}>{l.kind}</Badge> },
          { label: 'Material / SKU', render: l => `${l.materialCode || ''} ${l.materialName ? '· ' + l.materialName : ''}` },
          { label: 'Line', key: 'productionLine' },
          { label: 'Date', key: 'producedDate' },
          { label: 'Source', render: l => <span className="muted small">{l.sourceSystem} · {l.sourceRecordId}</span> },
        ]} />
      </Card>
    </>
  )
}
