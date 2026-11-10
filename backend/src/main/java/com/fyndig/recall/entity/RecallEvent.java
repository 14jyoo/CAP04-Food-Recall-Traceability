package com.fyndig.recall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "recall_event")
public class RecallEvent {

    public enum Status { SIMULATED, OPEN, NOTIFYING, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String reference;        // RC-2026-0001
    @Column(nullable = false)
    private String reason;
    @Column(nullable = false)
    private String severity;         // CLASS_I / CLASS_II / CLASS_III

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private SupplierLot supplierLot;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime lineageComputedAt;
    private LocalDateTime allNotifiedAt;
    private LocalDateTime closedAt;

    // scope metrics snapshot
    private int internalLotsAffected;
    private int finishedBatchesAffected;
    private int orderLinesAffected;
    private int ordersAffected;
    private int customersAffected;
    private int unresolvedReferences;
    private long traversalMillis;
    private int maxDepth;
    private boolean completenessVerified;

    @Column(length = 4000)
    private String completenessNotes;

    public Long getId() { return id; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public SupplierLot getSupplierLot() { return supplierLot; }
    public void setSupplierLot(SupplierLot supplierLot) { this.supplierLot = supplierLot; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getLineageComputedAt() { return lineageComputedAt; }
    public void setLineageComputedAt(LocalDateTime lineageComputedAt) { this.lineageComputedAt = lineageComputedAt; }
    public LocalDateTime getAllNotifiedAt() { return allNotifiedAt; }
    public void setAllNotifiedAt(LocalDateTime allNotifiedAt) { this.allNotifiedAt = allNotifiedAt; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }
    public int getInternalLotsAffected() { return internalLotsAffected; }
    public void setInternalLotsAffected(int v) { this.internalLotsAffected = v; }
    public int getFinishedBatchesAffected() { return finishedBatchesAffected; }
    public void setFinishedBatchesAffected(int v) { this.finishedBatchesAffected = v; }
    public int getOrderLinesAffected() { return orderLinesAffected; }
    public void setOrderLinesAffected(int v) { this.orderLinesAffected = v; }
    public int getOrdersAffected() { return ordersAffected; }
    public void setOrdersAffected(int v) { this.ordersAffected = v; }
    public int getCustomersAffected() { return customersAffected; }
    public void setCustomersAffected(int v) { this.customersAffected = v; }
    public int getUnresolvedReferences() { return unresolvedReferences; }
    public void setUnresolvedReferences(int v) { this.unresolvedReferences = v; }
    public long getTraversalMillis() { return traversalMillis; }
    public void setTraversalMillis(long v) { this.traversalMillis = v; }
    public int getMaxDepth() { return maxDepth; }
    public void setMaxDepth(int v) { this.maxDepth = v; }
    public boolean isCompletenessVerified() { return completenessVerified; }
    public void setCompletenessVerified(boolean v) { this.completenessVerified = v; }
    public String getCompletenessNotes() { return completenessNotes; }
    public void setCompletenessNotes(String v) { this.completenessNotes = v; }
}
