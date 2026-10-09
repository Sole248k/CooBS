package com.group2.fse.transaction_service.interbank.outbound;

import com.group2.fse.transaction_service.client.AccountLookupUnavailableException;
import com.group2.fse.transaction_service.client.OrchestrationClient;
import com.group2.fse.transaction_service.client.dto.AccountLookupResponse;
import com.group2.fse.transaction_service.entity.InterbankMessage;
import com.group2.fse.transaction_service.entity.TxnTransaction;
import com.group2.fse.transaction_service.exception.TransactionNotFoundException;
import com.group2.fse.transaction_service.exception.TransferRejectedException;
import com.group2.fse.transaction_service.interbank.security.InterbankSecurityProperties;
import com.group2.fse.transaction_service.repository.InterbankMessageRepository;
import com.group2.fse.transaction_service.repository.TxnTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("I4 inter-bank gateway: one message per transaction, and an error only when nothing was sent")
class OutboundInterbankGatewayTest {

    private static final UUID TXN = UUID.fromString("6f1c0e0a-3a52-4c0e-9d0b-2f0c8b7a1e11");

    @Mock
    private TxnTransactionRepository transactions;
    @Mock
    private InterbankMessageRepository messages;
    @Mock
    private OrchestrationClient accounts;
    @Mock
    private InterbankClient interbankClient;

    private OutboundInterbankGateway gateway;
    private TxnTransaction txn;
    /** The status the message had each time it was saved, in order. */
    private final List<String> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        InterbankSecurityProperties properties = new InterbankSecurityProperties();
        InterbankSecurityProperties.PartnerConfig bankB = new InterbankSecurityProperties.PartnerConfig();
        bankB.setBankCode("BANKB");
        bankB.setSharedSecret("test-shared-secret-2026");
        properties.getPartners().put("BANKB", bankB);
        gateway = new OutboundInterbankGateway(transactions, messages, properties, accounts, interbankClient);

        txn = TxnTransaction.builder()
                .transactionId(TXN)
                .referenceNo("TX-20261003-8F3A21C0")
                .customerId(1L)
                .sourceAccountId(1L)
                .destinationAccountId(9005L)
                .destinationAccountNumber("1000-2000-3000")
                .destinationBankCode("BANKB")
                .amount(new BigDecimal("2500.0000"))
                .currency("PHP")
                .remarks("Padala sa kapatid")
                .status("PROCESSING")
                .createdAt(Instant.parse("2026-10-03T11:40:00Z"))
                .build();
        when(transactions.findById(TXN)).thenReturn(Optional.of(txn));
        when(messages.findById(TXN)).thenReturn(Optional.of(message(InterbankMessage.CREATED)));
        when(messages.save(any(InterbankMessage.class))).thenAnswer(call -> {
            InterbankMessage saved = call.getArgument(0);
            savedStatuses.add(saved.getStatus());
            return saved;
        });
        AccountLookupResponse source = new AccountLookupResponse();
        source.setAccountId(1L);
        source.setAccountNumber("ACC_10000001");
        when(accounts.findAccountById(1L)).thenReturn(Optional.of(source));
    }

    private static InterbankMessage message(String status) {
        return InterbankMessage.builder()
                .messageId(TXN).direction("OUTBOUND").partnerBankCode("BANKB").transactionId(TXN)
                .counterpartyAccount("1000-2000-3000").counterpartyName("Ana Lim").status(status)
                .createdAt(Instant.parse("2026-10-03T11:40:00Z")).build();
    }

    private static OutboundInterbankRequest request() {
        return OutboundInterbankRequest.builder()
                .transactionId(TXN).referenceNo("TX-20261003-8F3A21C0").destinationBankCode("BANKB")
                .amount(new BigDecimal("2500.00")).currency("PHP").build();
    }

    @Test
    @DisplayName("accepted: the message is marked as possibly sent before the partner is called, then recorded as accepted")
    void partnerAccepts() {
        when(interbankClient.deliver(any(), eq(false))).thenReturn(PartnerAnswer.accepted("BB-20261003-000123"));

        OutboundInterbankResponse response = gateway.send(request());

        assertThat(response.getPartnerStatus()).isEqualTo("ACCEPTED");
        assertThat(response.getPartnerReference()).isEqualTo("BB-20261003-000123");
        assertThat(response.getReasonCode()).isNull();
        assertThat(savedStatuses).containsExactly("PENDING", "ACCEPTED");

        ArgumentCaptor<OutboundTransfer> sent = ArgumentCaptor.forClass(OutboundTransfer.class);
        verify(interbankClient).deliver(sent.capture(), eq(false));
        // The message ID is the transaction ID, and the beneficiary comes from our own record.
        assertThat(sent.getValue().messageId()).isEqualTo(TXN);
        assertThat(sent.getValue().senderAccountNumber()).isEqualTo("ACC_10000001");
        assertThat(sent.getValue().beneficiaryAccountNumber()).isEqualTo("1000-2000-3000");
        assertThat(sent.getValue().beneficiaryName()).isEqualTo("Ana Lim");
        assertThat(sent.getValue().ownBankCode()).isEqualTo("COOBS");
        assertThat(sent.getValue().senderReference()).isEqualTo("TX-20261003-8F3A21C0");
    }

    @Test
    @DisplayName("refused by the partner: recorded with the partner's reason")
    void partnerRejects() {
        when(interbankClient.deliver(any(), eq(false))).thenReturn(PartnerAnswer.rejected("ACCOUNT_NOT_FOUND"));

        OutboundInterbankResponse response = gateway.send(request());

        assertThat(response.getPartnerStatus()).isEqualTo("REJECTED");
        assertThat(response.getReasonCode()).isEqualTo("ACCOUNT_NOT_FOUND");
        assertThat(savedStatuses).containsExactly("PENDING", "REJECTED");
    }

    @Test
    @DisplayName("a transfer the partner already answered is answered from the record; the partner is not called again")
    void decidedTransferIsNotSentAgain() {
        when(messages.findById(TXN)).thenReturn(Optional.of(message(InterbankMessage.ACCEPTED)));

        OutboundInterbankResponse response = gateway.send(request());

        assertThat(response.getPartnerStatus()).isEqualTo("ACCEPTED");
        verify(interbankClient, never()).deliver(any(), anyBoolean());
        verify(messages, never()).save(any());
    }

    @Test
    @DisplayName("no answer: PENDING, and the message stays marked as possibly sent")
    void noAnswerIsPending() {
        when(interbankClient.deliver(any(), eq(false))).thenReturn(PartnerAnswer.unknown());

        OutboundInterbankResponse response = gateway.send(request());

        assertThat(response.getPartnerStatus()).isEqualTo("PENDING");
        assertThat(savedStatuses).containsExactly("PENDING");
    }

    @Test
    @DisplayName("a message that may have been sent is asked about first, and nothing is an error from then on")
    void possiblySentMessageNeverAnswersWithAnError() {
        when(messages.findById(TXN)).thenReturn(Optional.of(message(InterbankMessage.PENDING)));
        // Even our own account lookup failing must not look like "nothing was sent".
        when(accounts.findAccountById(1L)).thenThrow(new AccountLookupUnavailableException("down", null));
        when(interbankClient.deliver(any(), eq(true))).thenThrow(new IllegalStateException("partner client broke"));
        txn.setStatus("FAILED");

        OutboundInterbankResponse response = gateway.send(request());

        assertThat(response.getPartnerStatus()).isEqualTo("PENDING");
        ArgumentCaptor<OutboundTransfer> asked = ArgumentCaptor.forClass(OutboundTransfer.class);
        verify(interbankClient).deliver(asked.capture(), eq(true));
        assertThat(asked.getValue().senderAccountNumber()).isNull();
    }

    @Test
    @DisplayName("a possibly sent message that the partner did accept is recorded on the later call")
    void laterCallLearnsTheAnswer() {
        when(messages.findById(TXN)).thenReturn(Optional.of(message(InterbankMessage.PENDING)));
        when(interbankClient.deliver(any(), eq(true))).thenReturn(PartnerAnswer.accepted("BB-1"));

        assertThat(gateway.send(request()).getPartnerStatus()).isEqualTo("ACCEPTED");
        assertThat(savedStatuses).containsExactly("ACCEPTED");
    }

    @Test
    @DisplayName("before anything is sent, what is wrong is an error, and the partner is never called")
    void nothingSentIsAnError() {
        when(transactions.findById(TXN)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> gateway.send(request())).isInstanceOf(TransactionNotFoundException.class);
        when(transactions.findById(TXN)).thenReturn(Optional.of(txn));

        txn.setDestinationBankCode("BPI");
        OutboundInterbankRequest toBpi = request();
        toBpi.setDestinationBankCode("BPI");
        assertRefused(toBpi, HttpStatus.UNPROCESSABLE_ENTITY, "INTERBANK_REJECTED");

        txn.setDestinationBankCode("COOBS");
        assertRefused(request(), HttpStatus.UNPROCESSABLE_ENTITY, "INTERBANK_REJECTED");
        txn.setDestinationBankCode("BANKB");

        OutboundInterbankRequest otherAmount = request();
        otherAmount.setAmount(new BigDecimal("9999.00"));
        assertRefused(otherAmount, HttpStatus.BAD_REQUEST, "INVALID_REQUEST_PARAMETERS");

        txn.setStatus("FAILED");
        assertRefused(request(), HttpStatus.UNPROCESSABLE_ENTITY, "INTERBANK_REJECTED");
        txn.setStatus("PROCESSING");

        // Our own account lookup being down is a 503: nothing was sent, and the saga keeps the money and asks again.
        when(accounts.findAccountById(1L)).thenThrow(new AccountLookupUnavailableException("down", null));
        assertRefused(request(), HttpStatus.SERVICE_UNAVAILABLE, "ACCOUNT_UNAVAILABLE");

        verify(interbankClient, never()).deliver(any(), anyBoolean());
        assertThat(savedStatuses).isEmpty();
    }

    @Test
    @DisplayName("a transfer accepted before T1 recorded its message still gets one, from the transaction")
    void messageIsCreatedWhenMissing() {
        when(messages.findById(TXN)).thenReturn(Optional.empty());
        when(interbankClient.deliver(any(), eq(false))).thenReturn(PartnerAnswer.accepted("BB-2"));

        gateway.send(request());

        ArgumentCaptor<InterbankMessage> saved = ArgumentCaptor.forClass(InterbankMessage.class);
        verify(messages, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getMessageId()).isEqualTo(TXN);
        assertThat(saved.getValue().getDirection()).isEqualTo("OUTBOUND");
        assertThat(saved.getValue().getPartnerBankCode()).isEqualTo("BANKB");
        assertThat(saved.getValue().getCounterpartyAccount()).isEqualTo("1000-2000-3000");
    }

    private void assertRefused(OutboundInterbankRequest request, HttpStatus status, String errorCode) {
        assertThatThrownBy(() -> gateway.send(request))
                .isInstanceOfSatisfying(TransferRejectedException.class, refusal -> {
                    assertThat(refusal.getStatus()).isEqualTo(status);
                    assertThat(refusal.getErrorCode()).isEqualTo(errorCode);
                });
    }
}
