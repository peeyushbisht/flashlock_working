#ifndef IPC_H
#define IPC_H

#include <stdbool.h>

void* ipc_server_thread(void *arg);
void ipc_send_execute(long request_id, int product_id, int quantity, int priority, const char* source, long run_id, int worker_id);
void ipc_broadcast_metrics(long run_id);
void ipc_shutdown(void);

#endif // IPC_H
