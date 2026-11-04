package com.basic_ecommerce.app.controller;

import com.basic_ecommerce.app.dto.OrderStatusResponse;
import com.basic_ecommerce.app.service.OrderStatusService;
import com.basic_ecommerce.app.security.AuthAttributes;
import java.util.UUID;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
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
    public OrderStatusResponse byReference(@PathVariable String referenceId,
            @RequestAttribute(AuthAttributes.USER_ID) UUID userId) {
        return orderStatusService.byReference(referenceId, userId);
    }

    @GetMapping
    public List<OrderStatusResponse> list(@RequestAttribute(AuthAttributes.USER_ID) UUID userId) {
        return orderStatusService.listForUser(userId);
    }
}
