package com.fyndig.recall.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fyndig.recall.entity.*;

import java.util.List;
import java.util.Optional;

public interface UnmatchedRecordRepo extends JpaRepository<UnmatchedRecord, Long> {
    List<UnmatchedRecord> findByStatus(UnmatchedRecord.Status status);
    long countByStatus(UnmatchedRecord.Status status);
    List<UnmatchedRecord> findByStatusAndOffendingValue(UnmatchedRecord.Status status, String offendingValue);
}
