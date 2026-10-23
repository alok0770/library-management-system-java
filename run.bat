@echo off
title CampusLib - Library Management System
echo ========================================================
echo Starting CampusLib (Core Java + SQLite Server)...
echo Port: 8080
echo ========================================================

REM Compile Java files if needed
javac -cp "lib/*;src" -d out/production/Library-management-system-java src/LibraryManagementSystem/*.java

REM Open browser after 2 seconds
start "" http://localhost:8080

REM Launch Java Server
java -cp "out/production/Library-management-system-java;lib/*" LibraryManagementSystem.LibraryHttpServer
pause
