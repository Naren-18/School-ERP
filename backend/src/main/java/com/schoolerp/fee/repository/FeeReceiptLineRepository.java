package com.schoolerp.fee.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.schoolerp.fee.entity.FeeReceiptLine;

/** Lines have no school_id; only query them by receipt ids already loaded for this school. */
public interface FeeReceiptLineRepository extends JpaRepository<FeeReceiptLine, Long> {

	List<FeeReceiptLine> findByReceiptIdIn(Collection<Long> receiptIds);

	boolean existsByStudentFeeDueIdIn(Collection<Long> dueIds);

}
