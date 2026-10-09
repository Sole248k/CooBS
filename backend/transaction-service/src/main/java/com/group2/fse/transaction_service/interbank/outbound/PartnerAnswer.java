package com.group2.fse.transaction_service.interbank.outbound;

/**
 * What a partner bank said about one message.
 *
 * @param outcome   ACCEPTED and REJECTED are answers. NOT_FOUND is the partner's status endpoint
 *                  saying it never received the message. UNKNOWN is no usable answer at all.
 * @param reference the partner's own reference for an accepted transfer
 * @param reasonCode the partner's reason for a refusal
 */
public record PartnerAnswer(Outcome outcome, String reference, String reasonCode) {

    public enum Outcome { ACCEPTED, REJECTED, NOT_FOUND, UNKNOWN }

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

    /** True when the partner has accepted or refused; nothing more needs asking. */
    public boolean decided() {
        return outcome == Outcome.ACCEPTED || outcome == Outcome.REJECTED;
    }
}
