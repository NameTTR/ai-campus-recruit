$ErrorActionPreference = 'Stop'
$Host.UI.RawUI.WindowTitle = 'Codex Worker v0.3'

$workspace = 'D:\Study\homework\fenbushixitong\exfinal1'
Set-Location $workspace

$prompt = "Read docs/ai/current-worker-task.md, then implement it. Do not commit or push. Report changed files and verification results when finished."

$arguments = @(
    '--dangerously-bypass-approvals-and-sandbox',
    '-C',
    $workspace,
    '--no-alt-screen',
    $prompt
)

& codex @arguments
