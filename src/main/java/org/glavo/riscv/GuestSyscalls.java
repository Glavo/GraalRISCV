package org.glavo.riscv;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.TruffleLanguage;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
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
    /// The Linux RISC-V syscall number for `fcntl`.
    private static final long SYS_FCNTL = 25;

    /// The Linux RISC-V syscall number for `ioctl`.
    private static final long SYS_IOCTL = 29;

    /// The Linux RISC-V syscall number for `openat`.
    private static final long SYS_OPENAT = 56;

    /// The Linux RISC-V syscall number for `close`.
    private static final long SYS_CLOSE = 57;

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

    /// The Linux RISC-V syscall number for `fstat`.
    private static final long SYS_FSTAT = 80;

    /// The Linux RISC-V syscall number for `exit`.
    private static final long SYS_EXIT = 93;

    /// The Linux RISC-V syscall number for `exit_group`.
    private static final long SYS_EXIT_GROUP = 94;

    /// The Linux RISC-V syscall number for `set_tid_address`.
    private static final long SYS_SET_TID_ADDRESS = 96;

    /// The Linux RISC-V syscall number for `set_robust_list`.
    private static final long SYS_SET_ROBUST_LIST = 99;

    /// The Linux RISC-V syscall number for `nanosleep`.
    private static final long SYS_NANOSLEEP = 101;

    /// The Linux RISC-V syscall number for `clock_gettime`.
    private static final long SYS_CLOCK_GETTIME = 113;

    /// The Linux RISC-V syscall number for `sched_getaffinity`.
    private static final long SYS_SCHED_GETAFFINITY = 123;

    /// The Linux RISC-V syscall number for `rt_sigaction`.
    private static final long SYS_RT_SIGACTION = 134;

    /// The Linux RISC-V syscall number for `rt_sigprocmask`.
    private static final long SYS_RT_SIGPROCMASK = 135;

    /// The Linux RISC-V syscall number for `prctl`.
    private static final long SYS_PRCTL = 167;

    /// The Linux RISC-V syscall number for `getpid`.
    private static final long SYS_GETPID = 172;

    /// The Linux RISC-V syscall number for `gettid`.
    private static final long SYS_GETTID = 178;

    /// The Linux RISC-V syscall number for `brk`.
    private static final long SYS_BRK = 214;

    /// The Linux RISC-V syscall number for `munmap`.
    private static final long SYS_MUNMAP = 215;

    /// The Linux RISC-V syscall number for `mmap`.
    private static final long SYS_MMAP = 222;

    /// The Linux RISC-V syscall number for `mprotect`.
    private static final long SYS_MPROTECT = 226;

    /// The Linux RISC-V syscall number for `madvise`.
    private static final long SYS_MADVISE = 233;

    /// The Linux RISC-V syscall number for `riscv_hwprobe`.
    private static final long SYS_RISCV_HWPROBE = 258;

    /// The Linux RISC-V syscall number for `getrandom`.
    private static final long SYS_GETRANDOM = 278;

    /// Linux `EBADF` as a raw negative syscall result.
    private static final long EBADF = -9;

    /// Linux `ENOENT` as a raw negative syscall result.
    private static final long ENOENT = -2;

    /// Linux `EACCES` as a raw negative syscall result.
    private static final long EACCES = -13;

    /// Linux `ENOMEM` as a raw negative syscall result.
    private static final long ENOMEM = -12;

    /// Linux `EEXIST` as a raw negative syscall result.
    private static final long EEXIST = -17;

    /// Linux `EINTR` as a raw negative syscall result.
    private static final long EINTR = -4;

    /// Linux `ENODEV` as a raw negative syscall result.
    private static final long ENODEV = -19;

    /// Linux `EISDIR` as a raw negative syscall result.
    private static final long EISDIR = -21;

    /// Linux `EINVAL` as a raw negative syscall result.
    private static final long EINVAL = -22;

    /// Linux `ENOTTY` as a raw negative syscall result.
    private static final long ENOTTY = -25;

    /// Linux `ENAMETOOLONG` as a raw negative syscall result.
    private static final long ENAMETOOLONG = -36;

    /// Linux `ESPIPE` as a raw negative syscall result.
    private static final long ESPIPE = -29;

    /// The maximum Linux `iovcnt` accepted by `readv` and `writev`.
    private static final long IOV_MAX = 1024;

    /// The byte size of one Linux RISC-V 64-bit `struct iovec`.
    private static final int IOVEC_SIZE = 16;

    /// The byte offset of `iov_base` inside `struct iovec`.
    private static final int IOVEC_BASE_OFFSET = 0;

    /// The byte offset of `iov_len` inside `struct iovec`.
    private static final int IOVEC_LENGTH_OFFSET = 8;

    /// The Linux `AT_FDCWD` pseudo file descriptor for path-based syscalls.
    private static final long AT_FDCWD = -100;

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

    /// The byte size of Linux generic 64-bit kernel `sigset_t`.
    private static final long KERNEL_SIGSET_SIZE = 8;

    /// The byte size of Linux generic 64-bit kernel `struct sigaction`.
    private static final long KERNEL_SIGACTION_SIZE = 32;

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

    /// The `tv_sec` byte offset inside Linux RISC-V 64-bit `struct timespec`.
    private static final int TIMESPEC_SECONDS_OFFSET = 0;

    /// The `tv_nsec` byte offset inside Linux RISC-V 64-bit `struct timespec`.
    private static final int TIMESPEC_NANOSECONDS_OFFSET = Long.BYTES;

    /// The number of nanoseconds in one second.
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

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

    /// The Linux `S_IFCHR` file type bit used for character devices.
    private static final int STAT_MODE_CHARACTER_DEVICE = 0020000;

    /// The Linux `S_IFREG` file type bit used for regular files.
    private static final int STAT_MODE_REGULAR_FILE = 0100000;

    /// The file permission bits exposed for standard streams.
    private static final int STAT_MODE_READ_WRITE_ALL = 0666;

    /// The file permission bits exposed for read-only host files.
    private static final int STAT_MODE_READ_ALL = 0444;

    /// The `st_mode` value exposed for the simulator standard streams.
    private static final int STANDARD_STREAM_STAT_MODE = STAT_MODE_CHARACTER_DEVICE | STAT_MODE_READ_WRITE_ALL;

    /// The block size exposed for standard streams.
    private static final int STANDARD_STREAM_BLOCK_SIZE = 4096;

    /// The fixed guest process id returned by process identity syscalls.
    private static final long GUEST_PROCESS_ID = 1;

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

    /// The configured host root path exposed through sandboxed `openat`.
    private final @Nullable String hostRootPath;

    /// The resolved host root directory exposed through sandboxed `openat`.
    private @Nullable TruffleFile hostRoot;

    /// The lowest program break accepted by the `brk` syscall.
    private final long initialProgramBreak;

    /// The current guest program break returned by `brk`.
    private long programBreak;

    /// The active anonymous mappings created by `mmap`.
    private final ArrayList<MemoryMapping> memoryMappings = new ArrayList<>();

    /// The guest file descriptor table for host files opened by `openat`.
    private final ArrayList<@Nullable OpenFile> openFiles = new ArrayList<>();

    /// The address supplied by `set_tid_address`, or zero when unset.
    private long clearChildTidAddress;

    /// The robust futex list head address supplied by `set_robust_list`.
    private long robustListHeadAddress;

    /// The robust futex list structure length supplied by `set_robust_list`.
    private long robustListLength;

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

    /// Creates a syscall handler backed by the supplied host streams and heap boundary.
    public GuestSyscalls(
            Memory memory,
            InputStream in,
            OutputStream out,
            OutputStream err,
            long initialProgramBreak) {
        this(memory, in, out, err, initialProgramBreak, null, null, null, Clock.systemUTC());
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
        this(memory, in, out, err, initialProgramBreak, hostRoot, null, null, clock);
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
        this(memory, in, out, err, initialProgramBreak, null, env, hostRootPath, clock);
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
            Clock clock) {
        if (initialProgramBreak < memory.baseAddress() || initialProgramBreak > memory.endAddress()) {
            throw new RiscVException("Initial program break is outside guest memory: address=0x"
                    + Long.toUnsignedString(initialProgramBreak, 16));
        }

        this.memory = memory;
        this.in = in;
        this.out = out;
        this.err = err;
        this.env = env;
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
        if (callNumber == SYS_FCNTL) {
            state.setRegister(10, fcntl((int) state.register(10), state.register(11), state.register(12)));
            return;
        }
        if (callNumber == SYS_IOCTL) {
            state.setRegister(10, ioctl((int) state.register(10), state.register(11), state.register(12)));
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
        if (callNumber == SYS_FSTAT) {
            state.setRegister(10, fstat((int) state.register(10), state.register(11)));
            return;
        }
        if (callNumber == SYS_EXIT || callNumber == SYS_EXIT_GROUP) {
            throw new ProgramExitException(state.register(10));
        }
        if (callNumber == SYS_SET_TID_ADDRESS) {
            state.setRegister(10, setTidAddress(state.register(10)));
            return;
        }
        if (callNumber == SYS_SET_ROBUST_LIST) {
            state.setRegister(10, setRobustList(state.register(10), state.register(11)));
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
        if (callNumber == SYS_SCHED_GETAFFINITY) {
            state.setRegister(10, schedGetaffinity(state.register(10), state.register(11), state.register(12)));
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
        if (callNumber == SYS_PRCTL) {
            state.setRegister(10, prctl(
                    state.register(10),
                    state.register(11),
                    state.register(12),
                    state.register(13),
                    state.register(14)));
            return;
        }
        if (callNumber == SYS_GETPID || callNumber == SYS_GETTID) {
            state.setRegister(10, GUEST_PROCESS_ID);
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
        if (callNumber == SYS_GETRANDOM) {
            state.setRegister(10, getrandom(state.register(10), state.register(11), state.register(12)));
            return;
        }
        throw new RiscVException(unsupportedEcallMessage(state, pc, callNumber));
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
                openFile.channel().close();
                openFiles.set(index, null);
            } catch (IOException exception) {
                throw new RiscVException("Failed to close guest host file", exception);
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

    /// Opens a host file below the configured host root.
    private long openat(long directoryFileDescriptor, long pathAddress, long flags, long mode) {
        if (directoryFileDescriptor != AT_FDCWD) {
            return EBADF;
        }
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

        @Nullable TruffleFile hostFile = resolveHostFile(guestPath);
        if (hostFile == null) {
            return EACCES;
        }

        boolean readable = accessMode == O_RDONLY || accessMode == O_RDWR;
        boolean writable = accessMode == O_WRONLY || accessMode == O_RDWR;
        boolean create = (flags & O_CREAT) != 0;
        boolean truncate = (flags & O_TRUNC) != 0;
        boolean append = (flags & O_APPEND) != 0;
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
                return EISDIR;
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
            return addOpenFile(new OpenFile(hostFile, channel, readable, writable, append));
        } catch (IOException | SecurityException exception) {
            return EACCES;
        }
    }

    /// Reads bytes from guest stdin or an open host file into guest memory.
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

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (fileDescriptor != 0 && (openFile == null || !openFile.readable())) {
            return EBADF;
        }

        try {
            byte[] buffer = new byte[(int) length];
            int count = fileDescriptor == 0
                    ? in.read(buffer)
                    : openFile.channel().read(ByteBuffer.wrap(buffer));
            if (count < 0) {
                return 0;
            }

            memory.writeBytes(address, buffer, 0, count);
            return count;
        } catch (IOException exception) {
            throw new RiscVException("Guest read syscall failed", exception);
        }
    }

    /// Writes guest memory bytes to stdout, stderr, or an open host file.
    private long write(int fileDescriptor, long address, long length) {
        if (length < 0) {
            return EINVAL;
        }
        if (length == 0) {
            return 0;
        }

        OutputStream stream = outputStreamFor(fileDescriptor);
        try {
            byte[] bytes = memory.readBytes(address, length);
            if (stream != null) {
                stream.write(bytes);
                stream.flush();
            } else {
                @Nullable OpenFile openFile = openFile(fileDescriptor);
                if (openFile == null || !openFile.writable()) {
                    return EBADF;
                }
                writeOpenFile(openFile, bytes);
            }
            return bytes.length;
        } catch (IOException exception) {
            throw new RiscVException("Guest write syscall failed", exception);
        }
    }

    /// Reads bytes from guest stdin or an open host file into a guest `struct iovec` array.
    private long readv(int fileDescriptor, long iovecAddress, long iovecCount) {
        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (fileDescriptor != 0 && (openFile == null || !openFile.readable())) {
            return EBADF;
        }
        if (iovecCount < 0 || iovecCount > IOV_MAX) {
            return EINVAL;
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

    /// Writes buffers from a guest `struct iovec` array to stdout, stderr, or an open host file.
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

    /// Writes all bytes to an open host file.
    private static void writeOpenFile(OpenFile openFile, byte[] bytes) throws IOException {
        if (openFile.append()) {
            openFile.channel().position(openFile.channel().size());
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) {
            openFile.channel().write(buffer);
        }
    }

    /// Handles `close` for standard streams and guest-opened host files.
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
            openFile.channel().close();
            openFiles.set(index, null);
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
        if (isStandardFileDescriptor(fileDescriptor)) {
            return ESPIPE;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
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

    /// Writes a minimal Linux generic 64-bit `struct stat`.
    private long fstat(int fileDescriptor, long statAddress) {
        if (isStandardFileDescriptor(fileDescriptor)) {
            memory.clear(statAddress, STAT_SIZE);
            memory.writeLong(statAddress + STAT_INODE_OFFSET, fileDescriptor + 1L);
            memory.writeInt(statAddress + STAT_MODE_OFFSET, STANDARD_STREAM_STAT_MODE);
            memory.writeInt(statAddress + STAT_LINK_COUNT_OFFSET, 1);
            memory.writeInt(statAddress + STAT_BLOCK_SIZE_OFFSET, STANDARD_STREAM_BLOCK_SIZE);
            return 0;
        }

        @Nullable OpenFile openFile = openFile(fileDescriptor);
        if (openFile == null) {
            return EBADF;
        }

        try {
            long size = openFile.channel().size();
            int permissions = (openFile.readable() ? STAT_MODE_READ_ALL : 0)
                    | (openFile.writable() ? 0222 : 0);
            memory.clear(statAddress, STAT_SIZE);
            memory.writeLong(statAddress + STAT_INODE_OFFSET, fileDescriptor + 1L);
            memory.writeInt(statAddress + STAT_MODE_OFFSET, STAT_MODE_REGULAR_FILE | permissions);
            memory.writeInt(statAddress + STAT_LINK_COUNT_OFFSET, 1);
            memory.writeLong(statAddress + STAT_SIZE_OFFSET, size);
            memory.writeInt(statAddress + STAT_BLOCK_SIZE_OFFSET, STANDARD_STREAM_BLOCK_SIZE);
            memory.writeLong(statAddress + STAT_BLOCK_COUNT_OFFSET, (size + 511L) / 512L);
            return 0;
        } catch (IOException exception) {
            throw new RiscVException("Guest fstat syscall failed", exception);
        }
    }

    /// Handles tty-related ioctls used by common `isatty` and stdio setup paths.
    private long ioctl(int fileDescriptor, long request, long argument) {
        if (!isStandardFileDescriptor(fileDescriptor)) {
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

    /// Stores the guest clear-child-TID pointer and returns the fixed guest thread id.
    private long setTidAddress(long address) {
        clearChildTidAddress = address;
        return GUEST_PROCESS_ID;
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

    /// Sleeps for the requested Linux RISC-V 64-bit `struct timespec` duration.
    private long nanosleep(long requestAddress, long remainingAddress) {
        long seconds = memory.readLong(requestAddress + TIMESPEC_SECONDS_OFFSET);
        long nanoseconds = memory.readLong(requestAddress + TIMESPEC_NANOSECONDS_OFFSET);
        if (!isValidTimespec(seconds, nanoseconds)) {
            return EINVAL;
        }

        long totalNanoseconds = timespecToSaturatedNanoseconds(seconds, nanoseconds);
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

    /// Reports a deterministic single-CPU affinity mask for static libc queries.
    private long schedGetaffinity(long processId, long cpuSetSize, long cpuSetAddress) {
        if (cpuSetSize < MINIMUM_CPU_AFFINITY_MASK_SIZE) {
            return EINVAL;
        }

        memory.clear(cpuSetAddress, cpuSetSize);
        memory.writeLong(cpuSetAddress, 1);
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

    /// Writes a Linux RISC-V 64-bit `struct timespec` for common clock ids.
    private long clockGettime(long clockId, long timespecAddress) {
        if (!isSupportedClock(clockId)) {
            return EINVAL;
        }

        if (isRealtimeClock(clockId)) {
            writeTimespecFromInstant(timespecAddress, clock.instant());
        } else {
            writeTimespecFromDuration(timespecAddress, Duration.between(clockStartInstant, clock.instant()));
        }
        return 0;
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
    private long prctl(long option, long argument2, long argument3, long argument4, long argument5) {
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
            return getTidAddress(argument2, argument3, argument4, argument5);
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
    private long getTidAddress(long address, long argument3, long argument4, long argument5) {
        if (!unusedArgumentsAreZero(argument3, argument4, argument5)) {
            return EINVAL;
        }
        memory.writeLong(address, clearChildTidAddress);
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

    /// Resolves a guest path below the configured host root or returns null for escapes.
    private @Nullable TruffleFile resolveHostFile(String guestPath) {
        @Nullable TruffleFile root = currentHostRoot();
        if (root == null) {
            return null;
        }
        if (guestPath.indexOf('\\') >= 0 || guestPath.indexOf(':') >= 0) {
            return null;
        }

        String relativePath = guestPath;
        while (relativePath.startsWith("/")) {
            relativePath = relativePath.substring(1);
        }

        try {
            TruffleFile hostFile = root.resolve(relativePath).normalize();
            return hostFile.startsWith(root) ? hostFile : null;
        } catch (InvalidPathException exception) {
            return null;
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

    /// Adds an open host file to the guest file descriptor table.
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

    /// Returns an open host file for a guest file descriptor.
    private @Nullable OpenFile openFile(int fileDescriptor) {
        int index = openFileIndex(fileDescriptor);
        if (index < 0 || index >= openFiles.size()) {
            return null;
        }
        return openFiles.get(index);
    }

    /// Returns true when a file descriptor refers to a standard stream or open host file.
    private boolean isOpenFileDescriptor(int fileDescriptor) {
        return isStandardFileDescriptor(fileDescriptor) || openFile(fileDescriptor) != null;
    }

    /// Returns Linux status flags for a standard stream or open host file.
    private long statusFlagsFor(int fileDescriptor) {
        if (fileDescriptor == 0) {
            return O_RDONLY;
        }
        if (fileDescriptor == 1 || fileDescriptor == 2) {
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
        if (fileDescriptor == 1) {
            return out;
        }
        if (fileDescriptor == 2) {
            return err;
        }
        return null;
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

    /// Describes a host file opened through a guest file descriptor.
    private record OpenFile(
            /// The resolved host path backing the guest file descriptor.
            TruffleFile path,

            /// The host file channel.
            SeekableByteChannel channel,

            /// Whether the guest file descriptor permits reads.
            boolean readable,

            /// Whether the guest file descriptor permits writes.
            boolean writable,

            /// Whether writes append to the current end of file.
            boolean append) {
    }
}
