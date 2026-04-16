Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-StabilityBaseUrl {
    param(
        [Parameter(Mandatory)]
        [string]$BaseUrl
    )

    $normalized = $BaseUrl.Trim()
    if ([string]::IsNullOrWhiteSpace($normalized)) {
        throw "BaseUrl 不能为空。"
    }
    return $normalized.TrimEnd("/")
}

function Join-StabilityUrl {
    param(
        [Parameter(Mandatory)]
        [string]$BaseUrl,

        [Parameter(Mandatory)]
        [string]$Path,

        [hashtable]$Query
    )

    if ($Path -match "^https?://") {
        $builder = [System.UriBuilder]::new($Path)
    } else {
        $target = "{0}/{1}" -f $BaseUrl.TrimEnd("/"), $Path.TrimStart("/")
        $builder = [System.UriBuilder]::new($target)
    }

    if ($Query -and $Query.Count -gt 0) {
        $pairs = New-Object System.Collections.Generic.List[string]
        foreach ($key in ($Query.Keys | Sort-Object)) {
            $value = $Query[$key]
            if ($null -eq $value) {
                continue
            }
            $stringValue = [string]$value
            if ([string]::IsNullOrWhiteSpace($stringValue)) {
                continue
            }
            $pair = "{0}={1}" -f `
                [System.Uri]::EscapeDataString([string]$key), `
                [System.Uri]::EscapeDataString($stringValue)
            $pairs.Add($pair)
        }
        $builder.Query = [string]::Join("&", $pairs)
    }

    return $builder.Uri.AbsoluteUri
}

function New-StabilityHttpClient {
    param(
        [Parameter(Mandatory)]
        [string]$BaseUrl,

        [string]$Token,

        [int]$TimeoutSeconds = 120,

        [switch]$SkipCertificateCheck
    )

    $handler = [System.Net.Http.HttpClientHandler]::new()
    if ($SkipCertificateCheck) {
        $handler.ServerCertificateCustomValidationCallback = { $true }
    }

    $client = [System.Net.Http.HttpClient]::new($handler)
    $client.BaseAddress = [System.Uri]("{0}/" -f $BaseUrl.TrimEnd("/"))
    $client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)

    if (-not [string]::IsNullOrWhiteSpace($Token)) {
        $client.DefaultRequestHeaders.Authorization =
            [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    }

    return $client
}

function Get-StabilityBodyPreview {
    param(
        [AllowNull()]
        [string]$Body,

        [int]$MaxLength = 240
    )

    if ([string]::IsNullOrEmpty($Body)) {
        return ""
    }

    $normalized = ($Body -replace "\s+", " ").Trim()
    if ($normalized.Length -le $MaxLength) {
        return $normalized
    }
    return "{0}..." -f $normalized.Substring(0, $MaxLength)
}

function Get-StabilityPercentile {
    param(
        [double[]]$Values,
        [double]$Percentile
    )

    if (-not $Values -or $Values.Count -eq 0) {
        return 0
    }

    $sorted = $Values | Sort-Object
    $index = [Math]::Ceiling(($Percentile / 100.0) * $sorted.Count) - 1
    $index = [Math]::Min([Math]::Max($index, 0), $sorted.Count - 1)
    return [Math]::Round([double]$sorted[$index], 2)
}

function New-StabilityResult {
    param(
        [int]$Sequence,
        [string]$Label,
        [string]$Url,
        [bool]$Ok,
        [int]$StatusCode,
        [double]$ElapsedMs,
        [long]$Bytes,
        [string]$ResponsePreview,
        [string]$Error
    )

    return [pscustomobject]@{
        sequence         = $Sequence
        label            = $Label
        url              = $Url
        ok               = $Ok
        status_code      = $StatusCode
        elapsed_ms       = [Math]::Round($ElapsedMs, 2)
        bytes            = $Bytes
        response_preview = $ResponsePreview
        error            = $Error
    }
}

function Test-StabilitySpecField {
    param(
        [Parameter(Mandatory)]
        [object]$Spec,

        [Parameter(Mandatory)]
        [string]$Name
    )

    if ($Spec -is [hashtable]) {
        return $Spec.ContainsKey($Name)
    }

    return $Spec.PSObject.Properties.Match($Name).Count -gt 0
}

function Get-StabilitySpecField {
    param(
        [Parameter(Mandatory)]
        [object]$Spec,

        [Parameter(Mandatory)]
        [string]$Name
    )

    if ($Spec -is [hashtable]) {
        return $Spec[$Name]
    }

    return $Spec.$Name
}

function Invoke-StabilityBatch {
    param(
        [Parameter(Mandatory)]
        [System.Net.Http.HttpClient]$Client,

        [Parameter(Mandatory)]
        [ValidateSet("GET", "POST")]
        [string]$Method,

        [Parameter(Mandatory)]
        [int]$RequestCount,

        [Parameter(Mandatory)]
        [int]$Concurrency,

        [Parameter(Mandatory)]
        [scriptblock]$RequestFactory
    )

    if ($RequestCount -lt 1) {
        throw "RequestCount 必须大于 0。"
    }
    if ($Concurrency -lt 1) {
        throw "Concurrency 必须大于 0。"
    }

    $results = New-Object System.Collections.Generic.List[object]

    for ($offset = 0; $offset -lt $RequestCount; $offset += $Concurrency) {
        $batchSize = [Math]::Min($Concurrency, $RequestCount - $offset)
        $tasks = New-Object System.Collections.Generic.List[object]

        for ($i = 0; $i -lt $batchSize; $i++) {
            $sequence = $offset + $i + 1
            $spec = & $RequestFactory $sequence

            if (-not $spec -or [string]::IsNullOrWhiteSpace([string]$spec.Url)) {
                throw "第 $sequence 个请求没有返回有效的 Url。"
            }

            $request = [System.Net.Http.HttpRequestMessage]::new(
                [System.Net.Http.HttpMethod]::$Method,
                [string]$spec.Url
            )

            if ((Test-StabilitySpecField -Spec $spec -Name "Headers") -and (Get-StabilitySpecField -Spec $spec -Name "Headers")) {
                $headers = Get-StabilitySpecField -Spec $spec -Name "Headers"
                foreach ($key in $headers.Keys) {
                    $null = $request.Headers.TryAddWithoutValidation([string]$key, [string]$headers[$key])
                }
            }

            if ($Method -eq "POST") {
                if ((Test-StabilitySpecField -Spec $spec -Name "RawBody") -and $null -ne (Get-StabilitySpecField -Spec $spec -Name "RawBody")) {
                    $rawBody = Get-StabilitySpecField -Spec $spec -Name "RawBody"
                    $contentType = if ((Test-StabilitySpecField -Spec $spec -Name "ContentType") -and (Get-StabilitySpecField -Spec $spec -Name "ContentType")) {
                        [string](Get-StabilitySpecField -Spec $spec -Name "ContentType")
                    } else {
                        "application/json"
                    }
                    $request.Content = [System.Net.Http.StringContent]::new(
                        [string]$rawBody,
                        [System.Text.Encoding]::UTF8,
                        $contentType
                    )
                } elseif ((Test-StabilitySpecField -Spec $spec -Name "Body") -and $null -ne (Get-StabilitySpecField -Spec $spec -Name "Body")) {
                    $body = Get-StabilitySpecField -Spec $spec -Name "Body"
                    $json = $body | ConvertTo-Json -Depth 10 -Compress
                    $request.Content = [System.Net.Http.StringContent]::new(
                        $json,
                        [System.Text.Encoding]::UTF8,
                        "application/json"
                    )
                }
            }

            $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
            $sendTask = $Client.SendAsync($request)
            $tasks.Add([pscustomobject]@{
                Sequence  = $sequence
                Label     = if (Test-StabilitySpecField -Spec $spec -Name "Label") { [string](Get-StabilitySpecField -Spec $spec -Name "Label") } else { "" }
                Url       = [string](Get-StabilitySpecField -Spec $spec -Name "Url")
                Stopwatch = $stopwatch
                Request   = $request
                Task      = $sendTask
            })
        }

        foreach ($item in $tasks) {
            try {
                $response = $item.Task.GetAwaiter().GetResult()
                $item.Stopwatch.Stop()
                $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
                $length = if ($response.Content.Headers.ContentLength) {
                    [long]$response.Content.Headers.ContentLength
                } else {
                    [System.Text.Encoding]::UTF8.GetByteCount($body)
                }

                $results.Add(
                    (New-StabilityResult `
                        -Sequence $item.Sequence `
                        -Label $item.Label `
                        -Url $item.Url `
                        -Ok $response.IsSuccessStatusCode `
                        -StatusCode ([int]$response.StatusCode) `
                        -ElapsedMs $item.Stopwatch.Elapsed.TotalMilliseconds `
                        -Bytes $length `
                        -ResponsePreview (Get-StabilityBodyPreview -Body $body) `
                        -Error "")
                )
                $response.Dispose()
            } catch {
                $item.Stopwatch.Stop()
                $message = if ($_.Exception.InnerException) {
                    $_.Exception.InnerException.Message
                } else {
                    $_.Exception.Message
                }
                $results.Add(
                    (New-StabilityResult `
                        -Sequence $item.Sequence `
                        -Label $item.Label `
                        -Url $item.Url `
                        -Ok $false `
                        -StatusCode 0 `
                        -ElapsedMs $item.Stopwatch.Elapsed.TotalMilliseconds `
                        -Bytes 0 `
                        -ResponsePreview "" `
                        -Error $message)
                )
            } finally {
                $item.Request.Dispose()
            }
        }
    }

    return $results.ToArray()
}

function Get-StabilitySummary {
    param(
        [Parameter(Mandatory)]
        [object[]]$Results,

        [Parameter(Mandatory)]
        [string]$Label
    )

    $total = $Results.Count
    $success = @($Results | Where-Object { $_.ok }).Count
    $failed = $total - $success
    $latencies = @($Results | ForEach-Object { [double]$_.elapsed_ms })

    $statusSummary = @(
        $Results |
        Group-Object -Property status_code |
        Sort-Object Name |
        ForEach-Object { "{0}:{1}" -f $_.Name, $_.Count }
    ) -join ", "

    return [pscustomobject]@{
        label       = $Label
        total       = $total
        success     = $success
        failed      = $failed
        avg_ms      = [Math]::Round((($latencies | Measure-Object -Average).Average), 2)
        min_ms      = [Math]::Round((($latencies | Measure-Object -Minimum).Minimum), 2)
        p95_ms      = Get-StabilityPercentile -Values $latencies -Percentile 95
        max_ms      = [Math]::Round((($latencies | Measure-Object -Maximum).Maximum), 2)
        bytes_total = [long](($Results | Measure-Object -Property bytes -Sum).Sum)
        status      = $statusSummary
    }
}

function Write-StabilitySummary {
    param(
        [Parameter(Mandatory)]
        [object[]]$Summaries
    )

    $Summaries |
        Select-Object label, total, success, failed, avg_ms, p95_ms, max_ms, status |
        Format-Table -AutoSize
}

function Write-StabilityFailures {
    param(
        [Parameter(Mandatory)]
        [object[]]$Results,

        [int]$Limit = 5
    )

    $failures = @($Results | Where-Object { -not $_.ok } | Select-Object -First $Limit)
    if ($failures.Count -eq 0) {
        Write-Host "未发现失败请求。" -ForegroundColor Green
        return
    }

    Write-Host ""
    Write-Host "失败样本（最多 $Limit 条）：" -ForegroundColor Yellow
    $failures |
        Select-Object sequence, label, status_code, elapsed_ms, error, response_preview |
        Format-List
}

function Export-StabilityPayload {
    param(
        [Parameter(Mandatory)]
        [string]$OutputJson,

        [Parameter(Mandatory)]
        [object]$Payload
    )

    $directory = Split-Path -Parent $OutputJson
    if (-not [string]::IsNullOrWhiteSpace($directory)) {
        New-Item -ItemType Directory -Force -Path $directory | Out-Null
    }

    $Payload | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -Path $OutputJson
    Write-Host "原始结果已保存到 $OutputJson" -ForegroundColor Cyan
}
