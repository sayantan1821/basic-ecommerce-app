package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.OrderStatusResponse;
import com.basic_ecommerce.app.service.OrderStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderStatusController {
    private final OrderStatusService orderStatusService;

    public OrderStatusController(OrderStatusService orderStatusService) {
        this.orderStatusService = orderStatusService;
    }

    @GetMapping("/by-reference/{referenceId}")
    public OrderStatusResponse byReference(@PathVariable String referenceId) {
        return orderStatusService.byReference(referenceId);
    }
}
