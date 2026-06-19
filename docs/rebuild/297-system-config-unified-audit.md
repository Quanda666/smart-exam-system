# 297. System Config Unified Audit

## Scope
- Add system configuration change history to the unified administrator log workbench.
- Keep the existing local config audit drawer compatible.
- Make every config audit row copyable as a stable evidence ID and deep link.

## Backend
- Added unified monitor endpoints:
  - `GET /api/monitor/system-config-logs`
  - `GET /api/monitor/system-config-logs/export`
- The monitor endpoints delegate to `SystemConfigService`, so the local config panel and unified log page read the same audit source.
- Added `logId` filtering to config audit queries and exports for exact evidence lookup.
- Kept `/api/system/configs/audit` and `/api/system/configs/audit/export` available for the existing configuration panel.

## Frontend
- Added `System Config Audit` to `SystemLog.vue`.
- Supported filters:
  - log ID
  - keyword
  - category
  - config key
  - actor ID
  - time range
- Added CSV export from the unified tab.
- Added route hydration:
  - `/monitor/logs?systemConfigLogId=<id>`
  - `/monitor/logs?tab=systemConfig&logId=<id>`
- Added copy actions for audit ID and audit deep link in both:
  - unified `SystemLog.vue`
  - local `SystemConfigPanel.vue` audit drawer

## Collaboration Value
- Administrators can still audit changes from the configuration page while operating system settings.
- Security or operations reviewers can use the unified log workbench as the system-wide evidence ledger.
- Copied links now let config changes be referenced from tickets, incident notes, and approval records without manual filtering.

## Verification
- Quality gates now assert the unified monitor endpoints, frontend API, SystemLog tab, deep links, and local config audit evidence buttons.
