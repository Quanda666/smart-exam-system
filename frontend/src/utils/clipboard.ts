export async function copyNotificationAuditIdToClipboard(notificationId?: number | string | null) {
  if (!notificationId) return '';
  const value = String(notificationId);
  await writeClipboardText(value);
  return value;
}

export async function copyOperationLogIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyOperationLogLinkToClipboard(logId?: number | string | null) {
  const link = buildOperationLogDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyNotificationAuditLinkToClipboard(notificationId?: number | string | null) {
  const link = buildNotificationAuditDeepLink(notificationId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyNotificationRelatedAuditLinkToClipboard(relatedType?: string | null, relatedId?: number | string | null) {
  const link = buildNotificationRelatedAuditDeepLink(relatedType, relatedId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyApprovalReminderLogIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyApprovalReminderNotificationAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildApprovalReminderNotificationAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyApprovalReminderAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildApprovalReminderAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copySystemConfigAuditIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copySystemConfigAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildSystemConfigAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyQuestionReviewAuditIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyQuestionReviewAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildQuestionReviewAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyLoginAuditIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyLoginAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildLoginAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyScoreReleaseAuditIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyScoreReleaseAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildScoreReleaseAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyExamApprovalAuditIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyExamApprovalAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildExamApprovalAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyScoreAppealAuditIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyScoreAppealAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildScoreAppealAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export async function copyReviewScoreAuditIdToClipboard(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  await writeClipboardText(value);
  return value;
}

export async function copyReviewScoreAuditLinkToClipboard(logId?: number | string | null) {
  const link = buildReviewScoreAuditDeepLink(logId);
  if (!link) return '';
  await writeClipboardText(link);
  return link;
}

export function buildNotificationAuditDeepLink(notificationId?: number | string | null) {
  if (!notificationId) return '';
  const value = String(notificationId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?notificationId=${encodeURIComponent(value)}`;
}

export function buildOperationLogDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?operationLogId=${encodeURIComponent(value)}`;
}

export function buildNotificationRelatedAuditDeepLink(relatedType?: string | null, relatedId?: number | string | null) {
  if (!relatedType || relatedId === undefined || relatedId === null || relatedId === '') return '';
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?tab=notification&relatedType=${encodeURIComponent(relatedType)}&relatedId=${encodeURIComponent(String(relatedId))}`;
}

export function buildApprovalReminderNotificationAuditDeepLink(logId?: number | string | null) {
  return buildNotificationRelatedAuditDeepLink('APPROVAL_REMINDER', logId);
}

export function buildApprovalReminderAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?approvalReminderLogId=${encodeURIComponent(value)}`;
}

export function buildSystemConfigAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?systemConfigLogId=${encodeURIComponent(value)}`;
}

export function buildQuestionReviewAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?questionReviewLogId=${encodeURIComponent(value)}`;
}

export function buildLoginAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?loginLogId=${encodeURIComponent(value)}`;
}

export function buildScoreReleaseAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?scoreReleaseLogId=${encodeURIComponent(value)}`;
}

export function buildExamApprovalAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?examApprovalLogId=${encodeURIComponent(value)}`;
}

export function buildScoreAppealAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?scoreAppealLogId=${encodeURIComponent(value)}`;
}

export function buildReviewScoreAuditDeepLink(logId?: number | string | null) {
  if (!logId) return '';
  const value = String(logId);
  const origin = typeof window === 'undefined' ? '' : window.location.origin;
  return `${origin}/monitor/logs?reviewScoreLogId=${encodeURIComponent(value)}`;
}

export async function writeClipboardText(value: string) {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value);
    return;
  }
  const textarea = document.createElement('textarea');
  textarea.value = value;
  textarea.setAttribute('readonly', '');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand('copy');
  document.body.removeChild(textarea);
  if (!copied) {
    throw new Error('Clipboard copy failed');
  }
}
