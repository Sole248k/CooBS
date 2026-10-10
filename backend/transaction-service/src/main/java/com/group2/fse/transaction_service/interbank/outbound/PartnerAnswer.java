package com.group2.fse.transaction_service.interbank.outbound;

/**
 * What a partner bank said about one message.
 *
 * @param outcome   ACCEPTED and REJECTED are answers. NOT_FOUND is the partner's status endpoint
 *                  saying it never received the message. UNKNOWN is no usable answer at all.
 *                  UNREACHABLE is narrower than UNKNOWN: no connection to the partner was ever
 *                  opened, so this request certainly did not arrive.
 * @param reference the partner's own reference for an accepted transfer
 * @param reasonCode the partner's reason for a refusal
 */
public record PartnerAnswer(Outcome outcome, String reference, String reasonCode) {

    public enum Outcome { ACCEPTED, REJECTED, NOT_FOUND, UNKNOWN, UNREACHABLE }

    /** The reason recorded for a transfer that was never sent because the partner could not be reached. */
    public static final String PARTNER_UNREACHABLE = "PARTNER_UNREACHABLE";

    public static PartnerAnswer accepted(String reference) {
        return new PartnerAnswer(Outcome.ACCEPTED, reference, null);
    }

    public static PartnerAnswer rejected(String reasonCode) {
        return new PartnerAnswer(Outcome.REJECTED, null, reasonCode);
    }

    public static PartnerAnswer notFound() {
        return new PartnerAnswer(Outcome.NOT_FOUND, null, null);
    }

    public static PartnerAnswer unknown() {
        return new PartnerAnswer(Outcome.UNKNOWN, null, null);
    }

    public static PartnerAnswer unreachable() {
        return new PartnerAnswer(Outcome.UNREACHABLE, null, null);
    }

    /** True when the partner has accepted or refused; nothing more needs asking. */
    public boolean decided() {
        return outcome == Outcome.ACCEPTED || outcome == Outcome.REJECTED;
    }
}
