param(
    [string]$BaseUrl = "http://127.0.0.1:8080",
    [long]$AttemptId,
    [long]$ExamId,
    [string]$StudentToken = "",
    [string]$Username = "",
    [string]$Password = "",
    [string]$AdminToken = "",
    [string]$AdminUsername = "",
    [string]$AdminPassword = "",
    [string]$DiscoveryKeyword = "",
    [string]$AnswersJson = "",
    [switch]$Submit,
    [switch]$OmitLastAnswer,
    [switch]$CheckForgedQuestionRejection,
    [switch]$ExpectUnreleasedStudentInsightsHidden,
    [switch]$CheckScoreReleaseVisibilityCycle,
    [switch]$CheckMonitorEventDedup,
    [switch]$CheckMonitorActionForceSubmitBinding,
    [switch]$CheckMonitorForceSubmitTransaction,
    [switch]$CheckMonitorWarnNotification,
    [switch]$ExpectWriteBack
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Write-Step {
    param([string]$Message)
    Write-Host "[verify] $Message"
}

function Fail {
    param([string]$Message)
    throw "Verification failed: $Message"
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        Fail $Message
    }
}

function Optional-Bool {
    param($Object, [string]$Name)
    if ($null -eq $Object) {
        return $false
    }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property) {
        return $false
    }
    return [bool]$property.Value
}

function Assert-StudentExamAttemptNotificationLink {
    param($Notification, [string]$Type, [long]$ExpectedAttemptId)
    Assert-True ("$($Notification.type)" -eq $Type) "$Type notification type mismatch"
    Assert-True ("$($Notification.relatedType)" -eq "EXAM_ATTEMPT") "$Type notification relatedType was not EXAM_ATTEMPT"
    Assert-True ("$($Notification.relatedId)" -eq "$ExpectedAttemptId") "$Type notification relatedId did not match attempt $ExpectedAttemptId"
    Assert-True ("$($Notification.link)" -eq "/student/exams?attemptId=$ExpectedAttemptId") "$Type notification link did not target attempt $ExpectedAttemptId"
}

function Invoke-ExamApi {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = "",
        $Body = $null
    )
    $uri = ($BaseUrl.TrimEnd("/") + $Path)
    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $params["Body"] = ($Body | ConvertTo-Json -Depth 30)
    }
    try {
        $response = Invoke-RestMethod @params
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        throw "HTTP $Method $Path failed: $detail"
    }
    if ($null -eq $response) {
        Fail "HTTP $Method $Path returned an empty response"
    }
    if ($response.PSObject.Properties.Name -contains "success") {
        Assert-True ([bool]$response.success) "API $Path returned success=false: $($response.message)"
    }
    return $response
}

function Invoke-ExamApiExpectFailure {
    param(
        [string]$Method,
        [string]$Path,
        [string]$Token = "",
        $Body = $null,
        [string]$ExpectedMessage = ""
    )
    $uri = ($BaseUrl.TrimEnd("/") + $Path)
    $headers = @{}
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $headers
        ContentType = "application/json"
    }
    if ($null -ne $Body) {
        $params["Body"] = ($Body | ConvertTo-Json -Depth 30)
    }
    $response = $null
    try {
        $response = Invoke-RestMethod @params
    } catch {
        $detail = $_.Exception.Message
        if ($_.ErrorDetails -and $_.ErrorDetails.Message) {
            $detail = $_.ErrorDetails.Message
        }
        if ($ExpectedMessage) {
            Assert-True ($detail -like "*$ExpectedMessage*") "failure detail did not include '$ExpectedMessage': $detail"
        }
        return @{ failed = $true; detail = $detail }
    }
    if ($response.PSObject.Properties.Name -contains "success" -and -not [bool]$response.success) {
        if ($ExpectedMessage) {
            Assert-True ("$($response.message)" -like "*$ExpectedMessage*") "failure message did not include '$ExpectedMessage': $($response.message)"
        }
        return $response
    }
    Fail "HTTP $Method $Path was expected to fail but succeeded"
}

function Login-AndGetToken {
    param([string]$LoginUsername, [string]$LoginPassword)
    $payload = @{
        username = $LoginUsername
        password = $LoginPassword
    }
    $response = Invoke-ExamApi -Method "POST" -Path "/api/auth/login" -Body $payload
    Assert-True ($response.data.token -ne $null -and "$($response.data.token)" -ne "") "Login did not return a token"
    return "$($response.data.token)"
}

if ($Submit -and $CheckMonitorForceSubmitTransaction) {
    Fail "CheckMonitorForceSubmitTransaction force-submits the attempt and cannot be combined with Submit"
}

function Build-AnswerMap {
    param($Questions)
    if ($AnswersJson -and $AnswersJson.Trim()) {
        $custom = $AnswersJson | ConvertFrom-Json
        $provided = [ordered]@{}
        foreach ($property in $custom.PSObject.Properties) {
            $provided["$($property.Name)"] = "$($property.Value)"
        }
        return $provided
    }
    $answers = [ordered]@{}
    foreach ($question in $Questions) {
        $qid = $null
        if ($question.PSObject.Properties.Name -contains "questionId") {
            $qid = $question.questionId
        } elseif ($question.PSObject.Properties.Name -contains "id") {
            $qid = $question.id
        }
        if ($null -eq $qid) {
            continue
        }
        $value = ""
        if (($question.PSObject.Properties.Name -contains "questionType") -and
            @("SINGLE_CHOICE", "MULTIPLE_CHOICE", "TRUE_FALSE") -contains "$($question.questionType)" -and
            ($question.PSObject.Properties.Name -contains "options") -and
            $question.options -and $question.options.Count -gt 0) {
            $first = $question.options[0]
            if ($first.PSObject.Properties.Name -contains "optionLabel") {
                $value = "$($first.optionLabel)"
            } elseif ($first.PSObject.Properties.Name -contains "label") {
                $value = "$($first.label)"
            }
        } elseif (($question.PSObject.Properties.Name -contains "questionType") -and "$($question.questionType)" -eq "FILL_BLANK") {
            $value = "verify"
        } elseif (($question.PSObject.Properties.Name -contains "questionType") -and "$($question.questionType)" -eq "SUBJECTIVE") {
            $value = "resilience verification answer"
        }
        $answers["$qid"] = $value
    }
    return $answers
}

function Discover-AttemptId {
    param([string]$Token)
    $keyword = $DiscoveryKeyword
    if (-not $keyword -and $Username) {
        $keyword = $Username
    }
    $path = "/api/exams/attempt-resilience/candidates?openOnly=true&page=1&size=10"
    if ($keyword) {
        $path = "$path&keyword=$([uri]::EscapeDataString($keyword))"
    }
    $response = Invoke-ExamApi -Method "GET" -Path $path -Token $Token
    Assert-True ($response.data -ne $null) "candidate discovery returned no data"
    Assert-True ($response.data.list -ne $null -and $response.data.list.Count -gt 0) "no open attempt resilience candidates found"
    $candidate = $response.data.list[0]
    Assert-True ($candidate.attemptId -ne $null) "candidate did not include attemptId"
    Write-Step "Discovered attempt $($candidate.attemptId) for student $($candidate.studentUsername), exam $($candidate.examName)"
    if ($Username -and "$($candidate.studentUsername)" -ne $Username) {
        Write-Step "Warning: discovered student username differs from provided Username; start may fail unless StudentToken belongs to the candidate"
    }
    return [long]$candidate.attemptId
}

if (-not $AdminToken -and $AdminUsername -and $AdminPassword) {
    Write-Step "Logging in as admin $AdminUsername"
    $AdminToken = Login-AndGetToken -LoginUsername $AdminUsername -LoginPassword $AdminPassword
}

if ($AttemptId -le 0) {
    Assert-True ($AdminToken -ne "") "AttemptId is required unless AdminToken or AdminUsername/AdminPassword is provided for discovery"
    Write-Step "Discovering an open attempt resilience candidate"
    $AttemptId = Discover-AttemptId -Token $AdminToken
}

if (-not $StudentToken) {
    Assert-True ($Username -and $Password) "Provide StudentToken or Username/Password"
    Write-Step "Logging in as student $Username"
    $StudentToken = Login-AndGetToken -LoginUsername $Username -LoginPassword $Password
}

Write-Step "Starting attempt $AttemptId"
$start = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt/$AttemptId/start" -Token $StudentToken
Assert-True ($start.data -ne $null) "start response has no data"
Assert-True ($start.data.questions -ne $null -and $start.data.questions.Count -gt 0) "start response has no questions"

$answers = Build-AnswerMap -Questions $start.data.questions
Assert-True ($answers.Count -gt 0) "no answers could be prepared from the exam questions"

$clientDraftId = "verify-draft-$([guid]::NewGuid().ToString("N"))"
$revision = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$answersText = ($answers | ConvertTo-Json -Depth 30 -Compress)

Write-Step "Saving draft revision $revision"
$saveBody = @{
    answers = $answersText
    clientDraftId = $clientDraftId
    revision = $revision
}
$save = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt/$AttemptId/save" -Token $StudentToken -Body $saveBody
Assert-True ([bool]$save.data.saved) "draft was not saved: $($save.data.reason)"
Assert-True ([int64]$save.data.serverRevision -ge $revision) "server revision is older than the submitted revision"
Assert-True ("$($save.data.clientDraftId)" -eq $clientDraftId) "clientDraftId was not preserved"
if ($ExpectWriteBack) {
    Assert-True ([bool]$save.data.writeBack) "write-back mode was expected but save response did not report writeBack=true"
    Assert-True ("$($save.data.draftSource)" -eq "REDIS") "write-back mode was expected but draftSource is $($save.data.draftSource)"
}

Write-Step "Checking heartbeat recovery fields"
$heartbeat = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt/$AttemptId/heartbeat" -Token $StudentToken
Assert-True ($heartbeat.data.remainingSeconds -ne $null) "heartbeat did not return remainingSeconds"
Assert-True ($heartbeat.data.draftRevision -ne $null) "heartbeat did not return draftRevision"

if ($AdminToken) {
    Write-Step "Checking admin draft cache status contract"
    $cacheStatus = Invoke-ExamApi -Method "GET" -Path "/api/exams/draft-cache/status" -Token $AdminToken
    Assert-True ($cacheStatus.data.alertLevel -ne $null) "draft cache status did not return alertLevel"
    Assert-True ($cacheStatus.data.lastFlushAtEpochMillis -ne $null) "draft cache status did not return lastFlushAtEpochMillis"
    Assert-True ($cacheStatus.data.dirtyHighThreshold -ne $null) "draft cache status did not return dirtyHighThreshold"
}

if ($CheckMonitorEventDedup) {
    $monitorEventId = "verify-monitor-$([guid]::NewGuid().ToString("N"))"
    $clientEventTime = [DateTimeOffset]::UtcNow.ToString("o")
    Write-Step "Checking monitor event deduplication for clientEventId $monitorEventId"
    $monitor = Invoke-ExamApi -Method "POST" -Path "/api/monitor/cheat-events/batch" -Token $StudentToken -Body @{
        events = @(
            @{
                attemptId = $AttemptId
                eventType = "window_blur"
                extraInfo = "verify duplicate first"
                clientEventId = $monitorEventId
                clientEventTime = $clientEventTime
            },
            @{
                attemptId = $AttemptId
                eventType = "WINDOW_BLUR"
                extraInfo = "verify duplicate retry"
                clientEventId = $monitorEventId
                clientEventTime = $clientEventTime
            }
        )
    }
    Assert-True ([int]$monitor.data.accepted -eq 1) "duplicate monitor batch accepted count was $($monitor.data.accepted), expected 1"
    Assert-True ([int]$monitor.data.duplicates -eq 1) "duplicate monitor batch duplicates count was $($monitor.data.duplicates), expected 1"

    Write-Step "Checking unsupported monitor event rejection"
    Invoke-ExamApiExpectFailure -Method "POST" -Path "/api/monitor/cheat-events/batch" -Token $StudentToken -Body @{
        events = @(
            @{
                attemptId = $AttemptId
                eventType = "SCREEN_RECORDING"
                clientEventId = "verify-monitor-$([guid]::NewGuid().ToString("N"))"
                clientEventTime = [DateTimeOffset]::UtcNow.ToString("o")
            }
        )
    } -ExpectedMessage "Unsupported monitor event type" | Out-Null
}

if ($CheckMonitorActionForceSubmitBinding) {
    Assert-True ($AdminToken -ne "") "AdminToken is required for monitor action force-submit binding check"
    Assert-True ($ExamId -gt 0) "ExamId is required for monitor action force-submit binding check"

    Write-Step "Checking FORCE_SUBMIT monitor action requires a real forced submission"
    $sessionsResponse = Invoke-ExamApi -Method "GET" -Path "/api/monitor/exams/$ExamId/sessions" -Token $AdminToken
    $matchingSessions = @($sessionsResponse.data | Where-Object { "$($_.attemptId)" -eq "$AttemptId" })
    Assert-True ($matchingSessions.Count -gt 0) "monitor session was not found for attempt $AttemptId"
    $sessionId = $matchingSessions[0].id
    Assert-True ($sessionId -ne $null) "monitor session did not include id"
    Invoke-ExamApiExpectFailure -Method "POST" -Path "/api/monitor/sessions/$sessionId/actions" -Token $AdminToken -Body @{
        actionType = "FORCE_SUBMIT"
        note = "verify direct force-submit action should be rejected before real force submission"
    } -ExpectedMessage "requires a completed forced submission" | Out-Null
}

if ($CheckMonitorWarnNotification) {
    Assert-True ($AdminToken -ne "") "AdminToken is required for monitor warning notification check"
    Assert-True ($ExamId -gt 0) "ExamId is required for monitor warning notification check"

    $warnNote = "verify monitor warning notification $([guid]::NewGuid().ToString("N"))"
    Write-Step "Checking monitor warning notification delivery"
    $sessionsResponse = Invoke-ExamApi -Method "GET" -Path "/api/monitor/exams/$ExamId/sessions" -Token $AdminToken
    $matchingSessions = @($sessionsResponse.data | Where-Object { "$($_.attemptId)" -eq "$AttemptId" })
    Assert-True ($matchingSessions.Count -gt 0) "monitor session was not found for attempt $AttemptId"
    $sessionId = $matchingSessions[0].id
    Assert-True ($sessionId -ne $null) "monitor session did not include id"
    $warnAction = Invoke-ExamApi -Method "POST" -Path "/api/monitor/sessions/$sessionId/actions" -Token $AdminToken -Body @{
        actionType = "WARN"
        note = $warnNote
    }
    Assert-True ("$($warnAction.data.actionType)" -eq "WARN") "monitor warning action did not return WARN"
    Assert-True ([bool]$warnAction.data.notificationSent) "monitor warning action did not report notificationSent=true"

    $notifications = Invoke-ExamApi -Method "GET" -Path "/api/notifications/my?page=1&size=20" -Token $StudentToken
    $matches = @($notifications.data.list | Where-Object {
        "$($_.type)" -eq "MONITOR_WARNING" -and "$($_.content)" -eq $warnNote
    })
    Assert-True ($matches.Count -gt 0) "student notification list did not include the monitor warning"
    Assert-StudentExamAttemptNotificationLink -Notification $matches[0] -Type "MONITOR_WARNING" -ExpectedAttemptId $AttemptId
}

if ($CheckMonitorForceSubmitTransaction) {
    Assert-True ($AdminToken -ne "") "AdminToken is required for monitor force-submit transaction check"
    Assert-True ($ExamId -gt 0) "ExamId is required for monitor force-submit transaction check"

    Write-Step "Checking monitor force-submit transaction endpoint"
    $sessionsResponse = Invoke-ExamApi -Method "GET" -Path "/api/monitor/exams/$ExamId/sessions" -Token $AdminToken
    $matchingSessions = @($sessionsResponse.data | Where-Object { "$($_.attemptId)" -eq "$AttemptId" })
    Assert-True ($matchingSessions.Count -gt 0) "monitor session was not found for attempt $AttemptId"
    $sessionId = $matchingSessions[0].id
    Assert-True ($sessionId -ne $null) "monitor session did not include id"

    $forceSubmitNote = "verify transactional monitor force submit $([guid]::NewGuid().ToString("N"))"
    $forceResponse = Invoke-ExamApi -Method "POST" -Path "/api/monitor/sessions/$sessionId/force-submit" -Token $AdminToken -Body @{
        note = $forceSubmitNote
    }
    Assert-True ("$($forceResponse.data.submit.submitType)" -eq "FORCED") "monitor force-submit did not produce submitType=FORCED"
    Assert-True ([bool]$forceResponse.data.submit.forcedSubmitted) "monitor force-submit did not report forcedSubmitted=true"
    Assert-True ([bool]$forceResponse.data.submit.submitted) "monitor force-submit did not report submitted=true"
    Assert-True ($forceResponse.data.submit.submitTime -ne $null) "monitor force-submit did not return submitTime"
    Assert-True ($forceResponse.data.submit.questionCount -ne $null) "monitor force-submit did not return questionCount"
    Assert-True ($forceResponse.data.submit.answeredCount -ne $null) "monitor force-submit did not return answeredCount"
    Assert-True ($forceResponse.data.submit.unansweredCount -ne $null) "monitor force-submit did not return unansweredCount"
    Assert-True ("$($forceResponse.data.action.actionType)" -eq "FORCE_SUBMIT") "monitor force-submit did not record FORCE_SUBMIT action"
    Assert-True (-not [bool]$forceResponse.data.actionAlreadyRecorded) "first monitor force-submit unexpectedly reused an existing action"
    Assert-True ([bool]$forceResponse.data.notificationSent) "first monitor force-submit did not report notificationSent=true"

    $forceNotifications = Invoke-ExamApi -Method "GET" -Path "/api/notifications/my?page=1&size=50&relatedType=EXAM_ATTEMPT&relatedId=$AttemptId" -Token $StudentToken
    $forceMatches = @($forceNotifications.data.list | Where-Object {
        "$($_.type)" -eq "MONITOR_FORCE_SUBMIT" -and "$($_.content)" -eq $forceSubmitNote
    })
    Assert-True ($forceMatches.Count -gt 0) "student notification list did not include the monitor force-submit for attempt $AttemptId"
    Assert-StudentExamAttemptNotificationLink -Notification $forceMatches[0] -Type "MONITOR_FORCE_SUBMIT" -ExpectedAttemptId $AttemptId

    Write-Step "Checking monitor force-submit transaction idempotent action replay"
    $replayResponse = Invoke-ExamApi -Method "POST" -Path "/api/monitor/sessions/$sessionId/force-submit" -Token $AdminToken -Body @{
        note = "verify transactional monitor force submit replay"
    }
    Assert-True ("$($replayResponse.data.submit.submitType)" -eq "FORCED") "monitor force-submit replay did not return submitType=FORCED"
    Assert-True ([bool]$replayResponse.data.submit.forcedSubmitted) "monitor force-submit replay did not report forcedSubmitted=true"
    Assert-True ([bool]$replayResponse.data.submit.submitted) "monitor force-submit replay did not report submitted=true"
    Assert-True ("$($replayResponse.data.submit.submitTime)" -eq "$($forceResponse.data.submit.submitTime)") "monitor force-submit replay submitTime mismatch"
    Assert-True ("$($replayResponse.data.submit.questionCount)" -eq "$($forceResponse.data.submit.questionCount)") "monitor force-submit replay questionCount mismatch"
    Assert-True ("$($replayResponse.data.action.actionType)" -eq "FORCE_SUBMIT") "monitor force-submit replay did not return FORCE_SUBMIT action"
    Assert-True ([bool]$replayResponse.data.actionAlreadyRecorded) "monitor force-submit replay did not report actionAlreadyRecorded=true"
    Assert-True (-not [bool]$replayResponse.data.notificationSent) "monitor force-submit replay unexpectedly sent another notification"

    Write-Step "PASS monitor force-submit transaction check completed"
    exit 0
}

if (-not $Submit) {
    Write-Step "PASS non-destructive checks completed. Re-run with -Submit on a disposable attempt to verify submit replay."
    exit 0
}

$submitToken = "verify-submit-$([guid]::NewGuid().ToString("N"))"
if ($CheckForgedQuestionRejection) {
    $forgedAnswers = [ordered]@{}
    foreach ($key in $answers.Keys) {
        $forgedAnswers["$key"] = $answers[$key]
    }
    $forgedAnswers["999999999999"] = "forged-answer"
    Write-Step "Checking forged question rejection before the real submit"
    Invoke-ExamApiExpectFailure -Method "POST" -Path "/api/exams/attempt/$AttemptId/submit" -Token $StudentToken -Body @{
        answers = $forgedAnswers
        submitToken = "verify-forged-$([guid]::NewGuid().ToString("N"))"
    } -ExpectedMessage "does not belong to this attempt" | Out-Null
}

if ($OmitLastAnswer -and $answers.Count -gt 0) {
    $keys = @($answers.Keys)
    $removed = "$($keys[$keys.Count - 1])"
    Write-Step "Omitting answer for question $removed to verify unanswered accounting"
    $answers.Remove($removed)
}

$submitBody = @{
    answers = $answers
    submitToken = $submitToken
}

Write-Step "Submitting attempt $AttemptId with token $submitToken"
$firstSubmit = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt/$AttemptId/submit" -Token $StudentToken -Body $submitBody
Assert-True ([bool]$firstSubmit.data.success) "first submit did not report success"
Assert-True (-not [bool]$firstSubmit.data.alreadySubmitted) "first submit unexpectedly reported alreadySubmitted"
Assert-True ("$($firstSubmit.data.submitToken)" -eq $submitToken) "first submit did not echo submitToken"
Assert-True ($firstSubmit.data.submitPayloadHash -ne $null) "first submit did not return submitPayloadHash"
Assert-True ($firstSubmit.data.submitTime -ne $null) "first submit did not return submitTime"
Assert-True ($firstSubmit.data.PSObject.Properties.Name -notcontains "score") "first submit leaked score before release"
Assert-True (-not [bool]$firstSubmit.data.scoreVisible) "first submit should mark scoreVisible=false before release"
Assert-True ($firstSubmit.data.questionCount -ne $null) "first submit did not return questionCount"
Assert-True ($firstSubmit.data.answeredCount -ne $null) "first submit did not return answeredCount"
Assert-True ($firstSubmit.data.unansweredCount -ne $null) "first submit did not return unansweredCount"
if ($OmitLastAnswer) {
    Assert-True ([int]$firstSubmit.data.unansweredCount -ge 1) "omitted answer did not increase unansweredCount"
} else {
    Assert-True ([int]$firstSubmit.data.unansweredCount -eq 0) "complete answer set unexpectedly reported unansweredCount=$($firstSubmit.data.unansweredCount)"
}
if ($ExpectWriteBack) {
    Assert-True ([bool]$firstSubmit.data.draftFlushedBeforeSubmit) "write-back mode expected draftFlushedBeforeSubmit=true"
}

Write-Step "Repeating submit with the same token and payload"
$secondSubmit = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt/$AttemptId/submit" -Token $StudentToken -Body $submitBody
Assert-True ([bool]$secondSubmit.data.success) "second submit did not report success"
Assert-True ([bool]$secondSubmit.data.alreadySubmitted) "second submit did not report alreadySubmitted"
Assert-True ([bool]$secondSubmit.data.responseReplayed) "second submit did not replay the stored response"
Assert-True ("$($secondSubmit.data.submitToken)" -eq $submitToken) "replayed response submitToken mismatch"
Assert-True ("$($secondSubmit.data.submitPayloadHash)" -eq "$($firstSubmit.data.submitPayloadHash)") "replayed response payload hash mismatch"
Assert-True ("$($secondSubmit.data.submitTime)" -eq "$($firstSubmit.data.submitTime)") "replayed response submitTime mismatch"
Assert-True ($secondSubmit.data.PSObject.Properties.Name -notcontains "score") "replayed submit leaked score before release"
Assert-True (-not [bool]$secondSubmit.data.scoreVisible) "replayed submit should mark scoreVisible=false before release"
Assert-True ("$($secondSubmit.data.status)" -eq "$($firstSubmit.data.status)") "replayed response status mismatch"
Assert-True ("$($secondSubmit.data.unansweredCount)" -eq "$($firstSubmit.data.unansweredCount)") "replayed response unansweredCount mismatch"

Write-Step "Repeating submit with a different token and the same payload"
$tokenMismatchSubmit = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt/$AttemptId/submit" -Token $StudentToken -Body @{
    answers = $answers
    submitToken = "verify-submit-token-mismatch-$([guid]::NewGuid().ToString("N"))"
}
Assert-True ([bool]$tokenMismatchSubmit.data.success) "token mismatch replay did not report success"
Assert-True ([bool]$tokenMismatchSubmit.data.alreadySubmitted) "token mismatch replay did not report alreadySubmitted"
Assert-True ([bool]$tokenMismatchSubmit.data.responseReplayed) "token mismatch replay did not replay the stored response"
Assert-True (Optional-Bool -Object $tokenMismatchSubmit.data -Name "submitTokenMismatch") "token mismatch replay did not report submitTokenMismatch"
Assert-True (-not (Optional-Bool -Object $tokenMismatchSubmit.data -Name "submitPayloadMismatch")) "token mismatch replay unexpectedly reported submitPayloadMismatch"
Assert-True ($tokenMismatchSubmit.data.PSObject.Properties.Name -notcontains "score") "token mismatch replay leaked score before release"

$payloadMismatchAnswers = [ordered]@{}
foreach ($key in $answers.Keys) {
    $payloadMismatchAnswers["$key"] = $answers[$key]
}
$payloadMismatchKeys = @($payloadMismatchAnswers.Keys)
Assert-True ($payloadMismatchKeys.Count -gt 0) "payload mismatch replay requires at least one submitted answer"
$payloadMismatchKey = "$($payloadMismatchKeys[0])"
$payloadMismatchAnswers[$payloadMismatchKey] = "$($payloadMismatchAnswers[$payloadMismatchKey]) payload-mismatch-$([guid]::NewGuid().ToString("N"))"
Write-Step "Repeating submit with the same token and a different payload"
$payloadMismatchSubmit = Invoke-ExamApi -Method "POST" -Path "/api/exams/attempt/$AttemptId/submit" -Token $StudentToken -Body @{
    answers = $payloadMismatchAnswers
    submitToken = $submitToken
}
Assert-True ([bool]$payloadMismatchSubmit.data.success) "payload mismatch replay did not report success"
Assert-True ([bool]$payloadMismatchSubmit.data.alreadySubmitted) "payload mismatch replay did not report alreadySubmitted"
Assert-True ([bool]$payloadMismatchSubmit.data.responseReplayed) "payload mismatch replay did not replay the stored response"
Assert-True (Optional-Bool -Object $payloadMismatchSubmit.data -Name "submitPayloadMismatch") "payload mismatch replay did not report submitPayloadMismatch"
Assert-True (-not (Optional-Bool -Object $payloadMismatchSubmit.data -Name "submitTokenMismatch")) "payload mismatch replay unexpectedly reported submitTokenMismatch"
Assert-True ($payloadMismatchSubmit.data.PSObject.Properties.Name -notcontains "score") "payload mismatch replay leaked score before release"

if ($ExpectUnreleasedStudentInsightsHidden) {
    Write-Step "Checking unreleased score visibility for student-facing insights"
    $grades = Invoke-ExamApi -Method "GET" -Path "/api/student/grades" -Token $StudentToken
    $gradeRows = @($grades.data)
    $visibleAttempt = $gradeRows | Where-Object { "$($_.attemptId)" -eq "$AttemptId" }
    Assert-True ($null -eq $visibleAttempt -or @($visibleAttempt).Count -eq 0) "unreleased attempt appeared in student grades"

    $wrongQuestions = Invoke-ExamApi -Method "GET" -Path "/api/student/wrong-questions" -Token $StudentToken
    Assert-True (@($wrongQuestions.data).Count -eq 0) "unreleased attempt affected student wrong-question list"

    $mastery = Invoke-ExamApi -Method "GET" -Path "/api/student/mastery" -Token $StudentToken
    $masteryProperties = @()
    if ($mastery.data) {
        $masteryProperties = @($mastery.data.PSObject.Properties)
    }
    Assert-True ($masteryProperties.Count -eq 0) "unreleased attempt affected student mastery data"
}

if ($CheckScoreReleaseVisibilityCycle) {
    Assert-True ($AdminToken -ne "") "AdminToken is required for score release visibility cycle"
    Assert-True ($ExamId -gt 0) "ExamId is required for score release visibility cycle"

    Write-Step "Closing exam $ExamId before publishing scores"
    Invoke-ExamApi -Method "PUT" -Path "/api/exams/$ExamId/close" -Token $AdminToken | Out-Null
    Invoke-ExamApiExpectFailure -Method "GET" -Path "/api/exams/$ExamId/scores/export" -Token $AdminToken -ExpectedMessage "Scores have not been published" | Out-Null

    Write-Step "Publishing scores for exam $ExamId"
    $publish = Invoke-ExamApi -Method "POST" -Path "/api/exams/$ExamId/scores/publish" -Token $AdminToken
    Assert-True ([int]$publish.data.completedAttempts -ge 1) "publish response did not report completed attempts"
    Assert-True ([int]$publish.data.notifiedStudents -ge 1) "publish response did not report notified students"
    Assert-True ([int]$publish.data.notifiedAttempts -ge 1) "publish response did not report notified attempts"

    $scoreNotifications = Invoke-ExamApi -Method "GET" -Path "/api/notifications/my?page=1&size=50&relatedType=EXAM_ATTEMPT&relatedId=$AttemptId" -Token $StudentToken
    $scoreMatches = @($scoreNotifications.data.list | Where-Object {
        "$($_.type)" -eq "SCORE" -and "$($_.relatedType)" -eq "EXAM_ATTEMPT" -and "$($_.relatedId)" -eq "$AttemptId"
    })
    Assert-True ($scoreMatches.Count -gt 0) "student notification list did not include score release related to attempt $AttemptId"

    Write-Step "Checking published score visibility"
    $publishedGrades = Invoke-ExamApi -Method "GET" -Path "/api/student/grades" -Token $StudentToken
    $publishedRows = @($publishedGrades.data)
    $publishedAttempt = $publishedRows | Where-Object { "$($_.attemptId)" -eq "$AttemptId" }
    Assert-True ($null -ne $publishedAttempt -and @($publishedAttempt).Count -gt 0) "published attempt did not appear in student grades"
    $publishedResult = Invoke-ExamApi -Method "GET" -Path "/api/student/exam-result/$AttemptId" -Token $StudentToken
    Assert-True ($publishedResult.data.gradeInfo.attemptId -eq $AttemptId) "published exam result did not return the expected attempt"

    Write-Step "Revoking scores for exam $ExamId"
    $revokeReason = "verify score revoke reason $([guid]::NewGuid().ToString("N"))"
    $revoke = Invoke-ExamApi -Method "POST" -Path "/api/exams/$ExamId/scores/revoke" -Token $AdminToken -Body @{
        reason = $revokeReason
    }
    Assert-True ([int]$revoke.data.visibleAttemptsBeforeRevoke -ge 1) "revoke response did not report visible attempts"
    Assert-True ([int]$revoke.data.notifiedStudents -ge 1) "revoke response did not report notified students"
    Assert-True ([int]$revoke.data.notifiedAttempts -ge 1) "revoke response did not report notified attempts"
    Assert-True ("$($revoke.data.revokeReason)" -eq $revokeReason -or "$($revoke.data.note)" -eq $revokeReason) "revoke response did not echo the revoke reason"

    $revokeNotifications = Invoke-ExamApi -Method "GET" -Path "/api/notifications/my?page=1&size=50&relatedType=EXAM_ATTEMPT&relatedId=$AttemptId" -Token $StudentToken
    $revokeMatches = @($revokeNotifications.data.list | Where-Object {
        "$($_.type)" -eq "SCORE_REVOKED" -and "$($_.relatedType)" -eq "EXAM_ATTEMPT" -and "$($_.relatedId)" -eq "$AttemptId" -and "$($_.content)".Contains($revokeReason)
    })
    Assert-True ($revokeMatches.Count -gt 0) "student notification list did not include score revoke related to attempt $AttemptId with the revoke reason"

    Write-Step "Checking revoked score visibility"
    $revokedGrades = Invoke-ExamApi -Method "GET" -Path "/api/student/grades" -Token $StudentToken
    $revokedRows = @($revokedGrades.data)
    $revokedAttempt = $revokedRows | Where-Object { "$($_.attemptId)" -eq "$AttemptId" }
    Assert-True ($null -eq $revokedAttempt -or @($revokedAttempt).Count -eq 0) "revoked attempt still appeared in student grades"
    Invoke-ExamApiExpectFailure -Method "GET" -Path "/api/student/exam-result/$AttemptId" -Token $StudentToken -ExpectedMessage "Score has not been released" | Out-Null
    Invoke-ExamApiExpectFailure -Method "GET" -Path "/api/exams/$ExamId/scores/export" -Token $AdminToken -ExpectedMessage "Scores have not been published" | Out-Null
}

Write-Step "PASS destructive submit replay checks completed"
