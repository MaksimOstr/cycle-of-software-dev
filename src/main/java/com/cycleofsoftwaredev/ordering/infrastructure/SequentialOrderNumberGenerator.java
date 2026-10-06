package com.cycleofsoftwaredev.ordering.infrastructure;

import com.cycleofsoftwaredev.ordering.domain.OrderNumberGenerator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/** Numbers like SS-261005-00001. Will be replaced by a database sequence together with PostgreSQL. */
@Component
public class SequentialOrderNumberGenerator implements OrderNumberGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyMMdd");

    private final AtomicLong counter = new AtomicLong();
    private final Clock clock;

    public SequentialOrderNumberGenerator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String nextNumber() {
        return "SS-" + LocalDate.now(clock).format(DATE) + "-" + String.format("%05d", counter.incrementAndGet());
    }
}
