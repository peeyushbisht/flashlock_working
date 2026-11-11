#ifndef QUEUE_H
#define QUEUE_H

#include <pthread.h>
#include <semaphore.h>
#include <stdbool.h>
#include <sys/types.h>

typedef enum {
    SOURCE_WEB,
    SOURCE_SIMULATION
} RequestSource;

typedef struct {
    long request_id;
    int product_id;
    int quantity;
    int priority;           // 1 = Normal, 2 = Medium, 3 = VIP/High
    long long arrival_ms;   // Milliseconds epoch
    RequestSource source;
    long run_id;
} Request;

typedef struct {
    Request *items;
    int capacity;
    int front;
    int rear;
    int count;
    pthread_mutex_t mutex;
    sem_t free_slots;
    sem_t ready_items;
} RequestQueue;

void queue_init(RequestQueue *q, int capacity);
void queue_destroy(RequestQueue *q);
bool queue_push(RequestQueue *q, Request req);
bool queue_pop_fifo(RequestQueue *q, Request *out_req);
bool queue_pop_priority(RequestQueue *q, Request *out_req);
bool queue_pop_priority_aging(RequestQueue *q, Request *out_req, int aging_interval_ms);

#endif // QUEUE_H
