#ifndef IPC_H
#define IPC_H

#include <stdbool.h>

// arg must be the shared RequestQueue* that incoming SUBMIT messages are pushed into.
void* ipc_server_thread(void *arg);

// Sends an EXECUTE message (the request reached the front of the queue) to the backend.
// Returns false when no backend client is connected.
bool ipc_send_execute(long request_id, int product_id, int quantity, int priority, const char* source, long run_id, int worker_id, long wait_ms);

// Worker handshake: register the request before sending EXECUTE, then wait for the
// backend's DONE message (sent after the database transaction finishes).
void ipc_expect_done(int worker_id, long request_id);
bool ipc_wait_done(int worker_id, int timeout_ms);

void ipc_broadcast_metrics(long run_id);
void ipc_shutdown(void);

#endif // IPC_H
