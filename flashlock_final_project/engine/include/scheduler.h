#ifndef SCHEDULER_H
#define SCHEDULER_H

#include "queue.h"

typedef enum {
    POLICY_FIFO,
    POLICY_PRIORITY,
    POLICY_PRIORITY_AGING
} SchedulerPolicy;

typedef struct {
    SchedulerPolicy policy;
    int aging_interval_ms;
} SchedulerConfig;

extern SchedulerConfig g_scheduler_config;

void scheduler_set_policy(SchedulerPolicy policy, int aging_interval_ms);
bool scheduler_pop_next(RequestQueue *q, Request *out_req);

#endif // SCHEDULER_H
