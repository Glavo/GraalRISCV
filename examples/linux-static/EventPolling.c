#include <errno.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/epoll.h>
#include <sys/eventfd.h>
#include <unistd.h>

static int fail(const char *message) {
    puts(message);
    return 1;
}

int main(void) {
    int event_fd = eventfd(0, EFD_NONBLOCK | EFD_CLOEXEC);
    if (event_fd < 0) {
        return fail("eventfd-failed");
    }

    uint64_t value = 0;
    ssize_t count = read(event_fd, &value, sizeof(value));
    if (count != -1 || errno != EAGAIN) {
        return fail("eventfd-empty-read-failed");
    }

    int epoll_fd = epoll_create1(EPOLL_CLOEXEC);
    if (epoll_fd < 0) {
        return fail("epoll-create-failed");
    }

    struct epoll_event event;
    memset(&event, 0, sizeof(event));
    event.events = EPOLLIN;
    event.data.u64 = 0x1122334455667788ULL;
    if (epoll_ctl(epoll_fd, EPOLL_CTL_ADD, event_fd, &event) != 0) {
        return fail("epoll-add-failed");
    }

    struct epoll_event ready_event;
    memset(&ready_event, 0, sizeof(ready_event));
    int ready = epoll_wait(epoll_fd, &ready_event, 1, 0);
    if (ready != 0) {
        return fail("epoll-empty-wait-failed");
    }

    value = 7;
    if (write(event_fd, &value, sizeof(value)) != sizeof(value)) {
        return fail("eventfd-write-failed");
    }

    memset(&ready_event, 0, sizeof(ready_event));
    ready = epoll_wait(epoll_fd, &ready_event, 1, 0);
    if (ready != 1) {
        return fail("epoll-ready-wait-failed");
    }
    if ((ready_event.events & EPOLLIN) == 0 || ready_event.data.u64 != 0x1122334455667788ULL) {
        return fail("epoll-ready-data-failed");
    }

    value = 0;
    if (read(event_fd, &value, sizeof(value)) != sizeof(value) || value != 7) {
        return fail("eventfd-read-failed");
    }

    ready = epoll_wait(epoll_fd, &ready_event, 1, 0);
    if (ready != 0) {
        return fail("epoll-drained-wait-failed");
    }

    puts("event-polling-ok");
    return 0;
}
