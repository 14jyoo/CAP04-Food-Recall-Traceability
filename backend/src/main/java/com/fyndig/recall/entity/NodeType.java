package com.fyndig.recall.entity;

/**
 * Every traceable object in the plant is a node in one lineage graph.
 *
 *  SUPPLIER_LOT  -> INTERNAL_LOT (receipt)
 *  INTERNAL_LOT  -> INTERNAL_LOT (split / merge / rework / consumption into a production batch)
 *  INTERNAL_LOT  -> ORDER_LINE   (dispatch of a finished-goods batch)
 *
 * Keeping one edge table instead of one join table per hop is what makes split/merged batches,
 * many-to-many consumption and multi-level rework all traversable by the same query.
 */
public enum NodeType {
    SUPPLIER_LOT,
    INTERNAL_LOT,
    ORDER_LINE
}
