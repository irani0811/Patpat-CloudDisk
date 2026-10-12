param(
  [string]$Api = "http://localhost:8080",
  [string]$Username = "test",
  [string]$Password = "123",
  [string]$OutDir = "C:\temp\dedup-test",
  [int]$ChunkSizeBytes = 4MB,
  [string]$CloudPath1 = "sync/big.bin",
  [string]$CloudPath2 = "sync/big_modified.bin"
)

$ErrorActionPreference = 'Stop'

# --- Helpers ---
function MD5Hex([byte[]]$b){
  $md5=[Security.Cryptography.MD5]::Create();
  ($md5.ComputeHash($b)|ForEach-Object { $_.ToString('x2') }) -join ''
}
function SHA256Hex([byte[]]$b){
  $sha=[Security.Cryptography.SHA256]::Create();
  ($sha.ComputeHash($b)|ForEach-Object { $_.ToString('x2') }) -join ''
}
function Split-Bytes([byte[]]$data,[int]$size){
  $o=@();
  for($i=0;$i -lt $data.Length;$i+=$size){
    $len=[Math]::Min($size,$data.Length-$i)
    $buf=New-Object byte[] $len
    [Array]::Copy($data,$i,$buf,0,$len)
    $o+=,$buf
  }
  ,$o
}
function Run-Dedup([string]$filePath,[string]$cloudPath,[string]$token){
  Write-Host "`n[DEDUP] $filePath -> $cloudPath"
  $bytes=[IO.File]::ReadAllBytes($filePath)
  $fileHash=MD5Hex $bytes
  $chunks=Split-Bytes $bytes $ChunkSizeBytes
  $chunkHashes=@(); foreach($c in $chunks){ $chunkHashes += (SHA256Hex $c) }
  $headers=@{ Authorization=$token; "Content-Type"="application/json" }

  # init
  $initBody=@{
    filename=[IO.Path]::GetFileName($filePath)
    path=$cloudPath
    fileSize=$bytes.Length
    chunkSize=$ChunkSizeBytes
    fileHash=$fileHash
    chunkHashes=$chunkHashes
  } | ConvertTo-Json -Depth 5
  $init=Invoke-RestMethod -Method Post -Uri "$Api/api/dedup/init" -Headers $headers -Body $initBody
  if ($init.fileAlreadyExists -eq $true){
    return [PSCustomObject]@{ filePath=$filePath; cloudPath=$cloudPath; uploaded=0; totalChunks=$chunks.Count; fileId=$init.existingFileId; storagePath='<existing>'; fileSize=$bytes.Length }
  }
  $need=@(); if($init.needUploadIndices){ $need=$init.needUploadIndices }
  Write-Host ("needUpload: {0}/{1}" -f $need.Count, $chunks.Count)

  foreach($i in $need){
    $b64=[Convert]::ToBase64String($chunks[[int]$i])
    $u=@{ fileHash=$fileHash; chunkIndex=[int]$i; chunkHash=$chunkHashes[[int]$i]; dataBase64=$b64 } | ConvertTo-Json -Depth 4
    Invoke-RestMethod -Method Post -Uri "$Api/api/dedup/upload-chunk" -Headers $headers -Body $u | Out-Null
  }

  # finalize（若偶发失败重试一次）
  $final=@{ filename=[IO.Path]::GetFileName($filePath); path=$cloudPath; fileHash=$fileHash; chunkHashes=$chunkHashes } | ConvertTo-Json -Depth 5
  try{
    $resp=Invoke-RestMethod -Method Post -Uri "$Api/api/dedup/finalize" -Headers $headers -Body $final
  } catch {
    Start-Sleep -Milliseconds 300
    $resp=Invoke-RestMethod -Method Post -Uri "$Api/api/dedup/finalize" -Headers $headers -Body $final
  }
  [PSCustomObject]@{
    filePath=$filePath
    cloudPath=$cloudPath
    uploaded=$need.Count
    totalChunks=$chunks.Count
    fileId=$resp.fileId
    storagePath=$resp.storagePath
    fileSize=$resp.fileSize
  }
}

# --- Prepare sample files ---
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$big = Join-Path $OutDir 'big.bin'
if (!(Test-Path $big)){
  Write-Host "Generating $big (12MB)"
  $rng = New-Object System.Random(42)
  $fs = [System.IO.File]::Open($big, 'Create')
  $buf = New-Object byte[] (4KB)
  while ($fs.Length -lt 12MB) { $rng.NextBytes($buf); $fs.Write($buf, 0, $buf.Length) }
  $fs.Close()
}
$big2 = Join-Path $OutDir 'big_modified.bin'
Copy-Item $big $big2 -Force
$fs2 = [System.IO.File]::Open($big2, 'Open', 'ReadWrite')
$mid = [int64]((Get-Item $big2).Length / 2)
$fs2.Seek($mid, [System.IO.SeekOrigin]::Begin) | Out-Null
$patch = New-Object byte[] 1024
for ($i=0; $i -lt $patch.Length; $i++) { $patch[$i] = 0x41 }
$fs2.Write($patch, 0, $patch.Length)
$fs2.Close()

# --- Login ---
$loginBody = @{ username=$Username; password=$Password } | ConvertTo-Json
$login = Invoke-RestMethod -Method Post -Uri "$Api/api/auth/login" -Headers @{ "Content-Type"="application/json" } -Body $loginBody
if (-not $login.token){ Write-Error "Login failed"; exit 1 }
$TOKEN = "Bearer " + $login.token
Write-Host "TOKEN: $TOKEN"

# --- Run baseline and modified ---
$r1 = Run-Dedup -filePath $big  -cloudPath $CloudPath1 -token $TOKEN
$r2 = Run-Dedup -filePath $big2 -cloudPath $CloudPath2 -token $TOKEN

"`nRESULTS:"
$r1 | Format-List
$r2 | Format-List
