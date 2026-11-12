#ifndef METRICS_H
#define METRICS_H

#include <pthread.h>
#include <stdbool.h>

typedef struct {
    long submitted;
    long completed;
    long success;
    long rejected;
    long errors;
    double total_wait_ms;
    double max_wait_ms;
    pthread_mutex_t mutex;
} EngineMetrics;

extern EngineMetrics g_metrics;

void metrics_init(void);
void metrics_reset(void);
void metrics_record_submission(void);
void metrics_record_completion(long wait_ms, bool success, bool is_rejected);
void metrics_get_snapshot(long *submitted, long *completed, long *success, long *rejected, double *avg_wait_ms, double *max_wait_ms);

#endif // METRICS_H
