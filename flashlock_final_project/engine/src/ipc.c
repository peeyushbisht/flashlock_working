#include "../include/ipc.h"
#include "../include/metrics.h"
#include "../include/scheduler.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <pthread.h>

#define SOCKET_PATH "/tmp/flashlock.sock"
#define BUFFER_SIZE 2048

static int server_fd = -1;
static int client_fd = -1;
static pthread_mutex_t client_mutex = PTHREAD_MUTEX_INITIALIZER;
static volatile bool g_running = true;

void ipc_send_execute(long request_id, int product_id, int quantity, int priority, const char* source, long run_id, int worker_id) {
    pthread_mutex_lock(&client_mutex);
    if (client_fd >= 0) {
        char msg[BUFFER_SIZE];
        snprintf(msg, sizeof(msg),
            "{\"type\":\"EXECUTE\",\"requestId\":%ld,\"productId\":%d,\"quantity\":%d,\"priority\":%d,\"source\":\"%s\",\"simulationRunId\":%ld,\"workerId\":%d}\n",
            request_id, product_id, quantity, priority, source, run_id, worker_id);
        write(client_fd, msg, strlen(msg));
    }
    pthread_mutex_unlock(&client_mutex);
}

void ipc_broadcast_metrics(long run_id) {
    long sub, comp, succ, rej;
    double avg_wait, max_wait;
    metrics_get_snapshot(&sub, &comp, &succ, &rej, &avg_wait, &max_wait);

    pthread_mutex_lock(&client_mutex);
    if (client_fd >= 0) {
        char msg[BUFFER_SIZE];
        snprintf(msg, sizeof(msg),
            "{\"type\":\"METRICS\",\"simulationRunId\":%ld,\"submitted\":%ld,\"completed\":%ld,\"success\":%ld,\"rejected\":%ld,\"avgWaitMs\":%.2f,\"maxWaitMs\":%.2f}\n",
            run_id, sub, comp, succ, rej, avg_wait, max_wait);
        write(client_fd, msg, strlen(msg));
    }
    pthread_mutex_unlock(&client_mutex);
}

void* ipc_server_thread(void *arg) {
    struct sockaddr_un addr;

    unlink(SOCKET_PATH);
    if ((server_fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1) {
        perror("IPC socket creation failed");
        return NULL;
    }

    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, SOCKET_PATH, sizeof(addr.sun_path) - 1);

    if (bind(server_fd, (struct sockaddr*)&addr, sizeof(addr)) == -1) {
        perror("IPC bind failed");
        return NULL;
    }

    if (listen(server_fd, 5) == -1) {
        perror("IPC listen failed");
        return NULL;
    }

    printf("[IPC Engine] Server listening on %s\n", SOCKET_PATH);

    while (g_running) {
        int new_fd = accept(server_fd, NULL, NULL);
        if (new_fd < 0) {
            if (!g_running) break;
            continue;
        }

        pthread_mutex_lock(&client_mutex);
        if (client_fd >= 0) close(client_fd);
        client_fd = new_fd;
        printf("[IPC Engine] Spring Boot Client connected.\n");
        pthread_mutex_unlock(&client_mutex);

        char buffer[BUFFER_SIZE];
        ssize_t bytes_read;
        while ((bytes_read = read(client_fd, buffer, sizeof(buffer) - 1)) > 0) {
            buffer[bytes_read] = '\0';
        }

        pthread_mutex_lock(&client_mutex);
        close(client_fd);
        client_fd = -1;
        printf("[IPC Engine] Spring Boot Client disconnected.\n");
        pthread_mutex_unlock(&client_mutex);
    }

    return NULL;
}

void ipc_shutdown(void) {
    g_running = false;
    pthread_mutex_lock(&client_mutex);
    if (client_fd >= 0) {
        close(client_fd);
        client_fd = -1;
    }
    pthread_mutex_unlock(&client_mutex);

    if (server_fd >= 0) {
        close(server_fd);
        server_fd = -1;
    }
    unlink(SOCKET_PATH);
}
