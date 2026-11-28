package com.veggofresh.archive.entity;

/** Lifecycle of a purged user's archive. Nothing is ever deleted automatically: only an admin moves it on. */
public enum ArchiveStatus {
    /** The removed data is kept; an admin may still decide to delete it. */
    ARCHIVED,
    /** An admin deleted the archived data for good (files and rows). The header stays as a record. */
    PERMANENTLY_DELETED
}
