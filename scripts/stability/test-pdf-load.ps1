[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BaseUrl,

    [Parameter(Mandatory)]
    [string]$PdfPath,

    [int]$RequestCount = 30,

    [int]$Concurrency = 10,

    [string]$Token,

    [switch]$SkipCertificateCheck,

    [string]$OutputJson
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

. "$PSScriptRoot/common.ps1"

$normalizedBaseUrl = Resolve-StabilityBaseUrl -BaseUrl $BaseUrl
$targetUrl = Join-StabilityUrl -BaseUrl $normalizedBaseUrl -Path $PdfPath
$client = New-StabilityHttpClient `
    -BaseUrl $normalizedBaseUrl `
    -Token $Token `
    -SkipCertificateCheck:$SkipCertificateCheck `
    -TimeoutSeconds 300

try {
    Write-Host "开始执行并发 PDF 拉取测试..." -ForegroundColor Cyan
    Write-Host "Target: $targetUrl"
    Write-Host "RequestCount: $RequestCount"
    Write-Host "Concurrency: $Concurrency"

    $results = Invoke-StabilityBatch `
        -Client $client `
        -Method GET `
        -RequestCount $RequestCount `
        -Concurrency $Concurrency `
        -RequestFactory {
            param($sequence)
            return @{
                Label = "PDF 拉取"
                Url = $targetUrl
            }
        }

    $summary = Get-StabilitySummary -Results $results -Label "PDF 拉取"

    Write-Host ""
    Write-Host "汇总结果：" -ForegroundColor Green
    Write-StabilitySummary -Summaries @($summary)
    Write-StabilityFailures -Results $results

    if (-not [string]::IsNullOrWhiteSpace($OutputJson)) {
        Export-StabilityPayload -OutputJson $OutputJson -Payload @{
            generated_at = (Get-Date).ToString("s")
            test_type = "pdf_load"
            base_url = $normalizedBaseUrl
            pdf_path = $PdfPath
            target_url = $targetUrl
            request_count = $RequestCount
            concurrency = $Concurrency
            summaries = @($summary)
            results = @($results)
        }
    }
} finally {
    $client.Dispose()
}
