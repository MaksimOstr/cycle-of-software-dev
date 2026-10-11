package com.cycleofsoftwaredev.shared.infrastructure;

import com.cycleofsoftwaredev.shared.domain.Money;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

/**
 * Stores {@link Money} as one numeric column. The store works with one currency (UAH), so only the amount
 * is saved and the value object of the shared kernel stays free of persistence annotations.
 */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, BigDecimal> {

    @Override
    public BigDecimal convertToDatabaseColumn(Money money) {
        return money == null ? null : money.amount();
    }

    @Override
    public Money convertToEntityAttribute(BigDecimal amount) {
        return amount == null ? null : Money.of(amount);
    }
}
