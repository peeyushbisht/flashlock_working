CC = gcc
CFLAGS = -Wall -Wextra -Iinclude -pthread -O2
TARGET = bin/flashlock_engine
SRCS = src/main.c src/queue.c src/scheduler.c src/worker.c src/ipc.c src/metrics.c
OBJS = $(SRCS:.c=.o)

all: $(TARGET)

$(TARGET): $(OBJS)
	mkdir -p bin
	$(CC) $(CFLAGS) -o $(TARGET) $(OBJS)

%.o: %.c
	$(CC) $(CFLAGS) -c $< -o $@

clean:
	rm -rf src/*.o bin/

.PHONY: all clean
