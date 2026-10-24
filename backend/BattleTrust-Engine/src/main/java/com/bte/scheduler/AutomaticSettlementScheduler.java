package com.bte.scheduler;

/**
 * Retired marker, intentionally not a Spring component or scheduled job.
 * ApprovedPayoutScheduler runs only declarations explicitly scheduled by admin.
 */
@Deprecated
public final class AutomaticSettlementScheduler {
    private AutomaticSettlementScheduler() {}
}
