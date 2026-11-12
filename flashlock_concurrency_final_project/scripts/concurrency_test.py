#!/usr/bin/env python3
import argparse
import concurrent.futures
import requests
import time

BASE_URL = "http://localhost:8080/api"

def send_purchase(product_id, quantity, strategy):
    payload = {
        "productId": product_id,
        "quantity": quantity,
        "priority": 3,
        "strategy": strategy
    }
    try:
        response = requests.post(f"{BASE_URL}/purchase", json=payload, timeout=10)
        return response.status_code, response.json()
    except Exception as e:
        return 500, {"status": "ERROR", "message": str(e)}

def main():
    parser = argparse.ArgumentParser(description="FlashLock Concurrency Race-Condition Stress Test")
    parser.add_argument("--strategy", default="ATOMIC", choices=["ATOMIC", "PESSIMISTIC", "OPTIMISTIC"],
                        help="Concurrency strategy to test")
    parser.add_argument("--requests", type=int, default=100, help="Total concurrent requests to fire")
    parser.add_argument("--product", type=int, default=4, help="Target product ID")
    parser.add_argument("--reset-stock", type=int, default=None, help="Optional stock reset value before test")
    args = parser.parse_args()

    if args.reset_stock is not None:
        requests.post(f"{BASE_URL}/products/{args.product}/reset", json={"stock": args.reset_stock}, timeout=5)

    products = requests.get(f"{BASE_URL}/products", timeout=5).json()
    target = next((p for p in products if p["id"] == args.product), None)
    initial_stock = target["stock"] if target else 0

    print(f"=== FlashLock Concurrency Stress Test ({args.strategy}) ===")
    print(f"Target Product : #{args.product} (Initial Stock: {initial_stock})")
    print(f"Total Requests : {args.requests}")

    start_time = time.time()
    results = []
    workers = min(args.requests, 50)

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as executor:
        futures = [executor.submit(send_purchase, args.product, 1, args.strategy) for _ in range(args.requests)]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())

    elapsed = time.time() - start_time
    successful = sum(1 for _, body in results if body.get("status") == "SUCCESS")
    out_of_stock = sum(1 for _, body in results if body.get("status") == "OUT_OF_STOCK")
    conflicts = sum(1 for _, body in results if body.get("status") == "CONFLICT")
    throughput = int(args.requests / elapsed) if elapsed > 0 else 0

    products_after = requests.get(f"{BASE_URL}/products", timeout=5).json()
    target_after = next((p for p in products_after if p["id"] == args.product), None)
    final_stock = target_after["stock"] if target_after else 0

    overselling = final_stock < 0 or successful > initial_stock

    print("\n--- RESULTS ---")
    print(f"Strategy        : {args.strategy}")
    print(f"Successful      : {successful}")
    print(f"Out of Stock    : {out_of_stock}")
    print(f"Conflicts       : {conflicts}")
    print(f"Final Stock     : {final_stock}")
    print(f"Total Time      : {elapsed * 1000:.0f} ms")
    print(f"Throughput      : {throughput} req/sec")
    print(f"Overselling     : {'YES (FAILED)' if overselling else 'NO (PASSED)'}")

if __name__ == "__main__":
    main()
