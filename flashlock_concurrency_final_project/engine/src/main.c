#include "../include/queue.h"
#include "../include/scheduler.h"
#include "../include/worker.h"
#include "../include/ipc.h"
#include "../include/metrics.h"
#include <stdio.h>
#include <stdlib.h>
#include <signal.h>
#include <unistd.h>

static RequestQueue g_request_queue;
static pthread_t g_ipc_thread;

void handle_signal(int sig) {
    printf("\n[FlashLock Engine] Shutting down cleanly...\n");
    worker_pool_shutdown();
    ipc_shutdown();
    queue_destroy(&g_request_queue);
    exit(0);
}

int main(int argc, char *argv[]) {
    signal(SIGINT, handle_signal);
    signal(SIGTERM, handle_signal);

    int capacity = 256; // >= Tomcat max threads (200), so queued purchases are never dropped
    int workers = 10;

    printf("===========================================\n");
    printf("   FLASHLOCK OS CONCURRENCY ENGINE (C)    \n");
    printf("===========================================\n");

    metrics_init();
    queue_init(&g_request_queue, capacity);
    scheduler_set_policy(POLICY_PRIORITY_AGING, 5000);

    pthread_create(&g_ipc_thread, NULL, ipc_server_thread, &g_request_queue);
    worker_pool_init(workers, &g_request_queue);

    printf("[Main] Engine initialized. Queue Capacity=%d, Workers=%d\n", capacity, workers);

    while (1) {
        sleep(1);
    }

    return 0;
}
