// ============================================================================
// Notifications Service
// ============================================================================
import type { NotificationItem } from '../../types';
import { coreClient } from '../client';
import { SEED_NOTIFICATIONS } from '../seeds';

let localNotifications: NotificationItem[] = [...SEED_NOTIFICATIONS];

export async function getNotifications(): Promise<NotificationItem[]> {
  try {
    const res = await fetch('/api/v1/notifications/my-notifications', {
      headers: coreClient.getAuthHeaders()
    });
    if (res.ok) {
      const data = await res.json();
      const items = data.content || data;
      if (Array.isArray(items)) {
        localNotifications = items;
        return items;
      }
    }
  } catch {
    // fallback
  }
  return [...localNotifications];
}

export async function markNotificationAsRead(id: number): Promise<void> {
  try {
    await fetch(`/api/v1/notifications/${id}/read`, {
      method: 'PATCH',
      headers: coreClient.getAuthHeaders()
    });
  } catch { /* fallback */ }

  const item = localNotifications.find(n => n.id === id);
  if (item) item.isRead = true;
}
