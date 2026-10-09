package com.group2.fse.mobile_bff.service;

import com.group2.fse.mobile_bff.dto.MobileDashboardResponse;
import com.group2.fse.mobile_bff.dto.MobileRemittanceOptionsResponse;
import com.group2.fse.mobile_bff.dto.MobileRemittanceRequest;
import com.group2.fse.mobile_bff.dto.MobileRemittanceResponse;
import com.group2.fse.mobile_bff.dto.MobileTimelineResponse;

public interface MobileBffService {

    MobileDashboardResponse getDashboard(Long customerId, String correlationId, String bearerToken);

    MobileTimelineResponse getTimeline(String trackingReference, Long customerId, String view, String correlationId, String bearerToken);

    default MobileTimelineResponse getTimeline(String trackingReference, Long customerId, String correlationId, String bearerToken) {
        return getTimeline(trackingReference, customerId, null, correlationId, bearerToken);
    }

    default MobileTimelineResponse getTimeline(String trackingReference, String correlationId, String bearerToken) {
        return getTimeline(trackingReference, null, null, correlationId, bearerToken);
    }

    MobileRemittanceOptionsResponse getRemittanceOptions();

    MobileRemittanceResponse submitRemittance(MobileRemittanceRequest request, String idempotencyKey, Long customerId, String correlationId, String bearerToken);
}
