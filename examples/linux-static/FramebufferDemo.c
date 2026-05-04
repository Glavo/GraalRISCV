/*
 * This static musl example renders a small animated scene through the Linux
 * fbdev interface exposed by JRISC-V as /dev/fb0.
 */

#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <sys/ioctl.h>
#include <time.h>
#include <unistd.h>

#define DEMO_WIDTH 320
#define DEMO_HEIGHT 180
#define DEMO_BYTES_PER_PIXEL 4
#define DEMO_FRAME_COUNT 240

#define FBIOGET_VSCREENINFO 0x4600UL
#define FBIOGET_FSCREENINFO 0x4602UL
#define FB_TYPE_PACKED_PIXELS 0
#define FB_VISUAL_TRUECOLOR 2

struct fb_bitfield {
    uint32_t offset;
    uint32_t length;
    uint32_t msb_right;
};

struct fb_var_screeninfo {
    uint32_t xres;
    uint32_t yres;
    uint32_t xres_virtual;
    uint32_t yres_virtual;
    uint32_t xoffset;
    uint32_t yoffset;
    uint32_t bits_per_pixel;
    uint32_t grayscale;
    struct fb_bitfield red;
    struct fb_bitfield green;
    struct fb_bitfield blue;
    struct fb_bitfield transp;
    uint32_t nonstd;
    uint32_t activate;
    uint32_t height;
    uint32_t width;
    uint32_t accel_flags;
    uint32_t pixclock;
    uint32_t left_margin;
    uint32_t right_margin;
    uint32_t upper_margin;
    uint32_t lower_margin;
    uint32_t hsync_len;
    uint32_t vsync_len;
    uint32_t sync;
    uint32_t vmode;
    uint32_t rotate;
    uint32_t colorspace;
    uint32_t reserved[4];
};

struct fb_fix_screeninfo {
    char id[16];
    unsigned long smem_start;
    uint32_t smem_len;
    uint32_t type;
    uint32_t type_aux;
    uint32_t visual;
    uint16_t xpanstep;
    uint16_t ypanstep;
    uint16_t ywrapstep;
    uint16_t padding;
    uint32_t line_length;
    unsigned long mmio_start;
    uint32_t mmio_len;
    uint32_t accel;
    uint16_t capabilities;
    uint16_t reserved[2];
};

_Static_assert(sizeof(struct fb_var_screeninfo) == 160, "fb_var_screeninfo size");
_Static_assert(sizeof(struct fb_fix_screeninfo) == 80, "fb_fix_screeninfo size");

static uint8_t frame[DEMO_WIDTH * DEMO_HEIGHT * DEMO_BYTES_PER_PIXEL];

static int fail(const char *message) {
    puts(message);
    return 1;
}

static int write_all(int fd, const uint8_t *data, size_t length) {
    size_t written = 0;
    while (written < length) {
        ssize_t count = write(fd, data + written, length - written);
        if (count < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -1;
        }
        if (count == 0) {
            return -1;
        }
        written += (size_t) count;
    }
    return 0;
}

static void put_pixel(int x, int y, uint8_t red, uint8_t green, uint8_t blue) {
    size_t offset = ((size_t) y * DEMO_WIDTH + (size_t) x) * DEMO_BYTES_PER_PIXEL;
    frame[offset] = blue;
    frame[offset + 1] = green;
    frame[offset + 2] = red;
    frame[offset + 3] = 0;
}

static void draw_frame(int frame_index) {
    int center_x = 32 + (frame_index * 3) % (DEMO_WIDTH - 64);
    int center_y = 28 + (frame_index * 2) % (DEMO_HEIGHT - 56);

    for (int y = 0; y < DEMO_HEIGHT; y++) {
        for (int x = 0; x < DEMO_WIDTH; x++) {
            uint8_t red = (uint8_t) ((x + frame_index * 2) & 0xff);
            uint8_t green = (uint8_t) ((y * 2 + frame_index) & 0xff);
            uint8_t blue = (uint8_t) (((x ^ y) + frame_index * 3) & 0xff);

            int dx = x - center_x;
            int dy = y - center_y;
            if (dx * dx + dy * dy < 22 * 22) {
                red = 255;
                green = (uint8_t) (220 - (frame_index & 0x3f));
                blue = 32;
            }
            if (x < 3 || y < 3 || x >= DEMO_WIDTH - 3 || y >= DEMO_HEIGHT - 3) {
                red = 255;
                green = 255;
                blue = 255;
            }

            put_pixel(x, y, red, green, blue);
        }
    }
}

static int validate_framebuffer(const struct fb_var_screeninfo *variable, const struct fb_fix_screeninfo *fixed) {
    if (variable->xres != DEMO_WIDTH || variable->yres != DEMO_HEIGHT) {
        return fail("framebuffer-size-mismatch");
    }
    if (variable->bits_per_pixel != 32 || fixed->line_length != DEMO_WIDTH * DEMO_BYTES_PER_PIXEL) {
        return fail("framebuffer-layout-mismatch");
    }
    if (fixed->type != FB_TYPE_PACKED_PIXELS || fixed->visual != FB_VISUAL_TRUECOLOR) {
        return fail("framebuffer-type-mismatch");
    }
    if (variable->red.offset != 16 || variable->red.length != 8
            || variable->green.offset != 8 || variable->green.length != 8
            || variable->blue.offset != 0 || variable->blue.length != 8) {
        return fail("framebuffer-format-mismatch");
    }
    return 0;
}

int main(void) {
    int fd = open("/dev/fb0", O_RDWR);
    if (fd < 0) {
        return fail("framebuffer-open-failed");
    }

    struct fb_var_screeninfo variable;
    memset(&variable, 0, sizeof(variable));
    if (ioctl(fd, FBIOGET_VSCREENINFO, &variable) != 0) {
        close(fd);
        return fail("framebuffer-var-info-failed");
    }

    struct fb_fix_screeninfo fixed;
    memset(&fixed, 0, sizeof(fixed));
    if (ioctl(fd, FBIOGET_FSCREENINFO, &fixed) != 0) {
        close(fd);
        return fail("framebuffer-fixed-info-failed");
    }
    if (validate_framebuffer(&variable, &fixed) != 0) {
        close(fd);
        return 1;
    }

    struct timespec delay = {0, 16000000L};
    for (int index = 0; index < DEMO_FRAME_COUNT; index++) {
        draw_frame(index);
        if (lseek(fd, 0, SEEK_SET) != 0) {
            close(fd);
            return fail("framebuffer-seek-failed");
        }
        if (write_all(fd, frame, sizeof(frame)) != 0) {
            close(fd);
            return fail("framebuffer-write-failed");
        }
        if (nanosleep(&delay, NULL) != 0 && errno != EINTR) {
            close(fd);
            return fail("framebuffer-sleep-failed");
        }
    }

    close(fd);
    puts("framebuffer-demo-ok");
    return 0;
}
