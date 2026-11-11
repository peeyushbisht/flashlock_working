#include "../include/queue.h"
#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

static long long current_timestamp_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return ((long long)tv.tv_sec * 1000LL) + (tv.tv_usec / 1000);
}

void queue_init(RequestQueue *q, int capacity) {
    q->capacity = capacity;
    q->items = (Request*) malloc(sizeof(Request) * capacity);
    q->front = 0;
    q->rear = 0;
    q->count = 0;
    pthread_mutex_init(&q->mutex, NULL);
    sem_init(&q->free_slots, 0, capacity);
    sem_init(&q->ready_items, 0, 0);
}

void queue_destroy(RequestQueue *q) {
    pthread_mutex_destroy(&q->mutex);
    sem_destroy(&q->free_slots);
    sem_destroy(&q->ready_items);
    free(q->items);
}

bool queue_push(RequestQueue *q, Request req) {
    sem_wait(&q->free_slots);
    pthread_mutex_lock(&q->mutex);

    q->items[q->rear] = req;
    q->rear = (q->rear + 1) % q->capacity;
    q->count++;

    pthread_mutex_unlock(&q->mutex);
    sem_post(&q->ready_items);
    return true;
}

bool queue_pop_fifo(RequestQueue *q, Request *out_req) {
    pthread_mutex_lock(&q->mutex);
    if (q->count == 0) {
        pthread_mutex_unlock(&q->mutex);
        return false;
    }

    *out_req = q->items[q->front];
    q->front = (q->front + 1) % q->capacity;
    q->count--;

    pthread_mutex_unlock(&q->mutex);
    sem_post(&q->free_slots);
    return true;
}

bool queue_pop_priority(RequestQueue *q, Request *out_req) {
    pthread_mutex_lock(&q->mutex);
    if (q->count == 0) {
        pthread_mutex_unlock(&q->mutex);
        return false;
    }

    int best_index = -1;
    int highest_prio = -1;
    long long earliest_arrival = 0;

    for (int i = 0; i < q->count; i++) {
        int idx = (q->front + i) % q->capacity;
        Request *r = &q->items[idx];
        if (r->priority > highest_prio || 
           (r->priority == highest_prio && r->arrival_ms < earliest_arrival)) {
            highest_prio = r->priority;
            earliest_arrival = r->arrival_ms;
            best_index = idx;
        }
    }

    *out_req = q->items[best_index];

    int current = best_index;
    while (current != q->front) {
        int prev = (current - 1 + q->capacity) % q->capacity;
        q->items[current] = q->items[prev];
        current = prev;
    }
    q->front = (q->front + 1) % q->capacity;
    q->count--;

    pthread_mutex_unlock(&q->mutex);
    sem_post(&q->free_slots);
    return true;
}

bool queue_pop_priority_aging(RequestQueue *q, Request *out_req, int aging_interval_ms) {
    pthread_mutex_lock(&q->mutex);
    if (q->count == 0) {
        pthread_mutex_unlock(&q->mutex);
        return false;
    }

    long long now = current_timestamp_ms();
    int best_index = -1;
    int highest_effective_prio = -1;
    long long earliest_arrival = 0;

    for (int i = 0; i < q->count; i++) {
        int idx = (q->front + i) % q->capacity;
        Request *r = &q->items[idx];

        long long wait_time = now - r->arrival_ms;
        int aging_bonus = (aging_interval_ms > 0) ? (int)(wait_time / aging_interval_ms) : 0;
        int effective_prio = r->priority + aging_bonus;

        if (effective_prio > highest_effective_prio || 
           (effective_prio == highest_effective_prio && r->arrival_ms < earliest_arrival)) {
            highest_effective_prio = effective_prio;
            earliest_arrival = r->arrival_ms;
            best_index = idx;
        }
    }

    *out_req = q->items[best_index];

    int current = best_index;
    while (current != q->front) {
        int prev = (current - 1 + q->capacity) % q->capacity;
        q->items[current] = q->items[prev];
        current = prev;
    }
    q->front = (q->front + 1) % q->capacity;
    q->count--;

    pthread_mutex_unlock(&q->mutex);
    sem_post(&q->free_slots);
    return true;
}
