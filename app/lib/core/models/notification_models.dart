/// Notification models for Customer Notification Feed (N1 / FSE-601).
class NotificationItem {
  final int notificationId;
  final String title;
  final String message;
  final String channel;
  final bool isRead;
  final DateTime? createdAt;

  NotificationItem({
    required this.notificationId,
    required this.title,
    required this.message,
    required this.channel,
    required this.isRead,
    this.createdAt,
  });

  factory NotificationItem.fromJson(Map<String, dynamic> json) {
    DateTime? parsedDate;
    if (json['createdAt'] != null) {
      parsedDate = DateTime.tryParse(json['createdAt'].toString());
    }
    return NotificationItem(
      notificationId: (json['notificationId'] as num?)?.toInt() ?? 0,
      title: json['title'] as String? ?? 'Notification',
      message: json['message'] as String? ?? '',
      channel: json['channel'] as String? ?? 'IN_APP',
      isRead: json['isRead'] as bool? ?? false,
      createdAt: parsedDate,
    );
  }

  NotificationItem copyWith({bool? isRead}) {
    return NotificationItem(
      notificationId: notificationId,
      title: title,
      message: message,
      channel: channel,
      isRead: isRead ?? this.isRead,
      createdAt: createdAt,
    );
  }
}

class NotificationPageResponse {
  final List<NotificationItem> content;
  final int pageNumber;
  final int pageSize;
  final int totalElements;
  final int totalPages;
  final bool isLast;

  NotificationPageResponse({
    required this.content,
    required this.pageNumber,
    required this.pageSize,
    required this.totalElements,
    required this.totalPages,
    required this.isLast,
  });

  factory NotificationPageResponse.fromJson(Map<String, dynamic> json) {
    final list = json['content'] as List<dynamic>? ?? [];
    return NotificationPageResponse(
      content: list
          .map((e) => NotificationItem.fromJson(e as Map<String, dynamic>))
          .toList(),
      pageNumber: (json['pageNumber'] as num?)?.toInt() ?? 0,
      pageSize: (json['pageSize'] as num?)?.toInt() ?? 20,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? list.length,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 1,
      isLast: json['isLast'] as bool? ?? true,
    );
  }
}
