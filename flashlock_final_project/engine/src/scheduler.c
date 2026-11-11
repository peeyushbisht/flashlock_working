#include "../include/scheduler.h"

SchedulerConfig g_scheduler_config = {
    .policy = POLICY_PRIORITY_AGING,
    .aging_interval_ms = 5000
};

void scheduler_set_policy(SchedulerPolicy policy, int aging_interval_ms) {
    g_scheduler_config.policy = policy;
    g_scheduler_config.aging_interval_ms = aging_interval_ms;
}

bool scheduler_pop_next(RequestQueue *q, Request *out_req) {
    switch (g_scheduler_config.policy) {
        case POLICY_FIFO:
            return queue_pop_fifo(q, out_req);
        case POLICY_PRIORITY:
            return queue_pop_priority(q, out_req);
        case POLICY_PRIORITY_AGING:
            return queue_pop_priority_aging(q, out_req, g_scheduler_config.aging_interval_ms);
        default:
            return queue_pop_fifo(q, out_req);
    }
}
