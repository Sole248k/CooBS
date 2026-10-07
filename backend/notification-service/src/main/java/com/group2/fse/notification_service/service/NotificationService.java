package com.group2.fse.notification_service.service;

import com.group2.fse.notification_service.domain.Notification;
import com.group2.fse.notification_service.domain.NotificationChannel;
import com.group2.fse.notification_service.dto.NotificationPageResponseDto;
import com.group2.fse.notification_service.dto.NotificationReadResponseDto;
import com.group2.fse.notification_service.dto.NotificationResponseDto;
import com.group2.fse.notification_service.event.AccountClosureEvent;
import com.group2.fse.notification_service.event.KycRequestEvaluatedEvent;
import com.group2.fse.notification_service.event.KycRequestSubmittedEvent;
import com.group2.fse.notification_service.event.LedgerMutationEvent;
import com.group2.fse.notification_service.event.LedgerTransferEvent;
import com.group2.fse.notification_service.event.LedgerTransferFailedEvent;
import com.group2.fse.notification_service.exception.ResourceNotFoundException;
import com.group2.fse.notification_service.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Task: FSE-601
 * Core business logic for processing domain events, persisting in-app alerts,
 * and serving customer notification feeds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @org.springframework.beans.factory.annotation.Value("${notification.kafka.topics.dispatched:notification.dispatched.v1}")
    private String dispatchedTopic = "notification.dispatched.v1";

    @Transactional
    public void processMutationEvent(LedgerMutationEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("Discarding null LedgerMutationEvent or empty payload");
            return;
        }

        String eventId = event.getEventId();
        if (eventId != null && notificationRepository.existsByEventId(eventId)) {
            log.info("Idempotent skip: Event [{}] already processed.", eventId);
            return;
        }

        LedgerMutationEvent.MutationPayload p = event.getPayload();
        Long customerId = p.getCustomerId() != null ? p.getCustomerId() : p.getAccountId();
        String type = p.getTransactionType() != null ? p.getTransactionType().toUpperCase() : "MUTATION";

        String title = "DEBIT".equals(type) ? "Account Debited" : "Account Credited";
        String actionVerb = "DEBIT".equals(type) ? "debited from" : "credited to";
        String message = String.format("PHP %s was %s account %s. New balance: PHP %s.",
                p.getAmount(), actionVerb, p.getAccountNumber(), p.getNewBalance());

        Notification notification = Notification.builder()
                .customerId(customerId)
                .title(title)
                .message(message)
                .channel(NotificationChannel.IN_APP)
                .isRead(false)
                .eventId(eventId)
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        notificationDispatcher.dispatch(saved);
        log.info("Processed LedgerMutationEvent [{}] -> Created Notification [{}] for Customer [{}]",
                eventId, saved.getId(), customerId);
    }

    @Transactional
    public void processTransferEvent(LedgerTransferEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("Discarding null LedgerTransferEvent or empty payload");
            return;
        }

        String eventId = event.getEventId();
        if (eventId != null && notificationRepository.existsByEventId(eventId)) {
            log.info("Idempotent skip: Transfer Event [{}] already processed.", eventId);
            return;
        }

        LedgerTransferEvent.TransferPayload p = event.getPayload();

        // 1. Sender Notification
        Long sourceCustId = p.getSourceCustomerId() != null ? p.getSourceCustomerId() : p.getSourceAccountId();
        if (sourceCustId != null) {
            Notification senderNotification = Notification.builder()
                    .customerId(sourceCustId)
                    .title("Funds Transfer Sent")
                    .message(String.format("PHP %s was transferred to account %s. Reference: %s. New balance: PHP %s.",
                            p.getAmount(), p.getDestinationAccountId(), p.getTransferReference(), p.getSourceNewBalance()))
                    .channel(NotificationChannel.IN_APP)
                    .isRead(false)
                    .eventId(eventId != null ? eventId + "_SRC" : null)
                    .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                    .build();

            Notification savedSender = notificationRepository.save(senderNotification);
            notificationDispatcher.dispatch(savedSender);
        }

        // 2. Recipient Notification
        Long destCustId = p.getDestinationCustomerId() != null ? p.getDestinationCustomerId() : p.getDestinationAccountId();
        if (destCustId != null) {
            Notification destNotification = Notification.builder()
                    .customerId(destCustId)
                    .title("Funds Transfer Received")
                    .message(String.format("PHP %s was received from account %s. Reference: %s. New balance: PHP %s.",
                            p.getAmount(), p.getSourceAccountId(), p.getTransferReference(), p.getDestinationNewBalance()))
                    .channel(NotificationChannel.IN_APP)
                    .isRead(false)
                    .eventId(eventId != null ? eventId + "_DEST" : null)
                    .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                    .build();

            Notification savedDest = notificationRepository.save(destNotification);
            notificationDispatcher.dispatch(savedDest);
        }

        // Publish notification.dispatched.v1 to Kafka (Day 5 Event Propagation)
        try {
            java.util.List<Long> customerIds = new java.util.ArrayList<>();
            if (sourceCustId != null) customerIds.add(sourceCustId);
            if (destCustId != null) customerIds.add(destCustId);

            com.group2.fse.notification_service.event.NotificationDispatchedEvent dispatchedEvent =
                    com.group2.fse.notification_service.event.NotificationDispatchedEvent.builder()
                            .eventId("evt_" + java.util.UUID.randomUUID())
                            .eventType("NOTIFICATION_DISPATCHED")
                            .timestamp(Instant.now())
                            .version("1.0")
                            .payload(com.group2.fse.notification_service.event.NotificationDispatchedEvent.DispatchedPayload.builder()
                                    .referenceNo(p.getTransferReference())
                                    .sourceEventId(eventId)
                                    .customerIds(customerIds)
                                    .channel("IN_APP")
                                    .dispatchedAt(Instant.now())
                                    .build())
                            .build();

            String messageKey = p.getTransferReference() != null ? p.getTransferReference() : eventId;
            var future = kafkaTemplate.send(dispatchedTopic, messageKey, dispatchedEvent);
            if (future != null) {
                future.whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish NotificationDispatchedEvent for ref [{}]: {}",
                                p.getTransferReference(), ex.getMessage());
                    } else {
                        log.info("Published NotificationDispatchedEvent to topic [{}] for ref [{}]",
                                dispatchedTopic, p.getTransferReference());
                    }
                });
            }
        } catch (Exception ex) {
            log.error("Error publishing NotificationDispatchedEvent for ref [{}]: {}", p.getTransferReference(), ex.getMessage(), ex);
        }

        log.info("Processed LedgerTransferEvent [{}] ref [{}]", eventId, p.getTransferReference());
    }

    @Transactional
    public void processAccountClosureEvent(AccountClosureEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("Discarding null AccountClosureEvent or empty payload");
            return;
        }

        String eventId = event.getEventId();
        if (eventId != null && notificationRepository.existsByEventId(eventId)) {
            log.info("Idempotent skip: Closure Event [{}] already processed.", eventId);
            return;
        }

        AccountClosureEvent.ClosurePayload p = event.getPayload();
        Notification notification = Notification.builder()
                .customerId(p.getCustomerId())
                .title("Account Closed")
                .message(String.format("Account %s closure request #%s has been finalized. Final balance: PHP %s.",
                        p.getAccountNumber(), p.getClosureRequestId(), p.getFinalBalance()))
                .channel(NotificationChannel.IN_APP)
                .isRead(false)
                .eventId(eventId)
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        notificationDispatcher.dispatch(saved);
        log.info("Processed AccountClosureEvent [{}] for Customer [{}]", eventId, p.getCustomerId());
    }

    @Transactional
    public void processTransferFailedEvent(LedgerTransferFailedEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("Discarding null LedgerTransferFailedEvent or empty payload");
            return;
        }

        String eventId = event.getEventId();
        if (eventId != null && notificationRepository.existsByEventId(eventId)) {
            log.info("Idempotent skip: Transfer Failed Event [{}] already processed.", eventId);
            return;
        }

        LedgerTransferFailedEvent.FailedTransferPayload p = event.getPayload();
        Long customerId = p.getSourceCustomerId() != null ? p.getSourceCustomerId() : p.getSourceAccountId();
        if (customerId == null) {
            log.warn("No customer or source account associated with failed transfer [{}]", eventId);
            return;
        }

        String title = "Funds Transfer Failed";
        String message = String.format("Transfer of PHP %s to account %s failed. Reason: %s (Ref: %s)",
                p.getAmount() != null ? p.getAmount() : "0.00",
                p.getDestinationAccountId(),
                p.getFailureReason() != null ? p.getFailureReason() : "Transaction rejected",
                p.getTransferReference() != null ? p.getTransferReference() : "N/A");

        Notification notification = Notification.builder()
                .customerId(customerId)
                .title(title)
                .message(message)
                .channel(NotificationChannel.PUSH)
                .isRead(false)
                .eventId(eventId)
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        notificationDispatcher.dispatch(saved);
        log.info("Processed LedgerTransferFailedEvent [{}] -> Notification [{}] for Customer [{}]",
                eventId, saved.getId(), customerId);
    }

    @Transactional
    public void processKycSubmittedEvent(KycRequestSubmittedEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("Discarding null KycRequestSubmittedEvent or empty payload");
            return;
        }

        String eventId = event.getEventId();
        if (eventId != null && notificationRepository.existsByEventId(eventId)) {
            log.info("Idempotent skip: Kyc Submitted Event [{}] already processed.", eventId);
            return;
        }

        KycRequestSubmittedEvent.KycSubmittedPayload p = event.getPayload();
        Long customerId = p.getCustomerId();
        if (customerId == null) {
            log.warn("No customer associated with KYC submitted event [{}]", eventId);
            return;
        }

        String title = "KYC Update Submitted";
        String message = String.format("Your KYC update request #%s has been successfully submitted and is pending teller verification.",
                p.getRequestId());

        Notification notification = Notification.builder()
                .customerId(customerId)
                .title(title)
                .message(message)
                .channel(NotificationChannel.IN_APP)
                .isRead(false)
                .eventId(eventId)
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        notificationDispatcher.dispatch(saved);
        log.info("Processed KycRequestSubmittedEvent [{}] -> Notification [{}] for Customer [{}]",
                eventId, saved.getId(), customerId);
    }

    @Transactional
    public void processKycEvaluatedEvent(KycRequestEvaluatedEvent event) {
        if (event == null || event.getPayload() == null) {
            log.warn("Discarding null KycRequestEvaluatedEvent or empty payload");
            return;
        }

        String eventId = event.getEventId();
        if (eventId != null && notificationRepository.existsByEventId(eventId)) {
            log.info("Idempotent skip: Kyc Evaluated Event [{}] already processed.", eventId);
            return;
        }

        KycRequestEvaluatedEvent.KycEvaluatedPayload p = event.getPayload();
        Long customerId = p.getCustomerId();
        if (customerId == null) {
            log.warn("No customer associated with KYC evaluated event [{}]", eventId);
            return;
        }

        boolean isApproved = "APPROVED".equalsIgnoreCase(p.getStatus());
        String title = isApproved ? "KYC Update Approved" : "KYC Update Rejected";
        String message = isApproved
                ? String.format("Your KYC update request #%s has been approved. Your customer profile is now up to date.", p.getRequestId())
                : String.format("Your KYC update request #%s was rejected. Reason: %s",
                        p.getRequestId(),
                        p.getRejectionReason() != null ? p.getRejectionReason() : "Does not meet verification criteria.");

        Notification notification = Notification.builder()
                .customerId(customerId)
                .title(title)
                .message(message)
                .channel(NotificationChannel.IN_APP)
                .isRead(false)
                .eventId(eventId)
                .createdAt(event.getTimestamp() != null ? event.getTimestamp() : Instant.now())
                .build();

        Notification saved = notificationRepository.save(notification);
        notificationDispatcher.dispatch(saved);
        log.info("Processed KycRequestEvaluatedEvent [{}] -> Notification [{}] for Customer [{}] (Status: {})",
                eventId, saved.getId(), customerId, p.getStatus());
    }

    @Transactional(readOnly = true)
    public NotificationPageResponseDto getCustomerNotifications(Long customerId, boolean unreadOnly, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
        Page<Notification> entityPage;

        if (unreadOnly) {
            entityPage = notificationRepository.findByCustomerIdAndIsReadOrderByCreatedAtDesc(customerId, false, pageable);
        } else {
            entityPage = notificationRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable);
        }

        Page<NotificationResponseDto> dtoPage = entityPage.map(NotificationResponseDto::fromEntity);
        return NotificationPageResponseDto.fromPage(dtoPage);
    }

    @Transactional
    public NotificationReadResponseDto markAsRead(Long notificationId, Long customerId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification with id " + notificationId + " not found"));

        if (customerId != null && !customerId.equals(notification.getCustomerId())) {
            throw new ResourceNotFoundException("Notification with id " + notificationId + " not found");
        }

        notification.setRead(true);
        notification.setReadAt(Instant.now());
        notificationRepository.save(notification);

        return NotificationReadResponseDto.builder()
                .notificationId(notification.getId())
                .isRead(true)
                .build();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long customerId) {
        return notificationRepository.countByCustomerIdAndIsReadFalse(customerId);
    }
}
