package com.tdea.mcp_server_tdea.repository;

import com.tdea.mcp_server_tdea.model.CreditRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditRequestRepository extends JpaRepository<CreditRequest, Long> {
    List<CreditRequest> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
