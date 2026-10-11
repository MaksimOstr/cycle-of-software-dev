package com.cycleofsoftwaredev.ordering.infrastructure;

import com.cycleofsoftwaredev.ordering.domain.OrderNumberGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Numbers like SS-261005-00001. The counter is a PostgreSQL sequence, so the numbers stay unique after
 * a restart and when several instances of the application work with one database.
 */
@Component
public class DatabaseOrderNumberGenerator implements OrderNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final JdbcClient jdbc;
    private final Clock clock;

    public DatabaseOrderNumberGenerator(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public String nextNumber() {
        long next = jdbc.sql("SELECT nextval('ordering.order_number_seq')").query(Long.class).single();
        return "SS-" + LocalDate.now(clock).format(DATE) + "-" + String.format("%05d", next);
    }
}
