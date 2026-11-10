import { useMemo, useState } from 'react'

const EDGE_COLORS = {
  RECEIPT: '#64748b', CONSUMPTION: '#2563eb', SPLIT: '#d97706', MERGE: '#7c3aed', REWORK: '#db2777', DISPATCH: '#059669',
}
const NODE_STYLE = {
  SUPPLIER_LOT: { fill: '#fee2e2', stroke: '#dc2626' },
  INGREDIENT: { fill: '#f1f5f9', stroke: '#64748b' },
  WIP: { fill: '#fef3c7', stroke: '#d97706' },
  FINISHED: { fill: '#dbeafe', stroke: '#2563eb' },
  ORDER_LINE: { fill: '#d1fae5', stroke: '#059669' },
}

const W = 168, H = 40, GX = 64, GY = 14

/** Layered left-to-right lineage graph. Columns = longest-path depth so splits/merges read naturally. */
export default function LineageGraph({ graph, height = 520 }) {
  const [hover, setHover] = useState(null)

  const layout = useMemo(() => {
    if (!graph) return null
    const all = [graph.root, ...graph.nodes]
    const byKey = Object.fromEntries(all.map(n => [n.key, n]))
    // longest-path layering (graph is a DAG for traceability purposes)
    const hasIncoming = new Set(graph.edges.filter(e => byKey[e.from]).map(e => e.to))
    const layer = {}
    all.forEach(n => { if (!hasIncoming.has(n.key)) layer[n.key] = 0 })
    for (let i = 0; i < all.length; i++) {
      let changed = false
      for (const e of graph.edges) {
        if (layer[e.from] !== undefined && byKey[e.to]) {
          const l = layer[e.from] + 1
          if (layer[e.to] === undefined || layer[e.to] < l) { layer[e.to] = l; changed = true }
        }
      }
      if (!changed) break
    }
    // order lines always in the last column
    const maxL = Math.max(0, ...Object.values(layer))
    all.forEach(n => { if (n.type === 'ORDER_LINE') layer[n.key] = maxL })
    const cols = {}
    all.forEach(n => { const l = layer[n.key] ?? 0; (cols[l] = cols[l] || []).push(n) })
    const pos = {}
    let maxRows = 0
    Object.entries(cols).forEach(([l, ns]) => {
      ns.sort((a, b) => a.code.localeCompare(b.code))
      maxRows = Math.max(maxRows, ns.length)
      ns.forEach((n, i) => { pos[n.key] = { x: 20 + Number(l) * (W + GX), y: 20 + i * (H + GY) } })
    })
    // vertically centre short columns
    Object.entries(cols).forEach(([, ns]) => {
      const off = ((maxRows - ns.length) * (H + GY)) / 2
      ns.forEach(n => { pos[n.key].y += off })
    })
    return { all, pos, width: 40 + (maxL + 1) * (W + GX), height: 40 + maxRows * (H + GY) }
  }, [graph])

  // nodes on any path through the hovered node
  const lit = useMemo(() => {
    if (!hover || !graph) return null
    const s = new Set([hover])
    const down = [hover], up = [hover]
    while (down.length) { const k = down.pop(); graph.edges.filter(e => e.from === k).forEach(e => { if (!s.has(e.to)) { s.add(e.to); down.push(e.to) } }) }
    const s2 = new Set([hover])
    while (up.length) { const k = up.pop(); graph.edges.filter(e => e.to === k).forEach(e => { if (!s2.has(e.from)) { s2.add(e.from); up.push(e.from) } }) }
    s2.forEach(k => s.add(k))
    return s
  }, [hover, graph])

  if (!layout) return null
  const { all, pos, width } = layout
  const kindOf = n => (n.type === 'INTERNAL_LOT' ? n.kind : n.type)

  return (
    <div className="graph-wrap" style={{ maxHeight: height }}>
      <svg width={width} height={layout.height} role="img" aria-label="Lineage graph">
        <defs>
          {Object.entries(EDGE_COLORS).map(([t, c]) => (
            <marker key={t} id={`arrow-${t}`} viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
              <path d="M 0 0 L 10 5 L 0 10 z" fill={c} />
            </marker>
          ))}
        </defs>
        {graph.edges.map((e, i) => {
          const a = pos[e.from], b = pos[e.to]
          if (!a || !b) return null
          const x1 = a.x + W, y1 = a.y + H / 2, x2 = b.x, y2 = b.y + H / 2
          const mx = (x1 + x2) / 2
          const dim = lit && !(lit.has(e.from) && lit.has(e.to))
          return (
            <path key={i} d={`M${x1},${y1} C${mx},${y1} ${mx},${y2} ${x2 - 2},${y2}`} fill="none"
              stroke={EDGE_COLORS[e.edgeType] || '#999'} strokeWidth={dim ? 1 : 1.8} opacity={dim ? 0.15 : 0.9}
              markerEnd={`url(#arrow-${e.edgeType})`}>
              <title>{`${e.edgeType} ${e.quantity ?? ''} ${e.uom ?? ''} · ${e.sourceSystem} ${e.sourceRecordId}`}</title>
            </path>
          )
        })}
        {all.map(n => {
          const p = pos[n.key]
          const st = NODE_STYLE[kindOf(n)] || NODE_STYLE.INGREDIENT
          const dim = lit && !lit.has(n.key)
          return (
            <g key={n.key} transform={`translate(${p.x},${p.y})`} opacity={dim ? 0.25 : 1}
              onMouseEnter={() => setHover(n.key)} onMouseLeave={() => setHover(null)} style={{ cursor: 'pointer' }}>
              <rect width={W} height={H} rx={n.type === 'ORDER_LINE' ? 18 : 6} fill={st.fill} stroke={st.stroke} strokeWidth={n.depth === 0 ? 2.5 : 1.2} />
              <text x={10} y={16} fontSize="12" fontWeight="600" fill="#0f172a">{n.code.length > 22 ? n.code.slice(0, 21) + '…' : n.code}</text>
              <text x={10} y={31} fontSize="10" fill="#475569">{(n.material || kindOf(n) || '').slice(0, 26)}</text>
              <title>{`${kindOf(n)} ${n.code}\n${n.label}\n\n${(n.paths || []).slice(0, 4).join('\n')}`}</title>
            </g>
          )
        })}
      </svg>
      <div className="legend">
        {Object.entries(EDGE_COLORS).map(([t, c]) => <span key={t}><i style={{ background: c }} />{t.toLowerCase()}</span>)}
        <span className="muted">· hover a node to isolate its paths</span>
      </div>
    </div>
  )
}
