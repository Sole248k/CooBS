import { clsx, type ClassValue } from 'clsx';

/** Merge class names conditionally */
export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}

/**
 * Format a numeric string or number as Philippine Peso.
 * Input comes from the API as a string ("2500.00"); parse as decimal, never float.
 */
export function formatCurrency(value: string | number | undefined | null): string {
  if (value === undefined || value === null || value === '') return '₱0.00';
  const num = typeof value === 'string' ? parseFloat(value) : value;
  if (isNaN(num)) return '₱0.00';
  return `₱${num.toLocaleString('en-PH', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

/**
 * Format an ISO-8601 UTC timestamp to local time.
 * Shows date if older than today.
 */
export function formatTimestamp(iso: string | undefined | null): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (isNaN(date.getTime())) return iso;
  const now = new Date();
  const isToday =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();

  if (isToday) {
    return date.toLocaleTimeString('en-PH', {
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit',
    });
  }
  return date.toLocaleString('en-PH', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
}

/** Format a date-only string (e.g. "2026-10-07") for display */
export function formatDate(iso: string | undefined | null): string {
  if (!iso) return '—';
  const date = new Date(iso);
  if (isNaN(date.getTime())) return iso;
  return date.toLocaleDateString('en-PH', {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
  });
}

/** Truncate a hash string for display: "7f8a12c0...3d9f" */
export function truncateHash(hash: string | undefined | null, startLen = 8, endLen = 4): string {
  if (!hash) return '—';
  if (hash.length <= startLen + endLen + 3) return hash;
  return `${hash.slice(0, startLen)}...${hash.slice(-endLen)}`;
}

/** Generate a UUID v4 for Idempotency-Key and X-Correlation-ID */
export function generateUUID(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID();
  }
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
    const r = (Math.random() * 16) | 0;
    const v = c === 'x' ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

/** Format seconds to human-readable duration "Xm Ys" */
export function formatDuration(seconds: number | undefined | null): string {
  if (seconds === undefined || seconds === null) return '0s';
  const m = Math.floor(seconds / 60);
  const s = Math.floor(seconds % 60);
  if (m === 0) return `${s}s`;
  return `${m}m ${s}s`;
}

/** Get status pill CSS class based on status string */
export function getStatusClass(status: string | undefined | null): string {
  if (!status) return 'pill-closed';
  const s = status.toUpperCase();
  if (['ACTIVE', 'VERIFIED', 'POSTED', 'COMPLETED', 'PASSED', 'APPROVED'].includes(s))
    return 'pill-active';
  if (['PENDING', 'IN_REVIEW', 'PROCESSING', 'RUNNING', 'IN_PROGRESS', 'INITIATED'].includes(s))
    return 'pill-pending';
  if (['FROZEN', 'FAILED', 'REJECTED', 'HALTED'].includes(s))
    return 'pill-frozen';
  return 'pill-closed';
}
