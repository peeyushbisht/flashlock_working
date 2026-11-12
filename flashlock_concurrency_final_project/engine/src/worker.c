#include "../include/worker.h"
#include "../include/scheduler.h"
#include "../include/ipc.h"
#include "../include/metrics.h"
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/time.h>

#define EXECUTION_TIMEOUT_MS 10000

static pthread_t *g_workers = NULL;
static int g_num_workers = 0;
static RequestQueue *g_queue = NULL;
static volatile bool g_worker_running = true;

static long long current_timestamp_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return ((long long)tv.tv_sec * 1000LL) + (tv.tv_usec / 1000);
}

static void* worker_routine(void *arg) {
    int worker_id = (int)(intptr_t)arg;

    while (g_worker_running) {
        sem_wait(&g_queue->ready_items);
        if (!g_worker_running) break;

        Request req;
        if (scheduler_pop_next(g_queue, &req)) {
            long long now = current_timestamp_ms();
            long wait_time = (long)(now - req.arrival_ms);

            const char* src_str = (req.source == SOURCE_WEB) ? "WEB" : "SIMULATION";

            // Hand the request to the backend, then hold this worker until the
            // database transaction is confirmed (DONE). This bounds the number of
            // concurrent transactions to the worker count, so the scheduling
            // policy decides who runs next.
            ipc_expect_done(worker_id, req.request_id);
            bool sent = ipc_send_execute(req.request_id, req.product_id, req.quantity, req.priority, src_str, req.run_id, worker_id, wait_time);
            bool confirmed = ipc_wait_done(worker_id, sent ? EXECUTION_TIMEOUT_MS : 0);

            metrics_record_completion(wait_time, confirmed, !confirmed);
        }
    }
    return NULL;
}

void worker_pool_init(int worker_count, RequestQueue *q) {
    g_num_workers = worker_count;
    g_queue = q;
    g_worker_running = true;

    g_workers = (pthread_t*) malloc(sizeof(pthread_t) * worker_count);
    for (int i = 0; i < worker_count; i++) {
        pthread_create(&g_workers[i], NULL, worker_routine, (void*)(intptr_t)i);
    }
    printf("[Worker Pool] Initialized %d worker threads.\n", worker_count);
}

void worker_pool_shutdown(void) {
    g_worker_running = false;
    for (int i = 0; i < g_num_workers; i++) {
        sem_post(&g_queue->ready_items);
    }
    for (int i = 0; i < g_num_workers; i++) {
        pthread_join(g_workers[i], NULL);
    }
    free(g_workers);
    printf("[Worker Pool] Worker threads cleanly shutdown.\n");
}
