#Requires -Version 5.1
# BetterKill functional test suite via RCON.
# Usage: & .\tools\rcon-test.ps1 [-Port 25575] [-Password betterkill]
param(
    [int]$Port = 25575,
    [string]$Password = 'betterkill'
)

$ErrorActionPreference = 'Stop'
$HostIP = '127.0.0.1'

$tcp = New-Object System.Net.Sockets.TcpClient
$tcp.ReceiveTimeout = 8000
$tcp.Connect($HostIP, $Port)
$ns = $tcp.GetStream()

function Write-Bytes([byte[]]$b) {
    $ns.Write($b, 0, $b.Length)
    $ns.Flush()
}

function New-RconPacket([int]$id, [int]$type, [string]$body) {
    $enc = New-Object System.Text.UTF8Encoding($false)
    $bodyBytes = $enc.GetBytes($body)
    $payload = New-Object byte[] ($bodyBytes.Length + 2)
    [Array]::Copy($bodyBytes, $payload, $bodyBytes.Length) # + two trailing 0x00
    $len = 8 + $payload.Length
    $pkt = New-Object byte[] (4 + $len)
    [BitConverter]::GetBytes([int]$len).CopyTo($pkt, 0)
    [BitConverter]::GetBytes([int]$id).CopyTo($pkt, 4)
    [BitConverter]::GetBytes([int]$type).CopyTo($pkt, 8)
    $payload.CopyTo($pkt, 12)
    return $pkt
}

function Read-RconPacket {
    $lenBuf = New-Object byte[] 4
    $got = $ns.Read($lenBuf, 0, 4)
    if ($got -ne 4) { throw "RCON stream closed (read $got bytes)" }
    $len = [BitConverter]::ToInt32($lenBuf, 0)
    $rest = New-Object byte[] $len
    $off = 0
    while ($off -lt $len) {
        $r = $ns.Read($rest, $off, $len - $off)
        if ($r -le 0) { throw "RCON stream closed mid-packet" }
        $off += $r
    }
    return $rest
}

function Invoke-Rcon([string]$cmd, [int]$id) {
    Write-Bytes (New-RconPacket $id 2 $cmd)
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline) {
        $rest = Read-RconPacket
        $rid = [BitConverter]::ToInt32($rest, 0)
        $rtype = [BitConverter]::ToInt32($rest, 4)
        if ($rtype -eq 0 -and $rid -eq $id) {
            $enc = New-Object System.Text.UTF8Encoding($false)
            return $enc.GetString($rest, 8, $rest.Length - 10)
        }
    }
    throw "RCON timeout for command: $cmd"
}

# ---- auth ----
Write-Bytes (New-RconPacket 1 3 $Password)
$authed = $false
$deadline = (Get-Date).AddSeconds(15)
while ((Get-Date) -lt $deadline) {
    $rest = Read-RconPacket
    $rid = [BitConverter]::ToInt32($rest, 0)
    if ($rid -eq -1) { throw "RCON auth rejected (wrong password?)" }
    if ($rid -eq 1) { $authed = $true; break }
}
if (-not $authed) { throw "RCON auth timeout" }
Write-Host "RCON connected.`n"

$idCounter = 1000
$pass = 0
$fail = 0
$failures = @()

function Check([string]$name, [string]$cmd, [string[]]$expectAnyOf, [string[]]$expectNoneOf = @()) {
    $script:idCounter++
    $resp = Invoke-Rcon $cmd $script:idCounter
    $resp = $resp.Trim()
    $ok = $false
    foreach ($e in $expectAnyOf) {
        if ($resp -like "*$e*") { $ok = $true; break }
    }
    if ($ok) {
        foreach ($e in $expectNoneOf) {
            if ($resp -like "*$e*") { $ok = $false; break }
        }
    }
    if ($ok) { $script:pass++; Write-Host "[PASS] $name" -ForegroundColor Green }
    else { $script:fail++; $script:failures += $name; Write-Host "[FAIL] $name" -ForegroundColor Red }
    Write-Host ("  cmd : " + $cmd)
    Write-Host ("  resp: " + $resp)
    Write-Host ""
    Start-Sleep -Milliseconds 300
    return $resp
}

$stoneNbt = '@e[type=item,nbt={Item:{id:"minecraft:stone"}}]'

# ---- test sequence ----
Check "T1: bare /killa with no pending -> no_target" 'killa' @('没有指定杀死对象') | Out-Null

for ($i = 1; $i -le 3; $i++) {
    Check "T2.$i : summon stone item #$i" 'summon minecraft:item ~ ~ ~ {Item:{id:"minecraft:stone",Count:1}}' @('Summoned') | Out-Null
}

$resp = Check "T3: /kill @e[type=item] -> Chinese prompt with yellow count" 'kill @e[type=item]' @('本次指令将杀死', '确认杀死', '/killa') @('已杀死')
if ($resp -match '本次指令将杀死\s*§[0-9a-fk-or]*(?<n>\d+)(§[0-9a-fk-or]*)?\s*个实体') {
    $n = [int]$Matches['n']
} elseif ($resp -match '本次指令将杀死 (?<n>\d+) 个实体') {
    $n = [int]$Matches['n']
} else {
    $n = -1
    Write-Host "WARN: could not parse entity count from prompt"
}
if ($n -lt 3) {
    Write-Host "WARN: expected at least 3 item entities, got $n"
}

Check "T4: old /kill yes no longer confirms (parsed as player name)" 'kill yes' @('No entity was found', '没有找到实体') @('没有指定杀死对象', '已杀死') | Out-Null

Check "T5: /killa after old-command attempt -> no_target" 'killa' @('没有指定杀死对象') | Out-Null

Check "T6: /kill @e[type=item] -> same count $n" 'kill @e[type=item]' @("本次指令将杀死*$n*个实体") @('已杀死') | Out-Null

Check "T7: invalid command also invalidates the pending kill" 'foobar123 not a command' @('Unknown or incomplete command', '未知或不完整的命令') | Out-Null

Check "T8: /killa after invalid command -> no_target" 'killa' @('没有指定杀死对象') | Out-Null

Check "T9: /kill @e[type=item] -> same count $n" 'kill @e[type=item]' @("本次指令将杀死*$n*个实体") @('已杀死') | Out-Null

Check "T10: another valid command invalidates the pending kill" 'say invalidate-check' @('') @('本次指令将杀死', '没有指定杀死对象') | Out-Null

Check "T11: /killa after other command -> no_target" 'killa' @('没有指定杀死对象') | Out-Null

Check "T12: /kill @e[type=item] -> same count $n" 'kill @e[type=item]' @("本次指令将杀死*$n*个实体") @('已杀死') | Out-Null

Check "T13: /killa -> kills exactly $n" 'killa' @("已杀死 $n 个实体") | Out-Null

Check "T14: no stone items remain" "execute if entity $stoneNbt run say STONE_STILL_EXISTS" @('') @('STONE_STILL_EXISTS') | Out-Null

Check "T15: /killa again -> no_target" 'killa' @('没有指定杀死对象') | Out-Null

Check "T16: zero stone matches -> vanilla not-found error" "kill $stoneNbt" @('No entity was found') | Out-Null

$tcp.Close()
Write-Host "==============================="
Write-Host "PASS: $pass  FAIL: $fail"
if ($fail -gt 0) {
    Write-Host "Failed checks: $($failures -join ', ')"
    exit 1
}
Write-Host "ALL TESTS PASSED"
exit 0
