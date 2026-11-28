package com.veggofresh.vendor.service.impl;

import com.veggofresh.archive.entity.ArchiveItemType;
import com.veggofresh.archive.service.ArchivePayload;
import com.veggofresh.archive.service.PurgedAccountArchiveService;
import com.veggofresh.platform.storage.CloudinaryService;
import com.veggofresh.vendor.entity.Shop;
import com.veggofresh.vendor.entity.VendorDocument;
import com.veggofresh.vendor.repository.ShopRepository;
import com.veggofresh.vendor.repository.VendorDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Moves what a purged vendor leaves behind into the archive: the bank details stored on the shop and the
 * KYC documents. The Cloudinary files are NOT deleted here -- the archive keeps the reference until an
 * admin decides to delete it for good.
 *
 * It only archives; the existing purge listener still clears the shop's live fields afterwards.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VendorAccountArchiver {

    private static final String ROLE = "VENDOR";

    private final ShopRepository shopRepository;
    private final VendorDocumentRepository vendorDocumentRepository;
    private final PurgedAccountArchiveService archiveService;
    private final CloudinaryService cloudinaryService;

    /** @return number of items archived. Safe to call repeatedly: once nothing is left it does nothing. */
    @Transactional
    public int archiveAndRemove(UUID ownerUserId) {
        Optional<Shop> found = shopRepository.findByOwnerUserIdIncludingDeleted(ownerUserId);
        if (found.isEmpty()) {
            return 0;
        }
        Shop shop = found.get();
        int archived = 0;

        if (ArchivePayload.hasAny(shop.getBankName(), shop.getAccountHolderName(),
                shop.getAccountNumber(), shop.getIfscCode())) {
            archiveService.archiveItem(ownerUserId, ROLE, ArchiveItemType.PROFILE_BANK_DETAILS, shop.getId(),
                    ArchivePayload.of(
                            "bankName", shop.getBankName(),
                            "accountHolderName", shop.getAccountHolderName(),
                            "accountNumber", shop.getAccountNumber(),
                            "ifscCode", shop.getIfscCode()),
                    null, null);
            archived++;
        }

        for (VendorDocument d : vendorDocumentRepository.findByShopId(shop.getId())) {
            boolean stored = archiveService.archiveItem(ownerUserId, ROLE, ArchiveItemType.VENDOR_DOCUMENT, d.getId(),
                    ArchivePayload.of(
                            "shopId", shop.getId(),
                            "documentType", d.getDocumentType(),
                            "status", d.getStatus()),
                    d.getFileUrl(), d.getPublicId());
            if (!stored) {
                cloudinaryService.deleteQuietly(d.getPublicId()); // admin already chose to delete this user's data
            }
            vendorDocumentRepository.delete(d);
            archived++;
        }

        if (archived > 0) {
            log.info("Archived {} item(s) of purged vendor {} (shop {})", archived, ownerUserId, shop.getId());
        }
        return archived;
    }
}
