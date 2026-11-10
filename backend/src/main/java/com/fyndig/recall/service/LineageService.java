package com.fyndig.recall.service;

import com.fyndig.recall.entity.*;
import com.fyndig.recall.repo.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Graph traversal over lineage_edge.
 *
 * forward(): everything downstream of a node (recall direction: supplier lot -> orders)
 * backward(): everything upstream of a node (investigation direction: order -> supplier lots)
 *
 * Every reached node carries the explicit path that reached it, which is what makes the recall
 * scope *explainable* rather than just a list.
 */
@Service
public class LineageService {

    public record NodeRef(NodeType type, Long id) {}

    public record NodeView(String key, NodeType type, Long id, String code, String kind, String material,
                           String label, int depth, List<String> paths) {}

    public record EdgeView(String from, String to, String edgeType, String quantity, String uom, String eventTime,
                           String sourceSystem, String sourceRecordId) {}

    public record Graph(NodeView root, List<NodeView> nodes, List<EdgeView> edges, int maxDepth, long millis) {
        public List<NodeView> ofType(NodeType t) { return nodes.stream().filter(n -> n.type() == t).toList(); }
    }

    private final LineageEdgeRepo edges;
    private final SupplierLotRepo supplierLots;
    private final InternalLotRepo internalLots;
    private final OrderLineRepo orderLines;
    private final JdbcTemplate jdbc;

    public LineageService(LineageEdgeRepo edges, SupplierLotRepo supplierLots, InternalLotRepo internalLots,
                          OrderLineRepo orderLines, JdbcTemplate jdbc) {
        this.edges = edges; this.supplierLots = supplierLots; this.internalLots = internalLots;
        this.orderLines = orderLines; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Graph forward(NodeType type, Long id) { return traverse(type, id, true); }

    @Transactional(readOnly = true)
    public Graph backward(NodeType type, Long id) { return traverse(type, id, false); }

    private Graph traverse(NodeType rootType, Long rootId, boolean forward) {
        long t0 = System.nanoTime();
        Map<String, NodeView> seen = new LinkedHashMap<>();
        Map<String, List<String>> pathsByKey = new HashMap<>();
        Map<String, Integer> depthByKey = new HashMap<>();
        List<EdgeView> edgeViews = new ArrayList<>();
        Set<Long> edgeIds = new HashSet<>();

        String rootKey = key(rootType, rootId);
        String rootLabel = label(rootType, rootId);
        depthByKey.put(rootKey, 0);
        pathsByKey.put(rootKey, List.of(rootLabel));

        Deque<NodeRef> queue = new ArrayDeque<>();
        queue.add(new NodeRef(rootType, rootId));
        Set<String> visited = new HashSet<>();
        int maxDepth = 0;

        while (!queue.isEmpty()) {
            NodeRef cur = queue.poll();
            String curKey = key(cur.type(), cur.id());
            if (!visited.add(curKey)) continue;
            int depth = depthByKey.get(curKey);
            maxDepth = Math.max(maxDepth, depth);

            List<LineageEdge> out = forward
                    ? edges.findByParentTypeAndParentId(cur.type(), cur.id())
                    : edges.findByChildTypeAndChildId(cur.type(), cur.id());

            for (LineageEdge e : out) {
                NodeType nt = forward ? e.getChildType() : e.getParentType();
                Long nid = forward ? e.getChildId() : e.getParentId();
                String nkey = key(nt, nid);
                if (edgeIds.add(e.getId())) {
                    edgeViews.add(new EdgeView(key(e.getParentType(), e.getParentId()), key(e.getChildType(), e.getChildId()),
                            e.getEdgeType().name(), e.getQuantity() == null ? null : e.getQuantity().toPlainString(), e.getUom(),
                            e.getEventTime() == null ? null : e.getEventTime().toString(), e.getSourceSystem(), e.getSourceRecordId()));
                }
                // Record every distinct path (bounded) so split/merge rejoins are explainable.
                List<String> newPaths = new ArrayList<>();
                String step = " -" + e.getEdgeType().name().toLowerCase() + "-> ";
                String nLabel = label(nt, nid);
                for (String p : pathsByKey.getOrDefault(curKey, List.of(rootLabel))) {
                    if (p.contains(nLabel)) continue;                 // guard against cycles (rework loops)
                    newPaths.add(forward ? p + step + nLabel : nLabel + step + p);
                }
                List<String> existing = pathsByKey.computeIfAbsent(nkey, k -> new ArrayList<>());
                for (String np : newPaths) if (existing.size() < 25 && !existing.contains(np)) existing.add(np);
                depthByKey.merge(nkey, depth + 1, Math::min);
                if (!visited.contains(nkey)) queue.add(new NodeRef(nt, nid));
            }
        }

        for (String k : visited) {
            if (k.equals(rootKey)) continue;
            NodeType t = NodeType.valueOf(k.substring(0, k.indexOf(':')));
            Long id = Long.valueOf(k.substring(k.indexOf(':') + 1));
            seen.put(k, view(t, id, depthByKey.get(k), pathsByKey.getOrDefault(k, List.of())));
        }
        List<NodeView> nodes = new ArrayList<>(seen.values());
        nodes.sort(Comparator.comparingInt(NodeView::depth).thenComparing(NodeView::code));
        NodeView root = view(rootType, rootId, 0, List.of(rootLabel));
        return new Graph(root, nodes, edgeViews, maxDepth, (System.nanoTime() - t0) / 1_000_000);
    }

    // ------------------------------------------------------------ independent SQL check (recursive CTE)

    /**
     * Same closure computed in SQL (WITH RECURSIVE) with no Java involved. Used by the completeness
     * proof: if the Java BFS and the SQL closure disagree, the recall is flagged as unverified.
     */
    @Transactional(readOnly = true)
    public Set<String> forwardClosureSql(NodeType type, Long id) {
        String sql = """
            WITH RECURSIVE reach(node_type, node_id, depth) AS (
                SELECT CAST(? AS VARCHAR(16)), CAST(? AS BIGINT), 0
                UNION
                SELECT e.child_type, e.child_id, r.depth + 1
                FROM lineage_edge e
                JOIN reach r ON e.parent_type = r.node_type AND e.parent_id = r.node_id
                WHERE r.depth < 64
            )
            SELECT DISTINCT node_type, node_id FROM reach
            """;
        Set<String> out = new HashSet<>();
        jdbc.query(sql, rs -> { out.add(rs.getString(1) + ":" + rs.getLong(2)); }, type.name(), id);
        out.remove(key(type, id));
        return out;
    }

    // ------------------------------------------------------------ labels

    public static String key(NodeType t, Long id) { return t.name() + ":" + id; }

    public String label(NodeType t, Long id) {
        return switch (t) {
            case SUPPLIER_LOT -> supplierLots.findById(id).map(l -> l.getSupplier().getCanonicalCode() + "/" + l.getLotCodeRaw()).orElse("SUPPLIER_LOT#" + id);
            case INTERNAL_LOT -> internalLots.findById(id).map(InternalLot::getLotCode).orElse("INTERNAL_LOT#" + id);
            case ORDER_LINE -> orderLines.findById(id).map(o -> o.getOrderNo() + "/" + o.getLineNo()).orElse("ORDER_LINE#" + id);
        };
    }

    public NodeView view(NodeType t, Long id, int depth, List<String> paths) {
        return switch (t) {
            case SUPPLIER_LOT -> supplierLots.findById(id)
                    .map(l -> new NodeView(key(t, id), t, id, l.getLotCodeRaw(), "SUPPLIER_LOT", l.getIngredientCode(),
                            l.getSupplier().getCanonicalCode() + " " + l.getSupplier().getName() + " · " + l.getIngredientName(), depth, paths))
                    .orElse(new NodeView(key(t, id), t, id, "?", "SUPPLIER_LOT", null, "missing", depth, paths));
            case INTERNAL_LOT -> internalLots.findById(id)
                    .map(l -> new NodeView(key(t, id), t, id, l.getLotCode(), l.getKind().name(), l.getMaterialCode(),
                            (l.getMaterialName() == null ? l.getMaterialCode() : l.getMaterialName())
                                    + (l.getProductionLine() == null ? "" : " · " + l.getProductionLine()), depth, paths))
                    .orElse(new NodeView(key(t, id), t, id, "?", "INTERNAL_LOT", null, "missing", depth, paths));
            case ORDER_LINE -> orderLines.findById(id)
                    .map(o -> new NodeView(key(t, id), t, id, o.getOrderNo() + "/" + o.getLineNo(), "ORDER_LINE", o.getSkuCode(),
                            o.getCustomerCode() + " " + (o.getCustomerName() == null ? "" : o.getCustomerName())
                                    + (o.getShipToRegion() == null ? "" : " · " + o.getShipToRegion()), depth, paths))
                    .orElse(new NodeView(key(t, id), t, id, "?", "ORDER_LINE", null, "missing", depth, paths));
        };
    }
}
