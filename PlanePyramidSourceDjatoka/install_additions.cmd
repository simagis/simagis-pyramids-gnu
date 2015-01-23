@echo off
set java="%ProgramFiles(x86)%\Java\jre7\bin\java.exe"
set class=com.simagis.executable.installer.ZipExtractor
set jar=djatoka\adore-djatoka-full-archive-1.1.jar
%java% -cp LiveAPI/* %class% %jar% adore-djatoka-1.1/bin/Win32/kdu_a60R.dll bin/Windows/kdu_a60R.dll
%java% -cp LiveAPI/* %class% %jar% adore-djatoka-1.1/bin/Win32/kdu_jni.dll bin/Windows/kdu_jni.dll
%java% -cp LiveAPI/* %class% %jar% adore-djatoka-1.1/bin/Win32/kdu_v60R.dll bin/Windows/kdu_v60R.dll
