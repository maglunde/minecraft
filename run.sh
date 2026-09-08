#!/bin/bash
set -e
cd "$(dirname "$0")"
echo "Bygger og starter Minecraft Java Clone..."
mvn compile exec:exec
