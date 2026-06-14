package com.jpmc.midascore.application;

import com.jpmc.midascore.application.port.in.GetStatementUseCase.Statement;
import com.jpmc.midascore.application.port.out.StatementQueryPort;
import com.jpmc.midascore.application.service.StatementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatementServiceTest {

    @Test
    void maps_minor_units_to_decimal_amounts() {
        StatementQueryPort port = (accountId, limit) -> List.of(
                new StatementQueryPort.LedgerView("t1", -3000, "USD", Instant.now()),
                new StatementQueryPort.LedgerView("t2", 500, "USD", Instant.now()));
        StatementService service = new StatementService(port);

        Statement statement = service.statementFor(2L, 50);

        assertThat(statement.accountId()).isEqualTo(2L);
        assertThat(statement.entries()).hasSize(2);
        assertThat(statement.entries().get(0).amount()).isEqualByComparingTo(new BigDecimal("-30.00"));
        assertThat(statement.entries().get(1).amount()).isEqualByComparingTo(new BigDecimal("5.00"));
    }
}
