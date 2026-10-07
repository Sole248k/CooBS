package com.group2.fse.notification_service.service;

import com.group2.fse.notification_service.domain.Notification;
import com.group2.fse.notification_service.domain.NotificationChannel;
import com.group2.fse.notification_service.dto.NotificationPageResponseDto;
import com.group2.fse.notification_service.dto.NotificationReadResponseDto;
import com.group2.fse.notification_service.event.LedgerMutationEvent;
import com.group2.fse.notification_service.event.LedgerTransferEvent;
import com.group2.fse.notification_service.exception.ResourceNotFoundException;
import com.group2.fse.notification_service.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Mock
    private org.springframework.kafka.core.KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private NotificationService notificationService;

    private LedgerMutationEvent sampleDebitEvent;

    @BeforeEach
    void setUp() {
        sampleDebitEvent = LedgerMutationEvent.builder()
                .eventId("evt_12345")
                .eventType("LEDGER_MUTATION_COMPLETED")
                .timestamp(Instant.now())
                .payload(LedgerMutationEvent.MutationPayload.builder()
                        .transactionId(801L)
                        .accountId(1L)
                        .accountNumber("ACC_10000001")
                        .transactionType("DEBIT")
                        .amount(new BigDecimal("2500.0000"))
                        .previousBalance(new BigDecimal("50000.0000"))
                        .newBalance(new BigDecimal("47500.0000"))
                        .referenceNo("DEB-20260925-001")
                        .customerId(101L)
                        .build())
                .build();
    }

    @Test
    @DisplayName("Should successfully process LedgerMutationEvent and persist debit alert")
    void shouldProcessMutationDebitEvent() {
        when(notificationRepository.existsByEventId("evt_12345")).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(601L);
            return n;
        });

        notificationService.processMutationEvent(sampleDebitEvent);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();

        assertThat(saved.getCustomerId()).isEqualTo(101L);
        assertThat(saved.getTitle()).isEqualTo("Account Debited");
        assertThat(saved.getMessage()).contains("PHP 2500.0000 was debited from account ACC_10000001");
        assertThat(saved.isRead()).isFalse();
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(saved.getEventId()).isEqualTo("evt_12345");

        verify(notificationDispatcher).dispatch(any(Notification.class));
    }

    @Test
    @DisplayName("Should skip processing when eventId is already processed (Idempotency)")
    void shouldSkipDuplicateEvent() {
        when(notificationRepository.existsByEventId("evt_12345")).thenReturn(true);

        notificationService.processMutationEvent(sampleDebitEvent);

        verify(notificationRepository, never()).save(any());
        verify(notificationDispatcher, never()).dispatch(any());
    }

    @Test
    @DisplayName("Should successfully process LedgerTransferEvent and create alerts for sender & recipient")
    void shouldProcessTransferEvent() {
        LedgerTransferEvent transferEvent = LedgerTransferEvent.builder()
                .eventId("evt_trf_999")
                .eventType("LEDGER_TRANSFER_COMPLETED")
                .timestamp(Instant.now())
                .payload(LedgerTransferEvent.TransferPayload.builder()
                        .transferReference("TRF-20260925-001")
                        .sourceAccountId(1L)
                        .destinationAccountId(2L)
                        .amount(new BigDecimal("1000.0000"))
                        .sourcePreviousBalance(new BigDecimal("50000.0000"))
                        .sourceNewBalance(new BigDecimal("49000.0000"))
                        .destinationPreviousBalance(new BigDecimal("10000.0000"))
                        .destinationNewBalance(new BigDecimal("11000.0000"))
                        .sourceCustomerId(101L)
                        .destinationCustomerId(102L)
                        .build())
                .build();

        when(notificationRepository.existsByEventId("evt_trf_999")).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.processTransferEvent(transferEvent);

        // Expect 2 notifications saved (sender + recipient)
        verify(notificationRepository, times(2)).save(any(Notification.class));
        verify(notificationDispatcher, times(2)).dispatch(any(Notification.class));
    }

    @Test
    @DisplayName("Should retrieve paginated customer notifications")
    void shouldRetrieveCustomerNotifications() {
        Notification notification = Notification.builder()
                .id(601L)
                .customerId(101L)
                .title("Account Debited")
                .message("Test message")
                .channel(NotificationChannel.IN_APP)
                .isRead(false)
                .createdAt(Instant.now())
                .build();

        Page<Notification> mockPage = new PageImpl<>(List.of(notification));
        when(notificationRepository.findByCustomerIdOrderByCreatedAtDesc(eq(101L), any(Pageable.class)))
                .thenReturn(mockPage);

        NotificationPageResponseDto result = notificationService.getCustomerNotifications(101L, false, 0, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getNotificationId()).isEqualTo(601L);
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Account Debited");
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("Should mark notification as read")
    void shouldMarkNotificationAsRead() {
        Notification notification = Notification.builder()
                .id(601L)
                .customerId(101L)
                .title("Account Debited")
                .message("Test message")
                .isRead(false)
                .build();

        when(notificationRepository.findById(601L)).thenReturn(Optional.of(notification));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationReadResponseDto result = notificationService.markAsRead(601L, 101L);

        assertThat(result.getNotificationId()).isEqualTo(601L);
        assertThat(result.getIsRead()).isTrue();
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("Should throw ResourceNotFoundException when notification does not exist")
    void shouldThrowNotFoundWhenMarkingNonExistentNotification() {
        when(notificationRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(999L, 101L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("999 not found");
    }
}
