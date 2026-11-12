#ifndef WORKER_H
#define WORKER_H

#include "queue.h"

void worker_pool_init(int worker_count, RequestQueue *q);
void worker_pool_shutdown(void);

#endif // WORKER_H
