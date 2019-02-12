package com.redelastic.stocktrader.portfolio.impl;

import com.lightbend.lagom.serialization.Jsonable;
import com.redelastic.stocktrader.OrderId;
import com.redelastic.stocktrader.portfolio.api.LoyaltyLevel;
import com.redelastic.stocktrader.portfolio.impl.PortfolioEvent.*;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Wither;
import org.pcollections.HashTreePMap;
import org.pcollections.HashTreePSet;
import org.pcollections.PMap;
import org.pcollections.PSet;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.Optional;


/**
 * We'll encapsulate all the state transition logic here. Each state will provide an overloaded update method which
 * will accept applicable PortfolioEvents.
 */
public interface PortfolioState extends Jsonable {

    <T> T visit(Visitor<T> visitor);

    /**
     * State transition function. From a given state and event this produces the resulting state. The state transition
     * function allows us to replay history, either to reconstruct an entities state to recover it from it's journal,
     * or to support a projection (e.g. processing the journal to get a historical of the entity state.
     * @param evt event to transition on
     * @return The new state (null if there is no transition for the given state).
     */

    default Optional<PortfolioState> transition(PortfolioEvent evt) {
        try {
            Method method = getClass().getDeclaredMethod("update", evt.getClass());
            return Optional.of((Open)method.invoke(this, evt));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    BigDecimal getFunds();


    enum Closed implements PortfolioState {
        INSTANCE;

        @Override
        public <T> T visit(Visitor<T> visitor) {
            return visitor.visit(INSTANCE);
        }

        @Override
        public BigDecimal getFunds() {
            return BigDecimal.ZERO;
        }

        public PortfolioState update(PortfolioEvent evt) {
            return null;
        }
    }

    interface Visitor<T> {
        T visit(Open open);

        T visit(Liquidating liquidating);

        T visit(Closed closed);
    }



    @Value
    @Builder
    @Wither
    final class Open implements PortfolioState {
        @NonNull BigDecimal funds;
        @NonNull String name;
        @NonNull LoyaltyLevel loyaltyLevel;
        @NonNull Holdings holdings;
        @NonNull PMap<OrderId, OrderPlaced> activeOrders;
        @NonNull PSet<OrderId> completedOrders;

        public static Open initialState(String name) {
            return Open.builder()
                    .name(name)
                    .loyaltyLevel(LoyaltyLevel.BRONZE)
                    .funds(BigDecimal.valueOf(0))
                    .activeOrders(HashTreePMap.empty())
                    .holdings(Holdings.EMPTY)
                    .completedOrders(HashTreePSet.empty())
                    .build();
        }

        Open update(TransferReceived evt) {
            return this.withFunds(getFunds().add(evt.getAmount()));
        }

        Open update(TransferSent evt) {
            return this.withFunds(getFunds().subtract(evt.getAmount()));
        }

        Open update(SharesCredited evt) {
            return this.withHoldings(holdings.add(evt.getSymbol(), evt.getShares()));
        }

        Open update(SharesDebited evt) {
            return this.withHoldings(holdings.remove(evt.getSymbol(), evt.getShares()));
        }

        Open update(OrderPlaced evt) {
            return this.withActiveOrders(activeOrders.plus(evt.getOrderId(), evt));
        }

        Open update(SharesBought evt) {
            return this
                    .withHoldings(holdings.add(evt.getSymbol(), evt.getShares()))
                    .withActiveOrders(activeOrders.minus(evt.getOrderId()))
                    .withCompletedOrders(completedOrders.plus(evt.getOrderId()))
                    .withFunds(funds.subtract(evt.getSharePrice().multiply(BigDecimal.valueOf(evt.getShares()))));
        }

        Open update(SharesSold evt) {
            return this
                    .withHoldings(holdings.remove(evt.getSymbol(), evt.getShares()))
                    .withActiveOrders(activeOrders.minus(evt.getOrderId()))
                    .withCompletedOrders(completedOrders.plus(evt.getOrderId()))
                    .withFunds(funds.add(evt.getSharePrice().multiply(BigDecimal.valueOf(evt.getShares()))));
        }

        Open update(OrderFailed evt) {
            return this
                    .withActiveOrders(activeOrders.minus(evt.getOrderId()))
                    .withCompletedOrders(completedOrders.plus(evt.getOrderId()));
        }




        @Override
        public <T> T visit(Visitor<T> visitor) {
            return visitor.visit(this);
        }

    }

    @Value
    @Builder
    final class Liquidating implements PortfolioState {
        @NonNull BigDecimal funds;
        @NonNull String name;
        @NonNull LoyaltyLevel loyaltyLevel;
        @NonNull Holdings holdings;

        @Override
        public <T> T visit(Visitor<T> visitor) {
            return visitor.visit(this);
        }

    }

}