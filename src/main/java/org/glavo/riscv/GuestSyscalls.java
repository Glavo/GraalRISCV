package org.glavo.riscv;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

/// Handles the small Linux-compatible syscall subset exposed by the simulator.
@NotNullByDefault
public final class GuestSyscalls implements AutoCloseable {
    /// The Linux RISC-V syscall number for `getcwd`.
    private static final long SYS_GETCWD = 17;

    /// The Linux RISC-V syscall number for `eventfd2`.
    private static final long SYS_EVENTFD2 = 19;

    /// The Linux RISC-V syscall number for `epoll_create1`.
    private static final long SYS_EPOLL_CREATE1 = 20;

    /// The Linux RISC-V syscall number for `epoll_ctl`.
    private static final long SYS_EPOLL_CTL = 21;

    /// The Linux RISC-V syscall number for `epoll_pwait`.
    private static final long SYS_EPOLL_PWAIT = 22;

    /// The Linux RISC-V syscall number for `dup`.
    private static final long SYS_DUP = 23;

    /// The Linux RISC-V syscall number for `dup3`.
    private static final long SYS_DUP3 = 24;

    /// The Linux RISC-V syscall number for `fcntl`.
    private static final long SYS_FCNTL = 25;

    /// The Linux RISC-V syscall number for `ioctl`.
    private static final long SYS_IOCTL = 29;

    /// The Linux RISC-V syscall number for `mkdirat`.
    private static final long SYS_MKDIRAT = 34;

    /// The Linux RISC-V syscall number for `unlinkat`.
    private static final long SYS_UNLINKAT = 35;

    /// The Linux RISC-V syscall number for `renameat`.
    private static final long SYS_RENAMEAT = 38;

    /// The Linux RISC-V syscall number for `statfs`.
    private static final long SYS_STATFS = 43;

    /// The Linux RISC-V syscall number for `fstatfs`.
    private static final long SYS_FSTATFS = 44;

    /// The Linux RISC-V syscall number for `truncate`.
    private static final long SYS_TRUNCATE = 45;

    /// The Linux RISC-V syscall number for `ftruncate`.
    private static final long SYS_FTRUNCATE = 46;

    /// The Linux RISC-V syscall number for `faccessat`.
    private static final long SYS_FACCESSAT = 48;

    /// The Linux RISC-V syscall number for `chdir`.
    private static final long SYS_CHDIR = 49;

    /// The Linux RISC-V syscall number for `fchdir`.
    private static final long SYS_FCHDIR = 50;

    /// The Linux RISC-V syscall number for `openat`.
    private static final long SYS_OPENAT = 56;

    /// The Linux RISC-V syscall number for `close`.
    private static final long SYS_CLOSE = 57;

    /// The Linux RISC-V syscall number for `pipe2`.
    private static final long SYS_PIPE2 = 59;

    /// The Linux RISC-V syscall number for `getdents64`.
    private static final long SYS_GETDENTS64 = 61;

    /// The Linux RISC-V syscall number for `lseek`.
    private static final long SYS_LSEEK = 62;

    /// The Linux RISC-V syscall number for `read`.
    private static final long SYS_READ = 63;

    /// The Linux RISC-V syscall number for `write`.
    private static final long SYS_WRITE = 64;

    /// The Linux RISC-V syscall number for `readv`.
    private static final long SYS_READV = 65;

    /// The Linux RISC-V syscall number for `writev`.
    private static final long SYS_WRITEV = 66;

    /// The Linux RISC-V syscall number for `pread64`.
    private static final long SYS_PREAD64 = 67;

    /// The Linux RISC-V syscall number for `pwrite64`.
    private static final long SYS_PWRITE64 = 68;

    /// The Linux RISC-V syscall number for `readlinkat`.
    private static final long SYS_READLINKAT = 78;

    /// The Linux RISC-V syscall number for `newfstatat`.
    private static final long SYS_NEWFSTATAT = 79;

    /// The Linux RISC-V syscall number for `fstat`.
    private static final long SYS_FSTAT = 80;

    /// The Linux RISC-V syscall number for `sync`.
    private static final long SYS_SYNC = 81;

    /// The Linux RISC-V syscall number for `fsync`.
    private static final long SYS_FSYNC = 82;

    /// The Linux RISC-V syscall number for `fdatasync`.
    private static final long SYS_FDATASYNC = 83;

    /// The Linux RISC-V syscall number for `exit`.
    private static final long SYS_EXIT = 93;

    /// The Linux RISC-V syscall number for `exit_group`.
    private static final long SYS_EXIT_GROUP = 94;

    /// The Linux RISC-V syscall number for `set_tid_address`.
    private static final long SYS_SET_TID_ADDRESS = 96;

    /// The Linux RISC-V syscall number for `futex`.
    private static final long SYS_FUTEX = 98;

    /// The Linux RISC-V syscall number for `set_robust_list`.
    private static final long SYS_SET_ROBUST_LIST = 99;

    /// The Linux RISC-V syscall number for `get_robust_list`.
    private static final long SYS_GET_ROBUST_LIST = 100;

    /// The Linux RISC-V syscall number for `nanosleep`.
    private static final long SYS_NANOSLEEP = 101;

    /// The Linux RISC-V syscall number for `clock_gettime`.
    private static final long SYS_CLOCK_GETTIME = 113;

    /// The Linux RISC-V syscall number for `clock_getres`.
    private static final long SYS_CLOCK_GETRES = 114;

    /// The Linux RISC-V syscall number for `clock_nanosleep`.
    private static final long SYS_CLOCK_NANOSLEEP = 115;

    /// The Linux RISC-V syscall number for `sched_getaffinity`.
    private static final long SYS_SCHED_GETAFFINITY = 123;

    /// The Linux RISC-V syscall number for `sched_yield`.
    private static final long SYS_SCHED_YIELD = 124;

    /// The Linux RISC-V syscall number for `kill`.
    private static final long SYS_KILL = 129;

    /// The Linux RISC-V syscall number for `tkill`.
    private static final long SYS_TKILL = 130;

    /// The Linux RISC-V syscall number for `tgkill`.
    private static final long SYS_TGKILL = 131;

    /// The Linux RISC-V syscall number for `sigaltstack`.
    private static final long SYS_SIGALTSTACK = 132;

    /// The Linux RISC-V syscall number for `rt_sigaction`.
    private static final long SYS_RT_SIGACTION = 134;

    /// The Linux RISC-V syscall number for `rt_sigprocmask`.
    private static final long SYS_RT_SIGPROCMASK = 135;

    /// The Linux RISC-V syscall number for `times`.
    private static final long SYS_TIMES = 153;

    /// The Linux RISC-V syscall number for `getpgid`.
    private static final long SYS_GETPGID = 155;

    /// The Linux RISC-V syscall number for `setsid`.
    private static final long SYS_SETSID = 157;

    /// The Linux RISC-V syscall number for `uname`.
    private static final long SYS_UNAME = 160;

    /// The Linux RISC-V syscall number for `getrusage`.
    private static final long SYS_GETRUSAGE = 165;

    /// The Linux RISC-V syscall number for `prctl`.
    private static final long SYS_PRCTL = 167;

    /// The Linux RISC-V syscall number for `getcpu`.
    private static final long SYS_GETCPU = 168;

    /// The Linux RISC-V syscall number for `gettimeofday`.
    private static final long SYS_GETTIMEOFDAY = 169;

    /// The Linux RISC-V syscall number for `getpid`.
    private static final long SYS_GETPID = 172;

    /// The Linux RISC-V syscall number for `getppid`.
    private static final long SYS_GETPPID = 173;

    /// The Linux RISC-V syscall number for `getuid`.
    private static final long SYS_GETUID = 174;

    /// The Linux RISC-V syscall number for `geteuid`.
    private static final long SYS_GETEUID = 175;

    /// The Linux RISC-V syscall number for `getgid`.
    private static final long SYS_GETGID = 176;

    /// The Linux RISC-V syscall number for `getegid`.
    private static final long SYS_GETEGID = 177;

    /// The Linux RISC-V syscall number for `gettid`.
    private static final long SYS_GETTID = 178;

    /// The Linux RISC-V syscall number for `brk`.
    private static final long SYS_BRK = 214;

    /// The Linux RISC-V syscall number for `munmap`.
    private static final long SYS_MUNMAP = 215;

    /// The Linux RISC-V syscall number for `clone`.
    private static final long SYS_CLONE = 220;

    /// The Linux RISC-V syscall number for `mmap`.
    private static final long SYS_MMAP = 222;

    /// The Linux RISC-V syscall number for `mprotect`.
    private static final long SYS_MPROTECT = 226;

    /// The Linux RISC-V syscall number for `madvise`.
    private static final long SYS_MADVISE = 233;

    /// The Linux RISC-V syscall number for `riscv_hwprobe`.
    private static final long SYS_RISCV_HWPROBE = 258;

    /// The Linux RISC-V syscall number for `prlimit64`.
    private static final long SYS_PRLIMIT64 = 261;

    /// The Linux RISC-V syscall number for `syncfs`.
    private static final long SYS_SYNCFS = 267;

    /// The Linux RISC-V syscall number for `renameat2`.
    private static final long SYS_RENAMEAT2 = 276;

    /// The Linux RISC-V syscall number for `getrandom`.
    private static final long SYS_GETRANDOM = 278;

    /// The Linux RISC-V syscall number for `membarrier`.
    private static final long SYS_MEMBARRIER = 283;

    /// The Linux RISC-V syscall number for `statx`.
    private static final long SYS_STATX = 291;

    /// The Linux RISC-V syscall number for `rseq`.
    private static final long SYS_RSEQ = 293;

    /// The Linux RISC-V syscall number for `faccessat2`.
    private static final long SYS_FACCESSAT2 = 439;

    /// Linux `EBADF` as a raw negative syscall result.
    private static final long EBADF = -9;

    /// Linux `ENOENT` as a raw negative syscall result.
    private static final long ENOENT = -2;

    /// Linux `ESRCH` as a raw negative syscall result.
    private static final long ESRCH = -3;

    /// Linux `EACCES` as a raw negative syscall result.
    private static final long EACCES = -13;

    /// Linux `EPERM` as a raw negative syscall result.
    private static final long EPERM = -1;

    /// Linux `ENOMEM` as a raw negative syscall result.
    private static final long ENOMEM = -12;

    /// Linux `EEXIST` as a raw negative syscall result.
    private static final long EEXIST = -17;

    /// Linux `EINTR` as a raw negative syscall result.
    private static final long EINTR = -4;

    /// Linux `EAGAIN` as a raw negative syscall result.
    private static final long EAGAIN = -11;

    /// Linux `ENODEV` as a raw negative syscall result.
    private static final long ENODEV = -19;

    /// Linux `ENOTDIR` as a raw negative syscall result.
    private static final long ENOTDIR = -20;

    /// Linux `EISDIR` as a raw negative syscall result.
    private static final long EISDIR = -21;

    /// Linux `EINVAL` as a raw negative syscall result.
    private static final long EINVAL = -22;

    /// Linux `ENOTTY` as a raw negative syscall result.
    private static final long ENOTTY = -25;

    /// Linux `ERANGE` as a raw negative syscall result.
    private static final long ERANGE = -34;

    /// Linux `ENAMETOOLONG` as a raw negative syscall result.
    private static final long ENAMETOOLONG = -36;

    /// Linux `ENOSYS` as a raw negative syscall result.
    private static final long ENOSYS = -38;

    /// Linux `ENOTSUP` as a raw negative syscall result.
    private static final long ENOTSUP = -95;

    /// Linux `ENOTEMPTY` as a raw negative syscall result.
    private static final long ENOTEMPTY = -39;

    /// Linux `ESPIPE` as a raw negative syscall result.
    private static final long ESPIPE = -29;

    /// Linux `EPIPE` as a raw negative syscall result.
    private static final long EPIPE = -32;

    /// Linux `ETIMEDOUT` as a raw negative syscall result.
    private static final long ETIMEDOUT = -110;

    /// The maximum Linux `iovcnt` accepted by `readv` and `writev`.
    private static final long IOV_MAX = 1024;

    /// The byte size of one Linux RISC-V 64-bit `struct iovec`.
    private static final int IOVEC_SIZE = 16;

    /// The byte offset of `d_ino` inside Linux `struct linux_dirent64`.
    private static final int DIRENT64_INODE_OFFSET = 0;

    /// The byte offset of `d_off` inside Linux `struct linux_dirent64`.
    private static final int DIRENT64_NEXT_OFFSET = 8;

    /// The byte offset of `d_reclen` inside Linux `struct linux_dirent64`.
    private static final int DIRENT64_RECORD_LENGTH_OFFSET = 16;

    /// The byte offset of `d_type` inside Linux `struct linux_dirent64`.
    private static final int DIRENT64_TYPE_OFFSET = 18;

    /// The byte offset of `d_name` inside Linux `struct linux_dirent64`.
    private static final int DIRENT64_NAME_OFFSET = 19;

    /// The record alignment used by Linux `struct linux_dirent64`.
    private static final int DIRENT64_RECORD_ALIGNMENT = Long.BYTES;

    /// Linux `DT_UNKNOWN` directory entry type.
    private static final byte DIRECTORY_ENTRY_UNKNOWN = 0;

    /// Linux `DT_DIR` directory entry type.
    private static final byte DIRECTORY_ENTRY_DIRECTORY = 4;

    /// Linux `DT_REG` directory entry type.
    private static final byte DIRECTORY_ENTRY_REGULAR_FILE = 8;

    /// Linux `DT_LNK` directory entry type.
    private static final byte DIRECTORY_ENTRY_SYMBOLIC_LINK = 10;

    /// The byte offset of `iov_base` inside `struct iovec`.
    private static final int IOVEC_BASE_OFFSET = 0;

    /// The byte offset of `iov_len` inside `struct iovec`.
    private static final int IOVEC_LENGTH_OFFSET = 8;

    /// Linux `F_OK` access mode.
    private static final long F_OK = 0;

    /// Linux `X_OK` access mode bit.
    private static final long X_OK = 1;

    /// Linux `W_OK` access mode bit.
    private static final long W_OK = 2;

    /// Linux `R_OK` access mode bit.
    private static final long R_OK = 4;

    /// Linux access mode bit mask accepted by `faccessat`.
    private static final long ACCESS_MODE_MASK = R_OK | W_OK | X_OK;

    /// The Linux `AT_FDCWD` pseudo file descriptor for path-based syscalls.
    private static final long AT_FDCWD = -100;

    /// Linux `AT_SYMLINK_NOFOLLOW`.
    private static final long AT_SYMLINK_NOFOLLOW = 0x100;

    /// Linux `AT_EACCESS`.
    private static final long AT_EACCESS = 0x200;

    /// Linux `AT_REMOVEDIR`.
    private static final long AT_REMOVEDIR = 0x200;

    /// Linux `AT_NO_AUTOMOUNT`.
    private static final long AT_NO_AUTOMOUNT = 0x800;

    /// Linux `AT_EMPTY_PATH`.
    private static final long AT_EMPTY_PATH = 0x1000;

    /// Linux `AT_STATX_FORCE_SYNC`.
    private static final long AT_STATX_FORCE_SYNC = 0x2000;

    /// Linux `AT_STATX_DONT_SYNC`.
    private static final long AT_STATX_DONT_SYNC = 0x4000;

    /// Linux `newfstatat` flags accepted by this simulator.
    private static final long SUPPORTED_NEWFSTATAT_FLAGS = AT_SYMLINK_NOFOLLOW | AT_EMPTY_PATH;

    /// Linux `statx` flags accepted by this simulator.
    private static final long SUPPORTED_STATX_FLAGS =
            AT_EMPTY_PATH | AT_SYMLINK_NOFOLLOW | AT_NO_AUTOMOUNT | AT_STATX_FORCE_SYNC | AT_STATX_DONT_SYNC;

    /// Linux `faccessat2` flags accepted by this simulator.
    private static final long SUPPORTED_FACCESSAT2_FLAGS = AT_SYMLINK_NOFOLLOW | AT_EACCESS | AT_EMPTY_PATH;

    /// Linux `O_ACCMODE`.
    private static final long O_ACCMODE = 0x3;

    /// Linux `O_RDONLY`.
    private static final long O_RDONLY = 0;

    /// Linux `O_WRONLY`.
    private static final long O_WRONLY = 1;

    /// Linux `O_RDWR`.
    private static final long O_RDWR = 2;

    /// Linux `O_CREAT`.
    private static final long O_CREAT = 00000100L;

    /// Linux `O_EXCL`.
    private static final long O_EXCL = 00000200L;

    /// Linux `O_TRUNC`.
    private static final long O_TRUNC = 00001000L;

    /// Linux `O_APPEND`.
    private static final long O_APPEND = 00002000L;

    /// Linux `O_NONBLOCK`.
    private static final long O_NONBLOCK = 00004000L;

    /// Linux `O_DIRECTORY`.
    private static final long O_DIRECTORY = 00200000L;

    /// Linux `O_CLOEXEC`.
    private static final long O_CLOEXEC = 02000000L;

    /// Linux `EFD_SEMAPHORE`.
    private static final long EFD_SEMAPHORE = 1;

    /// Linux flags accepted by `eventfd2`.
    private static final long SUPPORTED_EVENTFD2_FLAGS = EFD_SEMAPHORE | O_NONBLOCK | O_CLOEXEC;

    /// Linux flags accepted by `epoll_create1`.
    private static final long SUPPORTED_EPOLL_CREATE1_FLAGS = O_CLOEXEC;

    /// Linux `EPOLL_CTL_ADD`.
    private static final int EPOLL_CTL_ADD = 1;

    /// Linux `EPOLL_CTL_DEL`.
    private static final int EPOLL_CTL_DEL = 2;

    /// Linux `EPOLL_CTL_MOD`.
    private static final int EPOLL_CTL_MOD = 3;

    /// Linux `EPOLLIN`.
    private static final int EPOLLIN = 0x001;

    /// Linux `EPOLLOUT`.
    private static final int EPOLLOUT = 0x004;

    /// Linux `EPOLLERR`.
    private static final int EPOLLERR = 0x008;

    /// Linux `EPOLLHUP`.
    private static final int EPOLLHUP = 0x010;

    /// The byte size of Linux RISC-V 64-bit `struct epoll_event`.
    private static final int EPOLL_EVENT_SIZE = 16;

    /// The byte offset of `events` inside Linux RISC-V 64-bit `struct epoll_event`.
    private static final int EPOLL_EVENT_EVENTS_OFFSET = 0;

    /// The byte offset of `data` inside Linux RISC-V 64-bit `struct epoll_event`.
    private static final int EPOLL_EVENT_DATA_OFFSET = Long.BYTES;

    /// Linux flags accepted by `pipe2`.
    private static final long SUPPORTED_PIPE2_FLAGS = O_NONBLOCK | O_CLOEXEC;

    /// Linux flags accepted by `dup3`.
    private static final long SUPPORTED_DUP3_FLAGS = O_CLOEXEC;

    /// Linux `F_GETFD`.
    private static final long F_GETFD = 1;

    /// Linux `F_SETFD`.
    private static final long F_SETFD = 2;

    /// Linux `F_GETFL`.
    private static final long F_GETFL = 3;

    /// Linux `F_SETFL`.
    private static final long F_SETFL = 4;

    /// The maximum guest path length accepted by `openat`, including the terminator.
    private static final int PATH_MAX = 4096;

    /// The Linux generic tty `TCGETS` ioctl request number.
    private static final long TCGETS = 0x5401;

    /// The Linux generic tty `TIOCGWINSZ` ioctl request number.
    private static final long TIOCGWINSZ = 0x5413;

    /// The byte size of Linux generic `struct termios`.
    private static final int TERMIOS_SIZE = 36;

    /// The byte size of Linux generic `struct winsize`.
    private static final int WINSIZE_SIZE = 8;

    /// The default terminal row count returned by `TIOCGWINSZ`.
    private static final short DEFAULT_TERMINAL_ROWS = 24;

    /// The default terminal column count returned by `TIOCGWINSZ`.
    private static final short DEFAULT_TERMINAL_COLUMNS = 80;

    /// The byte size of Linux generic 64-bit `struct stat`.
    private static final int STAT_SIZE = 128;

    /// The byte size of Linux generic 64-bit `struct statfs`.
    private static final int STATFS_SIZE = 120;

    /// The byte size of Linux generic `struct statx`.
    private static final int STATX_SIZE = 256;

    /// The byte size of one Linux `struct utsname` field.
    private static final int UTSNAME_FIELD_SIZE = 65;

    /// The byte size of Linux `struct utsname`.
    private static final int UTSNAME_SIZE = 6 * UTSNAME_FIELD_SIZE;

    /// The byte offset of `sysname` inside Linux `struct utsname`.
    private static final int UTSNAME_SYSNAME_OFFSET = 0;

    /// The byte offset of `nodename` inside Linux `struct utsname`.
    private static final int UTSNAME_NODENAME_OFFSET = UTSNAME_FIELD_SIZE;

    /// The byte offset of `release` inside Linux `struct utsname`.
    private static final int UTSNAME_RELEASE_OFFSET = 2 * UTSNAME_FIELD_SIZE;

    /// The byte offset of `version` inside Linux `struct utsname`.
    private static final int UTSNAME_VERSION_OFFSET = 3 * UTSNAME_FIELD_SIZE;

    /// The byte offset of `machine` inside Linux `struct utsname`.
    private static final int UTSNAME_MACHINE_OFFSET = 4 * UTSNAME_FIELD_SIZE;

    /// The byte offset of `domainname` inside Linux `struct utsname`.
    private static final int UTSNAME_DOMAINNAME_OFFSET = 5 * UTSNAME_FIELD_SIZE;

    /// The byte offset of `tv_sec` inside Linux RISC-V 64-bit `struct timeval`.
    private static final int TIMEVAL_SECONDS_OFFSET = 0;

    /// The byte offset of `tv_usec` inside Linux RISC-V 64-bit `struct timeval`.
    private static final int TIMEVAL_MICROSECONDS_OFFSET = Long.BYTES;

    /// The byte offset of `tz_minuteswest` inside Linux `struct timezone`.
    private static final int TIMEZONE_MINUTESWEST_OFFSET = 0;

    /// The byte offset of `tz_dsttime` inside Linux `struct timezone`.
    private static final int TIMEZONE_DSTTIME_OFFSET = Integer.BYTES;

    /// The byte size of Linux RISC-V 64-bit `struct tms`.
    private static final int TMS_SIZE = 4 * Long.BYTES;

    /// The byte offset of `tms_utime` inside Linux RISC-V 64-bit `struct tms`.
    private static final int TMS_USER_TIME_OFFSET = 0;

    /// The byte offset of `tms_stime` inside Linux RISC-V 64-bit `struct tms`.
    private static final int TMS_SYSTEM_TIME_OFFSET = Long.BYTES;

    /// The byte offset of `tms_cutime` inside Linux RISC-V 64-bit `struct tms`.
    private static final int TMS_CHILD_USER_TIME_OFFSET = 2 * Long.BYTES;

    /// The byte offset of `tms_cstime` inside Linux RISC-V 64-bit `struct tms`.
    private static final int TMS_CHILD_SYSTEM_TIME_OFFSET = 3 * Long.BYTES;

    /// The byte size of Linux RISC-V 64-bit `struct rusage`.
    private static final int RUSAGE_SIZE = 144;

    /// The byte offset of `ru_utime` inside Linux RISC-V 64-bit `struct rusage`.
    private static final int RUSAGE_USER_TIME_OFFSET = 0;

    /// The byte offset of `ru_stime` inside Linux RISC-V 64-bit `struct rusage`.
    private static final int RUSAGE_SYSTEM_TIME_OFFSET = 2 * Long.BYTES;

    /// Linux `RUSAGE_CHILDREN`.
    private static final long RUSAGE_CHILDREN = -1;

    /// Linux `RUSAGE_SELF`.
    private static final long RUSAGE_SELF = 0;

    /// Linux `RUSAGE_THREAD`.
    private static final long RUSAGE_THREAD = 1;

    /// The byte offset of `rlim_cur` inside Linux RISC-V 64-bit `struct rlimit64`.
    private static final int RLIMIT_CURRENT_OFFSET = 0;

    /// The byte offset of `rlim_max` inside Linux RISC-V 64-bit `struct rlimit64`.
    private static final int RLIMIT_MAXIMUM_OFFSET = Long.BYTES;

    /// Linux `RLIM_INFINITY`.
    private static final long RLIM_INFINITY = -1L;

    /// The number of Linux resource limits tracked by this simulator.
    private static final int RESOURCE_LIMIT_COUNT = 16;

    /// Linux `RLIMIT_STACK`.
    private static final int RLIMIT_STACK = 3;

    /// Linux `RLIMIT_NOFILE`.
    private static final int RLIMIT_NOFILE = 7;

    /// The default stack soft limit exposed through `prlimit64`.
    private static final long DEFAULT_STACK_LIMIT = 8L * 1024L * 1024L;

    /// The default open-file soft and hard limit exposed through `prlimit64`.
    private static final long DEFAULT_OPEN_FILE_LIMIT = 1024;

    /// Default Linux resource-limit soft values.
    private static final long @Unmodifiable [] DEFAULT_RESOURCE_LIMIT_CURRENT = initialResourceLimitCurrent();

    /// Default Linux resource-limit hard values.
    private static final long @Unmodifiable [] DEFAULT_RESOURCE_LIMIT_MAXIMUM = initialResourceLimitMaximum();

    /// The guest page size used for page-based Linux memory syscalls.
    private static final long PAGE_SIZE = 4096;

    /// The bit mask for Linux `mmap` protection values accepted by this simulator.
    private static final long SUPPORTED_MMAP_PROTECTION_MASK = 0x7;

    /// Linux `PROT_NONE`.
    private static final long PROT_NONE = 0x0;

    /// Linux `MAP_SHARED`.
    private static final long MAP_SHARED = 0x01;

    /// Linux `MAP_PRIVATE`.
    private static final long MAP_PRIVATE = 0x02;

    /// Linux `MAP_TYPE`.
    private static final long MAP_TYPE = 0x0f;

    /// Linux `MAP_FIXED`.
    private static final long MAP_FIXED = 0x10;

    /// Linux `MAP_ANONYMOUS`.
    private static final long MAP_ANONYMOUS = 0x20;

    /// Linux `MAP_FIXED_NOREPLACE`.
    private static final long MAP_FIXED_NOREPLACE = 0x100000;

    /// Linux `MADV_NORMAL`.
    private static final long MADV_NORMAL = 0;

    /// Linux `MADV_RANDOM`.
    private static final long MADV_RANDOM = 1;

    /// Linux `MADV_SEQUENTIAL`.
    private static final long MADV_SEQUENTIAL = 2;

    /// Linux `MADV_WILLNEED`.
    private static final long MADV_WILLNEED = 3;

    /// Linux `MADV_DONTNEED`.
    private static final long MADV_DONTNEED = 4;

    /// Linux `MADV_FREE`.
    private static final long MADV_FREE = 8;

    /// Linux `MADV_DONTFORK`.
    private static final long MADV_DONTFORK = 10;

    /// Linux `MADV_DOFORK`.
    private static final long MADV_DOFORK = 11;

    /// Linux `MADV_MERGEABLE`.
    private static final long MADV_MERGEABLE = 12;

    /// Linux `MADV_UNMERGEABLE`.
    private static final long MADV_UNMERGEABLE = 13;

    /// Linux `MADV_HUGEPAGE`.
    private static final long MADV_HUGEPAGE = 14;

    /// Linux `MADV_NOHUGEPAGE`.
    private static final long MADV_NOHUGEPAGE = 15;

    /// Linux `MADV_DONTDUMP`.
    private static final long MADV_DONTDUMP = 16;

    /// Linux `MADV_DODUMP`.
    private static final long MADV_DODUMP = 17;

    /// Linux `MADV_WIPEONFORK`.
    private static final long MADV_WIPEONFORK = 18;

    /// Linux `MADV_KEEPONFORK`.
    private static final long MADV_KEEPONFORK = 19;

    /// Linux `MADV_COLD`.
    private static final long MADV_COLD = 20;

    /// Linux `MADV_PAGEOUT`.
    private static final long MADV_PAGEOUT = 21;

    /// Linux `MADV_POPULATE_READ`.
    private static final long MADV_POPULATE_READ = 22;

    /// Linux `MADV_POPULATE_WRITE`.
    private static final long MADV_POPULATE_WRITE = 23;

    /// Linux `MADV_DONTNEED_LOCKED`.
    private static final long MADV_DONTNEED_LOCKED = 24;

    /// Linux `MADV_COLLAPSE`.
    private static final long MADV_COLLAPSE = 25;

    /// Linux `FUTEX_WAIT`.
    private static final long FUTEX_WAIT = 0;

    /// Linux `FUTEX_WAKE`.
    private static final long FUTEX_WAKE = 1;

    /// Linux `FUTEX_WAIT_BITSET`.
    private static final long FUTEX_WAIT_BITSET = 9;

    /// Linux `FUTEX_WAKE_BITSET`.
    private static final long FUTEX_WAKE_BITSET = 10;

    /// Linux futex command mask.
    private static final long FUTEX_COMMAND_MASK = 0x7f;

    /// Linux `FUTEX_PRIVATE_FLAG`.
    private static final long FUTEX_PRIVATE_FLAG = 128;

    /// Linux `FUTEX_CLOCK_REALTIME`.
    private static final long FUTEX_CLOCK_REALTIME = 256;

    /// Linux futex flags accepted by this simulator.
    private static final long SUPPORTED_FUTEX_FLAGS = FUTEX_PRIVATE_FLAG | FUTEX_CLOCK_REALTIME;

    /// Linux `FUTEX_BITSET_MATCH_ANY`.
    private static final long FUTEX_BITSET_MATCH_ANY = 0xffff_ffffL;

    /// Linux `CLONE_VM`.
    private static final long CLONE_VM = 0x00000100L;

    /// Linux `CLONE_FS`.
    private static final long CLONE_FS = 0x00000200L;

    /// Linux `CLONE_FILES`.
    private static final long CLONE_FILES = 0x00000400L;

    /// Linux `CLONE_SIGHAND`.
    private static final long CLONE_SIGHAND = 0x00000800L;

    /// Linux `CLONE_THREAD`.
    private static final long CLONE_THREAD = 0x00010000L;

    /// Linux `CLONE_SYSVSEM`.
    private static final long CLONE_SYSVSEM = 0x00040000L;

    /// Linux `CLONE_SETTLS`.
    private static final long CLONE_SETTLS = 0x00080000L;

    /// Linux `CLONE_PARENT_SETTID`.
    private static final long CLONE_PARENT_SETTID = 0x00100000L;

    /// Linux `CLONE_CHILD_CLEARTID`.
    private static final long CLONE_CHILD_CLEARTID = 0x00200000L;

    /// Linux `CLONE_DETACHED`.
    private static final long CLONE_DETACHED = 0x00400000L;

    /// Linux `CLONE_CHILD_SETTID`.
    private static final long CLONE_CHILD_SETTID = 0x01000000L;

    /// Linux clone flags required for the supported thread-style parent return path.
    private static final long REQUIRED_THREAD_CLONE_FLAGS =
            CLONE_VM | CLONE_SIGHAND | CLONE_THREAD;

    /// Linux clone flags accepted by the supported thread-style parent return path.
    private static final long SUPPORTED_THREAD_CLONE_FLAGS =
            REQUIRED_THREAD_CLONE_FLAGS
                    | CLONE_FS
                    | CLONE_FILES
                    | CLONE_SYSVSEM
                    | CLONE_SETTLS
                    | CLONE_PARENT_SETTID
                    | CLONE_CHILD_CLEARTID
                    | CLONE_DETACHED
                    | CLONE_CHILD_SETTID;

    /// The byte size of Linux generic 64-bit kernel `sigset_t`.
    private static final long KERNEL_SIGSET_SIZE = 8;

    /// The byte size of Linux generic 64-bit kernel `struct sigaction`.
    private static final long KERNEL_SIGACTION_SIZE = 32;

    /// The byte offset of `ss_sp` inside Linux RISC-V 64-bit `stack_t`.
    private static final long SIGNAL_STACK_POINTER_OFFSET = 0;

    /// The byte offset of `ss_flags` inside Linux RISC-V 64-bit `stack_t`.
    private static final long SIGNAL_STACK_FLAGS_OFFSET = Long.BYTES;

    /// The padding byte offset after `ss_flags` inside Linux RISC-V 64-bit `stack_t`.
    private static final long SIGNAL_STACK_FLAGS_PADDING_OFFSET = SIGNAL_STACK_FLAGS_OFFSET + Integer.BYTES;

    /// The byte offset of `ss_size` inside Linux RISC-V 64-bit `stack_t`.
    private static final long SIGNAL_STACK_SIZE_OFFSET = 2L * Long.BYTES;

    /// Linux `MINSIGSTKSZ`.
    private static final long MINIMUM_SIGNAL_STACK_SIZE = 2048;

    /// Linux `SS_ONSTACK`.
    private static final long SS_ONSTACK = 1;

    /// Linux `SS_DISABLE`.
    private static final long SS_DISABLE = 2;

    /// Linux `SS_AUTODISARM`.
    private static final long SS_AUTODISARM = 1L << 31;

    /// Linux flags accepted when registering an alternate signal stack.
    private static final long SUPPORTED_SIGNAL_STACK_FLAGS = SS_DISABLE | SS_AUTODISARM;

    /// The byte size of the minimal CPU affinity mask exposed to the guest.
    private static final long MINIMUM_CPU_AFFINITY_MASK_SIZE = Long.BYTES;

    /// The byte size of Linux `struct riscv_hwprobe`.
    private static final long RISCV_HWPROBE_PAIR_SIZE = 16;

    /// The byte offset of `key` inside `struct riscv_hwprobe`.
    private static final long RISCV_HWPROBE_KEY_OFFSET = 0;

    /// The byte offset of `value` inside `struct riscv_hwprobe`.
    private static final long RISCV_HWPROBE_VALUE_OFFSET = Long.BYTES;

    /// Linux `RISCV_HWPROBE_WHICH_CPUS`.
    private static final long RISCV_HWPROBE_WHICH_CPUS = 1;

    /// Linux `RISCV_HWPROBE_KEY_MVENDORID`.
    private static final long RISCV_HWPROBE_KEY_MVENDORID = 0;

    /// Linux `RISCV_HWPROBE_KEY_MARCHID`.
    private static final long RISCV_HWPROBE_KEY_MARCHID = 1;

    /// Linux `RISCV_HWPROBE_KEY_MIMPID`.
    private static final long RISCV_HWPROBE_KEY_MIMPID = 2;

    /// Linux `RISCV_HWPROBE_KEY_BASE_BEHAVIOR`.
    private static final long RISCV_HWPROBE_KEY_BASE_BEHAVIOR = 3;

    /// Linux `RISCV_HWPROBE_BASE_BEHAVIOR_IMA`.
    private static final long RISCV_HWPROBE_BASE_BEHAVIOR_IMA = 1;

    /// Linux `RISCV_HWPROBE_KEY_IMA_EXT_0`.
    private static final long RISCV_HWPROBE_KEY_IMA_EXT_0 = 4;

    /// Linux `RISCV_HWPROBE_IMA_FD`.
    private static final long RISCV_HWPROBE_IMA_FD = 1;

    /// Linux `RISCV_HWPROBE_IMA_C`.
    private static final long RISCV_HWPROBE_IMA_C = 1 << 1;

    /// Linux `RISCV_HWPROBE_EXT_ZICNTR`.
    private static final long RISCV_HWPROBE_EXT_ZICNTR = 1L << 50;

    /// Linux `RISCV_HWPROBE_KEY_CPUPERF_0`.
    private static final long RISCV_HWPROBE_KEY_CPUPERF_0 = 5;

    /// Linux `RISCV_HWPROBE_MISALIGNED_UNSUPPORTED`.
    private static final long RISCV_HWPROBE_MISALIGNED_UNSUPPORTED = 4;

    /// Linux `RISCV_HWPROBE_KEY_ZICBOZ_BLOCK_SIZE`.
    private static final long RISCV_HWPROBE_KEY_ZICBOZ_BLOCK_SIZE = 6;

    /// Linux `RISCV_HWPROBE_KEY_HIGHEST_VIRT_ADDRESS`.
    private static final long RISCV_HWPROBE_KEY_HIGHEST_VIRT_ADDRESS = 7;

    /// Linux `RISCV_HWPROBE_KEY_TIME_CSR_FREQ`.
    private static final long RISCV_HWPROBE_KEY_TIME_CSR_FREQ = 8;

    /// Linux `RISCV_HWPROBE_KEY_MISALIGNED_SCALAR_PERF`.
    private static final long RISCV_HWPROBE_KEY_MISALIGNED_SCALAR_PERF = 9;

    /// Linux `RISCV_HWPROBE_KEY_MISALIGNED_VECTOR_PERF`.
    private static final long RISCV_HWPROBE_KEY_MISALIGNED_VECTOR_PERF = 10;

    /// Linux `RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0`.
    private static final long RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0 = 11;

    /// Linux `RISCV_HWPROBE_KEY_ZICBOM_BLOCK_SIZE`.
    private static final long RISCV_HWPROBE_KEY_ZICBOM_BLOCK_SIZE = 12;

    /// Linux `RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0`.
    private static final long RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0 = 13;

    /// Linux `RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0`.
    private static final long RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0 = 14;

    /// Linux `RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE`.
    private static final long RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE = 15;

    /// The extension bits reported through `RISCV_HWPROBE_KEY_IMA_EXT_0`.
    private static final long RISCV_HWPROBE_IMA_EXTENSIONS =
            RISCV_HWPROBE_IMA_FD | RISCV_HWPROBE_IMA_C | RISCV_HWPROBE_EXT_ZICNTR;

    /// Linux `CLOCK_REALTIME`.
    private static final long CLOCK_REALTIME = 0;

    /// Linux `CLOCK_MONOTONIC`.
    private static final long CLOCK_MONOTONIC = 1;

    /// Linux `CLOCK_PROCESS_CPUTIME_ID`.
    private static final long CLOCK_PROCESS_CPUTIME_ID = 2;

    /// Linux `CLOCK_THREAD_CPUTIME_ID`.
    private static final long CLOCK_THREAD_CPUTIME_ID = 3;

    /// Linux `CLOCK_MONOTONIC_RAW`.
    private static final long CLOCK_MONOTONIC_RAW = 4;

    /// Linux `CLOCK_REALTIME_COARSE`.
    private static final long CLOCK_REALTIME_COARSE = 5;

    /// Linux `CLOCK_MONOTONIC_COARSE`.
    private static final long CLOCK_MONOTONIC_COARSE = 6;

    /// Linux `CLOCK_BOOTTIME`.
    private static final long CLOCK_BOOTTIME = 7;

    /// Linux `MEMBARRIER_CMD_QUERY`.
    private static final long MEMBARRIER_CMD_QUERY = 0;

    /// Linux `TIMER_ABSTIME`.
    private static final long TIMER_ABSTIME = 1;

    /// Linux flags accepted by `clock_nanosleep`.
    private static final long SUPPORTED_CLOCK_NANOSLEEP_FLAGS = TIMER_ABSTIME;

    /// The `tv_sec` byte offset inside Linux RISC-V 64-bit `struct timespec`.
    private static final int TIMESPEC_SECONDS_OFFSET = 0;

    /// The `tv_nsec` byte offset inside Linux RISC-V 64-bit `struct timespec`.
    private static final int TIMESPEC_NANOSECONDS_OFFSET = Long.BYTES;

    /// The number of nanoseconds in one second.
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    /// The Linux user-space clock ticks per second value used by `times`.
    private static final long CLOCK_TICKS_PER_SECOND = 100L;

    /// The number of nanoseconds in one millisecond.
    private static final long NANOSECONDS_PER_MILLISECOND = 1_000_000L;

    /// Linux `PR_SET_PDEATHSIG`.
    private static final long PR_SET_PDEATHSIG = 1;

    /// Linux `PR_GET_PDEATHSIG`.
    private static final long PR_GET_PDEATHSIG = 2;

    /// Linux `PR_GET_DUMPABLE`.
    private static final long PR_GET_DUMPABLE = 3;

    /// Linux `PR_SET_DUMPABLE`.
    private static final long PR_SET_DUMPABLE = 4;

    /// Linux `PR_SET_NAME`.
    private static final long PR_SET_NAME = 15;

    /// Linux `PR_GET_NAME`.
    private static final long PR_GET_NAME = 16;

    /// Linux `PR_SET_TIMERSLACK`.
    private static final long PR_SET_TIMERSLACK = 29;

    /// Linux `PR_GET_TIMERSLACK`.
    private static final long PR_GET_TIMERSLACK = 30;

    /// Linux `PR_SET_CHILD_SUBREAPER`.
    private static final long PR_SET_CHILD_SUBREAPER = 36;

    /// Linux `PR_GET_CHILD_SUBREAPER`.
    private static final long PR_GET_CHILD_SUBREAPER = 37;

    /// Linux `PR_SET_NO_NEW_PRIVS`.
    private static final long PR_SET_NO_NEW_PRIVS = 38;

    /// Linux `PR_GET_NO_NEW_PRIVS`.
    private static final long PR_GET_NO_NEW_PRIVS = 39;

    /// Linux `PR_GET_TID_ADDRESS`.
    private static final long PR_GET_TID_ADDRESS = 40;

    /// Linux `PR_SET_THP_DISABLE`.
    private static final long PR_SET_THP_DISABLE = 41;

    /// Linux `PR_GET_THP_DISABLE`.
    private static final long PR_GET_THP_DISABLE = 42;

    /// Linux `PR_SET_VMA`.
    private static final long PR_SET_VMA = 0x53564d41L;

    /// Linux `PR_SET_VMA_ANON_NAME`.
    private static final long PR_SET_VMA_ANON_NAME = 0;

    /// The Linux task command string size used by `PR_SET_NAME` and `PR_GET_NAME`.
    private static final int TASK_COMMAND_LENGTH = 16;

    /// The default timer slack value exposed by `PR_GET_TIMERSLACK`.
    private static final long DEFAULT_TIMER_SLACK_NANOSECONDS = 50_000L;

    /// The lowest regular Linux signal number accepted by signal syscalls.
    private static final long MIN_SIGNAL_NUMBER = 1;

    /// The highest regular Linux signal number accepted by signal syscalls.
    private static final long MAX_SIGNAL_NUMBER = 64;

    /// Linux `SIG_BLOCK` signal-mask operation.
    private static final long SIG_BLOCK = 0;

    /// Linux `SIG_UNBLOCK` signal-mask operation.
    private static final long SIG_UNBLOCK = 1;

    /// Linux `SIG_SETMASK` signal-mask operation.
    private static final long SIG_SETMASK = 2;

    /// The `st_ino` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_INODE_OFFSET = 8;

    /// The `st_mode` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_MODE_OFFSET = 16;

    /// The `st_nlink` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_LINK_COUNT_OFFSET = 20;

    /// The `st_size` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_SIZE_OFFSET = 48;

    /// The `st_blksize` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_BLOCK_SIZE_OFFSET = 56;

    /// The `st_blocks` byte offset inside Linux generic 64-bit `struct stat`.
    private static final int STAT_BLOCK_COUNT_OFFSET = 64;

    /// The byte offset of `f_type` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_TYPE_OFFSET = 0;

    /// The byte offset of `f_bsize` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_BLOCK_SIZE_OFFSET = 8;

    /// The byte offset of `f_blocks` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_BLOCKS_OFFSET = 16;

    /// The byte offset of `f_bfree` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_BLOCKS_FREE_OFFSET = 24;

    /// The byte offset of `f_bavail` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_BLOCKS_AVAILABLE_OFFSET = 32;

    /// The byte offset of `f_files` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_FILES_OFFSET = 40;

    /// The byte offset of `f_ffree` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_FILES_FREE_OFFSET = 48;

    /// The byte offset of `f_namelen` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_NAME_LENGTH_OFFSET = 64;

    /// The byte offset of `f_frsize` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_FRAGMENT_SIZE_OFFSET = 72;

    /// The byte offset of `f_flags` inside Linux generic 64-bit `struct statfs`.
    private static final int STATFS_FLAGS_OFFSET = 80;

    /// The byte offset of `stx_mask` inside Linux generic `struct statx`.
    private static final int STATX_MASK_OFFSET = 0;

    /// The byte offset of `stx_blksize` inside Linux generic `struct statx`.
    private static final int STATX_BLOCK_SIZE_OFFSET = 4;

    /// The byte offset of `stx_attributes` inside Linux generic `struct statx`.
    private static final int STATX_ATTRIBUTES_OFFSET = 8;

    /// The byte offset of `stx_nlink` inside Linux generic `struct statx`.
    private static final int STATX_LINK_COUNT_OFFSET = 16;

    /// The byte offset of `stx_uid` inside Linux generic `struct statx`.
    private static final int STATX_UID_OFFSET = 20;

    /// The byte offset of `stx_gid` inside Linux generic `struct statx`.
    private static final int STATX_GID_OFFSET = 24;

    /// The byte offset of `stx_mode` inside Linux generic `struct statx`.
    private static final int STATX_MODE_OFFSET = 28;

    /// The byte offset of `stx_ino` inside Linux generic `struct statx`.
    private static final int STATX_INODE_OFFSET = 32;

    /// The byte offset of `stx_size` inside Linux generic `struct statx`.
    private static final int STATX_FILE_SIZE_OFFSET = 40;

    /// The byte offset of `stx_blocks` inside Linux generic `struct statx`.
    private static final int STATX_BLOCK_COUNT_OFFSET = 48;

    /// The byte offset of `stx_attributes_mask` inside Linux generic `struct statx`.
    private static final int STATX_ATTRIBUTES_MASK_OFFSET = 56;

    /// The byte offset of `stx_dev_major` inside Linux generic `struct statx`.
    private static final int STATX_DEVICE_MAJOR_OFFSET = 136;

    /// The byte offset of `stx_dev_minor` inside Linux generic `struct statx`.
    private static final int STATX_DEVICE_MINOR_OFFSET = 140;

    /// The byte offset of `stx_mnt_id` inside Linux generic `struct statx`.
    private static final int STATX_MOUNT_ID_OFFSET = 144;

    /// The Linux `STATX_BASIC_STATS` bit mask returned by this simulator.
    private static final int STATX_BASIC_STATS_MASK = 0x0000_07ff;

    /// The deterministic mount id returned by `statx`.
    private static final long STATX_SYNTHETIC_MOUNT_ID = 1;

    /// The attribute mask returned by `statx`.
    private static final long STATX_ATTRIBUTES_MASK = 0;

    /// The Linux `S_IFCHR` file type bit used for character devices.
    private static final int STAT_MODE_CHARACTER_DEVICE = 0020000;

    /// The Linux `S_IFIFO` file type bit used for pipe endpoints.
    private static final int STAT_MODE_FIFO = 0010000;

    /// The Linux `S_IFDIR` file type bit used for directories.
    private static final int STAT_MODE_DIRECTORY = 0040000;

    /// The Linux `S_IFLNK` file type bit used for symbolic links.
    private static final int STAT_MODE_SYMBOLIC_LINK = 0120000;

    /// The Linux `S_IFREG` file type bit used for regular files.
    private static final int STAT_MODE_REGULAR_FILE = 0100000;

    /// The file permission bits exposed for standard streams.
    private static final int STAT_MODE_READ_WRITE_ALL = 0666;

    /// The file permission bits exposed for symbolic links.
    private static final int STAT_MODE_ALL = 0777;

    /// The file permission bits exposed for read-only host files.
    private static final int STAT_MODE_READ_ALL = 0444;

    /// The file permission bits exposed for readable host directories.
    private static final int STAT_MODE_READ_EXECUTE_ALL = 0555;

    /// The `st_mode` value exposed for the simulator standard streams.
    private static final int STANDARD_STREAM_STAT_MODE = STAT_MODE_CHARACTER_DEVICE | STAT_MODE_READ_WRITE_ALL;

    /// The block size exposed for standard streams.
    private static final int STANDARD_STREAM_BLOCK_SIZE = 4096;

    /// The synthetic filesystem magic returned by `statfs`.
    private static final long STATFS_MAGIC = 0x0102_1994L;

    /// The synthetic filesystem block size returned by `statfs`.
    private static final long STATFS_BLOCK_SIZE = 4096;

    /// The synthetic filesystem block count returned by `statfs`.
    private static final long STATFS_BLOCK_COUNT = 1_048_576;

    /// The synthetic filesystem file count returned by `statfs`.
    private static final long STATFS_FILE_COUNT = 1_048_576;

    /// The maximum guest filename length returned by `statfs`.
    private static final long STATFS_NAME_MAX = 255;

    /// The fixed guest process id returned by process identity syscalls.
    private static final long GUEST_PROCESS_ID = 1;

    /// The fixed guest parent process id returned by `getppid`.
    private static final long GUEST_PARENT_PROCESS_ID = 0;

    /// The fixed guest process group id used by process-group syscalls.
    private static final long GUEST_PROCESS_GROUP_ID = GUEST_PROCESS_ID;

    /// The deterministic guest user id exposed by identity syscalls.
    private static final long GUEST_USER_ID = 1000;

    /// The deterministic guest group id exposed by identity syscalls.
    private static final long GUEST_GROUP_ID = 1000;

    /// The non-cryptographic seed used for deterministic `getrandom` bytes.
    private static final long RANDOM_SEED = 0x4752_4953_4356_0001L;

    /// Linux `GRND_NONBLOCK`; accepted because simulator randomness never blocks.
    private static final long GRND_NONBLOCK = 0x0001;

    /// Linux `GRND_RANDOM`; accepted and mapped to the same deterministic source.
    private static final long GRND_RANDOM = 0x0002;

    /// The supported Linux `getrandom` flags mask.
    private static final long GETRANDOM_SUPPORTED_FLAGS = GRND_NONBLOCK | GRND_RANDOM;

    /// The guest memory accessed by syscall buffers.
    private final Memory memory;

    /// The host input stream used for guest stdin reads.
    private final InputStream in;

    /// The host output stream used for guest stdout writes.
    private final OutputStream out;

    /// The host output stream used for guest stderr writes.
    private final OutputStream err;

    /// The Truffle environment used to resolve the configured host root lazily.
    private final @Nullable TruffleLanguage.Env env;

    /// Runs guest thread states created by Linux `clone`.
    private final @Nullable GuestThreadRunner guestThreadRunner;

    /// The configured host root path exposed through sandboxed `openat`.
    private final @Nullable String hostRootPath;

    /// The resolved host root directory exposed through sandboxed `openat`.
    private @Nullable TruffleFile hostRoot;

    /// The guest-visible current working directory in absolute Linux path syntax.
    private String guestWorkingDirectory = "/";

    /// The lowest program break accepted by the `brk` syscall.
    private final long initialProgramBreak;

    /// The current guest program break returned by `brk`.
    private long programBreak;

    /// The active anonymous mappings created by `mmap`.
    private final ArrayList<MemoryMapping> memoryMappings = new ArrayList<>();

    /// The guest file descriptor table for host files opened by `openat`.
    private final ArrayList<@Nullable OpenFile> openFiles = new ArrayList<>();

    /// The next synthetic guest thread id returned by accepted `clone` calls.
    private long nextGuestThreadId = GUEST_PROCESS_ID + 1;

    /// Guards guest thread, futex, and process-exit state.
    private final Object threadLock = new Object();

    /// Host threads currently executing cloned guest threads.
    private final ArrayList<Thread> guestThreads = new ArrayList<>();

    /// Futex waiters currently blocked in guest syscalls.
    private final ArrayList<FutexWaiter> futexWaiters = new ArrayList<>();

    /// The number of live guest threads, including the initial thread.
    private int liveThreadCount = 1;

    /// Whether a guest `exit_group` has requested process termination.
    private volatile boolean processExitRequested;

    /// The exit code requested by `exit_group` or by the last exiting thread.
    private volatile long processExitCode;

    /// A host exception thrown while executing a guest thread, or null when none has failed.
    private volatile @Nullable Throwable threadFailure;

    /// The robust futex list head address supplied by `set_robust_list`.
    private long robustListHeadAddress;

    /// The robust futex list structure length supplied by `set_robust_list`.
    private long robustListLength;

    /// The guest alternate signal stack pointer registered by `sigaltstack`.
    private long alternateSignalStackPointer;

    /// The guest alternate signal stack size registered by `sigaltstack`.
    private long alternateSignalStackSize;

    /// The guest alternate signal stack flags reported by `sigaltstack`.
    private long alternateSignalStackFlags = SS_DISABLE;

    /// The signal reported by `PR_GET_PDEATHSIG`, or zero when unset.
    private int parentDeathSignal;

    /// The dumpable state reported by `PR_GET_DUMPABLE`.
    private int dumpable = 1;

    /// The process name reported by `PR_GET_NAME`.
    private final byte[] processName = initialProcessName();

    /// The timer slack value reported by `PR_GET_TIMERSLACK`.
    private long timerSlackNanoseconds = DEFAULT_TIMER_SLACK_NANOSECONDS;

    /// Whether this single-process guest is marked as a child subreaper.
    private boolean childSubreaper;

    /// Whether `PR_SET_NO_NEW_PRIVS` has been applied.
    private boolean noNewPrivileges;

    /// The transparent huge page disable state reported by `PR_GET_THP_DISABLE`.
    private int transparentHugePagesDisabled;

    /// The current soft resource limits returned by `prlimit64`.
    private final long[] resourceLimitCurrent = DEFAULT_RESOURCE_LIMIT_CURRENT.clone();

    /// The current hard resource limits returned by `prlimit64`.
    private final long[] resourceLimitMaximum = DEFAULT_RESOURCE_LIMIT_MAXIMUM.clone();

    /// The next deterministic random state used by `getrandom`.
    private long randomState = RANDOM_SEED;

    /// The clock exposed to guest time syscalls.
    private final Clock clock;

    /// The guest clock instant captured when the syscall handler was created.
    private final Instant clockStartInstant;

    /// Creates the default process name used by `PR_GET_NAME`.
    private static byte[] initialProcessName() {
        byte[] name = new byte[TASK_COMMAND_LENGTH];
        byte[] defaultName = RiscVLanguage.ID.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(defaultName, 0, name, 0, Math.min(defaultName.length, TASK_COMMAND_LENGTH - 1));
        return name;
    }

    /// Creates the default soft Linux resource-limit table.
    private static long @Unmodifiable [] initialResourceLimitCurrent() {
        long[] limits = new long[RESOURCE_LIMIT_COUNT];
        for (int index = 0; index < limits.length; index++) {
            limits[index] = RLIM_INFINITY;
        }
        limits[RLIMIT_STACK] = DEFAULT_STACK_LIMIT;
        limits[RLIMIT_NOFILE] = DEFAULT_OPEN_FILE_LIMIT;
        return limits;
    }

    /// Creates the default hard Linux resource-limit table.
    private static long @Unmodifiable [] initialResourceLimitMaximum() {
        long[] limits = new long[RESOURCE_LIMIT_COUNT];
        for (int index = 0; index < limits.length; index++) {
            limits[index] = RLIM_INFINITY;
        }
        limits[RLIMIT_NOFILE] = DEFAULT_OPEN_FILE_LIMIT;
        return limits;
    }

    /// Creates a syscall handler backed by the supplied host streams and heap boundary.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak) {
        this(memory, in, out, err, initialProgramBreak, null, null, null, Clock.systemUTC(), null);
    }

    /// Creates a syscall handler backed by the supplied host streams, heap boundary, and resolved host file root.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable TruffleFile hostRoot) {
        this(memory, in, out, err, initialProgramBreak, hostRoot, Clock.systemUTC());
    }

    /// Creates a syscall handler backed by the supplied streams, resolved host file root, and guest clock.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable TruffleFile hostRoot,
            Clock clock) {
        this(memory, in, out, err, initialProgramBreak, hostRoot, null, null, clock, null);
    }

    /// Creates a syscall handler backed by the supplied host streams, heap boundary, and lazy host file root.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath) {
        this(memory, in, out, err, initialProgramBreak, env, hostRootPath, Clock.systemUTC());
    }

    /// Creates a syscall handler backed by the supplied streams, lazy host file root, and guest clock.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            Clock clock) {
        this(memory, in, out, err, initialProgramBreak, env, hostRootPath, clock, null);
    }

    /// Creates a syscall handler backed by streams, lazy host root, guest clock, and guest thread runner.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            TruffleLanguage.Env env,
            String hostRootPath,
            Clock clock,
            GuestThreadRunner guestThreadRunner) {
        this(memory, in, out, err, initialProgramBreak, null, env, hostRootPath, clock, guestThreadRunner);
    }

    /// Creates a syscall handler with either an eager or lazy TruffleFile host root.
    private GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak,
            @Nullable TruffleFile hostRoot,
            @Nullable TruffleLanguage.Env env,
            @Nullable String hostRootPath,
            Clock clock,
            @Nullable GuestThreadRunner guestThreadRunner) {
        if (initialProgramBreak < memory.baseAddress() || initialProgramBreak > memory.endAddress()) {
            throw new RiscVException("Initial program break is outside guest memory: address=0x"
                    + Long.toUnsignedString(initialProgramBreak, 16));
        }

        this.memory = memory;
        this.in = in;
        this.out = out;
        this.err = err;
        this.env = env;
        this.guestThreadRunner = guestThreadRunner;
        this.hostRootPath = hostRootPath;
        this.hostRoot = hostRoot == null ? null : hostRoot.getAbsoluteFile().normalize();
        this.initialProgramBreak = initialProgramBreak;
        this.programBreak = initialProgramBreak;
        this.clock = clock;
        this.clockStartInstant = clock.instant();
    }

    /// Executes the syscall described by the guest argument registers at the supplied program counter.
    public void handle(MachineState state, long pc) {
        long callNumber = state.register(17);
        if (callNumber == SYS_GETCWD) {
            state.setRegister(10, getcwd(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_EVENTFD2) {
            state.setRegister(10, eventfd2(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_EPOLL_CREATE1) {
            state.setRegister(10, epollCreate1(state.register(10)));
            return;
        }
        if (callNumber == SYS_EPOLL_CTL) {
            state.setRegister(10, epollCtl(
                    (int) state.register(10),
                    (int) state.register(11),
                    (int) state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_EPOLL_PWAIT) {
            state.setRegister(10, epollPwait(
                    (int) state.register(10),
                    state.register(11),
                    (int) state.register(12),
                    (int) state.register(13),
                    state.register(14),
                    state.register(15)));
            return;
        }
        if (callNumber == SYS_DUP) {
            state.setRegister(10, dup((int) state.register(10)));
            return;
        }
        if (callNumber == SYS_DUP3) {
            state.setRegister(10, dup3((int) state.register(10), (int) state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_FCNTL) {
            state.setRegister(10, fcntl((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_IOCTL) {
            state.setRegister(10, ioctl((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_MKDIRAT) {
            state.setRegister(10, mkdirat(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_UNLINKAT) {
            state.setRegister(10, unlinkat(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_RENAMEAT) {
            state.setRegister(10, renameat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_STATFS) {
            state.setRegister(10, statfs(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_FSTATFS) {
            state.setRegister(10, fstatfs((int) state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_TRUNCATE) {
            state.setRegister(10, truncate(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_FTRUNCATE) {
            state.setRegister(10, ftruncate((int) state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_FACCESSAT) {
            state.setRegister(10, faccessat(state.register(10), state.register(11), state.register(12), 0));
            return;
        }
        if (callNumber == SYS_CHDIR) {
            state.setRegister(10, chdir(state.register(10)));
            return;
        }
        if (callNumber == SYS_FCHDIR) {
            state.setRegister(10, fchdir((int) state.register(10)));
            return;
        }
        if (callNumber == SYS_OPENAT) {
            state.setRegister(10, openat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_CLOSE) {
            state.setRegister(10, close((int) state.register(10)));
            return;
        }
        if (callNumber == SYS_PIPE2) {
            state.setRegister(10, pipe2(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_GETDENTS64) {
            state.setRegister(10, getdents64((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_LSEEK) {
            state.setRegister(10, lseek((int) state.register(10), state.register(11), (int) state.register(12)));
            return;
        }
        if (callNumber == SYS_READ) {
            state.setRegister(10, read((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_WRITE) {
            state.setRegister(10, write((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_READV) {
            state.setRegister(10, readv((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_WRITEV) {
            state.setRegister(10, writev((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_PREAD64) {
            state.setRegister(10, pread64(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_PWRITE64) {
            state.setRegister(10, pwrite64(
                    (int) state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_READLINKAT) {
            state.setRegister(10, readlinkat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_NEWFSTATAT) {
            state.setRegister(10, newfstatat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_FSTAT) {
            state.setRegister(10, fstat((int) state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_SYNC) {
            state.setRegister(10, sync());
            return;
        }
        if (callNumber == SYS_FSYNC) {
            state.setRegister(10, fsync((int) state.register(10)));
            return;
        }
        if (callNumber == SYS_FDATASYNC) {
            state.setRegister(10, fdatasync((int) state.register(10)));
            return;
        }
        if (callNumber == SYS_EXIT_GROUP) {
            requestProcessExit(state.register(10));
            throw new ProgramExitException(state.register(10));
        }
        if (callNumber == SYS_EXIT) {
            exitThread(state, state.register(10));
            return;
        }
        if (callNumber == SYS_SET_TID_ADDRESS) {
            state.setRegister(10, setTidAddress(state, state.register(10)));
            return;
        }
        if (callNumber == SYS_FUTEX) {
            state.setRegister(10, futex(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            return;
        }
        if (callNumber == SYS_SET_ROBUST_LIST) {
            state.setRegister(10, setRobustList(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_GET_ROBUST_LIST) {
            state.setRegister(10, getRobustList(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_NANOSLEEP) {
            state.setRegister(10, nanosleep(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_CLOCK_GETTIME) {
            state.setRegister(10, clockGettime(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_CLOCK_GETRES) {
            state.setRegister(10, clockGetres(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_CLOCK_NANOSLEEP) {
            state.setRegister(10, clockNanosleep(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_SCHED_GETAFFINITY) {
            state.setRegister(10, schedGetaffinity(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_SCHED_YIELD) {
            state.setRegister(10, schedYield());
            return;
        }
        if (callNumber == SYS_KILL) {
            state.setRegister(10, kill(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_TKILL) {
            state.setRegister(10, tkill(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_TGKILL) {
            state.setRegister(10, tgkill(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_SIGALTSTACK) {
            state.setRegister(10, sigaltstack(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_RT_SIGACTION) {
            state.setRegister(10, rtSigaction(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_RT_SIGPROCMASK) {
            state.setRegister(10, rtSigprocmask(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_TIMES) {
            state.setRegister(10, times(state.register(10)));
            return;
        }
        if (callNumber == SYS_GETPGID) {
            state.setRegister(10, getpgid(state.register(10)));
            return;
        }
        if (callNumber == SYS_SETSID) {
            state.setRegister(10, setsid());
            return;
        }
        if (callNumber == SYS_UNAME) {
            state.setRegister(10, uname(state.register(10)));
            return;
        }
        if (callNumber == SYS_GETRUSAGE) {
            state.setRegister(10, getrusage(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_PRCTL) {
            state.setRegister(10, prctl(
                    state,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            return;
        }
        if (callNumber == SYS_GETCPU) {
            state.setRegister(10, getcpu(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_GETTIMEOFDAY) {
            state.setRegister(10, gettimeofday(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_GETPID) {
            state.setRegister(10, GUEST_PROCESS_ID);
            return;
        }
        if (callNumber == SYS_GETTID) {
            state.setRegister(10, state.threadId());
            return;
        }
        if (callNumber == SYS_GETPPID) {
            state.setRegister(10, GUEST_PARENT_PROCESS_ID);
            return;
        }
        if (callNumber == SYS_GETUID || callNumber == SYS_GETEUID) {
            state.setRegister(10, GUEST_USER_ID);
            return;
        }
        if (callNumber == SYS_GETGID || callNumber == SYS_GETEGID) {
            state.setRegister(10, GUEST_GROUP_ID);
            return;
        }
        if (callNumber == SYS_CLONE) {
            state.setRegister(10, clone(
                    state,
                    pc,
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            return;
        }
        if (callNumber == SYS_BRK) {
            state.setRegister(10, brk(state.register(10)));
            return;
        }
        if (callNumber == SYS_MUNMAP) {
            state.setRegister(10, munmap(state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_MMAP) {
            state.setRegister(10, mmap(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14),
                    state.register(15)));
            return;
        }
        if (callNumber == SYS_MPROTECT) {
            state.setRegister(10, mprotect(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_MADVISE) {
            state.setRegister(10, madvise(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_RISCV_HWPROBE) {
            state.setRegister(10, riscvHwprobe(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            return;
        }
        if (callNumber == SYS_PRLIMIT64) {
            state.setRegister(10, prlimit64(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        if (callNumber == SYS_SYNCFS) {
            state.setRegister(10, syncfs((int) state.register(10)));
            return;
        }
        if (callNumber == SYS_RENAMEAT2) {
            state.setRegister(10, renameat2(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            return;
        }
        if (callNumber == SYS_GETRANDOM) {
            state.setRegister(10, getrandom(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_MEMBARRIER) {
            state.setRegister(10, membarrier(state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_STATX) {
            state.setRegister(10, statx(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            return;
        }
        if (callNumber == SYS_RSEQ) {
            state.setRegister(10, rseq());
            return;
        }
        if (callNumber == SYS_FACCESSAT2) {
            state.setRegister(10, faccessat(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13)));
            return;
        }
        throw new RiscVException(unsupportedEcallMessage(state, pc, callNumber));
    }

    /// Returns true when a syscall may need a full architectural register snapshot.
    static boolean needsFullRegisterSnapshot(long callNumber) {
        return callNumber == SYS_CLONE;
    }

    /// Closes all host files opened by guest file descriptors.
    @Override
    public void close() {
        for (int index = 0; index < openFiles.size(); index++) {
            @Nullable OpenFile openFile = openFiles.get(index);
            if (openFile == null) {
                continue;
            }

            try {
                openFiles.set(index, null);
                releaseOpenFile(openFile);
            } catch (IOException exception) {
                throw new RiscVException("Failed to close guest file descriptor", exception);
            }
        }
    }

    /// Builds a diagnostic message for a syscall that is not implemented by the simulator.
    private static String unsupportedEcallMessage(MachineState state, long pc, long callNumber) {
        return "Unsupported ecall number: " + callNumber
                + ", pc=0x" + unsignedHex(pc)
                + ", a0=0x" + unsignedHex(state.register(10))
                + ", a1=0x" + unsignedHex(state.register(11))
                + ", a2=0x" + unsignedHex(state.register(12))
                + ", a3=0x" + unsignedHex(state.register(13))
                + ", a4=0x" + unsignedHex(state.register(14))
                + ", a5=0x" + unsignedHex(state.register(15))
                + ", a6=0x" + unsignedHex(state.register(16))
                + ", a7=0x" + unsignedHex(state.register(17));
    }

    /// Formats a guest register value as an unsigned hexadecimal string.
    private static String unsignedHex(long value) {
        return Long.toUnsignedString(value, 16);
    }

    /// Writes the deterministic guest working directory path.
    private long getcwd(long bufferAddress, long bufferSize) {
        byte[] pathBytes = guestWorkingDirectory.getBytes(StandardCharsets.UTF_8);
        byte[] currentDirectory = new byte[pathBytes.length + 1];
        System.arraycopy(pathBytes, 0, currentDirectory, 0, pathBytes.length);
        if (bufferSize < currentDirectory.length) {
            return ERANGE;
        }
        memory.writeBytes(bufferAddress, currentDirectory, 0, currentDirectory.length);
        return currentDirectory.length;
    }

    /// Duplicates a file descriptor to the lowest available non-standard descriptor.
    private long dup(int fileDescriptor) {
        @Nullable OpenFile duplicate = duplicateOpenFile(fileDescriptor);
        if (duplicate == null) {
            return EBADF;
        }

        return addOpenFile(duplicate);
    }

    /// Duplicates a file descriptor to an explicit non-standard descriptor.
    private long dup3(int oldFileDescriptor, int newFileDescriptor, long flags) {
        if ((flags & ~SUPPORTED_DUP3_FLAGS) != 0 || oldFileDescriptor == newFileDescriptor) {
            return EINVAL;
        }
        if (newFileDescriptor < 3) {
            return EBADF;
        }

        @Nullable OpenFile source = duplicateOpenFile(oldFileDescriptor);
        if (source == null) {
            return EBADF;
        }

        int index = openFileIndex(newFileDescriptor);
        try {
            if (index < openFiles.size()) {
                @Nullable OpenFile previous = openFiles.get(index);
                if (previous != null) {
                    openFiles.set(index, null);
                    releaseOpenFile(previous);
                }
            }
            setOpenFile(newFileDescriptor, source);
            return newFileDescriptor;
        } catch (IOException exception) {
            try {
                releaseOpenFile(source);
            } catch (IOException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw new RiscVException("Guest dup3 syscall failed", exception);
        }
    }

    /// Creates an in-memory Linux `eventfd` counter.
    private long eventfd2(long initialValue, long flags) {
        if ((flags & ~SUPPORTED_EVENTFD2_FLAGS) != 0) {
            return EINVAL;
        }

        long counterValue = initialValue & 0xffff_ffffL;
        EventCounter counter = new EventCounter(
                counterValue,
                (flags & EFD_SEMAPHORE) != 0,
                (flags & O_NONBLOCK) != 0);
        return addOpenFile(OpenFile.eventFile(counter));
    }

    /// Creates an in-memory Linux `epoll` descriptor.
    private long epollCreate1(long flags) {
        if ((flags & ~SUPPORTED_EPOLL_CREATE1_FLAGS) != 0) {
            return EINVAL;
        }
        return addOpenFile(OpenFile.epollFile(new EpollSet()));
    }

    /// Adds, modifies, or removes one descriptor interest from an in-memory `epoll` set.
    private long epollCtl(int epollFileDescriptor, int operation, int fileDescriptor, long eventAddress) {
        @Nullable OpenFile epollFile = openFile(epollFileDescriptor);
        if (epollFile == null) {
            return EBADF;
        }
        if (!epollFile.isEpollFile()) {
            return EINVAL;
        }
        if (!isOpenFileDescriptor(fileDescriptor)) {
            return EBADF;
        }
        if (fileDescriptor == epollFileDescriptor) {
            return EINVAL;
        }

        EpollSet epollSet = epollFile.epollSet();
        if (operation == EPOLL_CTL_DEL) {
            return epollSet.remove(fileDescriptor);
        }
        if (operation != EPOLL_CTL_ADD && operation != EPOLL_CTL_MOD) {
            return EINVAL;
        }
        if (eventAddress == 0) {
            return EINVAL;
        }

        int events = memory.readInt(eventAddress + EPOLL_EVENT_EVENTS_OFFSET);
        long data = readLongUnaligned(eventAddress + EPOLL_EVENT_DATA_OFFSET);
        if (operation == EPOLL_CTL_ADD) {
            return epollSet.add(fileDescriptor, events, data);
        }
        return epollSet.modify(fileDescriptor, events, data);
    }

    /// Returns currently ready events from an in-memory `epoll` descriptor without blocking the host thread.
    private long epollPwait(
            int epollFileDescriptor,
            long eventsAddress,
            int maximumEvents,
            int timeoutMilliseconds,
            long signalMaskAddress,
            long signalSetSize) {
        if (maximumEvents <= 0) {
            return EINVAL;
        }
        if (signalMaskAddress != 0 && signalSetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }

        @Nullable OpenFile epollFile = openFile(epollFileDescriptor);
        if (epollFile == null) {
            return EBADF;
        }
        if (!epollFile.isEpollFile()) {
            return EINVAL;
        }

        int count = 0;
        EpollSet epollSet = epollFile.epollSet();
        for (int index = 0; index < epollSet.size() && count < maximumEvents; index++) {
            EpollInterest interest = epollSet.interest(index);
            int readyEvents = readyEventsFor(interest.fileDescriptor());
            int reportedEvents = (readyEvents & interest.events()) | (readyEvents & (EPOLLERR | EPOLLHUP));
            if (reportedEvents == 0) {
                continue;
            }

            writeEpollEvent(eventsAddress + (long) count * EPOLL_EVENT_SIZE, reportedEvents, interest.data());
            count++;
        }
        return count;
    }

    /// Creates an in-memory pipe and writes its read and write descriptors to guest memory.
    private long pipe2(long pipeAddress, long flags) {
        if ((flags & ~SUPPORTED_PIPE2_FLAGS) != 0) {
            return EINVAL;
        }

        boolean nonblocking = (flags & O_NONBLOCK) != 0;
        PipeBuffer pipe = new PipeBuffer();
        long readFileDescriptor = addOpenFile(OpenFile.pipeReader(pipe, nonblocking));
        long writeFileDescriptor = addOpenFile(OpenFile.pipeWriter(pipe, nonblocking));
        memory.writeInt(pipeAddress, (int) readFileDescriptor);
        memory.writeInt(pipeAddress + Integer.BYTES, (int) writeFileDescriptor);
        return 0;
    }

    /// Reads Linux `struct linux_dirent64` records from an open directory descriptor.
    private long getdents64(int fileDescriptor, long bufferAddress, long byteCount) {
        if (byteCount <= 0) {
            return EINVAL;
        }
        if (isStandardFileDescriptor(fileDescriptor)) {
            return ENOTDIR;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (!openFile.isDirectory()) {
            return ENOTDIR;
        }

        try {
            DirectoryEntry[] entries = directoryEntries(openFile);
            long written = 0;
            int index = openFile.directoryEntryIndex();
            while (index < entries.length) {
                DirectoryEntry entry = entries[index];
                byte[] name = entry.name().getBytes(StandardCharsets.UTF_8);
                int recordLength = directoryEntryRecordLength(name.length);
                if (recordLength > byteCount - written) {
                    if (written > 0) {
                        openFile.setDirectoryEntryIndex(index);
                    }
                    return written == 0 ? EINVAL : written;
                }

                writeDirectoryEntry(bufferAddress + written, entry, index + 1L, name, recordLength);
                written += recordLength;
                index++;
            }

            openFile.setDirectoryEntryIndex(index);
            return written;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Returns the cached entries for an open directory descriptor, loading them on first use.
    private DirectoryEntry[] directoryEntries(OpenFile openFile) throws IOException {
        @Nullable DirectoryEntry[] cachedEntries = openFile.directoryEntries();
        if (cachedEntries != null) {
            return cachedEntries;
        }

        @Nullable TruffleFile path = openFile.path();
        if (path == null) {
            throw new IOException("Directory descriptor has no path");
        }

        ArrayList<DirectoryEntry> entries = new ArrayList<>();
        entries.add(new DirectoryEntry(".", syntheticInode(path), DIRECTORY_ENTRY_DIRECTORY));
        entries.add(new DirectoryEntry("..", syntheticInode(parentDirectoryForDotDot(path)), DIRECTORY_ENTRY_DIRECTORY));

        ArrayList<DirectoryEntry> childEntries = new ArrayList<>();
        for (TruffleFile child : path.list()) {
            childEntries.add(new DirectoryEntry(child.getName(), syntheticInode(child), directoryEntryType(child)));
        }
        childEntries.sort((left, right) -> left.name().compareTo(right.name()));
        entries.addAll(childEntries);

        DirectoryEntry[] result = entries.toArray(DirectoryEntry[]::new);
        openFile.setDirectoryEntries(result);
        return result;
    }

    /// Returns the parent directory represented by the `..` entry without escaping the sandbox root.
    private TruffleFile parentDirectoryForDotDot(TruffleFile path) {
        @Nullable TruffleFile root = currentHostRoot();
        @Nullable TruffleFile parent = path.getParent();
        if (root != null && parent != null && parent.normalize().startsWith(root)) {
            return parent.normalize();
        }
        return path;
    }

    /// Returns the Linux directory entry type for a sandboxed host path.
    private static byte directoryEntryType(TruffleFile path) {
        try {
            if (path.isSymbolicLink()) {
                return DIRECTORY_ENTRY_SYMBOLIC_LINK;
            }
            if (path.isDirectory()) {
                return DIRECTORY_ENTRY_DIRECTORY;
            }
            if (path.isRegularFile()) {
                return DIRECTORY_ENTRY_REGULAR_FILE;
            }
            return DIRECTORY_ENTRY_UNKNOWN;
        } catch (SecurityException exception) {
            return DIRECTORY_ENTRY_UNKNOWN;
        }
    }

    /// Computes the aligned byte length of one Linux `struct linux_dirent64` record.
    private static int directoryEntryRecordLength(int nameLength) {
        return (int) alignUp(DIRENT64_NAME_OFFSET + nameLength + 1L, DIRENT64_RECORD_ALIGNMENT);
    }

    /// Writes one Linux `struct linux_dirent64` record to guest memory.
    private void writeDirectoryEntry(
            long address,
            DirectoryEntry entry,
            long nextOffset,
            byte[] name,
            int recordLength) {
        memory.clear(address, recordLength);
        memory.writeLong(address + DIRENT64_INODE_OFFSET, entry.inode());
        memory.writeLong(address + DIRENT64_NEXT_OFFSET, nextOffset);
        memory.writeShort(address + DIRENT64_RECORD_LENGTH_OFFSET, (short) recordLength);
        memory.writeByte(address + DIRENT64_TYPE_OFFSET, entry.type());
        memory.writeBytes(address + DIRENT64_NAME_OFFSET, name, 0, name.length);
    }

    /// Checks access to a sandboxed host path or open file descriptor.
    private long faccessat(long directoryFileDescriptor, long pathAddress, long mode, long flags) {
        if ((mode & ~ACCESS_MODE_MASK) != 0 || (flags & ~SUPPORTED_FACCESSAT2_FLAGS) != 0) {
            return EINVAL;
        }

        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }

        if (guestPath.isEmpty()) {
            if ((flags & AT_EMPTY_PATH) == 0) {
                return ENOENT;
            }
            if (directoryFileDescriptor == AT_FDCWD) {
                @Nullable TruffleFile currentDirectory = currentHostWorkingDirectory();
                return currentDirectory == null ? EACCES : accessHostFile(currentDirectory, mode);
            }
            return accessFileDescriptor((int) directoryFileDescriptor, mode);
        }

        if (!canResolvePathFrom(directoryFileDescriptor, guestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(directoryFileDescriptor, guestPath);
        if (hostFile == null) {
            return EACCES;
        }
        return accessHostFile(hostFile, mode);
    }

    /// Checks access bits on an open guest file descriptor.
    private long accessFileDescriptor(int fileDescriptor, long mode) {
        int standardFileDescriptor = standardFileDescriptorFor(fileDescriptor);
        if (standardFileDescriptor >= 0) {
            if ((mode & W_OK) != 0 && standardFileDescriptor == 0) {
                return EACCES;
            }
            if ((mode & R_OK) != 0 && standardFileDescriptor != 0) {
                return EACCES;
            }
            return 0;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if ((mode & R_OK) != 0 && !openFile.readable()) {
            return EACCES;
        }
        if ((mode & W_OK) != 0 && !openFile.writable()) {
            return EACCES;
        }
        if ((mode & X_OK) != 0 && !openFile.isDirectory()) {
            return EACCES;
        }
        return 0;
    }

    /// Checks access bits on a sandboxed host file.
    private long accessHostFile(TruffleFile hostFile, long mode) {
        try {
            if (!hostFile.exists()) {
                return ENOENT;
            }
            if (!canonicalFileStaysBelowHostRoot(hostFile)) {
                return EACCES;
            }
            if ((mode & R_OK) != 0 && !hostFile.isReadable()) {
                return EACCES;
            }
            if ((mode & W_OK) != 0 && !hostFile.isWritable()) {
                return EACCES;
            }
            if ((mode & X_OK) != 0 && !hostFile.isExecutable()) {
                return EACCES;
            }
            return 0;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Creates a directory below the configured host root without applying guest mode bits.
    private long mkdirat(long directoryFileDescriptor, long pathAddress, long mode) {
        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }
        if (guestPath.isEmpty()) {
            return ENOENT;
        }

        if (!canResolvePathFrom(directoryFileDescriptor, guestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(directoryFileDescriptor, guestPath);
        if (hostFile == null) {
            return EACCES;
        }

        long parentError = validateSandboxedParent(hostFile);
        if (parentError != 0) {
            return parentError;
        }

        try {
            if (pathEntryExists(hostFile)) {
                return EEXIST;
            }
            hostFile.createDirectory();
            return 0;
        } catch (FileAlreadyExistsException exception) {
            return EEXIST;
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Removes a file or empty directory below the configured host root.
    private long unlinkat(long directoryFileDescriptor, long pathAddress, long flags) {
        if ((flags & ~AT_REMOVEDIR) != 0) {
            return EINVAL;
        }

        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }
        if (guestPath.isEmpty()) {
            return ENOENT;
        }

        if (!canResolvePathFrom(directoryFileDescriptor, guestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(directoryFileDescriptor, guestPath);
        if (hostFile == null) {
            return EACCES;
        }

        long parentError = validateSandboxedParent(hostFile);
        if (parentError != 0) {
            return parentError;
        }

        boolean removeDirectory = (flags & AT_REMOVEDIR) != 0;
        try {
            boolean symbolicLink = hostFile.isSymbolicLink();
            if (!pathEntryExists(hostFile)) {
                return ENOENT;
            }
            if (removeDirectory) {
                if (symbolicLink || !hostFile.isDirectory()) {
                    return ENOTDIR;
                }
            } else if (!symbolicLink && hostFile.isDirectory()) {
                return EISDIR;
            }

            hostFile.delete();
            return 0;
        } catch (NoSuchFileException exception) {
            return ENOENT;
        } catch (DirectoryNotEmptyException exception) {
            return ENOTEMPTY;
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Renames a sandboxed host path to another sandboxed host path.
    private long renameat(
            long oldDirectoryFileDescriptor,
            long oldPathAddress,
            long newDirectoryFileDescriptor,
            long newPathAddress) {
        @Nullable String oldGuestPath = readGuestPath(oldPathAddress);
        if (oldGuestPath == null) {
            return ENAMETOOLONG;
        }
        @Nullable String newGuestPath = readGuestPath(newPathAddress);
        if (newGuestPath == null) {
            return ENAMETOOLONG;
        }
        if (oldGuestPath.isEmpty() || newGuestPath.isEmpty()) {
            return ENOENT;
        }

        if (!canResolvePathFrom(oldDirectoryFileDescriptor, oldGuestPath)
                || !canResolvePathFrom(newDirectoryFileDescriptor, newGuestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile oldHostFile = resolveHostFile(oldDirectoryFileDescriptor, oldGuestPath);
        @Nullable TruffleFile newHostFile = resolveHostFile(newDirectoryFileDescriptor, newGuestPath);
        if (oldHostFile == null || newHostFile == null) {
            return EACCES;
        }

        long oldParentError = validateSandboxedParent(oldHostFile);
        if (oldParentError != 0) {
            return oldParentError;
        }
        long newParentError = validateSandboxedParent(newHostFile);
        if (newParentError != 0) {
            return newParentError;
        }

        try {
            if (!pathEntryExists(oldHostFile)) {
                return ENOENT;
            }

            boolean oldDirectory = !oldHostFile.isSymbolicLink() && oldHostFile.isDirectory();
            if (pathEntryExists(newHostFile)) {
                boolean newDirectory = !newHostFile.isSymbolicLink() && newHostFile.isDirectory();
                if (oldDirectory && !newDirectory) {
                    return ENOTDIR;
                }
                if (!oldDirectory && newDirectory) {
                    return EISDIR;
                }
            }

            oldHostFile.move(newHostFile, StandardCopyOption.REPLACE_EXISTING);
            return 0;
        } catch (NoSuchFileException exception) {
            return ENOENT;
        } catch (DirectoryNotEmptyException exception) {
            return ENOTEMPTY;
        } catch (FileAlreadyExistsException exception) {
            return EEXIST;
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Handles `renameat2` without Linux-specific nonzero rename flags.
    private long renameat2(
            long oldDirectoryFileDescriptor,
            long oldPathAddress,
            long newDirectoryFileDescriptor,
            long newPathAddress,
            long flags) {
        if (flags != 0) {
            return EINVAL;
        }
        return renameat(oldDirectoryFileDescriptor, oldPathAddress, newDirectoryFileDescriptor, newPathAddress);
    }

    /// Truncates a sandboxed host file selected by path.
    private long truncate(long pathAddress, long length) {
        if (length < 0) {
            return EINVAL;
        }

        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }
        if (guestPath.isEmpty()) {
            return ENOENT;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(AT_FDCWD, guestPath);
        if (hostFile == null) {
            return EACCES;
        }

        try {
            if (!pathEntryExists(hostFile)) {
                return ENOENT;
            }
            if (!canonicalFileStaysBelowHostRoot(hostFile)) {
                return EACCES;
            }
            if (hostFile.isDirectory()) {
                return EISDIR;
            }
            if (!hostFile.isRegularFile()) {
                return ENODEV;
            }
            if (!hostFile.isWritable()) {
                return EACCES;
            }

            try (SeekableByteChannel channel = hostFile.newByteChannel(EnumSet.of(StandardOpenOption.WRITE))) {
                resizeHostChannel(channel, length);
            }
            return 0;
        } catch (NoSuchFileException exception) {
            return ENOENT;
        } catch (IOException | UnsupportedOperationException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Truncates the open host file referenced by a guest file descriptor.
    private long ftruncate(int fileDescriptor, long length) {
        if (length < 0) {
            return EINVAL;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (!openFile.isHostFile()) {
            return EINVAL;
        }
        if (!openFile.writable()) {
            return EBADF;
        }

        try {
            resizeHostChannel(openFile.channel(), length);
            return 0;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Changes the guest-visible current working directory to a sandboxed host directory.
    private long chdir(long pathAddress) {
        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }
        if (guestPath.isEmpty()) {
            return ENOENT;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(AT_FDCWD, guestPath);
        if (hostFile == null) {
            return EACCES;
        }
        return changeWorkingDirectory(hostFile);
    }

    /// Changes the guest-visible current working directory to an open directory descriptor.
    private long fchdir(int fileDescriptor) {
        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (!openFile.isDirectory()) {
            return ENOTDIR;
        }

        @Nullable TruffleFile path = openFile.path();
        if (path == null) {
            return EBADF;
        }
        return changeWorkingDirectory(path);
    }

    /// Applies a host directory as the guest-visible current working directory.
    private long changeWorkingDirectory(TruffleFile hostFile) {
        try {
            if (!pathEntryExists(hostFile)) {
                return ENOENT;
            }
            if (!canonicalFileStaysBelowHostRoot(hostFile)) {
                return EACCES;
            }
            if (!hostFile.isDirectory()) {
                return ENOTDIR;
            }

            @Nullable String guestPath = guestPathForHostFile(hostFile);
            if (guestPath == null) {
                return EACCES;
            }
            guestWorkingDirectory = guestPath;
            return 0;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Opens a host file or directory below the configured host root.
    private long openat(long directoryFileDescriptor, long pathAddress, long flags, long mode) {
        long accessMode = flags & O_ACCMODE;
        if (accessMode != O_RDONLY && accessMode != O_WRONLY && accessMode != O_RDWR) {
            return EINVAL;
        }

        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }
        if (guestPath.isEmpty()) {
            return ENOENT;
        }

        if (!canResolvePathFrom(directoryFileDescriptor, guestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(directoryFileDescriptor, guestPath);
        if (hostFile == null) {
            return EACCES;
        }

        boolean readable = accessMode == O_RDONLY || accessMode == O_RDWR;
        boolean writable = accessMode == O_WRONLY || accessMode == O_RDWR;
        boolean create = (flags & O_CREAT) != 0;
        boolean truncate = (flags & O_TRUNC) != 0;
        boolean append = (flags & O_APPEND) != 0;
        boolean directoryOnly = (flags & O_DIRECTORY) != 0;
        if (directoryOnly && create) {
            return EINVAL;
        }

        boolean exists;
        try {
            exists = hostFile.exists();
        } catch (SecurityException exception) {
            return EACCES;
        }
        if (!exists && !create) {
            return ENOENT;
        }
        if (exists && create && (flags & O_EXCL) != 0) {
            return EEXIST;
        }
        if ((truncate || append) && !writable) {
            return EACCES;
        }
        try {
            @Nullable TruffleFile root = currentHostRoot();
            if (root == null) {
                return EACCES;
            }
            TruffleFile realHostRoot = root.getCanonicalFile();
            if (exists) {
                if (!hostFile.getCanonicalFile().startsWith(realHostRoot)) {
                    return EACCES;
                }
            } else {
                @Nullable TruffleFile parent = hostFile.getParent();
                if (parent == null
                        || !parent.isDirectory()
                        || !parent.getCanonicalFile().startsWith(realHostRoot)) {
                    return EACCES;
                }
            }
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
        try {
            exists = hostFile.exists();
            if (exists && hostFile.isDirectory()) {
                if (!directoryOnly || writable || truncate || append) {
                    return EISDIR;
                }
                return addOpenFile(OpenFile.hostDirectory(hostFile));
            }
            if (directoryOnly) {
                return ENOTDIR;
            }
            if (exists && !hostFile.isRegularFile()) {
                return ENODEV;
            }
            if (exists && readable && !hostFile.isReadable()) {
                return EACCES;
            }
            if (exists && writable && !hostFile.isWritable()) {
                return EACCES;
            }
        } catch (SecurityException exception) {
            return EACCES;
        }

        try {
            Set<StandardOpenOption> options = EnumSet.noneOf(StandardOpenOption.class);
            if (readable) {
                options.add(StandardOpenOption.READ);
            }
            if (writable) {
                options.add(StandardOpenOption.WRITE);
            }
            if (create) {
                options.add(StandardOpenOption.CREATE);
            }
            if (truncate) {
                options.add(StandardOpenOption.TRUNCATE_EXISTING);
            }

            SeekableByteChannel channel = hostFile.newByteChannel(options);
            return addOpenFile(OpenFile.hostFile(hostFile, channel, readable, writable, append));
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Reads bytes from guest stdin, an open host file, or a pipe endpoint into guest memory.
    private long read(int fileDescriptor, long address, long length) {
        if (length < 0) {
            return EINVAL;
        }
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest read syscall buffer is too large: " + length);
        }
        if (length == 0) {
            return 0;
        }

        try {
            @Nullable InputStream stream = inputStreamFor(fileDescriptor);
            if (stream != null) {
                byte[] buffer = new byte[(int) length];
                int count = stream.read(buffer);
                if (count < 0) {
                    return 0;
                }

                memory.writeBytes(address, buffer, 0, count);
                return count;
            }

            @Nullable OpenFile openFile = openFile(fileDescriptor);
            if (openFile == null || !openFile.readable()) {
                return EBADF;
            }
            if (openFile.isEventFile()) {
                return readEventFile(openFile.eventCounter(), address, length);
            }
            if (openFile.isEpollFile()) {
                return EINVAL;
            }
            if (openFile.isDirectory()) {
                return EISDIR;
            }

            byte[] buffer = new byte[(int) length];
            int count;
            if (openFile.isPipeReader()) {
                count = openFile.pipe().read(buffer, buffer.length, openFile.nonblocking());
                if (count < 0) {
                    return EAGAIN;
                }
            } else if (openFile.isHostFile()) {
                count = openFile.channel().read(ByteBuffer.wrap(buffer));
            } else {
                return EBADF;
            }
            if (count < 0) {
                return 0;
            }

            memory.writeBytes(address, buffer, 0, count);
            return count;
        } catch (IOException exception) {
            throw new RiscVException("Guest read syscall failed", exception);
        }
    }

    /// Writes guest memory bytes to stdout, stderr, an open host file, or a pipe endpoint.
    private long write(int fileDescriptor, long address, long length) {
        if (length < 0) {
            return EINVAL;
        }
        if (length == 0) {
            return 0;
        }

        OutputStream stream = outputStreamFor(fileDescriptor);
        try {
            if (stream != null) {
                byte[] bytes = memory.readBytes(address, length);
                stream.write(bytes);
                stream.flush();
            } else {
                @Nullable OpenFile openFile = openFile(fileDescriptor);
                if (openFile == null || !openFile.writable()) {
                    return EBADF;
                }
                if (openFile.isEventFile()) {
                    return writeEventFile(openFile.eventCounter(), address, length);
                }
                if (openFile.isEpollFile()) {
                    return EINVAL;
                }
                byte[] bytes = memory.readBytes(address, length);
                long count = writeOpenFile(openFile, bytes);
                if (count < 0) {
                    return count;
                }
                return bytes.length;
            }
            return length;
        } catch (IOException exception) {
            throw new RiscVException("Guest write syscall failed", exception);
        }
    }

    /// Reads bytes from guest stdin, an open host file, or a pipe endpoint into a guest `struct iovec` array.
    private long readv(int fileDescriptor, long iovecAddress, long iovecCount) {
        if (iovecCount < 0 || iovecCount > IOV_MAX) {
            return EINVAL;
        }
        if (inputStreamFor(fileDescriptor) == null) {
            @Nullable OpenFile openFile = openFile(fileDescriptor);
            if (openFile == null || !openFile.readable()) {
                return EBADF;
            }
        }

        long total = 0;
        for (long index = 0; index < iovecCount; index++) {
            long entryAddress = iovecAddress + index * IOVEC_SIZE;
            long baseAddress = memory.readLong(entryAddress + IOVEC_BASE_OFFSET);
            long length = memory.readLong(entryAddress + IOVEC_LENGTH_OFFSET);
            if (length < 0) {
                return EINVAL;
            }
            if (length > Integer.MAX_VALUE) {
                throw new RiscVException("Guest readv syscall buffer is too large: " + length);
            }
            if (length == 0) {
                continue;
            }

            long count = read(fileDescriptor, baseAddress, length);
            if (count < 0) {
                return count;
            }

            total += count;
            if (count < length) {
                return total;
            }
        }
        return total;
    }

    /// Writes buffers from a guest `struct iovec` array to stdout, stderr, an open host file, or a pipe endpoint.
    private long writev(int fileDescriptor, long iovecAddress, long iovecCount) {
        if (iovecCount < 0 || iovecCount > IOV_MAX) {
            return EINVAL;
        }

        if (outputStreamFor(fileDescriptor) == null) {
            @Nullable OpenFile openFile = openFile(fileDescriptor);
            if (openFile == null || !openFile.writable()) {
                return EBADF;
            }
        }

        long total = 0;
        for (long index = 0; index < iovecCount; index++) {
            long entryAddress = iovecAddress + index * IOVEC_SIZE;
            long baseAddress = memory.readLong(entryAddress + IOVEC_BASE_OFFSET);
            long length = memory.readLong(entryAddress + IOVEC_LENGTH_OFFSET);
            if (length < 0) {
                return EINVAL;
            }
            if (length > Integer.MAX_VALUE) {
                throw new RiscVException("Guest writev syscall buffer is too large: " + length);
            }

            long count = write(fileDescriptor, baseAddress, length);
            if (count < 0) {
                return total == 0 ? count : total;
            }
            total += count;
            if (count < length) {
                return total;
            }
        }
        return total;
    }

    /// Reads bytes from a host file at a fixed offset without changing the descriptor offset.
    private long pread64(int fileDescriptor, long address, long length, long offset) {
        if (length < 0 || offset < 0) {
            return EINVAL;
        }
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest pread64 syscall buffer is too large: " + length);
        }
        if (length == 0) {
            return 0;
        }
        if (standardFileDescriptorFor(fileDescriptor) >= 0) {
            return ESPIPE;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null || !openFile.readable()) {
            return EBADF;
        }
        if (openFile.isDirectory()) {
            return EISDIR;
        }
        if (!openFile.isHostFile()) {
            return ESPIPE;
        }

        try {
            byte[] buffer = new byte[(int) length];
            int count = readHostFileAt(openFile.channel(), offset, ByteBuffer.wrap(buffer));
            if (count < 0) {
                return 0;
            }

            memory.writeBytes(address, buffer, 0, count);
            return count;
        } catch (IOException exception) {
            throw new RiscVException("Guest pread64 syscall failed", exception);
        }
    }

    /// Writes guest memory bytes to a host file at a fixed offset without changing the descriptor offset.
    private long pwrite64(int fileDescriptor, long address, long length, long offset) {
        if (length < 0 || offset < 0) {
            return EINVAL;
        }
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest pwrite64 syscall buffer is too large: " + length);
        }
        if (length == 0) {
            return 0;
        }
        if (standardFileDescriptorFor(fileDescriptor) >= 0) {
            return fileDescriptor == 0 ? EBADF : ESPIPE;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null || !openFile.writable()) {
            return EBADF;
        }
        if (openFile.isDirectory()) {
            return EISDIR;
        }
        if (!openFile.isHostFile()) {
            return ESPIPE;
        }

        try {
            return writeHostFileAt(openFile, memory.readBytes(address, length), offset);
        } catch (IOException exception) {
            throw new RiscVException("Guest pwrite64 syscall failed", exception);
        }
    }

    /// Writes all bytes to an open host file or pipe endpoint.
    private static long writeOpenFile(OpenFile openFile, byte[] bytes) throws IOException {
        if (openFile.isPipeWriter()) {
            return openFile.pipe().write(bytes);
        }
        if (openFile.append()) {
            openFile.channel().position(openFile.channel().size());
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            openFile.channel().write(buffer);
        }
        return bytes.length;
    }

    /// Reads one little-endian counter value from an `eventfd` descriptor.
    private long readEventFile(EventCounter counter, long address, long length) {
        if (length < Long.BYTES) {
            return EINVAL;
        }
        if (!counter.isReadable()) {
            return EAGAIN;
        }

        writeLongUnaligned(address, counter.readValue());
        return Long.BYTES;
    }

    /// Writes one little-endian counter increment to an `eventfd` descriptor.
    private long writeEventFile(EventCounter counter, long address, long length) {
        if (length != Long.BYTES) {
            return EINVAL;
        }

        long result = counter.write(readLongUnaligned(address));
        return result < 0 ? result : Long.BYTES;
    }

    /// Computes the currently ready low-level `epoll` event bits for one guest descriptor.
    private int readyEventsFor(int fileDescriptor) {
        int standardFileDescriptor = standardFileDescriptorFor(fileDescriptor);
        if (standardFileDescriptor == 0) {
            return EPOLLIN;
        }
        if (standardFileDescriptor == 1 || standardFileDescriptor == 2) {
            return EPOLLOUT;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EPOLLHUP;
        }
        if (openFile.isEventFile()) {
            EventCounter counter = openFile.eventCounter();
            int events = 0;
            if (counter.isReadable()) {
                events |= EPOLLIN;
            }
            if (counter.isWritable()) {
                events |= EPOLLOUT;
            }
            return events;
        }
        if (openFile.isPipeReader()) {
            int events = openFile.pipe().isReadable() ? EPOLLIN : 0;
            if (!openFile.pipe().isWriterOpen()) {
                events |= EPOLLHUP;
            }
            return events;
        }
        if (openFile.isPipeWriter()) {
            return openFile.pipe().isReaderOpen() ? EPOLLOUT : EPOLLERR;
        }
        if (openFile.isHostFile() || openFile.isDirectory()) {
            int events = 0;
            if (openFile.readable()) {
                events |= EPOLLIN;
            }
            if (openFile.writable()) {
                events |= EPOLLOUT;
            }
            return events;
        }
        return 0;
    }

    /// Writes a packed Linux generic `struct epoll_event` to guest memory.
    private void writeEpollEvent(long eventAddress, int events, long data) {
        memory.writeInt(eventAddress + EPOLL_EVENT_EVENTS_OFFSET, events);
        writeLongUnaligned(eventAddress + EPOLL_EVENT_DATA_OFFSET, data);
    }

    /// Reads bytes at a fixed host file offset while preserving the channel position.
    private static int readHostFileAt(SeekableByteChannel channel, long offset, ByteBuffer buffer) throws IOException {
        long position = channel.position();
        try {
            channel.position(offset);
            return channel.read(buffer);
        } finally {
            channel.position(position);
        }
    }

    /// Writes bytes at a fixed host file offset while preserving the channel position.
    private static long writeHostFileAt(OpenFile openFile, byte[] bytes, long offset) throws IOException {
        SeekableByteChannel channel = openFile.channel();
        long position = channel.position();
        try {
            channel.position(openFile.append() ? channel.size() : offset);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            return bytes.length;
        } finally {
            channel.position(position);
        }
    }

    /// Handles `close` for standard streams and guest-opened file descriptors.
    private long close(int fileDescriptor) {
        if (isStandardFileDescriptor(fileDescriptor)) {
            return 0;
        }

        int index = openFileIndex(fileDescriptor);
        if (index < 0 || index >= openFiles.size()) {
            return EBADF;
        }

        @Nullable OpenFile openFile = openFiles.get(index);
        if (openFile == null) {
            return EBADF;
        }

        try {
            openFiles.set(index, null);
            releaseOpenFile(openFile);
            return 0;
        } catch (IOException exception) {
            throw new RiscVException("Guest close syscall failed", exception);
        }
    }

    /// Handles `lseek` for guest-opened host files while rejecting standard streams.
    private long lseek(int fileDescriptor, long offset, int whence) {
        if (whence < 0 || whence > 2) {
            return EINVAL;
        }
        if (standardFileDescriptorFor(fileDescriptor) >= 0) {
            return ESPIPE;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (!openFile.isHostFile()) {
            return ESPIPE;
        }

        try {
            long basePosition = switch (whence) {
                case 0 -> 0;
                case 1 -> openFile.channel().position();
                case 2 -> openFile.channel().size();
                default -> throw new AssertionError("validated whence");
            };
            if (offset > 0 && basePosition > Long.MAX_VALUE - offset) {
                return EINVAL;
            }
            if (offset < 0 && basePosition < Long.MIN_VALUE - offset) {
                return EINVAL;
            }

            long position = basePosition + offset;
            if (position < 0) {
                return EINVAL;
            }
            openFile.channel().position(position);
            return position;
        } catch (IOException exception) {
            throw new RiscVException("Guest lseek syscall failed", exception);
        }
    }

    /// Reads a sandboxed symbolic link target into guest memory.
    private long readlinkat(long directoryFileDescriptor, long pathAddress, long bufferAddress, long bufferSize) {
        if (bufferSize <= 0 || bufferSize > Integer.MAX_VALUE) {
            return EINVAL;
        }

        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }
        if (guestPath.isEmpty()) {
            return ENOENT;
        }

        if (!canResolvePathFrom(directoryFileDescriptor, guestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(directoryFileDescriptor, guestPath);
        if (hostFile == null) {
            return EACCES;
        }

        try {
            if (!hostFile.exists()) {
                return ENOENT;
            }
            if (!hostFile.isSymbolicLink()) {
                return EINVAL;
            }
            if (!canonicalFileStaysBelowHostRoot(hostFile)) {
                return EACCES;
            }

            String target = hostFile.readSymbolicLink().toString();
            byte[] targetBytes = target.getBytes(StandardCharsets.UTF_8);
            int length = Math.min(targetBytes.length, (int) bufferSize);
            memory.writeBytes(bufferAddress, targetBytes, 0, length);
            return length;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Writes a minimal Linux generic 64-bit `struct stat` for a path or file descriptor.
    private long newfstatat(long directoryFileDescriptor, long pathAddress, long statAddress, long flags) {
        if ((flags & ~SUPPORTED_NEWFSTATAT_FLAGS) != 0) {
            return EINVAL;
        }

        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }

        if (guestPath.isEmpty()) {
            if ((flags & AT_EMPTY_PATH) == 0) {
                return ENOENT;
            }
            if (directoryFileDescriptor == AT_FDCWD) {
                @Nullable TruffleFile currentDirectory = currentHostWorkingDirectory();
                return currentDirectory == null ? EACCES : statHostFile(currentDirectory, statAddress);
            }
            return fstat((int) directoryFileDescriptor, statAddress);
        }

        if (!canResolvePathFrom(directoryFileDescriptor, guestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(directoryFileDescriptor, guestPath);
        if (hostFile == null) {
            return EACCES;
        }
        return statHostFile(hostFile, statAddress);
    }

    /// Writes a minimal Linux generic 64-bit `struct stat` for a file descriptor.
    private long fstat(int fileDescriptor, long statAddress) {
        int standardFileDescriptor = standardFileDescriptorFor(fileDescriptor);
        if (standardFileDescriptor >= 0) {
            writeStat(statAddress, standardFileDescriptor + 1L, STANDARD_STREAM_STAT_MODE, 0);
            return 0;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (openFile.isPipe()) {
            int permissions = (openFile.readable() ? STAT_MODE_READ_ALL : 0)
                    | (openFile.writable() ? 0222 : 0);
            writeStat(statAddress, fileDescriptor + 1L, STAT_MODE_FIFO | permissions, 0);
            return 0;
        }
        if (openFile.isEventFile() || openFile.isEpollFile()) {
            int permissions = (openFile.readable() ? 0400 : 0) | (openFile.writable() ? 0200 : 0);
            writeStat(statAddress, fileDescriptor + 1L, STAT_MODE_REGULAR_FILE | permissions, 0);
            return 0;
        }
        if (openFile.isDirectory()) {
            writeStat(statAddress, fileDescriptor + 1L, STAT_MODE_DIRECTORY | STAT_MODE_READ_EXECUTE_ALL, 0);
            return 0;
        }

        try {
            long size = openFile.channel().size();
            int permissions = (openFile.readable() ? STAT_MODE_READ_ALL : 0)
                    | (openFile.writable() ? 0222 : 0);
            writeStat(statAddress, fileDescriptor + 1L, STAT_MODE_REGULAR_FILE | permissions, size);
            return 0;
        } catch (IOException exception) {
            throw new RiscVException("Guest fstat syscall failed", exception);
        }
    }

    /// Handles Linux `sync`, which is a process-wide best-effort flush.
    private static long sync() {
        return 0;
    }

    /// Flushes host file metadata and data for a guest file descriptor.
    private long fsync(int fileDescriptor) {
        return syncFileDescriptor(fileDescriptor, true);
    }

    /// Flushes host file data for a guest file descriptor.
    private long fdatasync(int fileDescriptor) {
        return syncFileDescriptor(fileDescriptor, false);
    }

    /// Flushes the sandbox filesystem referenced by a guest file descriptor.
    private long syncfs(int fileDescriptor) {
        if (isStandardFileDescriptor(fileDescriptor)) {
            return 0;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        return openFile == null ? EBADF : 0;
    }

    /// Flushes a regular host file channel when the backing implementation supports it.
    private long syncFileDescriptor(int fileDescriptor, boolean metadata) {
        if (isStandardFileDescriptor(fileDescriptor)) {
            return EINVAL;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (openFile.isPipe()) {
            return EINVAL;
        }
        if (openFile.isEventFile() || openFile.isEpollFile()) {
            return EINVAL;
        }
        if (openFile.isDirectory()) {
            return 0;
        }

        try {
            SeekableByteChannel channel = openFile.channel();
            if (channel instanceof FileChannel fileChannel) {
                fileChannel.force(metadata);
            }
            return 0;
        } catch (IOException exception) {
            throw new RiscVException("Guest file sync syscall failed", exception);
        }
    }

    /// Writes a Linux generic `struct statx` for a path or file descriptor.
    private long statx(
            long directoryFileDescriptor,
            long pathAddress,
            long flags,
            long requestedMask,
            long statxAddress) {
        if ((flags & ~SUPPORTED_STATX_FLAGS) != 0) {
            return EINVAL;
        }

        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }

        if (guestPath.isEmpty()) {
            if ((flags & AT_EMPTY_PATH) == 0) {
                return ENOENT;
            }
            if (directoryFileDescriptor == AT_FDCWD) {
                @Nullable TruffleFile currentDirectory = currentHostWorkingDirectory();
                return currentDirectory == null
                        ? EACCES
                        : statxHostFile(currentDirectory, statxAddress, flags, requestedMask);
            }
            return statxFileDescriptor((int) directoryFileDescriptor, requestedMask, statxAddress);
        }

        if (!canResolvePathFrom(directoryFileDescriptor, guestPath)) {
            return EBADF;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(directoryFileDescriptor, guestPath);
        if (hostFile == null) {
            return EACCES;
        }
        return statxHostFile(hostFile, statxAddress, flags, requestedMask);
    }

    /// Writes a Linux generic `struct statx` for a file descriptor.
    private long statxFileDescriptor(int fileDescriptor, long requestedMask, long statxAddress) {
        int standardFileDescriptor = standardFileDescriptorFor(fileDescriptor);
        if (standardFileDescriptor >= 0) {
            writeStatx(statxAddress, standardFileDescriptor + 1L, STANDARD_STREAM_STAT_MODE, 0, requestedMask);
            return 0;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (openFile.isPipe()) {
            int permissions = (openFile.readable() ? STAT_MODE_READ_ALL : 0)
                    | (openFile.writable() ? 0222 : 0);
            writeStatx(statxAddress, fileDescriptor + 1L, STAT_MODE_FIFO | permissions, 0, requestedMask);
            return 0;
        }
        if (openFile.isEventFile() || openFile.isEpollFile()) {
            int permissions = (openFile.readable() ? 0400 : 0) | (openFile.writable() ? 0200 : 0);
            writeStatx(statxAddress, fileDescriptor + 1L, STAT_MODE_REGULAR_FILE | permissions, 0, requestedMask);
            return 0;
        }
        if (openFile.isDirectory()) {
            writeStatx(
                    statxAddress,
                    fileDescriptor + 1L,
                    STAT_MODE_DIRECTORY | STAT_MODE_READ_EXECUTE_ALL,
                    0,
                    requestedMask);
            return 0;
        }

        try {
            long size = openFile.channel().size();
            int permissions = (openFile.readable() ? STAT_MODE_READ_ALL : 0)
                    | (openFile.writable() ? 0222 : 0);
            writeStatx(statxAddress, fileDescriptor + 1L, STAT_MODE_REGULAR_FILE | permissions, size, requestedMask);
            return 0;
        } catch (IOException exception) {
            throw new RiscVException("Guest statx syscall failed", exception);
        }
    }

    /// Writes a deterministic Linux generic 64-bit `struct statfs` for a sandboxed path.
    private long statfs(long pathAddress, long statfsAddress) {
        @Nullable String guestPath = readGuestPath(pathAddress);
        if (guestPath == null) {
            return ENAMETOOLONG;
        }
        if (guestPath.isEmpty()) {
            return ENOENT;
        }

        @Nullable TruffleFile hostFile = resolveHostFile(AT_FDCWD, guestPath);
        if (hostFile == null) {
            return EACCES;
        }
        return statfsHostFile(hostFile, statfsAddress);
    }

    /// Writes a deterministic Linux generic 64-bit `struct statfs` for a file descriptor.
    private long fstatfs(int fileDescriptor, long statfsAddress) {
        if (isStandardFileDescriptor(fileDescriptor)) {
            writeStatfs(statfsAddress);
            return 0;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }
        if (openFile.isPipe()) {
            writeStatfs(statfsAddress);
            return 0;
        }

        @Nullable TruffleFile path = openFile.path();
        return path == null ? EACCES : statfsHostFile(path, statfsAddress);
    }

    /// Writes deterministic filesystem metadata for an existing sandboxed host path.
    private long statfsHostFile(TruffleFile hostFile, long statfsAddress) {
        try {
            if (!pathEntryExists(hostFile)) {
                return ENOENT;
            }
            if (!canonicalFileStaysBelowHostRoot(hostFile)) {
                return EACCES;
            }
            writeStatfs(statfsAddress);
            return 0;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Writes a minimal Linux generic 64-bit `struct stat` for a sandboxed host file.
    private long statHostFile(TruffleFile hostFile, long statAddress) {
        try {
            if (!hostFile.exists()) {
                return ENOENT;
            }
            if (!canonicalFileStaysBelowHostRoot(hostFile)) {
                return EACCES;
            }

            if (hostFile.isDirectory()) {
                int permissions = hostFile.isReadable() ? STAT_MODE_READ_EXECUTE_ALL : 0;
                writeStat(statAddress, syntheticInode(hostFile), STAT_MODE_DIRECTORY | permissions, 0);
                return 0;
            }
            if (hostFile.isRegularFile()) {
                long size = hostFile.size();
                int permissions = (hostFile.isReadable() ? STAT_MODE_READ_ALL : 0)
                        | (hostFile.isWritable() ? 0222 : 0);
                writeStat(statAddress, syntheticInode(hostFile), STAT_MODE_REGULAR_FILE | permissions, size);
                return 0;
            }
            return ENODEV;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Writes a Linux generic `struct statx` for a sandboxed host file.
    private long statxHostFile(TruffleFile hostFile, long statxAddress, long flags, long requestedMask) {
        try {
            if (!pathEntryExists(hostFile)) {
                return ENOENT;
            }
            if ((flags & AT_SYMLINK_NOFOLLOW) != 0 && hostFile.isSymbolicLink()) {
                String target = hostFile.readSymbolicLink().toString();
                long size = target.getBytes(StandardCharsets.UTF_8).length;
                writeStatx(
                        statxAddress,
                        syntheticInode(hostFile),
                        STAT_MODE_SYMBOLIC_LINK | STAT_MODE_ALL,
                        size,
                        requestedMask);
                return 0;
            }
            if (!hostFile.exists()) {
                return ENOENT;
            }
            if (!canonicalFileStaysBelowHostRoot(hostFile)) {
                return EACCES;
            }

            if (hostFile.isDirectory()) {
                int permissions = hostFile.isReadable() ? STAT_MODE_READ_EXECUTE_ALL : 0;
                writeStatx(
                        statxAddress,
                        syntheticInode(hostFile),
                        STAT_MODE_DIRECTORY | permissions,
                        0,
                        requestedMask);
                return 0;
            }
            if (hostFile.isRegularFile()) {
                long size = hostFile.size();
                int permissions = (hostFile.isReadable() ? STAT_MODE_READ_ALL : 0)
                        | (hostFile.isWritable() ? 0222 : 0);
                writeStatx(
                        statxAddress,
                        syntheticInode(hostFile),
                        STAT_MODE_REGULAR_FILE | permissions,
                        size,
                        requestedMask);
                return 0;
            }
            return ENODEV;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Writes the shared subset of Linux generic 64-bit `struct stat` fields.
    private void writeStat(long statAddress, long inode, int mode, long size) {
        memory.clear(statAddress, STAT_SIZE);
        memory.writeLong(statAddress + STAT_INODE_OFFSET, inode);
        memory.writeInt(statAddress + STAT_MODE_OFFSET, mode);
        memory.writeInt(statAddress + STAT_LINK_COUNT_OFFSET, 1);
        memory.writeLong(statAddress + STAT_SIZE_OFFSET, size);
        memory.writeInt(statAddress + STAT_BLOCK_SIZE_OFFSET, STANDARD_STREAM_BLOCK_SIZE);
        memory.writeLong(statAddress + STAT_BLOCK_COUNT_OFFSET, (size + 511L) / 512L);
    }

    /// Writes deterministic Linux generic `struct statx` fields.
    private void writeStatx(long statxAddress, long inode, int mode, long size, long requestedMask) {
        int returnedMask = (int) (requestedMask & STATX_BASIC_STATS_MASK);
        if (returnedMask == 0) {
            returnedMask = STATX_BASIC_STATS_MASK;
        }
        memory.clear(statxAddress, STATX_SIZE);
        memory.writeInt(statxAddress + STATX_MASK_OFFSET, returnedMask);
        memory.writeInt(statxAddress + STATX_BLOCK_SIZE_OFFSET, STANDARD_STREAM_BLOCK_SIZE);
        memory.writeLong(statxAddress + STATX_ATTRIBUTES_OFFSET, 0);
        memory.writeInt(statxAddress + STATX_LINK_COUNT_OFFSET, 1);
        memory.writeInt(statxAddress + STATX_UID_OFFSET, (int) GUEST_USER_ID);
        memory.writeInt(statxAddress + STATX_GID_OFFSET, (int) GUEST_GROUP_ID);
        memory.writeShort(statxAddress + STATX_MODE_OFFSET, (short) mode);
        memory.writeLong(statxAddress + STATX_INODE_OFFSET, inode);
        memory.writeLong(statxAddress + STATX_FILE_SIZE_OFFSET, size);
        memory.writeLong(statxAddress + STATX_BLOCK_COUNT_OFFSET, (size + 511L) / 512L);
        memory.writeLong(statxAddress + STATX_ATTRIBUTES_MASK_OFFSET, STATX_ATTRIBUTES_MASK);
        memory.writeInt(statxAddress + STATX_DEVICE_MAJOR_OFFSET, 0);
        memory.writeInt(statxAddress + STATX_DEVICE_MINOR_OFFSET, 0);
        memory.writeLong(statxAddress + STATX_MOUNT_ID_OFFSET, STATX_SYNTHETIC_MOUNT_ID);
    }

    /// Writes deterministic Linux generic 64-bit `struct statfs` fields.
    private void writeStatfs(long statfsAddress) {
        memory.clear(statfsAddress, STATFS_SIZE);
        memory.writeLong(statfsAddress + STATFS_TYPE_OFFSET, STATFS_MAGIC);
        memory.writeLong(statfsAddress + STATFS_BLOCK_SIZE_OFFSET, STATFS_BLOCK_SIZE);
        memory.writeLong(statfsAddress + STATFS_BLOCKS_OFFSET, STATFS_BLOCK_COUNT);
        memory.writeLong(statfsAddress + STATFS_BLOCKS_FREE_OFFSET, STATFS_BLOCK_COUNT);
        memory.writeLong(statfsAddress + STATFS_BLOCKS_AVAILABLE_OFFSET, STATFS_BLOCK_COUNT);
        memory.writeLong(statfsAddress + STATFS_FILES_OFFSET, STATFS_FILE_COUNT);
        memory.writeLong(statfsAddress + STATFS_FILES_FREE_OFFSET, STATFS_FILE_COUNT);
        memory.writeLong(statfsAddress + STATFS_NAME_LENGTH_OFFSET, STATFS_NAME_MAX);
        memory.writeLong(statfsAddress + STATFS_FRAGMENT_SIZE_OFFSET, STATFS_BLOCK_SIZE);
        memory.writeLong(statfsAddress + STATFS_FLAGS_OFFSET, 0);
    }

    /// Returns a deterministic synthetic inode number for a sandboxed host path.
    private static long syntheticInode(TruffleFile hostFile) {
        return Integer.toUnsignedLong(hostFile.toString().hashCode()) + 1024L;
    }

    /// Handles tty-related ioctls used by common `isatty` and stdio setup paths.
    private long ioctl(int fileDescriptor, long request, long argument) {
        int standardFileDescriptor = standardFileDescriptorFor(fileDescriptor);
        if (standardFileDescriptor < 0) {
            if (isOpenFileDescriptor(fileDescriptor)) {
                return ENOTTY;
            }
            return EBADF;
        }
        if (request == TCGETS) {
            memory.clear(argument, TERMIOS_SIZE);
            return 0;
        }
        if (request == TIOCGWINSZ) {
            memory.clear(argument, WINSIZE_SIZE);
            memory.writeShort(argument, DEFAULT_TERMINAL_ROWS);
            memory.writeShort(argument + Short.BYTES, DEFAULT_TERMINAL_COLUMNS);
            return 0;
        }
        return ENOTTY;
    }

    /// Handles the minimal `fcntl` subset used by static single-process libc code.
    private long fcntl(int fileDescriptor, long command, long argument) {
        if (!isOpenFileDescriptor(fileDescriptor)) {
            return EBADF;
        }

        if (command == F_GETFD) {
            return 0;
        }
        if (command == F_SETFD) {
            return 0;
        }
        if (command == F_GETFL) {
            return statusFlagsFor(fileDescriptor);
        }
        if (command == F_SETFL) {
            return 0;
        }
        return EINVAL;
    }

    /// Throws when another guest thread has failed or process termination has been requested.
    void checkProcessStatus() {
        @Nullable Throwable failure = threadFailure;
        if (failure != null) {
            throw guestThreadFailure(failure);
        }
        if (processExitRequested) {
            throw new ProgramExitException(processExitCode);
        }
    }

    /// Requests process-wide termination and wakes all guest threads blocked in host waits.
    void requestProcessExit(long exitCode) {
        synchronized (threadLock) {
            if (!processExitRequested) {
                processExitRequested = true;
                processExitCode = exitCode;
            }
            threadLock.notifyAll();
        }
    }

    /// Records that a guest thread exited and returns the process exit code once it is known.
    long completeThreadExit(MachineState state, long exitCode) {
        recordThreadExit(state, exitCode);
        return waitForProcessExit();
    }

    /// Records a guest thread exit and performs Linux clear-child-TID wakeup side effects.
    void recordThreadExit(MachineState state, long exitCode) {
        synchronized (threadLock) {
            clearChildTidAndWake(state);
            if (liveThreadCount > 0) {
                liveThreadCount--;
            }
            guestThreads.remove(Thread.currentThread());
            if (!processExitRequested && liveThreadCount == 0) {
                processExitRequested = true;
                processExitCode = exitCode;
            }
            threadLock.notifyAll();
        }
    }

    /// Records a host-side failure from a guest thread and wakes the process owner.
    void recordThreadFailure(Throwable throwable) {
        synchronized (threadLock) {
            if (threadFailure == null) {
                threadFailure = throwable;
            }
            processExitRequested = true;
            processExitCode = 1;
            threadLock.notifyAll();
        }
    }

    /// Joins all guest host threads before the shared guest memory is closed.
    void joinGuestThreads() {
        while (true) {
            Thread[] threads;
            synchronized (threadLock) {
                threads = guestThreads.toArray(Thread[]::new);
                if (threads.length == 0) {
                    if (threadFailure != null) {
                        throw guestThreadFailure(threadFailure);
                    }
                    return;
                }
            }

            Thread currentThread = Thread.currentThread();
            for (Thread thread : threads) {
                if (thread == currentThread) {
                    continue;
                }
                try {
                    thread.join();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RiscVException("Interrupted while waiting for guest threads to exit", exception);
                }
            }

            synchronized (threadLock) {
                for (int index = 0; index < guestThreads.size(); ) {
                    if (guestThreads.get(index).isAlive()) {
                        index++;
                    } else {
                        guestThreads.remove(index);
                    }
                }
            }
        }
    }

    /// Waits until the process exit code is known.
    private long waitForProcessExit() {
        synchronized (threadLock) {
            while (!processExitRequested && threadFailure == null) {
                try {
                    threadLock.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new RiscVException("Interrupted while waiting for guest process exit", exception);
                }
            }
            if (threadFailure != null) {
                throw guestThreadFailure(threadFailure);
            }
            return processExitCode;
        }
    }

    /// Clears a thread's child-TID word and wakes one matching futex waiter.
    private void clearChildTidAndWake(MachineState state) {
        long clearAddress = state.clearChildTidAddress();
        if (clearAddress == 0 || !memory.isBacked(clearAddress, Integer.BYTES)) {
            return;
        }

        memory.writeInt(clearAddress, 0);
        futexWakeLocked(clearAddress, 1, FUTEX_BITSET_MATCH_ANY);
    }

    /// Creates an exception that reports a failed guest worker thread.
    private static RiscVException guestThreadFailure(Throwable failure) {
        if (failure instanceof RiscVException exception) {
            return exception;
        }
        return new RiscVException("Guest thread failed", failure);
    }

    /// Stores the guest clear-child-TID pointer and returns this guest thread id.
    private static long setTidAddress(MachineState state, long address) {
        state.setClearChildTidAddress(address);
        return state.threadId();
    }

    /// Exits the current guest thread.
    private void exitThread(MachineState state, long exitCode) {
        synchronized (threadLock) {
            if (liveThreadCount <= 1) {
                if (!processExitRequested) {
                    processExitRequested = true;
                    processExitCode = exitCode;
                }
                threadLock.notifyAll();
                throw new ProgramExitException(exitCode);
            }
        }
        throw new ThreadExitException(exitCode);
    }

    /// Handles Linux futex wait and wake operations.
    private long futex(
            long address,
            long operation,
            long expectedValue,
            long timeoutAddress,
            long secondAddress,
            long thirdValue) {
        if ((address & (Integer.BYTES - 1L)) != 0) {
            return EINVAL;
        }

        long command = operation & FUTEX_COMMAND_MASK;
        long flags = operation & ~FUTEX_COMMAND_MASK;
        if ((flags & ~SUPPORTED_FUTEX_FLAGS) != 0) {
            return EINVAL;
        }
        if ((flags & FUTEX_CLOCK_REALTIME) != 0 && command != FUTEX_WAIT && command != FUTEX_WAIT_BITSET) {
            return EINVAL;
        }

        return switch ((int) command) {
            case (int) FUTEX_WAIT -> futexWait(
                    address,
                    expectedValue,
                    timeoutAddress,
                    FUTEX_BITSET_MATCH_ANY,
                    (flags & FUTEX_CLOCK_REALTIME) != 0);
            case (int) FUTEX_WAKE -> futexWake(address, expectedValue, FUTEX_BITSET_MATCH_ANY);
            case (int) FUTEX_WAIT_BITSET -> futexWait(
                    address,
                    expectedValue,
                    timeoutAddress,
                    thirdValue,
                    (flags & FUTEX_CLOCK_REALTIME) != 0);
            case (int) FUTEX_WAKE_BITSET -> futexWake(address, expectedValue, thirdValue);
            default -> ENOSYS;
        };
    }

    /// Handles a futex wait, blocking only when guest threading is active.
    private long futexWait(long address, long expectedValue, long timeoutAddress, long bitset, boolean realtimeTimeout) {
        if ((bitset & 0xffff_ffffL) == 0) {
            return EINVAL;
        }

        long timeoutNanos = -1;
        if (timeoutAddress != 0) {
            long seconds = memory.readLong(timeoutAddress + TIMESPEC_SECONDS_OFFSET);
            long nanoseconds = memory.readLong(timeoutAddress + TIMESPEC_NANOSECONDS_OFFSET);
            if (!isValidTimespec(seconds, nanoseconds)) {
                return EINVAL;
            }
            timeoutNanos = futexTimeoutNanos(seconds, nanoseconds, realtimeTimeout);
        }

        synchronized (threadLock) {
            int currentValue = memory.readInt(address);
            if (currentValue != (int) expectedValue) {
                return EAGAIN;
            }
            if (!guestThreadingEnabled()) {
                return ETIMEDOUT;
            }
            if (timeoutNanos == 0) {
                return ETIMEDOUT;
            }

            FutexWaiter waiter = new FutexWaiter(address, bitset);
            futexWaiters.add(waiter);
            try {
                long remainingNanos = timeoutNanos;
                long lastNanos = timeoutNanos >= 0 ? System.nanoTime() : 0;
                while (!waiter.woken && !processExitRequested && threadFailure == null) {
                    if (remainingNanos >= 0) {
                        if (remainingNanos <= 0) {
                            return ETIMEDOUT;
                        }
                        waitNanos(remainingNanos);
                        long now = System.nanoTime();
                        long elapsed = Math.max(0, now - lastNanos);
                        remainingNanos = elapsed >= remainingNanos ? 0 : remainingNanos - elapsed;
                        lastNanos = now;
                    } else {
                        threadLock.wait();
                    }
                }
                if (threadFailure != null || processExitRequested) {
                    return EINTR;
                }
                return 0;
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return EINTR;
            } finally {
                futexWaiters.remove(waiter);
            }
        }
    }

    /// Handles a futex wake against guest waiters.
    private long futexWake(long address, long count, long bitset) {
        if (count < 0 || (bitset & 0xffff_ffffL) == 0) {
            return EINVAL;
        }

        synchronized (threadLock) {
            long woken = futexWakeLocked(address, count, bitset);
            if (woken > 0) {
                threadLock.notifyAll();
            }
            return woken;
        }
    }

    /// Wakes matching futex waiters while `threadLock` is held.
    private long futexWakeLocked(long address, long count, long bitset) {
        long woken = 0;
        for (FutexWaiter waiter : futexWaiters) {
            if (woken >= count) {
                break;
            }
            if (waiter.address == address && (waiter.bitset & bitset) != 0) {
                waiter.woken = true;
                woken++;
            }
        }
        if (woken > 0) {
            threadLock.notifyAll();
        }
        return woken;
    }

    /// Waits on `threadLock` for at most the supplied nanosecond count.
    private void waitNanos(long nanoseconds) throws InterruptedException {
        long millis = nanoseconds / 1_000_000L;
        int nanos = (int) (nanoseconds % 1_000_000L);
        threadLock.wait(millis, nanos);
    }

    /// Converts a futex timeout to a relative nanosecond count.
    private long futexTimeoutNanos(long seconds, long nanoseconds, boolean realtimeTimeout) {
        long targetNanos = timespecToSaturatedNanoseconds(seconds, nanoseconds);
        if (!realtimeTimeout) {
            return targetNanos;
        }

        long nowNanos = instantToSaturatedNanoseconds(clock.instant());
        return targetNanos <= nowNanos ? 0 : targetNanos - nowNanos;
    }

    /// Converts an instant to saturated nanoseconds since the Unix epoch.
    private static long instantToSaturatedNanoseconds(Instant instant) {
        long seconds = instant.getEpochSecond();
        int nanoseconds = instant.getNano();
        if (seconds <= 0) {
            return 0;
        }
        return timespecToSaturatedNanoseconds(seconds, nanoseconds);
    }

    /// Returns true when clone-created guest threads can be started through the Truffle environment.
    private boolean guestThreadingEnabled() {
        return env != null && guestThreadRunner != null;
    }

    /// Handles the parent return path for Linux thread-style `clone` requests.
    private long clone(
            MachineState state,
            long pc,
            long flags,
            long stackAddress,
            long parentTidAddress,
            long tlsAddress,
            long childTidAddress) {
        if (stackAddress == 0
                || (flags & REQUIRED_THREAD_CLONE_FLAGS) != REQUIRED_THREAD_CLONE_FLAGS
                || (flags & ~SUPPORTED_THREAD_CLONE_FLAGS) != 0) {
            return EINVAL;
        }
        if (!guestThreadingEnabled()) {
            return EAGAIN;
        }

        long threadId;
        synchronized (threadLock) {
            if (processExitRequested || threadFailure != null) {
                return EAGAIN;
            }
            threadId = nextGuestThreadId;
            if (threadId <= GUEST_PROCESS_ID || threadId > Integer.MAX_VALUE) {
                return ENOMEM;
            }
            nextGuestThreadId++;
        }

        MachineState child = state.forkForClone(
                (int) threadId,
                pc + Integer.BYTES,
                stackAddress,
                tlsAddress,
                (flags & CLONE_SETTLS) != 0);
        if ((flags & CLONE_CHILD_CLEARTID) != 0) {
            child.setClearChildTidAddress(childTidAddress);
        }

        TruffleLanguage.Env currentEnv = env;
        GuestThreadRunner currentRunner = guestThreadRunner;
        Thread thread;
        try {
            thread = currentEnv.newTruffleThreadBuilder(() -> currentRunner.runGuestThread(child)).build();
        } catch (RuntimeException exception) {
            return EAGAIN;
        }
        thread.setUncaughtExceptionHandler((failedThread, throwable) -> recordThreadFailure(throwable));

        if ((flags & CLONE_PARENT_SETTID) != 0) {
            memory.writeInt(parentTidAddress, (int) threadId);
        }
        if ((flags & CLONE_CHILD_SETTID) != 0) {
            memory.writeInt(childTidAddress, (int) threadId);
        }

        synchronized (threadLock) {
            if (processExitRequested || threadFailure != null) {
                return EAGAIN;
            }
            liveThreadCount++;
            guestThreads.add(thread);
        }

        try {
            thread.start();
        } catch (RuntimeException exception) {
            unregisterUnstartedGuestThread(thread);
            return EAGAIN;
        }
        return threadId;
    }

    /// Removes a child thread that failed before it could start running guest code.
    private void unregisterUnstartedGuestThread(Thread thread) {
        synchronized (threadLock) {
            guestThreads.remove(thread);
            if (liveThreadCount > 0) {
                liveThreadCount--;
            }
            threadLock.notifyAll();
        }
    }

    /// Accepts a single-threaded robust futex list registration.
    private long setRobustList(long headAddress, long length) {
        if (length < 0) {
            return EINVAL;
        }
        robustListHeadAddress = headAddress;
        robustListLength = length;
        return 0;
    }

    /// Reports the robust futex list registered for this single-process guest.
    private long getRobustList(long processId, long headAddress, long lengthAddress) {
        if (processId != 0 && !isKnownGuestThreadId(processId)) {
            return ESRCH;
        }
        memory.writeLong(headAddress, robustListHeadAddress);
        memory.writeLong(lengthAddress, robustListLength);
        return 0;
    }

    /// Registers or reports the single-threaded alternate signal stack.
    private long sigaltstack(long stackAddress, long oldStackAddress) {
        long newStackPointer = 0;
        long newStackSize = 0;
        long newStackFlags = 0;
        if (stackAddress != 0) {
            newStackPointer = memory.readLong(stackAddress + SIGNAL_STACK_POINTER_OFFSET);
            newStackFlags = Integer.toUnsignedLong(memory.readInt(stackAddress + SIGNAL_STACK_FLAGS_OFFSET));
            newStackSize = memory.readLong(stackAddress + SIGNAL_STACK_SIZE_OFFSET);
            if ((newStackFlags & ~SUPPORTED_SIGNAL_STACK_FLAGS) != 0
                    || (newStackFlags & SS_ONSTACK) != 0
                    || newStackSize < 0) {
                return EINVAL;
            }
            if ((newStackFlags & SS_DISABLE) == 0 && newStackSize < MINIMUM_SIGNAL_STACK_SIZE) {
                return ENOMEM;
            }
        }

        if (oldStackAddress != 0) {
            writeSignalStack(oldStackAddress);
        }
        if (stackAddress != 0) {
            if ((newStackFlags & SS_DISABLE) != 0) {
                alternateSignalStackPointer = 0;
                alternateSignalStackSize = 0;
                alternateSignalStackFlags = SS_DISABLE;
            } else {
                alternateSignalStackPointer = newStackPointer;
                alternateSignalStackSize = newStackSize;
                alternateSignalStackFlags = newStackFlags;
            }
        }
        return 0;
    }

    /// Writes the current Linux RISC-V 64-bit `stack_t` alternate signal stack.
    private void writeSignalStack(long stackAddress) {
        memory.writeLong(stackAddress + SIGNAL_STACK_POINTER_OFFSET, alternateSignalStackPointer);
        memory.writeInt(stackAddress + SIGNAL_STACK_FLAGS_OFFSET, (int) alternateSignalStackFlags);
        memory.writeInt(stackAddress + SIGNAL_STACK_FLAGS_PADDING_OFFSET, 0);
        memory.writeLong(stackAddress + SIGNAL_STACK_SIZE_OFFSET, alternateSignalStackSize);
    }

    /// Sleeps for the requested Linux RISC-V 64-bit `struct timespec` duration.
    private long nanosleep(long requestAddress, long remainingAddress) {
        long seconds = memory.readLong(requestAddress + TIMESPEC_SECONDS_OFFSET);
        long nanoseconds = memory.readLong(requestAddress + TIMESPEC_NANOSECONDS_OFFSET);
        if (!isValidTimespec(seconds, nanoseconds)) {
            return EINVAL;
        }

        long totalNanoseconds = timespecToSaturatedNanoseconds(seconds, nanoseconds);
        return sleepNanoseconds(totalNanoseconds, remainingAddress);
    }

    /// Sleeps for a Linux `clock_nanosleep` request against a supported guest clock.
    private long clockNanosleep(long clockId, long flags, long requestAddress, long remainingAddress) {
        if (!isSupportedClock(clockId) || (flags & ~SUPPORTED_CLOCK_NANOSLEEP_FLAGS) != 0) {
            return EINVAL;
        }
        if (clockId == CLOCK_THREAD_CPUTIME_ID) {
            return EINVAL;
        }
        if (clockId == CLOCK_PROCESS_CPUTIME_ID) {
            return ENOTSUP;
        }

        long seconds = memory.readLong(requestAddress + TIMESPEC_SECONDS_OFFSET);
        long nanoseconds = memory.readLong(requestAddress + TIMESPEC_NANOSECONDS_OFFSET);
        if (!isValidTimespec(seconds, nanoseconds)) {
            return EINVAL;
        }

        boolean absolute = (flags & TIMER_ABSTIME) != 0;
        long totalNanoseconds = absolute
                ? absoluteClockSleepNanoseconds(clockId, seconds, nanoseconds)
                : timespecToSaturatedNanoseconds(seconds, nanoseconds);
        return sleepNanoseconds(totalNanoseconds, absolute ? 0 : remainingAddress);
    }

    /// Sleeps for a relative duration and writes the remaining duration when interrupted.
    private long sleepNanoseconds(long totalNanoseconds, long remainingAddress) {
        if (totalNanoseconds == 0) {
            return 0;
        }

        long startNanoseconds = System.nanoTime();
        try {
            Thread.sleep(
                    totalNanoseconds / NANOSECONDS_PER_MILLISECOND,
                    (int) (totalNanoseconds % NANOSECONDS_PER_MILLISECOND));
            return 0;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (remainingAddress != 0) {
                long elapsedNanoseconds = Math.max(0, System.nanoTime() - startNanoseconds);
                long remainingNanoseconds = Math.max(0, totalNanoseconds - elapsedNanoseconds);
                writeTimespecFromNanoseconds(remainingAddress, remainingNanoseconds);
            }
            return EINTR;
        }
    }

    /// Returns the relative wait needed to reach an absolute clock time.
    private long absoluteClockSleepNanoseconds(long clockId, long targetSeconds, long targetNanoseconds) {
        if (isRealtimeClock(clockId)) {
            Instant instant = clock.instant();
            return relativeNanosecondsUntil(targetSeconds, targetNanoseconds, instant.getEpochSecond(), instant.getNano());
        }

        Duration elapsed = elapsedDuration();
        return relativeNanosecondsUntil(targetSeconds, targetNanoseconds, elapsed.getSeconds(), elapsed.getNano());
    }

    /// Returns the saturated non-negative nanosecond distance from one timespec value to another.
    private static long relativeNanosecondsUntil(
            long targetSeconds,
            long targetNanoseconds,
            long currentSeconds,
            int currentNanoseconds) {
        if (targetSeconds < currentSeconds
                || (targetSeconds == currentSeconds && targetNanoseconds <= currentNanoseconds)) {
            return 0;
        }

        long seconds = targetSeconds - currentSeconds;
        long nanoseconds = targetNanoseconds - currentNanoseconds;
        if (nanoseconds < 0) {
            seconds--;
            nanoseconds += NANOSECONDS_PER_SECOND;
        }
        return timespecToSaturatedNanoseconds(seconds, nanoseconds);
    }

    /// Reports a deterministic single-CPU affinity mask for static libc queries.
    private long schedGetaffinity(long processId, long cpuSetSize, long cpuSetAddress) {
        if (cpuSetSize < MINIMUM_CPU_AFFINITY_MASK_SIZE) {
            return EINVAL;
        }

        memory.clear(cpuSetAddress, cpuSetSize);
        memory.writeLong(cpuSetAddress, 1);
        return MINIMUM_CPU_AFFINITY_MASK_SIZE;
    }

    /// Yields the host thread for the Linux `sched_yield` syscall.
    private static long schedYield() {
        Thread.yield();
        return 0;
    }

    /// Reports deterministic RISC-V hardware probe values for the single simulated CPU.
    private long riscvHwprobe(long pairsAddress, long pairCount, long cpuSetSize, long cpuSetAddress, long flags) {
        if (pairCount < 0
                || pairCount > Integer.MAX_VALUE
                || (flags & ~RISCV_HWPROBE_WHICH_CPUS) != 0
                || pairCount > Long.MAX_VALUE / RISCV_HWPROBE_PAIR_SIZE
                || (cpuSetAddress == 0 && cpuSetSize != 0)
                || (cpuSetAddress != 0 && cpuSetSize <= 0)) {
            return EINVAL;
        }

        if ((flags & RISCV_HWPROBE_WHICH_CPUS) == 0) {
            if (cpuSetAddress != 0 && !cpuSetContainsGuestCpu(cpuSetAddress)) {
                return EINVAL;
            }
            populateHwprobePairs(pairsAddress, pairCount);
            return 0;
        }

        if (cpuSetAddress == 0) {
            return EINVAL;
        }

        boolean selected = cpuSetContainsGuestCpu(cpuSetAddress) || cpuSetIsEmpty(cpuSetAddress, cpuSetSize);
        boolean matches = selected && hwprobePairsMatch(pairsAddress, pairCount);
        memory.clear(cpuSetAddress, cpuSetSize);
        if (matches) {
            memory.writeByte(cpuSetAddress, (byte) 1);
        }
        return 0;
    }

    /// Writes values for all requested RISC-V hardware probe pairs.
    private void populateHwprobePairs(long pairsAddress, long pairCount) {
        for (long index = 0; index < pairCount; index++) {
            long pairAddress = pairsAddress + index * RISCV_HWPROBE_PAIR_SIZE;
            long key = memory.readLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET);
            if (isSupportedHwprobeKey(key)) {
                memory.writeLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET, hwprobeValue(key));
            } else {
                memory.writeLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET, -1);
                memory.writeLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET, 0);
            }
        }
    }

    /// Returns true when all supplied RISC-V hardware probe constraints match the simulated CPU.
    private boolean hwprobePairsMatch(long pairsAddress, long pairCount) {
        boolean matches = true;
        for (long index = 0; index < pairCount; index++) {
            long pairAddress = pairsAddress + index * RISCV_HWPROBE_PAIR_SIZE;
            long key = memory.readLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET);
            long requestedValue = memory.readLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET);
            if (!isSupportedHwprobeKey(key)) {
                memory.writeLong(pairAddress + RISCV_HWPROBE_KEY_OFFSET, -1);
                memory.writeLong(pairAddress + RISCV_HWPROBE_VALUE_OFFSET, 0);
                matches = false;
                continue;
            }

            long actualValue = hwprobeValue(key);
            if (isHwprobeBitmaskKey(key)) {
                if ((actualValue & requestedValue) != requestedValue) {
                    matches = false;
                }
            } else if (actualValue != requestedValue) {
                matches = false;
            }
        }
        return matches;
    }

    /// Returns true when a hardware probe key is supported by this simulator.
    private static boolean isSupportedHwprobeKey(long key) {
        return key >= RISCV_HWPROBE_KEY_MVENDORID && key <= RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE;
    }

    /// Returns the deterministic value for a supported RISC-V hardware probe key.
    private static long hwprobeValue(long key) {
        return switch ((int) key) {
            case (int) RISCV_HWPROBE_KEY_MVENDORID,
                    (int) RISCV_HWPROBE_KEY_MARCHID,
                    (int) RISCV_HWPROBE_KEY_MIMPID,
                    (int) RISCV_HWPROBE_KEY_ZICBOZ_BLOCK_SIZE,
                    (int) RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0,
                    (int) RISCV_HWPROBE_KEY_ZICBOM_BLOCK_SIZE,
                    (int) RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0,
                    (int) RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0,
                    (int) RISCV_HWPROBE_KEY_ZICBOP_BLOCK_SIZE -> 0;
            case (int) RISCV_HWPROBE_KEY_BASE_BEHAVIOR -> RISCV_HWPROBE_BASE_BEHAVIOR_IMA;
            case (int) RISCV_HWPROBE_KEY_IMA_EXT_0 -> RISCV_HWPROBE_IMA_EXTENSIONS;
            case (int) RISCV_HWPROBE_KEY_CPUPERF_0,
                    (int) RISCV_HWPROBE_KEY_MISALIGNED_SCALAR_PERF,
                    (int) RISCV_HWPROBE_KEY_MISALIGNED_VECTOR_PERF -> RISCV_HWPROBE_MISALIGNED_UNSUPPORTED;
            case (int) RISCV_HWPROBE_KEY_HIGHEST_VIRT_ADDRESS -> Long.MAX_VALUE;
            case (int) RISCV_HWPROBE_KEY_TIME_CSR_FREQ -> NANOSECONDS_PER_SECOND;
            default -> throw new AssertionError("validated RISC-V hardware probe key");
        };
    }

    /// Returns true when a hardware probe key uses bitmask matching.
    private static boolean isHwprobeBitmaskKey(long key) {
        return key == RISCV_HWPROBE_KEY_BASE_BEHAVIOR
                || key == RISCV_HWPROBE_KEY_IMA_EXT_0
                || key == RISCV_HWPROBE_KEY_CPUPERF_0
                || key == RISCV_HWPROBE_KEY_VENDOR_EXT_THEAD_0
                || key == RISCV_HWPROBE_KEY_VENDOR_EXT_SIFIVE_0
                || key == RISCV_HWPROBE_KEY_VENDOR_EXT_MIPS_0;
    }

    /// Returns true when the supplied CPU set contains the simulator's only CPU.
    private boolean cpuSetContainsGuestCpu(long cpuSetAddress) {
        return (memory.readUnsignedByte(cpuSetAddress) & 1) != 0;
    }

    /// Returns true when all bytes in a guest CPU set are zero.
    private boolean cpuSetIsEmpty(long cpuSetAddress, long cpuSetSize) {
        for (long index = 0; index < cpuSetSize; index++) {
            if (memory.readUnsignedByte(cpuSetAddress + index) != 0) {
                return false;
            }
        }
        return true;
    }

    /// Accepts signal sends that target the single guest process.
    private long kill(long processId, long signalNumber) {
        if (!isValidSignalNumber(signalNumber)) {
            return EINVAL;
        }
        if (processId == 0 || processId == -1 || processId == GUEST_PROCESS_ID || processId == -GUEST_PROCESS_GROUP_ID) {
            return 0;
        }
        return ESRCH;
    }

    /// Accepts signal sends that target known guest thread ids.
    private long tkill(long threadId, long signalNumber) {
        if (!isValidSignalNumber(signalNumber)) {
            return EINVAL;
        }
        return isKnownGuestThreadId(threadId) ? 0 : ESRCH;
    }

    /// Accepts signal sends that target known guest thread ids in the single process.
    private long tgkill(long processId, long threadId, long signalNumber) {
        if (!isValidSignalNumber(signalNumber)) {
            return EINVAL;
        }
        if (processId != GUEST_PROCESS_ID) {
            return ESRCH;
        }
        return isKnownGuestThreadId(threadId) ? 0 : ESRCH;
    }

    /// Returns the deterministic process group id for the guest process.
    private long getpgid(long processId) {
        if (processId == 0 || processId == GUEST_PROCESS_ID) {
            return GUEST_PROCESS_GROUP_ID;
        }
        return ESRCH;
    }

    /// Accepts session creation as a deterministic no-op for the single guest process.
    private static long setsid() {
        return GUEST_PROCESS_GROUP_ID;
    }

    /// Returns true when a signal number is zero or a regular Linux signal.
    private static boolean isValidSignalNumber(long signalNumber) {
        return signalNumber == 0 || (signalNumber >= MIN_SIGNAL_NUMBER && signalNumber <= MAX_SIGNAL_NUMBER);
    }

    /// Returns true when a thread id belongs to the current process or a synthetic clone result.
    private boolean isKnownGuestThreadId(long threadId) {
        return threadId == GUEST_PROCESS_ID || (threadId > GUEST_PROCESS_ID && threadId < nextGuestThreadId);
    }

    /// Writes deterministic process CPU times and returns elapsed clock ticks.
    private long times(long tmsAddress) {
        long elapsedTicks = elapsedClockTicks();
        if (tmsAddress != 0) {
            memory.clear(tmsAddress, TMS_SIZE);
            memory.writeLong(tmsAddress + TMS_USER_TIME_OFFSET, elapsedTicks);
            memory.writeLong(tmsAddress + TMS_SYSTEM_TIME_OFFSET, 0);
            memory.writeLong(tmsAddress + TMS_CHILD_USER_TIME_OFFSET, 0);
            memory.writeLong(tmsAddress + TMS_CHILD_SYSTEM_TIME_OFFSET, 0);
        }
        return elapsedTicks;
    }

    /// Writes deterministic Linux `struct rusage` values for the supported `who` selectors.
    private long getrusage(long who, long rusageAddress) {
        if (who != RUSAGE_SELF && who != RUSAGE_CHILDREN && who != RUSAGE_THREAD) {
            return EINVAL;
        }

        memory.clear(rusageAddress, RUSAGE_SIZE);
        if (who != RUSAGE_CHILDREN) {
            writeTimevalFromDuration(rusageAddress + RUSAGE_USER_TIME_OFFSET, elapsedDuration());
            writeTimevalFromDuration(rusageAddress + RUSAGE_SYSTEM_TIME_OFFSET, Duration.ZERO);
        }
        return 0;
    }

    /// Reports the simulated single CPU and NUMA node.
    private long getcpu(long cpuAddress, long nodeAddress) {
        if (cpuAddress != 0) {
            memory.writeInt(cpuAddress, 0);
        }
        if (nodeAddress != 0) {
            memory.writeInt(nodeAddress, 0);
        }
        return 0;
    }

    /// Writes a deterministic Linux `struct utsname` for the simulated guest.
    private long uname(long utsnameAddress) {
        memory.clear(utsnameAddress, UTSNAME_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_SYSNAME_OFFSET, "Linux", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_NODENAME_OFFSET, "localhost", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_RELEASE_OFFSET, "6.12.0", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_VERSION_OFFSET, "#1 SMP", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_MACHINE_OFFSET, "riscv64", UTSNAME_FIELD_SIZE);
        writeNullTerminatedAscii(utsnameAddress + UTSNAME_DOMAINNAME_OFFSET, "localdomain", UTSNAME_FIELD_SIZE);
        return 0;
    }

    /// Writes a null-terminated US-ASCII string into a fixed-size guest field.
    private void writeNullTerminatedAscii(long address, String value, int fieldSize) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        int length = Math.min(bytes.length, fieldSize - 1);
        memory.writeBytes(address, bytes, 0, length);
    }

    /// Writes Linux `struct timeval` and optional `struct timezone` values.
    private long gettimeofday(long timevalAddress, long timezoneAddress) {
        if (timevalAddress != 0) {
            Instant instant = clock.instant();
            memory.writeLong(timevalAddress + TIMEVAL_SECONDS_OFFSET, instant.getEpochSecond());
            memory.writeLong(timevalAddress + TIMEVAL_MICROSECONDS_OFFSET, instant.getNano() / 1000L);
        }
        if (timezoneAddress != 0) {
            memory.writeInt(timezoneAddress + TIMEZONE_MINUTESWEST_OFFSET, 0);
            memory.writeInt(timezoneAddress + TIMEZONE_DSTTIME_OFFSET, 0);
        }
        return 0;
    }

    /// Returns the non-negative elapsed duration since the syscall handler was created.
    private Duration elapsedDuration() {
        Duration duration = Duration.between(clockStartInstant, clock.instant());
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    /// Returns elapsed Linux clock ticks since the syscall handler was created.
    private long elapsedClockTicks() {
        return durationToClockTicks(elapsedDuration());
    }

    /// Writes a Linux RISC-V 64-bit `struct timespec` for common clock ids.
    private long clockGettime(long clockId, long timespecAddress) {
        if (!isSupportedClock(clockId)) {
            return EINVAL;
        }

        if (isRealtimeClock(clockId)) {
            writeTimespecFromInstant(timespecAddress, clock.instant());
        } else {
            writeTimespecFromDuration(timespecAddress, elapsedDuration());
        }
        return 0;
    }

    /// Writes the resolution for a supported Linux clock.
    private long clockGetres(long clockId, long timespecAddress) {
        if (!isSupportedClock(clockId)) {
            return EINVAL;
        }
        if (timespecAddress != 0) {
            memory.writeLong(timespecAddress + TIMESPEC_SECONDS_OFFSET, 0);
            memory.writeLong(timespecAddress + TIMESPEC_NANOSECONDS_OFFSET, 1);
        }
        return 0;
    }

    /// Writes a Linux RISC-V 64-bit `struct timeval` from a non-negative elapsed duration.
    private void writeTimevalFromDuration(long timevalAddress, Duration duration) {
        memory.writeLong(timevalAddress + TIMEVAL_SECONDS_OFFSET, duration.getSeconds());
        memory.writeLong(timevalAddress + TIMEVAL_MICROSECONDS_OFFSET, duration.getNano() / 1000L);
    }

    /// Writes a Linux RISC-V 64-bit `struct timespec` from a clock instant.
    private void writeTimespecFromInstant(long timespecAddress, Instant instant) {
        memory.writeLong(timespecAddress + TIMESPEC_SECONDS_OFFSET, instant.getEpochSecond());
        memory.writeLong(timespecAddress + TIMESPEC_NANOSECONDS_OFFSET, instant.getNano());
    }

    /// Writes a Linux RISC-V 64-bit `struct timespec` from a non-negative elapsed duration.
    private void writeTimespecFromDuration(long timespecAddress, Duration duration) {
        if (duration.isNegative()) {
            memory.writeLong(timespecAddress + TIMESPEC_SECONDS_OFFSET, 0);
            memory.writeLong(timespecAddress + TIMESPEC_NANOSECONDS_OFFSET, 0);
            return;
        }

        memory.writeLong(timespecAddress + TIMESPEC_SECONDS_OFFSET, duration.getSeconds());
        memory.writeLong(timespecAddress + TIMESPEC_NANOSECONDS_OFFSET, duration.getNano());
    }

    /// Writes a Linux RISC-V 64-bit `struct timespec` from a non-negative nanosecond count.
    private void writeTimespecFromNanoseconds(long timespecAddress, long nanoseconds) {
        memory.writeLong(timespecAddress + TIMESPEC_SECONDS_OFFSET, nanoseconds / NANOSECONDS_PER_SECOND);
        memory.writeLong(timespecAddress + TIMESPEC_NANOSECONDS_OFFSET, nanoseconds % NANOSECONDS_PER_SECOND);
    }

    /// Returns true when the supplied timespec fields represent a valid non-negative duration.
    private static boolean isValidTimespec(long seconds, long nanoseconds) {
        return seconds >= 0 && nanoseconds >= 0 && nanoseconds < NANOSECONDS_PER_SECOND;
    }

    /// Converts a valid timespec to nanoseconds, saturating very large values.
    private static long timespecToSaturatedNanoseconds(long seconds, long nanoseconds) {
        if (seconds > (Long.MAX_VALUE - nanoseconds) / NANOSECONDS_PER_SECOND) {
            return Long.MAX_VALUE;
        }
        return seconds * NANOSECONDS_PER_SECOND + nanoseconds;
    }

    /// Converts a non-negative duration to Linux clock ticks, saturating very large values.
    private static long durationToClockTicks(Duration duration) {
        long seconds = duration.getSeconds();
        long nanoseconds = duration.getNano();
        if (seconds > (Long.MAX_VALUE - CLOCK_TICKS_PER_SECOND) / CLOCK_TICKS_PER_SECOND) {
            return Long.MAX_VALUE;
        }
        long secondTicks = seconds * CLOCK_TICKS_PER_SECOND;
        long fractionalTicks = nanoseconds * CLOCK_TICKS_PER_SECOND / NANOSECONDS_PER_SECOND;
        return secondTicks > Long.MAX_VALUE - fractionalTicks ? Long.MAX_VALUE : secondTicks + fractionalTicks;
    }

    /// Returns true when `clock_gettime` accepts the supplied Linux clock id.
    private static boolean isSupportedClock(long clockId) {
        return clockId == CLOCK_REALTIME
                || clockId == CLOCK_MONOTONIC
                || clockId == CLOCK_PROCESS_CPUTIME_ID
                || clockId == CLOCK_THREAD_CPUTIME_ID
                || clockId == CLOCK_MONOTONIC_RAW
                || clockId == CLOCK_REALTIME_COARSE
                || clockId == CLOCK_MONOTONIC_COARSE
                || clockId == CLOCK_BOOTTIME;
    }

    /// Returns true when the Linux clock id represents a wall-clock source.
    private static boolean isRealtimeClock(long clockId) {
        return clockId == CLOCK_REALTIME || clockId == CLOCK_REALTIME_COARSE;
    }

    /// Accepts signal action setup for a guest that never delivers host signals.
    private long rtSigaction(long signalNumber, long actionAddress, long oldActionAddress, long sigsetSize) {
        if (sigsetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }
        if (signalNumber < MIN_SIGNAL_NUMBER || signalNumber > MAX_SIGNAL_NUMBER) {
            return EINVAL;
        }

        if (oldActionAddress != 0) {
            memory.clear(oldActionAddress, KERNEL_SIGACTION_SIZE);
        }
        return 0;
    }

    /// Accepts signal-mask queries and no-op updates for a single-threaded guest.
    private long rtSigprocmask(long how, long setAddress, long oldSetAddress, long sigsetSize) {
        if (sigsetSize != KERNEL_SIGSET_SIZE) {
            return EINVAL;
        }
        if (setAddress != 0 && how != SIG_BLOCK && how != SIG_UNBLOCK && how != SIG_SETMASK) {
            return EINVAL;
        }

        if (oldSetAddress != 0) {
            memory.writeLong(oldSetAddress, 0);
        }
        return 0;
    }

    /// Handles the Linux `prctl` operations needed by single-process user-mode guests.
    private long prctl(
            MachineState state,
            long option,
            long argument2,
            long argument3,
            long argument4,
            long argument5) {
        if (option == PR_SET_PDEATHSIG) {
            return setParentDeathSignal(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_PDEATHSIG) {
            return getParentDeathSignal(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_DUMPABLE) {
            return noArguments(argument2, argument3, argument4, argument5) ? dumpable : EINVAL;
        }
        if (option == PR_SET_DUMPABLE) {
            return setDumpable(argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_NAME) {
            return setProcessName(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_NAME) {
            return getProcessName(argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_TIMERSLACK) {
            return setTimerSlack(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_TIMERSLACK) {
            return noArguments(argument2, argument3, argument4, argument5) ? timerSlackNanoseconds : EINVAL;
        }
        if (option == PR_SET_CHILD_SUBREAPER) {
            return setChildSubreaper(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_CHILD_SUBREAPER) {
            return getChildSubreaper(argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_NO_NEW_PRIVS) {
            return setNoNewPrivileges(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_NO_NEW_PRIVS) {
            return noArguments(argument2, argument3, argument4, argument5) ? (noNewPrivileges ? 1 : 0) : EINVAL;
        }
        if (option == PR_GET_TID_ADDRESS) {
            return getTidAddress(state, argument2, argument3, argument4, argument5);
        }
        if (option == PR_SET_THP_DISABLE) {
            return setTransparentHugePagesDisabled(argument2, argument3, argument4, argument5);
        }
        if (option == PR_GET_THP_DISABLE) {
            return noArguments(argument2, argument3, argument4, argument5) ? transparentHugePagesDisabled : EINVAL;
        }
        if (option == PR_SET_VMA) {
            return setVirtualMemoryAreaName(argument2, argument3, argument4, argument5);
        }
        return EINVAL;
    }

    /// Stores the parent-death signal value used by `PR_GET_PDEATHSIG`.
    private long setParentDeathSignal(long signalNumber, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        if (signalNumber != 0 && (signalNumber < MIN_SIGNAL_NUMBER || signalNumber > MAX_SIGNAL_NUMBER)) {
            return EINVAL;
        }
        parentDeathSignal = (int) signalNumber;
        return 0;
    }

    /// Writes the configured parent-death signal to a guest `int` pointer.
    private long getParentDeathSignal(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeInt(address, parentDeathSignal);
        return 0;
    }

    /// Stores the dumpable state exposed by `PR_GET_DUMPABLE`.
    private long setDumpable(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || (value != 0 && value != 1)) {
            return EINVAL;
        }
        dumpable = (int) value;
        return 0;
    }

    /// Copies a Linux task command name from guest memory.
    private long setProcessName(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }

        for (int index = 0; index < processName.length; index++) {
            processName[index] = 0;
        }
        for (int index = 0; index < TASK_COMMAND_LENGTH - 1; index++) {
            int value = memory.readUnsignedByte(address + index);
            if (value == 0) {
                return 0;
            }
            processName[index] = (byte) value;
        }
        return 0;
    }

    /// Copies the Linux task command name to guest memory.
    private long getProcessName(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeBytes(address, processName, 0, processName.length);
        return 0;
    }

    /// Stores the timer slack value exposed by `PR_GET_TIMERSLACK`.
    private long setTimerSlack(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || value < 0) {
            return EINVAL;
        }
        timerSlackNanoseconds = value == 0 ? DEFAULT_TIMER_SLACK_NANOSECONDS : value;
        return 0;
    }

    /// Stores the child-subreaper flag exposed by `PR_GET_CHILD_SUBREAPER`.
    private long setChildSubreaper(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || (value != 0 && value != 1)) {
            return EINVAL;
        }
        childSubreaper = value == 1;
        return 0;
    }

    /// Writes the child-subreaper flag to a guest `int` pointer.
    private long getChildSubreaper(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeInt(address, childSubreaper ? 1 : 0);
        return 0;
    }

    /// Applies the monotonic `PR_SET_NO_NEW_PRIVS` flag.
    private long setNoNewPrivileges(long value, long argument3, long argument4, long argument5) {
        if (value != 1 || !unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        noNewPrivileges = true;
        return 0;
    }

    /// Writes the clear-child-TID address to a guest `long` pointer.
    private long getTidAddress(MachineState state, long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeLong(address, state.clearChildTidAddress());
        return 0;
    }

    /// Stores the transparent huge page disable state exposed by `PR_GET_THP_DISABLE`.
    private long setTransparentHugePagesDisabled(long value, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5) || (value != 0 && value != 1)) {
            return EINVAL;
        }
        transparentHugePagesDisabled = (int) value;
        return 0;
    }

    /// Accepts Linux memory-area naming requests as a no-op in the flat guest memory model.
    private static long setVirtualMemoryAreaName(long suboption, long startAddress, long length, long nameAddress) {
        if (suboption != PR_SET_VMA_ANON_NAME || startAddress < 0 || length < 0 || nameAddress < 0) {
            return EINVAL;
        }
        return 0;
    }

    /// Returns true when all `prctl` arguments are unused zero values.
    private static boolean noArguments(long argument2, long argument3, long argument4, long argument5) {
        return argument2 == 0 && unusedArgumentsAreZero(argument3, argument4, argument5);
    }

    /// Returns true when all trailing `prctl` arguments are unused zero values.
    private static boolean unusedArgumentsAreZero(long argument3, long argument4, long argument5) {
        return argument3 == 0 && argument4 == 0 && argument5 == 0;
    }

    /// Implements the Linux `brk` syscall within the simulator memory window.
    private long brk(long requestedAddress) {
        if (requestedAddress == 0) {
            return programBreak;
        }
        if (requestedAddress >= initialProgramBreak
                && requestedAddress <= memory.endAddress()
                && !overlapsMemoryMappings(initialProgramBreak, requestedAddress - initialProgramBreak)) {
            programBreak = requestedAddress;
        }
        return programBreak;
    }

    /// Implements anonymous `mmap` allocations and reservations in the guest address space.
    private long mmap(long address, long length, long protection, long flags, long fileDescriptor, long offset) {
        if (length <= 0 || offset < 0 || !isPageAligned(offset)) {
            return EINVAL;
        }
        if ((protection & ~SUPPORTED_MMAP_PROTECTION_MASK) != 0) {
            return EINVAL;
        }

        long mappingType = flags & MAP_TYPE;
        if (mappingType != MAP_PRIVATE && mappingType != MAP_SHARED) {
            return EINVAL;
        }
        if ((flags & MAP_ANONYMOUS) == 0) {
            return fileDescriptor < 0 ? EBADF : ENODEV;
        }

        long alignedLength = alignUp(length, PAGE_SIZE);
        if (alignedLength <= 0) {
            return ENOMEM;
        }

        long mappingAddress;
        if (requiresFixedMapping(flags)) {
            if (!isPageAligned(address) || !isValidGuestRange(address, alignedLength)) {
                return ENOMEM;
            }
            if ((flags & MAP_FIXED_NOREPLACE) != 0 && overlapsMemoryMappings(address, alignedLength)) {
                return EEXIST;
            }
            removeMemoryMappings(address, alignedLength);
            mappingAddress = address;
        } else {
            mappingAddress = findMmapAddress(address, alignedLength);
            if (mappingAddress == 0) {
                return ENOMEM;
            }
        }

        if (isMemoryBackedProtection(protection)) {
            if (!ensureMemoryBacking(mappingAddress, alignedLength)) {
                return ENOMEM;
            }
            memory.clear(mappingAddress, alignedLength);
        }
        addMemoryMapping(mappingAddress, alignedLength, protection);
        return mappingAddress;
    }

    /// Implements `munmap` by releasing tracked anonymous mappings.
    private long munmap(long address, long length) {
        if (length <= 0 || !isPageAligned(address)) {
            return EINVAL;
        }

        long alignedLength = alignUp(length, PAGE_SIZE);
        if (alignedLength <= 0 || !isValidGuestRange(address, alignedLength)) {
            return EINVAL;
        }

        removeMemoryMappings(address, alignedLength);
        return 0;
    }

    /// Implements `mprotect` for tracked anonymous mappings and the initial memory window.
    private long mprotect(long address, long length, long protection) {
        if (!isPageAligned(address) || length < 0 || (protection & ~SUPPORTED_MMAP_PROTECTION_MASK) != 0) {
            return EINVAL;
        }
        if (length == 0) {
            return 0;
        }

        long alignedLength = alignUp(length, PAGE_SIZE);
        if (alignedLength <= 0 || !isValidGuestRange(address, alignedLength)) {
            return ENOMEM;
        }
        if (!isMappedRange(address, alignedLength)) {
            return ENOMEM;
        }
        if (!prepareMemoryProtection(address, alignedLength, protection)) {
            return ENOMEM;
        }

        updateMemoryMappingsProtection(address, alignedLength, protection);
        return 0;
    }

    /// Implements Linux `madvise` hints that static runtimes commonly emit.
    private long madvise(long address, long length, long advice) {
        if (!isPageAligned(address) || length < 0 || !isSupportedMemoryAdvice(advice)) {
            return EINVAL;
        }
        if (length == 0) {
            return 0;
        }

        long alignedLength = alignUp(length, PAGE_SIZE);
        if (alignedLength <= 0 || !isValidGuestRange(address, alignedLength)) {
            return ENOMEM;
        }
        if (!isMappedRange(address, alignedLength)) {
            return ENOMEM;
        }

        if (advice == MADV_DONTNEED || advice == MADV_FREE || advice == MADV_DONTNEED_LOCKED) {
            clearAdvisedMemory(address, alignedLength);
        }
        return 0;
    }

    /// Gets or lowers Linux resource limits for the single guest process.
    private long prlimit64(long processId, long resource, long newLimitAddress, long oldLimitAddress) {
        if (processId != 0 && processId != GUEST_PROCESS_ID) {
            return ESRCH;
        }
        if (resource < 0 || resource >= RESOURCE_LIMIT_COUNT) {
            return EINVAL;
        }

        int index = (int) resource;
        long oldCurrent = resourceLimitCurrent[index];
        long oldMaximum = resourceLimitMaximum[index];
        if (oldLimitAddress != 0) {
            writeResourceLimit(oldLimitAddress, oldCurrent, oldMaximum);
        }

        if (newLimitAddress != 0) {
            long newCurrent = memory.readLong(newLimitAddress + RLIMIT_CURRENT_OFFSET);
            long newMaximum = memory.readLong(newLimitAddress + RLIMIT_MAXIMUM_OFFSET);
            if (Long.compareUnsigned(newCurrent, newMaximum) > 0) {
                return EINVAL;
            }
            if (Long.compareUnsigned(newMaximum, oldMaximum) > 0) {
                return EPERM;
            }
            resourceLimitCurrent[index] = newCurrent;
            resourceLimitMaximum[index] = newMaximum;
        }
        return 0;
    }

    /// Writes a Linux RISC-V 64-bit `struct rlimit64`.
    private void writeResourceLimit(long address, long current, long maximum) {
        memory.writeLong(address + RLIMIT_CURRENT_OFFSET, current);
        memory.writeLong(address + RLIMIT_MAXIMUM_OFFSET, maximum);
    }

    /// Fills a guest buffer with deterministic bytes for the Linux `getrandom` syscall.
    private long getrandom(long address, long length, long flags) {
        if ((flags & ~GETRANDOM_SUPPORTED_FLAGS) != 0) {
            return EINVAL;
        }
        if (length < 0) {
            return EINVAL;
        }
        if (length > Integer.MAX_VALUE) {
            throw new RiscVException("Guest getrandom syscall buffer is too large: " + length);
        }
        if (length == 0) {
            return 0;
        }

        byte[] bytes = new byte[(int) length];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = nextRandomByte();
        }
        memory.writeBytes(address, bytes, 0, bytes.length);
        return bytes.length;
    }

    /// Reports no supported Linux `membarrier` commands for runtime capability probes.
    private static long membarrier(long command, long flags, long cpuId) {
        if (flags != 0 || cpuId != 0) {
            return EINVAL;
        }
        return command == MEMBARRIER_CMD_QUERY ? 0 : EINVAL;
    }

    /// Reports that Linux restartable sequences are unavailable to guest runtimes.
    private static long rseq() {
        return ENOSYS;
    }

    /// Reads a null-terminated UTF-8 path string from guest memory.
    private @Nullable String readGuestPath(long address) {
        byte[] bytes = new byte[PATH_MAX];
        for (int index = 0; index < bytes.length; index++) {
            int value = memory.readUnsignedByte(address + index);
            if (value == 0) {
                return new String(bytes, 0, index, StandardCharsets.UTF_8);
            }
            bytes[index] = (byte) value;
        }
        return null;
    }

    /// Reads a little-endian 64-bit value without requiring guest alignment.
    private long readLongUnaligned(long address) {
        long value = 0;
        for (int index = 0; index < Long.BYTES; index++) {
            value |= (long) memory.readUnsignedByte(address + index) << (index * Byte.SIZE);
        }
        return value;
    }

    /// Writes a little-endian 64-bit value without requiring guest alignment.
    private void writeLongUnaligned(long address, long value) {
        byte[] bytes = new byte[Long.BYTES];
        for (int index = 0; index < bytes.length; index++) {
            bytes[index] = (byte) (value >>> (index * Byte.SIZE));
        }
        memory.writeBytes(address, bytes, 0, bytes.length);
    }

    /// Returns true when a non-empty guest path can be resolved from the supplied directory descriptor.
    private boolean canResolvePathFrom(long directoryFileDescriptor, String guestPath) {
        if (directoryFileDescriptor == AT_FDCWD || isAbsoluteGuestPath(guestPath)) {
            return true;
        }
        if (directoryFileDescriptor < Integer.MIN_VALUE || directoryFileDescriptor > Integer.MAX_VALUE) {
            return false;
        }

        @Nullable OpenFile openFile = openFile((int) directoryFileDescriptor);
        return openFile != null && openFile.isDirectory();
    }

    /// Resolves a guest path below the configured host root or an open directory descriptor.
    private @Nullable TruffleFile resolveHostFile(long directoryFileDescriptor, String guestPath) {
        @Nullable TruffleFile root = currentHostRoot();
        if (root == null) {
            return null;
        }
        if (guestPath.indexOf('\\') >= 0 || guestPath.indexOf(':') >= 0) {
            return null;
        }

        boolean absoluteGuestPath = isAbsoluteGuestPath(guestPath);
        TruffleFile base = root;
        String relativePath = guestPath;
        if (absoluteGuestPath) {
            relativePath = removeLeadingSlashes(guestPath);
        } else if (directoryFileDescriptor != AT_FDCWD) {
            @Nullable OpenFile directory = openFile((int) directoryFileDescriptor);
            if (directory == null || !directory.isDirectory()) {
                return null;
            }
            @Nullable TruffleFile path = directory.path();
            if (path == null) {
                return null;
            }
            base = path;
        } else {
            @Nullable TruffleFile currentDirectory = currentHostWorkingDirectory(root);
            if (currentDirectory == null) {
                return null;
            }
            base = currentDirectory;
        }

        try {
            TruffleFile hostFile = base.resolve(relativePath).normalize();
            return hostFile.startsWith(root) ? hostFile : null;
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    /// Returns true when the guest path is absolute in Linux path syntax.
    private static boolean isAbsoluteGuestPath(String guestPath) {
        return guestPath.startsWith("/");
    }

    /// Removes every leading Linux path separator from an absolute guest path.
    private static String removeLeadingSlashes(String guestPath) {
        int index = 0;
        while (index < guestPath.length() && guestPath.charAt(index) == '/') {
            index++;
        }
        return guestPath.substring(index);
    }

    /// Returns true when a host file's canonical location stays below the sandbox root.
    private boolean canonicalFileStaysBelowHostRoot(TruffleFile hostFile) throws IOException {
        @Nullable TruffleFile root = currentHostRoot();
        if (root == null) {
            return false;
        }
        return hostFile.getCanonicalFile().startsWith(root.getCanonicalFile());
    }

    /// Returns the sandboxed host directory backing the guest-visible current working directory.
    private @Nullable TruffleFile currentHostWorkingDirectory() {
        @Nullable TruffleFile root = currentHostRoot();
        return root == null ? null : currentHostWorkingDirectory(root);
    }

    /// Returns the sandboxed host directory backing the guest-visible current working directory.
    private @Nullable TruffleFile currentHostWorkingDirectory(TruffleFile root) {
        try {
            TruffleFile hostFile = root.resolve(removeLeadingSlashes(guestWorkingDirectory)).normalize();
            return hostFile.startsWith(root) ? hostFile : null;
        } catch (InvalidPathException exception) {
            return null;
        }
    }

    /// Converts a sandboxed host path to an absolute guest-visible Linux path.
    private @Nullable String guestPathForHostFile(TruffleFile hostFile) {
        @Nullable TruffleFile root = currentHostRoot();
        if (root == null) {
            return null;
        }

        TruffleFile normalizedRoot = root.normalize();
        TruffleFile normalizedHostFile = hostFile.normalize();
        if (!normalizedHostFile.startsWith(normalizedRoot)) {
            return null;
        }

        try {
            String relativePath = normalizedRoot.relativize(normalizedHostFile).getPath().replace('\\', '/');
            return relativePath.isEmpty() ? "/" : "/" + relativePath;
        } catch (IllegalArgumentException | SecurityException exception) {
            return null;
        }
    }

    /// Validates that a file's parent directory exists inside the host-root sandbox.
    private long validateSandboxedParent(TruffleFile hostFile) {
        try {
            @Nullable TruffleFile root = currentHostRoot();
            if (root == null) {
                return EACCES;
            }

            @Nullable TruffleFile parent = hostFile.getParent();
            if (parent == null) {
                return EACCES;
            }
            if (!parent.exists()) {
                return ENOENT;
            }
            if (!parent.isDirectory()) {
                return ENOTDIR;
            }
            if (!parent.getCanonicalFile().startsWith(root.getCanonicalFile())) {
                return EACCES;
            }
            return 0;
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Returns true when the host path names an existing entry or a symbolic link.
    private static boolean pathEntryExists(TruffleFile hostFile) {
        return hostFile.exists() || hostFile.isSymbolicLink();
    }

    /// Resizes a writable host file channel while preserving its current offset.
    private static void resizeHostChannel(SeekableByteChannel channel, long length) throws IOException {
        long position = channel.position();
        try {
            long size = channel.size();
            if (length <= size) {
                channel.truncate(length);
                return;
            }

            channel.position(length - 1);
            channel.write(ByteBuffer.wrap(new byte[]{0}));
        } finally {
            channel.position(position);
        }
    }

    /// Returns the resolved host root, creating it through the Truffle environment on first use.
    private @Nullable TruffleFile currentHostRoot() {
        if (hostRoot != null) {
            return hostRoot;
        }
        if (env == null || hostRootPath == null) {
            return null;
        }

        try {
            hostRoot = env.getPublicTruffleFile(hostRootPath).getAbsoluteFile().normalize();
            return hostRoot;
        } catch (IllegalArgumentException | SecurityException exception) {
            return null;
        }
    }

    /// Adds an open file description to the guest descriptor table.
    private long addOpenFile(OpenFile openFile) {
        for (int index = 0; index < openFiles.size(); index++) {
            if (openFiles.get(index) == null) {
                openFiles.set(index, openFile);
                return index + 3L;
            }
        }

        openFiles.add(openFile);
        return openFiles.size() + 2L;
    }

    /// Stores an open file description at an explicit non-standard descriptor.
    private void setOpenFile(int fileDescriptor, OpenFile openFile) {
        int index = openFileIndex(fileDescriptor);
        while (openFiles.size() <= index) {
            openFiles.add(null);
        }
        openFiles.set(index, openFile);
    }

    /// Returns a new descriptor entry that duplicates the supplied file descriptor.
    private @Nullable OpenFile duplicateOpenFile(int fileDescriptor) {
        if (isStandardFileDescriptor(fileDescriptor)) {
            return OpenFile.standardFileDescriptor(fileDescriptor);
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return null;
        }
        openFile.retain();
        return openFile;
    }

    /// Releases a descriptor table entry and closes its backing object when it was the last reference.
    private static void releaseOpenFile(OpenFile openFile) throws IOException {
        if (openFile.release()) {
            openFile.close();
        }
    }

    /// Returns an open file description for a guest file descriptor.
    private @Nullable OpenFile openFile(int fileDescriptor) {
        int index = openFileIndex(fileDescriptor);
        if (index < 0 || index >= openFiles.size()) {
            return null;
        }
        return openFiles.get(index);
    }

    /// Returns true when a file descriptor refers to a standard stream or open guest descriptor.
    private boolean isOpenFileDescriptor(int fileDescriptor) {
        return isStandardFileDescriptor(fileDescriptor) || openFile(fileDescriptor) != null;
    }

    /// Returns Linux status flags for a standard stream or open guest descriptor.
    private long statusFlagsFor(int fileDescriptor) {
        int standardFileDescriptor = standardFileDescriptorFor(fileDescriptor);
        if (standardFileDescriptor == 0) {
            return O_RDONLY;
        }
        if (standardFileDescriptor == 1 || standardFileDescriptor == 2) {
            return O_WRONLY;
        }

        OpenFile openFile = openFile(fileDescriptor);
        assert openFile != null;

        long flags = openFile.readable() && openFile.writable()
                ? O_RDWR
                : openFile.writable() ? O_WRONLY : O_RDONLY;
        if (openFile.append()) {
            flags |= O_APPEND;
        }
        if (openFile.nonblocking()) {
            flags |= O_NONBLOCK;
        }
        if (openFile.isDirectory()) {
            flags |= O_DIRECTORY;
        }
        return flags;
    }

    /// Converts a guest file descriptor to an open-file table index.
    private static int openFileIndex(int fileDescriptor) {
        return fileDescriptor - 3;
    }

    /// Finds the first free page range for a new anonymous mapping.
    private long findMmapAddress(long requestedAddress, long length) {
        if (requestedAddress != 0) {
            long alignedAddress = alignUp(requestedAddress, PAGE_SIZE);
            if (alignedAddress > 0 && isMmapRangeAvailable(alignedAddress, length)) {
                return alignedAddress;
            }
        }

        long candidateAddress = alignUp(programBreak, PAGE_SIZE);
        while (candidateAddress > 0 && fitsGuestMemory(candidateAddress, length)) {
            MemoryMapping overlap = overlappingMemoryMapping(candidateAddress, length);
            if (overlap == null) {
                return candidateAddress;
            }
            candidateAddress = alignUp(overlap.endAddress(), PAGE_SIZE);
        }

        return findSparseMmapAddress(length);
    }

    /// Finds the first free sparse range outside the initial memory window.
    private long findSparseMmapAddress(long length) {
        long candidateAddress = alignUp(Math.max(memory.endAddress(), programBreak), PAGE_SIZE);
        while (candidateAddress > 0 && isValidGuestRange(candidateAddress, length)) {
            MemoryMapping overlap = overlappingMemoryMapping(candidateAddress, length);
            if (overlap == null) {
                return candidateAddress;
            }
            candidateAddress = alignUp(overlap.endAddress(), PAGE_SIZE);
        }
        return 0;
    }

    /// Returns true when a guest range is suitable for a new anonymous mapping.
    private boolean isMmapRangeAvailable(long address, long length) {
        return isValidGuestRange(address, length)
                && address >= programBreak
                && !overlapsMemoryMappings(address, length)
                && (fitsGuestMemory(address, length) || !overlapsInitialMemory(address, length));
    }

    /// Adds an active anonymous mapping in address order.
    private void addMemoryMapping(long address, long length, long protection) {
        MemoryMapping mapping = new MemoryMapping(address, length, protection);
        int insertionIndex = 0;
        while (insertionIndex < memoryMappings.size()
                && Long.compareUnsigned(memoryMappings.get(insertionIndex).address(), address) < 0) {
            insertionIndex++;
        }
        memoryMappings.add(insertionIndex, mapping);
    }

    /// Removes or splits active anonymous mappings overlapped by a guest range.
    private void removeMemoryMappings(long address, long length) {
        long endAddress = address + length;
        for (int index = 0; index < memoryMappings.size(); ) {
            MemoryMapping mapping = memoryMappings.get(index);
            if (!rangesOverlap(address, endAddress, mapping.address(), mapping.endAddress())) {
                index++;
                continue;
            }

            memoryMappings.remove(index);
            long clearStart = Math.max(address, mapping.address());
            long clearEnd = Math.min(endAddress, mapping.endAddress());
            releaseRemovedMappingMemory(mapping, clearStart, clearEnd - clearStart);

            if (mapping.address() < address) {
                memoryMappings.add(index, new MemoryMapping(
                        mapping.address(),
                        address - mapping.address(),
                        mapping.protection()));
                index++;
            }
            if (endAddress < mapping.endAddress()) {
                memoryMappings.add(index, new MemoryMapping(
                        endAddress,
                        mapping.endAddress() - endAddress,
                        mapping.protection()));
                index++;
            }
        }
    }

    /// Ensures that the supplied range has native backing for non-`PROT_NONE` access.
    private boolean ensureMemoryBacking(long address, long length) {
        if (fitsGuestMemory(address, length)) {
            return true;
        }
        if (!isValidGuestRange(address, length) || overlapsInitialMemory(address, length)) {
            return false;
        }
        return memory.map(address, length);
    }

    /// Releases or clears native backing for a removed mapping range.
    private void releaseRemovedMappingMemory(MemoryMapping mapping, long address, long length) {
        if (!mapping.isBacked()) {
            return;
        }
        if (fitsGuestMemory(address, length)) {
            memory.clear(address, length);
        } else {
            memory.unmap(address, length);
        }
    }

    /// Releases native backing after a mapped range transitions to `PROT_NONE`.
    private void releaseProtectedMemory(long address, long length) {
        if (!fitsGuestMemory(address, length)) {
            memory.unmap(address, length);
        }
    }

    /// Allocates native backing needed before changing mappings to a backed protection.
    private boolean prepareMemoryProtection(long address, long length, long protection) {
        if (!isMemoryBackedProtection(protection)) {
            return true;
        }

        ArrayList<MemoryRange> allocatedRanges = new ArrayList<>();
        long endAddress = address + length;
        for (MemoryMapping mapping : memoryMappings) {
            if (!rangesOverlap(address, endAddress, mapping.address(), mapping.endAddress()) || mapping.isBacked()) {
                continue;
            }

            long protectStart = Math.max(address, mapping.address());
            long protectEnd = Math.min(endAddress, mapping.endAddress());
            long protectLength = protectEnd - protectStart;
            if (fitsGuestMemory(protectStart, protectLength)) {
                memory.clear(protectStart, protectLength);
                continue;
            }
            if (!memory.map(protectStart, protectLength)) {
                rollbackAllocatedMemory(allocatedRanges);
                return false;
            }
            allocatedRanges.add(new MemoryRange(protectStart, protectLength));
            memory.clear(protectStart, protectLength);
        }
        return true;
    }

    /// Rolls back sparse memory backing allocated during a failed protection change.
    private void rollbackAllocatedMemory(ArrayList<MemoryRange> ranges) {
        for (MemoryRange range : ranges) {
            memory.unmap(range.address(), range.length());
        }
    }

    /// Updates tracked mapping protection metadata, splitting mappings as needed.
    private void updateMemoryMappingsProtection(long address, long length, long protection) {
        long endAddress = address + length;
        for (int index = 0; index < memoryMappings.size(); ) {
            MemoryMapping mapping = memoryMappings.get(index);
            if (!rangesOverlap(address, endAddress, mapping.address(), mapping.endAddress())) {
                index++;
                continue;
            }

            memoryMappings.remove(index);
            long protectStart = Math.max(address, mapping.address());
            long protectEnd = Math.min(endAddress, mapping.endAddress());
            long protectLength = protectEnd - protectStart;
            if (mapping.isBacked() && !isMemoryBackedProtection(protection)) {
                releaseProtectedMemory(protectStart, protectLength);
            }

            if (mapping.address() < protectStart) {
                memoryMappings.add(index, new MemoryMapping(
                        mapping.address(),
                        protectStart - mapping.address(),
                        mapping.protection()));
                index++;
            }
            memoryMappings.add(index, new MemoryMapping(protectStart, protectLength, protection));
            index++;
            if (protectEnd < mapping.endAddress()) {
                memoryMappings.add(index, new MemoryMapping(
                        protectEnd,
                        mapping.endAddress() - protectEnd,
                        mapping.protection()));
                index++;
            }
        }
    }

    /// Clears backed anonymous memory ranges covered by discard-style `madvise` hints.
    private void clearAdvisedMemory(long address, long length) {
        long endAddress = address + length;
        for (MemoryMapping mapping : memoryMappings) {
            if (!mapping.isBacked() || !rangesOverlap(address, endAddress, mapping.address(), mapping.endAddress())) {
                continue;
            }

            long clearStart = Math.max(address, mapping.address());
            long clearEnd = Math.min(endAddress, mapping.endAddress());
            memory.clear(clearStart, clearEnd - clearStart);
        }
    }

    /// Returns true when an advisory memory hint can be accepted by this simulator.
    private static boolean isSupportedMemoryAdvice(long advice) {
        return advice == MADV_NORMAL
                || advice == MADV_RANDOM
                || advice == MADV_SEQUENTIAL
                || advice == MADV_WILLNEED
                || advice == MADV_DONTNEED
                || advice == MADV_FREE
                || advice == MADV_DONTFORK
                || advice == MADV_DOFORK
                || advice == MADV_MERGEABLE
                || advice == MADV_UNMERGEABLE
                || advice == MADV_HUGEPAGE
                || advice == MADV_NOHUGEPAGE
                || advice == MADV_DONTDUMP
                || advice == MADV_DODUMP
                || advice == MADV_WIPEONFORK
                || advice == MADV_KEEPONFORK
                || advice == MADV_COLD
                || advice == MADV_PAGEOUT
                || advice == MADV_POPULATE_READ
                || advice == MADV_POPULATE_WRITE
                || advice == MADV_DONTNEED_LOCKED
                || advice == MADV_COLLAPSE;
    }

    /// Returns true when a guest range is mapped by the initial window or anonymous mappings.
    private boolean isMappedRange(long address, long length) {
        if (!isValidGuestRange(address, length)) {
            return false;
        }

        long endAddress = address + length;
        long cursor = address;
        while (cursor < endAddress) {
            if (cursor >= memory.baseAddress() && cursor < memory.endAddress()) {
                cursor = Math.min(endAddress, memory.endAddress());
                continue;
            }

            @Nullable MemoryMapping mapping = memoryMappingCovering(cursor);
            if (mapping == null) {
                return false;
            }
            cursor = Math.min(endAddress, mapping.endAddress());
        }
        return true;
    }

    /// Returns the anonymous mapping covering a guest address, or null when absent.
    private @Nullable MemoryMapping memoryMappingCovering(long address) {
        for (MemoryMapping mapping : memoryMappings) {
            if (address >= mapping.address() && address < mapping.endAddress()) {
                return mapping;
            }
            if (address < mapping.address()) {
                return null;
            }
        }
        return null;
    }

    /// Returns true when a guest range overlaps an active anonymous mapping.
    private boolean overlapsMemoryMappings(long address, long length) {
        return overlappingMemoryMapping(address, length) != null;
    }

    /// Returns the first active anonymous mapping overlapped by a guest range.
    private @Nullable MemoryMapping overlappingMemoryMapping(long address, long length) {
        long endAddress = address + length;
        for (MemoryMapping mapping : memoryMappings) {
            if (rangesOverlap(address, endAddress, mapping.address(), mapping.endAddress())) {
                return mapping;
            }
        }
        return null;
    }

    /// Returns true when a mapping flag combination requires an exact address.
    private static boolean requiresFixedMapping(long flags) {
        return (flags & (MAP_FIXED | MAP_FIXED_NOREPLACE)) != 0;
    }

    /// Returns true when a protection value requires guest memory to be backed.
    private static boolean isMemoryBackedProtection(long protection) {
        return protection != PROT_NONE;
    }

    /// Returns true when an address is aligned to the guest page size.
    private static boolean isPageAligned(long address) {
        return (address & (PAGE_SIZE - 1L)) == 0;
    }

    /// Rounds a positive guest size or address up to a power-of-two alignment.
    private static long alignUp(long value, long alignment) {
        long mask = alignment - 1;
        if (value > Long.MAX_VALUE - mask) {
            return -1;
        }
        return (value + mask) & ~mask;
    }

    /// Returns true when the supplied range is non-negative and does not overflow.
    private static boolean isValidGuestRange(long address, long length) {
        return address >= 0 && length >= 0 && address <= Long.MAX_VALUE - length;
    }

    /// Returns true when the supplied guest range fits in the memory window.
    private boolean fitsGuestMemory(long address, long length) {
        return address >= memory.baseAddress()
                && length >= 0
                && address <= memory.endAddress()
                && length <= memory.endAddress() - address;
    }

    /// Returns true when the supplied guest range overlaps the initial memory window.
    private boolean overlapsInitialMemory(long address, long length) {
        return rangesOverlap(address, address + length, memory.baseAddress(), memory.endAddress());
    }

    /// Returns true when two half-open address ranges overlap.
    private static boolean rangesOverlap(long firstStart, long firstEnd, long secondStart, long secondEnd) {
        return firstStart < secondEnd && secondStart < firstEnd;
    }

    /// Returns the next deterministic pseudo-random byte.
    private byte nextRandomByte() {
        randomState = randomState * 6364136223846793005L + 1442695040888963407L;
        return (byte) (randomState >>> 56);
    }

    /// Returns the host output stream mapped to a guest file descriptor.
    private @Nullable OutputStream outputStreamFor(int fileDescriptor) {
        int standardFileDescriptor = standardFileDescriptorFor(fileDescriptor);
        if (standardFileDescriptor == 1) {
            return out;
        }
        if (standardFileDescriptor == 2) {
            return err;
        }
        return null;
    }

    /// Returns the host input stream mapped to a guest file descriptor.
    private @Nullable InputStream inputStreamFor(int fileDescriptor) {
        return standardFileDescriptorFor(fileDescriptor) == 0 ? in : null;
    }

    /// Returns the underlying standard descriptor number, or `-1` for non-standard descriptors.
    private int standardFileDescriptorFor(int fileDescriptor) {
        if (isStandardFileDescriptor(fileDescriptor)) {
            return fileDescriptor;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile != null && openFile.isStandardFileDescriptor()) {
            return openFile.standardFileDescriptor();
        }
        return -1;
    }

    /// Returns true when the file descriptor is one of stdin, stdout, or stderr.
    private static boolean isStandardFileDescriptor(int fileDescriptor) {
        return fileDescriptor >= 0 && fileDescriptor <= 2;
    }

    /// Describes an anonymous guest memory mapping tracked by the syscall layer.
    private record MemoryMapping(
            /// The inclusive guest start address of the mapping.
            long address,

            /// The byte length of the mapping.
            long length,

            /// The Linux protection flags currently tracked for the mapping.
            long protection) {
        /// Returns the exclusive guest end address of the mapping.
        long endAddress() {
            return address + length;
        }

        /// Returns true when this mapping should have host memory backing.
        boolean isBacked() {
            return isMemoryBackedProtection(protection);
        }
    }

    /// Describes a guest memory range allocated while changing protections.
    private record MemoryRange(
            /// The inclusive guest start address of the range.
            long address,

            /// The byte length of the range.
            long length) {
    }

    /// Describes one cached Linux directory entry for a directory descriptor.
    private record DirectoryEntry(
            /// The entry name without a trailing null byte.
            String name,

            /// The deterministic inode value exposed to the guest.
            long inode,

            /// The Linux `DT_*` entry type.
            byte type) {
    }

    /// Stores one guest thread blocked in a futex wait operation.
    private static final class FutexWaiter {
        /// The guest futex word address being waited on.
        private final long address;

        /// The futex bitset mask used to match wake operations.
        private final long bitset;

        /// Whether a matching futex wake selected this waiter.
        private boolean woken;

        /// Creates a waiter for the supplied futex word and bitset.
        private FutexWaiter(long address, long bitset) {
            this.address = address;
            this.bitset = bitset;
        }
    }

    /// Stores the shared counter state for one Linux `eventfd` open-file description.
    private static final class EventCounter {
        /// The current unsigned 64-bit counter value.
        private long value;

        /// Whether reads decrement the counter by one and return `1`.
        private final boolean semaphore;

        /// Whether the open-file status flags include `O_NONBLOCK`.
        private final boolean nonblocking;

        /// Creates an event counter with the supplied initial value and flags.
        EventCounter(long value, boolean semaphore, boolean nonblocking) {
            this.value = value;
            this.semaphore = semaphore;
            this.nonblocking = nonblocking;
        }

        /// Returns true when a read can complete without blocking.
        boolean isReadable() {
            return value != 0;
        }

        /// Returns true when a write can increment the counter without overflowing.
        boolean isWritable() {
            return value != -2L;
        }

        /// Returns true when the open-file status flags include `O_NONBLOCK`.
        boolean nonblocking() {
            return nonblocking;
        }

        /// Reads the current counter value and applies Linux `eventfd` decrement rules.
        long readValue() {
            if (semaphore) {
                value--;
                return 1;
            }

            long result = value;
            value = 0;
            return result;
        }

        /// Adds an unsigned increment to the counter or returns a raw negative Linux error.
        long write(long increment) {
            if (increment == -1L) {
                return EINVAL;
            }

            long sum = value + increment;
            if (Long.compareUnsigned(sum, value) < 0 || sum == -1L) {
                return EAGAIN;
            }

            value = sum;
            return 0;
        }
    }

    /// One descriptor interest stored in an in-memory Linux `epoll` set.
    private record EpollInterest(
            /// The watched guest file descriptor.
            int fileDescriptor,

            /// The event mask requested by the guest.
            int events,

            /// The opaque guest data returned with readiness notifications.
            long data) {
    }

    /// Stores descriptor interests for a single in-memory Linux `epoll` descriptor.
    private static final class EpollSet {
        /// Ordered descriptor interests registered by the guest.
        private final ArrayList<EpollInterest> interests = new ArrayList<>();

        /// Adds a new descriptor interest or returns a raw negative Linux error.
        long add(int fileDescriptor, int events, long data) {
            if (findIndex(fileDescriptor) >= 0) {
                return EEXIST;
            }

            interests.add(new EpollInterest(fileDescriptor, events, data));
            return 0;
        }

        /// Replaces an existing descriptor interest or returns a raw negative Linux error.
        long modify(int fileDescriptor, int events, long data) {
            int index = findIndex(fileDescriptor);
            if (index < 0) {
                return ENOENT;
            }

            interests.set(index, new EpollInterest(fileDescriptor, events, data));
            return 0;
        }

        /// Removes an existing descriptor interest or returns a raw negative Linux error.
        long remove(int fileDescriptor) {
            int index = findIndex(fileDescriptor);
            if (index < 0) {
                return ENOENT;
            }

            interests.remove(index);
            return 0;
        }

        /// Returns the number of registered descriptor interests.
        int size() {
            return interests.size();
        }

        /// Returns the descriptor interest at the supplied index.
        EpollInterest interest(int index) {
            return interests.get(index);
        }

        /// Returns the index of the descriptor interest, or `-1` when absent.
        private int findIndex(int fileDescriptor) {
            for (int index = 0; index < interests.size(); index++) {
                if (interests.get(index).fileDescriptor() == fileDescriptor) {
                    return index;
                }
            }
            return -1;
        }
    }

    /// Stores buffered bytes for a single in-memory pipe.
    private static final class PipeBuffer {
        /// The initial pipe buffer capacity used before the first expansion.
        private static final int INITIAL_CAPACITY = 64;

        /// The circular byte buffer containing unread pipe bytes.
        private byte[] buffer = new byte[INITIAL_CAPACITY];

        /// The index of the first unread byte in `buffer`.
        private int start;

        /// The number of unread bytes currently stored in `buffer`.
        private int length;

        /// Whether at least one read endpoint is still open.
        private boolean readerOpen = true;

        /// Whether at least one write endpoint is still open.
        private boolean writerOpen = true;

        /// Reads up to the requested byte count into the supplied destination.
        int read(byte[] destination, int maximumLength, boolean nonblocking) {
            if (length == 0) {
                return writerOpen && nonblocking ? (int) EAGAIN : 0;
            }

            int count = Math.min(maximumLength, length);
            int firstCount = Math.min(count, buffer.length - start);
            System.arraycopy(buffer, start, destination, 0, firstCount);
            int secondCount = count - firstCount;
            if (secondCount > 0) {
                System.arraycopy(buffer, 0, destination, firstCount, secondCount);
            }

            start = (start + count) % buffer.length;
            length -= count;
            if (length == 0) {
                start = 0;
            }
            return count;
        }

        /// Writes all supplied bytes to the pipe buffer.
        long write(byte[] source) {
            if (!readerOpen) {
                return EPIPE;
            }

            ensureCapacity(length + source.length);
            int writeIndex = (start + length) % buffer.length;
            int firstCount = Math.min(source.length, buffer.length - writeIndex);
            System.arraycopy(source, 0, buffer, writeIndex, firstCount);
            int secondCount = source.length - firstCount;
            if (secondCount > 0) {
                System.arraycopy(source, firstCount, buffer, 0, secondCount);
            }
            length += source.length;
            return source.length;
        }

        /// Marks the read endpoint as closed.
        void closeReader() {
            readerOpen = false;
        }

        /// Marks the write endpoint as closed.
        void closeWriter() {
            writerOpen = false;
        }

        /// Returns true when a read endpoint would observe data or end-of-file immediately.
        boolean isReadable() {
            return length > 0 || !writerOpen;
        }

        /// Returns true when at least one read endpoint is still open.
        boolean isReaderOpen() {
            return readerOpen;
        }

        /// Returns true when at least one write endpoint is still open.
        boolean isWriterOpen() {
            return writerOpen;
        }

        /// Expands the circular buffer while preserving unread byte order.
        private void ensureCapacity(int requestedCapacity) {
            if (requestedCapacity <= buffer.length) {
                return;
            }

            int newCapacity = buffer.length;
            while (newCapacity < requestedCapacity) {
                newCapacity *= 2;
            }

            byte[] newBuffer = new byte[newCapacity];
            int firstCount = Math.min(length, buffer.length - start);
            System.arraycopy(buffer, start, newBuffer, 0, firstCount);
            int secondCount = length - firstCount;
            if (secondCount > 0) {
                System.arraycopy(buffer, 0, newBuffer, firstCount, secondCount);
            }

            buffer = newBuffer;
            start = 0;
        }
    }

    /// Describes an open file description referenced by one or more guest file descriptors.
    private static final class OpenFile {
        /// The original standard descriptor number, or `-1` for non-standard entries.
        private final int standardFileDescriptor;

        /// The resolved host path backing the guest file descriptor.
        private final @Nullable TruffleFile path;

        /// The host file channel backing a regular host file descriptor.
        private final @Nullable SeekableByteChannel channel;

        /// The pipe buffer backing a pipe endpoint.
        private final @Nullable PipeBuffer pipe;

        /// The counter backing an eventfd descriptor.
        private final @Nullable EventCounter eventCounter;

        /// The interest set backing an epoll descriptor.
        private final @Nullable EpollSet epollSet;

        /// Whether this descriptor refers to a host directory.
        private final boolean directory;

        /// Whether this descriptor is the read endpoint of a pipe.
        private final boolean pipeReader;

        /// Whether this descriptor is the write endpoint of a pipe.
        private final boolean pipeWriter;

        /// Whether the guest file descriptor permits reads.
        private final boolean readable;

        /// Whether the guest file descriptor permits writes.
        private final boolean writable;

        /// Whether writes append to the current end of a host file.
        private final boolean append;

        /// Whether empty pipe reads should return `EAGAIN`.
        private final boolean nonblocking;

        /// The cached directory entries for this descriptor, or null until first `getdents64`.
        private @Nullable DirectoryEntry[] directoryEntries;

        /// The next directory entry index returned by `getdents64`.
        private int directoryEntryIndex;

        /// The number of guest descriptor table slots sharing this entry.
        private int references = 1;

        /// Creates a file descriptor entry.
        private OpenFile(
                int standardFileDescriptor,
                @Nullable TruffleFile path,
                @Nullable SeekableByteChannel channel,
                @Nullable PipeBuffer pipe,
                @Nullable EventCounter eventCounter,
                @Nullable EpollSet epollSet,
                boolean directory,
                boolean pipeReader,
                boolean pipeWriter,
                boolean readable,
                boolean writable,
                boolean append,
                boolean nonblocking) {
            this.standardFileDescriptor = standardFileDescriptor;
            this.path = path;
            this.channel = channel;
            this.pipe = pipe;
            this.eventCounter = eventCounter;
            this.epollSet = epollSet;
            this.directory = directory;
            this.pipeReader = pipeReader;
            this.pipeWriter = pipeWriter;
            this.readable = readable;
            this.writable = writable;
            this.append = append;
            this.nonblocking = nonblocking;
        }

        /// Creates an entry backed by a host file channel.
        static OpenFile hostFile(
                TruffleFile path,
                SeekableByteChannel channel,
                boolean readable,
                boolean writable,
                boolean append) {
            return new OpenFile(
                    -1,
                    path,
                    channel,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    readable,
                    writable,
                    append,
                    false);
        }

        /// Creates an entry backed by a host directory path.
        static OpenFile hostDirectory(TruffleFile path) {
            return new OpenFile(-1, path, null, null, null, null, true, false, false, true, false, false, false);
        }

        /// Creates an entry that duplicates a standard stream descriptor.
        static OpenFile standardFileDescriptor(int fileDescriptor) {
            return new OpenFile(
                    fileDescriptor,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    false,
                    false,
                    fileDescriptor == 0,
                    fileDescriptor == 1 || fileDescriptor == 2,
                    false,
                    false);
        }

        /// Creates the read endpoint of a pipe.
        static OpenFile pipeReader(PipeBuffer pipe, boolean nonblocking) {
            return new OpenFile(-1, null, null, pipe, null, null, false, true, false, true, false, false, nonblocking);
        }

        /// Creates the write endpoint of a pipe.
        static OpenFile pipeWriter(PipeBuffer pipe, boolean nonblocking) {
            return new OpenFile(-1, null, null, pipe, null, null, false, false, true, false, true, false, nonblocking);
        }

        /// Creates an entry backed by an eventfd counter.
        static OpenFile eventFile(EventCounter eventCounter) {
            return new OpenFile(
                    -1,
                    null,
                    null,
                    null,
                    eventCounter,
                    null,
                    false,
                    false,
                    false,
                    true,
                    true,
                    false,
                    eventCounter.nonblocking());
        }

        /// Creates an entry backed by an epoll interest set.
        static OpenFile epollFile(EpollSet epollSet) {
            return new OpenFile(-1, null, null, null, null, epollSet, false, false, false, true, false, false, false);
        }

        /// Returns true when this entry duplicates one of the original standard streams.
        boolean isStandardFileDescriptor() {
            return standardFileDescriptor >= 0;
        }

        /// Returns the original standard descriptor number.
        int standardFileDescriptor() {
            return standardFileDescriptor;
        }

        /// Returns true when this entry is backed by a host file channel.
        boolean isHostFile() {
            return channel != null;
        }

        /// Returns true when this entry is backed by a pipe endpoint.
        boolean isPipe() {
            return pipe != null;
        }

        /// Returns true when this entry is backed by an eventfd counter.
        boolean isEventFile() {
            return eventCounter != null;
        }

        /// Returns true when this entry is backed by an epoll interest set.
        boolean isEpollFile() {
            return epollSet != null;
        }

        /// Returns true when this entry refers to a host directory.
        boolean isDirectory() {
            return directory;
        }

        /// Returns true when this entry is a pipe read endpoint.
        boolean isPipeReader() {
            return pipeReader;
        }

        /// Returns true when this entry is a pipe write endpoint.
        boolean isPipeWriter() {
            return pipeWriter;
        }

        /// Returns the host file channel backing this descriptor.
        SeekableByteChannel channel() {
            assert channel != null;
            return channel;
        }

        /// Returns the pipe buffer backing this descriptor.
        PipeBuffer pipe() {
            assert pipe != null;
            return pipe;
        }

        /// Returns the eventfd counter backing this descriptor.
        EventCounter eventCounter() {
            assert eventCounter != null;
            return eventCounter;
        }

        /// Returns the epoll interest set backing this descriptor.
        EpollSet epollSet() {
            assert epollSet != null;
            return epollSet;
        }

        /// Returns the host path backing this descriptor.
        @Nullable TruffleFile path() {
            return path;
        }

        /// Returns true when reads are permitted.
        boolean readable() {
            return readable;
        }

        /// Returns true when writes are permitted.
        boolean writable() {
            return writable;
        }

        /// Returns true when host file writes append to the end of file.
        boolean append() {
            return append;
        }

        /// Returns true when pipe reads are nonblocking.
        boolean nonblocking() {
            return nonblocking;
        }

        /// Returns cached directory entries, or null when the descriptor has not been listed yet.
        @Nullable DirectoryEntry[] directoryEntries() {
            return directoryEntries;
        }

        /// Stores cached directory entries for repeated `getdents64` calls.
        void setDirectoryEntries(DirectoryEntry[] directoryEntries) {
            this.directoryEntries = directoryEntries;
        }

        /// Returns the next directory entry index for `getdents64`.
        int directoryEntryIndex() {
            return directoryEntryIndex;
        }

        /// Updates the next directory entry index for `getdents64`.
        void setDirectoryEntryIndex(int directoryEntryIndex) {
            this.directoryEntryIndex = directoryEntryIndex;
        }

        /// Adds one guest descriptor reference to this entry.
        void retain() {
            references++;
        }

        /// Removes one guest descriptor reference and returns true when this entry is no longer referenced.
        boolean release() {
            references--;
            if (references < 0) {
                throw new AssertionError("open file reference count became negative");
            }
            return references == 0;
        }

        /// Closes the backing object after the last guest descriptor reference is released.
        void close() throws IOException {
            if (channel != null) {
                channel.close();
                return;
            }
            if (pipe != null) {
                if (pipeReader) {
                    pipe.closeReader();
                }
                if (pipeWriter) {
                    pipe.closeWriter();
                }
            }
        }
    }
}
