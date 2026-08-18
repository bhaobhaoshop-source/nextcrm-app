#!/usr/bin/env bash
# Load a new list. Usage: ./import.sh my-list.pdf
cd "$(dirname "$0")"
[ -z "$1" ] && { echo "Usage: ./import.sh <file.pdf|.csv|.xlsx>"; exit 1; }
python3 tools/clean_leads.py "$1" && echo && echo "Now run: ./start.sh"
