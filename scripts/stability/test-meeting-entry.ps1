[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BaseUrl,

    [Parameter(Mandatory)]
    [int]$MeetingId,

    [int]$UserId,

    [int]$RequestCountPerEndpoint = 30,

    [int]$Concurrency = 10,

    [string]$Token,

    [switch]$SkipCertificateCheck,

    [string]$OutputJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/common.ps1"

$normalizedBaseUrl = Resolve-StabilityBaseUrl -BaseUrl $BaseUrl
$client = New-StabilityHttpClient `
    -BaseUrl $normalizedBaseUrl `
    -Token $Token `
    -SkipCertificateCheck:$SkipCertificateCheck

try {
    $query = @{
        user_id = if ($UserId -gt 0) { $UserId } else { $null }
    }

    $endpointSpecs = @(
        @{
            Label = "会议列表"
            Url = (Join-StabilityUrl -BaseUrl $normalizedBaseUrl -Path "/meetings/" -Query (@{
                        limit = 20
                        user_id = $query.user_id
                    }))
        },
        @{
            Label = "会议详情"
            Url = (Join-StabilityUrl -BaseUrl $normalizedBaseUrl -Path "/meetings/$MeetingId" -Query $query)
        },
        @{
            Label = "互动总览"
            Url = (Join-StabilityUrl -BaseUrl $normalizedBaseUrl -Path "/interactions/meeting/$MeetingId/overview" -Query $query)
        }
    )

    $allResults = New-Object System.Collections.Generic.List[object]
    $summaries = New-Object System.Collections.Generic.List[object]

    Write-Host "开始执行并发进会测试..." -ForegroundColor Cyan
    Write-Host "BaseUrl: $normalizedBaseUrl"
    Write-Host "MeetingId: $MeetingId"
    Write-Host "RequestCountPerEndpoint: $RequestCountPerEndpoint"
    Write-Host "Concurrency: $Concurrency"

    foreach ($endpoint in $endpointSpecs) {
        Write-Host ""
        Write-Host "测试接口: $($endpoint.Label)" -ForegroundColor Yellow
        $results = Invoke-StabilityBatch `
            -Client $client `
            -Method GET `
            -RequestCount $RequestCountPerEndpoint `
            -Concurrency $Concurrency `
            -RequestFactory {
                param($sequence)
                return @{
                    Label = $endpoint.Label
                    Url = $endpoint.Url
                }
            }

        foreach ($result in $results) {
            $allResults.Add($result)
        }
        $summaries.Add((Get-StabilitySummary -Results $results -Label $endpoint.Label))
    }

    Write-Host ""
    Write-Host "汇总结果：" -ForegroundColor Green
    Write-StabilitySummary -Summaries @($summaries)
    Write-StabilityFailures -Results @($allResults)

    if (-not [string]::IsNullOrWhiteSpace($OutputJson)) {
        Export-StabilityPayload -OutputJson $OutputJson -Payload @{
            generated_at = (Get-Date).ToString("s")
            test_type = "meeting_entry"
            base_url = $normalizedBaseUrl
            meeting_id = $MeetingId
            user_id = if ($UserId -gt 0) { $UserId } else { $null }
            request_count_per_endpoint = $RequestCountPerEndpoint
            concurrency = $Concurrency
            summaries = @($summaries)
            results = @($allResults)
        }
    }
} finally {
    $client.Dispose()
}
