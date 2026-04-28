/*
 * This static musl example validates basic pthread support on top of the
 * simulator's clone, futex, thread-local-state, and guest-thread integration.
 * It creates two workers, joins them, and verifies both return values and
 * cross-thread atomic updates.
 */

#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>

/* Shared counter used to verify that both worker threads actually ran. */
static atomic_int worker_counter = 0;

/* Worker entry point used by pthread_create. */
static void *worker_main(void *argument) {
    intptr_t increments = (intptr_t) argument;
    for (intptr_t index = 0; index < increments; index++) {
        atomic_fetch_add_explicit(&worker_counter, 1, memory_order_relaxed);
    }
    return (void *) (increments + 1);
}

int main(void) {
    /* Create two guest threads with different workloads and return values. */
    pthread_t first_thread;
    pthread_t second_thread;
    if (pthread_create(&first_thread, NULL, worker_main, (void *) 17) != 0) {
        puts("first-create-failed");
        return 1;
    }
    if (pthread_create(&second_thread, NULL, worker_main, (void *) 23) != 0) {
        puts("second-create-failed");
        return 2;
    }

    /* Joining must wait for completion and copy each guest return value back to the caller. */
    void *first_result = NULL;
    void *second_result = NULL;
    if (pthread_join(first_thread, &first_result) != 0) {
        puts("first-join-failed");
        return 3;
    }
    if (pthread_join(second_thread, &second_result) != 0) {
        puts("second-join-failed");
        return 4;
    }

    /* The relaxed atomic count is deterministic because both joins completed before the load. */
    int counter = atomic_load_explicit(&worker_counter, memory_order_relaxed);
    if (counter != 40 || (intptr_t) first_result != 18 || (intptr_t) second_result != 24) {
        puts("thread-result-failed");
        return 5;
    }

    puts("thread-join-ok");
    return 0;
}
