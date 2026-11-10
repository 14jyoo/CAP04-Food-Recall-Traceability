package com.fyndig.recall.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fyndig.recall.entity.*;

import java.util.List;
import java.util.Optional;

public interface OrderLineRepo extends JpaRepository<OrderLine, Long> {
    Optional<OrderLine> findByOrderNoAndLineNo(String orderNo, Integer lineNo);
    List<OrderLine> findByOrderNo(String orderNo);
    List<OrderLine> findByCustomerCode(String customerCode);
}
