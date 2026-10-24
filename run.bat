@echo off
title CampusLib - Library Management System

:: Ensure we are in the folder where this bat file is located (even from pendrive or admin)
cd /d "%~dp0"

echo ========================================================
echo Starting CampusLib (Core Java + SQLite Server)...
echo Port: 8080
echo ========================================================

:: Ensure output directory exists
if not exist "out\production\Library-management-system-java" mkdir "out\production\Library-management-system-java"

:: Compile Java files
javac -cp "lib/*;src" -d out/production/Library-management-system-java src/LibraryManagementSystem/*.java

:: Open browser
start "" http://localhost:8080

:: Launch Java Server
java -cp "out/production/Library-management-system-java;lib/*" LibraryManagementSystem.LibraryHttpServer
pause
