package com.fyndig.recall.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fyndig.recall.entity.*;

import java.util.List;
import java.util.Optional;

public interface SupplierLotRepo extends JpaRepository<SupplierLot, Long> {
    Optional<SupplierLot> findBySupplierIdAndLotCodeNormalized(Long supplierId, String lotCodeNormalized);
    List<SupplierLot> findBySupplierId(Long supplierId);

    @Query("select l from SupplierLot l where upper(l.lotCodeRaw) like upper(concat('%', :q, '%')) " +
           "or upper(l.ingredientCode) like upper(concat('%', :q, '%')) " +
           "or upper(l.ingredientName) like upper(concat('%', :q, '%')) " +
           "or upper(l.supplier.canonicalCode) like upper(concat('%', :q, '%')) " +
           "or upper(l.supplier.name) like upper(concat('%', :q, '%'))")
    List<SupplierLot> search(@Param("q") String q);
}
