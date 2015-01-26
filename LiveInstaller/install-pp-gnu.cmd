@echo off
set jre64=%ProgramFiles%\Java\jre7
set jre86=%ProgramFiles(x86)%\Java\jre7
set liveJarFolder=\pathogensRoot\.imageIO\LivePyramid
if not exist %liveJarFolder%\PlanePyramid.jar (
    echo Standard Live module \pathogensRoot\.imageIO\ is not installed properly
    goto end
)
echo Installing additional Simagis Live modules
echo Already installed Simagis Live libraries %liveJarFolder%\*.jar will be used
FOR /D /r %%G in (".*") DO (
    echo Installing \pathogensRoot\%%~nxG
    IF EXIST \pathogensRoot\%%~nxG\jre (
        rmdir \pathogensRoot\%%~nxG\jre
    )
    xcopy /I/E/Y %%~nxG \pathogensRoot\%%~nxG >nul
    xcopy /I/E/Y %liveJarFolder% \pathogensRoot\%%~nxG\LivePyramid >nul
    IF EXIST %%~nxG\x86\con (
        mklink /J \pathogensRoot\%%~nxG\jre "%jre86%"
    ) ELSE (
        mklink /J \pathogensRoot\%%~nxG\jre "%jre64%"
    )
    IF EXIST \pathogensRoot\%%~nxG\install_additions.cmd (
        PUSHD \pathogensRoot\%%~nxG
        CALL install_additions.cmd
        POPD
    )
)
echo:
:end
