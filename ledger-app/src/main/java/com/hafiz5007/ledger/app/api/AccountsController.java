package com.hafiz5007.ledger.app.api;

import com.hafiz5007.ledger.app.api.dto.AccountResponse;
import com.hafiz5007.ledger.app.api.dto.BalanceResponse;
import com.hafiz5007.ledger.app.api.dto.CreateAccountRequest;
import com.hafiz5007.ledger.application.CreateAccountCommand;
import com.hafiz5007.ledger.application.CreateAccountUseCase;
import com.hafiz5007.ledger.application.GetAccountBalanceUseCase;
import com.hafiz5007.ledger.domain.model.AccountId;
import com.hafiz5007.ledger.domain.model.AccountType;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Currency;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountsController {

    private final CreateAccountUseCase createAccount;
    private final GetAccountBalanceUseCase getBalance;

    public AccountsController(CreateAccountUseCase createAccount, GetAccountBalanceUseCase getBalance) {
        this.createAccount = createAccount;
        this.getBalance = getBalance;
    }

    @PostMapping
    @Transactional     // account write + outbox insert in one transaction
    public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
        var command = new CreateAccountCommand(
            request.name(),
            AccountType.valueOf(request.type().name()),
            Currency.getInstance(request.currency())
        );
        var account = createAccount.execute(command);
        var body = AccountResponse.from(account);
        return ResponseEntity
            .created(URI.create("/api/v1/accounts/" + account.id()))
            .body(body);
    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<BalanceResponse> balance(@PathVariable String id) {
        var accountId = new AccountId(UUID.fromString(id));
        return getBalance.execute(accountId)
            .map(BalanceResponse::from)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
}
