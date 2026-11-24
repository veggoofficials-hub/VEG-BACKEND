package com.veggofresh.admin.service;

import com.veggofresh.admin.dto.response.DeletedAccountResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/** Read-only admin archive of accounts their owners deleted themselves. */
public interface AdminDeletedAccountService {

    /**
     * @param role   CUSTOMER / VENDOR / DELIVERY, or null for all
     * @param search matches phone, name or e-mail
     * @param status IN_WINDOW / REACTIVATED / PURGED / RETURNED_AS_NEW, or null for all
     */
    Page<DeletedAccountResponseDto> list(String role, String search, String status, Pageable pageable);

    DeletedAccountResponseDto getById(UUID id);
}
