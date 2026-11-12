#include "../include/metrics.h"
#include <string.h>

EngineMetrics g_metrics;

void metrics_init(void) {
    memset(&g_metrics, 0, sizeof(EngineMetrics));
    pthread_mutex_init(&g_metrics.mutex, NULL);
}

void metrics_reset(void) {
    pthread_mutex_lock(&g_metrics.mutex);
    g_metrics.submitted = 0;
    g_metrics.completed = 0;
    g_metrics.success = 0;
    g_metrics.rejected = 0;
    g_metrics.errors = 0;
    g_metrics.total_wait_ms = 0.0;
    g_metrics.max_wait_ms = 0.0;
    pthread_mutex_unlock(&g_metrics.mutex);
}

void metrics_record_submission(void) {
    pthread_mutex_lock(&g_metrics.mutex);
    g_metrics.submitted++;
    pthread_mutex_unlock(&g_metrics.mutex);
}

void metrics_record_completion(long wait_ms, bool success, bool is_rejected) {
    pthread_mutex_lock(&g_metrics.mutex);
    g_metrics.completed++;
    if (success) {
        g_metrics.success++;
    } else if (is_rejected) {
        g_metrics.rejected++;
    } else {
        g_metrics.errors++;
    }

    g_metrics.total_wait_ms += wait_ms;
    if (wait_ms > g_metrics.max_wait_ms) {
        g_metrics.max_wait_ms = (double)wait_ms;
    }
    pthread_mutex_unlock(&g_metrics.mutex);
}

void metrics_get_snapshot(long *submitted, long *completed, long *success, long *rejected, double *avg_wait_ms, double *max_wait_ms) {
    pthread_mutex_lock(&g_metrics.mutex);
    *submitted = g_metrics.submitted;
    *completed = g_metrics.completed;
    *success = g_metrics.success;
    *rejected = g_metrics.rejected;
    *avg_wait_ms = (g_metrics.completed > 0) ? (g_metrics.total_wait_ms / g_metrics.completed) : 0.0;
    *max_wait_ms = g_metrics.max_wait_ms;
    pthread_mutex_unlock(&g_metrics.mutex);
}
