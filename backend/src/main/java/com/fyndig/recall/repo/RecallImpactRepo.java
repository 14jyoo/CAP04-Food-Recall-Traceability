package com.fyndig.recall.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fyndig.recall.entity.*;

import java.util.List;
import java.util.Optional;

public interface RecallImpactRepo extends JpaRepository<RecallImpact, Long> {
    List<RecallImpact> findByRecall_Id(Long recallId);
    List<RecallImpact> findByOrderLineOrderNo(String orderNo);
    List<RecallImpact> findByOrderLineCustomerCode(String customerCode);
    void deleteByRecall_Id(Long recallId);
}
