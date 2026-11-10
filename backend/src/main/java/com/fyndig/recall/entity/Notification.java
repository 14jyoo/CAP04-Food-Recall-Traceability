package com.fyndig.recall.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/** One notification per affected customer per recall. */
@Entity
@Table(name = "notification", indexes = @Index(name = "ix_notif_recall", columnList = "recall_id"))
public class Notification {

    public enum Status { PENDING, SENT, ACKNOWLEDGED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recall_id")
    private RecallEvent recall;

    @Column(nullable = false)
    private String customerCode;
    private String customerName;
    private String customerEmail;
    private String channel = "EMAIL";
    private int orderLinesAffected;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PENDING;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime sentAt;
    private LocalDateTime acknowledgedAt;
    @Column(length = 1000)
    private String note;

    public Long getId() { return id; }
    @JsonIgnore
    public RecallEvent getRecall() { return recall; }
    @JsonProperty("recallId")
    public Long getRecallId() { return recall == null ? null : recall.getId(); }
    @JsonProperty("recallRef")
    public String getRecallRef() { return recall == null ? null : recall.getReference(); }
    public void setRecall(RecallEvent recall) { this.recall = recall; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public int getOrderLinesAffected() { return orderLinesAffected; }
    public void setOrderLinesAffected(int orderLinesAffected) { this.orderLinesAffected = orderLinesAffected; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
