package com.fyndig.recall.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fyndig.recall.entity.*;

import java.util.List;
import java.util.Optional;

public interface InternalLotRepo extends JpaRepository<InternalLot, Long> {
    Optional<InternalLot> findByLotCode(String lotCode);
    List<InternalLot> findByKind(InternalLot.Kind kind);

    @Query("select l from InternalLot l where upper(l.lotCode) like upper(concat('%', :q, '%')) " +
           "or upper(l.materialCode) like upper(concat('%', :q, '%'))")
    List<InternalLot> search(@Param("q") String q);
}
