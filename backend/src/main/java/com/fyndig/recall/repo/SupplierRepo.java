package com.fyndig.recall.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fyndig.recall.entity.*;

import java.util.List;
import java.util.Optional;

public interface SupplierRepo extends JpaRepository<Supplier, Long> {
    Optional<Supplier> findByCanonicalCode(String canonicalCode);
}
