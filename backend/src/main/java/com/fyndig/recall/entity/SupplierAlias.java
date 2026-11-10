package com.fyndig.recall.entity;

import jakarta.persistence.*;

/**
 * Raw supplier codes seen in source systems ("sup-1", "Supplier 001", "S0001") mapped to one canonical supplier.
 * Reconciliation adds rows here; ingestion consults it before declaring a code UNMATCHED.
 */
@Entity
@Table(name = "supplier_alias", uniqueConstraints = @UniqueConstraint(columnNames = "alias_normalized"))
public class SupplierAlias {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "alias_raw", nullable = false)
    private String aliasRaw;

    @Column(name = "alias_normalized", nullable = false)
    private String aliasNormalized;

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    private Supplier supplier;

    /** AUTO (matched by normalisation rule) or MANUAL (resolved by a human in the reconciliation screen). */
    @Column(nullable = false, length = 16)
    private String resolutionType = "AUTO";

    public Long getId() { return id; }
    public String getAliasRaw() { return aliasRaw; }
    public void setAliasRaw(String aliasRaw) { this.aliasRaw = aliasRaw; }
    public String getAliasNormalized() { return aliasNormalized; }
    public void setAliasNormalized(String aliasNormalized) { this.aliasNormalized = aliasNormalized; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier supplier) { this.supplier = supplier; }
    public String getResolutionType() { return resolutionType; }
    public void setResolutionType(String resolutionType) { this.resolutionType = resolutionType; }
}
