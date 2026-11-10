package com.fyndig.recall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A source row that could not be linked into the graph (unknown supplier code, unknown lot, etc.).
 * It is never silently dropped: it sits in the reconciliation queue and is counted against
 * every recall's completeness score until resolved.
 */
@Entity
@Table(name = "unmatched_record")
public class UnmatchedRecord {

    public enum Status { OPEN, RESOLVED, IGNORED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String sourceSystem;
    @Column(nullable = false)
    private String sourceRecordId;
    @Column(nullable = false)
    private String recordType;      // SUPPLIER_LOT, RECEIPT, CONSUMPTION, DISPATCH ...
    @Column(nullable = false)
    private String issue;           // UNKNOWN_SUPPLIER_CODE, UNKNOWN_INTERNAL_LOT, ...
    private String offendingValue;
    @Column(length = 4000)
    private String rawRow;          // the original row as JSON so it can be replayed after resolution

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OPEN;

    private String resolvedTo;      // canonical code / lot code it was mapped to
    private String resolvedBy;
    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime resolvedAt;

    public Long getId() { return id; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
    public String getRecordType() { return recordType; }
    public void setRecordType(String recordType) { this.recordType = recordType; }
    public String getIssue() { return issue; }
    public void setIssue(String issue) { this.issue = issue; }
    public String getOffendingValue() { return offendingValue; }
    public void setOffendingValue(String offendingValue) { this.offendingValue = offendingValue; }
    public String getRawRow() { return rawRow; }
    public void setRawRow(String rawRow) { this.rawRow = rawRow; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getResolvedTo() { return resolvedTo; }
    public void setResolvedTo(String resolvedTo) { this.resolvedTo = resolvedTo; }
    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
