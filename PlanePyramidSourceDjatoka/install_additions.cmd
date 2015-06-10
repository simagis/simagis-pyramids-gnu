@echo off
set java="%ProgramFiles(x86)%\Java\jre7\bin\java.exe"
set cp=LiveAPI/simagis-plane-pyramid-sdk-1.2.5.jar
set class=net.algart.simagis.executable.installer.ZipExtractor
set jar=djatoka\adore-djatoka-full-archive-1.1.jar
%java% -cp %cp% %class% %jar% adore-djatoka-1.1/bin/Win32/kdu_a60R.dll kdu_a60R.dll
%java% -cp %cp% %class% %jar% adore-djatoka-1.1/bin/Win32/kdu_jni.dll kdu_jni.dll
%java% -cp %cp% %class% %jar% adore-djatoka-1.1/bin/Win32/kdu_v60R.dll kdu_v60R.dll
