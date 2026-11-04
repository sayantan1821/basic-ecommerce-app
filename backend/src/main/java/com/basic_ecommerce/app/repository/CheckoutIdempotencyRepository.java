package com.basic_ecommerce.app.repository;

import com.basic_ecommerce.app.model.CheckoutIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, String> {}
