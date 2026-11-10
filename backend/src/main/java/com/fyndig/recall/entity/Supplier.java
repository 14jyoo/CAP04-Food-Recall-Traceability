package com.fyndig.recall.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "supplier", uniqueConstraints = @UniqueConstraint(columnNames = "canonical_code"))
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Canonical, normalised supplier code, e.g. SUP0001. All aliases map to this. */
    @Column(name = "canonical_code", nullable = false, length = 32)
    private String canonicalCode;

    @Column(nullable = false)
    private String name;

    private String country;
    private String contactEmail;

    public Long getId() { return id; }
    public String getCanonicalCode() { return canonicalCode; }
    public void setCanonicalCode(String canonicalCode) { this.canonicalCode = canonicalCode; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }
}
