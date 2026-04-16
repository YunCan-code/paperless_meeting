[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BaseUrl,

    [Parameter(Mandatory)]
    [int]$VoteId,

    [Parameter(Mandatory)]
    [int[]]$OptionIds,

    [int[]]$UserIds,

    [string]$UserCsvPath,

    [int]$Concurrency = 10,

    [int]$MaxRequests = 0,

    [string]$Token,

    [switch]$SkipCertificateCheck,

    [string]$OutputJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/common.ps1"

function Get-StabilityUserIds {
    param(
        [int[]]$InlineUserIds,
        [string]$CsvPath
    )

    $userIds = New-Object System.Collections.Generic.List[int]

    if ($InlineUserIds) {
        foreach ($userId in $InlineUserIds) {
            if ($userId -gt 0) {
                $userIds.Add([int]$userId)
            }
        }
    }

    if (-not [string]::IsNullOrWhiteSpace($CsvPath)) {
        if (-not (Test-Path -LiteralPath $CsvPath)) {
            throw "找不到用户 CSV: $CsvPath"
        }

        foreach ($row in (Import-Csv -LiteralPath $CsvPath)) {
            if ($null -eq $row.user_id -or [string]::IsNullOrWhiteSpace([string]$row.user_id)) {
                continue
            }
            $userIds.Add([int]$row.user_id)
        }
    }

    $deduplicated = $userIds | Select-Object -Unique
    return @($deduplicated)
}

$normalizedBaseUrl = Resolve-StabilityBaseUrl -BaseUrl $BaseUrl
$resolvedUserIds = Get-StabilityUserIds -InlineUserIds $UserIds -CsvPath $UserCsvPath

if (-not $resolvedUserIds -or $resolvedUserIds.Count -eq 0) {
    throw "请通过 -UserIds 或 -UserCsvPath 提供至少一个测试用户。"
}
if (-not $OptionIds -or $OptionIds.Count -eq 0) {
    throw "OptionIds 不能为空。"
}

$requestCount = if ($MaxRequests -gt 0) { $MaxRequests } else { $resolvedUserIds.Count }
if ($requestCount -gt $resolvedUserIds.Count) {
    throw "请求数 $requestCount 超过可用唯一用户数 $($resolvedUserIds.Count)。请补充测试用户，避免重复投票。"
}

$voteUrl = Join-StabilityUrl -BaseUrl $normalizedBaseUrl -Path "/vote/$VoteId/submit"
$client = New-StabilityHttpClient `
    -BaseUrl $normalizedBaseUrl `
    -Token $Token `
    -SkipCertificateCheck:$SkipCertificateCheck

try {
    Write-Host "开始执行并发投票提交测试..." -ForegroundColor Cyan
    Write-Host "VoteId: $VoteId"
    Write-Host "Target: $voteUrl"
    Write-Host "RequestCount: $requestCount"
    Write-Host "Concurrency: $Concurrency"
    Write-Host "OptionIds: $($OptionIds -join ', ')"

    $selectedUsers = @($resolvedUserIds | Select-Object -First $requestCount)
    $results = Invoke-StabilityBatch `
        -Client $client `
        -Method POST `
        -RequestCount $requestCount `
        -Concurrency $Concurrency `
        -RequestFactory {
            param($sequence)
            $userId = [int]$selectedUsers[$sequence - 1]
            return @{
                Label = "投票提交"
                Url = $voteUrl
                Body = @{
                    user_id = $userId
                    option_ids = @($OptionIds)
                }
            }
        }

    $summary = Get-StabilitySummary -Results $results -Label "投票提交"

    Write-Host ""
    Write-Host "汇总结果：" -ForegroundColor Green
    Write-StabilitySummary -Summaries @($summary)
    Write-StabilityFailures -Results $results

    if (-not [string]::IsNullOrWhiteSpace($OutputJson)) {
        Export-StabilityPayload -OutputJson $OutputJson -Payload @{
            generated_at = (Get-Date).ToString("s")
            test_type = "vote_submit"
            base_url = $normalizedBaseUrl
            vote_id = $VoteId
            option_ids = @($OptionIds)
            request_count = $requestCount
            concurrency = $Concurrency
            user_ids = @($selectedUsers)
            summaries = @($summary)
            results = @($results)
        }
    }
} finally {
    $client.Dispose()
}
