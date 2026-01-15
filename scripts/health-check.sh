#!/bin/bash

# ============================================
# Health Check Script
# ============================================
# Check application health endpoint

if [ -z "$1" ]; then
    echo "Usage: $0 <ec2-ip-or-domain>"
    echo "Example: $0 3.34.123.45"
    exit 1
fi

HOST=$1
URL="http://$HOST:8080/actuator/health"
MAX_RETRIES=10
RETRY_INTERVAL=5

echo "=========================================="
echo "Health Check"
echo "=========================================="
echo "URL: $URL"
echo "Max retries: $MAX_RETRIES"
echo "Retry interval: ${RETRY_INTERVAL}s"
echo "=========================================="
echo ""

for i in $(seq 1 $MAX_RETRIES); do
    echo "[$i/$MAX_RETRIES] Checking health..."

    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" $URL)

    if [ "$HTTP_CODE" = "200" ]; then
        RESPONSE=$(curl -s $URL)
        echo ""
        echo "✅ Application is healthy!"
        echo ""
        echo "Response:"
        echo "$RESPONSE" | jq . 2>/dev/null || echo "$RESPONSE"
        echo ""
        exit 0
    else
        echo "❌ Health check failed (HTTP $HTTP_CODE)"

        if [ $i -lt $MAX_RETRIES ]; then
            echo "Retrying in ${RETRY_INTERVAL}s..."
            sleep $RETRY_INTERVAL
        fi
    fi
done

echo ""
echo "❌ Health check failed after $MAX_RETRIES attempts"
echo ""
exit 1
