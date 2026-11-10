package com.fyndig.recall.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fyndig.recall.entity.*;

import java.util.List;
import java.util.Optional;

public interface RecallEventRepo extends JpaRepository<RecallEvent, Long> {
    List<RecallEvent> findAllByOrderByCreatedAtDesc();
    List<RecallEvent> findBySupplierLotSupplierId(Long supplierId);
}
