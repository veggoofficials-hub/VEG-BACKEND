package com.veggofresh.archive.entity;

/** What kind of removed data an archive item holds. */
public enum ArchiveItemType {
    /** A saved bank account from user_bank_accounts (any role). */
    BANK_ACCOUNT,
    /** Bank details that lived on the vendor shop / delivery profile itself. */
    PROFILE_BANK_DETAILS,
    /** A delivery partner's emergency contact. */
    EMERGENCY_CONTACT,
    /** A vendor KYC document (row + Cloudinary file reference). */
    VENDOR_DOCUMENT,
    /** A delivery KYC document (row + Cloudinary file reference). */
    DELIVERY_DOCUMENT,
    /** The old/new values and document of a delivery change request. */
    CHANGE_REQUEST
}
