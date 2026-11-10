package com.fyndig.recall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A lot as identified by the supplier on their CoA / delivery note.
 * Natural key: (supplier, supplier_lot_code normalised). Surrogate id is what the lineage graph references.
 */
@Entity
@Table(name = "supplier_lot",
        uniqueConstraints = @UniqueConstraint(columnNames = {"supplier_id", "lot_code_normalized"}))
public class SupplierLot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Supplier supplier;

    @Column(name = "lot_code_raw", nullable = false)
    private String lotCodeRaw;

    @Column(name = "lot_code_normalized", nullable = false)
    private String lotCodeNormalized;

    @Column(nullable = false)
    private String ingredientCode;

    private String ingredientName;

    @Column(precision = 18, scale = 3)
    private BigDecimal quantityReceived;

    private String uom;
    private LocalDate receivedDate;
    private LocalDate expiryDate;
    private String sourceSystem;
    private String sourceRecordId;

    public Long getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public String getLotCodeRaw() { return lotCodeRaw; }
    public void setLotCodeRaw(String lotCodeRaw) { this.lotCodeRaw = lotCodeRaw; }
    public String getLotCodeNormalized() { return lotCodeNormalized; }
    public void setLotCodeNormalized(String lotCodeNormalized) { this.lotCodeNormalized = lotCodeNormalized; }
    public String getIngredientCode() { return ingredientCode; }
    public void setIngredientCode(String ingredientCode) { this.ingredientCode = ingredientCode; }
    public String getIngredientName() { return ingredientName; }
    public void setIngredientName(String ingredientName) { this.ingredientName = ingredientName; }
    public BigDecimal getQuantityReceived() { return quantityReceived; }
    public void setQuantityReceived(BigDecimal quantityReceived) { this.quantityReceived = quantityReceived; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public LocalDate getReceivedDate() { return receivedDate; }
    public void setReceivedDate(LocalDate receivedDate) { this.receivedDate = receivedDate; }
    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
}
