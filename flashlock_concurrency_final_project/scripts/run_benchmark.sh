#!/usr/bin/env bash
echo "=== FlashLock Scheduling Policy Benchmark ==="
echo "Building C Engine..."
cd ../engine && make clean && make
cd ../scripts

echo "Starting engine and executing benchmark..."
python3 concurrency_test.py
