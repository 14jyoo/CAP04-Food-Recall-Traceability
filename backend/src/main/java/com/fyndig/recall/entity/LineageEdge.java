package com.fyndig.recall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Directed edge parent -> child in the plant lineage graph. One table for every hop type so that
 * a single recursive traversal covers receipt, consumption, split, merge, rework and dispatch.
 *
 * (parent_type, parent_id, child_type, child_id, source_record_id) is unique: the same physical
 * movement cannot be ingested twice, which is one of the ingestion validation rules.
 */
@Entity
@Table(name = "lineage_edge",
        uniqueConstraints = @UniqueConstraint(columnNames = {"parent_type", "parent_id", "child_type", "child_id", "source_record_id"}),
        indexes = {
                @Index(name = "ix_edge_parent", columnList = "parent_type,parent_id"),
                @Index(name = "ix_edge_child", columnList = "child_type,child_id")
        })
public class LineageEdge {

    public enum EdgeType { RECEIPT, CONSUMPTION, SPLIT, MERGE, REWORK, DISPATCH }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "parent_type", nullable = false, length = 16)
    private NodeType parentType;

    @Column(name = "parent_id", nullable = false)
    private Long parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "child_type", nullable = false, length = 16)
    private NodeType childType;

    @Column(name = "child_id", nullable = false)
    private Long childId;

    @Enumerated(EnumType.STRING)
    @Column(name = "edge_type", nullable = false, length = 16)
    private EdgeType edgeType;

    @Column(precision = 18, scale = 3)
    private BigDecimal quantity;
    private String uom;
    private LocalDateTime eventTime;
    private String sourceSystem;

    @Column(name = "source_record_id", nullable = false)
    private String sourceRecordId;

    public Long getId() { return id; }
    public NodeType getParentType() { return parentType; }
    public void setParentType(NodeType parentType) { this.parentType = parentType; }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public NodeType getChildType() { return childType; }
    public void setChildType(NodeType childType) { this.childType = childType; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public EdgeType getEdgeType() { return edgeType; }
    public void setEdgeType(EdgeType edgeType) { this.edgeType = edgeType; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
}
