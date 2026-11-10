package com.fyndig.recall.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

/** One affected order line for a committed recall, with the explainable path that reached it. */
@Entity
@Table(name = "recall_impact", indexes = @Index(name = "ix_impact_recall", columnList = "recall_id"))
public class RecallImpact {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "recall_id")
    private RecallEvent recall;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private OrderLine orderLine;

    private String finishedLotCode;
    private int depth;

    /** e.g. "SUP0001/AB-7781 -> ING-24-0101 -> WIP-24-0103 (split) -> FG-24-0210 -> ORD-1001/1" */
    @Column(length = 2000)
    private String lineagePath;

    public Long getId() { return id; }
    @JsonIgnore
    public RecallEvent getRecall() { return recall; }
    @JsonProperty("recallId")
    public Long getRecallId() { return recall == null ? null : recall.getId(); }
    @JsonProperty("recallRef")
    public String getRecallRef() { return recall == null ? null : recall.getReference(); }
    public void setRecall(RecallEvent recall) { this.recall = recall; }
    public OrderLine getOrderLine() { return orderLine; }
    public void setOrderLine(OrderLine orderLine) { this.orderLine = orderLine; }
    public String getFinishedLotCode() { return finishedLotCode; }
    public void setFinishedLotCode(String finishedLotCode) { this.finishedLotCode = finishedLotCode; }
    public int getDepth() { return depth; }
    public void setDepth(int depth) { this.depth = depth; }
    public String getLineagePath() { return lineagePath; }
    public void setLineagePath(String lineagePath) { this.lineagePath = lineagePath; }
}
