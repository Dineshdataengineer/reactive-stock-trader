package services.quote;

import lombok.NonNull;
import lombok.Value;

import java.math.BigDecimal;

@Value
public class ValuedHolding {
    @NonNull String symbol;

    int shareCount;
    BigDecimal marketValue; // Nullable
}
