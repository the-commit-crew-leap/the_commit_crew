package com.thecommitcrew.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Component;

import com.thecommitcrew.domain.model.Position;
import com.thecommitcrew.domain.model.Order;
import com.thecommitcrew.domain.enums.OrderSide;
import com.thecommitcrew.domain.exception.NegativePriceException;

@Component 
public class PositionService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final long ZERO_QUANTITY = 0L;
    
    public Position applyOrder(Position position, Order order) {
        long orderQuantity = order.getQuantity();
        long currentQuantity = position.getQuantity();
        long newQuantity = calculateNewQuantity(currentQuantity, orderQuantity, order.getSide());
        
        if (newQuantity < ZERO_QUANTITY) {
            throw new IllegalArgumentException("Cannot sell more shares than owned");
        }
        
        BigDecimal newAverageCost = calculateUpdatedCostBasis(position, order, newQuantity);
        
        return new Position(position.getAccountId(), order.getSymbol(), newQuantity, newAverageCost);
    }
    
    private long calculateNewQuantity(long currentQuantity, long orderQuantity, OrderSide side) {
        return side.apply(currentQuantity, orderQuantity);
    }
    
    private BigDecimal calculateUpdatedCostBasis(Position position, Order order, long newQuantity) {
        
        if (newQuantity == ZERO_QUANTITY) {
            return ZERO;
        }
        
        if (order.getSide() == OrderSide.SELL) {
            return position.getAverageCost();
        }

        BigDecimal costBasis = calculateCostBasis(position);
        BigDecimal tradeTotal = new BigDecimal(order.getQuantity()).multiply(order.getPrice());
        BigDecimal newTotal = costBasis.add(tradeTotal);
        BigDecimal averageCost = newTotal.divide(new BigDecimal(newQuantity), RoundingMode.HALF_UP);

        return averageCost;
    }

    public BigDecimal marketValue(Position position, BigDecimal currentPrice) throws NegativePriceException {
        if (currentPrice.compareTo(ZERO) < 0) {
            throw new NegativePriceException("Current price of asset cannot be negative.");
        }
        return new BigDecimal(position.getQuantity()).multiply(currentPrice);
    }

    public BigDecimal unrealizedProfitLoss(Position position, BigDecimal currentPrice) throws NegativePriceException {
        BigDecimal costBasis = calculateCostBasis(position);
        BigDecimal profitLoss = marketValue(position, currentPrice).subtract(costBasis);
        return profitLoss;
    }

    public BigDecimal calculateCostBasis(Position position) {
        return new BigDecimal(position.getQuantity()).multiply(position.getAverageCost());
    }
}