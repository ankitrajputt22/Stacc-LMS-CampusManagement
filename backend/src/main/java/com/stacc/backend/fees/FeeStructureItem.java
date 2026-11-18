package com.stacc.backend.fees;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "fee_structure_items")
public class FeeStructureItem {

    public enum Head { TUITION, EXAMINATION, LIBRARY, LABORATORY, HOSTEL, MESS, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fee_structure_id", nullable = false, updatable = false)
    private FeeStructure feeStructure;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20, updatable = false)
    private Head head;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected FeeStructureItem() {
    }

    public FeeStructureItem(FeeStructure feeStructure, Head head, BigDecimal amount) {
        this.feeStructure = feeStructure;
        this.head = head;
        this.amount = amount;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public FeeStructure getFeeStructure() {
        return feeStructure;
    }

    public Head getHead() {
        return head;
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
