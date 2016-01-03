@echo off
set jre64=%ProgramFiles%\Java\jre7
set jre86=%ProgramFiles(x86)%\Java\jre7
set jre_8_64_found=false
set jre_8_86_found=false
echo:
FOR /D  %%G in ("%ProgramFiles%\Java\jre1.8*.*") DO (
    set jre64=%%G
    set jre_8_64_found=true
)
FOR /D  %%G in ("%ProgramFiles(x86)%\Java\jre1.8*.*") DO (
    set jre86=%%G
    set jre_8_86_found=true
)
IF NOT %jre_8_64_found%==true (
    echo Java-x86 SDK 1.8 NOT FOUND!
    echo Trying to use "%jre64%"
    echo:
)
IF NOT %jre_8_86_found%==true (
    echo Java-x64 SDK 1.8 NOT FOUND!
    echo Trying to use "%jre86%"
    echo:
)
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
    IF EXIST \pathogensRoot\%%~nxG\jre86 (
        rmdir \pathogensRoot\%%~nxG\jre86
    )
    xcopy /I/E/Y %%~nxG \pathogensRoot\%%~nxG >nul
    xcopy /I/E/Y %liveJarFolder% \pathogensRoot\%%~nxG\LivePyramid >nul
    IF EXIST %%~nxG\x86\.keep mklink /J \pathogensRoot\%%~nxG\jre86 "%jre86%"
    IF EXIST %%~nxG\x86\.only86 mklink /J \pathogensRoot\%%~nxG\jre86 "%jre86%"
    IF NOT EXIST %%~nxG\x86\.only86 mklink /J \pathogensRoot\%%~nxG\jre "%jre64%"
    IF EXIST \pathogensRoot\%%~nxG\.x86 del \pathogensRoot\%%~nxG\.x86
    IF EXIST \pathogensRoot\%%~nxG\.x64 del \pathogensRoot\%%~nxG\.x64
    IF EXIST \pathogensRoot\%%~nxG\install_additions.cmd (
        PUSHD \pathogensRoot\%%~nxG
        CALL install_additions.cmd
        POPD
    )
    IF EXIST \pathogensRoot\%%~nxG\x86\con (
        rmdir /s/q \pathogensRoot\%%~nxG\x86
        rem This folder contains only markers for this installation file, not used by Simagis Live
    )
)
echo:
:end
