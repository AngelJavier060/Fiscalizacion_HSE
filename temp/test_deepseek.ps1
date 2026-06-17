$ErrorActionPreference = "Stop"
try {
    $headers = @{
        "Authorization" = "Bearer sk-8cd81faf0d064972b2736fffdb59806d"
        "Content-Type" = "application/json"
    }
    $body = '{"model":"deepseek-chat","messages":[{"role":"user","content":"Hola, prueba de conexion"}],"max_tokens":50}'
    
    Write-Host "Probando conexion a DeepSeek API..."
    $response = Invoke-WebRequest -Uri "https://api.deepseek.com/v1/chat/completions" -Method Post -Headers $headers -Body $body -ContentType "application/json" -TimeoutSec 15 -UseBasicParsing
    
    Write-Host "Status: $($response.StatusCode)"
    Write-Host "Response: $($response.Content)"
} catch {
    Write-Host "ERROR: $($_.Exception.Message)"
    if ($_.Exception.Response) {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        Write-Host "Detalle: $($reader.ReadToEnd())"
    }
}
