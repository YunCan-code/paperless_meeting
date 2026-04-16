# 会场稳定性验证脚本

这套脚本用于开发阶段做低成本稳定性验证，重点是替代“大量真设备同时操作”的场景。

## 覆盖范围

- `test-meeting-entry.ps1`：并发进入会议
- `test-pdf-load.ps1`：并发拉取同一份 PDF
- `test-vote-submit.ps1`：并发提交投票

这套脚本不负责替代断网恢复、后端重启、Redis 暂停、Android 新旧版本混用等场景。那些场景已经整理进 [开发阶段会场稳定性检查单](../../doc/开发阶段会场稳定性检查单.md)。

## 运行前准备

- 建议用 `pwsh` 运行
- 准备一套本地或测试环境
- 需要时准备测试会议、测试投票、测试用户
- 如果环境是自签名 HTTPS，可以加 `-SkipCertificateCheck`

## 参数说明

### 通用参数

- `-BaseUrl`
  - API 基地址
  - 既可以传后端直连地址，例如 `http://127.0.0.1:8000`
  - 也可以传前端代理地址，例如 `http://127.0.0.1:5000/api`
- `-Token`
  - 可选，透传 `Authorization: Bearer ...`
- `-SkipCertificateCheck`
  - 可选，适合测试环境自签名证书
- `-OutputJson`
  - 可选，把原始结果和汇总落到 JSON 文件，便于归档

## 使用示例

### 1. 并发进入会议

```powershell
pwsh ./scripts/stability/test-meeting-entry.ps1 `
  -BaseUrl http://127.0.0.1:8000 `
  -MeetingId 12 `
  -UserId 5 `
  -RequestCountPerEndpoint 50 `
  -Concurrency 20 `
  -OutputJson ./tmp/meeting-entry.json
```

### 2. 并发拉取 PDF

```powershell
pwsh ./scripts/stability/test-pdf-load.ps1 `
  -BaseUrl http://127.0.0.1:5000 `
  -PdfPath /static/example.pdf `
  -RequestCount 50 `
  -Concurrency 20
```

### 3. 并发提交投票

```powershell
pwsh ./scripts/stability/test-vote-submit.ps1 `
  -BaseUrl http://127.0.0.1:8000 `
  -VoteId 18 `
  -OptionIds 33 `
  -UserIds 101,102,103,104,105 `
  -Concurrency 5
```

也可以从 CSV 读取用户，CSV 至少包含 `user_id` 列：

```csv
user_id
101
102
103
```

```powershell
pwsh ./scripts/stability/test-vote-submit.ps1 `
  -BaseUrl http://127.0.0.1:8000 `
  -VoteId 18 `
  -OptionIds 33 `
  -UserCsvPath ./scripts/stability/users.csv `
  -Concurrency 20
```

## 输出怎么看

脚本会输出两类信息：

- 汇总表：总请求数、成功数、失败数、平均耗时、P95、最大耗时、状态码分布
- 失败样本：最多打印前 5 条失败响应，方便快速定位

建议把脚本输出、截图和日志一起记录到 [result-template.md](./result-template.md)。

## 注意事项

- `test-vote-submit.ps1` 需要保证测试用户尚未参与本轮投票，否则会被后端正常拒绝为重复投票
- 如果 `BaseUrl` 已经带 `/api`，脚本会直接拼在其后；如果没有，也可以直接连 backend
- 并发脚本只能验证 HTTP 层是否稳定，不能替代真实 Android 上的 PDF 渲染、Socket 恢复、前后台切换
