package com.redelastic.stocktrader.portfolio.api;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
@JsonDeserialize(builder = OpenPortfolioRequest.OpenPortfolioRequestBuilder.class)
public class OpenPortfolioRequest {
    @NonNull String name;
}