#!/bin/bash
# Move to the project directory regardless of where it was launched from
cd "$(dirname "$0")"

echo "========================================================"
echo "Starting CampusLib (Core Java + SQLite Server)..."
echo "Port: 8080"
echo "========================================================"

# Compile Java files
javac -cp "lib/*:src" -d out/production/Library-management-system-java src/LibraryManagementSystem/*.java

# Open browser in background
(sleep 1 && (open http://localhost:8080 2>/dev/null || xdg-open http://localhost:8080 2>/dev/null)) &

# Launch Java Server
java -cp "out/production/Library-management-system-java:lib/*" LibraryManagementSystem.LibraryHttpServer
