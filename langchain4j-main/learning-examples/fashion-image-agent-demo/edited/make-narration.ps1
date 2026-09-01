$outDir = 'E:\ai-workspace\langchain4j-main\langchain4j-main\learning-examples\fashion-image-agent-demo\edited'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$text = [System.IO.File]::ReadAllText((Join-Path $outDir 'narration.txt'), [System.Text.Encoding]::UTF8)
Add-Type -AssemblyName System.Speech
$voice = New-Object System.Speech.Synthesis.SpeechSynthesizer
$voice.Rate = 1
$voice.Volume = 100
$chineseVoice = $voice.GetInstalledVoices() | Where-Object { $_.VoiceInfo.Name -match 'Huihui|Chinese' } | Select-Object -First 1
if ($null -ne $chineseVoice) { $voice.SelectVoice($chineseVoice.VoiceInfo.Name) }
$output = Join-Path $outDir 'narration.wav'
$voice.SetOutputToWaveFile($output)
$voice.Speak($text)
$voice.Dispose()
Write-Output $output
