package com.fyndig.recall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Any lot the plant itself tracks: a received ingredient lot, a WIP intermediate, a split/merge child,
 * or a finished-goods production batch (then skuCode is set).
 * Natural key: internal lot code (plant-wide unique).
 */
@Entity
@Table(name = "internal_lot", uniqueConstraints = @UniqueConstraint(columnNames = "lot_code"))
public class InternalLot {

    public enum Kind { INGREDIENT, WIP, FINISHED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lot_code", nullable = false)
    private String lotCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    /** Ingredient code for INGREDIENT/WIP lots, SKU code for FINISHED batches. */
    private String materialCode;
    private String materialName;

    @Column(precision = 18, scale = 3)
    private BigDecimal quantity;
    private String uom;

    private LocalDate producedDate;
    private String productionLine;
    private String sourceSystem;
    private String sourceRecordId;

    public Long getId() { return id; }
    public String getLotCode() { return lotCode; }
    public void setLotCode(String lotCode) { this.lotCode = lotCode; }
    public Kind getKind() { return kind; }
    public void setKind(Kind kind) { this.kind = kind; }
    public String getMaterialCode() { return materialCode; }
    public void setMaterialCode(String materialCode) { this.materialCode = materialCode; }
    public String getMaterialName() { return materialName; }
    public void setMaterialName(String materialName) { this.materialName = materialName; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public LocalDate getProducedDate() { return producedDate; }
    public void setProducedDate(LocalDate producedDate) { this.producedDate = producedDate; }
    public String getProductionLine() { return productionLine; }
    public void setProductionLine(String productionLine) { this.productionLine = productionLine; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
}
