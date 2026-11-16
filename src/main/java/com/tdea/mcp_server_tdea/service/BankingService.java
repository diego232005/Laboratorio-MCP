package com.tdea.mcp_server_tdea.service;

import com.tdea.mcp_server_tdea.model.Account;
import com.tdea.mcp_server_tdea.model.BankTransaction;
import com.tdea.mcp_server_tdea.model.CreditRequest;
import com.tdea.mcp_server_tdea.repository.AccountRepository;
import com.tdea.mcp_server_tdea.repository.BankTransactionRepository;
import com.tdea.mcp_server_tdea.repository.CreditRequestRepository;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankingService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final CreditRequestRepository creditRequestRepository;
    private final AtomicLong accountSeq = new AtomicLong(1000);

    public BankingService(AccountRepository accountRepository,
                          BankTransactionRepository transactionRepository,
                          CreditRequestRepository creditRequestRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.creditRequestRepository = creditRequestRepository;
    }

    @Tool(
            name = "createAccount",
            description = "Crea una cuenta bancaria. Parametros: ownerName (titular), accountType (AHORROS o CORRIENTE), initialBalance (saldo inicial >= 0)."
    )
    @Transactional
    public String createAccount(String ownerName, String accountType, double initialBalance) {
        if (ownerName == null || ownerName.trim().isEmpty()) {
            return "Error: el nombre del titular es obligatorio.";
        }
        if (initialBalance < 0) {
            return "Error: el saldo inicial no puede ser negativo.";
        }
        String type = normalizeAccountType(accountType);
        if (type == null) {
            return "Error: accountType debe ser AHORROS o CORRIENTE.";
        }

        String accountNumber = "ACC-" + accountSeq.incrementAndGet();
        while (accountRepository.findByAccountNumber(accountNumber).isPresent()) {
            accountNumber = "ACC-" + accountSeq.incrementAndGet();
        }

        Account account = new Account(accountNumber, ownerName.trim(), type, initialBalance);
        accountRepository.save(account);

        if (initialBalance > 0) {
            transactionRepository.save(new BankTransaction(
                    accountNumber, null, "DEPOSIT", initialBalance, "Saldo inicial"));
        }

        return "Cuenta creada: " + accountNumber
                + " | Titular: " + account.getOwnerName()
                + " | Tipo: " + account.getAccountType()
                + " | Saldo: " + formatMoney(account.getBalance());
    }

    @Tool(
            name = "getAccount",
            description = "Consulta una cuenta por su numero (accountNumber, ej. ACC-1001) y devuelve titular, tipo, saldo y estado."
    )
    @Transactional(readOnly = true)
    public String getAccount(String accountNumber) {
        Optional<Account> opt = findAccount(accountNumber);
        if (opt.isEmpty()) {
            return "Error: cuenta '" + accountNumber + "' no encontrada.";
        }
        Account a = opt.get();
        return "Cuenta " + a.getAccountNumber()
                + " | Titular: " + a.getOwnerName()
                + " | Tipo: " + a.getAccountType()
                + " | Saldo: " + formatMoney(a.getBalance())
                + " | Estado: " + a.getStatus();
    }

    @Tool(
            name = "listAccounts",
            description = "Lista todas las cuentas bancarias con su numero, titular, tipo y saldo."
    )
    @Transactional(readOnly = true)
    public String listAccounts() {
        List<Account> accounts = accountRepository.findAll();
        if (accounts.isEmpty()) {
            return "No hay cuentas registradas.";
        }
        return accounts.stream()
                .map(a -> a.getAccountNumber() + " | " + a.getOwnerName()
                        + " | " + a.getAccountType() + " | " + formatMoney(a.getBalance()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(
            name = "deposit",
            description = "Realiza una consignacion (deposito) en una cuenta. Parametros: accountNumber, amount (>0), description (opcional)."
    )
    @Transactional
    public String deposit(String accountNumber, double amount, String description) {
        if (amount <= 0) {
            return "Error: el monto debe ser mayor a 0.";
        }
        Optional<Account> opt = findAccount(accountNumber);
        if (opt.isEmpty()) {
            return "Error: cuenta '" + accountNumber + "' no encontrada.";
        }
        Account account = opt.get();
        if (!"ACTIVE".equals(account.getStatus())) {
            return "Error: la cuenta no esta activa.";
        }

        account.setBalance(account.getBalance() + amount);
        accountRepository.save(account);
        transactionRepository.save(new BankTransaction(
                account.getAccountNumber(), null, "DEPOSIT", amount,
                description == null || description.isBlank() ? "Consignacion" : description.trim()));

        return "Consignacion exitosa de " + formatMoney(amount)
                + " en " + account.getAccountNumber()
                + ". Nuevo saldo: " + formatMoney(account.getBalance());
    }

    @Tool(
            name = "withdraw",
            description = "Retira dinero de una cuenta. Parametros: accountNumber, amount (>0), description (opcional)."
    )
    @Transactional
    public String withdraw(String accountNumber, double amount, String description) {
        if (amount <= 0) {
            return "Error: el monto debe ser mayor a 0.";
        }
        Optional<Account> opt = findAccount(accountNumber);
        if (opt.isEmpty()) {
            return "Error: cuenta '" + accountNumber + "' no encontrada.";
        }
        Account account = opt.get();
        if (!"ACTIVE".equals(account.getStatus())) {
            return "Error: la cuenta no esta activa.";
        }
        if (account.getBalance() < amount) {
            return "Error: saldo insuficiente. Saldo actual: " + formatMoney(account.getBalance());
        }

        account.setBalance(account.getBalance() - amount);
        accountRepository.save(account);
        transactionRepository.save(new BankTransaction(
                account.getAccountNumber(), null, "WITHDRAW", amount,
                description == null || description.isBlank() ? "Retiro" : description.trim()));

        return "Retiro exitoso de " + formatMoney(amount)
                + " de " + account.getAccountNumber()
                + ". Nuevo saldo: " + formatMoney(account.getBalance());
    }

    @Tool(
            name = "transfer",
            description = "Transfiere dinero entre cuentas. Parametros: fromAccount, toAccount, amount (>0), description (opcional)."
    )
    @Transactional
    public String transfer(String fromAccount, String toAccount, double amount, String description) {
        if (amount <= 0) {
            return "Error: el monto debe ser mayor a 0.";
        }
        if (fromAccount == null || toAccount == null
                || fromAccount.trim().equalsIgnoreCase(toAccount.trim())) {
            return "Error: las cuentas origen y destino deben ser diferentes.";
        }

        Optional<Account> fromOpt = findAccount(fromAccount);
        Optional<Account> toOpt = findAccount(toAccount);
        if (fromOpt.isEmpty()) {
            return "Error: cuenta origen '" + fromAccount + "' no encontrada.";
        }
        if (toOpt.isEmpty()) {
            return "Error: cuenta destino '" + toAccount + "' no encontrada.";
        }

        Account from = fromOpt.get();
        Account to = toOpt.get();
        if (!"ACTIVE".equals(from.getStatus()) || !"ACTIVE".equals(to.getStatus())) {
            return "Error: ambas cuentas deben estar activas.";
        }
        if (from.getBalance() < amount) {
            return "Error: saldo insuficiente en origen. Saldo: " + formatMoney(from.getBalance());
        }

        String desc = description == null || description.isBlank()
                ? "Transferencia" : description.trim();

        from.setBalance(from.getBalance() - amount);
        to.setBalance(to.getBalance() + amount);
        accountRepository.save(from);
        accountRepository.save(to);

        transactionRepository.save(new BankTransaction(
                from.getAccountNumber(), to.getAccountNumber(), "TRANSFER_OUT", amount, desc));
        transactionRepository.save(new BankTransaction(
                to.getAccountNumber(), from.getAccountNumber(), "TRANSFER_IN", amount, desc));

        return "Transferencia exitosa de " + formatMoney(amount)
                + " desde " + from.getAccountNumber()
                + " hacia " + to.getAccountNumber()
                + ". Saldo origen: " + formatMoney(from.getBalance())
                + " | Saldo destino: " + formatMoney(to.getBalance());
    }

    @Tool(
            name = "createCreditRequest",
            description = "Crea una solicitud de credito. Parametros: accountNumber, amount (>0), termMonths (>=1), purpose (motivo)."
    )
    @Transactional
    public String createCreditRequest(String accountNumber, double amount, int termMonths, String purpose) {
        if (amount <= 0) {
            return "Error: el monto del credito debe ser mayor a 0.";
        }
        if (termMonths < 1) {
            return "Error: el plazo en meses debe ser al menos 1.";
        }
        Optional<Account> opt = findAccount(accountNumber);
        if (opt.isEmpty()) {
            return "Error: cuenta '" + accountNumber + "' no encontrada.";
        }

        CreditRequest request = new CreditRequest(
                opt.get().getAccountNumber(),
                amount,
                termMonths,
                purpose == null ? "" : purpose.trim());
        creditRequestRepository.save(request);

        return "Solicitud de credito #" + request.getId()
                + " creada para " + request.getAccountNumber()
                + " | Monto: " + formatMoney(request.getAmount())
                + " | Plazo: " + request.getTermMonths() + " meses"
                + " | Estado: " + request.getStatus();
    }

    @Tool(
            name = "approveCreditRequest",
            description = "Aprueba una solicitud de credito pendiente y deposita el monto en la cuenta. Parametro: requestId."
    )
    @Transactional
    public String approveCreditRequest(long requestId) {
        Optional<CreditRequest> opt = creditRequestRepository.findById(requestId);
        if (opt.isEmpty()) {
            return "Error: solicitud #" + requestId + " no encontrada.";
        }
        CreditRequest request = opt.get();
        if (!"PENDING".equals(request.getStatus())) {
            return "Error: la solicitud #" + requestId + " ya esta en estado " + request.getStatus();
        }

        Optional<Account> accountOpt = findAccount(request.getAccountNumber());
        if (accountOpt.isEmpty()) {
            return "Error: cuenta asociada no encontrada.";
        }

        Account account = accountOpt.get();
        account.setBalance(account.getBalance() + request.getAmount());
        accountRepository.save(account);

        request.setStatus("APPROVED");
        creditRequestRepository.save(request);

        transactionRepository.save(new BankTransaction(
                account.getAccountNumber(), null, "CREDIT_DISBURSEMENT",
                request.getAmount(), "Desembolso credito #" + request.getId()));

        return "Credito #" + request.getId() + " aprobado. Se consignaron "
                + formatMoney(request.getAmount()) + " en " + account.getAccountNumber()
                + ". Nuevo saldo: " + formatMoney(account.getBalance());
    }

    @Tool(
            name = "rejectCreditRequest",
            description = "Rechaza una solicitud de credito pendiente. Parametro: requestId."
    )
    @Transactional
    public String rejectCreditRequest(long requestId) {
        Optional<CreditRequest> opt = creditRequestRepository.findById(requestId);
        if (opt.isEmpty()) {
            return "Error: solicitud #" + requestId + " no encontrada.";
        }
        CreditRequest request = opt.get();
        if (!"PENDING".equals(request.getStatus())) {
            return "Error: la solicitud #" + requestId + " ya esta en estado " + request.getStatus();
        }
        request.setStatus("REJECTED");
        creditRequestRepository.save(request);
        return "Solicitud de credito #" + request.getId() + " rechazada.";
    }

    @Tool(
            name = "listCreditRequests",
            description = "Lista solicitudes de credito. Si accountNumber esta vacio, lista todas; si no, filtra por cuenta."
    )
    @Transactional(readOnly = true)
    public String listCreditRequests(String accountNumber) {
        List<CreditRequest> requests;
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            requests = creditRequestRepository.findAll();
        } else {
            requests = creditRequestRepository.findByAccountNumberOrderByCreatedAtDesc(
                    accountNumber.trim().toUpperCase());
        }
        if (requests.isEmpty()) {
            return "No hay solicitudes de credito.";
        }
        return requests.stream()
                .map(r -> "#" + r.getId() + " | " + r.getAccountNumber()
                        + " | " + formatMoney(r.getAmount())
                        + " | " + r.getTermMonths() + " meses"
                        + " | " + r.getStatus()
                        + " | " + (r.getPurpose() == null ? "" : r.getPurpose()))
                .collect(Collectors.joining("\n"));
    }

    @Tool(
            name = "getTransactionHistory",
            description = "Obtiene el historial de transacciones de una cuenta (accountNumber)."
    )
    @Transactional(readOnly = true)
    public String getTransactionHistory(String accountNumber) {
        Optional<Account> opt = findAccount(accountNumber);
        if (opt.isEmpty()) {
            return "Error: cuenta '" + accountNumber + "' no encontrada.";
        }
        List<BankTransaction> txs = transactionRepository
                .findByAccountNumberOrderByCreatedAtDesc(opt.get().getAccountNumber());
        if (txs.isEmpty()) {
            return "La cuenta " + opt.get().getAccountNumber() + " no tiene transacciones.";
        }
        return txs.stream()
                .map(t -> t.getCreatedAt() + " | " + t.getType()
                        + " | " + formatMoney(t.getAmount())
                        + (t.getRelatedAccountNumber() != null
                        ? " | ref: " + t.getRelatedAccountNumber() : "")
                        + " | " + (t.getDescription() == null ? "" : t.getDescription()))
                .collect(Collectors.joining("\n"));
    }

    private Optional<Account> findAccount(String accountNumber) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            return Optional.empty();
        }
        return accountRepository.findByAccountNumber(accountNumber.trim().toUpperCase());
    }

    private String normalizeAccountType(String accountType) {
        if (accountType == null) {
            return null;
        }
        String t = accountType.trim().toUpperCase();
        if (t.equals("AHORROS") || t.equals("SAVINGS")) {
            return "AHORROS";
        }
        if (t.equals("CORRIENTE") || t.equals("CHECKING")) {
            return "CORRIENTE";
        }
        return null;
    }

    private String formatMoney(double amount) {
        return String.format("$%,.2f", amount);
    }
}
