# 343 Teacher Review Audit Links

## Problem
Teacher review actions were recorded through operation logs and notifications, but administrators had to manually jump between the user row, operation log, and notification audit pages to reconstruct what happened after an approval or rejection.

## Changes
- Extended the user-management operation audit banner to carry an optional related object.
- Teacher review approval now records the latest operation log and exposes the related `USER` notification audit link.
- Teacher review rejection now records the latest operation log and exposes the related `USER` notification audit link.
- The banner now offers:
  - copy operation log ID;
  - copy operation log deep-link;
  - copy related notification audit deep-link when the action produced user-facing account notifications.

## Workflow
1. Administrator approves or rejects a teacher registration.
2. User management shows the existing operation audit banner.
3. The administrator can copy the operation log link and the user notification audit link from the same place.
4. The notification audit link opens `/monitor/logs?tab=notification&relatedType=USER&relatedId=<userId>`.

## Validation
- Quality gate source checks assert that teacher review actions attach `USER + userId` relation metadata to the audit banner.
- Frontend build validates the typed audit banner state and copy action.
