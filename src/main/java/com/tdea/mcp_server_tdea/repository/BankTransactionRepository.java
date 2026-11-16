package com.tdea.mcp_server_tdea.repository;

import com.tdea.mcp_server_tdea.model.BankTransaction;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository extends JpaRepository<BankTransaction, Long> {
    List<BankTransaction> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
}
