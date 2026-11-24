package com.veggofresh.auth.entity;

import com.veggofresh.platform.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Permanent history of every block and unblock of a Customer / Vendor / Delivery account: who did
 * it (admin), when (createdAt), and why (the reason shown to the person, or the optional unblock
 * note). The phone and name are copied in so a row stays readable even after the account is deleted.
 * The CURRENT state lives on the {@code users} row; this table is the trail.
 */
@Entity
@Table(name = "account_block_log")
@Getter
@Setter
public class AccountBlockLog extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 50)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BlockAction action;

    /** BLOCK: the reason the person sees. UNBLOCK: the optional admin note. */
    @Column(length = 500)
    private String reason;

    /** BLOCK only: whether new payout requests were put on hold. */
    @Column(name = "payouts_held", nullable = false)
    private boolean payoutsHeld = false;

    @Column(name = "admin_id")
    private UUID adminId;

    /** The admin's e-mail (or id) at the time, for display. */
    @Column(name = "admin_label", length = 255)
    private String adminLabel;

    @Column(length = 64)
    private String phone;

    @Column(name = "full_name", length = 255)
    private String fullName;
}
