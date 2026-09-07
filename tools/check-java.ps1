function Assert-BaselineJava($JavaHome, $Specification) {
    $suffix = if ([Environment]::OSVersion.Platform -eq 'Win32NT') { '.exe' } else { '' }
    foreach ($command in @('java', 'javac')) {
        if (!(Test-Path -LiteralPath (Join-Path $JavaHome "bin/$command$suffix"))) {
            throw "Missing $command in JDK: $JavaHome. Run tools/bootstrap.ps1."
        }
    }
    # Process redirection also works on Windows PowerShell 5.1, where native
    # stderr (including java -version) can otherwise become a terminating error.
    $start = New-Object System.Diagnostics.ProcessStartInfo
    $start.FileName = Join-Path $JavaHome "bin/java$suffix"
    $start.Arguments = '-XshowSettings:properties -version'
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardError = $true
    $process = [System.Diagnostics.Process]::Start($start)
    $details = $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    $javaExit = $process.ExitCode
    $process.Dispose()
    $runtime = [regex]::Escape($Specification.runtimeVersion)
    $vendor = [regex]::Escape($Specification.vendor)
    if ($javaExit -ne 0 -or $details -notmatch "java.runtime.version = $runtime(\r?\n)" -or
        $details -notmatch "java.vendor = $vendor(\r?\n)") {
        throw "Baseline requires $($Specification.vendor) JDK $($Specification.runtimeVersion); rejected $JavaHome. Java 21 compatibility is a separate check."
    }
}
