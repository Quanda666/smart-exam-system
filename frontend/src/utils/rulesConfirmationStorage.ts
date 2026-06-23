const RULES_CONFIRM_PREFIX = 'smart_exam_rules_confirmed_';

function rulesConfirmationKey(attemptId: number) {
  return `${RULES_CONFIRM_PREFIX}${attemptId}`;
}

export function readRulesConfirmation(attemptId: number) {
  const key = rulesConfirmationKey(attemptId);
  try {
    return sessionStorage.getItem(key) === '1' || localStorage.getItem(key) === '1';
  } catch {
    return false;
  }
}

export function persistRulesConfirmation(attemptId: number) {
  const key = rulesConfirmationKey(attemptId);
  try {
    sessionStorage.setItem(key, '1');
    localStorage.setItem(key, '1');
  } catch {
    // Backend confirmation remains the audit source.
  }
}

export function syncRulesConfirmationFromServer(attemptId: number, rulesConfirmedAt?: string | null) {
  if (!rulesConfirmedAt) return false;
  persistRulesConfirmation(attemptId);
  return true;
}
