#include "../include/ipc.h"
#include "../include/metrics.h"
#include "../include/scheduler.h"
#include "../include/queue.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <errno.h>
#include <time.h>
#include <sys/time.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <pthread.h>

#define SOCKET_PATH "/tmp/flashlock.sock"
#define BUFFER_SIZE 2048
#define MAX_WORKER_SLOTS 256

static int server_fd = -1;
static int client_fd = -1;
static pthread_mutex_t client_mutex = PTHREAD_MUTEX_INITIALIZER;
static volatile bool g_running = true;

// ---------------------------------------------------------------------------
// Worker <-> backend completion handshake
// ---------------------------------------------------------------------------
typedef struct {
    long request_id;
    bool waiting;
    bool done;
} DoneSlot;

static DoneSlot g_slots[MAX_WORKER_SLOTS];
static pthread_mutex_t g_done_mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t g_done_cv = PTHREAD_COND_INITIALIZER;

void ipc_expect_done(int worker_id, long request_id) {
    pthread_mutex_lock(&g_done_mutex);
    DoneSlot *s = &g_slots[worker_id % MAX_WORKER_SLOTS];
    s->request_id = request_id;
    s->waiting = true;
    s->done = false;
    pthread_mutex_unlock(&g_done_mutex);
}

bool ipc_wait_done(int worker_id, int timeout_ms) {
    struct timespec deadline;
    clock_gettime(CLOCK_REALTIME, &deadline);
    deadline.tv_sec += timeout_ms / 1000;
    deadline.tv_nsec += (long)(timeout_ms % 1000) * 1000000L;
    if (deadline.tv_nsec >= 1000000000L) {
        deadline.tv_sec++;
        deadline.tv_nsec -= 1000000000L;
    }

    pthread_mutex_lock(&g_done_mutex);
    DoneSlot *s = &g_slots[worker_id % MAX_WORKER_SLOTS];
    int rc = 0;
    while (timeout_ms > 0 && !s->done && rc != ETIMEDOUT) {
        rc = pthread_cond_timedwait(&g_done_cv, &g_done_mutex, &deadline);
    }
    bool ok = s->done;
    s->waiting = false;
    pthread_mutex_unlock(&g_done_mutex);
    return ok;
}

static void mark_done(long request_id) {
    pthread_mutex_lock(&g_done_mutex);
    for (int i = 0; i < MAX_WORKER_SLOTS; i++) {
        if (g_slots[i].waiting && g_slots[i].request_id == request_id) {
            g_slots[i].done = true;
        }
    }
    pthread_cond_broadcast(&g_done_cv);
    pthread_mutex_unlock(&g_done_mutex);
}

static void release_all_waiters(void) {
    pthread_mutex_lock(&g_done_mutex);
    for (int i = 0; i < MAX_WORKER_SLOTS; i++) {
        if (g_slots[i].waiting) g_slots[i].done = true;
    }
    pthread_cond_broadcast(&g_done_cv);
    pthread_mutex_unlock(&g_done_mutex);
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
static long long now_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return ((long long)tv.tv_sec * 1000LL) + (tv.tv_usec / 1000);
}

static const char* policy_name(SchedulerPolicy p) {
    switch (p) {
        case POLICY_FIFO: return "FIFO";
        case POLICY_PRIORITY: return "PRIORITY";
        default: return "PRIORITY_AGING";
    }
}

static bool send_to_client(const char *msg) {
    bool ok = false;
    pthread_mutex_lock(&client_mutex);
    if (client_fd >= 0) {
        ok = send(client_fd, msg, strlen(msg), MSG_NOSIGNAL) >= 0;
    }
    pthread_mutex_unlock(&client_mutex);
    return ok;
}

static bool json_get_long(const char *line, const char *key, long *out) {
    char pat[64];
    snprintf(pat, sizeof(pat), "\"%s\"", key);
    const char *p = strstr(line, pat);
    if (!p) return false;
    p += strlen(pat);
    while (*p == ' ' || *p == ':') p++;
    char *end;
    long v = strtol(p, &end, 10);
    if (end == p) return false;
    *out = v;
    return true;
}

static bool json_get_string(const char *line, const char *key, char *out, size_t out_size) {
    char pat[64];
    snprintf(pat, sizeof(pat), "\"%s\"", key);
    const char *p = strstr(line, pat);
    if (!p) return false;
    p += strlen(pat);
    while (*p == ' ' || *p == ':') p++;
    if (*p != '"') return false;
    p++;
    size_t i = 0;
    while (*p && *p != '"' && i + 1 < out_size) out[i++] = *p++;
    out[i] = '\0';
    return true;
}

// ---------------------------------------------------------------------------
// Outgoing messages
// ---------------------------------------------------------------------------
bool ipc_send_execute(long request_id, int product_id, int quantity, int priority, const char* source, long run_id, int worker_id, long wait_ms) {
    char msg[BUFFER_SIZE];
    snprintf(msg, sizeof(msg),
        "{\"type\":\"EXECUTE\",\"requestId\":%ld,\"productId\":%d,\"quantity\":%d,\"priority\":%d,\"source\":\"%s\",\"simulationRunId\":%ld,\"workerId\":%d,\"waitMs\":%ld,\"policy\":\"%s\"}\n",
        request_id, product_id, quantity, priority, source, run_id, worker_id, wait_ms,
        policy_name(g_scheduler_config.policy));
    return send_to_client(msg);
}

void ipc_broadcast_metrics(long run_id) {
    long sub, comp, succ, rej;
    double avg_wait, max_wait;
    metrics_get_snapshot(&sub, &comp, &succ, &rej, &avg_wait, &max_wait);

    char msg[BUFFER_SIZE];
    snprintf(msg, sizeof(msg),
        "{\"type\":\"METRICS\",\"simulationRunId\":%ld,\"submitted\":%ld,\"completed\":%ld,\"success\":%ld,\"rejected\":%ld,\"avgWaitMs\":%.2f,\"maxWaitMs\":%.2f}\n",
        run_id, sub, comp, succ, rej, avg_wait, max_wait);
    send_to_client(msg);
}

// ---------------------------------------------------------------------------
// Incoming messages (newline-delimited JSON from the Spring Boot backend)
// ---------------------------------------------------------------------------
static void handle_line(const char *line, RequestQueue *queue) {
    char type[32];
    if (!json_get_string(line, "type", type, sizeof(type))) return;

    if (strcmp(type, "SUBMIT") == 0) {
        Request req;
        long v;
        memset(&req, 0, sizeof(req));
        if (!json_get_long(line, "requestId", &req.request_id)) return;
        req.product_id = json_get_long(line, "productId", &v) ? (int)v : 0;
        req.quantity = json_get_long(line, "quantity", &v) ? (int)v : 1;
        req.priority = json_get_long(line, "priority", &v) ? (int)v : 1;
        req.run_id = json_get_long(line, "simulationRunId", &v) ? v : 0;

        char source[32] = "WEB";
        json_get_string(line, "source", source, sizeof(source));
        req.source = (strcmp(source, "SIMULATION") == 0) ? SOURCE_SIMULATION : SOURCE_WEB;
        req.arrival_ms = now_ms();

        // Never block the IPC reader: it also has to receive DONE messages.
        if (queue && queue_try_push(queue, req)) {
            metrics_record_submission();
        } else {
            char msg[256];
            snprintf(msg, sizeof(msg),
                "{\"type\":\"REJECT\",\"requestId\":%ld,\"reason\":\"QUEUE_FULL\"}\n", req.request_id);
            send_to_client(msg);
        }
    } else if (strcmp(type, "DONE") == 0) {
        long request_id;
        if (json_get_long(line, "requestId", &request_id)) {
            mark_done(request_id);
        }
    } else if (strcmp(type, "CONFIG") == 0) {
        char policy[32] = "PRIORITY_AGING";
        long aging = 5000;
        json_get_string(line, "policy", policy, sizeof(policy));
        json_get_long(line, "agingIntervalMs", &aging);
        if (aging < 1) aging = 5000;

        SchedulerPolicy p = POLICY_PRIORITY_AGING;
        if (strcmp(policy, "FIFO") == 0) p = POLICY_FIFO;
        else if (strcmp(policy, "PRIORITY") == 0) p = POLICY_PRIORITY;

        scheduler_set_policy(p, (int)aging);
        metrics_reset();

        char msg[256];
        snprintf(msg, sizeof(msg),
            "{\"type\":\"CONFIG_ACK\",\"policy\":\"%s\",\"agingIntervalMs\":%ld}\n", policy_name(p), aging);
        send_to_client(msg);
        printf("[IPC Engine] Scheduling policy set to %s (aging interval %ld ms)\n", policy_name(p), aging);
    }
}

void* ipc_server_thread(void *arg) {
    RequestQueue *queue = (RequestQueue*)arg;
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
        size_t len = 0;
        ssize_t bytes_read;
        while ((bytes_read = read(new_fd, buffer + len, sizeof(buffer) - 1 - len)) > 0) {
            len += (size_t)bytes_read;
            buffer[len] = '\0';

            char *start = buffer;
            char *nl;
            while ((nl = strchr(start, '\n')) != NULL) {
                *nl = '\0';
                if (*start) handle_line(start, queue);
                start = nl + 1;
            }
            len = strlen(start);
            memmove(buffer, start, len + 1);
            if (len >= sizeof(buffer) - 1) len = 0; // discard an oversized, unterminated line
        }

        pthread_mutex_lock(&client_mutex);
        if (client_fd == new_fd) {
            close(client_fd);
            client_fd = -1;
        }
        printf("[IPC Engine] Spring Boot Client disconnected.\n");
        pthread_mutex_unlock(&client_mutex);

        // Nobody is left to confirm in-flight requests; free the workers.
        release_all_waiters();
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
