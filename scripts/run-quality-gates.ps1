param(
    [switch]$SkipFrontendBuild,
    [switch]$SkipBackendCompile,
    [switch]$SkipDockerConfig,
    [switch]$SkipGitDiffCheck
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[quality] $Message"
}

function Fail {
    param([string]$Message)
    throw "Quality gate failed: $Message"
}

function Invoke-NativeChecked {
    param(
        [string]$FileName,
        [string[]]$Arguments,
        [string]$WorkingDirectory = ""
    )
    $display = "$FileName $($Arguments -join ' ')"
    Write-Step $display
    $previousLocation = Get-Location
    try {
        if ($WorkingDirectory) {
            Set-Location -LiteralPath $WorkingDirectory
        }
        & $FileName @Arguments
        if ($LASTEXITCODE -ne 0) {
            Fail "$display exited with code $LASTEXITCODE"
        }
    } finally {
        Set-Location -LiteralPath $previousLocation
    }
}

function Test-PowerShellScripts {
    $files = @(
        "scripts\backup-compose-data.ps1",
        "scripts\restore-mysql-backup.ps1",
        "scripts\run-exam-load-smoke.ps1",
        "scripts\check-exam-metrics-smoke.ps1",
        "scripts\write-acceptance-summary.ps1",
        "scripts\check-java-source-hygiene.ps1",
        "scripts\check-frontend-source-hygiene.ps1",
        "scripts\check-deploy-config.ps1",
        "scripts\check-ops-health.ps1",
        "scripts\verify-attempt-resilience.ps1",
        "scripts\prepare-attempt-resilience-fixture.ps1",
        "scripts\run-attempt-resilience-acceptance.ps1",
        "scripts\cleanup-attempt-resilience-fixtures.ps1"
    )
    foreach ($file in $files) {
        if (-not (Test-Path -LiteralPath $file)) {
            Fail "missing script: $file"
        }
        $tokens = $null
        $errors = $null
        [System.Management.Automation.Language.Parser]::ParseFile((Resolve-Path -LiteralPath $file), [ref]$tokens, [ref]$errors) | Out-Null
        if ($errors.Count -gt 0) {
            $detail = ($errors | ForEach-Object { $_.Message }) -join "; "
            Fail "$file has PowerShell syntax errors: $detail"
        }
    }
    Write-Step "PowerShell script syntax PASS"
}

function Test-WorkflowFiles {
    $files = @(
        ".github\workflows\cloud-verify.yml",
        ".github\workflows\nightly-acceptance.yml"
    )
    foreach ($file in $files) {
        if (-not (Test-Path -LiteralPath $file)) {
            Fail "missing workflow: $file"
        }
        $content = Get-Content -LiteralPath $file -Raw
        if ($content -match "docs/init.sql") {
            Fail "$file references removed docs/init.sql"
        }
    }
    Write-Step "workflow file smoke PASS"
}

function Test-NotificationRelationSource {
    $examService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\ExamService.java" -Raw
    $examController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\ExamController.java" -Raw
    $examRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\exam\ExamRequest.java" -Raw
    $userController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\UserController.java" -Raw
    $authService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\AuthService.java" -Raw
    $userService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\UserService.java" -Raw
    $rejectTeacherReviewRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\system\RejectTeacherReviewRequest.java" -Raw
    $overviewService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\OverviewService.java" -Raw
    $tokenStore = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\auth\TokenStore.java" -Raw
    $loginAttemptGuard = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\auth\LoginAttemptGuard.java" -Raw
    $schemaSql = Get-Content -LiteralPath "backend\src\main\resources\db\schema.sql" -Raw
    $migrationRunner = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\config\DatabaseMigrationRunner.java" -Raw
    $menuService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\MenuService.java" -Raw
    $materialLibraryService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\MaterialLibraryService.java" -Raw
    $materialLibraryController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\MaterialLibraryController.java" -Raw
    $questionBankService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\QuestionBankService.java" -Raw
    $documentTextExtractorService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\DocumentTextExtractorService.java" -Raw
    $appVue = Get-Content -LiteralPath "frontend\src\App.vue" -Raw
    $questionBankPanel = Get-Content -LiteralPath "frontend\src\components\QuestionBankPanel.vue" -Raw
    $materialLibraryPanel = Get-Content -LiteralPath "frontend\src\components\MaterialLibraryPanel.vue" -Raw
    $materialApi = Get-Content -LiteralPath "frontend\src\api\material.ts" -Raw
    $roleManagement = Get-Content -LiteralPath "frontend\src\components\RoleManagement.vue" -Raw
    $userProfile = Get-Content -LiteralPath "frontend\src\components\UserProfile.vue" -Raw
    $userManagement = Get-Content -LiteralPath "frontend\src\components\UserManagement.vue" -Raw
    $adminDashboard = Get-Content -LiteralPath "frontend\src\components\AdminDashboard.vue" -Raw
    $adminApi = Get-Content -LiteralPath "frontend\src\api\admin.ts" -Raw
    if ($examService -notmatch 'EXAM_ATTEMPT' -or $examService -notmatch 'notifiedAttempts') {
        Fail "ExamService.publishScores must send score notifications related to EXAM_ATTEMPT and report notifiedAttempts"
    }
    if ($examRequest -notmatch 'import jakarta\.validation\.constraints\.Positive;' -or
        $examRequest -notmatch '@Positive\(message = "paperId must be positive"\)' -or
        $examRequest -notmatch 'List<@Positive\(message = "classId must be positive"\) Long> classIds' -or
        $examRequest -notmatch 'List<@Positive\(message = "classCourseId must be positive"\) Long> classCourseIds' -or
        $examRequest -notmatch 'List<@Positive\(message = "studentUserId must be positive"\) Long> studentUserIds' -or
        $examService -notmatch 'requirePositiveExamPublishId\(request\.getPaperId\(\), "paperId"\);' -or
        $examService -notmatch 'paperId = requirePositiveExamPublishId\(paperId, "paperId"\);' -or
        $examService -notmatch 'requirePositiveExamPublishId\(id, "classId"\)' -or
        $examService -notmatch 'requirePositiveExamPublishId\(id, "classCourseId"\)' -or
        $examService -notmatch 'requirePositiveExamPublishId\(id, "studentUserId"\)' -or
        $examService -notmatch 'private Long requirePositiveExamPublishId\(Long id, String fieldName\)' -or
        $examService -notmatch 'fieldName \+ " must be positive"') {
        Fail "Exam publish requests must reject non-positive paper and target ids before database access"
    }
    if ($userService -notmatch '"ACCOUNT_ENABLED", accountProfileLink\(\), "USER", id' -or
        $userService -notmatch 'private String accountProfileLink\(\)' -or
        $userService -notmatch '"/account/profile"') {
        Fail "UserService account enabled notifications must deep-link to account profile and relate to the enabled user"
    }
    if ($userService -notmatch '"ACCOUNT_PASSWORD_RESET", accountSecurityLink\(\), "USER", id' -or
        $userService -notmatch 'private String accountSecurityLink\(\)' -or
        $userService -notmatch '"/account/profile\?panel=security"') {
        Fail "UserService password reset notifications must deep-link to account security and relate to the affected user"
    }
    if ($authService -notmatch 'NotificationService notificationService' -or
        $authService -notmatch '"ACCOUNT_PASSWORD_CHANGED", accountSecurityLink\(\), "USER", userId' -or
        $authService -notmatch '"ACCOUNT_EMAIL_BOUND", accountSecurityLink\(\), "USER", userId' -or
        $authService -notmatch 'private String accountSecurityLink\(\)' -or
        $authService -notmatch '"/account/profile\?panel=security"') {
        Fail "AuthService self-service account security changes must notify the affected user with USER relation"
    }
    if ($tokenStore -notmatch 'void revokeUserTokens\(Long userId\)' -or
        $tokenStore -notmatch 'DELETE FROM user_token WHERE user_id = \?' -or
        $authService -notmatch 'tokenStore\.revokeUserTokens\(userId\)' -or
        [regex]::Matches($userService, 'tokenStore\.revokeUserTokens\(id\)').Count -lt 3) {
        Fail "Account password changes, resets, disables, and deletes must revoke affected user sessions"
    }
    if ($authService -notmatch 'SELECT id, code, used, expires_at FROM email_verification' -or
        $authService -notmatch 'Long codeId = longValue\(row\.get\("id"\)\)' -or
        $authService -notmatch 'UPDATE email_verification SET used = 1 WHERE id = \? AND used = 0' -or
        $authService -notmatch 'if \(rows == 0\)' -or
        $authService -match 'UPDATE email_verification SET used = 1 WHERE email = \? AND code = \? AND purpose = \?') {
        Fail "AuthService email verification codes must be consumed atomically by verification id"
    }
    if ($authService -notmatch 'public void sendLoginCode\(String email\)[\s\S]*loginAttemptGuard\.assertNotLocked\(account\)' -or
        $authService -notmatch 'public LoginResponse loginByCode\(String email, String code\)[\s\S]*loginAttemptGuard\.assertNotLocked\(account\)' -or
        $authService -notmatch 'public LoginResponse loginByCode\(String email, String code\)[\s\S]*loginAttemptGuard\.recordSuccess\(account\)' -or
        $authService -notmatch 'catch \(RuntimeException ex\)[\s\S]*loginAttemptGuard\.recordFailure\(account\)[\s\S]*throw ex;') {
        Fail "AuthService code login must use LoginAttemptGuard for lock, failure, and success tracking"
    }
    if ($loginAttemptGuard -notmatch 'JdbcTemplate jdbcTemplate' -or
        $loginAttemptGuard -notmatch 'SELECT locked_until FROM login_attempt WHERE account = \?' -or
        $loginAttemptGuard -notmatch 'INSERT INTO login_attempt \(account, failure_count, locked_until\)' -or
        $loginAttemptGuard -notmatch 'ON DUPLICATE KEY UPDATE' -or
        $loginAttemptGuard -notmatch 'DELETE FROM login_attempt WHERE account = \?' -or
        $loginAttemptGuard -match 'ConcurrentHashMap' -or
        $schemaSql -notmatch 'CREATE TABLE IF NOT EXISTS login_attempt' -or
        $schemaSql -notmatch 'PRIMARY KEY \(account\)' -or
        $migrationRunner -notmatch 'ensureLoginAttemptTable\(jdbc\)' -or
        $migrationRunner -notmatch 'CREATE TABLE IF NOT EXISTS login_attempt') {
        Fail "LoginAttemptGuard must persist lock state in the database for multi-instance authentication"
    }
    if ($authService -notmatch 'notifyAdminsTeacherRegistration\(jdbcTemplate, userId, realName\)' -or
        $authService -notmatch '"ACCOUNT_REVIEW", adminUserReviewLink\(userId\), "USER", userId' -or
        $authService -notmatch 'private String adminUserReviewLink\(Long userId\)' -or
        $authService -notmatch '"/system/users\?userId=" \+ userId' -or
        $authService -notmatch 'INSERT INTO teacher_profile \(user_id, teacher_no, title, introduction, status\)[\s\S]*VALUES \(\?, \?, \?, \?, 0\)') {
        Fail "Teacher self-registration must notify administrators with exact user review deep-links"
    }
    if ($userController -notmatch '@RequestParam\(required = false\) Integer teacherStatus' -or
        $userController -notmatch '@RequestParam\(required = false\) Long userId' -or
        $userController -notmatch 'userService\.listUsers\(keyword, role, status, teacherStatus, userId, page, size\)' -or
        $userService -notmatch 'listUsers\(String keyword, String role, Integer status,\s*Integer teacherStatus, Long userId, int page, int size\)' -or
        $userService -notmatch 'AND \(\? IS NULL OR u\.id = \?\)' -or
        $adminApi -notmatch 'userId\?: number \| string \| null' -or
        $adminApi -notmatch 'teacherStatus\?: number \| null' -or
        $adminApi -notmatch "params\.set\('userId', String\(query\.userId\)\)" -or
        $adminApi -notmatch "params\.set\('teacherStatus', String\(query\.teacherStatus\)\)" -or
        $userManagement -notmatch 'useRoute' -or
        $userManagement -notmatch 'route\.query\.userId' -or
        $userManagement -notmatch 'focusedUserId' -or
        $userManagement -notmatch 'userId: selectedScope\.value\.type === ''class'' \? undefined : \(focusedUserId\.value \|\| undefined\)' -or
        $userManagement -notmatch 'userRowClassName' -or
        $userManagement -notmatch 'user-row-focused') {
        Fail "User management must support exact userId deep-links and focused review rows"
    }
    if ($userService -notmatch 'pendingTeacherReviews' -or
        $userService -notmatch 'rejectedTeacherReviews' -or
        $userService -notmatch "tr\.role_code = 'TEACHER'" -or
        $userService -notmatch 'JOIN teacher_profile tp ON tp\.user_id = tu\.id AND tp\.deleted = 0' -or
        $userService -notmatch 'AND tp\.status = 0' -or
        $userService -notmatch 'AND tp\.status = 2' -or
        $userService -notmatch 'approvePendingTeacherProfile\(jt, id\)' -or
        $overviewService -notmatch 'pendingTeacherReviews' -or
        $overviewService -notmatch 'rejectedTeacherReviews' -or
        $overviewService -notmatch "r\.role_code = 'TEACHER'" -or
        $overviewService -notmatch 'AND tp\.status = 0' -or
        $overviewService -notmatch 'AND tp\.status = 2' -or
        $adminDashboard -notmatch 'pendingTeacherReviewRoute' -or
        $adminDashboard -notmatch 'rejectedTeacherReviewRoute' -or
        $adminDashboard -notmatch '/system/users\?role=TEACHER&status=0&teacherStatus=0' -or
        $adminDashboard -notmatch '/system/users\?role=TEACHER&status=0&teacherStatus=2' -or
        $adminDashboard -notmatch 'pendingTeacherReviews' -or
        $adminDashboard -notmatch 'rejectedTeacherReviews' -or
        $adminDashboard -notmatch 'openStatCard\(card\.route\)' -or
        $userManagement -notmatch 'openTeacherReviewQueue' -or
        $userManagement -notmatch 'teacherReview:2' -or
        $userManagement -notmatch 'rejectedTeacherReviews' -or
        $userManagement -notmatch 'currentUserListQuery' -or
        $userManagement -notmatch 'currentListRole' -or
        $userManagement -notmatch 'currentListTeacherStatus' -or
        $userManagement -notmatch 'const response = await listUsers\(currentUserListQuery\(1, 10000\)\)' -or
        $userManagement -notmatch 'const exportList = selectedScope\.value\.type === ''class''' -or
        $userManagement -notmatch 'exportFilePrefix' -or
        $userManagement -notmatch 'useRouter' -or
        $userManagement -notmatch 'applyRouteFilters' -or
        $userManagement -notmatch 'routeRole\(\)' -or
        $userManagement -notmatch 'routeStatus\(\)' -or
        $userManagement -notmatch 'routeTeacherStatus\(\)' -or
        $userManagement -notmatch 'isPendingTeacher' -or
        $userManagement -notmatch '\[route\.query\.userId, route\.query\.role, route\.query\.status, route\.query\.teacherStatus\]') {
        Fail "Administrator teacher review entry must expose exact pending teacher review counts and hydrate user-list review deep-links"
    }
    if ($rejectTeacherReviewRequest -notmatch 'class RejectTeacherReviewRequest' -or
        $rejectTeacherReviewRequest -notmatch '@NotBlank' -or
        $userController -notmatch '@PutMapping\("/\{id\}/teacher-review/reject"\)' -or
        $userController -notmatch 'RejectTeacherReviewRequest request' -or
        $userController -notmatch 'userService\.rejectTeacherReview\(id, request\.getReason\(\)\)' -or
        $userController -notmatch 'teacherStatus", 2' -or
        $userService -notmatch 'public void rejectTeacherReview\(Long id, String reason\)' -or
        $userService -notmatch 'SET tp\.status = 2' -or
        $userService -notmatch 'AND tp\.status = 0' -or
        $userService -notmatch '"ACCOUNT_REJECTED", accountProfileLink\(\), "USER", id' -or
        $userService -notmatch 'tp\.status IN \(0, 2\)' -or
        $adminApi -notmatch 'rejectTeacherReview\(id: number, reason: string\)' -or
        $userManagement -notmatch 'rejectPendingTeacher' -or
        $userManagement -notmatch 'isRejectedTeacher' -or
        $userManagement -notmatch 'teacherStatus === 2' -or
        $userManagement -notmatch 'ElMessageBox\.prompt' -or
        $userManagement -notmatch 'rejectTeacherReview\(row\.id' -or
        $userManagement -notmatch 'copyLatestNotificationAuditLink' -or
        $userManagement -notmatch 'relatedType\?: string' -or
        $userManagement -notmatch "rememberOperationAudit\([\s\S]*\[response\.data\.operationLogId\][\s\S]*'USER'[\s\S]*row\.id" -or
        $userManagement -notmatch "isTeacherReviewAction \? 'USER' : undefined") {
        Fail "Teacher review must support explicit rejection, rejected status display, audit, and notification"
    }
    if ($userService -notmatch 'record UserStatusUpdateResult' -or
        $userService -notmatch 'boolean teacherReviewApproved' -or
        $userService -notmatch 'approvePendingTeacherProfile\(jt, id\) > 0' -or
        $userController -notmatch 'UserService\.UserStatusUpdateResult result = userService\.updateStatus' -or
        $userController -notmatch 'result\.teacherReviewApproved\(\)' -or
        $userController -notmatch '"teacherReviewApproved", result\.teacherReviewApproved\(\)' -or
        $adminApi -notmatch 'teacherReviewApproved\?: boolean' -or
        $adminApi -notmatch 'operatorName\?: string' -or
        $adminApi -notmatch 'createdAt\?: string' -or
        $userManagement -notmatch 'listOperationLogs' -or
        $userManagement -notmatch 'recentTeacherReviewLogs' -or
        $userManagement -notmatch 'loadRecentTeacherReviewLogs' -or
        $userManagement -notmatch 'copyRecentTeacherReviewLogLink' -or
        $userManagement -notmatch 'listOperationLogs\(1, 6, \{ action:' -or
        $userManagement -notmatch 'Boolean\(response\.data\.teacherReviewApproved\)') {
        Fail "Teacher review queues must expose recent operation audit history and backend approval semantics"
    }
    if ($appVue -notmatch "ACCOUNT_PROFILE_PATH = '/account/profile'" -or
        $appVue -notmatch 'isAccountProfilePath' -or
        $appVue -notmatch 'openAccountProfileRoute' -or
        $appVue -notmatch 'accountProfilePanelFromUrl' -or
        $appVue -notmatch 'openAccountPanel\(panel\)' -or
        $appVue -notmatch 'userProfileRef' -or
        $appVue -notmatch 'route\.fullPath') {
        Fail "App must route account profile notifications to the user profile dialog without requiring menu permission"
    }
    if ($appVue -notmatch 'MaterialLibraryPanel' -or
        $appVue -notmatch "currentPath\.value === '/materials'" -or
        $menuService -notmatch '"/materials"' -or
        $menuService -notmatch '"/materials", "Notebook", List\.of\("ADMIN", "TEACHER"\)' -or
        $migrationRunner -notmatch "UNION ALL SELECT 'ADMIN', '/materials'" -or
        $migrationRunner -notmatch "UNION ALL SELECT 'TEACHER', '/materials'" -or
        $roleManagement -notmatch "'/materials'" -or
        $materialLibraryPanel -notmatch 'uploadCourseMaterial' -or
        $materialLibraryPanel -notmatch 'listCourseMaterials' -or
        $materialLibraryPanel -notmatch 'fetchCourseMaterial' -or
        $materialLibraryPanel -notmatch 'generateQuestionsFromLibraryMaterial' -or
        $materialLibraryPanel -notmatch 'saveGeneratedQuestions' -or
        $materialLibraryPanel -notmatch 'deleteCourseMaterial' -or
        $materialLibraryPanel -notmatch 'removeMaterial' -or
        $materialLibraryPanel -notmatch 'ElMessageBox\.confirm' -or
        $materialLibraryPanel -notmatch 'generationDrafts' -or
        $materialLibraryPanel -notmatch 'selectedDraftIndexes' -or
        $materialLibraryPanel -notmatch 'selectedGenerationDrafts' -or
        $materialLibraryPanel -notmatch 'selectedDraftCount' -or
        $materialLibraryPanel -notmatch 'toggleDraftSelection' -or
        $materialLibraryPanel -notmatch 'selectAllGenerationDrafts' -or
        $materialLibraryPanel -notmatch 'clearDraftSelection' -or
        $materialLibraryPanel -notmatch 'saveLibraryDrafts' -or
        $materialLibraryPanel -notmatch 'saveGeneratedQuestions\(selectedGenerationDrafts\.value\.map' -or
        $materialLibraryPanel -notmatch 'correctAnswerText\(draft\)' -or
        $materialLibraryPanel -notmatch 'isCorrectOption\(option\.correct\)' -or
        $materialLibraryPanel -notmatch 'generation-answer' -or
        $materialLibraryPanel -notmatch 'AI_MATERIAL_TYPE_COUNT = 30' -or
        $materialLibraryPanel -notmatch 'AI_MATERIAL_TOTAL_COUNT = 30' -or
        $materialLibraryPanel -notmatch ':max="AI_MATERIAL_TYPE_COUNT"' -or
        $materialLibraryPanel -notmatch 'generationTotalInvalid' -or
        $materialLibraryPanel -notmatch 'typeCounts: \{ \.\.\.generationCounts \}' -or
        $materialLibraryPanel -notmatch 'MATERIAL_DOCUMENT_MAX_FILE_BYTES = 25 \* 1024 \* 1024' -or
        $materialLibraryPanel -notmatch 'MATERIAL_FILENAME_MAX_LENGTH = 255' -or
        $materialLibraryPanel -notmatch 'MATERIAL_TITLE_MAX_LENGTH = 200' -or
        $materialLibraryPanel -notmatch 'MATERIAL_DOCUMENT_SUPPORTED_EXTENSIONS' -or
        $materialLibraryPanel -notmatch ':accept="MATERIAL_DOCUMENT_ACCEPT"' -or
        $materialLibraryController -notmatch '@DeleteMapping\("/\{id\}"\)' -or
        $materialLibraryController -notmatch 'materialLibraryService\.deleteMaterial\(id, user\)' -or
        $materialLibraryController -notmatch 'OperationLogService' -or
        $materialLibraryController -notmatch 'withOperationLogId' -or
        $materialLibraryController -notmatch 'UPLOAD_MATERIAL' -or
        $materialLibraryController -notmatch 'GENERATE_MATERIAL_QUESTIONS' -or
        $materialLibraryController -notmatch 'DELETE_MATERIAL' -or
        $materialLibraryController -notmatch 'result\.put\("operationLogId", operationLogId\)' -or
        $materialLibraryService -notmatch 'public Map<String, Object> deleteMaterial\(Long materialId, AuthUser user\)' -or
        $materialLibraryService -notmatch 'UPDATE course_material[\s\S]*SET deleted = 1, status = 0, updated_at = CURRENT_TIMESTAMP[\s\S]*WHERE id = \? AND deleted = 0' -or
        $materialLibraryService -notmatch 'Map<String, Object> material = getMaterialRow\(jdbc, materialId, user\)' -or
        $materialLibraryService -notmatch 'question\.setSourceType\("AI_RAG"\)' -or
        $materialLibraryService -notmatch 'question\.setMaterialId\(materialId\)' -or
        $materialLibraryPanel -notmatch 'lastMaterialOperationAudit' -or
        $materialLibraryPanel -notmatch 'rememberMaterialOperationAudit' -or
        $materialLibraryPanel -notmatch 'copyLatestMaterialOperationAuditId' -or
        $materialLibraryPanel -notmatch 'copyLatestMaterialOperationAuditLink' -or
        $materialLibraryPanel -notmatch 'copyOperationLogIdToClipboard' -or
        $materialLibraryPanel -notmatch 'copyQuestionReviewAuditLinkToClipboard' -or
        $materialLibraryPanel -notmatch 'response\.data\.operationLogId' -or
        $materialLibraryPanel -notmatch 'response\.data\.questions' -or
        $materialLibraryPanel -notmatch 'response\.data\.questionReviewLogIds' -or
        $materialLibraryPanel -notmatch 'material-operation-audit' -or
        $materialApi -notmatch 'operationLogId\?: number \| string \| null' -or
        $materialApi -notmatch 'MaterialQuestionGenerateResult' -or
        $materialApi -notmatch 'MaterialDeleteResult' -or
        $materialApi -notmatch 'deleteCourseMaterial' -or
        $materialApi -notmatch 'deleteJson<MaterialDeleteResult>' -or
        $questionBankService -notmatch 'validateQuestionSource\(jdbcTemplate, request, creator\)' -or
        $questionBankService -notmatch 'validateQuestionSource\(jdbcTemplate,[\s\S]*request\.getMaterialId\(\) == null \? longValue\(before\.get\("materialId"\)\) : request\.getMaterialId\(\)' -or
        $questionBankService -notmatch '"AI_RAG"\.equals\(sourceType\) && materialId == null' -or
        $questionBankService -notmatch 'FROM course_material' -or
        $questionBankService -notmatch 'WHERE id = \? AND deleted = 0' -or
        $questionBankService -notmatch 'Question source material subject must match question subject' -or
        $questionBankService -notmatch 'Question source material is not accessible' -or
        $materialApi -notmatch '/api/materials/\$\{id\}' -or
        $materialApi -notmatch '/api/materials') {
        Fail "Material library must be reachable from admin/teacher menus and provide bounded upload/list/detail/generate/delete workflows with audit evidence and source permission checks"
    }
    if ($userProfile -notmatch 'openProfileDialog' -or
        $userProfile -notmatch 'openAccountPanel' -or
        $userProfile -notmatch "panel === 'security'" -or
        $userProfile -notmatch 'defineExpose\(\{ openProfileDialog, openAccountPanel \}\)') {
        Fail "UserProfile must expose a profile dialog opener for account notification deep-links"
    }
    if ($userManagement -notmatch 'copyUserNotificationAuditLink' -or
        $userManagement -notmatch "copyNotificationRelatedAuditLinkToClipboard\('USER', row\.id\)" -or
        $userManagement -notmatch '通知审计') {
        Fail "UserManagement must expose per-user notification audit links for account lifecycle notifications"
    }
    if ($examService -notmatch 'studentResultLink\(attemptId\)' -or
        $examService -notmatch 'private String studentResultLink\(Long attemptId\)' -or
        $examService -notmatch '"/student/results\?attemptId=" \+ attemptId') {
        Fail "ExamService score release and revoke notifications must deep-link students to the exact attempt result"
    }
    if ($examService -match 'sendBatch\(studentIds,\s*"Score released') {
        Fail "ExamService.publishScores must not use unscoped sendBatch for score release notifications"
    }
    if ($examService -notmatch 'notifyPublishedExamStudents' -or
        $examService -match 'sendBatch\(new ArrayList<>\(studentIds\), "New exam: ' -or
        $examService -notmatch 'sendOnceAndReturnId\(studentId, "New exam: ' -or
        $examService -notmatch 'WHERE exam_id = \?[\s\S]*AND user_id IN \(%s\)[\s\S]*AND attempt_no = 1' -or
        $examService -notmatch '"EXAM",\s*studentExamLink\(attemptId\),\s*"EXAM_ATTEMPT",\s*attemptId' -or
        $examService -notmatch 'private String studentExamLink\(Long attemptId\)' -or
        $examService -notmatch '"/student/exams\?attemptId=" \+ attemptId') {
        Fail "ExamService.publishApprovedExam must notify students idempotently with EXAM_ATTEMPT related attempt ids and exact exam deep-links"
    }
    if ($examService -notmatch 'SCORE_REVOKED' -or $examService -notmatch 'visibleAttemptsBeforeRevoke' -or $examService -notmatch 'notifiedStudents') {
        Fail "ExamService.revokeScores must notify affected students and report revoke notification statistics"
    }
    if ($examService -notmatch 'List<Map<String, Object>> visibleAttemptRows = jt\.queryForList\("""[\s\S]*?JOIN score_release sr ON sr\.exam_id = a\.exam_id AND sr\.status = 1[\s\S]*?WHERE a\.exam_id = \? AND a\.status = 5 AND a\.score IS NOT NULL[\s\S]*?sa\.handling_result = ''RECHECK_REQUIRED''') {
        Fail "ExamService.revokeScores must count and notify only currently visible released scores"
    }
    if ($examService -notmatch 'ScoreRevokeRequest' -or
        $examService -notmatch 'scoreRevokeReason' -or
        $examService -notmatch 'recordScoreReleaseLog' -or
        $examService -notmatch 'SCORE_RELEASE_ACTION_REVOKE' -or
        $examService -notmatch 'Scores have not been published' -or
        $examService -notmatch 'revoke_reason = VALUES\(revoke_reason\)' -or
        $examService -notmatch 'note = VALUES\(note\)') {
        Fail "ExamService.revokeScores must require and persist a score revoke reason"
    }
    if ($examService -notmatch 'listScoreReleaseLogs' -or
        $examService -notmatch 'exportScoreReleaseLogs' -or
        $examService -notmatch '"Log ID", "Time", "Exam", "Action"' -or
        $examService -notmatch 'CsvExport\.build\(headers, rows\)' -or
        $examService -notmatch 'FROM score_release_log' -or
        $examService -notmatch 'visible_attempt_count AS visibleAttemptCount' -or
        $examService -notmatch 'SCORE_RELEASE_ACTION_PUBLISH') {
        Fail "ExamService must persist and expose score release audit logs"
    }
    if ($examService -notmatch 'listApprovalLogs' -or
        $examService -notmatch 'exportApprovalLogs' -or
        $examService -notmatch 'FROM exam_approval_log l' -or
        $examService -notmatch '"Log ID", "Time", "Exam", "Action"' -or
        $examService -notmatch 'candidate_count AS candidateCount' -or
        $examService -notmatch 'CsvExport\.build\(headers, rows\)') {
        Fail "ExamService must expose scoped approval audit logs and export"
    }
    if ($examService -notmatch 'private Long recordScoreReleaseLog' -or
        [regex]::Matches($examService, 'Long scoreReleaseLogId = recordScoreReleaseLog').Count -lt 2 -or
        [regex]::Matches($examService, 'state\.put\("scoreReleaseLogId", scoreReleaseLogId\)').Count -lt 2) {
        Fail "ExamService score release and revoke responses must include score release log ids"
    }
    if ($examService -notmatch 'PublishNotificationStats' -or
        $examService -notmatch 'candidate_count, notified_student_count, notified_attempt_count' -or
        $examService -notmatch 'l\.candidate_count AS candidateCount' -or
        $examService -notmatch 'l\.notified_student_count AS notifiedStudentCount' -or
        $examService -notmatch 'l\.notified_attempt_count AS notifiedAttemptCount' -or
        $examService -notmatch 'new PublishNotificationStats\(studentIds\.size\(\), notifiedStudentIds\.size\(\), notifiedAttemptCount\)') {
        Fail "ExamService approval logs must persist and expose exam publish notification statistics"
    }
    if ($examService -notmatch 'attachPublishNotificationStats' -or
        $examService -notmatch '"publishCandidateCount"' -or
        $examService -notmatch '"publishNotifiedStudentCount"' -or
        $examService -notmatch '"publishNotifiedAttemptCount"' -or
        [regex]::Matches($examService, 'attachPublishNotificationStats\(result, publishStats\)').Count -lt 2) {
        Fail "ExamService publish and approve responses must include publish notification statistics"
    }
    if ($examService -notmatch 'notifyExamCreator' -or
        $examService -notmatch 'notificationService\.send\(creatorId, title, content, "EXAM_APPROVAL", link, "EXAM", examId\)' -or
        $examService -notmatch 'teacherExamLink\(id\)' -or
        $examService -notmatch 'private String teacherExamLink\(Long examId\)' -or
        $examService -notmatch '"/exam-tasks\?examId=" \+ examId' -or
        $examService -match '"/exam/tasks"') {
        Fail "Exam approval result notifications must be related to the exam and deep-link to the exact teacher exam row"
    }
    if ($examService -notmatch 'Long reminderLogId = recordApprovalReminderLog' -or
        $examService -notmatch '"EXAM_APPROVAL", approvalReminderLink\(reminderLogId\), "APPROVAL_REMINDER", reminderLogId' -or
        $examService -notmatch 'private String approvalReminderLink\(Long reminderLogId\)' -or
        $examService -notmatch '"/exam-approvals\?reminderLogId=" \+ reminderLogId' -or
        $examService -notmatch 'result\.put\("reminderLogId", reminderLogId\)') {
        Fail "Exam approval overdue reminders must relate notifications to reminder log ids and deep-link to the exact reminder log"
    }
    if ($examService -notmatch 'exportApprovalReminderLogs' -or
        $examService -notmatch 'listApprovalReminderLogs\(int page, int size, Long logId, AuthUser user\)' -or
        $examService -notmatch 'WHERE l\.id = \?' -or
        $examService -notmatch '"Reminder Log ID", "Time", "Status", "Trigger Source"' -or
        $examService -notmatch 'FROM exam_approval_reminder_log l' -or
        $examService -notmatch 'safeExportName\("approval-reminder"\)') {
        Fail "ExamService must filter and export approval reminder audit logs"
    }
    if ($examService -notmatch 'scoreRevokedAt' -or
        $examService -notmatch 'scoreReleaseNote' -or
        $examService -notmatch 'scorePublishNote' -or
        $examService -notmatch 'scoreRevokeReason' -or
        $examService -notmatch 'scorePublishedByName' -or
        $examService -notmatch 'scoreRevokedByName' -or
        $examService -notmatch 'LEFT JOIN sys_user pub ON pub.id = sr.published_by' -or
        $examService -notmatch 'LEFT JOIN sys_user rev ON rev.id = sr.revoked_by') {
        Fail "ExamService.listTeacherExams must expose score release audit actor fields"
    }
    if ($examService -notmatch 'redactSubmitScore' -or
        $examService -notmatch 'scoreVisibility' -or
        $examService -match 'result\.put\("score",\s*(totalScore|current\.get\("score"\))') {
        Fail "ExamService submit responses must not expose scores before score release"
    }
    if ($examService -notmatch 'SELECT submit_time AS submitTime' -or
        $examService -notmatch 'result\.put\("submitTime", submitMeta\.get\("submitTime"\)\)' -or
        $examService -notmatch 'submit_time AS submitTime') {
        Fail "ExamService first submit and replay responses must expose authoritative submitTime"
    }
    if ($examService -notmatch 'private List<Map<String, Object>> loadExamQuestions' -or
        $examService -notmatch 'exam\.put\("questions", sanitizeStudentTakingQuestions\(questions\)\)' -or
        $examService -notmatch 'private List<Map<String, Object>> sanitizeStudentTakingQuestions' -or
        $examService -notmatch 'STUDENT_TAKING_SENSITIVE_KEYS' -or
        $examService -notmatch '"correctanswer"' -or
        $examService -notmatch '"iscorrect"' -or
        $examService -notmatch '"analysis"' -or
        $examService -notmatch '"answercontent"' -or
        $examService -notmatch 'private Map<String, Object> sanitizeStudentTakingMap\(Map<\?, \?> raw\)' -or
        $examService -notmatch 'private Object sanitizeStudentTakingValue\(Object value\)' -or
        $examService -notmatch 'private boolean isStudentTakingSensitiveKey\(String key\)' -or
        $examService -notmatch 'key\.replaceAll\("\[\^A-Za-z0-9\]", ""\)\.toLowerCase\(Locale\.ROOT\)' -or
        $examService -match 'private List<Map<String, Object>> loadExamQuestions[\s\S]*?(correctAnswer|correct_answer|analysis)[\s\S]*?private List<Map<String, Object>> loadExamQuestionOptions') {
        Fail "ExamService.startExam must not expose correct answers or analysis in student taking payloads"
    }
    if ($examService -notmatch 'private List<Map<String, Object>> loadExamQuestionOptions[\s\S]*if \(hasQuestionSnapshot\(jt, examId\)\)[\s\S]*FROM exam_question_option_snapshot[\s\S]*WHERE exam_id = \? AND question_id = \?[\s\S]*FROM question_option' -or
        $examService -match 'private List<Map<String, Object>> loadExamQuestionOptions[\s\S]*SELECT COUNT\(\*\)[\s\S]*FROM exam_question_option_snapshot') {
        Fail "ExamService.startExam option payloads must use frozen option snapshots whenever a question snapshot exists"
    }
    if ($examService -notmatch 'e\.pass_score AS passScore,[\s\S]*FROM exam_question_snapshot eqs_score[\s\S]*WHERE eqs_score\.exam_id = e\.id[\s\S]*p\.total_score, 0\) AS totalScore' -or
        $examService -notmatch 'List<Map<String, Object>> questions = loadExamQuestions\(jt, examId, \(\(Number\) paperId\)\.longValue\(\)\)') {
        Fail "ExamService.startExam totalScore must use the same snapshot-first source as the taking questions"
    }
    if ($examService -notmatch 'CASE WHEN COALESCE\(sr\.status, 0\) = 1 AND a\.status = 5' -or
        $examService -notmatch 'NOT EXISTS \(' -or
        $examService -notmatch 'FROM score_appeal sa' -or
        $examService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'" -or
        $examService -notmatch 'CASE WHEN COALESCE\(sr\.status, 0\) = 1 AND a\.status = 5 AND a\.score IS NOT NULL' -or
        $examService -notmatch "THEN 'PENDING_RECHECK'" -or
        $examService -notmatch "WHEN a\.status = 4 THEN 'PENDING_REVIEW'" -or
        $examService -notmatch "WHEN a\.score IS NULL THEN 'PENDING_SCORE'" -or
        $examService -notmatch 'END AS scoreVisibility') {
        Fail "ExamService.listStudentExams must hide scores unless they are released, finalized, scored, and not in open recheck"
    }
    if ($examService -notmatch 'requireExamReadyForScoreRelease' -or
        [regex]::Matches($examService, 'lockExamForScoreReleaseTransition\(jt, id\);').Count -lt 2 -or
        $examService -notmatch 'SELECT id[\s\S]*FROM exam[\s\S]*WHERE id = \? AND deleted = 0[\s\S]*FOR UPDATE' -or
        $examService -notmatch 'Scores have already been published' -or
        $examService -notmatch 'nonFinalStartedAttemptCount' -or
        $examService -notmatch 'completedAttemptCount' -or
        $examService -notmatch 'pendingReviewAttemptCount' -or
        $examService -notmatch 'pendingAnswerReviewCount' -or
        $examService -notmatch 'pendingScoreAppealCount' -or
        $examService -notmatch 'openRecheckAppealCount' -or
        $examService -notmatch 'unscoredCompletedAttemptCount' -or
        $examService -notmatch 'public Map<String, Object> scoreReleaseReadiness' -or
        $examService -notmatch 'PENDING_REVIEW_ANSWERS' -or
        $examService -notmatch 'JOIN exam_attempt a ON a\.id = ar\.attempt_id' -or
        $examService -notmatch 'LEFT JOIN exam_attempt aa ON aa\.id = sa\.attempt_id' -or
        $examService -notmatch 'aa\.id IS NOT NULL AND aa\.exam_id = e\.id' -or
        $examService -notmatch 'aa\.id IS NULL AND sa\.exam_id = e\.id' -or
        $examService -notmatch 'LEFT JOIN exam_attempt a ON a\.id = sa\.attempt_id' -or
        $examService -notmatch 'a\.id IS NOT NULL AND a\.exam_id = \?' -or
        $examService -notmatch 'a\.id IS NULL AND sa\.exam_id = \?' -or
        $examService -notmatch 'Scores cannot be published while started attempts are not finalized' -or
        $examService -notmatch 'Scores cannot be published while score appeals are pending' -or
        $examService -notmatch 'Scores cannot be published while recheck appeals are open' -or
        $examService -notmatch 'Scores cannot be published while answers are still waiting for review' -or
        $examService -notmatch 'sa\.status = 0' -or
        $examService -notmatch 'Scores cannot be published while completed attempts have missing scores' -or
        $examService -notmatch 'WHERE exam_id = \? AND status = 5 AND score IS NOT NULL' -or
        $examService -notmatch "sa\.status = 1[\s\S]*sa\.handling_result = 'RECHECK_REQUIRED'" -or
        $examService -notmatch 'Scores can only be published after the exam has ended' -or
        $examService -notmatch 'appendScoreReleaseReadiness' -or
        $examService -notmatch 'scoreReleaseBlockers' -or
        $examService -notmatch 'scoreReleaseReady' -or
        $examService -notmatch 'EXAM_NOT_ENDED' -or
        $examService -notmatch 'OPEN_RECHECK') {
        Fail "ExamService.publishScores must require ended exams, finalized scored attempts, no open recheck appeals, and expose release blockers"
    }
    if ($examService -notmatch 'private boolean attemptExamOpen' -or
        $examService -notmatch 'public Map<String, Object> attemptHeartbeat' -or
        $examService -notmatch 'Exam is no longer open for taking' -or
        $examService -notmatch 'forcedSubmitted' -or
        $examService -notmatch 'if \(!attemptExamOpen\(attempt\)\)' -or
        $examService -notmatch 'Exam is not open for submission') {
        Fail "ExamService attempt heartbeat and submit paths must reject or finalize attempts when the exam is no longer published"
    }
    if ($examService -notmatch 'finalizeAttemptOnStartIfClosedOrExpired' -or
        $examService -notmatch 'startFinalization = finalizeAttemptOnStartIfClosedOrExpired' -or
        $examService -notmatch 'startFinalization\.isEmpty\(\)\) \{[\s\S]*return startFinalization;[\s\S]*validateExamWindow\(exam\)' -or
        $examService -notmatch 'if \(remaining <= 0\) \{[\s\S]*Auto submitted when student re-entered after deadline' -or
        $examService -notmatch 'Auto submitted when student re-entered after deadline' -or
        $examService -notmatch 'Exam is no longer open when student re-entered') {
        Fail "ExamService.startExam must finalize in-progress closed or expired attempts before rejecting the exam window"
    }
    $startExamRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\exam\StartExamRequest.java" -Raw
    $answerRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\exam\AnswerRequest.java" -Raw
    $draftRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\exam\DraftRequest.java" -Raw
    if ($examController -notmatch 'StartExamRequest' -or
        $examController -notmatch '@RequestBody\(required = false\) StartExamRequest request' -or
        $examService -notmatch 'requireStartRulesConfirmed' -or
        $examService -notmatch 'Exam rules must be confirmed before starting' -or
        $examService -notmatch 'attemptStatus == 0' -or
        $examService -notmatch 'shouldRecordInProgressRulesConfirmation' -or
        $examService -notmatch 'WHERE id = \? AND status = 1' -or
        $examService -notmatch 'rules_confirmed_at = COALESCE\(rules_confirmed_at, NOW\(\)\)' -or
        $examService -notmatch 'rules_confirmed_at AS rulesConfirmedAt' -or
        $startExamRequest -notmatch 'rulesConfirmed') {
        Fail "Exam start must require rules confirmation before first student entry"
    }
    if ($examService -notmatch 'if \(status >= 2\) \{[\s\S]*submittedAttemptResult\(jt, attempt, true\)[\s\S]*result\.put\("submitted", true\)[\s\S]*return result;' -or
        $examService -match 'This exam attempt has already been submitted') {
        Fail "ExamService.startExam must return an idempotent submitted result instead of throwing for submitted attempts"
    }
    if ($examService -notmatch 'cleanupFinalizedAttemptState\(jt, attemptId, examId, userId\)' -or
        $examService -notmatch 'private void cleanupFinalizedAttemptState' -or
        $examService -notmatch 'DELETE FROM exam_answer_draft WHERE attempt_id = \?' -or
        $examService -notmatch 'examDraftCacheService\.delete\(attemptId\)' -or
        $examService -notmatch 'markMonitorSessionSubmitted\(jt, attemptId, examId, userId\)') {
        Fail "ExamService submitted attempt replay must clean residual drafts and mark monitor session submitted"
    }
    if ($answerRequest -notmatch 'import jakarta\.validation\.constraints\.Positive;' -or
        $answerRequest -notmatch 'import jakarta\.validation\.constraints\.Size;' -or
        $answerRequest -notmatch '@Size\(max = 1000, message = "answers cannot contain more than 1000 entries"\)' -or
        $answerRequest -notmatch 'Map<@Positive\(message = "questionId must be positive"\) Long,' -or
        $answerRequest -notmatch '@Size\(max = 20000, message = "answer content must be at most 20000 characters"\) String> answers' -or
        $answerRequest -notmatch '@Size\(max = 80, message = "submitToken must be at most 80 characters"\)' -or
        $examService -notmatch 'MAX_SUBMITTED_ANSWER_COUNT = 1000' -or
        $examService -notmatch 'MAX_SUBMITTED_ANSWER_LENGTH = 20000' -or
        $examService -notmatch 'MAX_SUBMIT_TOKEN_LENGTH = 80' -or
        $examService -notmatch 'answers\.size\(\) > MAX_SUBMITTED_ANSWER_COUNT' -or
        $examService -notmatch 'requireBoundedAnswerContent\(answers\.get\(questionId\)\);' -or
        $examService -notmatch 'private void requireBoundedAnswerContent\(String answer\)' -or
        $examService -notmatch 'answer\.length\(\) > MAX_SUBMITTED_ANSWER_LENGTH' -or
        $examService -notmatch 'String safeSubmitToken = normalizeSubmitToken\(submitToken\);' -or
        $examService -notmatch 'List<Map<String, Object>> paperQuestions = loadQuestionsForSubmit\(jt, attemptId\);' -or
        $examService -notmatch 'String requestPayloadHash = answerPayloadHash\(answers, paperQuestions\);' -or
        $examService -notmatch 'String payloadHash = answerPayloadHash\(answers, paperQuestions\);' -or
        $examService -notmatch 'private String answerPayloadHash\(Map<Long, String> answers, List<Map<String, Object>> paperQuestions\)' -or
        $examService -notmatch 'for \(Map<String, Object> question : paperQuestions\)' -or
        $examService -notmatch 'sorted\.put\(questionId, canonicalSubmittedAnswerForHash\(answers == null \? null : answers\.get\(questionId\)\)\);' -or
        $examService -notmatch 'private String canonicalSubmittedAnswerForHash\(String answer\)' -or
        $examService -notmatch 'if \(isBlankAnswer\(answer\)\) \{[\s\S]*return "";' -or
        $examService -notmatch 'submittedAttemptResult\(jt, attempt, true, safeSubmitToken, requestPayloadHash\)' -or
        $examService -notmatch 'private Map<String, Object> submittedAttemptResult\(JdbcTemplate jt, Map<String, Object> attempt,[\s\S]*String requestSubmitToken,[\s\S]*String requestPayloadHash\)' -or
        $examService -notmatch 'private void appendSubmitPayloadMismatch\(Map<String, Object> result, String requestPayloadHash\)' -or
        $examService -notmatch 'result\.put\("submitPayloadMismatch", true\)' -or
        $examService -notmatch 'storeSubmitResponse\(jt, attemptId, safeSubmitToken,' -or
        $examService -notmatch 'private String normalizeSubmitToken\(String submitToken\)' -or
        $examService -notmatch 'submitToken must be at most 80 characters' -or
        $examService -match 'trimToLength\(submitToken, 80\)' -or
        $examService -match 'trimToLength\(requestSubmitToken, 80\)' -or
        $examService -notmatch 'public Map<String, Object> startExam\(Long attemptId, StartExamRequest request, AuthUser user\)[\s\S]*attemptId = requirePositiveAttemptId\(attemptId\);' -or
        $examService -notmatch 'public Map<String, Object> saveDraft\(Long attemptId, String answersJson, String clientDraftId,[\s\S]*attemptId = requirePositiveAttemptId\(attemptId\);' -or
        $examService -notmatch 'public Map<String, Object> submitExam\(Long attemptId, Map<Long, String> answers, String submitToken, AuthUser user\)[\s\S]*attemptId = requirePositiveAttemptId\(attemptId\);' -or
        $examService -notmatch 'public Map<String, Object> attemptHeartbeat\(Long attemptId, AuthUser user\)[\s\S]*attemptId = requirePositiveAttemptId\(attemptId\);' -or
        $examService -notmatch 'public Map<String, Object> forceSubmitAttempt\(Long attemptId, AuthUser user\)[\s\S]*attemptId = requirePositiveAttemptId\(attemptId\);' -or
        $examService -notmatch 'private Map<String, Object> loadAttemptForSubmit\(JdbcTemplate jt, Long attemptId, Long userId\)[\s\S]*attemptId = requirePositiveAttemptId\(attemptId\);' -or
        $examService -notmatch 'private Long requirePositiveAttemptId\(Long attemptId\)' -or
        $examService -notmatch 'attemptId must be positive' -or
        $examService -notmatch 'requirePositiveAnswerQuestionId\(questionId, "Submitted answers contain a non-positive question id"\);' -or
        $examService -notmatch 'requirePositiveAnswerQuestionId\(questionId, "Draft answers contain a non-positive question id"\);' -or
        $examService -notmatch 'private Long requirePositiveAnswerQuestionId\(Long questionId, String message\)' -or
        $examService -notmatch 'questionId == null \|\| questionId <= 0') {
        Fail "Exam answer and draft requests must reject non-positive question ids before question-scope checks"
    }
    if ($draftRequest -notmatch 'import jakarta\.validation\.constraints\.PositiveOrZero;' -or
        $draftRequest -notmatch 'import jakarta\.validation\.constraints\.Size;' -or
        $draftRequest -notmatch '@Size\(max = 200000, message = "answers must be at most 200000 characters"\)' -or
        $draftRequest -notmatch '@Size\(max = 80, message = "clientDraftId must be at most 80 characters"\)' -or
        $draftRequest -notmatch '@PositiveOrZero\(message = "revision must be greater than or equal to 0"\)' -or
        $examController -notmatch '@Valid @RequestBody DraftRequest request' -or
        $examService -notmatch 'long safeRevision = normalizeDraftRevision\(revision\);' -or
        $examService -notmatch 'String safeClientDraftId = normalizeClientDraftId\(clientDraftId\);' -or
        $examService -notmatch 'private long normalizeDraftRevision\(Long revision\)' -or
        $examService -notmatch 'revision must be greater than or equal to 0' -or
        $examService -notmatch 'private String normalizeClientDraftId\(String clientDraftId\)' -or
        $examService -notmatch 'clientDraftId must be at most 80 characters') {
        Fail "Exam draft save requests must validate revision and client draft metadata before cache or database writes"
    }
    if ($examService -notmatch 'String safeAnswersJson = validateDraftAnswersJson\(jt, attemptId, answersJson\)' -or
        $examService -notmatch 'examDraftCacheService\.put\(attemptId, safeAnswersJson' -or
        $examService -notmatch 'private String validateDraftAnswersJson' -or
        $examService -notmatch 'Draft answers must be a valid JSON object' -or
        $examService -notmatch 'validateDraftAnswerValue\(entry\.getValue\(\)\);' -or
        $examService -notmatch 'private void validateDraftAnswerValue\(Object value\)' -or
        $examService -notmatch 'private boolean isDraftAnswerScalar\(Object value\)' -or
        $examService -notmatch 'private void requireBoundedDraftAnswerContent\(String answer\)' -or
        $examService -notmatch 'Draft answer content must be at most 20000 characters' -or
        $examService -notmatch 'OBJECT_MAPPER\.writeValueAsString\(sanitized\)' -or
        $examService -notmatch 'loadQuestionsForSubmit\(jt, attemptId\)' -or
        $examService -notmatch 'Draft answers contain a question that does not belong to this attempt') {
        Fail "ExamService.saveDraft must validate draft answer question ids against the attempt question set"
    }
    if ($examService -notmatch '@Transactional\s+public Map<String, Object> saveDraft' -or
        $examService -notmatch 'Map<String, Object> attempt = loadAttemptForSubmit\(jt, attemptId, user\.getId\(\)\)' -or
        $examService -notmatch 'if \(!attemptDraftSaveOpen\(attempt\)\) \{[\s\S]*return Map\.of\("saved", false, "reason", "Attempt is not active"\)' -or
        $examService -notmatch 'private boolean attemptDraftSaveOpen' -or
        $examService -notmatch 'attempt\.get\("status"\)\)\.intValue\(\) != 1 \|\| !attemptExamOpen\(attempt\)' -or
        $examService -notmatch 'remainingValue == null \|\| \(\(Number\) remainingValue\)\.longValue\(\) > 0' -or
        $examService -notmatch 'CASE WHEN e\.start_time IS NULL OR e\.start_time <= NOW\(\) THEN 1 ELSE 0 END AS examStarted' -or
        $examService -notmatch 'FOR UPDATE') {
        Fail "ExamService.saveDraft must serialize with submit/force-submit by locking the attempt row before writing drafts"
    }
    if ($examService -notmatch 'exam\.put\("draftAnswers", sanitizeRecoveredDraftAnswersJson\(jt, attemptId, stringValue\(bestDraft\.get\("answers"\)\)\)\)' -or
        $examService -notmatch 'private String sanitizeRecoveredDraftAnswersJson' -or
        $examService -notmatch 'expectedQuestionIdsForAttempt\(jt, attemptId\)' -or
        $examService -notmatch 'private boolean isRecoverableDraftAnswerValue\(Object value\)' -or
        $examService -notmatch 'expectedQuestionIds\.contains\(questionId\) && isRecoverableDraftAnswerValue\(entry\.getValue\(\)\)' -or
        $examService -match 'exam\.put\("draftAnswers", bestDraft\.get\("answers"\)\)') {
        Fail "ExamService.startExam must sanitize recovered draft answers before returning them to students"
    }
    if ($examService -notmatch 'private Map<Long, String> loadDraftAnswerMap' -or
        $examService -notmatch 'Set<Long> expectedQuestionIds = expectedQuestionIdsForAttempt\(jt, attemptId\)' -or
        $examService -notmatch 'catch \(NumberFormatException ex\) \{[\s\S]*continue;' -or
        $examService -notmatch 'if \(expectedQuestionIds\.contains\(questionId\) && isRecoverableDraftAnswerValue\(entry\.getValue\(\)\)\) \{[\s\S]*result\.put\(questionId, normalizeDraftAnswer\(entry\.getValue\(\)\)\)') {
        Fail "ExamService draft-based auto/force submit must filter recovered draft answers to the attempt question set"
    }
    if ([regex]::Matches($examService, 'safeAnswers = validateDraftAnswersJson\(jt, attemptId, answers\)').Count -lt 2 -or
        [regex]::Matches($examService, 'clientDraftId = normalizeClientDraftId\(stringValue\((draft|cacheDraft)\.get\("clientDraftId"\)\)\)').Count -lt 2 -or
        [regex]::Matches($examService, 'catch \(IllegalArgumentException ex\) \{[\s\S]*?examDraftCacheService\.delete\(attemptId\);[\s\S]*?examDraftCacheService\.markFlushSkipped\(\);').Count -lt 2 -or
        [regex]::Matches($examService, 'attemptId, safeAnswers, clientDraftId, revision').Count -lt 2 -or
        [regex]::Matches($examService, 'markClean\(attemptId, safeAnswers, clientDraftId, revision').Count -lt 2 -or
        $examService -match 'trimToLength\(stringValue\((draft|cacheDraft)\.get\("clientDraftId"\)\), 80\)' -or
        $examService -notmatch 'if \(attemptId == null \|\| attemptId <= 0\)') {
        Fail "ExamService Redis draft flush paths must validate cached answers and client draft metadata before database write-back"
    }
    if ($examService -notmatch 'private void touchMonitorSession' -or
        $examService -notmatch "status = CASE WHEN status = 'SUBMITTED' THEN status ELSE VALUES\(status\) END" -or
        $examService -notmatch 'private void markMonitorSessionSubmitted' -or
        $examService -notmatch "status = 'SUBMITTED'" -or
        $examService -notmatch 'exam_id = VALUES\(exam_id\)' -or
        $examService -notmatch 'user_id = VALUES\(user_id\)') {
        Fail "ExamService monitor session writes must preserve SUBMITTED state and refresh ownership snapshots"
    }
    if ($examService -notmatch 'normalizeObjectiveAnswer\(answer\)\.equals\(normalizeObjectiveAnswer\(correctAnswer\)\)' -or
        $examService -notmatch 'private String normalizeObjectiveAnswer' -or
        $examService -match 'private String normalizeObjective\(' -or
        $examService -notmatch 'Character\.isLetterOrDigit' -or
        $examService -notmatch 'Character\.toUpperCase' -or
        $examService -notmatch 'Arrays\.sort\(chars\)') {
        Fail "ExamService objective auto scoring must normalize option separators consistently"
    }
    $exportService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\ExportService.java" -Raw
    if ($exportService -notmatch 'requireScoresReleased' -or $exportService -notmatch 'FROM score_release' -or $exportService -notmatch 'status = 1') {
        Fail "ExportService.examScoreSheet must reject score exports before scores are published"
    }
    if ($exportService -notmatch 'public ExportFile examScoreSheet\(Long examId, AuthUser user\)[\s\S]*examId = requirePositiveExamId\(examId\);' -or
        $exportService -notmatch 'private void requireScoresReleased\(JdbcTemplate jt, Long examId\)[\s\S]*examId = requirePositiveExamId\(examId\);' -or
        $exportService -notmatch 'private Long requirePositiveExamId\(Long examId\)' -or
        $exportService -notmatch 'examId must be positive') {
        Fail "ExportService exam score export must reject non-positive exam ids before score release queries"
    }
    if ($exportService -notmatch 'JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1') {
        Fail "ExportService roster and student score exports must only include released scores"
    }
    if ($exportService -notmatch 'SELECT u\.real_name AS realName[\s\S]*JOIN exam e ON e\.id = ea\.exam_id[\s\S]*JOIN score_release sr ON sr\.exam_id = e\.id AND sr\.status = 1[\s\S]*WHERE ea\.exam_id = \? AND ea\.status = 5') {
        Fail "ExportService.examScoreSheet record query must re-check score release status while exporting"
    }
    if ($exportService -notmatch 'NOT EXISTS \(' -or
        $exportService -notmatch 'FROM score_appeal sa' -or
        $exportService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'" -or
        [regex]::Matches($exportService, 'ea\.status = 5 AND ea\.score IS NOT NULL').Count -lt 4) {
        Fail "ExportService score exports must exclude open recheck and unscored attempts"
    }
    if ($exportService -notmatch 'COALESCE\(\(SELECT SUM\(eqs\.score\)[\s\S]*FROM exam_question_snapshot eqs[\s\S]*WHERE eqs\.exam_id = e\.id\), p\.total_score\) AS totalScore') {
        Fail "ExportService score exports must use published question snapshot totals before falling back to current paper totals"
    }
    if ($exportService -notmatch 'AS questionCount' -or
        $exportService -notmatch 'AS answeredCount' -or
        $exportService -notmatch 'AS unansweredCount' -or
        $exportService -notmatch '"Question Count"' -or
        $exportService -notmatch '"Answered Count"' -or
        $exportService -notmatch '"Unanswered Count"' -or
        $exportService -notmatch 'FROM answer_record ar_answered' -or
        $exportService -notmatch 'TRIM\(ar_answered\.answer_content\)' -or
        $exportService -notmatch 'FROM exam_question_snapshot eqs_count' -or
        $exportService -notmatch 'FROM paper_question pq_count' -or
        $exportService -notmatch 'FROM exam_question_snapshot eqs_exists' -or
        $exportService -notmatch 'JOIN exam_question_snapshot eqs_answered' -or
        $exportService -notmatch 'JOIN paper_question pq_answered' -or
        $exportService -notmatch 'COUNT\(DISTINCT ar_answered\.question_id\)' -or
        $exportService -notmatch 'COUNT\(DISTINCT ar_missing\.question_id\)') {
        Fail "ExportService score CSVs must include submitted answer completeness statistics"
    }
    $studentInsightService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\StudentInsightService.java" -Raw
    if ($studentInsightService -notmatch 'JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1' -or
        $studentInsightService -notmatch 'NOT EXISTS \(' -or
        $studentInsightService -notmatch 'FROM score_appeal sa' -or
        $studentInsightService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'" -or
        [regex]::Matches($studentInsightService, 'ea\.status = 5 AND ea\.score IS NOT NULL').Count -lt 4) {
        Fail "StudentInsightService must only compute insight scores from released, finalized, scored, non-rechecking exams"
    }
    if ($studentInsightService -notmatch 'COALESCE\(\(SELECT SUM\(eqs\.score\)[\s\S]*FROM exam_question_snapshot eqs[\s\S]*WHERE eqs\.exam_id = e\.id\), p\.total_score\) AS totalScore') {
        Fail "StudentInsightService exam history must use published question snapshot totals before falling back to current paper totals"
    }
    if ($studentInsightService -notmatch 'AS questionCount' -or
        $studentInsightService -notmatch 'AS answeredCount' -or
        $studentInsightService -notmatch 'AS unansweredCount' -or
        $studentInsightService -notmatch 'FROM answer_record ar_answered' -or
        $studentInsightService -notmatch 'FROM exam_question_snapshot eqs_count' -or
        $studentInsightService -notmatch 'FROM paper_question pq_count' -or
        $studentInsightService -notmatch 'FROM exam_question_snapshot eqs_exists' -or
        $studentInsightService -notmatch 'JOIN exam_question_snapshot eqs_answered' -or
        $studentInsightService -notmatch 'JOIN paper_question pq_answered' -or
        $studentInsightService -notmatch 'COUNT\(DISTINCT ar_answered\.question_id\)' -or
        $studentInsightService -notmatch 'COUNT\(DISTINCT ar_missing\.question_id\)') {
        Fail "StudentInsightService exam history must expose submitted answer completeness statistics"
    }
    $studentService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\StudentService.java" -Raw
    $gradeInfo = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\student\GradeInfo.java" -Raw
    if ($studentService -notmatch 'visible_score' -or
        $studentService -notmatch 'scoreVisibility' -or
        $studentService -notmatch 'PENDING_REVIEW' -or
        $studentService -notmatch 'PENDING_RECHECK' -or
        $studentService -notmatch 'PENDING_SCORE' -or
        $studentService -notmatch 'NOT EXISTS \(' -or
        $studentService -notmatch 'FROM score_appeal sa' -or
        $studentService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'" -or
        $studentService -notmatch 'REVOKED' -or
        $studentService -notmatch 'appealOpen' -or
        $studentService -notmatch 'appealWindowDays' -or
        $studentService -notmatch 'score.appealEnabled' -or
        $studentService -notmatch 'score.appealWindowDays' -or
        $studentService -notmatch 'AS questionCount' -or
        $studentService -notmatch 'AS answeredCount' -or
        $studentService -notmatch 'AS unansweredCount' -or
        $studentService -notmatch 'FROM answer_record ar_answered' -or
        $studentService -notmatch 'FROM exam_question_snapshot eqs_count' -or
        $studentService -notmatch 'FROM paper_question pq_count' -or
        $studentService -notmatch 'FROM exam_question_snapshot eqs_exists' -or
        $studentService -notmatch 'JOIN exam_question_snapshot eqs_answered' -or
        $studentService -notmatch 'JOIN paper_question pq_answered' -or
        $studentService -notmatch 'COUNT\(DISTINCT ar_answered\.question_id\)' -or
        $studentService -notmatch 'COUNT\(DISTINCT ar_missing\.question_id\)' -or
        $studentService -notmatch 'CASE WHEN COALESCE\(sr\.status, 0\) = 1 AND ea\.status = 5 AND ea\.score IS NOT NULL' -or
        $studentService -notmatch 'LEFT JOIN score_release sr ON sr.exam_id = e.id' -or
        $studentService -notmatch 'JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1') {
        Fail "StudentService must expose student score visibility without leaking unreleased or unscored attempts"
    }
    if ($studentService -notmatch 'getWrongQuestions' -or
        $studentService -notmatch 'getKnowledgePointMastery' -or
        $studentService -notmatch 'ar\.is_correct = 0' -or
        $studentService -notmatch 'getExamResult' -or
        $studentService -notmatch 'answer\.put\("options", snapshotQuestionOptions' -or
        $studentService -notmatch 'SELECT expected\.questionId AS questionId' -or
        $studentService -notmatch 'FROM exam_question_snapshot eqs' -or
        $studentService -notmatch 'UNION ALL' -or
        $studentService -notmatch 'snapshot_check' -or
        $studentService -notmatch 'LEFT JOIN answer_record ar ON ar\.attempt_id = ea\.id AND ar\.question_id = expected\.questionId' -or
        $studentService -notmatch 'COALESCE\(ar\.answer_content, ''''\) AS studentAnswer' -or
        $studentService -notmatch 'ORDER BY expected\.sortOrder, expected\.questionId' -or
        $studentService -notmatch 'answer\.remove\("examId"\)' -or
        $studentService -notmatch 'WITH wrong_answers AS' -or
        $studentService -notmatch 'ranked_wrong AS' -or
        $studentService -notmatch 'ROW_NUMBER\(\) OVER' -or
        $studentService -notmatch 'PARTITION BY question_id, exam_id' -or
        $studentService -match 'COALESCE\(MAX\(eqs\.' -or
        $studentService -match 'wrongQuestionOptions' -or
        $studentService -match 'FIND_IN_SET\(eqos\.option_label' -or
        $studentService -notmatch 'latest_exam_id' -or
        $studentService -notmatch 'snapshotQuestionOptions' -or
        $studentService -notmatch 'answerContainsOption' -or
        $studentService -notmatch 'normalizeAnswerToken' -or
        $studentService -notmatch 'Character\.isLetterOrDigit' -or
        $studentService -notmatch 'Character\.toUpperCase' -or
        $studentService -notmatch 'FROM exam_question_option_snapshot eqos' -or
        $studentService -notmatch 'SELECT kp\.point_name, AVG\(ar\.score / expected\.score\) as mastery' -or
        $studentService -notmatch 'eqs\.knowledge_point_id AS knowledgePointId' -or
        $studentService -notmatch 'q\.knowledge_point_id AS knowledgePointId' -or
        $studentService -notmatch 'expected ON expected\.examId = e\.id AND expected\.questionId = ar\.question_id' -or
        $studentService -notmatch 'JOIN edu_knowledge_point kp ON kp\.id = expected\.knowledgePointId' -or
        $studentService -notmatch 'GROUP BY kp\.id, kp\.point_name' -or
        [regex]::Matches($studentService, 'ea\.status = 5 AND ea\.score IS NOT NULL|AND ea\.score IS NOT NULL').Count -lt 4 -or
        $studentService -notmatch 'AND expected\.score > 0' -or
        $studentService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'") {
        Fail "StudentService result details, wrong book, and mastery must use exam snapshots and exclude open recheck or unscored attempts"
    }
    $aiService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\AiService.java" -Raw
    $aiController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\AiController.java" -Raw
    $wrongExplainRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\ai\WrongQuestionExplainRequest.java" -Raw
    if ($aiController -notmatch '@RequireRoles\(\{"STUDENT"\}\)' -or
        $aiController -notmatch 'aiService\.explainWrongQuestion\(request, currentUser\(\)\)' -or
        $wrongExplainRequest -notmatch 'import jakarta\.validation\.constraints\.Positive;' -or
        $wrongExplainRequest -notmatch '@NotNull\(message = "questionId is required"\)' -or
        $wrongExplainRequest -notmatch '@NotNull\(message = "examId is required"\)' -or
        $wrongExplainRequest -notmatch '@Positive\(message = "questionId must be positive"\)' -or
        $wrongExplainRequest -notmatch '@Positive\(message = "examId must be positive"\)' -or
        $wrongExplainRequest -match '@NotBlank\(message = "题干不能为空"\)' -or
        $aiService -notmatch 'loadReleasedWrongQuestionForExplain' -or
        $aiService -notmatch 'loadReleasedWrongQuestionForExplain\(Long questionId, Long examId, AuthUser user\)' -or
        $aiService -notmatch 'questionId must be positive' -or
        $aiService -notmatch 'examId must be positive' -or
        $aiService -notmatch 'AND e\.id = \?' -or
        $aiService -notmatch 'PARTITION BY question_id, exam_id' -or
        $aiService -notmatch 'JOIN score_release sr ON sr\.exam_id = e\.id AND sr\.status = 1' -or
        $aiService -notmatch 'AND ar\.is_correct = 0' -or
        $aiService -notmatch 'AND ea\.score IS NOT NULL' -or
        $aiService -notmatch 'sa\.handling_result = ''RECHECK_REQUIRED''' -or
        $aiService -notmatch 'loadWrongQuestionOptionsForExplain' -or
        $aiService -notmatch 'FROM exam_question_option_snapshot' -or
        $aiService -notmatch 'answerContainsOption' -or
        $aiService -notmatch 'normalizeAnswerToken') {
        Fail "AI wrong-question explanation must be grounded in the student's released wrong-question snapshot"
    }
    if ($gradeInfo -notmatch 'scoreVisible' -or
        $gradeInfo -notmatch 'scoreVisibility' -or
        $gradeInfo -notmatch 'scoreRevokeReason' -or
        $gradeInfo -notmatch 'scorePublishedAt' -or
        $gradeInfo -notmatch 'appealOpen' -or
        $gradeInfo -notmatch 'appealDeadlineAt' -or
        $gradeInfo -notmatch 'questionCount' -or
        $gradeInfo -notmatch 'answeredCount' -or
        $gradeInfo -notmatch 'unansweredCount') {
        Fail "GradeInfo must expose score visibility fields to the student frontend"
    }
    $analysisService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\AnalysisService.java" -Raw
    if ($analysisService -notmatch 'JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1' -or
        $analysisService -notmatch 'NOT EXISTS \(' -or
        $analysisService -notmatch 'FROM score_appeal sa' -or
        $analysisService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'" -or
        $analysisService -match 'FROM exam_attempt WHERE status = 5 AND score IS NOT NULL') {
        Fail "AnalysisService score metrics must only include released, finalized, non-rechecking scores"
    }
    if ($analysisService -match 'SELECT COUNT\(\*\) FROM exam_attempt WHERE status = 5' -or
        $analysisService -match 'SELECT COUNT\(\*\) FROM exam_attempt ea JOIN exam e ON e\.id = ea\.exam_id WHERE e\.created_by = \? AND e\.deleted = 0 AND ea\.status = 5' -or
        $analysisService -notmatch 'data\.put\("completedCount",\s*count\(jt,\s*"""[\s\S]*?JOIN score_release sr ON sr\.exam_id = e\.id AND sr\.status = 1[\s\S]*?ea\.score IS NOT NULL[\s\S]*?sa\.handling_result = ''RECHECK_REQUIRED''' -or
        $analysisService -notmatch 'data\.put\("completedCount",\s*count\(jt,\s*"""[\s\S]*?WHERE e\.created_by = \? AND e\.deleted = 0 AND ea\.status = 5 AND ea\.score IS NOT NULL[\s\S]*?sa\.handling_result = ''RECHECK_REQUIRED''' -or
        [regex]::Matches($analysisService, 'LEFT JOIN exam_attempt ea ON ea\.exam_id = e\.id AND ea\.status = 5 AND ea\.score IS NOT NULL AND sr\.exam_id IS NOT NULL').Count -lt 2) {
        Fail "AnalysisService completed counts and subject attempt counts must use the released score-analysis sample"
    }
    $overviewService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\OverviewService.java" -Raw
    if ($overviewService -notmatch 'JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1' -or
        $overviewService -notmatch 'NOT EXISTS \(' -or
        $overviewService -notmatch 'FROM score_appeal sa' -or
        $overviewService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'" -or
        $overviewService -notmatch "COUNT\(DISTINCT CONCAT\(e\.id, ':', ar\.question_id\)\)" -or
        $overviewService -match 'COUNT\(DISTINCT ar\.question_id\)' -or
        $overviewService -notmatch 'LEFT JOIN exam_question_snapshot eqs ON eqs\.exam_id = e\.id AND eqs\.question_id = ar\.question_id' -or
        $overviewService -notmatch 'CASE WHEN eqs\.id IS NOT NULL THEN eqs\.knowledge_point_id ELSE q\.knowledge_point_id END' -or
        [regex]::Matches($overviewService, 'ea\.status = 5 AND ea\.score IS NOT NULL|ea\.score IS NOT NULL AND ea\.status = 5').Count -lt 6 -or
        $overviewService -match 'ea.score IS NOT NULL AND ea.status IN \(2,4,5\)' -or
        $overviewService -match 'e.created_by = \? AND e.deleted = 0 AND ea.submit_time IS NOT NULL') {
        Fail "OverviewService score metrics and knowledge mastery must use released, finalized, scored, non-rechecking snapshot data"
    }
    if ($overviewService -notmatch 'FROM exam_question_snapshot eqs_count' -or
        $overviewService -notmatch 'FROM exam_question_snapshot eqs_score' -or
        $overviewService -notmatch 'FROM paper_question pq_count' -or
        $overviewService -notmatch 'FROM paper_question pq_score') {
        Fail "OverviewService pending approval risks must use snapshot-first question counts and scores"
    }
    if ($examService -notmatch 'AS questionCount' -or
        $examService -notmatch 'AS totalScore' -or
        [regex]::Matches($examService, 'FROM exam_question_snapshot eqs_count').Count -lt 3 -or
        [regex]::Matches($examService, 'FROM exam_question_snapshot eqs_score').Count -lt 3 -or
        [regex]::Matches($examService, 'FROM paper_question pq_count').Count -lt 3 -or
        [regex]::Matches($examService, 'FROM paper_question pq_score').Count -lt 3) {
        Fail "ExamService approval queue risks and filters must use snapshot-first question counts and scores"
    }
    if ([regex]::Matches($examService, 'currentExamStatusForUpdate\(jt, id\)').Count -lt 5 -or
        $examService -notmatch 'SELECT status[\s\S]*FROM exam[\s\S]*WHERE id = \? AND deleted = 0[\s\S]*FOR UPDATE' -or
        $examService -notmatch 'Only pending exams can be approved' -or
        $examService -notmatch 'Only pending exams can be rejected' -or
        $examService -notmatch 'Only published exams can be closed' -or
        $examService -notmatch 'Exam cannot be deleted after a student has entered' -or
        $examService -notmatch 'APPROVAL_ACTION_RESUBMIT' -or
        $examService -notmatch 'validateExamUpdateScore\(jt, id, request\.getPassScore\(\)\)' -or
        $examService -notmatch 'private void validateExamUpdateScore\(JdbcTemplate jt, Long id, BigDecimal passScore\)' -or
        $examService -notmatch 'FROM exam_question_snapshot eqs[\s\S]*WHERE eqs\.exam_id = e\.id[\s\S]*p\.total_score, 0' -or
        $examService -notmatch 'Pass score cannot be greater than exam total score') {
        Fail "ExamService approval, resubmit, update, delete, and close transitions must lock rows and keep update score constraints aligned with exam totals"
    }
    if ($examService -notmatch 'public Map<String, Object> approveExam\(Long id, ExamApprovalDecisionRequest decision, AuthUser approver\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'public Map<String, Object> rejectExam\(Long id, ExamApprovalDecisionRequest decision, AuthUser approver\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'public Map<String, Object> updateExam\(Long id, ExamUpdateRequest request, AuthUser user\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'public void deleteExam\(Long id, AuthUser user\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'public void closeExam\(Long id, AuthUser user\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'public Map<String, Object> publishScores\(Long id, AuthUser user\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'public Map<String, Object> revokeScores\(Long id, ScoreRevokeRequest request, AuthUser user\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'public Map<String, Object> getExamById\(Long examId\)[\s\S]*examId = requirePositiveExamId\(examId\);' -or
        $examService -notmatch 'private int currentExamStatusForUpdate\(JdbcTemplate jt, Long id\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'private void requireOwnedExam\(Long id, AuthUser user\)[\s\S]*id = requirePositiveExamId\(id\);' -or
        $examService -notmatch 'private Long requirePositiveExamId\(Long examId\)' -or
        $examService -notmatch 'examId must be positive') {
        Fail "ExamService exam lifecycle and score release entries must reject non-positive exam ids before database access"
    }
    $reviewService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\ReviewService.java" -Raw
    $reviewRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\review\ReviewRequest.java" -Raw
    if ($reviewRequest -notmatch 'import jakarta\.validation\.constraints\.Positive;' -or
        $reviewRequest -notmatch '@Positive\(message = "answerRecordId must be positive"\)' -or
        $reviewService -notmatch 'answerRecordId must be positive') {
        Fail "ReviewRequest and ReviewService must reject non-positive answer record ids"
    }
    if ($reviewService -notmatch 'attemptId = requirePositiveAttemptId\(attemptId\);' -or
        $reviewService -notmatch 'private Long requirePositiveAttemptId\(Long attemptId\)' -or
        $reviewService -notmatch 'attemptId must be positive') {
        Fail "ReviewService must reject non-positive attempt ids before review access queries"
    }
    if ($reviewService -notmatch 'public List<Map<String, Object>> getPendingReviews\(Long examId, String reviewType, AuthUser user\)' -or
        $reviewService -notmatch 'Long safeExamId = examId == null \? null : requirePositiveExamId\(examId\)' -or
        $reviewService -notmatch 'String normalizedReviewType = normalizeReviewType\(reviewType\)' -or
        $reviewService -notmatch 'SELECT a\.id AS attemptId, e\.id AS examId' -or
        $reviewService -notmatch 'AND e\.id = \?' -or
        $reviewService -notmatch 'GROUP BY a\.id, e\.id, e\.exam_name') {
        Fail "ReviewService pending review queue must support scoped exam filtering"
    }
    if ($reviewService -notmatch 'openRecheckAnswerCondition' -or
        $reviewService -notmatch 'normalizeReviewType' -or
        $reviewService -notmatch 'reviewType must be RECHECK or STANDARD' -or
        $reviewService -notmatch 'recheckTaskCount' -or
        $reviewService -notmatch 'recheckRequired' -or
        $reviewService -notmatch 'recheckAppealCount' -or
        $reviewService -notmatch 'sa_recheck\.handling_result = ''RECHECK_REQUIRED''' -or
        $reviewService -notmatch '"RECHECK"\.equals\(normalizedReviewType\)' -or
        $reviewService -notmatch '"STANDARD"\.equals\(normalizedReviewType\)') {
        Fail "ReviewService pending review queue must identify and filter recheck tasks"
    }
    if ($reviewService -notmatch 'public List<Map<String, Object>> listReviewProgress' -or
        $reviewService -notmatch 'examId == null \? null : requirePositiveExamId\(examId\)' -or
        $reviewService -notmatch 'private Long requirePositiveExamId\(Long examId\)' -or
        $reviewService -notmatch 'pendingAttemptCount' -or
        $reviewService -notmatch 'pendingAnswerCount' -or
        $reviewService -notmatch 'reviewedAnswerCount' -or
        $reviewService -notmatch 'reviewableAnswerCount' -or
        $reviewService -notmatch 'progressPercent' -or
        $reviewService -notmatch 'firstPendingAttemptId' -or
        $reviewService -notmatch 'pendingRecheckAnswerCount' -or
        $reviewService -notmatch 'firstRecheckAttemptId' -or
        $reviewService -notmatch 'recheckAppealCount' -or
        $reviewService -notmatch 'blocksScoreRelease' -or
        $reviewService -notmatch 'EXPECTED_ANSWER_SCOPE_CONDITION') {
        Fail "ReviewService must expose scoped exam-level review progress using the expected question set"
    }
    if ($reviewService -notmatch 'Review score cannot be negative' -or
        $reviewService -notmatch 'BigDecimal\.ZERO' -or
        $reviewService -notmatch 'Review submission must cover all pending answers' -or
        $reviewService -notmatch 'Review score cannot exceed question score' -or
        $reviewService -notmatch 'requireAttemptPendingReview' -or
        $reviewService -notmatch 'requireAttemptPendingReviewForUpdate' -or
        $reviewService -notmatch 'requireAttemptPendingReviewForUpdate\(jt, attemptId\);' -or
        $reviewService -notmatch 'FOR UPDATE' -or
        $reviewService -notmatch 'SELECT CASE WHEN status = 4 THEN 1 ELSE 0 END' -or
        $reviewService -notmatch 'Attempt is not pending review') {
        Fail "ReviewService must enforce pending-review state, complete review coverage, and score bounds"
    }
    if ($reviewService -notmatch 'reviewedCorrect = review\.getScore\(\)\.compareTo\(answer\.maxScore\(\)\) >= 0' -or
        $reviewService -notmatch 'UPDATE answer_record SET score = \?, is_correct = \?, review_status = 1 WHERE id = \?') {
        Fail "ReviewService must synchronize answer correctness after manual review scoring"
    }
    if ($reviewService -notmatch 'AS questionCount' -or
        $reviewService -notmatch 'AS answeredCount' -or
        $reviewService -notmatch 'AS unansweredCount' -or
        $reviewService -notmatch 'FROM exam_question_snapshot eqs_count' -or
        $reviewService -notmatch 'FROM paper_question pq_count' -or
        $reviewService -notmatch 'FROM exam_question_snapshot eqs_total' -or
        $reviewService -notmatch 'FROM paper_question pq_total' -or
        $reviewService -notmatch 'FROM exam_question_snapshot eqs_exists' -or
        $reviewService -notmatch 'JOIN exam_question_snapshot eqs_answered' -or
        $reviewService -notmatch 'JOIN paper_question pq_answered' -or
        $reviewService -notmatch 'COUNT\(DISTINCT answered\.question_id\)' -or
        $reviewService -notmatch 'GROUP BY a\.id, e\.id, e\.exam_name, e\.paper_id, u\.real_name') {
        Fail "ReviewService pending review list and details must include submitted answer statistics"
    }
    if ($reviewService -notmatch 'EXPECTED_ANSWER_SCOPE_CONDITION' -or
        $reviewService -notmatch 'countPendingExpectedReviewAnswers' -or
        $reviewService -notmatch 'SELECT COALESCE\(SUM\(ar\.score\), 0\)' -or
        $reviewService -notmatch 'eqs_scope\.question_id = ar\.question_id' -or
        $reviewService -notmatch 'pq_scope\.question_id = ar\.question_id' -or
        $reviewService -match 'SELECT COUNT\(\*\) FROM answer_record WHERE attempt_id = \? AND review_status = 0') {
        Fail "ReviewService review completion and score recalculation must be scoped to the expected question set"
    }
    if ($reviewService -notmatch 'recordReviewScoreLog' -or
        $reviewService -notmatch 'resolveReviewScoreLogContext' -or
        $reviewService -notmatch 'SELECT ar\.attempt_id AS attemptId, ar\.question_id AS questionId' -or
        $reviewService -notmatch 'JOIN exam_attempt a ON a\.id = ar\.attempt_id' -or
        $reviewService -notmatch 'ReviewAnswerAuditContext resolved' -or
        $reviewService -notmatch 'listReviewScoreLogs' -or
        $reviewService -notmatch 'exportReviewScoreLogs' -or
        $reviewService -notmatch '"Log ID", "Time", "Exam", "Student", "Attempt ID"' -or
        $reviewService -notmatch 'nullable\(log\.get\("id"\)\)' -or
        $reviewService -notmatch 'FROM review_score_log' -or
        $reviewService -notmatch 'INSERT INTO review_score_log') {
        Fail "ReviewService must record, list, and export review score audit logs"
    }
    if ($reviewService -notmatch 'List<Long> reviewScoreLogIds' -or
        $reviewService -notmatch '"reviewScoreLogIds", reviewScoreLogIds' -or
        $reviewService -notmatch 'return jt\.queryForObject\("SELECT LAST_INSERT_ID\(\)", Long\.class\);') {
        Fail "ReviewService review submission must return generated review score audit log IDs"
    }
    if ($reviewRequest -notmatch 'Size\(max = 1000' -or
        $reviewService -notmatch 'MAX_REVIEW_COMMENT_LENGTH = 1000' -or
        $reviewService -notmatch 'private String normalizeReviewComment\(String comment\)' -or
        $reviewService -notmatch 'normalized\.length\(\) > MAX_REVIEW_COMMENT_LENGTH' -or
        $reviewService -notmatch 'Review comment must be 1000 characters or less' -or
        $reviewService -notmatch 'Map<Long, String> normalizedComments' -or
        $reviewService -notmatch 'normalizedComments\.put\(answerRecordId, normalizeReviewComment\(review\.getComment\(\)\)\);' -or
        $reviewService -match 'review\.getScore\(\), review\.getComment\(\)' -or
        $reviewService -match 'review\.getComment\(\), reviewer\.getId\(\)') {
        Fail "ReviewService must normalize and reject overlong review comments before writing review records or score audit logs"
    }
    if ($examService -notmatch 'affected\.put\("reviewScoreLogs"[\s\S]*DELETE FROM review_score_log WHERE attempt_id IN \(%s\)[\s\S]*affected\.put\("reviewRecords"[\s\S]*affected\.put\("answerRecords"') {
        Fail "Attempt resilience fixture cleanup must delete review score logs before review records and answer records"
    }
    $scoreRevokeRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\exam\ScoreRevokeRequest.java" -Raw
    $examApprovalDecisionRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\exam\ExamApprovalDecisionRequest.java" -Raw
    if ($scoreRevokeRequest -notmatch 'getReason' -or $scoreRevokeRequest -notmatch 'Size\(max = 500') {
        Fail "ScoreRevokeRequest must expose a bounded reason field"
    }
    if ($examApprovalDecisionRequest -notmatch 'getNote' -or
        $examApprovalDecisionRequest -notmatch 'Size\(max = 1000') {
        Fail "ExamApprovalDecisionRequest must expose a bounded note field"
    }
    if ($examService -notmatch 'MAX_SCORE_REVOKE_REASON_LENGTH = 500' -or
        $examService -notmatch 'MAX_APPROVAL_NOTE_LENGTH = 1000' -or
        $examService -notmatch 'private String scoreRevokeReason\(ScoreRevokeRequest request\)[\s\S]*reason\.length\(\) > MAX_SCORE_REVOKE_REASON_LENGTH[\s\S]*Score revoke reason must be 500 characters or less' -or
        $examService -match 'return trimToLength\(reason, 500\)' -or
        $examService -notmatch 'private String normalizeApprovalNote\(String note\)' -or
        $examService -notmatch 'normalized\.length\(\) > MAX_APPROVAL_NOTE_LENGTH' -or
        $examService -notmatch 'Approval note must be 1000 characters or less' -or
        $examService -notmatch 'String note = normalizeApprovalNote\(decision == null \? null : decision\.getNote\(\)\);') {
        Fail "ExamService must reject overlong score revoke reasons and approval notes instead of silently truncating audit text"
    }
    $migrationRunner = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\config\DatabaseMigrationRunner.java" -Raw
    if ($migrationRunner -notmatch 'ensureScoreReleaseNoteColumn' -or
        $migrationRunner -notmatch 'ensureScoreReleaseAuditColumns' -or
        $migrationRunner -notmatch 'ensureScoreReleaseUniqueExam' -or
        $migrationRunner -notmatch 'deduplicateScoreReleaseBeforeUniqueIndex' -or
        $migrationRunner -notmatch 'ALTER TABLE score_release ADD COLUMN note' -or
        $migrationRunner -notmatch 'ALTER TABLE score_release ADD COLUMN publish_note' -or
        $migrationRunner -notmatch 'ALTER TABLE score_release ADD COLUMN revoke_reason' -or
        $migrationRunner -notmatch 'SET revoke_reason = note' -or
        $migrationRunner -notmatch 'tmp_score_release_merge' -or
        $migrationRunner -notmatch 'tmp_score_release_dedup' -or
        $migrationRunner -notmatch 'GROUP BY sr\.exam_id\s+HAVING COUNT\(\*\) > 1' -or
        $migrationRunner -notmatch 'DELETE sr\s+FROM score_release sr\s+JOIN tmp_score_release_dedup d ON d\.duplicate_id = sr\.id' -or
        $migrationRunner -notmatch 'ALTER TABLE score_release ADD UNIQUE KEY uk_score_release_exam \(exam_id\)' -or
        $migrationRunner -notmatch 'ALTER TABLE score_release ADD INDEX idx_score_release_status \(status, published_at\)' -or
        $migrationRunner -notmatch 'ensureScoreReleaseLogTable' -or
        $migrationRunner -notmatch 'CREATE TABLE IF NOT EXISTS score_release_log' -or
        $migrationRunner -notmatch 'ensureScoreAppealHandlingResultColumn' -or
        $migrationRunner -notmatch 'ADD COLUMN handling_result' -or
        $migrationRunner -notmatch 'ensureScoreAppealRecheckColumns' -or
        $migrationRunner -notmatch 'ADD COLUMN recheck_note' -or
        $migrationRunner -notmatch 'ensureScoreAppealExamIdColumn' -or
        $migrationRunner -notmatch 'ADD COLUMN exam_id' -or
        $migrationRunner -notmatch 'backfill score appeal exam id' -or
        $migrationRunner -notmatch 'sa\.exam_id IS NULL OR sa\.exam_id <> a\.exam_id' -or
        $migrationRunner -notmatch 'idx_score_appeal_exam_status' -or
        $migrationRunner -notmatch 'ensureScoreAppealActiveTargetIndex' -or
        $migrationRunner -notmatch 'idx_score_appeal_active_target' -or
        $migrationRunner -notmatch 'ensureScoreAppealLogTable' -or
        $migrationRunner -notmatch 'CREATE TABLE IF NOT EXISTS score_appeal_log' -or
        $migrationRunner -notmatch 'ensureScoreAppealLogExamIdConsistency' -or
        $migrationRunner -notmatch 'reconcile score appeal log exam id' -or
        $migrationRunner -notmatch 'l\.exam_id IS NULL OR l\.exam_id <> a\.exam_id') {
        Fail "DatabaseMigrationRunner must backfill score release audit columns for old databases"
    }
    $schemaSql = Get-Content -LiteralPath "backend\src\main\resources\db\schema.sql" -Raw
    if ($migrationRunner -notmatch 'ADD COLUMN candidate_count INT NOT NULL DEFAULT 0' -or
        $migrationRunner -notmatch 'ADD COLUMN notified_student_count INT NOT NULL DEFAULT 0' -or
        $migrationRunner -notmatch 'ADD COLUMN notified_attempt_count INT NOT NULL DEFAULT 0' -or
        $schemaSql -notmatch 'candidate_count\s+INT NOT NULL DEFAULT 0' -or
        $schemaSql -notmatch 'notified_student_count\s+INT NOT NULL DEFAULT 0' -or
        $schemaSql -notmatch 'notified_attempt_count\s+INT NOT NULL DEFAULT 0') {
        Fail "exam approval log schema and migrations must include publish notification statistics"
    }
    if ($schemaSql -notmatch 'exam_id\s+BIGINT\s+NOT NULL' -or
        $schemaSql -notmatch 'idx_score_appeal_exam_status' -or
        $schemaSql -notmatch 'idx_score_appeal_active_target') {
        Fail "schema.sql must include score appeal exam snapshot and lookup indexes"
    }
    if ($schemaSql -notmatch 'UNIQUE KEY uk_score_release_exam \(exam_id\)' -or
        $schemaSql -notmatch 'KEY idx_score_release_status \(status, published_at\)') {
        Fail "schema.sql must keep score release one-row-per-exam indexes"
    }
    if ($schemaSql -notmatch 'rules_confirmed_at DATETIME DEFAULT NULL' -or
        $migrationRunner -notmatch 'ADD COLUMN rules_confirmed_at DATETIME DEFAULT NULL AFTER start_time') {
        Fail "exam_attempt must persist student rules confirmation time for audit"
    }
    if ($schemaSql -notmatch 'knowledge_point_id BIGINT\s+DEFAULT NULL' -or
        $schemaSql -notmatch 'idx_exam_question_snapshot_kp' -or
        $migrationRunner -notmatch 'ensureExamQuestionSnapshotKnowledgePointColumn' -or
        $migrationRunner -notmatch 'ALTER TABLE exam_question_snapshot ADD COLUMN knowledge_point_id' -or
        $migrationRunner -notmatch 'backfill exam question snapshot knowledge points' -or
        $examService -notmatch 'COALESCE\(qv\.knowledge_point_id, q\.knowledge_point_id\)') {
        Fail "Exam question snapshots must freeze knowledge point ids for student mastery analytics"
    }
    if ($schemaSql -notmatch 'exam_id\s+BIGINT\s+NOT NULL DEFAULT 0' -or
        $schemaSql -notmatch 'uk_wrong_user_exam_question \(user_id, exam_id, question_id\)' -or
        $schemaSql -notmatch 'idx_wrong_exam_question \(exam_id, question_id\)' -or
        $migrationRunner -notmatch 'ensureWrongQuestionBookSnapshotIdentity' -or
        $migrationRunner -notmatch 'ALTER TABLE wrong_question_book ADD COLUMN exam_id BIGINT NOT NULL DEFAULT 0 AFTER user_id' -or
        $migrationRunner -notmatch 'backfill wrong question book exam identity' -or
        $migrationRunner -notmatch 'DROP INDEX uk_wrong_user_question' -or
        $migrationRunner -notmatch 'deduplicateWrongQuestionBookBeforeUniqueIndex' -or
        $migrationRunner -notmatch 'tmp_wrong_question_book_merge' -or
        $migrationRunner -notmatch 'uk_wrong_user_exam_question \(user_id, exam_id, question_id\)' -or
        $examService -notmatch 'INSERT INTO wrong_question_book \(user_id, exam_id, question_id, wrong_count, last_wrong_time\)' -or
        $examService -notmatch 'DELETE FROM wrong_question_book WHERE exam_id IN' -or
        $examService -match 'DELETE FROM wrong_question_book WHERE user_id IN') {
        Fail "Wrong question book persistence must use exam snapshot identity"
    }
    if ($migrationRunner -notmatch 'ensureSystemConfigLogTable' -or
        $migrationRunner -notmatch 'CREATE TABLE IF NOT EXISTS system_config_log' -or
        $migrationRunner -notmatch 'idx_config_log_key_time') {
        Fail "DatabaseMigrationRunner must create system config audit log table for old databases"
    }
    if ($migrationRunner -notmatch 'ensureReviewScoreLogTable' -or
        $migrationRunner -notmatch 'CREATE TABLE IF NOT EXISTS review_score_log' -or
        $migrationRunner -notmatch 'idx_review_score_log_attempt' -or
        $migrationRunner -notmatch 'ensureReviewScoreLogExamIdConsistency' -or
        $migrationRunner -notmatch 'reconcile review score log exam id' -or
        $migrationRunner -notmatch 'UPDATE review_score_log l[\s\S]*SET l\.exam_id = a\.exam_id[\s\S]*l\.exam_id IS NULL OR l\.exam_id <> a\.exam_id' -or
        $migrationRunner -notmatch 'ensureReviewScoreLogAnswerConsistency' -or
        $migrationRunner -notmatch 'reconcile review score log answer ownership' -or
        $migrationRunner -notmatch 'JOIN answer_record ar ON ar\.id = l\.answer_record_id' -or
        $migrationRunner -notmatch 'SET l\.attempt_id = ar\.attempt_id,[\s\S]*l\.question_id = ar\.question_id,[\s\S]*l\.exam_id = a\.exam_id,[\s\S]*l\.user_id = a\.user_id') {
        Fail "DatabaseMigrationRunner must create review score audit log table for old databases"
    }
    if ($migrationRunner -notmatch 'ensureMonitorActionTable' -or
        $migrationRunner -notmatch 'CREATE TABLE IF NOT EXISTS exam_monitor_action' -or
        $schemaSql -notmatch 'notification_sent\s+TINYINT\s+NOT NULL DEFAULT 0' -or
        $schemaSql -notmatch 'notification_id\s+BIGINT\s+DEFAULT NULL' -or
        $schemaSql -notmatch 'idx_monitor_action_notification \(notification_id\)' -or
        $migrationRunner -notmatch 'ADD COLUMN notification_sent TINYINT NOT NULL DEFAULT 0 AFTER note' -or
        $migrationRunner -notmatch 'ADD COLUMN notification_id BIGINT DEFAULT NULL AFTER notification_sent' -or
        $migrationRunner -notmatch 'ADD INDEX idx_monitor_action_notification \(notification_id\)' -or
        $migrationRunner -notmatch 'ensureMonitorSessionOwnershipConsistency' -or
        $migrationRunner -notmatch 'reconcile monitor session ownership' -or
        $migrationRunner -notmatch 'UPDATE exam_monitor_session s[\s\S]*SET s\.exam_id = a\.exam_id,[\s\S]*s\.user_id = a\.user_id' -or
        $migrationRunner -notmatch 'ensureMonitorSessionEventAggregateConsistency' -or
        $migrationRunner -notmatch 'create monitor sessions from cheat events' -or
        $migrationRunner -notmatch 'reconcile monitor session event aggregates' -or
        $migrationRunner -notmatch 'INSERT INTO exam_monitor_session \([\s\S]*attempt_id, exam_id, user_id, status, last_heartbeat_at,[\s\S]*last_event_at, event_count, risk_score, last_event_type' -or
        $migrationRunner -notmatch 'COALESCE\(SUM\(ce\.risk_score\), 0\) AS risk_score' -or
        $migrationRunner -notmatch 'FROM cheat_event ce[\s\S]*JOIN exam_attempt a ON a\.id = ce\.attempt_id[\s\S]*GROUP BY ce\.attempt_id, a\.exam_id, a\.user_id' -or
        $migrationRunner -notmatch 'SET s\.event_count = agg\.event_count,[\s\S]*s\.risk_score = agg\.risk_score,[\s\S]*s\.last_event_at = agg\.last_event_at,[\s\S]*s\.last_event_type = latest\.event_type' -or
        $migrationRunner -notmatch 'ORDER BY ce2\.event_time DESC, ce2\.id DESC' -or
        $migrationRunner -notmatch 'ensureMonitorSessionSubmittedStateConsistency' -or
        $migrationRunner -notmatch 'reconcile monitor session submitted state' -or
        $migrationRunner -notmatch "UPDATE exam_monitor_session s[\s\S]*JOIN exam_attempt a ON a\.id = s\.attempt_id[\s\S]*SET s\.status = 'SUBMITTED'[\s\S]*WHERE a\.status >= 2[\s\S]*AND s\.status <> 'SUBMITTED'" -or
        $migrationRunner -notmatch 'ensureMonitorActionOwnershipConsistency' -or
        $migrationRunner -notmatch 'reconcile monitor action ownership' -or
        $migrationRunner -notmatch 'JOIN exam_monitor_session s ON s\.id = ma\.session_id' -or
        $migrationRunner -notmatch 'SET ma\.attempt_id = s\.attempt_id,[\s\S]*ma\.exam_id = a\.exam_id,[\s\S]*ma\.user_id = a\.user_id') {
        Fail "DatabaseMigrationRunner must reconcile monitor action ownership for old databases"
    }
    if ($schemaSql -notmatch 'UNIQUE KEY uk_monitor_attempt \(attempt_id\)' -or
        $migrationRunner -notmatch 'ensureMonitorSessionUniqueIdentity' -or
        $migrationRunner -notmatch 'ensureMonitorActionTable\(jdbc\);[\s\S]*ensureMonitorSessionUniqueIdentity\(jdbc\);[\s\S]*ensureMonitorActionOwnershipConsistency\(jdbc\);' -or
        $migrationRunner -notmatch 'ADD COLUMN status VARCHAR\(32\) NOT NULL DEFAULT ''ONLINE'' AFTER user_id' -or
        $migrationRunner -notmatch 'ADD COLUMN last_heartbeat_at DATETIME DEFAULT NULL AFTER status' -or
        $migrationRunner -notmatch 'ADD INDEX idx_monitor_exam_status \(exam_id, status, last_heartbeat_at\)' -or
        $migrationRunner -notmatch 'deduplicateMonitorSessionsBeforeUniqueIndex' -or
        $migrationRunner -notmatch 'tmp_monitor_session_merge' -or
        $migrationRunner -notmatch 'tmp_monitor_session_dedup' -or
        $migrationRunner -notmatch 'UPDATE exam_monitor_action ma[\s\S]*JOIN tmp_monitor_session_dedup d ON d\.duplicate_session_id = ma\.session_id[\s\S]*SET ma\.session_id = d\.keep_id' -or
        $migrationRunner -notmatch 'DELETE s[\s\S]*FROM exam_monitor_session s[\s\S]*JOIN tmp_monitor_session_dedup d ON d\.duplicate_session_id = s\.id' -or
        $migrationRunner -notmatch 'ALTER TABLE exam_monitor_session ADD UNIQUE KEY uk_monitor_attempt \(attempt_id\)') {
        Fail "exam_monitor_session must be unique per attempt and duplicate sessions must be merged before adding the unique key"
    }
    if ($schemaSql -notmatch 'UNIQUE KEY uk_draft_attempt \(attempt_id\)' -or
        $migrationRunner -notmatch 'create exam_answer_draft table' -or
        $migrationRunner -notmatch 'CREATE TABLE IF NOT EXISTS exam_answer_draft' -or
        $migrationRunner -notmatch 'deduplicateExamAnswerDraftsBeforeUniqueIndex' -or
        $migrationRunner -notmatch 'tmp_exam_answer_draft_merge' -or
        $migrationRunner -notmatch 'GROUP BY d\.attempt_id[\s\S]*HAVING COUNT\(\*\) > 1' -or
        $migrationRunner -notmatch 'DELETE d[\s\S]*FROM exam_answer_draft d[\s\S]*JOIN tmp_exam_answer_draft_merge m ON m\.attempt_id = d\.attempt_id' -or
        $migrationRunner -notmatch 'ALTER TABLE exam_answer_draft ADD UNIQUE KEY uk_draft_attempt \(attempt_id\)') {
        Fail "exam_answer_draft must be unique per attempt and old duplicate drafts must be merged before adding the unique key"
    }
    if ($schemaSql -notmatch 'UNIQUE KEY uk_exam_submit_response_attempt \(attempt_id\)' -or
        $migrationRunner -notmatch 'create exam_submit_response table' -or
        $migrationRunner -notmatch 'ADD COLUMN submit_token VARCHAR\(80\) DEFAULT NULL AFTER attempt_id' -or
        $migrationRunner -notmatch 'ADD COLUMN submit_payload_hash VARCHAR\(64\) DEFAULT NULL AFTER submit_token' -or
        $migrationRunner -notmatch 'ADD COLUMN response_json LONGTEXT DEFAULT NULL AFTER submit_payload_hash' -or
        $migrationRunner -notmatch 'deduplicateExamSubmitResponsesBeforeUniqueIndex' -or
        $migrationRunner -notmatch 'tmp_exam_submit_response_merge' -or
        $migrationRunner -notmatch 'FROM exam_submit_response r[\s\S]*GROUP BY r\.attempt_id[\s\S]*HAVING COUNT\(\*\) > 1' -or
        $migrationRunner -notmatch 'DELETE r[\s\S]*FROM exam_submit_response r[\s\S]*JOIN tmp_exam_submit_response_merge m ON m\.attempt_id = r\.attempt_id' -or
        $migrationRunner -notmatch 'ALTER TABLE exam_submit_response ADD UNIQUE KEY uk_exam_submit_response_attempt \(attempt_id\)' -or
        $migrationRunner -notmatch 'ALTER TABLE exam_submit_response ADD INDEX idx_exam_submit_response_token \(submit_token\)' -or
        $migrationRunner -notmatch 'ALTER TABLE exam_submit_response ADD INDEX idx_exam_submit_response_hash \(submit_payload_hash\)') {
        Fail "exam_submit_response must be unique per attempt and old duplicate response snapshots must be merged before adding the unique key"
    }
    if ($schemaSql -notmatch 'uk_answer_attempt_question' -or
        $migrationRunner -notmatch 'ensureAnswerRecordUniqueIndex' -or
        $migrationRunner -notmatch 'deduplicateAnswerRecordsBeforeUniqueIndex' -or
        $migrationRunner -notmatch 'tmp_answer_record_dedup' -or
        $migrationRunner -notmatch 'UPDATE review_record rr' -or
        $migrationRunner -notmatch 'UPDATE review_score_log' -or
        $migrationRunner -notmatch 'DELETE ar FROM answer_record' -or
        $migrationRunner -notmatch 'deduplicateAnswerRecordsBeforeUniqueIndex\(jdbc\);[\s\S]*ALTER TABLE answer_record ADD UNIQUE KEY uk_answer_attempt_question' -or
        $migrationRunner -notmatch 'ALTER TABLE answer_record ADD UNIQUE KEY uk_answer_attempt_question \(attempt_id, question_id\)' -or
        $examService -notmatch 'INSERT INTO answer_record \(attempt_id, question_id, answer_content, score, is_correct, review_status\)[\s\S]*ON DUPLICATE KEY UPDATE[\s\S]*answer_content = VALUES\(answer_content\)[\s\S]*review_status = VALUES\(review_status\)') {
        Fail "answer_record must be deduplicated, unique per attempt/question, and submit finalization must upsert answers"
    }
    if ($examService -notmatch 'boolean hasPendingManualReview = false' -or
        $examService -notmatch 'boolean answered = !isBlankAnswer\(answer\)' -or
        $examService -notmatch 'else if \(answered\) \{\s*hasPendingManualReview = true;' -or
        $examService -notmatch 'else \{\s*reviewStatus = 1;\s*\}' -or
        $examService -notmatch 'int finalStatus = hasPendingManualReview \? 4 : 5' -or
        $examService -match 'int finalStatus = hasSubjective \? 4 : 5') {
        Fail "Blank subjective answers must be auto-finalized as zero-score reviewed answers instead of creating manual review work"
    }
    if ($schemaSql -notmatch 'UNIQUE KEY uk_attempt_exam_user_no \(exam_id, user_id, attempt_no\)' -or
        $migrationRunner -notmatch 'ensureExamAttemptUniqueIdentity' -or
        $migrationRunner -notmatch 'deduplicateExamAttemptsBeforeUniqueIndex' -or
        $migrationRunner -notmatch 'tmp_exam_attempt_dedup' -or
        $migrationRunner -notmatch 'tmp_exam_attempt_answer_record_dedup' -or
        $migrationRunner -notmatch 'UPDATE review_record rr[\s\S]*SET rr\.answer_record_id = d\.keep_id' -or
        $migrationRunner -notmatch 'UPDATE review_score_log rsl[\s\S]*SET rsl\.answer_record_id = d\.keep_id' -or
        $migrationRunner -notmatch 'UPDATE exam_monitor_action ma[\s\S]*SET ma\.session_id = d\.keep_session_id' -or
        $migrationRunner -notmatch 'UPDATE cheat_event ce[\s\S]*SET ce\.attempt_id = d\.keep_id,[\s\S]*ce\.exam_id = keep_attempt\.exam_id,[\s\S]*ce\.user_id = keep_attempt\.user_id' -or
        $migrationRunner -notmatch 'ALTER TABLE exam_attempt ADD UNIQUE KEY uk_attempt_exam_user_no \(exam_id, user_id, attempt_no\)' -or
        $examService -notmatch 'INSERT INTO exam_attempt \(exam_id, user_id, attempt_no, status\)[\s\S]*VALUES \(\?, \?, 1, 0\)[\s\S]*ON DUPLICATE KEY UPDATE id = id' -or
        $examService -notmatch 'INSERT INTO exam_attempt \(exam_id, user_id, attempt_no, status\)[\s\S]*VALUES \(\?, \?, \?, 0\)[\s\S]*ON DUPLICATE KEY UPDATE id = id') {
        Fail "exam_attempt must be unique per exam/user/attempt_no, migrate duplicates, and use idempotent inserts"
    }
    if ($examService -notmatch 'appendSubmittedAnswerStats' -or
        $examService -notmatch 'FROM exam_question_snapshot eqs' -or
        $examService -notmatch 'FROM exam_question_snapshot snapshot_check' -or
        $examService -notmatch 'LEFT JOIN answer_record ar ON ar\.attempt_id = \? AND ar\.question_id = expected\.question_id' -or
        $examService -notmatch 'result\.put\("questionCount", questionCount\)' -or
        $examService -notmatch 'result\.put\("answeredCount", answeredCount\)' -or
        $examService -notmatch 'result\.put\("unansweredCount", Math\.max\(0, questionCount - answeredCount\)\)' -or
        $examService -notmatch 'appendSubmittedAnswerStats\(jt, attemptId, examId, replayed\)' -or
        $examService -notmatch 'appendSubmittedAnswerStats\(jt, attemptId, examId, result\)') {
        Fail "Submitted attempt replay and fallback responses must include answer statistics"
    }

    $appealService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\ScoreAppealService.java" -Raw
    $studentScoreAppealRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\student\ScoreAppealRequest.java" -Raw
    $appealReplyRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\review\ScoreAppealReplyRequest.java" -Raw
    $appealRecheckCloseRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\review\ScoreAppealRecheckCloseRequest.java" -Raw
    $reviewController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\ReviewController.java" -Raw
    if ($studentScoreAppealRequest -notmatch 'import jakarta\.validation\.constraints\.Positive;' -or
        $studentScoreAppealRequest -notmatch '@Positive\(message = "attemptId must be positive"\)' -or
        $studentScoreAppealRequest -notmatch '@Positive\(message = "questionId must be positive"\)' -or
        $appealService -notmatch 'validateAppealRequestIds\(request\);' -or
        $appealService -notmatch 'private void validateAppealRequestIds\(ScoreAppealRequest request\)' -or
        $appealService -notmatch 'request\.getAttemptId\(\) == null \|\| request\.getAttemptId\(\) <= 0' -or
        $appealService -notmatch 'request\.getQuestionId\(\) != null && request\.getQuestionId\(\) <= 0') {
        Fail "ScoreAppealService must reject non-positive appeal attempt/question ids before database access"
    }
    if ($appealService -notmatch 'private void requirePositiveAppealId\(Long appealId\)' -or
        $appealService -notmatch 'appealId == null \|\| appealId <= 0' -or
        $appealService -notmatch 'requirePositiveAppealId\(appealId\);[\s\S]*baseAppealSelect\(\) \+ " WHERE sa\.id = \?"' -or
        $appealService -notmatch 'requirePositiveAppealId\(appealId\);[\s\S]*baseAppealSelect\(\) \+ " WHERE sa\.id = \? FOR UPDATE"' -or
        $appealService -notmatch 'requirePositiveAppealId\(appealId\);[\s\S]*WHERE sa\.id = \? AND sa\.user_id = \?' -or
        $appealService -notmatch 'if \(appealId != null\) \{[\s\S]*requirePositiveAppealId\(appealId\);[\s\S]*AND sa\.id = \?') {
        Fail "ScoreAppealService must reject non-positive score appeal ids before database access and exact filtering"
    }
    if ($appealService -notmatch '"SCORE_APPEAL",\s*id' -or
        $appealService -notmatch 'JOIN score_release sr ON sr.exam_id = e.id AND sr.status = 1' -or
        $appealService -notmatch 'WHERE a\.id = \? AND a\.user_id = \? AND a\.status = 5 AND a\.score IS NOT NULL' -or
        $appealService -notmatch 'FOR UPDATE' -or
        $appealService -notmatch 'e\.created_by AS examOwnerId' -or
        $appealService -notmatch 'INSERT INTO score_appeal \(attempt_id, exam_id, question_id, user_id, reason, status\)' -or
        $appealService -notmatch 'VALUES \(\?, \?, \?, \?, \?, 0\)' -or
        $appealService -notmatch 'notifyTeachersForNewAppeal' -or
        $appealService -notmatch 'scoreAppealTeacherRecipientIds' -or
        $appealService -notmatch 'new LinkedHashSet<>\(\)' -or
        $appealService -notmatch 'JOIN paper p ON p\.id = e\.paper_id AND p\.deleted = 0' -or
        $appealService -notmatch 'JOIN edu_course co ON co\.subject_id = p\.subject_id' -or
        $appealService -notmatch 'JOIN student_course_enrollment sce' -or
        $appealService -notmatch 'JOIN teacher_class_course tcc' -or
        $appealService -notmatch 'recipientIds\.remove\(student\.getId\(\)\)' -or
        $appealService -notmatch 'sendOnceAndReturnId' -or
        $appealService -notmatch 'New score appeal: ' -or
        $appealService -notmatch 'teacherAppealLink\(appealId\)' -or
        $appealService -notmatch 'private String teacherAppealLink\(Long appealId\)' -or
        $appealService -notmatch '"/reviews\?appealId=" \+ appealId' -or
        $appealService -match '"/reviews\?appealStatus=0&appealHandlingResult=ALL"' -or
        $appealService -notmatch '"SCORE_APPEAL",\s*appealId' -or
        $appealService -notmatch 'listAppeals\(Integer status, String handlingResult, Long appealId, AuthUser handler\)' -or
        $appealService -notmatch 'AND sa\.id = \?' -or
        $appealService -notmatch 'Score appeal window has expired' -or
        $appealService -notmatch 'handling_result' -or
        $appealService -notmatch 'normalizeHandlingResult' -or
        $appealService -notmatch 'sa\.handling_result = \?' -or
        $appealService -notmatch 'studentAppealLink\(id\)' -or
        $appealService -notmatch '"/student/results\?appealId=" \+ appealId') {
        Fail "ScoreAppealService must gate appeal submission to released scored attempts and deep-link teacher/student notifications to appeal ids"
    }
    if ([regex]::Matches($appealService, 'Map<String, Object> appeal = requireAppealAccessForUpdate\(jt, id, handler\);').Count -lt 2 -or
        $appealService -notmatch 'baseAppealSelect\(\) \+ " WHERE sa\.id = \? FOR UPDATE"' -or
        $appealService -notmatch 'requireAppealAccessFromRows') {
        Fail "ScoreAppealService teacher appeal reply and recheck close must lock the appeal row before status transitions"
    }
    if ($reviewController -notmatch '@RequestParam\(required = false\) Long appealId' -or
        $reviewController -notmatch 'scoreAppealService\.listAppeals\(status, handlingResult, appealId, user\)') {
        Fail "ReviewController must expose exact score appeal filtering for teacher notification deep-links"
    }
    if ($reviewController -notmatch '@RequestParam\(required = false\) Long examId' -or
        $reviewController -notmatch 'scoreAppealService\.listAppeals\(status, handlingResult, appealId, examId, user\)' -or
        $appealService -notmatch 'listAppeals\(Integer status, String handlingResult, Long appealId, Long examId, AuthUser handler\)' -or
        $appealService -notmatch 'requirePositiveExamId' -or
        $appealService -notmatch 'AND e\.id = \?') {
        Fail "Score appeal review queue must support scoped exam filtering for release-blocker deep-links"
    }
    if ($reviewController -notmatch 'getPendingReviews\(@RequestParam\(required = false\) Long examId,' -or
        $reviewController -notmatch '@RequestParam\(required = false\) String reviewType' -or
        $reviewController -notmatch 'reviewService\.getPendingReviews\(examId, reviewType, user\)') {
        Fail "ReviewController must expose exam filtering for pending review queues"
    }
    if ($reviewController -notmatch 'GetMapping\("/progress"\)' -or
        $reviewController -notmatch 'listReviewProgress\(@RequestParam\(required = false\) Long examId\)' -or
        $reviewController -notmatch 'reviewService\.listReviewProgress\(examId, user\)') {
        Fail "ReviewController must expose scoped review progress by exam"
    }
    if ($appealService -notmatch 'status IN \(0, 1\)' -or
        $appealService -notmatch '\? IS NULL OR question_id IS NULL OR question_id = \?' -or
        $appealService -notmatch 'An appeal already exists for this attempt or question') {
        Fail "ScoreAppealService must block overlapping active score appeals"
    }
    if ($reviewController -notmatch 'String handlingResult' -or
        $reviewController -notmatch 'listAppeals\(status, handlingResult, appealId, user\)' -or
        $reviewController -notmatch 'appeals/\{id\}/logs' -or
        $reviewController -notmatch 'listAppealLogs' -or
        $reviewController -notmatch 'appeals/\{id\}/logs/export' -or
        $reviewController -notmatch 'exportAppealLogs' -or
        $reviewController -notmatch 'recheck/close' -or
        $reviewController -notmatch 'closeRecheck') {
        Fail "ReviewController must expose handlingResult and exact appeal filtering for score appeals"
    }
    if ($reviewController -notmatch 'score-logs' -or
        $reviewController -notmatch 'listReviewScoreLogs' -or
        $reviewController -notmatch 'exportReviewScoreLogs') {
        Fail "ReviewController must expose scoped review score audit logs and export"
    }
    if ($reviewController -notmatch 'recheck/readiness' -or
        $reviewController -notmatch 'getAppealRecheckReadiness' -or
        $reviewController -notmatch 'scoreAppealService\.recheckReadiness\(id, user\)' -or
        $appealService -notmatch 'public Map<String, Object> recheckReadiness' -or
        $appealService -notmatch 'recheckAnswerEvidence' -or
        $appealService -notmatch 'reviewScoreLogCount' -or
        $appealService -notmatch 'pendingRecheckAnswerCount' -or
        $appealService -notmatch 'NO_REVIEW_SCORE_LOGS' -or
        $appealService -notmatch 'LEFT JOIN review_score_log rsl' -or
        $appealService -notmatch 'MAX\(opened\.created_at\)' -or
        $appealService -notmatch 'Recheck review score evidence is required before closing appeal' -or
        $appealService -notmatch 'closeAllowed') {
        Fail "ScoreAppealService must expose recheck close readiness and review score evidence before closing"
    }
    if ($appealReplyRequest -notmatch 'handlingResult' -or
        $appealReplyRequest -notmatch 'MAINTAINED\|RECHECK_REQUIRED\|ADJUSTED_OFFLINE') {
        Fail "ScoreAppealReplyRequest must require a structured handling result"
    }
    if ($studentScoreAppealRequest -notmatch '@Size\(max = 1000' -or
        $appealReplyRequest -notmatch '@Size\(max = 1000' -or
        $appealRecheckCloseRequest -notmatch '@Size\(max = 1000' -or
        $appealService -notmatch 'MAX_APPEAL_TEXT_LENGTH = 1000' -or
        $appealService -notmatch 'String reason = normalizeRequiredAppealText\(request\.getReason\(\), "appeal reason"\);' -or
        $appealService -notmatch 'String reply = normalizeRequiredAppealText\(request\.getReply\(\), "appeal reply"\);' -or
        $appealService -notmatch 'String recheckNote = normalizeRequiredAppealText\(request\.getRecheckNote\(\), "recheck note"\);' -or
        $appealService -notmatch 'private String normalizeRequiredAppealText\(String value, String fieldName\)' -or
        $appealService -notmatch 'fieldName \+ " must be at most 1000 characters"' -or
        $appealService -notmatch 'Score appeal request is required' -or
        $appealService -notmatch 'Score appeal reply request is required' -or
        $appealService -notmatch 'Score appeal recheck close request is required' -or
        $appealService -match 'trim\(request\.getReason\(\)\)' -or
        $appealService -match 'trim\(request\.getReply\(\)\)' -or
        $appealService -match 'trim\(request\.getRecheckNote\(\)\)') {
        Fail "ScoreAppealService must validate appeal lifecycle text at the service boundary before audit writes"
    }
    if ($appealRecheckCloseRequest -notmatch 'recheckNote' -or
        $appealRecheckCloseRequest -notmatch 'NotBlank' -or
        $appealService -notmatch 'closeRecheck' -or
        $appealService -notmatch 'Only recheck-required appeals can be closed' -or
        $appealService -notmatch 'recheck_note' -or
        $appealService -notmatch 'rechecked_at' -or
        $appealService -notmatch 'recordAppealLog' -or
        $appealService -notmatch 'resolveAppealLogExamId' -or
        $appealService -notmatch 'SELECT exam_id[\s\S]*FROM exam_attempt[\s\S]*WHERE id = \?' -or
        $appealService -notmatch 'resolvedExamId' -or
        $appealService -notmatch 'listAppealLogs' -or
        $appealService -notmatch 'exportAppealLogs' -or
        $appealService -notmatch 'CsvExport\.build\(headers, rows\)' -or
        $appealService -notmatch 'safeExportName\(String\.valueOf\(appeal\.get\("examName"\)\)\)' -or
        $appealService -notmatch 'FROM score_appeal_log l' -or
        $appealService -notmatch 'INSERT INTO score_appeal_log' -or
        $appealService -notmatch '"SUBMIT"' -or
        $appealService -notmatch '"REPLY"' -or
        $appealService -notmatch '"RECHECK_OPEN"' -or
        $appealService -notmatch '"CLOSE_RECHECK"' -or
        $appealService -notmatch 'reopenAppealReviewTasks' -or
        $appealService -notmatch 'countPendingRecheckAnswers' -or
        $appealService -notmatch 'requireRecheckAttemptFinalized' -or
        $appealService -notmatch 'status = 5 AND score IS NOT NULL' -or
        $appealService -notmatch 'Recheck attempt must be finalized with a score before closing appeal' -or
        $appealService -notmatch 'LEFT JOIN exam_question_snapshot eqs' -or
        $appealService -notmatch 'COALESCE\(eqs\.question_type, q\.question_type, ''''\) IN \(''FILL_BLANK'', ''SUBJECTIVE''\)' -or
        $appealService -notmatch 'review_status = 0' -or
        $appealService -notmatch 'Recheck review tasks must be completed before closing appeal') {
        Fail "ScoreAppealService must support snapshot-based recheck-required score appeals"
    }
    if ($appealService -notmatch 'Long submitLogId = recordAppealLog' -or
        $appealService -notmatch 'Long replyLogId = recordAppealLog' -or
        $appealService -notmatch 'Long recheckOpenLogId = recordAppealLog' -or
        $appealService -notmatch 'Long closeLogId = recordAppealLog' -or
        $appealService -notmatch 'result\.put\("scoreAppealLogId"' -or
        $appealService -notmatch 'result\.put\("scoreAppealLogIds"' -or
        $appealService -notmatch 'return jt\.queryForObject\("SELECT LAST_INSERT_ID\(\)", Long\.class\);' -or
        $appealService -notmatch 'private List<Long> logIds') {
        Fail "ScoreAppealService appeal transitions must return generated score appeal audit log IDs"
    }
    if ($appealService -notmatch "WHERE l\.appeal_id = sa\.id AND l\.action = 'SUBMIT'[\s\S]*AS scoreAppealLogId") {
        Fail "ScoreAppealService appeal list/detail queries must expose the submit audit log id"
    }
    if ($appealService -notmatch 'EXPECTED_ANSWER_SCOPE_CONDITION' -or
        $appealService -notmatch 'eqs_scope\.question_id = ar\.question_id' -or
        $appealService -notmatch 'pq_scope\.question_id = ar\.question_id' -or
        $appealService -notmatch 'WHERE ar\.attempt_id = \? AND ar\.question_id = \?' -or
        $appealService -notmatch 'WHERE ar\.attempt_id = \? AND ar\.review_status = 0' -or
        $appealService -match 'UPDATE answer_record\s+SET review_status = 0' -or
        $appealService -match 'FROM answer_record\s+WHERE attempt_id = \? AND review_status = 0') {
        Fail "ScoreAppealService recheck reopen and pending checks must be scoped to the expected question set"
    }
    if ($appealService -notmatch 'COALESCE\(eqs\.stem, q\.stem\) AS questionStem' -or
        $appealService -notmatch 'COALESCE\(eqs\.question_type, q\.question_type\) AS questionType' -or
        $appealService -notmatch 'LEFT JOIN exam_question_snapshot eqs ON eqs\.exam_id = e\.id AND eqs\.question_id = sa\.question_id') {
        Fail "ScoreAppealService appeal list/detail question labels must prefer exam snapshots"
    }
    $examController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\ExamController.java" -Raw
    if ($examController -notmatch 'approval-logs' -or
        $examController -notmatch 'listApprovalLogs' -or
        $examController -notmatch 'approval-logs/export' -or
        $examController -notmatch 'exportApprovalLogs') {
        Fail "ExamController must expose scoped approval audit logs and export"
    }
    if ($examController -notmatch 'approvals/reminders/export' -or
        $examController -notmatch 'exportApprovalReminderLogs' -or
        $examController -notmatch '@RequestParam\(required = false\) Long logId' -or
        $examController -notmatch 'listApprovalReminderLogs\(page, size, logId, currentUser\(\)\)') {
        Fail "ExamController must expose approval reminder log filtering and export"
    }
    if ($examController -notmatch 'score-release-logs' -or
        $examController -notmatch 'listScoreReleaseLogs' -or
        $examController -notmatch 'score-release-logs/export' -or
        $examController -notmatch 'exportScoreReleaseLogs') {
        Fail "ExamController must expose score release audit logs"
    }
    if ($examController -notmatch 'scores/readiness' -or
        $examController -notmatch 'scoreReleaseReadiness') {
        Fail "ExamController must expose score release readiness preflight"
    }
    if ($examController -notmatch 'OperationLogService' -or
        $examController -notmatch 'withOperationLogId' -or
        $examController -notmatch 'CREATE_EXAM' -or
        $examController -notmatch 'UPDATE_EXAM' -or
        $examController -notmatch 'DELETE_EXAM' -or
        $examController -notmatch 'CLOSE_EXAM' -or
        $examController -notmatch 'result\.put\("operationLogId", operationLogId\)') {
        Fail "Exam management mutations must return generated operation log ids for audit evidence"
    }
    $monitorController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\MonitorController.java" -Raw
    $monitorService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\MonitorService.java" -Raw
    $cheatEventRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\monitor\CheatEventRequest.java" -Raw
    $cheatEventBatchRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\monitor\CheatEventBatchRequest.java" -Raw
    $monitorActionRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\monitor\MonitorActionRequest.java" -Raw
    $monitorForceSubmitRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\monitor\MonitorForceSubmitRequest.java" -Raw
    $aiController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\AiController.java" -Raw
    $authController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\AuthController.java" -Raw
    $authService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\AuthService.java" -Raw
    $userController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\UserController.java" -Raw
    $roleController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\RoleController.java" -Raw
    $basicDataController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\BasicDataController.java" -Raw
    $paperController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\PaperController.java" -Raw
    $operationLogService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\OperationLogService.java" -Raw
    $basicDataService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\BasicDataService.java" -Raw
    $questionBankService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\QuestionBankService.java" -Raw
    if ($monitorController -notmatch 'logs/export' -or
        $monitorController -notmatch 'exportOperationLogs' -or
        $monitorController -notmatch 'Long logId' -or
        $monitorController -notmatch 'String keyword' -or
        $monitorService -notmatch 'appendOperationLogFilters' -or
        $monitorService -notmatch 'appendOperationLogFilters\(StringBuilder where, List<Object> params, Long logId' -or
        $monitorService -notmatch 'AND l\.id = \?' -or
        $monitorService -notmatch 'exportOperationLogs' -or
        $monitorService -notmatch 'FROM operation_log l' -or
        $monitorService -notmatch 'LIMIT 5000') {
        Fail "Monitor operation log APIs must support administrator exact ID lookup, search, and export"
    }
    if ($operationLogService -notmatch 'public Long record' -or
        $operationLogService -notmatch 'GeneratedKeyHolder' -or
        $operationLogService -notmatch 'Statement\.RETURN_GENERATED_KEYS' -or
        $operationLogService -notmatch 'return key == null \? null : key\.longValue\(\)' -or
        $userController -notmatch 'operationLogId' -or
        $userController -notmatch 'responseWithOperationLogId' -or
        $userController -notmatch 'Long operationLogId = operationLogService\.record' -or
        $userController -notmatch 'user\.put\("operationLogId", operationLogId\)') {
        Fail "User management operations must return generated operation log ids for audit evidence"
    }
    if ($roleController -notmatch 'OperationLogService' -or
        $roleController -notmatch 'AuthContext\.requireSession' -or
        $roleController -notmatch 'UPDATE_ROLE_PAGES' -or
        $roleController -notmatch 'before=' -or
        $roleController -notmatch 'after=' -or
        $roleController -notmatch 'result\.put\("operationLogId", operationLogId\)') {
        Fail "Role permission updates must return generated operation log ids for audit evidence"
    }
    if ($basicDataController -notmatch 'OperationLogService' -or
        $basicDataController -notmatch 'withOperationLogId' -or
        $basicDataController -notmatch 'CREATE_BASIC_CLASS' -or
        $basicDataController -notmatch 'UPDATE_BASIC_COURSE' -or
        $basicDataController -notmatch 'DELETE_BASIC_CLASS_COURSE' -or
        $basicDataController -notmatch 'CREATE_BASIC_TEACHING_ASSIGNMENT' -or
        $basicDataController -notmatch 'CREATE_BASIC_STUDENT_MEMBERSHIP' -or
        $basicDataController -notmatch 'UPDATE_BASIC_SUBJECT' -or
        $basicDataController -notmatch 'DELETE_BASIC_KNOWLEDGE_POINT' -or
        $basicDataController -notmatch 'CREATE_BASIC_NOTICE' -or
        $basicDataController -notmatch 'result\.put\("operationLogId", operationLogId\)') {
        Fail "Basic data mutations must return generated operation log ids for audit evidence"
    }
    if ($basicDataController -notmatch '@RequestParam\(required = false\) Long noticeId' -or
        $basicDataController -notmatch 'basicDataService\.listNotices\(keyword, status, noticeId, user\)' -or
        $basicDataService -notmatch 'listNotices\(String keyword, Integer status, Long noticeId, AuthUser user\)' -or
        $basicDataService -notmatch 'AND \(\? IS NULL OR n\.id = \?\)' -or
        $basicDataService -notmatch 'noticeLink\(noticeId\)' -or
        $basicDataService -notmatch 'private String noticeLink\(Long noticeId\)' -or
        $basicDataService -notmatch '"/basic/notices\?noticeId=" \+ noticeId' -or
        $basicDataService -notmatch '"NOTICE", noticeLink\(noticeId\), "NOTICE", noticeId') {
        Fail "Basic notice notifications must relate to and deep-link to exact notice ids"
    }
    if ($paperController -notmatch 'OperationLogService' -or
        $paperController -notmatch 'withOperationLogId' -or
        $paperController -notmatch 'CREATE_PAPER' -or
        $paperController -notmatch 'GENERATE_PAPER' -or
        $paperController -notmatch 'COPY_PAPER' -or
        $paperController -notmatch 'UPDATE_PAPER_STATUS' -or
        $paperController -notmatch 'DELETE_PAPER' -or
        $paperController -notmatch 'result\.put\("operationLogId", operationLogId\)') {
        Fail "Paper mutations must return generated operation log ids for audit evidence"
    }
    if ($questionBankService -notmatch 'private Long recordQuestionLog' -or
        $questionBankService -notmatch 'GeneratedKeyHolder' -or
        $questionBankService -notmatch 'Statement\.RETURN_GENERATED_KEYS' -or
        $questionBankService -notmatch 'withQuestionReviewLogId' -or
        $questionBankService -notmatch 'result\.put\("questionReviewLogId", questionReviewLogId\)' -or
        [regex]::Matches($questionBankService, 'Long questionReviewLogId = recordQuestionLog').Count -lt 7) {
        Fail "Question bank mutations must return generated question review audit log ids for local evidence"
    }
    if ($questionBankService -notmatch 'MAX_QUESTION_REVIEW_COMMENT_LENGTH = 500' -or
        $questionBankService -notmatch 'private String normalizeOptionalQuestionReviewComment\(String comment\)' -or
        $questionBankService -notmatch 'private String normalizeRequiredQuestionReviewComment\(String comment\)' -or
        $questionBankService -notmatch 'normalized\.length\(\) > MAX_QUESTION_REVIEW_COMMENT_LENGTH' -or
        $questionBankService -notmatch 'Question review comment must be 500 characters or less' -or
        $questionBankService -notmatch 'String safeComment = normalizeOptionalQuestionReviewComment\(comment\);' -or
        $questionBankService -notmatch 'String safeComment = normalizeRequiredQuestionReviewComment\(comment\);' -or
        $questionBankService -notmatch 'statement\.setString\(8, normalizeOptionalQuestionReviewComment\(comment\)\);' -or
        $questionBankService -match 'truncate\(blankToNull\(comment\), 500\)') {
        Fail "Question review comments must be normalized and overlong values rejected before writing question review evidence"
    }
    $questionRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\question\QuestionRequest.java" -Raw
    if ($questionRequest -notmatch 'Size\(max = 255' -or
        $questionRequest -notmatch 'Size\(max = 500' -or
        $questionRequest -notmatch 'Size\(max = 64' -or
        $questionBankService -notmatch 'MAX_SOURCE_DETAIL_LENGTH = 255' -or
        $questionBankService -notmatch 'MAX_SOURCE_EXCERPT_LENGTH = 500' -or
        $questionBankService -notmatch 'MAX_AI_MODEL_LENGTH = 64' -or
        $questionBankService -notmatch 'MAX_PROMPT_VERSION_LENGTH = 64' -or
        $questionBankService -notmatch 'private String normalizeQuestionSourceMetadata\(String value, int maxLength, String fieldName\)' -or
        $questionBankService -notmatch 'normalizeSourceDetail\(request\.getSourceDetail\(\)\)' -or
        $questionBankService -notmatch 'normalizeSourceExcerpt\(request\.getSourceExcerpt\(\)\)' -or
        $questionBankService -notmatch 'normalizeAiModel\(request\.getAiModel\(\)\)' -or
        $questionBankService -notmatch 'normalizePromptVersion\(request\.getPromptVersion\(\)\)' -or
        $questionBankService -match 'truncate\(blankToNull\(request\.get(SourceExcerpt|AiModel|PromptVersion)\(\)\), (500|64)\)' -or
        $questionBankService -match 'blankToNull\(request\.getSourceDetail\(\)\), request\.getMaterialId\(\)') {
        Fail "Question source metadata must be normalized and overlong values rejected before question/version evidence is written"
    }
    $questionOptionRequest = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\dto\question\QuestionOptionRequest.java" -Raw
    if ($questionRequest -notmatch 'Size\(max = 4000' -or
        $questionOptionRequest -notmatch 'Size\(max = 16' -or
        $questionOptionRequest -notmatch 'Size\(max = 1000' -or
        $questionBankService -notmatch 'MAX_QUESTION_TEXT_LENGTH = 4000' -or
        $questionBankService -notmatch 'MAX_OPTION_LABEL_LENGTH = 16' -or
        $questionBankService -notmatch 'MAX_OPTION_CONTENT_LENGTH = 1000' -or
        $questionBankService -notmatch 'private String normalizeQuestionStem\(String value\)' -or
        $questionBankService -notmatch 'private String normalizeQuestionCorrectAnswer\(String value\)' -or
        $questionBankService -notmatch 'private String normalizeQuestionAnalysis\(String value\)' -or
        $questionBankService -notmatch 'private void validateQuestionOptions\(List<QuestionOptionRequest> options\)' -or
        $questionBankService -notmatch 'private String normalizeRequiredQuestionText\(String value, int maxLength, String fieldName\)' -or
        $questionBankService -notmatch 'private String normalizeOptionalQuestionText\(String value, int maxLength, String fieldName\)' -or
        [regex]::Matches($questionBankService, 'normalizeQuestionStem\(request\.getStem\(\)\)').Count -lt 3 -or
        [regex]::Matches($questionBankService, 'normalizeQuestionCorrectAnswer\(request\.getCorrectAnswer\(\)\)').Count -lt 4 -or
        [regex]::Matches($questionBankService, 'normalizeQuestionAnalysis\(request\.getAnalysis\(\)\)').Count -lt 3 -or
        $questionBankService -notmatch 'validateQuestionOptions\(request\.getOptions\(\)\);' -or
        $questionBankService -notmatch 'normalizeOptionLabel\(option\), normalizeOptionContent\(option\)' -or
        $questionBankService -match 'trim\(request\.get(Stem|CorrectAnswer|Analysis)\(\)\)' -or
        $questionBankService -match 'trim\(option\.get(OptionLabel|OptionContent)\(\)\)') {
        Fail "Question core text and options must be normalized and bounded in service layer before question/version evidence is written"
    }
    if ($materialLibraryService -notmatch 'MAX_MATERIAL_TITLE_LENGTH = 200' -or
        $materialLibraryService -notmatch 'MAX_MATERIAL_FILENAME_LENGTH = 255' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialFilename\(String filename\)' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialTitle\(String title\)' -or
        $materialLibraryService -notmatch 'Material filename must be 255 characters or less' -or
        $materialLibraryService -notmatch 'Material title must be 200 characters or less' -or
        $materialLibraryService -notmatch 'String filename = normalizeMaterialFilename\(file\.getOriginalFilename\(\)\);' -or
        $materialLibraryService -notmatch 'String materialTitle = normalizeMaterialTitle\(firstNonBlank\(title, filename\.isBlank\(\) \? "[^"]+" : filename\)\);' -or
        $materialLibraryService -match 'truncate\(materialTitle, 200\)' -or
        $materialLibraryService -match 'truncate\(filename, 255\)' -or
        $materialLibraryService -match 'safeFilename\(file\.getOriginalFilename\(\)\)') {
        Fail "Material upload titles and filenames must be normalized and overlong values rejected before material evidence is written"
    }
    if ($materialLibraryService -notmatch 'MAX_MATERIAL_OUTLINE_TITLE_LENGTH = 200' -or
        $materialLibraryService -notmatch 'MAX_MATERIAL_OUTLINE_SUMMARY_LENGTH = 1000' -or
        $materialLibraryService -notmatch 'MAX_MATERIAL_OUTLINE_KEYWORDS_LENGTH = 500' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialOutlineTitle\(Object title\)' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialOutlineSummary\(Object summary\)' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialOutlineKeywords\(Object keywords\)' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialOutlineText\(Object value, int maxLength, String fieldName\)' -or
        $materialLibraryService -notmatch 'normalizeMaterialOutlineTitle\(item\.get\("title"\)\)' -or
        $materialLibraryService -notmatch 'normalizeMaterialOutlineSummary\(item\.get\("summary"\)\)' -or
        $materialLibraryService -notmatch 'normalizeMaterialOutlineKeywords\(item\.get\("keywords"\)\)' -or
        $materialLibraryService -match 'truncate\(String\.valueOf\(item\.getOrDefault\("title"' -or
        $materialLibraryService -match 'truncate\(stringValue\(item\.get\("(summary|keywords)"\)\), (1000|500)\)') {
        Fail "Material outline evidence must be normalized and overlong values rejected before outline rows are written"
    }
    if ($materialLibraryService -notmatch 'MAX_MATERIAL_CHUNK_HEADING_LENGTH = 200' -or
        $materialLibraryService -notmatch 'MAX_MATERIAL_CHUNK_KEYWORDS_LENGTH = 500' -or
        $materialLibraryService -notmatch 'MAX_MATERIAL_CHUNK_CONTENT_LENGTH = 4000' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialChunkHeading\(String heading\)' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialChunkKeywords\(String keywords\)' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialChunkContent\(String content\)' -or
        $materialLibraryService -notmatch 'private String normalizeMaterialChunkText\(String value, int maxLength, String fieldName\)' -or
        $materialLibraryService -notmatch 'private void appendContentChunks\(List<MaterialChunk> chunks, int paragraph, String heading, String content\)' -or
        $materialLibraryService -notmatch 'private int chunkContentSplitEnd\(String content, int offset\)' -or
        $materialLibraryService -notmatch 'appendContentChunks\(chunks, startParagraph, heading, buffer\.toString\(\)\)' -or
        $materialLibraryService -notmatch 'normalizeMaterialChunkHeading\(chunk\.heading\(\)\), chunk\.content\(\)' -or
        $materialLibraryService -notmatch 'normalizeMaterialChunkKeywords\(chunk\.keywords\(\)\)' -or
        $materialLibraryService -notmatch 'if \(!buffer\.isEmpty\(\) && buffer\.length\(\) \+ line\.length\(\) > 1200\)' -or
        $materialLibraryService -match 'truncate\(chunk\.heading\(\), 200\)' -or
        $materialLibraryService -match 'truncate\(chunk\.keywords\(\), 500\)' -or
        $materialLibraryService -match 'truncate\(content\.trim\(\), 4000\)') {
        Fail "Material chunk evidence must be normalized and long-line chunking must not create empty chunks"
    }
    if ($materialLibraryService -notmatch 'question\.setSourceDetail\("Material library: " \+ material\.get\("title"\)\);' -or
        $materialLibraryService -notmatch 'builder\.append\("\[page "\)\.append\(chunk\.pageNo\(\)\)\.append\(" paragraph "\)\.append\(chunk\.paragraphNo\(\)\)\.append\("\] "\);') {
        Fail "Material library AI evidence labels must use stable non-mojibake source and page/paragraph text"
    }
    if ($aiService -notmatch 'PROMPT_VERSION_MATERIAL_GENERATE = "material-question-v3"' -or
        $aiService -notmatch 'If material chunks use labels like \[page 1 paragraph 2\], return sourcePage, sourceParagraph, and sourceExcerpt\.') {
        Fail "AI material question prompt must match material context page/paragraph labels and version the prompt change"
    }
    if ($aiService -notmatch 'MAX_MATERIAL_TYPE_COUNT = 30' -or
        $aiService -notmatch 'value > MAX_MATERIAL_TYPE_COUNT' -or
        $aiService -notmatch 'Each material question type count must be 30 or less' -or
        $aiService -match 'Math\.min\(value, 30\)') {
        Fail "AI material question type counts must be rejected when over limit instead of silently capped"
    }
    if ($aiService -notmatch 'MAX_BATCH_QUESTION_COUNT = 10' -or
        $aiService -notmatch 'normalizedCount\(request\.getCount\(\)\);' -or
        $aiService -notmatch 'value > MAX_BATCH_QUESTION_COUNT' -or
        $aiService -notmatch 'AI batch question count must be 10 or less' -or
        $aiService -match 'Math\.max\(1, Math\.min\(value, 10\)\)') {
        Fail "AI batch question count must be rejected when over limit instead of silently capped"
    }
    if ($questionBankPanel -notmatch 'AI_BATCH_QUESTION_COUNT = 10' -or
        $questionBankPanel -notmatch 'AI_MATERIAL_TYPE_COUNT = 30' -or
        $questionBankPanel -notmatch 'AI_MATERIAL_TOTAL_COUNT = 30' -or
        $questionBankPanel -notmatch 'AI_DOCUMENT_MAX_FILE_BYTES = 25 \* 1024 \* 1024' -or
        $questionBankPanel -notmatch 'AI_SOURCE_DETAIL_MAX_LENGTH = 255' -or
        $questionBankPanel -notmatch 'AI_DOCUMENT_SUPPORTED_EXTENSIONS = \[''txt'', ''text'', ''md'', ''doc'', ''docx'', ''ppt'', ''pptx'', ''pdf''\]' -or
        $questionBankPanel -notmatch 'AI_DOCUMENT_ACCEPT = AI_DOCUMENT_SUPPORTED_EXTENSIONS\.map' -or
        $questionBankPanel -notmatch ':accept="AI_DOCUMENT_ACCEPT"' -or
        $questionBankPanel -notmatch 'QUESTION_DOCUMENT_SOURCE_LABEL = ''Question document import''' -or
        $questionBankPanel -notmatch 'MATERIAL_GENERATION_SOURCE_LABEL = ''Course material generation''' -or
        $questionBankPanel -notmatch ':max="AI_BATCH_QUESTION_COUNT"' -or
        $questionBankPanel -notmatch ':max="AI_MATERIAL_TYPE_COUNT"' -or
        $questionBankPanel -notmatch 'materialQuestionTotalInvalid' -or
        $questionBankPanel -notmatch ':disabled="materialQuestionTotalInvalid"' -or
        $questionBankPanel -notmatch 'function validateDocumentUploadFile\(file: File, sourceLabel: string\)' -or
        $questionBankPanel -notmatch 'file\.size > AI_DOCUMENT_MAX_FILE_BYTES' -or
        $questionBankPanel -notmatch 'AI_SOURCE_DETAIL_MAX_LENGTH - sourceLabel\.length - 2' -or
        $questionBankPanel -notmatch '!AI_DOCUMENT_SUPPORTED_EXTENSIONS\.includes\(documentExtension\(filename\)\)' -or
        $questionBankPanel -notmatch 'function documentExtension\(filename: string\)' -or
        $questionBankPanel -notmatch 'validateDocumentUploadFile\(file, QUESTION_DOCUMENT_SOURCE_LABEL\)' -or
        $questionBankPanel -notmatch 'validateDocumentUploadFile\(file, MATERIAL_GENERATION_SOURCE_LABEL\)' -or
        $questionBankPanel -notmatch 'function validateMaterialQuestionCounts\(\)' -or
        $questionBankPanel -notmatch 'total > AI_MATERIAL_TOTAL_COUNT' -or
        $questionBankPanel -notmatch 'if \(!validateMaterialQuestionCounts\(\)\) return;' -or
        $questionBankPanel -match ':max="20"') {
        Fail "QuestionBankPanel AI count controls must match backend batch and material generation limits"
    }
    if ($aiService -notmatch 'MAX_GENERATED_QUESTION_TEXT_LENGTH = 4000' -or
        $aiService -notmatch 'MAX_GENERATED_OPTION_CONTENT_LENGTH = 1000' -or
        $aiService -notmatch 'private String normalizeGeneratedQuestionText\(String value, String fieldName\)' -or
        $aiService -notmatch 'private String normalizeGeneratedOptionContent\(String value\)' -or
        $aiService -notmatch 'normalized\.length\(\) > MAX_GENERATED_QUESTION_TEXT_LENGTH' -or
        $aiService -notmatch 'normalized\.length\(\) > MAX_GENERATED_OPTION_CONTENT_LENGTH' -or
        $aiService -match 'truncate\(normalized\.getStem\(\)\.trim\(\), 4000\)' -or
        $aiService -match 'truncate\(firstNonBlank\(normalized\.getAnalysis\(\)' -or
        $aiService -match 'truncate\(firstNonBlank\(normalized\.getCorrectAnswer\(\)' -or
        $aiService -match 'truncate\(content, 1000\)' -or
        $aiService -match 'truncate\(item\.getOptionContent\(\)\.trim\(\), 1000\)') {
        Fail "AI generated question text and options must be bounded explicitly instead of silently truncated before teacher review"
    }
    if ($documentTextExtractorService -notmatch 'MAX_TEXT_LENGTH = 80_000' -or
        $documentTextExtractorService -notmatch 'SUPPORTED_EXTENSIONS = Set\.of\("txt", "text", "md", "doc", "docx", "ppt", "pptx", "pdf"\)' -or
        $documentTextExtractorService -notmatch 'ensureSupportedExtension\(extension\);' -or
        $documentTextExtractorService -notmatch 'private void ensureSupportedExtension\(String extension\)' -or
        $documentTextExtractorService -notmatch 'Unsupported document file type; supported: txt, text, md, doc, docx, ppt, pptx, pdf' -or
        $documentTextExtractorService -match 'default -> decodePlainText\(bytes\)' -or
        $documentTextExtractorService -notmatch 'ensureExtractedTextWithinLimit\(normalized\);' -or
        $documentTextExtractorService -notmatch 'private void ensureExtractedTextWithinLimit\(String normalized\)' -or
        $documentTextExtractorService -notmatch 'Extracted document text must be 80000 characters or less' -or
        $documentTextExtractorService -match 'substring\(0, MAX_TEXT_LENGTH\)') {
        Fail "Document extraction must reject unsupported or overlong material text instead of silently accepting unsafe source evidence"
    }
    if ($aiController -notmatch 'MAX_SOURCE_DETAIL_LENGTH = 255' -or
        $aiController -notmatch 'detail\.length\(\) > MAX_SOURCE_DETAIL_LENGTH' -or
        $aiController -notmatch 'AI source detail must be 255 characters or less' -or
        $aiController -match 'detail\.substring\(0, 255\)') {
        Fail "AI controller source detail must reject overlong provenance instead of silently truncating it"
    }
    if ($aiController -notmatch 'questionReviewLogIds' -or
        $aiController -notmatch 'question\.get\("questionReviewLogId"\)' -or
        $aiController -notmatch '"AI question drafts saved"' -or
        $aiController -notmatch '"questions", saved') {
        Fail "AI generated question saves must return generated question review audit log ids"
    }
    if ($authController -notmatch 'recordLoginFailure' -or
        $authController -notmatch 'recordAuthSecurityEvent' -or
        $authController -notmatch 'LOGIN_FAILED' -or
        $authController -notmatch 'CODE_LOGIN_FAILED' -or
        $authController -notmatch 'REGISTER_REQUEST' -or
        $authController -notmatch 'LOGOUT' -or
        $authController -notmatch 'PASSWORD_CHANGED' -or
        $authController -notmatch 'LOGIN_CODE_SENT' -or
        $authController -notmatch 'BIND_CODE_SENT' -or
        $authController -notmatch 'EMAIL_BOUND' -or
        $authController -notmatch 'PROFILE_UPDATED' -or
        $authController -match 'request\.getPassword\(\)' -or
        $authService -notmatch 'listLoginLogs' -or
        $authService -notmatch "action IN \('登录系统', '验证码登录'" -or
        $monitorController -notmatch 'login-logs' -or
        $monitorController -notmatch 'getLoginAuditLogs' -or
        $monitorController -notmatch 'exportLoginAuditLogs' -or
        $monitorService -notmatch 'getLoginAuditLogs' -or
        $monitorService -notmatch 'exportLoginAuditLogs' -or
        $monitorService -notmatch 'appendLoginAuditFilters' -or
        $monitorService -notmatch 'FROM operation_log l' -or
        $monitorService -notmatch 'loginAuditSuccessText' -or
        $monitorService -notmatch 'LIMIT 5000') {
        Fail "Authentication failures and administrator login audit logs must be recorded, searched, and exported"
    }
    if ($monitorController -notmatch 'ai-logs/export' -or
        $monitorController -notmatch 'exportAiUsageLogs' -or
        $monitorService -notmatch 'appendAiUsageLogFilters' -or
        $monitorService -notmatch 'exportAiUsageLogs' -or
        $monitorService -notmatch 'l\.prompt LIKE' -or
        $monitorService -notmatch 'aiUsageSuccessText') {
        Fail "Monitor AI usage log APIs must support scoped search and export"
    }
    if ($cheatEventRequest -notmatch 'import jakarta\.validation\.constraints\.Positive;' -or
        $cheatEventRequest -notmatch '@Positive\(message = "attemptId must be positive"\)' -or
        $cheatEventBatchRequest -notmatch 'import jakarta\.validation\.Valid;' -or
        $cheatEventBatchRequest -notmatch '@NotEmpty\(message = "Monitor events cannot be empty"\)' -or
        $cheatEventBatchRequest -notmatch '@Size\(max = 100, message = "At most 100 monitor events can be reported at once"\)' -or
        $cheatEventBatchRequest -notmatch 'private List<@Valid CheatEventRequest> events;' -or
        $cheatEventRequest -notmatch '@NotBlank\(message = "clientEventTime is required for monitor event reporting"\)' -or
        $monitorService -notmatch 'Timestamp clientEventTime = requireClientEventTime\(request\.getClientEventTime\(\)\);' -or
        $monitorService -notmatch 'private Timestamp requireClientEventTime\(String value\)' -or
        $monitorService -notmatch 'clientEventTime is required for monitor event reporting' -or
        $monitorService -notmatch 'attemptId = requireAttemptId\(attemptId\);' -or
        $monitorService -notmatch 'examId = requireExamId\(examId\);' -or
        $monitorService -notmatch 'sessionId = requireSessionId\(sessionId\);' -or
        $monitorService -notmatch 'request = requireMonitorActionRequest\(request\);' -or
        $monitorService -notmatch 'private MonitorActionRequest requireMonitorActionRequest\(MonitorActionRequest request\)' -or
        $monitorService -notmatch 'Monitor action request is required' -or
        $monitorService -notmatch 'private Long requireAttemptId\(Long attemptId\)' -or
        $monitorService -notmatch 'private Long requireExamId\(Long examId\)' -or
        $monitorService -notmatch 'private Long requireSessionId\(Long sessionId\)' -or
        $monitorService -notmatch 'examId must be positive' -or
        $monitorService -notmatch 'sessionId must be positive') {
        Fail "Monitor APIs must reject non-positive attempt, exam, and session ids before database access"
    }
    if ($cheatEventRequest -notmatch '@Size\(max = 1000' -or
        $monitorActionRequest -notmatch '@Size\(max = 1000' -or
        $monitorForceSubmitRequest -notmatch '@Size\(max = 1000' -or
        $monitorService -notmatch 'MAX_MONITOR_TEXT_LENGTH = 1000' -or
        $monitorService -notmatch 'String extraInfo = normalizeMonitorText\(request\.getExtraInfo\(\), "extraInfo"\);' -or
        $monitorService -notmatch 'String note = normalizeMonitorText\(request\.getNote\(\), "note"\);' -or
        $monitorService -notmatch 'String actionNote = normalizeMonitorText\(note, "note"\);' -or
        $monitorService -notmatch 'private String normalizeMonitorText\(String value, String fieldName\)' -or
        $monitorService -notmatch 'fieldName \+ " must be at most 1000 characters"' -or
        $monitorService -match 'trimToLength\(request\.getExtraInfo\(\), 1000\)' -or
        $monitorService -match 'trimToLength\(request\.getNote\(\), 1000\)' -or
        $monitorService -match 'trimToLength\(note, 1000\)') {
        Fail "Monitor audit text fields must reject overlong evidence text instead of silently truncating it"
    }
    if ($monitorController -notmatch 'sessions/export' -or
        $monitorController -notmatch 'exportExamMonitorSessions' -or
        $monitorController -notmatch 'String sessionStatus' -or
        $monitorController -notmatch 'Integer minRiskScore' -or
        $monitorController -notmatch 'String latestNotificationStatus' -or
        $monitorController -notmatch 'String rulesConfirmationStatus' -or
        $monitorController -notmatch 'String latestActionType' -or
        $monitorService -notmatch 'exportExamMonitorSessions' -or
        $monitorService -notmatch 'filterMonitorSessionsForExport' -or
        $monitorService -notmatch 'normalizeSessionExportStatus' -or
        $monitorService -notmatch 'Unsupported monitor session status' -or
        $monitorService -notmatch 'normalizeLatestNotificationStatus' -or
        $monitorService -notmatch 'Unsupported latest notification status' -or
        $monitorService -notmatch 'latestNotificationStatusMatches' -or
        $monitorService -notmatch 'normalizeRulesConfirmationStatus' -or
        $monitorService -notmatch 'Unsupported rules confirmation status' -or
        $monitorService -notmatch 'rulesConfirmationStatusMatches' -or
        $monitorService -notmatch 'normalizeLatestActionType' -or
        $monitorService -notmatch 'Unsupported latest monitor action type' -or
        $monitorService -notmatch 'RULES_REMINDER' -or
        $monitorService -notmatch 'MONITOR_RULES_REMINDER' -or
        $monitorService -notmatch '/student/exams\?notice=rules&attemptId=' -or
        $monitorService -notmatch 'MONITOR_WARNING' -or
        $monitorService -notmatch 'MONITOR_FORCE_SUBMIT' -or
        $monitorService -notmatch 'String attemptLink = monitorAttemptLink\(attemptId\)' -or
        $monitorService -notmatch 'private String monitorAttemptLink\(Long attemptId\)' -or
        $monitorService -notmatch '"/student/exams\?attemptId=" \+ attemptId' -or
        $monitorService -notmatch '"MONITOR_WARNING",\s*attemptLink,\s*"EXAM_ATTEMPT",\s*attemptId' -or
        $monitorService -notmatch '"MONITOR_FORCE_SUBMIT",\s*attemptLink,\s*"EXAM_ATTEMPT",\s*attemptId' -or
        $monitorService -notmatch 'RULES_REMINDER action requires missing rules confirmation' -or
        $monitorService -notmatch 'a\.rules_confirmed_at AS rulesConfirmedAt' -or
        $monitorService -notmatch 'normalizeMinRiskScore\(minRiskScore, "minRiskScore"\)' -or
        $monitorService -notmatch 'listExamMonitorSessions' -or
        $monitorService -notmatch 'rules_confirmed_at AS rulesConfirmedAt' -or
        $monitorService -notmatch 'AS questionCount' -or
        $monitorService -notmatch 'AS answeredCount' -or
        $monitorService -notmatch 'AS unansweredCount' -or
        $monitorService -notmatch 'FROM exam_question_snapshot eqs_count' -or
        $monitorService -notmatch 'FROM paper_question pq_count' -or
        $monitorService -notmatch 'FROM exam_question_snapshot eqs_total' -or
        $monitorService -notmatch 'FROM paper_question pq_total' -or
        $monitorService -notmatch 'FROM exam_question_snapshot eqs_exists' -or
        $monitorService -notmatch 'JOIN exam_question_snapshot eqs_answered' -or
        $monitorService -notmatch 'JOIN paper_question pq_answered' -or
        $monitorService -notmatch 'COUNT\(DISTINCT ar\.question_id\)' -or
        $monitorService -notmatch '"Question Count"' -or
        $monitorService -notmatch '"Answered Count"' -or
        $monitorService -notmatch '"Unanswered Count"' -or
        $monitorService -notmatch '"Rules Confirmed At"' -or
        $monitorService -notmatch '"Rules Reminder Status"' -or
        $monitorService -notmatch 'a\.last_draft_saved_at AS lastDraftSavedAt' -or
        $monitorService -notmatch 'a\.draft_version AS draftRevision' -or
        $monitorService -notmatch 'END AS deadlineAt' -or
        $monitorService -notmatch 'END AS remainingSeconds' -or
        $monitorService -notmatch 'TIMESTAMPDIFF\(SECOND, NOW\(\)' -or
        $monitorService -notmatch '"Last Draft Saved At"' -or
        $monitorService -notmatch '"Draft Revision"' -or
        $monitorService -notmatch '"Deadline At"' -or
        $monitorService -notmatch '"Remaining Seconds"' -or
        $monitorService -notmatch 'rulesReminderStatusText' -or
        $monitorService -notmatch 'Pending confirmation' -or
        $monitorService -notmatch 'Confirmed after reminder' -or
        $monitorService -notmatch 'session\.get\("rulesConfirmedAt"\)' -or
        $monitorService -notmatch 'monitorSessionStatusText' -or
        $monitorService -notmatch 'monitorRiskLevelText' -or
        $monitorService -notmatch 'latestActionNotificationSent' -or
        $monitorService -notmatch 'latestActionNotificationRead' -or
        $monitorService -notmatch 'withLatestActionNotificationHeaders' -or
        $monitorService -notmatch '"Latest Notification Read"' -or
        $monitorService -notmatch 'latestActionNotificationSentText') {
        Fail "Monitor session APIs must export scoped exam monitor audit sessions"
    }
    $examMonitorPanel = Get-Content -LiteralPath "frontend\src\components\ExamMonitorPanel.vue" -Raw
    $monitorApiForPanel = Get-Content -LiteralPath "frontend\src\api\monitor.ts" -Raw
    if ($examMonitorPanel -notmatch "Page unload attempt" -or
        $examMonitorPanel -notmatch "History back attempt" -or
        $examMonitorPanel -notmatch "Context menu" -or
        $examMonitorPanel -notmatch "PAGE_UNLOAD_ATTEMPT: '尝试离开页面'" -or
        $examMonitorPanel -notmatch "HISTORY_BACK_ATTEMPT: '尝试返回上一页'" -or
        $examMonitorPanel -notmatch "CONTEXT_MENU: '打开右键菜单'") {
        Fail "ExamMonitorPanel must filter and label navigation/context-menu monitor events"
    }
    if ($monitorApiForPanel -notmatch 'questionCount\?: number \| string \| null' -or
        $monitorApiForPanel -notmatch 'answeredCount\?: number \| string \| null' -or
        $monitorApiForPanel -notmatch 'unansweredCount\?: number \| string \| null' -or
        $examMonitorPanel -notmatch 'submitAnswerStatsText' -or
        $examMonitorPanel -notmatch 'Number\(row\.questionCount\)' -or
        $examMonitorPanel -notmatch 'Number\(row\.answeredCount\)' -or
        $examMonitorPanel -notmatch 'Number\(row\.unansweredCount\)' -or
        $examMonitorPanel -notmatch '\$\{answered\}/\$\{questionTotal\} answered, \$\{unanswered\} unanswered') {
        Fail "ExamMonitorPanel must display backend submit answer statistics"
    }
    if ($monitorApiForPanel -notmatch 'lastDraftSavedAt\?: string \| null' -or
        $monitorApiForPanel -notmatch 'draftRevision\?: number \| string \| null' -or
        $monitorApiForPanel -notmatch 'deadlineAt\?: string \| null' -or
        $monitorApiForPanel -notmatch 'remainingSeconds\?: number \| string \| null' -or
        $examMonitorPanel -notmatch 'Saved drafts' -or
        $examMonitorPanel -notmatch 'Time critical' -or
        $examMonitorPanel -notmatch 'Runtime' -or
        $examMonitorPanel -notmatch 'remainingTimeText' -or
        $examMonitorPanel -notmatch 'draftTelemetryText' -or
        $examMonitorPanel -notmatch 'hasSavedDraft' -or
        $examMonitorPanel -notmatch 'isTimeCritical' -or
        $examMonitorPanel -notmatch 'runtime-cell' -or
        $examMonitorPanel -notmatch 'grid-template-columns: repeat\(auto-fit, minmax\(120px, 1fr\)\)') {
        Fail "ExamMonitorPanel must display monitor attempt runtime telemetry"
    }
    if ($monitorController -notmatch 'sessions/\{sessionId\}/incident' -or
        $monitorController -notmatch 'getMonitorAttemptIncident' -or
        $monitorService -notmatch 'getMonitorAttemptIncident' -or
        $monitorService -notmatch 'loadIncidentDraft' -or
        $monitorService -notmatch 'loadIncidentAnswerStats' -or
        $monitorService -notmatch 'forceSubmitEvidence' -or
        $monitorService -notmatch 'draftAnswerCounters' -or
        $monitorApiForPanel -notmatch 'MonitorIncidentDetail' -or
        $monitorApiForPanel -notmatch 'getMonitorAttemptIncident' -or
        $examMonitorPanel -notmatch 'incidentDetail' -or
        $examMonitorPanel -notmatch 'incident-stat-grid' -or
        $examMonitorPanel -notmatch 'Force Submit') {
        Fail "ExamMonitorPanel must expose aggregated monitor attempt incident evidence"
    }
    if ($examMonitorPanel -notmatch 'Confirmed reminders' -or
        $examMonitorPanel -notmatch 'confirmedRulesReminders' -or
        $examMonitorPanel -notmatch 'isConfirmedRulesReminderFilterActive' -or
        $examMonitorPanel -notmatch 'applyConfirmedRulesReminderFilter' -or
        $examMonitorPanel -notmatch "sessionFilter\.rulesConfirmationStatus = 'CONFIRMED'" -or
        $examMonitorPanel -notmatch 'isConfirmedRulesReminder') {
        Fail "ExamMonitorPanel must expose a confirmed rules reminder quick filter"
    }
    if ($examMonitorPanel -notmatch 'rulesReminderUnavailableReason' -or
        $examMonitorPanel -notmatch 'action-state-alert' -or
        $examMonitorPanel -notmatch ':title="rulesReminderUnavailableReason\(scope\.row as MonitorSession\)' -or
        $examMonitorPanel -notmatch "actionForm\.actionType === 'RULES_REMINDER'" -or
        $examMonitorPanel -notmatch 'Rules already confirmed by the student\.' -or
        $examMonitorPanel -notmatch 'Rules reminders require an active attempt\.') {
        Fail "ExamMonitorPanel must explain and guard unavailable rules reminder actions"
    }
    if ($monitorController -notmatch 'cheat-events/\{attemptId\}/export' -or
        $monitorController -notmatch 'exportCheatEvents' -or
        $monitorController -notmatch 'String eventType' -or
        $monitorController -notmatch 'String startFrom' -or
        $monitorController -notmatch 'String startTo' -or
        $monitorController -notmatch 'Integer minRiskScore' -or
        $monitorService -notmatch 'exportCheatEvents' -or
        $monitorService -notmatch 'appendCheatEventFilters' -or
        $monitorService -notmatch 'event_time >= \?' -or
        $monitorService -notmatch 'risk_score >= \?' -or
        $monitorService -notmatch 'minRiskScore cannot be negative' -or
        $monitorService -notmatch 'parseMonitorFilterTime' -or
        $monitorService -notmatch 'clientEventId' -or
        $monitorService -notmatch 'teachingScopeService\.canAccessStudent' -or
        $monitorService -notmatch 'No permission to monitor this attempt') {
        Fail "Monitor event APIs must export scoped attempt monitor event details"
    }
    if ($monitorService -notmatch 'MONITOR_LATE_EVENT_GRACE_SECONDS' -or
        $monitorService -notmatch 'loadStudentMonitorAttempt' -or
        $monitorService -notmatch 'requireMonitorEventReportableAttempt' -or
        $monitorService -notmatch 'Submitted attempt monitor events require clientEventTime' -or
        $monitorService -notmatch 'requireMonitorEventAfterAttemptStart' -or
        $monitorService -notmatch 'Active attempt monitor event time is too far in the future' -or
        $monitorService -notmatch 'LocalDateTime\.now\(\)\.plusSeconds\(MONITOR_LATE_EVENT_GRACE_SECONDS\)' -or
        $monitorService -notmatch 'Monitor event time is outside the late reporting window' -or
        $monitorService -notmatch 'monitorStatusForAttemptEvent' -or
        $monitorService -notmatch 'exam_id = VALUES\(exam_id\)' -or
        $monitorService -notmatch 'user_id = VALUES\(user_id\)' -or
        $monitorService -notmatch 'CASE WHEN status = ''SUBMITTED'' THEN status ELSE VALUES\(status\) END') {
        Fail "MonitorService must accept bounded late monitor event uploads without downgrading submitted sessions"
    }
    if ($schemaSql -notmatch 'CREATE TABLE IF NOT EXISTS cheat_event' -or
        $schemaSql -notmatch 'exam_id\s+BIGINT\s+NOT NULL' -or
        $schemaSql -notmatch 'user_id\s+BIGINT\s+NOT NULL' -or
        $schemaSql -notmatch 'UNIQUE KEY uk_cheat_attempt_client_event \(attempt_id, client_event_id\)' -or
        $schemaSql -notmatch 'idx_cheat_exam_time \(exam_id, event_time\)' -or
        $schemaSql -notmatch 'idx_cheat_user_time \(user_id, event_time\)' -or
        $migrationRunner -notmatch 'ensureCheatEventOwnershipConsistency' -or
        $migrationRunner -notmatch 'deduplicateCheatEventsBeforeClientEventUniqueIndex' -or
        $migrationRunner -notmatch 'tmp_cheat_event_client_dedup' -or
        $migrationRunner -notmatch 'WHERE client_event_id IS NOT NULL[\s\S]*GROUP BY attempt_id, client_event_id[\s\S]*HAVING COUNT\(\*\) > 1' -or
        $migrationRunner -notmatch 'DELETE ce[\s\S]*FROM cheat_event ce[\s\S]*JOIN tmp_cheat_event_client_dedup d ON d\.duplicate_id = ce\.id' -or
        $migrationRunner -notmatch 'deduplicateCheatEventsBeforeClientEventUniqueIndex\(jdbc\);[\s\S]*ALTER TABLE cheat_event ADD UNIQUE KEY uk_cheat_attempt_client_event \(attempt_id, client_event_id\)' -or
        $migrationRunner -notmatch 'reconcile cheat event ownership' -or
        $migrationRunner -notmatch 'UPDATE cheat_event ce[\s\S]*JOIN exam_attempt a ON a\.id = ce\.attempt_id[\s\S]*SET ce\.exam_id = a\.exam_id,[\s\S]*ce\.user_id = a\.user_id' -or
        $monitorService -notmatch 'INSERT IGNORE INTO cheat_event \([\s\S]*attempt_id, exam_id, user_id, event_type' -or
        $monitorService -notmatch 'exam_id AS examId' -or
        $monitorService -notmatch 'user_id AS userId') {
        Fail "Monitor event records must persist exam/user ownership snapshots for audit and aggregation"
    }
    if ($schemaSql -notmatch 'risk_score\s+INT\s+NOT NULL DEFAULT 0' -or
        $schemaSql -notmatch 'idx_cheat_exam_risk_time \(exam_id, risk_score, event_time\)' -or
        $migrationRunner -notmatch 'ensureCheatEventRiskScoreConsistency' -or
        $migrationRunner -notmatch 'reconcile cheat event risk score' -or
        $migrationRunner -notmatch "WHEN 'PASTE' THEN 8[\s\S]*WHEN 'NETWORK_ONLINE' THEN 1" -or
        $monitorService -notmatch 'int riskScore = riskWeight\(eventType\)' -or
        $monitorService -notmatch 'INSERT IGNORE INTO cheat_event \([\s\S]*attempt_id, exam_id, user_id, event_type, risk_score' -or
        $monitorService -notmatch 'risk_score AS riskScore' -or
        $monitorService -notmatch '"Risk Score"' -or
        $monitorService -notmatch 'event\.get\("riskScore"\)') {
        Fail "Monitor event records must persist per-event risk scores and aggregate sessions from event scores"
    }
    if ($monitorService -notmatch '"PAGE_UNLOAD_ATTEMPT"' -or
        $monitorService -notmatch '"HISTORY_BACK_ATTEMPT"' -or
        $monitorService -notmatch '"CONTEXT_MENU"' -or
        $monitorService -notmatch 'case "FULLSCREEN_EXIT", "PAGE_UNLOAD_ATTEMPT" -> 5' -or
        $monitorService -notmatch 'case "NETWORK_OFFLINE", "HISTORY_BACK_ATTEMPT" -> 4' -or
        $monitorService -notmatch 'case "VISIBILITY_HIDDEN", "WINDOW_BLUR", "CONTEXT_MENU" -> 3' -or
        $migrationRunner -notmatch "WHEN 'PAGE_UNLOAD_ATTEMPT' THEN 5" -or
        $migrationRunner -notmatch "WHEN 'HISTORY_BACK_ATTEMPT' THEN 4" -or
        $migrationRunner -notmatch "WHEN 'CONTEXT_MENU' THEN 3") {
        Fail "MonitorService must accept, label, and score navigation/context-menu monitor events"
    }
    if ($monitorController -notmatch 'sessions/\{sessionId\}/actions/export' -or
        $monitorController -notmatch 'exportMonitorActions' -or
        $monitorService -notmatch 'exportMonitorActions' -or
        $monitorService -notmatch 'resolveMonitorActionOwnership' -or
        $monitorService -notmatch 'SELECT s\.attempt_id, a\.exam_id, a\.user_id' -or
        $monitorService -notmatch 'JOIN exam_attempt a ON a\.id = s\.attempt_id' -or
        $monitorService -notmatch 'WARN action requires an in-progress attempt' -or
        $monitorService -notmatch '"WARN"\.equals\(actionType\)[\s\S]*attemptStatus != 1' -or
        $monitorService -notmatch 'sendAndReturnId' -or
        $monitorService -notmatch 'updateMonitorActionNotification' -or
        $monitorService -notmatch 'ma\.notification_sent AS notificationSent' -or
        $monitorService -notmatch 'ma\.notification_id AS notificationId' -or
        $monitorService -notmatch 'n\.is_read AS notificationRead' -or
        $monitorService -notmatch 'n\.created_at AS notificationCreatedAt' -or
        $monitorService -notmatch 'LEFT JOIN notification n ON n\.id = ma\.notification_id' -or
        $monitorService -notmatch 'withMonitorNotificationHeader\(headers\)' -or
        $monitorService -notmatch '"Notification ID"' -or
        $monitorService -notmatch '"Notification Read"' -or
        $monitorService -notmatch 'monitorNotificationReadText' -or
        $monitorService -notmatch 'monitorNotificationSentText' -or
        $monitorService -notmatch 's\.attempt_id AS attemptId,[\s\S]*a\.exam_id AS examId,[\s\S]*a\.user_id AS userId' -or
        $monitorService -notmatch 'JOIN exam e ON e\.id = a\.exam_id' -or
        $monitorService -notmatch 'LEFT JOIN student_profile sp ON sp\.user_id = a\.user_id AND sp\.deleted = 0' -or
        $monitorService -notmatch 'WHERE a\.exam_id = \?' -or
        $monitorService -notmatch 'AND a\.user_id IN \(' -or
        $monitorService -notmatch 'SELECT s\.id,[\s\S]*s\.attempt_id,[\s\S]*a\.exam_id,[\s\S]*a\.user_id' -or
        $monitorService -notmatch 'JOIN exam e ON e\.id = a\.exam_id AND e\.deleted = 0' -or
        $monitorService -notmatch 'JOIN sys_user u ON u\.id = a\.user_id' -or
        $monitorService -notmatch 'LEFT JOIN exam_candidate_snapshot ecs ON ecs\.exam_id = a\.exam_id AND ecs\.user_id = a\.user_id' -or
        $monitorService -notmatch 'FROM exam_monitor_action ma' -or
        $monitorService -notmatch 'monitorActionTypeText' -or
        $monitorService -notmatch 'No permission to handle this monitor session') {
        Fail "Monitor action APIs must export scoped monitor action details"
    }
    if ($monitorController -notmatch 'score-release-logs' -or
        $monitorController -notmatch '@RequestParam\(required = false\) Long logId' -or
        $monitorController -notmatch 'getScoreReleaseAuditLogs' -or
        $monitorService -notmatch 'getScoreReleaseAuditLogs' -or
        $monitorService -notmatch 'FROM score_release_log l' -or
        $monitorService -notmatch 'AND l\.id = \?' -or
        $monitorService -notmatch 'e.exam_name LIKE' -or
        $monitorService -notmatch 'l.visible_attempt_count AS visibleAttemptCount') {
        Fail "Monitor audit APIs must expose global score release logs for administrators"
    }
    if ($monitorController -notmatch 'score-release-logs/export' -or
        $monitorController -notmatch 'exportScoreReleaseAuditLogs' -or
        $monitorService -notmatch 'exportScoreReleaseAuditLogs' -or
        $monitorService -notmatch 'appendScoreReleaseAuditFilters' -or
        $monitorService -notmatch 'scoreReleaseAuditActionText' -or
        $monitorService -notmatch 'notifiedAttemptCount') {
        Fail "Monitor audit APIs must export filtered global score release logs for administrators"
    }
    if ($monitorController -notmatch 'score-appeal-logs' -or
        $monitorController -notmatch 'getScoreAppealAuditLogs' -or
        $monitorService -notmatch 'getScoreAppealAuditLogs' -or
        $monitorService -notmatch 'FROM score_appeal_log l' -or
        $monitorService -notmatch 'l.handling_result = \?' -or
        $monitorService -notmatch 'RECHECK_OPEN' -or
        $monitorService -notmatch 'studentName') {
        Fail "Monitor audit APIs must expose global score appeal logs for administrators"
    }
    if ($monitorController -notmatch 'score-appeal-logs/export' -or
        $monitorController -notmatch 'exportScoreAppealAuditLogs' -or
        $monitorService -notmatch 'exportScoreAppealAuditLogs' -or
        $monitorService -notmatch 'CsvExport.build' -or
        $monitorService -notmatch 'LIMIT 5000' -or
        $monitorService -notmatch 'appendScoreAppealAuditFilters') {
        Fail "Monitor audit APIs must export filtered global score appeal logs for administrators"
    }
    if ($monitorController -notmatch 'review-score-logs' -or
        $monitorController -notmatch 'getReviewScoreAuditLogs' -or
        $monitorController -notmatch 'exportReviewScoreAuditLogs' -or
        $monitorService -notmatch 'getReviewScoreAuditLogs' -or
        $monitorService -notmatch 'exportReviewScoreAuditLogs' -or
        $monitorService -notmatch 'FROM review_score_log l' -or
        $monitorService -notmatch 'appendReviewScoreAuditFilters\(StringBuilder where, List<Object> params, Long logId' -or
        $monitorService -notmatch 'AND l\.id = \?' -or
        $monitorService -notmatch 'appendReviewScoreAuditFilters' -or
        $monitorService -notmatch 'reviewerId' -or
        $monitorService -notmatch 'LIMIT 5000') {
        Fail "Monitor audit APIs must expose and export global review score audit logs for administrators"
    }
    if ($monitorController -notmatch 'question-review-logs' -or
        $monitorController -notmatch 'getQuestionReviewAuditLogs' -or
        $monitorController -notmatch 'exportQuestionReviewAuditLogs' -or
        $monitorController -notmatch 'QuestionBankService' -or
        $questionBankService -notmatch 'listReviewAuditLogs' -or
        $questionBankService -notmatch 'exportReviewAuditLogs' -or
        $questionBankService -notmatch 'FROM question_review_log qrl' -or
        $questionBankService -notmatch 'appendReviewAuditFilters\(StringBuilder where, List<Object> params, Long logId' -or
        $questionBankService -notmatch 'AND qrl\.id = \?' -or
        $questionBankService -notmatch 'questionReviewAuditSelectSql' -or
        $questionBankService -notmatch 'LIMIT 5000' -or
        $questionBankService -notmatch 'CsvExport.build') {
        Fail "Monitor audit APIs must expose and export global question review audit logs for administrators"
    }
    if ([regex]::Matches($monitorService, 'LEFT JOIN exam_question_snapshot eqs ON eqs\.exam_id = e\.id AND eqs\.question_id = l\.question_id').Count -lt 6 -or
        [regex]::Matches($monitorService, 'COALESCE\(eqs\.stem, q\.stem\) AS questionStem').Count -lt 4 -or
        [regex]::Matches($monitorService, 'COALESCE\(eqs\.stem, q\.stem\) LIKE CONCAT').Count -lt 2) {
        Fail "Monitor appeal and review score audit question labels/search must prefer exam snapshots"
    }

    $notificationApi = Get-Content -LiteralPath "frontend\src\api\notification.ts" -Raw
    if ($notificationApi -notmatch 'relatedType\?' -or $notificationApi -notmatch 'relatedId\?') {
        Fail "frontend notification API type must expose relatedType and relatedId"
    }

    $notificationController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\NotificationController.java" -Raw
    if ($notificationController -notmatch 'relatedType' -or $notificationController -notmatch 'relatedId') {
        Fail "NotificationController.myNotifications must accept relatedType and relatedId filters"
    }
    $notificationService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\NotificationService.java" -Raw
    if ($notificationService -notmatch 'buildMyNotificationWhere' -or $notificationService -notmatch 'related_type = \?') {
        Fail "NotificationService.myNotifications must filter by related_type and related_id"
    }
    if ($notificationService -notmatch 'sendAndReturnId' -or
        $notificationService -notmatch 'sendOnceAndReturnId' -or
        $notificationService -notmatch 'sendBatch\(List<Long> userIds, String title, String content, String type, String link,\s*[\r\n]+\s*String relatedType, Long relatedId\)' -or
        $notificationService -notmatch 'insertNotification' -or
        $notificationService -notmatch 'notificationDedupKey' -or
        $notificationService -notmatch 'dedup_key' -or
        $notificationService -notmatch 'ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID\(id\)' -or
        $notificationService -notmatch 'GeneratedKeyHolder' -or
        $notificationService -notmatch 'Statement\.RETURN_GENERATED_KEYS') {
        Fail "NotificationService must expose generated notification ids and idempotent related notification sends"
    }
    $migrationRunner = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\config\DatabaseMigrationRunner.java" -Raw
    $schemaSql = Get-Content -LiteralPath "backend\src\main\resources\db\schema.sql" -Raw
    if ($migrationRunner -notmatch 'ADD COLUMN dedup_key VARCHAR\(255\)' -or
        $migrationRunner -notmatch 'uk_notification_dedup_key' -or
        $schemaSql -notmatch 'dedup_key VARCHAR\(255\) DEFAULT NULL' -or
        $schemaSql -notmatch 'UNIQUE KEY uk_notification_dedup_key \(dedup_key\)') {
        Fail "notification schema and migrations must support nullable dedup keys for idempotent related sends"
    }
    if ($notificationController -notmatch 'notifications"\)' -or
        $notificationController -notmatch 'audit/export' -or
        $notificationController -notmatch 'exportNotificationAudit' -or
        $notificationController -notmatch 'Long notificationId' -or
        $notificationController -notmatch 'Boolean read' -or
        $notificationService -notmatch 'auditNotifications' -or
        $notificationService -notmatch 'exportNotificationAudit' -or
        $notificationService -notmatch 'appendNotificationAuditFilters' -or
        $notificationService -notmatch 'n\.id = \?' -or
        $notificationService -notmatch 'FROM notification n' -or
        $notificationService -notmatch 'LIMIT 5000') {
        Fail "Notification audit APIs must support administrator search and export"
    }
    $examTaking = Get-Content -LiteralPath "frontend\src\components\ExamTaking.vue" -Raw
    if ($examTaking -notmatch 'currentAttemptNotificationQuery' -or $examTaking -notmatch "relatedType: 'EXAM_ATTEMPT'") {
        Fail "ExamTaking monitor notification polling must request notifications related to the current attempt"
    }
    if ($examTaking -notmatch 'MONITOR_RULES_REMINDER' -or
        $examTaking -notmatch 'confirmRulesFromMonitorNotice' -or
        $examTaking -notmatch 'handleExistingUnreadRulesReminder' -or
        $examTaking -notmatch 'isUnreadNotification' -or
        $examTaking -notmatch '\.filter\(isCurrentExamNotification\)' -or
        $examTaking -notmatch 'acknowledgeAlreadyConfirmedRulesReminder' -or
        $examTaking -notmatch 'rulesReminderConfirmOpen' -or
        $examTaking -notmatch 'markRead' -or
        $examTaking -notmatch 'markMonitorNoticeRead' -or
        $examTaking -notmatch 'readRulesConfirmation\(props\.attemptId\)' -or
        $examTaking -notmatch 'startExam\(props\.attemptId, \{ rulesConfirmed: true \}\)' -or
        $examTaking -notmatch 'persistRulesConfirmation\(props\.attemptId\)' -or
        $examTaking -notmatch 'await markRead\(notice\.id\)' -or
        $examTaking -notmatch 'rulesReminderNoticeTitle' -or
        $examTaking -notmatch "monitorNotice\.value\?\.type === 'MONITOR_RULES_REMINDER'" -or
        $examTaking -notmatch 'Please confirm the exam rules before continuing\.') {
        Fail "ExamTaking must surface MONITOR_RULES_REMINDER and persist server-side rules confirmation"
    }
    $examApi = Get-Content -LiteralPath "frontend\src\api\exam.ts" -Raw
    $monitorApi = Get-Content -LiteralPath "frontend\src\api\monitor.ts" -Raw
    $examList = Get-Content -LiteralPath "frontend\src\components\ExamList.vue" -Raw
    $rulesConfirmationStorage = Get-Content -LiteralPath "frontend\src\utils\rulesConfirmationStorage.ts" -Raw
    $examOperationMetricsAspect = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\aspect\ExamOperationMetricsAspect.java" -Raw
    $examBusinessMetricsDoc = Get-Content -LiteralPath "docs\rebuild\43-exam-business-metrics.md" -Raw
    if ($examTaking -notmatch 'heartbeatSubmitMessage' -or
        $examTaking -notmatch 'forcedSubmitted' -or
        $examTaking -notmatch "submitType === 'FORCED'" -or
        $examApi -notmatch 'forcedSubmitted\?: boolean' -or
        $examApi -notmatch "submitType\?: 'MANUAL' \| 'TIMEOUT' \| 'FORCED'") {
        Fail "ExamTaking heartbeat handling must distinguish forced submit from timeout auto submit"
    }
    if ($examTaking -notmatch 'if \(data\.submitted \|\| data\.autoSubmitted \|\| data\.status >= 2\) \{[\s\S]*clearLocalDraft\(\);[\s\S]*clearSubmitToken\(\);[\s\S]*clearSubmitRetryTimer\(\);') {
        Fail "ExamTaking heartbeat finalization must clear local draft, submit token, and pending submit retry"
    }
    if ($examTaking -notmatch 'submitInFlight' -or
        $examTaking -notmatch 'submitRetryTimer' -or
        $examTaking -notmatch 'scheduleSubmitRetry' -or
        $examTaking -notmatch 'autoSubmit \|\| timeLeft\.value <= 0' -or
        $examTaking -notmatch 'clearSubmitRetryTimer') {
        Fail "ExamTaking must retry failed timeout auto-submit and prevent duplicate in-flight submits"
    }
    if ($examTaking -notmatch 'const unansweredCount = computed' -or
        $examTaking -notmatch 'questionCount\.value - answeredCount\.value' -or
        $examTaking -notmatch 'There are \$\{unansweredCount\.value\} unanswered questions\. Submit anyway\?' -or
        $examTaking -notmatch "ElMessageBox\.confirm\(message, 'Submit exam'") {
        Fail "ExamTaking manual submit confirmation must warn about unanswered questions"
    }
    if ($examTaking -notmatch 'submitResultSummary\(response\.data\)' -or
        $examTaking -notmatch 'Server accepted answers: \$\{answered\}/\$\{questionTotal\} answered, \$\{unanswered\} unanswered\.' -or
        $examTaking -notmatch 'Number\(data\.answeredCount\)' -or
        $examTaking -notmatch 'Number\(data\.questionCount\)' -or
        $examTaking -notmatch 'Number\(data\.unansweredCount\)') {
        Fail "ExamTaking must surface backend submit answer statistics after submission"
    }
    if ($examTaking -notmatch 'submitReplayWarning\(response\.data\)' -or
        $examTaking -notmatch 'function submitReplayWarning\(data: \{ submitPayloadMismatch\?: boolean; submitTokenMismatch\?: boolean \}\)' -or
        $examTaking -notmatch 'data\.submitPayloadMismatch' -or
        $examTaking -notmatch 'Server kept the first submitted answers; this retry sent different answers\.' -or
        $examTaking -notmatch 'data\.submitTokenMismatch' -or
        $examTaking -notmatch 'Server kept the first submitted answers; this retry used a different submit token\.') {
        Fail "ExamTaking must surface submit replay token and payload mismatch warnings"
    }
    if ($examOperationMetricsAspect -notmatch 'private String submitOutcome\(Map<\?, \?> map\)' -or
        $examOperationMetricsAspect -notmatch 'submitPayloadMismatch' -or
        $examOperationMetricsAspect -notmatch 'return "replay_payload_mismatch";' -or
        $examOperationMetricsAspect -notmatch 'submitTokenMismatch' -or
        $examOperationMetricsAspect -notmatch 'return "replay_token_mismatch";' -or
        $examOperationMetricsAspect -notmatch 'if \(bool\(map\.get\("submitPayloadMismatch"\)\)\) \{[\s\S]*return "replay_payload_mismatch";[\s\S]*if \(bool\(map\.get\("submitTokenMismatch"\)\)\) \{[\s\S]*return "replay_token_mismatch";[\s\S]*if \(bool\(map\.get\("responseReplayed"\)\)\)' -or
        $examBusinessMetricsDoc -notmatch 'replay_payload_mismatch' -or
        $examBusinessMetricsDoc -notmatch 'replay_token_mismatch') {
        Fail "Exam operation metrics must classify submit replay payload and token mismatches before generic replay outcomes"
    }
    if ($examTaking -notmatch "reportExamEvent\('PAGE_UNLOAD_ATTEMPT'\)" -or
        $examTaking -notmatch "reportExamEvent\('HISTORY_BACK_ATTEMPT'\)" -or
        $examTaking -notmatch "reportExamEvent\('CONTEXT_MENU'\)" -or
        $examTaking -notmatch "window\.addEventListener\('contextmenu', handleContextMenu\)" -or
        $examTaking -notmatch "window\.removeEventListener\('contextmenu', handleContextMenu\)") {
        Fail "ExamTaking must record page-unload, back-navigation, and context-menu monitor events"
    }
    if ($examApi -notmatch 'submitted\?: boolean' -or
        $examApi -notmatch 'alreadySubmitted\?: boolean' -or
        $examTaking -notmatch 'response\.data\.submitted' -or
        $examTaking -notmatch 'response\.data\.alreadySubmitted' -or
        $examTaking -notmatch 'Number\(response\.data\.status \|\| 0\) >= 2' -or
        $examTaking -notmatch 'clearSubmitToken\(\);') {
        Fail "ExamTaking must treat submitted startExam responses as successful submission cleanup"
    }
    if ($examApi -notmatch 'StartExamPayload' -or
        $examApi -notmatch 'rulesConfirmed\?: boolean' -or
        $examApi -notmatch 'postJson<ExamDetail, StartExamPayload>' -or
        $examTaking -notmatch '../utils/rulesConfirmationStorage' -or
        $examTaking -notmatch 'readRulesConfirmation' -or
        $examTaking -notmatch 'rulesConfirmed: readRulesConfirmation\(props\.attemptId\)' -or
        $rulesConfirmationStorage -notmatch 'smart_exam_rules_confirmed_' -or
        $rulesConfirmationStorage -notmatch 'readRulesConfirmation' -or
        $rulesConfirmationStorage -notmatch 'persistRulesConfirmation' -or
        $rulesConfirmationStorage -notmatch 'syncRulesConfirmationFromServer' -or
        $rulesConfirmationStorage -notmatch 'sessionStorage\.getItem' -or
        $rulesConfirmationStorage -notmatch 'localStorage\.getItem' -or
        $rulesConfirmationStorage -notmatch 'sessionStorage\.setItem' -or
        $rulesConfirmationStorage -notmatch 'localStorage\.setItem') {
        Fail "ExamTaking must pass confirmed exam rules to the start endpoint"
    }
    if ($examTaking -notmatch 'syncRulesConfirmationFromServer\(props\.attemptId, response\.data\.rulesConfirmedAt\)' -or
        $examList -notmatch 'syncRulesConfirmationFromServer\(exam\.attemptId, exam\.rulesConfirmedAt\)') {
        Fail "Student rule confirmation cache must sync from server rulesConfirmedAt"
    }
    if ($examTaking -notmatch 'const monitorFlushed = await flushMonitorEvents\(true\)' -or
        $examTaking -notmatch 'retryLateMonitorEventsAfterSubmit' -or
        $examTaking -notmatch 'const lateMonitorFlushed = monitorFlushed \|\| await retryLateMonitorEventsAfterSubmit\(\)' -or
        $examTaking -notmatch 'if \(lateMonitorFlushed\)' -or
        $examTaking -notmatch 'do \{' -or
        $examTaking -notmatch 'monitorEventQueue\.slice\(0, 100\)' -or
        $examTaking -notmatch 'isolateMonitorUploadFailures' -or
        $examTaking -notmatch 'isPermanentMonitorUploadReject' -or
        $examTaking -notmatch 'error instanceof ApiError && error\.status === 400' -or
        $examTaking -notmatch 'recordCheatEventsBatch\(\[remaining\[index\]\]\)' -or
        $examTaking -notmatch 'monitorEventQueue\.splice\(0, batch\.length, \.\.\.isolatedRemaining\)' -or
        $examTaking -notmatch 'while \(force && monitorEventQueue\.length > 0\)' -or
        $examTaking -notmatch 'return false;' -or
        $examTaking -notmatch 'return monitorEventQueue\.length === 0;') {
        Fail "ExamTaking must drain forced monitor uploads and preserve unflushed events instead of clearing them after submit"
    }
    if ($examTaking -notmatch 'draftRevision\?: number' -or
        $examTaking -notmatch 'localDraftIsOlderThanServer' -or
        $examTaking -notmatch 'clearLocalDraft\(\);[\s\S]*return;' -or
        $examTaking -notmatch 'function writeLocalDraft\(revision = draftRevision\.value\)' -or
        $examTaking -notmatch 'draftRevision: revision' -or
        $examTaking -notmatch 'localRevision < draftRevision\.value' -or
        $examTaking -notmatch 'serverSavedAt') {
        Fail "ExamTaking must not let stale local drafts overwrite newer server drafts"
    }
    if (-not $examTaking.Contains('draftConflictLocked')) {
        Fail "ExamTaking must lock draft saving after server stale conflict instead of retrying over newer drafts"
    }
    if (-not $examTaking.Contains('if (draftConflictLocked.value)')) {
        Fail "ExamTaking must lock draft saving after server stale conflict instead of retrying over newer drafts"
    }
    if (-not $examTaking.Contains('draftConflictLocked.value = true')) {
        Fail "ExamTaking must lock draft saving after server stale conflict instead of retrying over newer drafts"
    }
    if (-not $examTaking.Contains('clearRetryTimer();')) {
        Fail "ExamTaking must lock draft saving after server stale conflict instead of retrying over newer drafts"
    }
    if (-not $examTaking.Contains('writeLocalDraft();')) {
        Fail "ExamTaking must lock draft saving after server stale conflict instead of retrying over newer drafts"
    }
    if (-not $examTaking.Contains('writeLocalDraft(response.data.revision || nextRevision)') -or
        -not $examTaking.Contains('function writeLocalDraft(revision = draftRevision.value)') -or
        -not $examTaking.Contains('draftRevision: revision')) {
        Fail "ExamTaking stale conflict local draft must keep the rejected revision so restart cannot restore it over newer server drafts"
    }
    if ($examTaking -notmatch 'recovery-panel' -or
        $examTaking -notmatch 'retryRecoverySync' -or
        $examTaking -notmatch 'restoreLocalDraftFromPanel' -or
        $examTaking -notmatch 'networkOnline' -or
        $examTaking -notmatch 'heartbeatError' -or
        $examTaking -notmatch 'lastHeartbeatAt' -or
        $examTaking -notmatch 'serverDraftSavedAt' -or
        $examTaking -notmatch 'localDraftSavedAt' -or
        $examTaking -notmatch 'draftRetryPending' -or
        $examTaking -notmatch 'submitRetryPending' -or
        $examTaking -notmatch 'monitorQueueSize' -or
        $examTaking -notmatch 'updateMonitorQueueSize' -or
        $examTaking -notmatch 'formatRecoveryDate' -or
        $examApi -notmatch 'examAttemptHeartbeat[\s\S]*lastHeartbeatAt\?: string \| null' -or
        $examApi -notmatch 'examAttemptHeartbeat[\s\S]*draftSavedCount\?: number') {
        Fail "ExamTaking must expose student exam recovery status and manual recovery actions"
    }
    if ($monitorApi -notmatch 'flushStoredMonitorQueues' -or
        $monitorApi -notmatch 'MONITOR_QUEUE_STORAGE_PREFIX' -or
        $monitorApi -notmatch 'STORED_MONITOR_EVENT_MAX_AGE_MS' -or
        $monitorApi -notmatch 'storedMonitorQueueKeys' -or
        $monitorApi -notmatch 'readStoredMonitorQueue' -or
        $monitorApi -notmatch 'writeStoredMonitorQueue' -or
        $monitorApi -notmatch 'isRecentStoredMonitorEvent' -or
        $monitorApi -notmatch 'Date\.parse\(clientEventTime\)' -or
        $monitorApi -notmatch 'isolateStoredMonitorQueueFailures' -or
        $monitorApi -notmatch 'isPermanentStoredMonitorUploadReject' -or
        $monitorApi -notmatch 'error instanceof ApiError && error\.status === 400' -or
        $monitorApi -notmatch 'recordCheatEventsBatch\(\[remaining\[index\]\]\)' -or
        $monitorApi -notmatch 'recordCheatEventsBatch\(batch\)' -or
        $monitorApi -notmatch 'while \(remaining\.length > 0\)' -or
        $examList -notmatch 'flushStoredMonitorQueues' -or
        $examList -notmatch 'flushPendingMonitorQueues' -or
        $examList -notmatch 'monitorFlushInFlight' -or
        $examList -notmatch 'void flushPendingMonitorQueues\(\)' -or
        $examList -notmatch 'must not block exam center loading') {
        Fail "ExamList must opportunistically flush locally stored monitor event queues without blocking list loading"
    }
    if ($examApi -match 'submitExam[\s\S]*score:\s*number' -or $examApi -match 'forceSubmitAttempt[\s\S]*score:\s*number') {
        Fail "frontend exam submit API types must not expose a score field before release"
    }
    if ($examApi -notmatch 'submitTime\?: string \| null' -or
        $examApi -notmatch 'submitPayloadMismatch\?: boolean' -or
        $examApi -notmatch 'forceSubmitAttempt[\s\S]*submitTime\?: string \| null' -or
        $examApi -notmatch 'examAttemptHeartbeat[\s\S]*submitTime\?: string \| null') {
        Fail "frontend exam submit, heartbeat, and force-submit API types must expose submitTime and submit replay mismatch flags"
    }
    if ($examService -notmatch 'appendForcedSubmitFlags' -or
        $examService -notmatch 'result\.put\("forcedSubmitted", true\)' -or
        $examService -notmatch 'result\.put\("submitted", true\)' -or
        $examApi -notmatch 'forceSubmitAttempt[\s\S]*forcedSubmitted\?: boolean' -or
        $examApi -notmatch 'forceSubmitAttempt[\s\S]*questionCount\?: number') {
        Fail "teacher force-submit responses must expose forced submission flags and answer statistics"
    }
    if ($examApi -notmatch 'ScoreReleaseLog' -or
        $examApi -notmatch 'getScoreReleaseLogs' -or
        $examApi -notmatch 'exportScoreReleaseLogs' -or
        $examApi -notmatch 'score-release-logs/export') {
        Fail "frontend exam API must expose score release audit logs"
    }
    $adminApi = Get-Content -LiteralPath "frontend\src\api\admin.ts" -Raw
    $aiApi = Get-Content -LiteralPath "frontend\src\api\ai.ts" -Raw
    $basicApi = Get-Content -LiteralPath "frontend\src\api\basic.ts" -Raw
    $paperApi = Get-Content -LiteralPath "frontend\src\api\paper.ts" -Raw
    $questionApi = Get-Content -LiteralPath "frontend\src\api\question.ts" -Raw
    $systemLog = Get-Content -LiteralPath "frontend\src\components\SystemLog.vue" -Raw
    $userManagement = Get-Content -LiteralPath "frontend\src\components\UserManagement.vue" -Raw
    $roleManagement = Get-Content -LiteralPath "frontend\src\components\RoleManagement.vue" -Raw
    $basicDataPanel = Get-Content -LiteralPath "frontend\src\components\BasicDataPanel.vue" -Raw
    $paperPanel = Get-Content -LiteralPath "frontend\src\components\PaperPanel.vue" -Raw
    $questionBankPanel = Get-Content -LiteralPath "frontend\src\components\QuestionBankPanel.vue" -Raw
    $clipboardUtils = Get-Content -LiteralPath "frontend\src\utils\clipboard.ts" -Raw
    if ($adminApi -notmatch 'OperationLogQuery' -or
        $adminApi -notmatch 'logId\?: number \| string' -or
        $adminApi -notmatch "params\.set\('logId', String\(query\.logId\)\)" -or
        $adminApi -notmatch 'exportOperationLogs' -or
        $adminApi -notmatch 'logs/export' -or
        $systemLog -notmatch 'Operation log ID' -or
        $systemLog -notmatch 'operationLogId' -or
        $systemLog -notmatch 'normalizedOperationLogId' -or
        $systemLog -notmatch 'operation-log-id-cell' -or
        $systemLog -notmatch 'Copy operation log ID' -or
        $systemLog -notmatch 'Copy operation log link' -or
        $systemLog -notmatch 'copyOperationLogId' -or
        $systemLog -notmatch 'copyOperationLogLink' -or
        $clipboardUtils -notmatch 'copyOperationLogIdToClipboard' -or
        $clipboardUtils -notmatch 'copyOperationLogLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildOperationLogDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?operationLogId=' -or
        $systemLog -notmatch 'operationQuery' -or
        $systemLog -notmatch 'searchOperationLogs' -or
        $systemLog -notmatch 'exportOperationLogRows') {
        Fail "SystemLog must expose administrator operation log exact lookup, copyable links, search, and export"
    }
    if ($adminApi -notmatch 'UserOperationResult' -or
        $adminApi -notmatch 'operationLogId\?: number \| null' -or
        $adminApi -notmatch 'putJson<UserOperationResult>' -or
        $adminApi -notmatch 'deleteJson<UserOperationResult>' -or
        $adminApi -notmatch 'postJson<SystemUser>' -or
        $userManagement -notmatch 'lastOperationAudit' -or
        $userManagement -notmatch 'rememberOperationAudit' -or
        $userManagement -notmatch 'operationAuditText' -or
        $userManagement -notmatch 'copyLatestOperationAuditId' -or
        $userManagement -notmatch 'copyLatestOperationAuditLink' -or
        $userManagement -notmatch 'copyOperationLogIdToClipboard' -or
        $userManagement -notmatch 'copyOperationLogLinkToClipboard' -or
        $userManagement -notmatch 'operationLogId' -or
        $userManagement -notmatch 'user-operation-audit') {
        Fail "UserManagement must surface and copy operation audit evidence ids after user operations"
    }
    if ($adminApi -notmatch 'RolePageUpdateResult' -or
        $adminApi -notmatch 'putJson<RolePageUpdateResult>' -or
        $roleManagement -notmatch 'role-operation-audit' -or
        $roleManagement -notmatch 'lastOperationAudit' -or
        $roleManagement -notmatch 'rememberOperationAudit' -or
        $roleManagement -notmatch 'response\.data\.operationLogId' -or
        $roleManagement -notmatch 'copyLatestOperationAuditId' -or
        $roleManagement -notmatch 'copyLatestOperationAuditLink' -or
        $roleManagement -notmatch 'copyOperationLogIdToClipboard' -or
        $roleManagement -notmatch 'copyOperationLogLinkToClipboard') {
        Fail "RoleManagement must surface and copy operation audit evidence ids after role permission updates"
    }
    if ($basicApi -notmatch 'operationLogId\?: number \| null' -or
        $basicDataPanel -notmatch 'lastBasicOperationAudit' -or
        $basicDataPanel -notmatch 'rememberBasicOperationAudit' -or
        $basicDataPanel -notmatch 'basic-operation-audit' -or
        $basicDataPanel -notmatch 'copyLatestBasicOperationAuditId' -or
        $basicDataPanel -notmatch 'copyLatestBasicOperationAuditLink' -or
        $basicDataPanel -notmatch 'copyOperationLogIdToClipboard' -or
        $basicDataPanel -notmatch 'copyOperationLogLinkToClipboard' -or
        $basicDataPanel -notmatch 'response\.data\.operationLogId' -or
        $basicDataPanel -notmatch 'Create class' -or
        $basicDataPanel -notmatch 'Update subject' -or
        $basicDataPanel -notmatch 'Delete notice') {
        Fail "BasicDataPanel must surface and copy operation audit evidence ids after basic data mutations"
    }
    if ($basicApi -notmatch 'noticeId\?: number \| null' -or
        $basicDataPanel -notmatch 'useRoute' -or
        $basicDataPanel -notmatch 'route\.query\.noticeId' -or
        $basicDataPanel -notmatch 'focusedNoticeId' -or
        $basicDataPanel -notmatch 'routeNoticeId' -or
        $basicDataPanel -notmatch 'noticeId: focusedNoticeId\.value \|\| undefined' -or
        $basicDataPanel -notmatch 'noticeRowClassName' -or
        $basicDataPanel -notmatch 'notice-row-focused') {
        Fail "BasicDataPanel must deep-link NOTICE notifications to exact notice rows"
    }
    if ($paperApi -notmatch 'operationLogId\?: number \| null' -or
        $paperPanel -notmatch 'lastPaperOperationAudit' -or
        $paperPanel -notmatch 'rememberPaperOperationAudit' -or
        $paperPanel -notmatch 'paper-operation-audit' -or
        $paperPanel -notmatch 'copyLatestPaperOperationAuditId' -or
        $paperPanel -notmatch 'copyLatestPaperOperationAuditLink' -or
        $paperPanel -notmatch 'copyOperationLogIdToClipboard' -or
        $paperPanel -notmatch 'copyOperationLogLinkToClipboard' -or
        $paperPanel -notmatch 'response\.data\.operationLogId' -or
        $paperPanel -notmatch 'responses\.map\(\(response\) => response\.data\.operationLogId\)' -or
        $paperPanel -notmatch 'Generate paper' -or
        $paperPanel -notmatch 'Batch delete papers') {
        Fail "PaperPanel must surface and copy operation audit evidence ids after paper mutations"
    }
    if ($adminApi -notmatch 'LoginAuditLog' -or
        $adminApi -notmatch 'LoginAuditQuery' -or
        $adminApi -notmatch 'listLoginAuditLogs' -or
        $adminApi -notmatch 'exportLoginAuditLogs' -or
        $adminApi -notmatch 'login-logs/export' -or
        $systemLog -notmatch 'Login Audit' -or
        $systemLog -notmatch 'loginLogs' -or
        $systemLog -notmatch 'loginLogId' -or
        $systemLog -notmatch 'normalizedLoginLogId' -or
        $systemLog -notmatch 'searchLoginLogs' -or
        $systemLog -notmatch 'exportLoginLogs' -or
        $systemLog -notmatch 'login-log-id-cell' -or
        $systemLog -notmatch 'Copy login audit ID' -or
        $systemLog -notmatch 'Copy login audit link' -or
        $systemLog -notmatch 'copyLoginAuditId' -or
        $systemLog -notmatch 'copyLoginAuditLink' -or
        $clipboardUtils -notmatch 'copyLoginAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copyLoginAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildLoginAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?loginLogId=') {
        Fail "SystemLog must expose administrator login audit history with copyable unified deep links"
    }
    if ($adminApi -notmatch 'exportAiUsageLogs' -or
        $adminApi -notmatch 'ai-logs/export' -or
        $adminApi -notmatch 'startFrom\?: string' -or
        $systemLog -notmatch 'aiQuery.keyword' -or
        $systemLog -notmatch 'aiDateRange' -or
        $systemLog -notmatch 'exportAiLogs') {
        Fail "SystemLog must expose AI usage log search and export"
    }
    if ($systemLog -notmatch 'hydrateNotificationRelationQuery' -or
        $systemLog -notmatch "hasRouteQueryKey\('relatedType'\)" -or
        $systemLog -notmatch "hasRouteQueryKey\('relatedId'\)" -or
        $systemLog -notmatch 'notificationQuery\.relatedType = relatedType' -or
        $systemLog -notmatch 'notificationQuery\.relatedId = relatedId') {
        Fail "SystemLog notification audit route hydration must support relatedType and relatedId filters"
    }
    if ($clipboardUtils -notmatch 'copyNotificationRelatedAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildNotificationRelatedAuditDeepLink' -or
        $clipboardUtils -notmatch 'relatedType=.*relatedId=' -or
        $systemLog -notmatch 'copyNotificationRelatedAuditLink' -or
        $systemLog -notmatch 'notification-related-cell') {
        Fail "SystemLog notification audit related rows must expose copyable related filter links"
    }
    if ($adminApi -notmatch 'ScoreReleaseAuditLog' -or
        $adminApi -notmatch 'export interface ScoreReleaseAuditQuery \{[\s\S]*?logId\?: number \| string' -or
        $adminApi -notmatch 'listScoreReleaseAuditLogs' -or
        $adminApi -notmatch 'exportScoreReleaseAuditLogs' -or
        $adminApi -notmatch 'score-release-logs/export' -or
        $systemLog -notmatch 'scoreReleaseLogs' -or
        $systemLog -notmatch 'scoreReleaseLogId' -or
        $systemLog -notmatch 'normalizedScoreReleaseLogId' -or
        $systemLog -notmatch '成绩发布审计' -or
        $systemLog -notmatch 'exportScoreReleaseLogs' -or
        $systemLog -notmatch 'searchScoreReleaseLogs') {
        Fail "SystemLog must expose administrator score release audit history"
    }
    if ($clipboardUtils -notmatch 'copyScoreReleaseAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copyScoreReleaseAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildScoreReleaseAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?scoreReleaseLogId=' -or
        $systemLog -notmatch 'score-release-id-cell' -or
        $systemLog -notmatch 'Copy score release audit ID' -or
        $systemLog -notmatch 'Copy score release audit link' -or
        $systemLog -notmatch 'copyScoreReleaseAuditId' -or
        $systemLog -notmatch 'copyScoreReleaseAuditLink' -or
        $systemLog -notmatch 'copyScoreReleaseAuditLinkToClipboard') {
        Fail "SystemLog score release audit rows must expose copyable ID and deep link"
    }
    if ($adminApi -notmatch 'ExamApprovalAuditLog' -or
        $adminApi -notmatch 'export interface ExamApprovalAuditQuery \{[\s\S]*?logId\?: number \| string' -or
        $adminApi -notmatch 'listExamApprovalAuditLogs' -or
        $adminApi -notmatch 'exportExamApprovalAuditLogs' -or
        $adminApi -notmatch 'exam-approval-logs/export' -or
        $monitorController -notmatch 'exam-approval-logs' -or
        $monitorController -notmatch 'getExamApprovalAuditLogs' -or
        $monitorController -notmatch 'exportExamApprovalAuditLogs' -or
        $monitorService -notmatch 'getExamApprovalAuditLogs' -or
        $monitorService -notmatch 'exportExamApprovalAuditLogs' -or
        $monitorService -notmatch 'appendExamApprovalAuditFilters\(StringBuilder where, List<Object> params, Long logId' -or
        $monitorService -notmatch 'FROM exam_approval_log l' -or
        $systemLog -notmatch 'Exam Approval Audit' -or
        $systemLog -notmatch 'examApprovalLogs' -or
        $systemLog -notmatch 'examApprovalLogId' -or
        $systemLog -notmatch 'normalizedExamApprovalLogId' -or
        $systemLog -notmatch 'searchExamApprovalLogs' -or
        $systemLog -notmatch 'exportExamApprovalLogs') {
        Fail "SystemLog must expose administrator exam approval audit search and export"
    }
    if ($clipboardUtils -notmatch 'copyExamApprovalAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copyExamApprovalAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildExamApprovalAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?examApprovalLogId=' -or
        $systemLog -notmatch 'exam-approval-id-cell' -or
        $systemLog -notmatch 'Copy exam approval audit ID' -or
        $systemLog -notmatch 'Copy exam approval audit link' -or
        $systemLog -notmatch 'copyExamApprovalAuditId' -or
        $systemLog -notmatch 'copyExamApprovalAuditLink' -or
        $systemLog -notmatch 'copyExamApprovalAuditLinkToClipboard') {
        Fail "SystemLog exam approval audit rows must expose copyable ID and deep link"
    }
    if ($adminApi -notmatch 'ApprovalReminderAuditLog' -or
        $adminApi -notmatch 'export interface ApprovalReminderAuditQuery \{[\s\S]*?logId\?: number \| string' -or
        $adminApi -notmatch 'listApprovalReminderAuditLogs' -or
        $adminApi -notmatch 'exportApprovalReminderAuditLogs' -or
        $adminApi -notmatch 'approval-reminder-logs/export' -or
        $monitorController -notmatch 'approval-reminder-logs' -or
        $monitorController -notmatch 'getApprovalReminderAuditLogs' -or
        $monitorController -notmatch 'exportApprovalReminderAuditLogs' -or
        $monitorService -notmatch 'getApprovalReminderAuditLogs' -or
        $monitorService -notmatch 'exportApprovalReminderAuditLogs' -or
        $monitorService -notmatch 'appendApprovalReminderAuditFilters\(StringBuilder where, List<Object> params, Long logId' -or
        $monitorService -notmatch 'FROM exam_approval_reminder_log l' -or
        $systemLog -notmatch 'Approval Reminder Audit' -or
        $systemLog -notmatch 'approvalReminderLogs' -or
        $systemLog -notmatch 'approvalReminderLogId' -or
        $systemLog -notmatch 'normalizedApprovalReminderLogId' -or
        $systemLog -notmatch 'searchApprovalReminderLogs' -or
        $systemLog -notmatch 'exportApprovalReminderLogs') {
        Fail "SystemLog must expose administrator approval reminder audit search and export"
    }
    if ($clipboardUtils -notmatch 'copyApprovalReminderAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildApprovalReminderAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?approvalReminderLogId=' -or
        $systemLog -notmatch 'approval-reminder-id-cell' -or
        $systemLog -notmatch 'Copy approval reminder audit ID' -or
        $systemLog -notmatch 'Copy approval reminder audit link' -or
        $systemLog -notmatch 'copyApprovalReminderAuditId' -or
        $systemLog -notmatch 'copyApprovalReminderAuditLink' -or
        $systemLog -notmatch 'copyApprovalReminderAuditLinkToClipboard') {
        Fail "SystemLog approval reminder audit rows must expose copyable ID and deep link"
    }
    if ($adminApi -notmatch 'ScoreAppealAuditLog' -or
        $adminApi -notmatch 'export interface ScoreAppealAuditQuery \{[\s\S]*?logId\?: number \| string' -or
        $adminApi -notmatch 'listScoreAppealAuditLogs' -or
        $adminApi -notmatch 'exportScoreAppealAuditLogs' -or
        $adminApi -notmatch 'score-appeal-logs/export' -or
        $monitorController -notmatch 'score-appeal-logs' -or
        $monitorService -notmatch 'appendScoreAppealAuditFilters\(StringBuilder where, List<Object> params, Long logId' -or
        $systemLog -notmatch 'scoreAppealLogs' -or
        $systemLog -notmatch 'scoreAppealLogId' -or
        $systemLog -notmatch 'normalizedScoreAppealLogId' -or
        $systemLog -notmatch '成绩申诉审计' -or
        $systemLog -notmatch 'exportScoreAppealLogs' -or
        $systemLog -notmatch 'searchScoreAppealLogs' -or
        $systemLog -notmatch 'scoreAppealHandlingResultText') {
        Fail "SystemLog must expose administrator score appeal audit history"
    }
    if ($clipboardUtils -notmatch 'copyScoreAppealAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copyScoreAppealAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildScoreAppealAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?scoreAppealLogId=' -or
        $systemLog -notmatch 'score-appeal-id-cell' -or
        $systemLog -notmatch 'Copy score appeal audit ID' -or
        $systemLog -notmatch 'Copy score appeal audit link' -or
        $systemLog -notmatch 'copyScoreAppealAuditId' -or
        $systemLog -notmatch 'copyScoreAppealAuditLink' -or
        $systemLog -notmatch 'copyScoreAppealAuditLinkToClipboard') {
        Fail "SystemLog score appeal audit rows must expose copyable ID and deep link"
    }
    if ($adminApi -notmatch 'ReviewScoreAuditLog' -or
        $adminApi -notmatch 'ReviewScoreAuditQuery' -or
        $adminApi -notmatch 'export interface ReviewScoreAuditQuery \{[\s\S]*?logId\?: number \| string' -or
        $adminApi -notmatch 'listReviewScoreAuditLogs' -or
        $adminApi -notmatch 'exportReviewScoreAuditLogs' -or
        $adminApi -notmatch 'review-score-logs/export' -or
        $systemLog -notmatch 'Review Score Audit' -or
        $systemLog -notmatch 'reviewScoreLogs' -or
        $systemLog -notmatch 'reviewScoreQuery' -or
        $systemLog -notmatch 'reviewScoreLogId' -or
        $systemLog -notmatch 'normalizedReviewScoreLogId' -or
        $systemLog -notmatch 'searchReviewScoreLogs' -or
        $systemLog -notmatch 'exportReviewScoreLogs') {
        Fail "SystemLog must expose administrator review score audit search and export"
    }
    if ($clipboardUtils -notmatch 'copyReviewScoreAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copyReviewScoreAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildReviewScoreAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?reviewScoreLogId=' -or
        $systemLog -notmatch 'review-score-id-cell' -or
        $systemLog -notmatch 'Copy review score audit ID' -or
        $systemLog -notmatch 'Copy review score audit link' -or
        $systemLog -notmatch 'copyReviewScoreAuditId' -or
        $systemLog -notmatch 'copyReviewScoreAuditLink' -or
        $systemLog -notmatch 'copyReviewScoreAuditLinkToClipboard') {
        Fail "SystemLog review score audit rows must expose copyable ID and deep link"
    }
    if ($adminApi -notmatch 'QuestionReviewAuditLog' -or
        $adminApi -notmatch 'QuestionReviewAuditQuery' -or
        $adminApi -notmatch 'export interface QuestionReviewAuditQuery \{[\s\S]*?logId\?: number \| string' -or
        $adminApi -notmatch 'listQuestionReviewAuditLogs' -or
        $adminApi -notmatch 'exportQuestionReviewAuditLogs' -or
        $adminApi -notmatch 'question-review-logs/export' -or
        $systemLog -notmatch 'Question Review Audit' -or
        $systemLog -notmatch 'questionReviewLogs' -or
        $systemLog -notmatch 'questionReviewLogId' -or
        $systemLog -notmatch 'normalizedQuestionReviewLogId' -or
        $systemLog -notmatch 'searchQuestionReviewLogs' -or
        $systemLog -notmatch 'exportQuestionReviewLogs' -or
        $systemLog -notmatch 'questionReviewActionText' -or
        $systemLog -notmatch 'questionReviewStatusText') {
        Fail "SystemLog must expose administrator question review audit search and export"
    }
    if ($clipboardUtils -notmatch 'copyQuestionReviewAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copyQuestionReviewAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildQuestionReviewAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?questionReviewLogId=' -or
        $systemLog -notmatch 'question-review-id-cell' -or
        $systemLog -notmatch 'Copy question review audit ID' -or
        $systemLog -notmatch 'Copy question review audit link' -or
        $systemLog -notmatch 'copyQuestionReviewAuditId' -or
        $systemLog -notmatch 'copyQuestionReviewAuditLink' -or
        $questionBankPanel -notmatch 'question-review-log-id-cell' -or
        $questionBankPanel -notmatch 'copyQuestionReviewAuditIdToClipboard' -or
        $questionBankPanel -notmatch 'copyQuestionReviewAuditLinkToClipboard') {
        Fail "Question review audit rows must expose copyable IDs and unified deep links"
    }
    if ($questionApi -notmatch 'questionReviewLogId\?: number \| null' -or
        $questionBankPanel -notmatch 'lastQuestionOperationAudit' -or
        $questionBankPanel -notmatch 'rememberQuestionOperationAudit' -or
        $questionBankPanel -notmatch 'questionOperationAuditText' -or
        $questionBankPanel -notmatch 'question-operation-audit' -or
        $questionBankPanel -notmatch 'copyLatestQuestionOperationAuditId' -or
        $questionBankPanel -notmatch 'copyLatestQuestionOperationAuditLink' -or
        $questionBankPanel -notmatch 'response\.data\.questionReviewLogId' -or
        $questionBankPanel -notmatch 'responses\.map\(\(response\) => response\.data\.questionReviewLogId\)' -or
        $questionBankPanel -notmatch 'Batch submit question review') {
        Fail "QuestionBankPanel must surface generated question review audit ids after question mutations"
    }
    if ($aiApi -notmatch 'questionReviewLogIds\?: Array<number \| string>' -or
        $questionBankPanel -notmatch 'Save AI generated questions' -or
        $questionBankPanel -notmatch 'response\.data\.questionReviewLogIds \|\| response\.data\.questions\.map\(\(question\) => question\.questionReviewLogId\)') {
        Fail "QuestionBankPanel must surface generated question review audit ids after AI generated question saves"
    }
    if ($adminApi -notmatch 'NotificationAuditLog' -or
        $adminApi -notmatch 'NotificationAuditQuery' -or
        $adminApi -notmatch 'notificationId\?: number \| string' -or
        $adminApi -notmatch "params\.set\('notificationId', String\(query\.notificationId\)\)" -or
        $adminApi -notmatch 'listNotificationAuditLogs' -or
        $adminApi -notmatch 'exportNotificationAuditLogs' -or
        $adminApi -notmatch 'notifications/audit/export' -or
        $systemLog -notmatch 'notificationLogs' -or
        $systemLog -notmatch 'Notification Audit' -or
        $systemLog -notmatch 'Notification delivery audit' -or
        $systemLog -notmatch 'useRoute' -or
        $systemLog -notmatch 'logTabs' -or
        $systemLog -notmatch 'hydrateLogRouteQuery' -or
        $systemLog -notmatch 'route\.fullPath' -or
        $systemLog -notmatch 'previousTab' -or
        $systemLog -notmatch 'changed && activeTab\.value === previousTab' -or
        $systemLog -notmatch 'route\.query\.notificationId' -or
        $systemLog -notmatch 'hasNotificationIdQuery' -or
        $systemLog -notmatch 'hasRouteQueryKey' -or
        $systemLog -notmatch "tab === 'notification' && notificationQuery\.notificationId" -or
        $systemLog -notmatch "notificationQuery\.notificationId = ''" -or
        $systemLog -notmatch 'activeTab\.value = ''notification''' -or
        $systemLog -notmatch 'isLogTab' -or
        $systemLog -notmatch 'firstQueryValue' -or
        $systemLog -notmatch 'extractNotificationAuditId' -or
        $systemLog -notmatch 'notificationPage\.value = 1' -or
        $systemLog -notmatch 'label="ID"' -or
        $systemLog -notmatch '#\{\{ scope\.row\.id \}\}' -or
        $systemLog -notmatch 'DocumentCopy' -or
        $systemLog -notmatch 'Copy notification audit ID' -or
        $systemLog -notmatch 'Copy notification audit link' -or
        $systemLog -notmatch 'copyNotificationAuditId' -or
        $systemLog -notmatch 'copyNotificationAuditLink' -or
        $systemLog -notmatch 'copyNotificationAuditIdToClipboard' -or
        $systemLog -notmatch 'copyNotificationAuditLinkToClipboard' -or
        $systemLog -notmatch '@click="copyNotificationAuditLink\(scope\.row\.id\)"' -or
        $systemLog -notmatch 'notification-id-cell' -or
        $systemLog -notmatch 'notificationQuery\.notificationId' -or
        $systemLog -notmatch 'placeholder="Notification ID"' -or
        $systemLog -notmatch '@blur="normalizeNotificationAuditIdField"' -or
        $systemLog -notmatch 'normalizedNotificationAuditId' -or
        $systemLog -notmatch 'normalizeNotificationAuditIdField' -or
        $systemLog -notmatch 'value\.match\(/\\d\+/\)' -or
        $systemLog -notmatch 'notificationId: normalizedNotificationAuditId\(\) \|\| undefined' -or
        $systemLog -notmatch 'searchNotificationLogs' -or
        $systemLog -notmatch 'exportNotificationLogs' -or
        $systemLog -notmatch 'notificationReadText' -or
        $clipboardUtils -notmatch 'copyNotificationAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copyNotificationAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildNotificationAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?notificationId=' -or
        $clipboardUtils -notmatch 'encodeURIComponent\(value\)' -or
        $clipboardUtils -notmatch 'writeClipboardText' -or
        $clipboardUtils -notmatch 'navigator\.clipboard\?\.writeText' -or
        $clipboardUtils -notmatch "document\.execCommand\('copy'\)") {
        Fail "SystemLog must expose administrator notification audit history"
    }
    $systemApi = Get-Content -LiteralPath "frontend\src\api\system.ts" -Raw
    $systemConfigPanel = Get-Content -LiteralPath "frontend\src\components\SystemConfigPanel.vue" -Raw
    $systemConfigController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\SystemConfigController.java" -Raw
    $systemConfigService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\SystemConfigService.java" -Raw
    if ($systemConfigController -notmatch 'configs"\)' -or
        $systemConfigController -notmatch 'audit/export' -or
        $systemConfigController -notmatch 'exportAuditLogs' -or
        $systemConfigController -notmatch 'Long logId' -or
        $systemConfigService -notmatch 'recordConfigAudit' -or
        $systemConfigService -notmatch 'listConfigAuditLogs' -or
        $systemConfigService -notmatch 'exportConfigAuditLogs' -or
        $systemConfigService -notmatch 'appendConfigAuditFilters\(StringBuilder where, List<Object> params, Long logId' -or
        $systemConfigService -notmatch 'AND l\.id = \?' -or
        $systemConfigService -notmatch 'FROM system_config_log l' -or
        $systemConfigService -notmatch 'LIMIT 5000') {
        Fail "System config APIs must record, search, and export config audit logs"
    }
    if ($systemApi -notmatch 'SystemConfigAuditLog' -or
        $systemApi -notmatch 'listSystemConfigAuditLogs' -or
        $systemApi -notmatch 'exportSystemConfigAuditLogs' -or
        $systemApi -notmatch 'configs/audit/export' -or
        $systemConfigPanel -notmatch 'Config Audit' -or
        $systemConfigPanel -notmatch 'auditLogs' -or
        $systemConfigPanel -notmatch 'openAuditDrawer' -or
        $systemConfigPanel -notmatch 'exportAuditLogs') {
        Fail "SystemConfigPanel must expose system config audit search and export"
    }
    if ($adminApi -notmatch 'SystemConfigAuditLog' -or
        $adminApi -notmatch 'export interface SystemConfigAuditQuery \{[\s\S]*?logId\?: number \| string' -or
        $adminApi -notmatch 'listSystemConfigAuditLogs' -or
        $adminApi -notmatch 'exportSystemConfigAuditLogs' -or
        $adminApi -notmatch 'system-config-logs/export' -or
        $monitorController -notmatch 'system-config-logs' -or
        $monitorController -notmatch 'getSystemConfigAuditLogs' -or
        $monitorController -notmatch 'exportSystemConfigAuditLogs' -or
        $monitorController -notmatch 'SystemConfigService' -or
        $systemLog -notmatch 'System Config Audit' -or
        $systemLog -notmatch 'systemConfigLogs' -or
        $systemLog -notmatch 'systemConfigLogId' -or
        $systemLog -notmatch 'normalizedSystemConfigLogId' -or
        $systemLog -notmatch 'searchSystemConfigLogs' -or
        $systemLog -notmatch 'exportSystemConfigLogs' -or
        $systemLog -notmatch 'system-config-id-cell' -or
        $systemLog -notmatch 'Copy system config audit ID' -or
        $systemLog -notmatch 'Copy system config audit link' -or
        $clipboardUtils -notmatch 'copySystemConfigAuditIdToClipboard' -or
        $clipboardUtils -notmatch 'copySystemConfigAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildSystemConfigAuditDeepLink' -or
        $clipboardUtils -notmatch '/monitor/logs\?systemConfigLogId=' -or
        $systemConfigPanel -notmatch 'config-audit-id-cell' -or
        $systemConfigPanel -notmatch 'copySystemConfigAuditLinkToClipboard') {
        Fail "System config audit must be available from unified SystemLog and local audit evidence rows"
    }
    $monitorApi = Get-Content -LiteralPath "frontend\src\api\monitor.ts" -Raw
    if ($monitorApi -match 'MonitorForceSubmitResult[\s\S]*score:\s*number') {
        Fail "frontend monitor force-submit type must not expose a score field before release"
    }
    if ($monitorApi -notmatch 'MonitorForceSubmitResult[\s\S]*forcedSubmitted\?: boolean' -or
        $monitorApi -notmatch 'MonitorForceSubmitResult[\s\S]*submitted\?: boolean' -or
        $monitorApi -notmatch 'MonitorForceSubmitResult[\s\S]*submitTime\?: string \| null' -or
        $monitorApi -notmatch 'MonitorForceSubmitResult[\s\S]*questionCount\?: number') {
        Fail "frontend monitor force-submit type must expose forced submission flags, submitTime, and answer statistics"
    }
    $examMonitorPanel = Get-Content -LiteralPath "frontend\src\components\ExamMonitorPanel.vue" -Raw
    if ($monitorController -notmatch 'OperationLogService' -or
        $monitorController -notmatch 'CREATE_MONITOR_ACTION' -or
        $monitorController -notmatch 'FORCE_SUBMIT_MONITOR_SESSION' -or
        $monitorController -notmatch 'result\.put\("operationLogId", operationLogId\)' -or
        $monitorApi -notmatch 'MonitorAction[\s\S]*operationLogId\?: number \| string \| null' -or
        $monitorApi -notmatch 'MonitorForceSubmitResult[\s\S]*operationLogId\?: number \| string \| null' -or
        $examMonitorPanel -notmatch 'lastMonitorOperationAudit' -or
        $examMonitorPanel -notmatch 'rememberMonitorOperationAudit' -or
        $examMonitorPanel -notmatch 'monitor-operation-audit' -or
        $examMonitorPanel -notmatch 'copyLatestMonitorOperationAuditId' -or
        $examMonitorPanel -notmatch 'copyLatestMonitorOperationAuditLink' -or
        $examMonitorPanel -notmatch 'copyOperationLogIdToClipboard' -or
        $examMonitorPanel -notmatch 'copyOperationLogLinkToClipboard' -or
        $examMonitorPanel -notmatch 'response\.data\.operationLogId') {
        Fail "ExamMonitorPanel must surface operation audit evidence ids after monitor actions and force-submit"
    }
    $notificationBell = Get-Content -LiteralPath "frontend\src\components\NotificationBell.vue" -Raw
    $examList = Get-Content -LiteralPath "frontend\src\components\ExamList.vue" -Raw
    $studentDashboard = Get-Content -LiteralPath "frontend\src\components\StudentDashboard.vue" -Raw
    if ($notificationBell -notmatch 'useRouter' -or
        $notificationBell -notmatch 'router\.push\(item\.link\)' -or
        $notificationBell -match 'window\.location\.pathname = item\.link' -or
        $examList -notmatch 'rulesReminderExam' -or
        $examList -notmatch 'Rules confirmation required' -or
        $examList -notmatch 'route\.query\.attemptId' -or
        $examList -notmatch 'route\.query\.notice' -or
        $examList -notmatch 'exam\.status >= 2 \|\| exam\.rulesConfirmedAt' -or
        $examList -notmatch 'applyRouteAttemptFocus' -or
        $examList -notmatch 'examRowClassName' -or
        $examList -notmatch 'clearRulesReminderRoute' -or
        $examList -notmatch 'delete nextQuery\.notice' -or
        $examList -notmatch 'delete nextQuery\.attemptId' -or
        $examList -notmatch 'router\.replace\(\{ path: route\.path, query: nextQuery \}\)' -or
        $examList -notmatch 'exam-row-focused' -or
        $examList -notmatch ':row-class-name="examRowClassName"') {
        Fail "Student rules reminder notifications must deep-link to and highlight the target exam"
    }
    if ($overviewService -notmatch 'ea\.id AS attemptId' -or
        $studentDashboard -notmatch '@click="openRecentExam\(exam\)"' -or
        $studentDashboard -notmatch 'function openRecentExam\(exam: RecentExam\)' -or
        $studentDashboard -notmatch 'emit\(''navigate'', `/student/exams\?attemptId=\$\{attemptId\}`\)' -or
        $studentDashboard -notmatch 'emit\(''navigate'', ''/student/exams''\)') {
        Fail "StudentDashboard recent exams must preserve attemptId and deep-link to the exact exam row"
    }
    if ($monitorApi -notmatch 'exportExamMonitorSessions' -or
        $monitorApi -notmatch 'sessions/export' -or
        $monitorApi -notmatch 'MonitorSessionExportQuery' -or
        $monitorApi -notmatch 'latestNotificationStatus\?: string' -or
        $monitorApi -notmatch 'rulesConfirmationStatus\?: string' -or
        $monitorApi -notmatch 'latestActionType\?: string' -or
        $monitorApi -notmatch "params\.set\('latestNotificationStatus', query\.latestNotificationStatus\)" -or
        $monitorApi -notmatch "params\.set\('rulesConfirmationStatus', query\.rulesConfirmationStatus\)" -or
        $monitorApi -notmatch "params\.set\('latestActionType', query\.latestActionType\)" -or
        $monitorApi -notmatch 'latestActionNotificationRead\?: boolean \| number \| string \| null' -or
        $monitorApi -notmatch 'latestActionNotificationCreatedAt\?: string \| null' -or
        $monitorApi -notmatch 'rulesConfirmedAt\?: string \| null' -or
        $monitorApi -notmatch 'monitorSessionExportQueryString' -or
        $monitorApi -notmatch 'monitorSessionExportFileName' -or
        $monitorApi -notmatch 'monitorSessionExportFilterParts' -or
        $monitorApi -notmatch 'safeFileNamePart' -or
        $monitorApi -notmatch 'status_\$\{safeFileNamePart\(query\.sessionStatus\)\}' -or
        $monitorApi -notmatch 'risk_\$\{query\.minRiskScore\}' -or
        $monitorApi -notmatch 'notice_\$\{safeFileNamePart\(query\.latestNotificationStatus\)\}' -or
        $monitorApi -notmatch 'rules_\$\{safeFileNamePart\(query\.rulesConfirmationStatus\)\}' -or
        $monitorApi -notmatch 'action_\$\{safeFileNamePart\(query\.latestActionType\)\}' -or
        $monitorApi -notmatch "params\.set\('sessionStatus', query\.sessionStatus\)" -or
        $examMonitorPanel -notmatch 'sessionFilter' -or
        $examMonitorPanel -notmatch 'filteredSessions' -or
        $examMonitorPanel -notmatch ':data="filteredSessions"' -or
        $examMonitorPanel -notmatch 'session-status-filter' -or
        $examMonitorPanel -notmatch 'session-risk-filter' -or
        $examMonitorPanel -notmatch 'session-notification-filter' -or
        $examMonitorPanel -notmatch 'session-rules-filter' -or
        $examMonitorPanel -notmatch 'session-action-filter' -or
        $examMonitorPanel -notmatch 'scope\.row\.rulesConfirmedAt' -or
        $examMonitorPanel -notmatch 'label="Rules"' -or
        $examMonitorPanel -notmatch 'latestNotificationStatusMatches' -or
        $examMonitorPanel -notmatch 'rulesConfirmationStatusMatches' -or
        $examMonitorPanel -notmatch 'latestActionTypeMatches' -or
        $examMonitorPanel -notmatch 'canSendRulesReminder' -or
        $examMonitorPanel -notmatch 'RULES_REMINDER' -or
        $examMonitorPanel -notmatch 'isNotificationActionType' -or
        $examMonitorPanel -notmatch 'hasLatestNotification' -or
        $examMonitorPanel -notmatch "latestNotificationStatus: 'ALL'" -or
        $examMonitorPanel -notmatch "rulesConfirmationStatus: 'ALL'" -or
        $examMonitorPanel -notmatch "latestActionType: 'ALL'" -or
        $examMonitorPanel -notmatch 'metrics\.unreadNotices' -or
        $examMonitorPanel -notmatch 'metrics\.missingRules' -or
        $examMonitorPanel -notmatch 'metrics\.rulesReminders' -or
        $examMonitorPanel -notmatch 'metrics\.pendingRulesReminders' -or
        $examMonitorPanel -notmatch 'Unread notices' -or
        $examMonitorPanel -notmatch 'Missing rules' -or
        $examMonitorPanel -notmatch 'Rules reminders' -or
        $examMonitorPanel -notmatch 'Pending reminders' -or
        $examMonitorPanel -notmatch 'metric-card notice' -or
        $examMonitorPanel -notmatch 'metric-card rules' -or
        $examMonitorPanel -notmatch 'metric-card rules-reminder' -or
        $examMonitorPanel -notmatch 'metric-card pending-reminder' -or
        $examMonitorPanel -notmatch 'isUnreadNoticeFilterActive' -or
        $examMonitorPanel -notmatch 'isMissingRulesFilterActive' -or
        $examMonitorPanel -notmatch 'isRulesReminderFilterActive' -or
        $examMonitorPanel -notmatch 'isPendingRulesReminderFilterActive' -or
        $examMonitorPanel -notmatch ':disabled="metrics\.unreadNotices === 0"' -or
        $examMonitorPanel -notmatch ':disabled="metrics\.missingRules === 0"' -or
        $examMonitorPanel -notmatch ':disabled="metrics\.rulesReminders === 0"' -or
        $examMonitorPanel -notmatch ':disabled="metrics\.pendingRulesReminders === 0"' -or
        $examMonitorPanel -notmatch ':aria-pressed="isUnreadNoticeFilterActive"' -or
        $examMonitorPanel -notmatch ':aria-pressed="isMissingRulesFilterActive"' -or
        $examMonitorPanel -notmatch ':aria-pressed="isRulesReminderFilterActive"' -or
        $examMonitorPanel -notmatch ':aria-pressed="isPendingRulesReminderFilterActive"' -or
        $examMonitorPanel -notmatch 'button\.metric-card\.active' -or
        $examMonitorPanel -notmatch 'button\.metric-card:disabled' -or
        $examMonitorPanel -notmatch 'if \(metrics\.value\.unreadNotices === 0\) return' -or
        $examMonitorPanel -notmatch 'if \(metrics\.value\.missingRules === 0\) return' -or
        $examMonitorPanel -notmatch 'if \(metrics\.value\.rulesReminders === 0\) return' -or
        $examMonitorPanel -notmatch 'if \(metrics\.value\.pendingRulesReminders === 0\) return' -or
        $examMonitorPanel -notmatch 'applyUnreadNoticeFilter' -or
        $examMonitorPanel -notmatch 'applyMissingRulesFilter' -or
        $examMonitorPanel -notmatch 'applyRulesReminderFilter' -or
        $examMonitorPanel -notmatch 'applyPendingRulesReminderFilter' -or
        $examMonitorPanel -notmatch 'isPendingRulesReminder' -or
        $examMonitorPanel -notmatch 'rulesReminderResolutionText' -or
        $examMonitorPanel -notmatch 'rulesReminderResolutionTagType' -or
        $examMonitorPanel -notmatch 'Pending confirmation' -or
        $examMonitorPanel -notmatch 'Confirmed after reminder' -or
        $examMonitorPanel -notmatch "sessionFilter\.latestNotificationStatus = 'UNREAD'" -or
        $examMonitorPanel -notmatch "sessionFilter\.rulesConfirmationStatus = 'MISSING'" -or
        $examMonitorPanel -notmatch "sessionFilter\.latestActionType = 'RULES_REMINDER'" -or
        $examMonitorPanel -notmatch "sessionFilter\.rulesConfirmationStatus = 'ALL'" -or
        $examMonitorPanel -notmatch "sessionFilter\.latestActionType = 'ALL'" -or
        $examMonitorPanel -notmatch 'button\.metric-card:focus-visible' -or
        $examMonitorPanel -notmatch 'resetSessionFilters' -or
        $examMonitorPanel -notmatch 'No monitor sessions match the filters' -or
        $examMonitorPanel -notmatch 'return filteredSessions\.value\.reduce' -or
        $examMonitorPanel -notmatch 'buildSessionExportQuery' -or
        $examMonitorPanel -notmatch 'rulesConfirmationStatus:\s*[\r\n]+\s*sessionFilter\.rulesConfirmationStatus === ''ALL'' \? undefined : sessionFilter\.rulesConfirmationStatus' -or
        $examMonitorPanel -notmatch 'latestActionType: sessionFilter\.latestActionType === ''ALL'' \? undefined : sessionFilter\.latestActionType' -or
        $examMonitorPanel -notmatch 'exportExamMonitorSessions\(selectedExamId\.value, selectedExam\.value\?\.examName, buildSessionExportQuery\(\)\)' -or
        $examMonitorPanel -notmatch 'latestNotificationText' -or
        $examMonitorPanel -notmatch 'latestNotificationTagType' -or
        $examMonitorPanel -notmatch 'isLatestNotificationRead' -or
        $examMonitorPanel -notmatch 'DocumentCopy' -or
        $examMonitorPanel -notmatch 'Copy notification audit ID' -or
        $examMonitorPanel -notmatch 'Copy notification audit link' -or
        $examMonitorPanel -notmatch 'Copy link' -or
        $examMonitorPanel -notmatch 'copyNotificationAuditId' -or
        $examMonitorPanel -notmatch 'copyNotificationAuditLink' -or
        $examMonitorPanel -notmatch 'copyNotificationAuditIdToClipboard' -or
        $examMonitorPanel -notmatch 'copyNotificationAuditLinkToClipboard' -or
        $examMonitorPanel -notmatch '@click\.stop="copyNotificationAuditId\(scope\.row\.latestActionNotificationId\)"' -or
        $examMonitorPanel -notmatch '@click\.stop="copyNotificationAuditLink\(scope\.row\.latestActionNotificationId\)"' -or
        $examMonitorPanel -notmatch 'value="WARN" :disabled="actionTarget\?\.attemptStatus !== 1"' -or
        $examMonitorPanel -notmatch 'exportExamMonitorSessions' -or
        $examMonitorPanel -notmatch 'exportSessions' -or
        $examMonitorPanel -notmatch 'exporting') {
        Fail "ExamMonitorPanel must export scoped exam monitor sessions"
    }
    if ($monitorApi -notmatch 'exportAttemptMonitorEvents' -or
        $monitorApi -notmatch 'cheat-events/\$\{attemptId\}/export' -or
        $monitorApi -notmatch 'examId\?: number' -or
        $monitorApi -notmatch 'userId\?: number' -or
        $monitorApi -notmatch 'riskScore\?: number' -or
        $monitorApi -notmatch 'minRiskScore\?: number' -or
        $monitorApi -notmatch "params\.set\('minRiskScore', String\(query\.minRiskScore\)\)" -or
        $monitorApi -notmatch 'MonitorEventQuery' -or
        $monitorApi -notmatch 'monitorEventQueryString' -or
        $monitorApi -notmatch 'monitorEventExportFileName' -or
        $monitorApi -notmatch 'monitorEventExportFilterParts' -or
        $monitorApi -notmatch 'event_\$\{safeFileNamePart\(query\.eventType\)\}' -or
        $monitorApi -notmatch 'from_\$\{safeFileNamePart\(query\.startFrom\)\}' -or
        $monitorApi -notmatch 'to_\$\{safeFileNamePart\(query\.startTo\)\}' -or
        $monitorApi -notmatch 'attempt_\$\{attemptId\}' -or
        $examMonitorPanel -notmatch 'event-risk-filter' -or
        $examMonitorPanel -notmatch 'eventFilter\.minRiskScore' -or
        $examMonitorPanel -notmatch 'minRiskScore: eventFilter\.minRiskScore >= 0 \? eventFilter\.minRiskScore : undefined' -or
        $examMonitorPanel -notmatch 'event-title' -or
        $examMonitorPanel -notmatch 'riskType\(Number\(event\.riskScore \|\| 0\)\)' -or
        $examMonitorPanel -notmatch 'Risk \{\{ event\.riskScore \|\| 0 \}\}' -or
        $examMonitorPanel -notmatch 'exportAttemptMonitorEvents' -or
        $examMonitorPanel -notmatch 'exportActiveEvents' -or
        $examMonitorPanel -notmatch 'eventExporting' -or
        $examMonitorPanel -notmatch 'eventFilter' -or
        $examMonitorPanel -notmatch 'buildEventQuery' -or
        $examMonitorPanel -notmatch 'reloadActiveEvents') {
        Fail "ExamMonitorPanel must export scoped monitor event details"
    }
    if ($monitorApi -notmatch 'exportMonitorActions' -or
        $monitorApi -notmatch 'sessions/\$\{sessionId\}/actions/export' -or
        $monitorApi -notmatch 'notificationSent\?: boolean \| number \| string' -or
        $monitorApi -notmatch 'notificationId\?: number \| string \| null' -or
        $monitorApi -notmatch 'notificationRead\?: boolean \| number \| string \| null' -or
        $monitorApi -notmatch 'notificationCreatedAt\?: string \| null' -or
        $monitorApi -notmatch 'monitorActionExportFileName' -or
        $monitorApi -notmatch 'session_\$\{sessionId\}' -or
        $examMonitorPanel -notmatch 'exportMonitorActions' -or
        $examMonitorPanel -notmatch 'exportActiveActions' -or
        $examMonitorPanel -notmatch 'activeSession\.value\.attemptId' -or
        $examMonitorPanel -notmatch 'actionNotificationText' -or
        $examMonitorPanel -notmatch 'actionNotificationTagType' -or
        $examMonitorPanel -notmatch 'isNotificationRead' -or
        $examMonitorPanel -notmatch "Read' : 'Unread'" -or
        $examMonitorPanel -notmatch 'Notification #\$\{action\.notificationId\}' -or
        $examMonitorPanel -notmatch 'action-notification-row' -or
        $examMonitorPanel -notmatch '@click="copyNotificationAuditId\(action\.notificationId\)"' -or
        $examMonitorPanel -notmatch '@click="copyNotificationAuditLink\(action\.notificationId\)"' -or
        $examMonitorPanel -notmatch 'actionExporting') {
        Fail "ExamMonitorPanel must export scoped monitor action details"
    }
    $examManagement = Get-Content -LiteralPath "frontend\src\components\ExamManagement.vue" -Raw
    $examApprovalQueue = Get-Content -LiteralPath "frontend\src\components\ExamApprovalQueue.vue" -Raw
    $adminDashboard = Get-Content -LiteralPath "frontend\src\components\AdminDashboard.vue" -Raw
    $teacherDashboard = Get-Content -LiteralPath "frontend\src\components\TeacherDashboard.vue" -Raw
    if ($examController -notmatch '@RequestParam\(required = false\) Long examId' -or
        $examService -notmatch 'listTeacherExams\(String keyword, Integer status, Long examId, AuthUser user' -or
        $examService -notmatch 'AND \(\? IS NULL OR e\.id = \?\)' -or
        $examApi -notmatch 'examId\?: number \| null' -or
        $examApi -notmatch "params\.set\('examId', String\(query\.examId\)\)" -or
        $examManagement -notmatch 'useRoute' -or
        $examManagement -notmatch 'route\.query\.examId' -or
        $examManagement -notmatch 'examId: focusedExamId\.value' -or
        $examManagement -notmatch 'examRowClassName' -or
        $examManagement -notmatch 'exam-management-row-focused' -or
        $adminDashboard -notmatch 'function openRecentExam\(exam: RecentExam\)' -or
        $adminDashboard -notmatch 'emit\(''navigate'', `/exam-tasks\?examId=\$\{examId\}`\)' -or
        $teacherDashboard -notmatch 'function openRecentExam\(exam: RecentExam\)' -or
        $teacherDashboard -notmatch 'emit\(''navigate'', `/exam-tasks\?examId=\$\{examId\}`\)') {
        Fail "Admin and teacher recent exams must deep-link to exact exam management rows through scoped examId filtering"
    }
    if ($overviewService -notmatch 'data\.put\("opsCapacity", adminOpsCapacity\(jt\)\)' -or
        $overviewService -notmatch 'private Map<String, Object> adminOpsCapacity\(JdbcTemplate jt\)' -or
        $overviewService -notmatch 'databaseHealth' -or
        $overviewService -notmatch 'draftCacheCapacity' -or
        $overviewService -notmatch 'examRuntimeCapacity' -or
        $overviewService -notmatch 'monitorRuntimeCapacity' -or
        $overviewService -notmatch 'submitRuntimeCapacity' -or
        $overviewService -notmatch 'opsCapacityAlerts' -or
        $overviewService -notmatch 'examDraftCacheService\.stats\(\)' -or
        $adminDashboard -notmatch 'ops-capacity-panel' -or
        $adminDashboard -notmatch 'interface OpsCapacity' -or
        $adminDashboard -notmatch 'databaseStatusText' -or
        $adminDashboard -notmatch 'opsLevelTagType' -or
        $adminDashboard -notmatch 'data\.opsCapacity\.monitorRuntime\.eventsLast10m') {
        Fail "AdminDashboard must surface operations capacity and backend opsCapacity evidence"
    }
    if ($examService -notmatch 'listApprovalQueue\(String keyword, String creatorKeyword, Integer status,[\s\S]*Long examId, int page, int size, AuthUser user' -or
        $examService -notmatch 'approvalQueueWhere\(keyword, creatorKeyword, status, startFrom, startTo, risk, examId, params\)' -or
        $examService -notmatch 'private String approvalQueueWhere\(String keyword, String creatorKeyword, Integer status,[\s\S]*String startFrom, String startTo, String risk, Long examId, List<Object> params\)' -or
        $examService -notmatch 'if \(examId != null\) \{[\s\S]*where\.append\(" AND e\.id = \?"\);[\s\S]*params\.add\(examId\);' -or
        $examApi -notmatch 'listExamApprovalQueue[\s\S]*examId\?: number \| null' -or
        $examApi -notmatch 'listExamApprovalQueue[\s\S]*params\.set\(''examId'', String\(query\.examId\)\)' -or
        $examApprovalQueue -notmatch 'useRoute' -or
        $examApprovalQueue -notmatch 'route\.query\.examId' -or
        $examApprovalQueue -notmatch 'examId: focusedApprovalExamId\.value' -or
        $examApprovalQueue -notmatch 'approvalRowClassName' -or
        $examApprovalQueue -notmatch 'approval-row-focused' -or
        $adminDashboard -notmatch 'function openPendingApprovalExam\(exam: PendingApprovalExam\)' -or
        $adminDashboard -notmatch 'emit\(''navigate'', `/exam-approvals\?examId=\$\{examId\}`\)') {
        Fail "Admin pending approval exams must deep-link to exact approval queue rows through scoped examId filtering"
    }
    if ($examApi -notmatch 'candidateCount\?: number;' -or
        $examApi -notmatch 'notifiedStudentCount\?: number;' -or
        $examApi -notmatch 'notifiedAttemptCount\?: number;') {
        Fail "ExamApprovalLog frontend type must include publish notification statistics"
    }
    if ($examApi -notmatch 'exportExamApprovalLogs' -or
        $examApi -notmatch 'approval-logs/export') {
        Fail "frontend exam API must expose scoped approval log export"
    }
    if ($examApi -notmatch 'publishCandidateCount\?: number;' -or
        $examApi -notmatch 'publishNotifiedStudentCount\?: number;' -or
        $examApi -notmatch 'publishNotifiedAttemptCount\?: number;') {
        Fail "ExamInfo frontend type must include immediate publish notification statistics"
    }
    if ($examService -notmatch 'Long approvalLogId = recordApprovalLog\(jt, id, APPROVAL_ACTION_APPROVE' -or
        $examService -notmatch 'Long approvalLogId = recordApprovalLog\(jt, id, APPROVAL_ACTION_REJECT' -or
        $examService -notmatch 'result\.put\("approvalLogId", approvalLogId\)' -or
        $examService -notmatch 'private Long recordApprovalLog' -or
        $examService -notmatch 'return lastInsertId\(jt\);' -or
        $examApi -notmatch 'approvalLogId\?: number \| null;' -or
        $examApprovalQueue -notmatch 'lastApprovalDecisionAudit' -or
        $examApprovalQueue -notmatch 'rememberApprovalDecisionAudit' -or
        $examApprovalQueue -notmatch 'approval-decision-audit' -or
        $examApprovalQueue -notmatch 'copyLatestApprovalDecisionAuditId' -or
        $examApprovalQueue -notmatch 'copyLatestApprovalDecisionAuditLink' -or
        $examApprovalQueue -notmatch 'copyExamApprovalAuditIdToClipboard' -or
        $examApprovalQueue -notmatch 'copyExamApprovalAuditLinkToClipboard' -or
        $examApprovalQueue -notmatch 'response\.data\.approvalLogId') {
        Fail "ExamApprovalQueue must surface immediate approval decision audit evidence ids"
    }
    if ($examApi -notmatch 'reminderLogId\?: number \| null;') {
        Fail "ApprovalReminderResult frontend type must expose reminderLogId"
    }
    if ($examApi -notmatch 'listApprovalReminderLogs\(page = 1, size = 10, query\?: \{ logId\?: number \| string \| null \}\)' -or
        $examApi -notmatch "params\.set\('logId', String\(query\.logId\)\)" -or
        $examApi -notmatch 'exportApprovalReminderLogs' -or
        $examApi -notmatch 'approvals/reminders/export') {
        Fail "frontend exam API must expose approval reminder log filtering and export"
    }
    if ($examManagement -notmatch 'approvalPublishStatsText' -or
        $examManagement -notmatch 'candidateCount \?\? 0' -or
        $examManagement -notmatch 'notifiedStudentCount \?\? 0' -or
        $examManagement -notmatch 'notifiedAttemptCount \?\? 0' -or
        $examManagement -notmatch 'approvalLogExporting' -or
        $examManagement -notmatch 'exportApprovalLogRows' -or
        $examManagement -notmatch 'exportExamApprovalLogs' -or
        $examManagement -notmatch 'approval-log-toolbar') {
        Fail "ExamManagement approval logs must display publish notification statistics"
    }
    if ($examManagement -notmatch 'openApprovalAudit' -or
        $examManagement -notmatch 'copyApprovalAuditLink' -or
        $examManagement -notmatch 'copyExamApprovalAuditLinkToClipboard' -or
        $examManagement -notmatch 'examApprovalLogId=' -or
        $examManagement -notmatch 'label="Audit"') {
        Fail "ExamManagement approval log drawer must deep-link to unified approval audit"
    }
    if ($examManagement -notmatch 'publishNotificationSummary' -or
        $examManagement -notmatch 'publishCandidateCount \?\? 0' -or
        $examManagement -notmatch 'publishNotifiedStudentCount \?\? 0' -or
        $examManagement -notmatch 'publishNotifiedAttemptCount \?\? 0') {
        Fail "ExamManagement publish success feedback must display notification statistics"
    }
    if ($examApi -notmatch 'operationLogId\?: number \| null' -or
        $examApi -notmatch 'deleteJson<\{ id: number; deleted: boolean; operationLogId\?: number \| null \}>' -or
        $examApi -notmatch 'putJson<\{ id: number; operationLogId\?: number \| null \}>' -or
        $examManagement -notmatch 'lastExamOperationAudit' -or
        $examManagement -notmatch 'rememberExamOperationAudit' -or
        $examManagement -notmatch 'exam-operation-audit' -or
        $examManagement -notmatch 'copyLatestExamOperationAuditId' -or
        $examManagement -notmatch 'copyLatestExamOperationAuditLink' -or
        $examManagement -notmatch 'copyOperationLogIdToClipboard' -or
        $examManagement -notmatch 'copyOperationLogLinkToClipboard' -or
        $examManagement -notmatch 'response\.data\.operationLogId' -or
        $examManagement -notmatch 'responses\.map\(\(response\) => response\.data\.operationLogId\)' -or
        $examManagement -notmatch 'Batch delete exams') {
        Fail "ExamManagement must surface and copy operation audit evidence ids after exam task mutations"
    }
    if ($examApprovalQueue -notmatch 'publishStatsText' -or
        $examApprovalQueue -notmatch 'candidateCount \?\? 0' -or
        $examApprovalQueue -notmatch 'notifiedStudentCount \?\? 0' -or
        $examApprovalQueue -notmatch 'notifiedAttemptCount \?\? 0' -or
        $examApprovalQueue -notmatch 'logExporting' -or
        $examApprovalQueue -notmatch 'exportApprovalLogs' -or
        $examApprovalQueue -notmatch 'exportExamApprovalLogs' -or
        $examApprovalQueue -notmatch 'approval-log-toolbar') {
        Fail "ExamApprovalQueue approval logs must display publish notification statistics"
    }
    if ($examApprovalQueue -notmatch 'openApprovalAudit' -or
        $examApprovalQueue -notmatch 'copyApprovalAuditLink' -or
        $examApprovalQueue -notmatch 'copyExamApprovalAuditLinkToClipboard' -or
        $examApprovalQueue -notmatch 'examApprovalLogId=' -or
        $examApprovalQueue -notmatch 'label="Audit"') {
        Fail "ExamApprovalQueue approval log drawer must deep-link to unified approval audit"
    }
    if ($examApprovalQueue -notmatch 'publishNotificationSummary' -or
        $examApprovalQueue -notmatch 'publishCandidateCount \?\? 0' -or
        $examApprovalQueue -notmatch 'publishNotifiedStudentCount \?\? 0' -or
        $examApprovalQueue -notmatch 'publishNotifiedAttemptCount \?\? 0') {
        Fail "ExamApprovalQueue publish success feedback must display notification statistics"
    }
    if ($examApprovalQueue -notmatch 'approvalReminderSuccessText' -or
        $examApprovalQueue -notmatch 'reminderLogId' -or
        $examApprovalQueue -notmatch 'ApprovalReminderResult') {
        Fail "ExamApprovalQueue reminder success feedback must expose reminder log ids"
    }
    if ($examApprovalQueue -notmatch 'route\.query\.reminderLogId' -or
        $examApprovalQueue -notmatch 'focusedReminderLogId' -or
        $examApprovalQueue -notmatch 'applyReminderLogRouteFocus' -or
        $examApprovalQueue -notmatch 'routeReminderLogId' -or
        $examApprovalQueue -notmatch 'reminderLogRowClassName' -or
        $examApprovalQueue -notmatch 'reminder-log-row-focused' -or
        $examApprovalQueue -notmatch 'logId: focusedReminderLogId\.value') {
        Fail "ExamApprovalQueue must deep-link approval reminder notifications to exact reminder log rows"
    }
    if ($examApprovalQueue -notmatch 'openReminderNotificationAudit' -or
        $examApprovalQueue -notmatch "relatedType: 'APPROVAL_REMINDER'" -or
        $examApprovalQueue -notmatch "path: '/monitor/logs'" -or
        $examApprovalQueue -notmatch 'relatedId: String\(row\.id\)') {
        Fail "ExamApprovalQueue reminder logs must deep-link to notification audit by reminder log id"
    }
    if ($clipboardUtils -notmatch 'copyApprovalReminderLogIdToClipboard' -or
        $clipboardUtils -notmatch 'copyApprovalReminderNotificationAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'copyApprovalReminderAuditLinkToClipboard' -or
        $clipboardUtils -notmatch 'buildApprovalReminderNotificationAuditDeepLink' -or
        $clipboardUtils -notmatch 'buildApprovalReminderAuditDeepLink' -or
        $clipboardUtils -notmatch "buildNotificationRelatedAuditDeepLink\('APPROVAL_REMINDER', logId\)" -or
        $clipboardUtils -notmatch '/monitor/logs\?approvalReminderLogId=' -or
        $examApprovalQueue -notmatch 'reminder-log-id-cell' -or
        $examApprovalQueue -notmatch 'Copy approval reminder log ID' -or
        $examApprovalQueue -notmatch 'Copy approval reminder notification audit link' -or
        $examApprovalQueue -notmatch 'Copy approval reminder audit link' -or
        $examApprovalQueue -notmatch 'copyReminderLogId' -or
        $examApprovalQueue -notmatch 'copyReminderNotificationAuditLink' -or
        $examApprovalQueue -notmatch 'copyReminderAuditLink') {
        Fail "ExamApprovalQueue reminder logs must expose copyable ids and notification audit links"
    }
    if ($examApprovalQueue -notmatch 'reminderLogExporting' -or
        $examApprovalQueue -notmatch 'exportReminderLogs' -or
        $examApprovalQueue -notmatch 'exportApprovalReminderLogs' -or
        $examApprovalQueue -notmatch 'reminder-log-toolbar') {
        Fail "ExamApprovalQueue reminder logs must expose export controls"
    }
    if ($examManagement -notmatch 'ElMessageBox\.prompt' -or $examManagement -notmatch 'revokeExamScores\(row\.id, \{ reason \}\)') {
        Fail "ExamManagement score revoke action must collect and submit a revoke reason"
    }
    if ($examManagement -notmatch 'scoreReleaseDetail' -or
        $examManagement -notmatch 'scoreReleaseNote' -or
        $examManagement -notmatch 'scorePublishNote' -or
        $examManagement -notmatch 'scoreRevokeReason' -or
        $examManagement -notmatch 'hasScoreRevokeAudit' -or
        $examManagement -notmatch 'scoreRevokedAt' -or
        $examManagement -notmatch 'scorePublishedByName' -or
        $examManagement -notmatch 'scoreRevokedByName') {
        Fail "ExamManagement must display score release revoke audit details"
    }
    if ($examManagement -notmatch 'openScoreReleaseLogs' -or
        $examManagement -notmatch 'getScoreReleaseLogs' -or
        $examManagement -notmatch 'exportScoreReleaseLogRows' -or
        $examManagement -notmatch 'exportScoreReleaseLogs' -or
        $examManagement -notmatch 'scoreReleaseLogExporting' -or
        $examManagement -notmatch 'score-release-log-toolbar' -or
        $examManagement -notmatch 'scoreReleaseLogs' -or
        $examManagement -notmatch 'scoreReleaseActionText') {
        Fail "ExamManagement must expose score release audit history"
    }
    if ($examManagement -notmatch 'openScoreReleaseAudit' -or
        $examManagement -notmatch 'scoreReleaseLogId=' -or
        $examManagement -notmatch 'copyScoreReleaseAuditLink' -or
        $examManagement -notmatch 'copyScoreReleaseAuditLinkToClipboard' -or
        $examManagement -notmatch 'DocumentCopy' -or
        $examManagement -notmatch "props.role === 'ADMIN'" -or
        $systemLog -notmatch 'scoreReleaseLogId') {
        Fail "ExamManagement score release records must deep-link and copy global score release audit logs for admins"
    }
    if ($examApi -notmatch 'scoreReleaseLogId\?: number \| null;' -or
        $examManagement -notmatch 'scoreReleaseLogSuffix' -or
        $examManagement -notmatch 'response\.data\.scoreReleaseLogId') {
        Fail "ExamManagement score release and revoke success feedback must display score release log ids"
    }
    if ($examManagement -notmatch 'lastScoreReleaseAudit' -or
        $examManagement -notmatch 'rememberScoreReleaseAudit' -or
        $examManagement -notmatch 'score-release-audit' -or
        $examManagement -notmatch 'copyLatestScoreReleaseAuditId' -or
        $examManagement -notmatch 'copyLatestScoreReleaseAuditLink' -or
        $examManagement -notmatch 'copyScoreReleaseAuditIdToClipboard' -or
        $examManagement -notmatch 'copyScoreReleaseAuditLinkToClipboard' -or
        $examManagement -notmatch "rememberScoreReleaseAudit\('Publish scores', \[response\.data\.scoreReleaseLogId\]\)" -or
        $examManagement -notmatch "rememberScoreReleaseAudit\('Revoke scores', \[response\.data\.scoreReleaseLogId\]\)") {
        Fail "ExamManagement must surface copyable local score release audit evidence after publish and revoke"
    }
    if ($examManagement -notmatch 'canExportScores' -or $examManagement -notmatch 'exportScoresDisabledReason' -or $examManagement -notmatch ':disabled="!canExportScores') {
        Fail "ExamManagement must disable score export unless scores are published"
    }
    if ($examManagement -notmatch 'canPublishScores' -or
        $examManagement -notmatch 'publishScoresDisabledReason' -or
        $examManagement -notmatch 'nonFinalStartedAttemptCount' -or
        $examManagement -notmatch 'completedAttemptCount' -or
        $examManagement -notmatch 'unscoredCompletedAttemptCount' -or
        $examManagement -notmatch 'pendingAnswerReviewCount' -or
        $examManagement -notmatch 'pendingScoreAppealCount' -or
        $examManagement -notmatch 'openRecheckAppealCount' -or
        $examManagement -notmatch '成绩复核申诉' -or
        $examApi -notmatch 'pendingScoreAppealCount' -or
        $examApi -notmatch 'pendingAnswerReviewCount' -or
        $examApi -notmatch 'unscoredCompletedAttemptCount' -or
        $examApi -notmatch 'openRecheckAppealCount') {
        Fail "ExamManagement must disable score publishing until scores are ready and recheck appeals are closed"
    }
    if ($examManagement -notmatch 'scoreReleaseBlockers' -or
        $examManagement -notmatch 'scoreReleaseBlockerText' -or
        $examManagement -notmatch 'scoreReleaseBlockerActionText' -or
        $examManagement -notmatch 'scoreReleaseReadinessLoading' -or
        $examManagement -notmatch 'scoreReleaseReadinessVisible' -or
        $examManagement -notmatch 'scoreReleaseReadinessDetailRows' -or
        $examManagement -notmatch 'scoreReleaseReadinessMetricRows' -or
        $examManagement -notmatch 'openScoreReleaseReadiness' -or
        $examManagement -notmatch 'openScoreReleaseBlockerResolution' -or
        $examManagement -notmatch 'canResolveScoreReleaseBlocker' -or
        $examManagement -notmatch 'getScoreReleaseReadiness' -or
        $examManagement -notmatch 'applyScoreReleaseReadiness' -or
        $examManagement -notmatch 'PENDING_REVIEW_ANSWERS' -or
        $examManagement -notmatch 'getScoreReleaseReadiness\(row\.id\)[\s\S]*publishExamScores\(row\.id\)' -or
        $examManagement -notmatch 'scoreReleaseReady' -or
        $examApi -notmatch 'ScoreReleaseReadiness' -or
        $examApi -notmatch 'getScoreReleaseReadiness' -or
        $examApi -notmatch 'scoreReleaseReady' -or
        $examApi -notmatch 'scoreReleaseBlockers') {
        Fail "ExamManagement must consume backend score release readiness and blocker fields"
    }
    $studentResults = Get-Content -LiteralPath "frontend\src\components\StudentResultsPanel.vue" -Raw
    $studentApi = Get-Content -LiteralPath "frontend\src\api\student.ts" -Raw
    $studentController = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\controller\StudentController.java" -Raw
    if ($studentApi -notmatch 'scoreVisible' -or
        $studentApi -notmatch 'scoreVisibility' -or
        $studentApi -notmatch 'PENDING_RECHECK' -or
        $studentApi -notmatch 'PENDING_SCORE' -or
        $studentApi -notmatch 'options\?: Array' -or
        $studentApi -notmatch 'optionContent' -or
        $studentApi -notmatch 'scoreRevokeReason' -or
        $studentApi -notmatch 'appealOpen' -or
        $studentApi -notmatch 'appealDeadlineAt' -or
        $studentApi -notmatch 'questionCount\?: number \| string \| null' -or
        $studentApi -notmatch 'answeredCount\?: number \| string \| null' -or
        $studentApi -notmatch 'unansweredCount\?: number \| string \| null') {
        Fail "frontend student API must expose score visibility fields"
    }
    if ($studentResults -notmatch 'score-release-cell' -or
        $studentResults -notmatch 'canViewResult' -or
        $studentResults -notmatch 'scoreVisibilityText' -or
        $studentResults -notmatch 'scoreVisibilityHint' -or
        $studentResults -notmatch 'PENDING_RECHECK' -or
        $studentResults -notmatch 'PENDING_SCORE' -or
        $studentResults -notmatch 'scoreRevokeReason' -or
        $studentResults -notmatch 'canSubmitAppeal' -or
        $studentResults -notmatch 'canSubmitAppealFor' -or
        $studentResults -notmatch 'hasActiveAppealOverlap' -or
        $studentResults -notmatch 'appealSubmitDisabledReason' -or
        $studentResults -notmatch 'appealWindowHint' -or
        $studentResults -notmatch 'appealDisabledReason' -or
        $studentResults -notmatch 'result-option-list' -or
        $studentResults -notmatch 'isCorrectValue\(option\.correct \?\? option\.isCorrect\)' -or
        $studentResults -notmatch 'appealHandlingResultText' -or
        $studentResults -notmatch 'recheckNote' -or
        $studentResults -notmatch 'answerStatsText' -or
        $studentResults -notmatch 'Answer stats' -or
        $studentResults -notmatch 'Number\(grade\.questionCount\)' -or
        $studentResults -notmatch '\$\{answered\}/\$\{questionTotal\} answered, \$\{unanswered\} unanswered') {
        Fail "StudentResultsPanel must explain score release visibility and block unreleased detail access"
    }
    if ($studentResults -notmatch 'lastStudentScoreAppealAudit' -or
        $studentResults -notmatch 'rememberStudentScoreAppealAudit' -or
        $studentResults -notmatch 'student-score-appeal-audit' -or
        $studentResults -notmatch 'copyLatestStudentScoreAppealAuditId' -or
        $studentResults -notmatch 'copyLatestStudentScoreAppealAuditLink' -or
        $studentResults -notmatch 'studentScoreAppealAuditText' -or
        $studentResults -notmatch "rememberStudentScoreAppealAudit\('Submit appeal', response\.data\.scoreAppealLogIds \|\| \[\]\)" -or
        $studentResults -notmatch 'copyScoreAppealAuditIdToClipboard\(ids\.join\('',''\)\)' -or
        $studentResults -notmatch 'copyScoreAppealAuditLinkToClipboard\(id\)') {
        Fail "StudentResultsPanel must surface copyable local score appeal audit evidence after submission"
    }
    if ($studentResults -notmatch 'student-appeal-audit-id-cell' -or
        $studentResults -notmatch 'scope\.row\.scoreAppealLogId' -or
        $studentResults -notmatch 'copyStudentAppealRowAuditId' -or
        $studentResults -notmatch 'copyStudentAppealRowAuditLink' -or
        $studentResults -notmatch 'Copy score appeal audit ID' -or
        $studentResults -notmatch 'Copy score appeal audit link') {
        Fail "StudentResultsPanel must expose copyable score appeal audit evidence in the appeal history table"
    }
    if ($studentController -notmatch 'appeals/\{id\}/logs' -or
        $studentController -notmatch 'listMyAppealLogs' -or
        $appealService -notmatch 'public List<Map<String, Object>> listMyAppealLogs\(Long id, AuthUser student\)' -or
        $appealService -notmatch 'requireStudentAppealAccess\(jt, id, student\)' -or
        $appealService -notmatch 'WHERE sa\.id = \? AND sa\.user_id = \?' -or
        $appealService -notmatch 'private List<Map<String, Object>> appealLogs\(JdbcTemplate jt, Long id\)') {
        Fail "Student score appeal logs must be exposed only for the owning student"
    }
    if ($studentApi -notmatch 'ScoreAppealLog' -or
        $studentApi -notmatch 'getMyScoreAppealLogs' -or
        $studentApi -notmatch '/api/student/appeals/\$\{id\}/logs' -or
        $studentResults -notmatch 'appealLogVisible' -or
        $studentResults -notmatch 'openStudentAppealLogs' -or
        $studentResults -notmatch 'getMyScoreAppealLogs\(row\.id\)' -or
        $studentResults -notmatch 'Score appeal logs' -or
        $studentResults -notmatch 'appealLogActionText' -or
        $studentResults -notmatch 'appealLogStatusText') {
        Fail "StudentResultsPanel must let students inspect their own score appeal lifecycle logs"
    }
    if ($studentController -notmatch 'appeals/\{id\}/evidence' -or
        $studentController -notmatch 'studentAppealEvidence\(id, user\)' -or
        $appealService -notmatch 'public Map<String, Object> studentAppealEvidence\(Long id, AuthUser student\)' -or
        $appealService -notmatch 'requireStudentAppealEvidenceVisible' -or
        $appealService -notmatch "sa\.handling_result = 'RECHECK_REQUIRED'" -or
        $appealService -notmatch 'Recheck evidence is only available after scores are released and all rechecks are closed') {
        Fail "Student score appeal evidence must be exposed only after closed recheck and released score visibility"
    }
    if ($studentApi -notmatch 'ScoreAppealEvidenceAnswer' -or
        $studentApi -notmatch 'ScoreAppealEvidence extends ScoreAppeal' -or
        $studentApi -notmatch 'getMyScoreAppealEvidence' -or
        $studentApi -notmatch '/api/student/appeals/\$\{id\}/evidence' -or
        $studentResults -notmatch 'appealEvidenceVisible' -or
        $studentResults -notmatch 'openStudentAppealEvidence' -or
        $studentResults -notmatch 'getMyScoreAppealEvidence\(row\.id\)' -or
        $studentResults -notmatch 'canViewAppealEvidence' -or
        $studentResults -notmatch 'appealEvidenceAnswerStatusText' -or
        $studentResults -notmatch 'Recheck Evidence' -or
        $studentResults -notmatch 'reviewScoreLogCount' -or
        $studentResults -notmatch 'openStudentAppealEvidence\(refreshedAppeal\)') {
        Fail "StudentResultsPanel must show closed recheck score evidence to the owning student"
    }
    if ($studentResults -notmatch 'useRoute' -or
        $studentResults -notmatch 'route\.query\.appealId' -or
        $studentResults -notmatch 'applyAppealRouteFocus' -or
        $studentResults -notmatch 'normalizedRouteAppealId' -or
        $studentResults -notmatch 'viewResult\(appeal\.attemptId\)' -or
        $studentResults -notmatch 'openStudentAppealLogs\(refreshedAppeal\)' -or
        $studentResults -notmatch 'focusedRouteAppealId') {
        Fail "StudentResultsPanel must deep-link score appeal notifications to the exact appeal logs"
    }
    if ($studentResults -notmatch 'route\.query\.attemptId' -or
        $studentResults -notmatch 'applyAttemptRouteFocus' -or
        $studentResults -notmatch 'normalizedRouteAttemptId' -or
        $studentResults -notmatch 'focusedRouteAttemptId' -or
        $studentResults -notmatch 'viewResult\(attemptId\)' -or
        $studentResults -notmatch 'normalizedRouteAppealId\(\)') {
        Fail "StudentResultsPanel must deep-link score release and revoke notifications to the exact attempt result"
    }
    if ($studentController -notmatch 'appeals/\{id\}/logs/export' -or
        $studentController -notmatch 'exportMyAppealLogs' -or
        $studentController -notmatch 'file\.toDownload\(\)' -or
        $appealService -notmatch 'public ExportFile exportMyAppealLogs\(Long id, AuthUser student\)' -or
        $appealService -notmatch 'return buildAppealLogsExport\(id, appeal, appealLogs\(jt, id\)\)' -or
        $appealService -notmatch 'private ExportFile buildAppealLogsExport') {
        Fail "Student score appeal log export must reuse the student ownership check and shared CSV builder"
    }
    if ($studentApi -notmatch 'downloadFile' -or
        $studentApi -notmatch 'exportMyScoreAppealLogs' -or
        $studentApi -notmatch '/api/student/appeals/\$\{id\}/logs/export' -or
        $studentResults -notmatch 'appealLogExporting' -or
        $studentResults -notmatch 'student-appeal-log-toolbar' -or
        $studentResults -notmatch 'exportStudentAppealLogs' -or
        $studentResults -notmatch 'exportMyScoreAppealLogs\(activeLogAppeal\.value\.id, activeLogAppeal\.value\.examName\)' -or
        $studentResults -notmatch 'Score appeal log export started') {
        Fail "StudentResultsPanel must let students export their own score appeal lifecycle logs"
    }
    $studentInsightApi = Get-Content -LiteralPath "frontend\src\api\insight.ts" -Raw
    $studentInsightPanel = Get-Content -LiteralPath "frontend\src\components\StudentInsight.vue" -Raw
    if ($studentInsightApi -notmatch 'questionCount\?: number \| string \| null' -or
        $studentInsightApi -notmatch 'answeredCount\?: number \| string \| null' -or
        $studentInsightApi -notmatch 'unansweredCount\?: number \| string \| null' -or
        $studentInsightPanel -notmatch 'answerStatsText' -or
        $studentInsightPanel -notmatch 'Answer stats' -or
        $studentInsightPanel -notmatch 'Number\(record\.questionCount\)' -or
        $studentInsightPanel -notmatch '\$\{answered\}/\$\{questionTotal\} answered, \$\{unanswered\} unanswered' -or
        $studentInsightPanel -notmatch 'Question Count' -or
        $studentInsightPanel -notmatch 'Answered Count' -or
        $studentInsightPanel -notmatch 'Unanswered Count') {
        Fail "StudentInsight must display and export submitted answer completeness statistics"
    }
    $aiApi = Get-Content -LiteralPath "frontend\src\api\ai.ts" -Raw
    $studentWrongBook = Get-Content -LiteralPath "frontend\src\components\StudentWrongBook.vue" -Raw
    if ($aiApi -notmatch 'questionId: number' -or
        $aiApi -notmatch 'examId: number' -or
        $studentApi -notmatch 'examId: number' -or
        $aiApi -match 'export interface WrongQuestionExplainPayload \{[\s\S]*stem\??: string' -or
        $aiApi -match 'export interface WrongQuestionExplainPayload \{[\s\S]*questionType\??: string' -or
        $aiApi -match 'export interface WrongQuestionExplainPayload \{[\s\S]*studentAnswer\??: string' -or
        $aiApi -match 'export interface WrongQuestionExplainPayload \{[\s\S]*correctAnswer\??: string' -or
        $aiApi -match 'export interface WrongQuestionExplainPayload \{[\s\S]*analysis\??: string' -or
        $aiApi -match 'export interface WrongQuestionExplainPayload \{[\s\S]*wrongCount\??: number' -or
        $aiApi -match 'export interface WrongQuestionExplainPayload \{[\s\S]*options\??:' -or
        $studentWrongBook -notmatch 'wrongQuestionKey' -or
        $studentWrongBook -notmatch 'questionId: item\.questionId,[\s\S]*examId: item\.examId' -or
        $studentWrongBook -match 'correctAnswer: item\.correctAnswer' -or
        $studentWrongBook -match 'analysis: item\.analysis') {
        Fail "Student wrong-book AI explanation must only submit questionId and examId and rely on the backend released wrong-answer snapshot"
    }
    if ($examService -notmatch 'accessStatus' -or
        $examService -notmatch 'canStart' -or
        $examService -notmatch 'secondsUntilStart' -or
        $examService -notmatch 'secondsUntilEnd' -or
        $examService -notmatch "WHEN e\.start_time IS NOT NULL AND e\.start_time > NOW\(\) THEN 'WAITING'" -or
        $examService -notmatch "WHEN e\.end_time IS NOT NULL AND e\.end_time <= NOW\(\) THEN 'CLOSED'") {
        Fail "ExamService.listStudentExams must expose server-side exam access status for student waiting-room gating"
    }
    if ($examApi -notmatch 'scoreVisible' -or
        $examApi -notmatch 'scoreVisibility' -or
        $examApi -notmatch 'PENDING_RECHECK' -or
        $examApi -notmatch 'PENDING_SCORE' -or
        $examApi -notmatch 'accessStatus' -or
        $examApi -notmatch 'rulesConfirmedAt\?: string \| null' -or
        $examApi -notmatch 'canStart\?: boolean \| number' -or
        $examApi -notmatch 'secondsUntilStart\?: number' -or
        $examApi -notmatch 'secondsUntilEnd\?: number \| null') {
        Fail "frontend exam API must expose student exam score visibility fields"
    }
    if ($examApi -notmatch 'interface ExamTakingQuestion' -or
        $examApi -notmatch 'interface ExamTakingOption' -or
        $examApi -notmatch 'questions: ExamTakingQuestion\[\]' -or
        $examApi -match "Omit<PaperQuestionInfo, 'analysis'>" -or
        $examApi -match "Omit<QuestionOption, 'correct'>") {
        Fail "frontend ExamDetail taking type must use a dedicated no-answer taking question contract"
    }
    if ($examList -notmatch 'canViewResult' -or
        $examList -notmatch 'scoreVisible' -or
        $examList -notmatch "scoreVisibility === 'RELEASED'" -or
        $examList -notmatch 'PENDING_RECHECK' -or
        $examList -notmatch 'exam\.status === 5' -or
        $examList -notmatch ':disabled="!canViewResult' -or
        $examList -notmatch 'canEnterExam' -or
        $examList -notmatch ':disabled="!canEnterExam\(scope\.row as StudentExamInfo\)"' -or
        $examList -notmatch 'examAccessHint' -or
        $examList -notmatch "accessStatus === 'READY'" -or
        $examList -notmatch "accessStatus === 'IN_PROGRESS'" -or
        $examList -notmatch 'Starts in \$\{formatDuration' -or
        $examList -notmatch 'ElMessageBox\.confirm' -or
        $examList -notmatch 'requiresRulesConfirmation' -or
        $examList -notmatch "route\.query\.notice === 'rules' && focusedAttemptId\.value === exam\.attemptId && !exam\.rulesConfirmedAt" -or
        $examList -notmatch '../utils/rulesConfirmationStorage' -or
        $examList -notmatch 'persistRulesConfirmation' -or
        $examList -notmatch 'Confirm and start') {
        Fail "ExamList must block student result access unless scores are released and finalized"
    }
    if ($examList -match 'smart_exam_rules_confirmed_' -or
        $examTaking -match 'smart_exam_rules_confirmed_') {
        Fail "Rules confirmation local storage key must stay centralized in rulesConfirmationStorage.ts"
    }
    $reviewApi = Get-Content -LiteralPath "frontend\src\api\review.ts" -Raw
    $studentApi = Get-Content -LiteralPath "frontend\src\api\student.ts" -Raw
    $reviewPanel = Get-Content -LiteralPath "frontend\src\components\ReviewPanel.vue" -Raw
    if ($reviewApi -notmatch 'handlingResult' -or
        $reviewApi -notmatch 'scoreAppealLogId\?: number \| null' -or
        $reviewApi -notmatch 'scoreAppealLogIds\?: number\[\]' -or
        $studentApi -notmatch 'scoreAppealLogId\?: number \| null' -or
        $studentApi -notmatch 'scoreAppealLogIds\?: number\[\]' -or
        $reviewApi -notmatch 'ScoreAppealLog' -or
        $reviewApi -notmatch 'listScoreAppealLogs' -or
        $reviewApi -notmatch 'exportScoreAppealLogs' -or
        $reviewApi -notmatch 'appeals/\$\{id\}/logs/export' -or
        $reviewApi -notmatch 'export function listScoreAppeals' -or
        $reviewApi -notmatch 'appealId\?: number \| string \| null' -or
        $reviewApi -notmatch "params\.set\('appealId', String\(appealId\)\)" -or
        $reviewApi -notmatch 'ScoreAppealRecheckReadiness' -or
        $reviewApi -notmatch 'ScoreAppealRecheckAnswer' -or
        $reviewApi -notmatch 'getScoreAppealRecheckReadiness' -or
        $reviewApi -notmatch 'recheck/readiness' -or
        $reviewApi -notmatch 'replyScoreAppeal\(id: number, payload' -or
        $reviewApi -notmatch 'closeScoreAppealRecheck') {
        Fail "frontend review API must submit structured appeal handling results"
    }
    if ($reviewApi -notmatch 'ReviewScoreLog' -or
        $reviewApi -notmatch 'reviewScoreLogIds\?: number\[\]' -or
        $reviewApi -notmatch 'listReviewScoreLogs' -or
        $reviewApi -notmatch 'exportReviewScoreLogs' -or
        $reviewApi -notmatch 'score-logs/export') {
        Fail "frontend review API must expose review score audit list and export"
    }
    if ($reviewApi -notmatch 'examId\?: number \| string' -or
        $reviewApi -notmatch 'ReviewTaskType' -or
        $reviewApi -notmatch 'recheckTaskCount' -or
        $reviewApi -notmatch 'recheckRequired' -or
        $reviewApi -notmatch 'getPendingReviews\(examId\?: number \| string \| null, reviewType\?: ReviewTaskType \| null\)' -or
        $reviewApi -notmatch "params\.set\('examId', String\(examId\)\)" -or
        $reviewApi -notmatch "params\.set\('reviewType', reviewType\)" -or
        $reviewApi -notmatch '/api/reviews/pending' -or
        $reviewApi -notmatch 'query \?') {
        Fail "frontend review API must support pending review exam filtering"
    }
    if ($reviewApi -notmatch 'ReviewProgress' -or
        $reviewApi -notmatch 'pendingAttemptCount' -or
        $reviewApi -notmatch 'pendingAnswerCount' -or
        $reviewApi -notmatch 'pendingRecheckAnswerCount' -or
        $reviewApi -notmatch 'progressPercent' -or
        $reviewApi -notmatch 'firstPendingAttemptId' -or
        $reviewApi -notmatch 'firstRecheckAttemptId' -or
        $reviewApi -notmatch 'blocksScoreRelease' -or
        $reviewApi -notmatch 'listReviewProgress\(examId\?: number \| string \| null\)' -or
        $reviewApi -notmatch '/api/reviews/progress') {
        Fail "frontend review API must expose exam-level review progress"
    }
    if ($reviewApi -notmatch 'questionCount\?: number \| string \| null' -or
        $reviewApi -notmatch 'answeredCount\?: number \| string \| null' -or
        $reviewApi -notmatch 'unansweredCount\?: number \| string \| null' -or
        $reviewPanel -notmatch 'reviewAnswerStatsText' -or
        $reviewPanel -notmatch 'Answer stats' -or
        $reviewPanel -notmatch 'Number\(row\.questionCount\)' -or
        $reviewPanel -notmatch '\$\{answered\}/\$\{questionTotal\} answered, \$\{unanswered\} unanswered') {
        Fail "ReviewPanel must display submitted answer statistics for pending reviews"
    }
    if ($reviewPanel -notmatch 'review-progress-card' -or
        $reviewPanel -notmatch 'Review progress' -or
        $reviewPanel -notmatch 'reviewProgress' -or
        $reviewPanel -notmatch 'progressLoading' -or
        $reviewPanel -notmatch 'loadReviewProgress' -or
        $reviewPanel -notmatch 'listReviewProgress' -or
        $reviewPanel -notmatch 'reviewProgressPercent' -or
        $reviewPanel -notmatch 'reviewBlocksRelease' -or
        $reviewPanel -notmatch 'firstPendingAttemptId' -or
        $reviewPanel -notmatch 'blocksScoreRelease' -or
        $reviewPanel -notmatch 'await loadReviewProgress\(\);[\s\S]*await loadPendingReviews\(\);') {
        Fail "ReviewPanel must show exam-level review progress and refresh it after review work"
    }
    if ($reviewPanel -notmatch 'useRoute, useRouter' -or
        $reviewPanel -notmatch 'focusedReviewExamId' -or
        $reviewPanel -notmatch 'routeReviewExamId' -or
        $reviewPanel -notmatch 'reviewExamId' -or
        $reviewPanel -notmatch 'focusReviewExam' -or
        $reviewPanel -notmatch 'clearReviewExamFilter' -or
        $reviewPanel -notmatch 'getPendingReviews\(focusedReviewExamId\.value, activeReviewTaskType\(\)\)' -or
        $reviewPanel -notmatch 'listReviewProgress\(focusedReviewExamId\.value\)' -or
        $reviewPanel -notmatch 'pending-review-toolbar' -or
        $reviewPanel -notmatch 'Clear filter') {
        Fail "ReviewPanel must deep-link and filter review progress/pending queues by exam"
    }
    if ($reviewPanel -notmatch 'reviewTaskTypeFilter' -or
        $reviewPanel -notmatch 'reviewTaskType' -or
        $reviewPanel -notmatch 'updateReviewTaskTypeFilter' -or
        $reviewPanel -notmatch 'applyReviewRouteFilters' -or
        $reviewPanel -notmatch 'normalizeReviewTaskType' -or
        $reviewPanel -notmatch 'activeReviewTaskType' -or
        $reviewPanel -notmatch 'isRecheckReview' -or
        $reviewPanel -notmatch 'pendingRecheckAnswerCount' -or
        $reviewPanel -notmatch 'preferredReviewAttemptId' -or
        $reviewPanel -notmatch 'firstRecheckAttemptId' -or
        $reviewPanel -notmatch 'Recheck' -or
        $reviewPanel -notmatch 'Standard') {
        Fail "ReviewPanel must identify and filter recheck review tasks"
    }
    if ($reviewPanel -notmatch 'appealHandlingResult' -or
        $reviewPanel -notmatch 'appealHandlingResultFilter' -or
        $reviewPanel -notmatch 'appealHandlingResultText' -or
        $reviewPanel -notmatch 'handlerName' -or
        $reviewPanel -notmatch 'canCloseRecheck' -or
        $reviewPanel -notmatch 'recheckCloseVisible' -or
        $reviewPanel -notmatch 'activeRecheckReadiness' -or
        $reviewPanel -notmatch 'getScoreAppealRecheckReadiness' -or
        $reviewPanel -notmatch 'submitCloseRecheck' -or
        $reviewPanel -notmatch 'isRecheckCloseAllowed' -or
        $reviewPanel -notmatch 'recheckCloseBlockerText' -or
        $reviewPanel -notmatch 'recheckAnswerStatusText' -or
        $reviewPanel -notmatch 'reviewScoreLogCount' -or
        $reviewPanel -notmatch 'closeScoreAppealRecheck' -or
        $reviewPanel -notmatch 'Recheck Review' -or
        $reviewPanel -notmatch 'openAppealLogs' -or
        $reviewPanel -notmatch 'appealLogActionText' -or
        $reviewPanel -notmatch 'RECHECK_OPEN' -or
        $reviewPanel -notmatch 'RECHECK_REQUIRED' -or
        $reviewPanel -notmatch 'ADJUSTED_OFFLINE' -or
        $reviewPanel -notmatch 'scoreAppealLogSuffix' -or
        $reviewPanel -notmatch 'appeal-log-id-cell' -or
        $reviewPanel -notmatch 'appeal-log-toolbar' -or
        $reviewPanel -notmatch 'appealLogExporting' -or
        $reviewPanel -notmatch 'exportAppealLogs' -or
        $reviewPanel -notmatch 'exportScoreAppealLogs' -or
        $reviewPanel -notmatch 'Copy score appeal audit ID' -or
        $reviewPanel -notmatch 'Copy score appeal audit link' -or
        $reviewPanel -notmatch 'copyScoreAppealAuditIdToClipboard' -or
        $reviewPanel -notmatch 'copyScoreAppealAuditLinkToClipboard') {
        Fail "ReviewPanel must let teachers choose structured appeal handling results"
    }
    if ($reviewPanel -notmatch 'lastScoreAppealAudit' -or
        $reviewPanel -notmatch 'rememberScoreAppealAudit' -or
        $reviewPanel -notmatch 'score-appeal-audit' -or
        $reviewPanel -notmatch 'copyLatestScoreAppealAuditId' -or
        $reviewPanel -notmatch 'copyLatestScoreAppealAuditLink' -or
        $reviewPanel -notmatch 'scoreAppealAuditText' -or
        $reviewPanel -notmatch "rememberScoreAppealAudit\('Handle appeal', response\.data\.scoreAppealLogIds \|\| \[\]\)" -or
        $reviewPanel -notmatch "rememberScoreAppealAudit\('Close recheck', response\.data\.scoreAppealLogIds \|\| \[\]\)" -or
        $reviewPanel -notmatch 'copyScoreAppealAuditIdToClipboard\(ids\.join\('',''\)\)' -or
        $reviewPanel -notmatch 'copyScoreAppealAuditLinkToClipboard\(id\)') {
        Fail "ReviewPanel must surface copyable local score appeal audit evidence after reply and recheck close"
    }
    if ($reviewPanel -notmatch 'scoreLogVisible' -or
        $reviewPanel -notmatch 'openScoreLogs' -or
        $reviewPanel -notmatch 'exportScoreLogs' -or
        $reviewPanel -notmatch 'Review Score Logs' -or
        $reviewPanel -notmatch 'scoreLogExporting' -or
        $reviewPanel -notmatch 'reviewScoreLogSuffix' -or
        $reviewPanel -notmatch 'review-score-log-id-cell' -or
        $reviewPanel -notmatch 'copyReviewScoreAuditIdToClipboard' -or
        $reviewPanel -notmatch 'copyReviewScoreAuditLinkToClipboard') {
        Fail "ReviewPanel must expose review score audit logs and export"
    }
    if ($reviewPanel -notmatch 'lastReviewScoreAudit' -or
        $reviewPanel -notmatch 'rememberReviewScoreAudit' -or
        $reviewPanel -notmatch 'review-score-audit' -or
        $reviewPanel -notmatch 'copyLatestReviewScoreAuditId' -or
        $reviewPanel -notmatch 'copyLatestReviewScoreAuditLink' -or
        $reviewPanel -notmatch 'reviewScoreAuditText' -or
        $reviewPanel -notmatch "rememberReviewScoreAudit\('Submit review', response\.data\.reviewScoreLogIds \|\| \[\]\)" -or
        $reviewPanel -notmatch 'copyReviewScoreAuditIdToClipboard\(ids\.join\('',''\)\)' -or
        $reviewPanel -notmatch 'copyReviewScoreAuditLinkToClipboard\(id\)') {
        Fail "ReviewPanel must surface copyable local review score audit evidence after submit"
    }
    $overviewService = Get-Content -LiteralPath "backend\src\main\java\com\smartexam\service\OverviewService.java" -Raw
    $teacherDashboard = Get-Content -LiteralPath "frontend\src\components\TeacherDashboard.vue" -Raw
    $appVue = Get-Content -LiteralPath "frontend\src\App.vue" -Raw
    if ($overviewService -notmatch 'pendingAppeals' -or
        $overviewService -notmatch 'recheckAppeals' -or
        $overviewService -notmatch 'scoreAppealCount' -or
        $overviewService -notmatch 'sa\.handling_result = \?' -or
        $overviewService -notmatch 'visibleStudentUserIds') {
        Fail "OverviewService must expose teacher score appeal workbench counts with teaching scope"
    }
    if ($teacherDashboard -notmatch 'pendingAppeals' -or
        $teacherDashboard -notmatch 'recheckAppeals' -or
        $teacherDashboard -notmatch 'Tickets' -or
        $teacherDashboard -notmatch 'appealStatus=0' -or
        $teacherDashboard -notmatch 'appealHandlingResult=RECHECK_REQUIRED') {
        Fail "TeacherDashboard must expose score appeal workbench entry and counts"
    }
    if ($appVue -notmatch 'targetUrl' -or
        $appVue -notmatch 'targetPath' -or
        $appVue -notmatch 'split\(/\[\?\#\]/' -or
        $appVue -notmatch 'route\.fullPath') {
        Fail "App navigation must preserve query strings while checking menu access by path"
    }
    if ($reviewPanel -notmatch 'useRoute' -or
        $reviewPanel -notmatch 'applyAppealRouteFilters' -or
        $reviewPanel -notmatch 'appealStatus' -or
        $reviewPanel -notmatch 'appealHandlingResult' -or
        $reviewPanel -notmatch "result === 'ALL'") {
        Fail "ReviewPanel must apply score appeal filters from route query"
    }
    if ($reviewPanel -notmatch 'route\.query\.appealId' -or
        $reviewPanel -notmatch 'focusedAppealId' -or
        $reviewPanel -notmatch 'routeAppealId' -or
        $reviewPanel -notmatch 'listScoreAppeals\(status, handlingResult, appealId\)' -or
        $reviewPanel -notmatch 'appealRowClassName' -or
        $reviewPanel -notmatch 'appeal-row-focused') {
        Fail "ReviewPanel must deep-link teacher score appeal notifications to exact appeal rows"
    }
    if ($reviewApi -notmatch 'examId\?: number \| string \| null' -or
        $reviewPanel -notmatch 'focusedAppealExamId' -or
        $reviewPanel -notmatch 'routeAppealExamId' -or
        $reviewPanel -notmatch 'clearAppealExamFilter' -or
        $reviewPanel -notmatch 'listScoreAppeals\(status, handlingResult, appealId, appealExamId\)' -or
        $reviewPanel -notmatch 'focusRecheckQueueForAppeal' -or
        $reviewPanel -notmatch 'openRecheckReview' -or
        $reviewPanel -notmatch "reviewTaskType: 'RECHECK'" -or
        $examManagement -notmatch 'canResolveScoreReleaseBlockers' -or
        $examManagement -notmatch 'openScoreReleaseResolution' -or
        $examManagement -notmatch 'appealExamId' -or
        $examManagement -notmatch "nextQuery\.reviewTaskType = 'RECHECK'") {
        Fail "Score release blockers must deep-link to scoped appeal and recheck review queues"
    }
    $attemptVerifier = Get-Content -LiteralPath "scripts\verify-attempt-resilience.ps1" -Raw
    $examMetricsSmoke = Get-Content -LiteralPath "scripts\check-exam-metrics-smoke.ps1" -Raw
    $acceptanceSummary = Get-Content -LiteralPath "scripts\write-acceptance-summary.ps1" -Raw
    $nightlyAcceptance = Get-Content -LiteralPath ".github\workflows\nightly-acceptance.yml" -Raw
    $examMetricsSmokeDoc = Get-Content -LiteralPath "docs\rebuild\49-exam-metrics-smoke.md" -Raw
    $structuredAcceptanceDoc = Get-Content -LiteralPath "docs\rebuild\50-structured-acceptance-artifacts.md" -Raw
    $metricsEarlyFailureDoc = Get-Content -LiteralPath "docs\rebuild\408-metrics-smoke-early-failure-artifact.md" -Raw
    if ($attemptVerifier -notmatch 'SCORE_REVOKED' -or $attemptVerifier -notmatch 'relatedType=EXAM_ATTEMPT&relatedId=\$AttemptId' -or $attemptVerifier -notmatch 'revokeReason') {
        Fail "attempt resilience verifier must check score revoke notifications by related attempt and revoke reason"
    }
    if ($attemptVerifier -notmatch '/api/exams/\$ExamId/scores/export' -or $attemptVerifier -notmatch 'Scores have not been published') {
        Fail "attempt resilience verifier must check score export is blocked before publish and after revoke"
    }
    if ($attemptVerifier -notmatch 'first submit did not return submitTime' -or
        $attemptVerifier -notmatch 'replayed response submitTime mismatch') {
        Fail "attempt resilience verifier must check first submit and replay submitTime consistency"
    }
    if ($attemptVerifier -notmatch 'token mismatch replay did not report submitTokenMismatch' -or
        $attemptVerifier -notmatch 'payload mismatch replay did not report submitPayloadMismatch' -or
        $attemptVerifier -notmatch 'verify-submit-token-mismatch' -or
        $attemptVerifier -notmatch 'payload-mismatch-' -or
        $examMetricsSmoke -notmatch 'RequireSubmitReplayMismatchOutcomes' -or
        $examMetricsSmoke -notmatch 'submitExam:replay_payload_mismatch' -or
        $examMetricsSmoke -notmatch 'submitExam:replay_token_mismatch' -or
        $nightlyAcceptance -notmatch 'RequireSubmitReplayMismatchOutcomes' -or
        $nightlyAcceptance -notmatch '\$metricsArgs' -or
        $examMetricsSmokeDoc -notmatch 'RequireSubmitReplayMismatchOutcomes' -or
        $examMetricsSmokeDoc -notmatch 'submitExam:replay_payload_mismatch' -or
        $examMetricsSmokeDoc -notmatch 'submitExam:replay_token_mismatch') {
        Fail "submit replay acceptance must generate and require payload and token mismatch operation metrics"
    }
    if ($examMetricsSmoke -notmatch 'function New-MetricsResult' -or
        $examMetricsSmoke -notmatch 'missingOperations' -or
        $examMetricsSmoke -notmatch 'missingOperationOutcomes' -or
        $examMetricsSmoke -notmatch 'invalidRequiredOperationOutcomes' -or
        $examMetricsSmoke -notmatch 'failureReason' -or
        $examMetricsSmoke -notmatch 'Write-JsonResult -Result \(New-MetricsResult[\s\S]*-Success \$false' -or
        $acceptanceSummary -notmatch 'Missing Metrics Requirement' -or
        $acceptanceSummary -notmatch 'failureReason' -or
        $acceptanceSummary -notmatch 'missingOperationOutcomes' -or
        $examMetricsSmokeDoc -notmatch '缺少必需 operation/outcome 时也会先写出失败结果' -or
        $structuredAcceptanceDoc -notmatch 'missingOperations' -or
        $structuredAcceptanceDoc -notmatch 'missingOperationOutcomes' -or
        $structuredAcceptanceDoc -notmatch 'failureReason') {
        Fail "exam metrics smoke failures must write structured artifacts and nightly summary diagnostics"
    }
    if ($examMetricsSmoke -notmatch 'function Fail-WithMetricsResult' -or
        $examMetricsSmoke -notmatch 'PrometheusFile does not exist' -or
        $examMetricsSmoke -notmatch 'GET /actuator/prometheus failed' -or
        $examMetricsSmoke -notmatch '/actuator/prometheus did not return Prometheus text exposition' -or
        $examMetricsSmokeDoc -notmatch 'ResultFile' -or
        $examMetricsSmokeDoc -notmatch 'Prometheus' -or
        $examMetricsSmokeDoc -notmatch 'operation/outcome' -or
        $metricsEarlyFailureDoc -notmatch 'Fail-WithMetricsResult' -or
        $metricsEarlyFailureDoc -notmatch 'PrometheusFile' -or
        $metricsEarlyFailureDoc -notmatch 'missing file' -or
        $metricsEarlyFailureDoc -notmatch 'text exposition') {
        Fail "exam metrics smoke early failures must also write structured artifacts"
    }
    if ($attemptVerifier -notmatch 'monitor force-submit did not report forcedSubmitted=true' -or
        $attemptVerifier -notmatch 'monitor force-submit replay submitTime mismatch' -or
        $attemptVerifier -notmatch 'monitor force-submit did not return questionCount') {
        Fail "attempt resilience verifier must check monitor force-submit flags and response statistics"
    }
    if ($attemptVerifier -notmatch 'Assert-StudentExamAttemptNotificationLink' -or
        $attemptVerifier -notmatch '"/student/exams\?attemptId=\$ExpectedAttemptId"' -or
        $attemptVerifier -notmatch 'MONITOR_WARNING' -or
        $attemptVerifier -notmatch 'MONITOR_FORCE_SUBMIT' -or
        $attemptVerifier -notmatch 'relatedType=EXAM_ATTEMPT&relatedId=\$AttemptId' -or
        $attemptVerifier -notmatch 'forceSubmitNote') {
        Fail "attempt resilience verifier must check monitor action notification exact attempt links"
    }
    Write-Step "notification relation source smoke PASS"
}

function Test-BackendCompile {
    if ($SkipBackendCompile) {
        Write-Step "Skipping backend compile"
        return
    }
    $maven = Get-Command mvn -ErrorAction SilentlyContinue
    if ($maven) {
        Invoke-NativeChecked -FileName "mvn" -Arguments @("-B", "-DskipTests", "compile") -WorkingDirectory "backend"
        return
    }

    $javac = Get-Command javac -ErrorAction SilentlyContinue
    if (-not $javac) {
        Fail "Maven and javac are both unavailable"
    }
    Write-Step "Maven not found; running rough javac syntax/type filter"
    $sources = Get-ChildItem backend/src/main/java -Recurse -Filter *.java | ForEach-Object { $_.FullName }
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    try {
        $rawOutput = javac -proc:none $sources 2>&1
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $output = $rawOutput |
        Select-String -Pattern "unclosed|illegal start|';' expected|reached end|not a statement|非法|未结束|需要|variable .* already|cannot find symbol|method .* cannot be applied|incompatible types"
    if ($output) {
        $output | ForEach-Object { Write-Host $_ }
        Fail "rough backend javac filter found errors"
    }
    Write-Step "rough backend javac filter PASS"
}

Test-PowerShellScripts
Test-WorkflowFiles
Test-NotificationRelationSource
Write-Step "check-java-source-hygiene.ps1"
& (Join-Path (Get-Location) "scripts\check-java-source-hygiene.ps1") -SourceRoot "backend\src\main\java"
Write-Step "check-frontend-source-hygiene.ps1"
& (Join-Path (Get-Location) "scripts\check-frontend-source-hygiene.ps1") -SourceRoot "frontend\src"

if ($SkipDockerConfig) {
    Write-Step "check-deploy-config.ps1 -UseExample -SkipDockerConfig"
    & (Join-Path (Get-Location) "scripts\check-deploy-config.ps1") -UseExample -SkipDockerConfig
} else {
    Write-Step "check-deploy-config.ps1 -UseExample"
    & (Join-Path (Get-Location) "scripts\check-deploy-config.ps1") -UseExample
}

if (-not $SkipFrontendBuild) {
    Invoke-NativeChecked -FileName "npm" -Arguments @("run", "build") -WorkingDirectory "frontend"
} else {
    Write-Step "Skipping frontend build"
}

Test-BackendCompile

if (-not $SkipGitDiffCheck -and (Get-Command git -ErrorAction SilentlyContinue)) {
    Invoke-NativeChecked -FileName "git" -Arguments @("diff", "--check")
}

Write-Step "PASS all local quality gates completed"
