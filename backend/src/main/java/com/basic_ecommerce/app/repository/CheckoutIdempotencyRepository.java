package com.basic_ecommerce.app.repository;

import com.basic_ecommerce.app.model.CheckoutIdempotency;
import com.basic_ecommerce.app.model.CheckoutIdempotencyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckoutIdempotencyRepository extends JpaRepository<CheckoutIdempotency, CheckoutIdempotencyId> {}
