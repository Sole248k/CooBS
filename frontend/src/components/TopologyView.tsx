import React from 'react';
import { Network, Server, Database, Radio, Cpu, ShieldCheck } from 'lucide-react';

interface TopologyViewProps {
  gatewayStatus: string;
}

export const TopologyView: React.FC<TopologyViewProps> = ({
  gatewayStatus
}) => {
  const isOnline = gatewayStatus === 'ONLINE' || gatewayStatus === 'DEMO_STANDALONE';

  const services = [
    {
      name: 'Spring Cloud Gateway',
      port: '8080',
      role: 'Unified Public Edge Ingress & Reverse Proxy',
      tech: 'Spring Cloud 2023 / WebFlux Netty',
      status: isOnline ? 'ONLINE' : 'OFFLINE',
      icon: Network,
      color: 'var(--accent-cyan)'
    },
    {
      name: 'Auth Service',
      port: '8081',
      role: 'Identity, Registration, MFA & Token Authority',
      tech: 'Spring Boot 3.3.4 / JJWT / Redis Cache',
      status: 'OPERATIONAL',
      icon: ShieldCheck,
      color: 'var(--accent-indigo)'
    },
    {
      name: 'Account Service',
      port: '8082',
      role: 'Customer KYC, Profiles & Account Lifecycle',
      tech: 'Spring Boot 3.3.4 / Oracle JPA / Redis',
      status: 'OPERATIONAL',
      icon: Server,
      color: 'var(--accent-cyan)'
    },
    {
      name: 'Ledger Service',
      port: '8083',
      role: 'Double-Entry Mutations & Idempotent Transfers',
      tech: 'Spring Boot 3.3.4 / Oracle Row Locks / Kafka',
      status: 'OPERATIONAL',
      icon: Cpu,
      color: 'var(--color-success)'
    },
    {
      name: 'Notification Service',
      port: '8084',
      role: 'Kafka Event Consumer & In-App Customer Feeds',
      tech: 'Spring Boot 3.3.4 / Spring Kafka / Postgres',
      status: 'OPERATIONAL',
      icon: Radio,
      color: 'var(--color-warning)'
    },
    {
      name: 'Audit & Compliance Service',
      port: '8085',
      role: 'Cryptographic SHA-256 Hash Chaining & Forensic Statement Engine',
      tech: 'Spring Boot 3.3.4 / PostgreSQL 16 (Port 5434) / JJWT',
      status: 'OPERATIONAL',
      icon: Database,
      color: 'var(--color-success)'
    }
  ];

  const databases = [
    {
      name: 'Oracle Database XE',
      port: '1522 / 1521',
      role: 'Master System of Record & Pessimistic Row Locking',
      engine: 'Oracle Free 23c / XE',
      tables: '13 Tables (ACCOUNT, BALANCE, TRANSACTION, USER...)'
    },
    {
      name: 'PostgreSQL 16',
      port: '5434 / 5432',
      role: 'Immutable Forensic Audit Store (SHA-256 Hash Chained)',
      engine: 'PostgreSQL 16 Alpine',
      tables: 'audit_store.ledger_mutation_audit'
    },
    {
      name: 'Redis In-Memory Engine',
      port: '6379',
      role: 'Distributed Idempotency Mutex & Response Cache',
      engine: 'Redis 7.4 Alpine',
      tables: 'AOF Persistence, Atomic SETNX'
    },
    {
      name: 'Apache Kafka KRaft',
      port: '9092',
      role: 'Event Streaming Backbone (Single Broker Quorum)',
      engine: 'Apache Kafka 3.8',
      tables: 'ledger.mutation.completed.v1, ledger.transfer.completed.v1'
    }
  ];

  return (
    <div style={{ maxWidth: '1320px', margin: '0 auto', width: 'calc(100% - 32px)', display: 'flex', flexDirection: 'column', gap: '28px' }}>
      
      {/* Title */}
      <div>
        <h2 style={{ fontSize: '24px', fontWeight: '800', color: '#ffffff', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <Network size={24} color="var(--accent-cyan)" />
          <span>Distributed Architecture & Edge Gateway Topology</span>
        </h2>
        <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '4px' }}>
          Single Source of Truth (SSOT) architecture enforcing strict domain isolation and distributed tracing
        </p>
      </div>

      {/* Microservice Topology Grid */}
      <div>
        <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#ffffff', marginBottom: '14px' }}>
          Microservices Cluster & Port Mapping
        </h3>
        <div className="grid-3">
          {services.map(svc => {
            const Icon = svc.icon;
            return (
              <div key={svc.name} className="glass-card" style={{ padding: '20px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between' }}>
                <div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div style={{ padding: '8px', borderRadius: '8px', background: 'rgba(255, 255, 255, 0.05)', color: svc.color }}>
                      <Icon size={20} />
                    </div>
                    <span className="badge badge-info" style={{ fontFamily: 'var(--font-mono)' }}>
                      PORT {svc.port}
                    </span>
                  </div>

                  <h4 style={{ fontSize: '16px', fontWeight: '700', color: '#ffffff', marginTop: '14px' }}>
                    {svc.name}
                  </h4>
                  <p style={{ fontSize: '12px', color: 'var(--text-muted)', marginTop: '4px' }}>
                    {svc.role}
                  </p>
                </div>

                <div style={{ marginTop: '16px', paddingTop: '12px', borderTop: '1px solid var(--border-subtle)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '11px', color: 'var(--text-subtle)' }}>
                    {svc.tech}
                  </span>
                  <span className="badge badge-success" style={{ fontSize: '10px' }}>
                    <span className="pulse-dot"></span>
                    {svc.status}
                  </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Database & Messaging Tier */}
      <div>
        <h3 style={{ fontSize: '16px', fontWeight: '700', color: '#ffffff', marginBottom: '14px' }}>
          Data Foundation & Persistence Engines
        </h3>
        <div className="grid-2">
          {databases.map(db => (
            <div key={db.name} className="glass-card" style={{ padding: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <span style={{ fontSize: '16px', fontWeight: '700', color: '#ffffff' }}>
                  {db.name}
                </span>
                <span className="badge badge-warning" style={{ fontFamily: 'var(--font-mono)' }}>
                  PORT {db.port}
                </span>
              </div>
              <p style={{ fontSize: '13px', color: 'var(--text-muted)', marginTop: '8px' }}>
                {db.role}
              </p>
              <div style={{ marginTop: '12px', padding: '10px 14px', borderRadius: '8px', background: 'rgba(2, 6, 23, 0.6)', border: '1px solid var(--border-subtle)', fontSize: '12px', fontFamily: 'var(--font-mono)', color: 'var(--accent-cyan)' }}>
                {db.tables}
              </div>
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};
