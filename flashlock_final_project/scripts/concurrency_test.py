#!/usr/bin/env python3
import concurrent.futures
import requests
import time

URL = "http://localhost:8080/api/purchase"
CONCURRENT_REQUESTS = 30
PRODUCT_ID = 1

def send_request(req_id):
    payload = {"productId": PRODUCT_ID, "quantity": 1, "priority": 3}
    try:
        response = requests.post(URL, json=payload, timeout=5)
        return response.status_code, response.json()
    except Exception as e:
        return 500, str(e)

def main():
    print("--- FlashLock 20+ Concurrent Purchase Stress Test ---")
    print(f"Firing {CONCURRENT_REQUESTS} simultaneous purchase requests for Product #{PRODUCT_ID}...")

    start_time = time.time()
    results = []

    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENT_REQUESTS) as executor:
        futures = [executor.submit(send_request, i) for i in range(CONCURRENT_REQUESTS)]
        for future in concurrent.futures.as_completed(futures):
            results.append(future.result())

    elapsed = time.time() - start_time
    successful = sum(1 for status, body in results if status == 200 and body.get("status") == "SUCCESS")
    rejected = sum(1 for status, body in results if status == 409 or body.get("status") == "OUT_OF_STOCK")

    print("\n--- TEST RESULTS ---")
    print(f"Total Requests  : {CONCURRENT_REQUESTS}")
    print(f"Successful      : {successful}")
    print(f"Rejected        : {rejected}")
    print(f"Elapsed Time    : {elapsed:.2f} seconds")
    print(f"Zero Oversell   : {'PASSED (Stock check enforced by PostgreSQL)' if successful <= 10 else 'FAILED'}")

if __name__ == "__main__":
    main()
