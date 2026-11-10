package com.fyndig.recall.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** One dispatched line of a customer order. Natural key: (order_no, line_no). */
@Entity
@Table(name = "order_line", uniqueConstraints = @UniqueConstraint(columnNames = {"order_no", "line_no"}))
public class OrderLine {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false)
    private String orderNo;

    @Column(name = "line_no", nullable = false)
    private Integer lineNo;

    @Column(nullable = false)
    private String customerCode;
    private String customerName;
    private String customerEmail;
    private String skuCode;

    @Column(precision = 18, scale = 3)
    private BigDecimal quantity;
    private String uom;
    private LocalDate dispatchDate;
    private String shipToRegion;
    private String sourceSystem;
    private String sourceRecordId;

    public Long getId() { return id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Integer getLineNo() { return lineNo; }
    public void setLineNo(Integer lineNo) { this.lineNo = lineNo; }
    public String getCustomerCode() { return customerCode; }
    public void setCustomerCode(String customerCode) { this.customerCode = customerCode; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }
    public String getSkuCode() { return skuCode; }
    public void setSkuCode(String skuCode) { this.skuCode = skuCode; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
    public String getUom() { return uom; }
    public void setUom(String uom) { this.uom = uom; }
    public LocalDate getDispatchDate() { return dispatchDate; }
    public void setDispatchDate(LocalDate dispatchDate) { this.dispatchDate = dispatchDate; }
    public String getShipToRegion() { return shipToRegion; }
    public void setShipToRegion(String shipToRegion) { this.shipToRegion = shipToRegion; }
    public String getSourceSystem() { return sourceSystem; }
    public void setSourceSystem(String sourceSystem) { this.sourceSystem = sourceSystem; }
    public String getSourceRecordId() { return sourceRecordId; }
    public void setSourceRecordId(String sourceRecordId) { this.sourceRecordId = sourceRecordId; }
}
