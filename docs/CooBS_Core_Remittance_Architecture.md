# CooBS Core Remittance Architecture — Streamlined Services Breakdown
### Capstone 2: Omnichannel Real-Time Remittance & Fraud Screening
*(Streamlined Architecture: Disregarding Loan, Interest, Statement, and Mock Temenos)*

---

## 1. System Architecture Overview

```
                                      [Omnichannel Clients]
               +----------------------------------+----------------------------------+
               |                                                                     |
      [React Web Teller]                                                   [Flutter Mobile App]
    (Staff: Port 3000/5173)                                                    (Retail: app/)
               |                                                                     |
               +----------------------------------+----------------------------------+
                                                  |
                                                  v  (HTTPS / Port 8080)
      +-----------------------------------------------------------------------------------------------+
      |                              Spring Cloud API Gateway (Port 8080)                             |
      |  - Edge Reverse Proxy & Dedicated Docker Bridge Routing (core-banking-net)                    |
      |  - Redis Token-Bucket Rate Limiter (HTTP 429 on Bursts; 50 req/s on /api/v1/mobile/**)        |
      |  - Resilience4j Circuit Breaker & High-Resilience Fallback Cache                              |
      +-------+-------------------+-------------------+-------------------+-------------------+-------+
              |                   |                   |                   |                   |
       (Auth & Tokens)     (KYC & Profile)     (Direct Balances)   (Remittance Intake)  (Mobile BFF Ingress)
              |                   |                   |                   |                   |
              v                   v                   v                   v                   v
        +-----------+       +-----------+       +-----------+       +-----------+       +-----------+
        |Auth Serv. |       | Cust Serv |       | Acct Serv |       | Txn Serv  |<------+Mobile BFF |
        | (Pt 8081) |       | (Pt 8089) |       | (Pt 8082) |       | (Pt 8090) |       | (Pt 8094) |
        +-----------+       +-----+-----+       +-----+-----+       +-----+-----+       +-----+-----+
                                  |                   ^                   |                   |
                                  +--(Open Acct KYC)--+                   | (Dispatch Saga)   +-(Aggregates: 8089,
                                                                          v                  8082, 8090, 8084)
                                                                +-------------------+
                                                                |Orchestration Serv.|
                                                                |    (Port 8091)    |
                                                                +--------+----------+
                                                                          |
                                                  +-----------------------+
                                                  |                       |
                                           (1. Fraud Check)        (2. Commit SoR)
                                                              |                       |
                                                              v                       v
                                                    +-------------------+   +-------------------+
                                                    |    Risk Engine    |   |  Ledger Service   |
                                                    |  (Python/Async)   |   | (Port 8083 - SoR) |
                                                    |    (Port 8092)    |   |                   |
                                                    +-------------------+   +--------+----------+
                                                                                      |
                                                              +-----------------------+
                                                              |                       |
                                                       (In-App Alerts)         (Forensic Audit)
                                                              |                       |
                                                              v                       v
                                                    +-------------------+   +-------------------+
                                                    |Notification Serv. |   |   Audit Service   |
                                                    | (Kafka Consumer)  |   | (Forensic Ledger) |
                                                    |    (Port 8084)    |   |    (Port 8085)    |
                                                    +-------------------+   +-------------------+
```

---

