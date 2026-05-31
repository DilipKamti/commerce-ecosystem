package io.commerce.order_service.service;

import io.commerce.order_service.client.ProductClient;
import io.commerce.order_service.dto.*;
import io.commerce.order_service.entity.Order;
import io.commerce.order_service.entity.OrderItem;
import io.commerce.order_service.entity.OrderStatus;
import io.commerce.order_service.exception.BadRequestException;
import io.commerce.order_service.exception.ResourceNotFoundException;
import io.commerce.order_service.exception.ServiceUnavailableException;
import io.commerce.order_service.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    @Transactional
    public OrderResponse createOrder(UUID userId, CreateOrderRequest request) {

        log.info("Order creation initiated. UserId={}, ItemCount={}", userId, request.getItems().size());

        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {

            log.debug("Fetching product details. ProductId={}, Quantity={}", itemRequest.getProductId(), itemRequest.getQuantity());

            ProductApiResponse apiResponse = productClient.getProductById(itemRequest.getProductId());

            if (apiResponse == null || apiResponse.getData() == null) {

                log.warn("Product validation failed. ProductId={}", itemRequest.getProductId());

                throw new ServiceUnavailableException("Product not found or service unavailable: " + itemRequest.getProductId());
            }

            ProductResponse product = apiResponse.getData();

            BigDecimal subtotal = product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity()));

            totalAmount = totalAmount.add(subtotal);

            log.debug("Calculated subtotal. ProductId={}, Subtotal={}", product.getId(), subtotal);

            orderItems.add(OrderItem.builder().productId(product.getId()).productName(product.getName()).unitPrice(product.getPrice()).quantity(itemRequest.getQuantity()).build());
        }

        Order order = Order.builder().userId(userId).status(OrderStatus.PENDING).totalAmount(totalAmount).active(true).build();

        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }

        order.setItems(orderItems);

        Order savedOrder = orderRepository.save(order);

        log.info("Order created successfully. OrderId={}, UserId={}, TotalAmount={}", savedOrder.getId(), userId, savedOrder.getTotalAmount());

        return mapToResponse(savedOrder);
    }

    public OrderResponse getOrderById(UUID userId, UUID orderId) {

        log.debug("Fetching order. OrderId={}, UserId={}", orderId, userId);

        Order order = orderRepository.findByIdAndActiveTrue(orderId).orElseThrow(() -> {
            log.warn("Order not found. OrderId={}", orderId);

            return new ResourceNotFoundException("Order not found: " + orderId);
        });

        if (!order.getUserId().equals(userId)) {

            log.warn("Unauthorized order access attempt. OrderId={}, UserId={}", orderId, userId);

            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        log.debug("Order retrieved successfully. OrderId={}, UserId={}", orderId, userId);

        return mapToResponse(order);
    }

    public Page<OrderResponse> getMyOrders(UUID userId, Pageable pageable) {

        log.info("Fetching orders. UserId={}, Page={}, Size={}", userId, pageable.getPageNumber(), pageable.getPageSize());

        Page<OrderResponse> orders = orderRepository.findAllByUserIdAndActiveTrue(userId, pageable).map(this::mapToResponse);

        log.info("Orders retrieved successfully. UserId={}, TotalOrders={}", userId, orders.getTotalElements());

        return orders;
    }

    @Transactional
    public OrderResponse cancelOrder(UUID userId, UUID orderId) {

        log.info("Order cancellation initiated. OrderId={}, UserId={}", orderId, userId);

        Order order = orderRepository.findByIdAndActiveTrue(orderId).orElseThrow(() -> {
            log.warn("Order cancellation failed. Order not found. OrderId={}", orderId);

            return new ResourceNotFoundException("Order not found: " + orderId);
        });

        if (!order.getUserId().equals(userId)) {

            log.warn("Unauthorized order cancellation attempt. OrderId={}, UserId={}", orderId, userId);

            throw new ResourceNotFoundException("Order not found: " + orderId);
        }

        if (order.getStatus() != OrderStatus.PENDING) {

            log.warn("Order cancellation failed. Invalid status. OrderId={}, Status={}", orderId, order.getStatus());

            throw new BadRequestException("Only PENDING orders can be cancelled");
        }

        order.setStatus(OrderStatus.CANCELLED);

        Order updatedOrder = orderRepository.save(order);

        log.info("Order cancelled successfully. OrderId={}, UserId={}", orderId, userId);

        return mapToResponse(updatedOrder);
    }

    private OrderResponse mapToResponse(Order order) {

        List<OrderItemResponse> itemResponses = order.getItems().stream().map(item -> OrderItemResponse.builder().id(item.getId()).productId(item.getProductId()).productName(item.getProductName()).unitPrice(item.getUnitPrice()).quantity(item.getQuantity()).subtotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))).build()).toList();

        return OrderResponse.builder().id(order.getId()).userId(order.getUserId()).status(order.getStatus()).totalAmount(order.getTotalAmount()).items(itemResponses).createdAt(order.getCreatedAt()).build();
    }
}