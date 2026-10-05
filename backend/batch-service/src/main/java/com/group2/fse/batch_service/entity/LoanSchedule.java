package com.group2.fse.batch_service.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "loan_schedule", schema = "batch")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "schedule_id")
    private Long scheduleId;

    @Column(name = "loan_account_id", nullable = false)
    private Long loanAccountId;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false, precision = 18, scale = 4)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false, precision = 18, scale = 4)
    private BigDecimal interestDue;

    @Column(name = "status", nullable = false, length = 20)
    private String status;
}
