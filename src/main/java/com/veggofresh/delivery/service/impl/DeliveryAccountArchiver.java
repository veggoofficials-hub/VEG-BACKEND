package com.veggofresh.delivery.service.impl;

import com.veggofresh.archive.entity.ArchiveItemType;
import com.veggofresh.archive.service.ArchivePayload;
import com.veggofresh.archive.service.PurgedAccountArchiveService;
import com.veggofresh.delivery.entity.DeliveryChangeRequest;
import com.veggofresh.delivery.entity.DeliveryDocument;
import com.veggofresh.delivery.entity.DeliveryPartnerProfile;
import com.veggofresh.delivery.repository.DeliveryChangeRequestRepository;
import com.veggofresh.delivery.repository.DeliveryDocumentRepository;
import com.veggofresh.delivery.repository.DeliveryPartnerProfileRepository;
import com.veggofresh.platform.storage.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Moves what a purged delivery partner leaves behind into the archive: bank details and emergency contact
 * stored on the profile, KYC documents, and the old/new values and documents of change requests.
 * Cloudinary files are NOT deleted here -- the archive keeps the reference until an admin deletes it for good.
 *
 * It only archives; the existing purge listener still clears the profile's live fields afterwards.
 * Change-request ROWS stay (they are history, with their status and decision); only their values and
 * file are emptied.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryAccountArchiver {

    private static final String ROLE = "DELIVERY";
    private static final String EMPTY_JSON = "{}";

    private final DeliveryPartnerProfileRepository profileRepository;
    private final DeliveryDocumentRepository documentRepository;
    private final DeliveryChangeRequestRepository changeRequestRepository;
    private final PurgedAccountArchiveService archiveService;
    private final CloudinaryService cloudinaryService;

    /** @return number of items archived. Safe to call repeatedly: once nothing is left it does nothing. */
    @Transactional
    public int archiveAndRemove(UUID partnerUserId) {
        int archived = 0;

        Optional<DeliveryPartnerProfile> profileOpt = profileRepository.findByUserIdIncludingDeleted(partnerUserId);
        if (profileOpt.isPresent()) {
            DeliveryPartnerProfile p = profileOpt.get();
            if (ArchivePayload.hasAny(p.getBankName(), p.getAccountHolderName(), p.getAccountNumber(), p.getIfscCode())) {
                archiveService.archiveItem(partnerUserId, ROLE, ArchiveItemType.PROFILE_BANK_DETAILS, p.getId(),
                        ArchivePayload.of(
                                "bankName", p.getBankName(),
                                "accountHolderName", p.getAccountHolderName(),
                                "accountNumber", p.getAccountNumber(),
                                "ifscCode", p.getIfscCode()),
                        null, null);
                archived++;
            }
            if (ArchivePayload.hasAny(p.getEmergencyContactName(), p.getEmergencyContactRelationship(),
                    p.getEmergencyContactPhone())) {
                archiveService.archiveItem(partnerUserId, ROLE, ArchiveItemType.EMERGENCY_CONTACT, p.getId(),
                        ArchivePayload.of(
                                "name", p.getEmergencyContactName(),
                                "relationship", p.getEmergencyContactRelationship(),
                                "phone", p.getEmergencyContactPhone()),
                        null, null);
                archived++;
            }
        }

        for (DeliveryDocument d : documentRepository.findByDeliveryPartnerUserId(partnerUserId)) {
            boolean stored = archiveService.archiveItem(partnerUserId, ROLE, ArchiveItemType.DELIVERY_DOCUMENT, d.getId(),
                    ArchivePayload.of(
                            "documentType", d.getDocumentType(),
                            "status", d.getStatus(),
                            "expiryDate", d.getExpiryDate()),
                    d.getFileUrl(), d.getPublicId());
            if (!stored) {
                cloudinaryService.deleteQuietly(d.getPublicId());
            }
            documentRepository.delete(d);
            archived++;
        }

        for (DeliveryChangeRequest c : changeRequestRepository
                .findByDeliveryPartnerUserIdOrderByCreatedAtDescIdDesc(partnerUserId)) {
            boolean hasData = c.getDocumentPublicId() != null || c.getPreviousValues() != null
                    || (c.getRequestedValues() != null && !EMPTY_JSON.equals(c.getRequestedValues()));
            if (!hasData) {
                continue; // already emptied
            }
            boolean stored = archiveService.archiveItem(partnerUserId, ROLE, ArchiveItemType.CHANGE_REQUEST, c.getId(),
                    ArchivePayload.of(
                            "section", c.getSection(),
                            "status", c.getStatus(),
                            "previousValues", c.getPreviousValues(),
                            "requestedValues", c.getRequestedValues(),
                            "decisionReason", c.getDecisionReason(),
                            "decidedAt", c.getDecidedAt()),
                    c.getDocumentUrl(), c.getDocumentPublicId());
            if (!stored) {
                cloudinaryService.deleteQuietly(c.getDocumentPublicId());
            }
            c.setPreviousValues(null);
            c.setRequestedValues(EMPTY_JSON); // column is NOT NULL
            c.setDocumentUrl(null);
            c.setDocumentPublicId(null);
            changeRequestRepository.save(c);
            archived++;
        }

        if (archived > 0) {
            log.info("Archived {} item(s) of purged delivery partner {}", archived, partnerUserId);
        }
        return archived;
    }
}
