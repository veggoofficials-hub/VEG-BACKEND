package com.veggofresh.payment.repository;

import com.veggofresh.payment.entity.PaymentOrder;
import com.veggofresh.payment.entity.PaymentOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    Optional<PaymentOrder> findByRazorpayOrderId(String razorpayOrderId);

    /**
     * Does this customer have a payment of this status that was started after {@code after}? Used to tell that an
     * online checkout is still waiting for its payment, in which case the customer's carts must not be re-arranged.
     */
    boolean existsByUserIdAndStatusAndCreatedAtAfter(UUID userId, PaymentOrderStatus status, Instant after);

    /**
     * PESSIMISTIC_WRITE row lock -- required before mutating status/capturedAmount.
     * A batch can be resolved by two lines finishing at nearly the same instant
     * (e.g. two vendors accepting within milliseconds of each other), and the
     * capture decision ("are all lines resolved now?") is a read-then-write over
     * the whole batch, not a single conditional UPDATE like OrderRepository
     * .atomicAccept() -- so it needs the same lock pattern WalletRepository uses
     * for credit/debit, not an optimistic-only approach.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentOrder p WHERE p.id = :id")
    Optional<PaymentOrder> findByIdForUpdate(@Param("id") UUID id);

    Page<PaymentOrder> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
