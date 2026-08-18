#!/usr/bin/env bash
# Start the Cold Call Command Center. Usage: ./start.sh
cd "$(dirname "$0")"
if [ ! -f data/leads.json ]; then
  echo "⚠️  No leads loaded yet. Run:"
  echo "    python3 tools/clean_leads.py /path/to/your-list.pdf"
  exit 1
fi
IP=$(hostname -I 2>/dev/null | awk '{print $1}')
echo "Team members on your Wi-Fi can use:  http://${IP:-<your-ip>}:${PORT:-3100}"
exec node server.js
