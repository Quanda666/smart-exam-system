# 282 Score Release Record Copy Audit Link

## Background

Administrators can now jump from an exam's score release record drawer to the global score release audit row. In real review and support workflows, however, administrators often need to share the evidence link without leaving the exam management page.

## Changes

- Added an administrator-only copy action in the exam management score release record drawer.
- The action copies the global score release audit deep link for the selected record.
- Reused `copyScoreReleaseAuditLinkToClipboard` so the drawer and System Log use the same URL contract.
- Kept the existing administrator-only audit navigation action.
- Extended the quality gate to require both deep-link navigation and copyable audit links.

## Three-End Coordination

- Administrator side can either open or copy the exact score publish/revoke audit row from exam management.
- Teacher side still sees the exam-local score release records but not global audit navigation or copy actions.
- Student side remains unaffected; score visibility, notifications, and result access are unchanged.

## Acceptance

- Admin opens an exam's score release records and sees both audit navigation and copy actions.
- Clicking copy stores a URL containing `scoreReleaseLogId=<id>`.
- Opening the copied URL hydrates the System Log score release audit filter.
- Teacher role does not receive the global audit copy action.
