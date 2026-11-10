package com.fyndig.recall.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Audit of every file ingested: what came in, what was accepted, rejected or parked for reconciliation. */
@Entity
@Table(name = "ingestion_run")
public class IngestionRun {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String feedType;       // SUPPLIER_LOTS, RECEIPTS, CONSUMPTION, DISPATCHES, ORDERS
    @Column(nullable = false)
    private String sourceSystem;
    private String fileName;
    private LocalDateTime startedAt = LocalDateTime.now();
    private LocalDateTime finishedAt;
    private int rowsRead;
    private int rowsAccepted;
    private int rowsRejected;
    private int rowsUnmatched;
    private int rowsDuplicate;
    @Column(length = 8000)
    private String validationReport;   // JSON array of row-level messages

    public Long getId() { return id; }
    public String getFeedType() { return feedType; }
    public void setFeedType(String feedType) { this.feedType = feedType; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime finishedAt) { this.finishedAt = finishedAt; }
    public int getRowsRead() { return rowsRead; }
    public void setRowsRead(int rowsRead) { this.rowsRead = rowsRead; }
    public int getRowsAccepted() { return rowsAccepted; }
    public void setRowsAccepted(int rowsAccepted) { this.rowsAccepted = rowsAccepted; }
    public int getRowsRejected() { return rowsRejected; }
    public void setRowsRejected(int rowsRejected) { this.rowsRejected = rowsRejected; }
    public int getRowsUnmatched() { return rowsUnmatched; }
    public void setRowsUnmatched(int rowsUnmatched) { this.rowsUnmatched = rowsUnmatched; }
    public int getRowsDuplicate() { return rowsDuplicate; }
    public void setRowsDuplicate(int rowsDuplicate) { this.rowsDuplicate = rowsDuplicate; }
    public String getValidationReport() { return validationReport; }
    public void setValidationReport(String validationReport) { this.validationReport = validationReport; }
}
