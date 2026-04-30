// Copyright (c) 2026 Glavo
// SPDX-License-Identifier: MPL-2.0

package org.glavo.riscv.runtime;

import org.jetbrains.annotations.NotNullByDefault;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

/// Supplies realtime, elapsed, and monotonic host time values for guest syscalls.
@NotNullByDefault
public final class TimeSource {
    /// The number of nanoseconds in one second.
    private static final long NANOSECONDS_PER_SECOND = 1_000_000_000L;

    /// The realtime clock exposed by this source.
    private final Clock realtimeClock;

    /// The realtime instant captured when this source was created.
    private final Instant startInstant;

    /// Creates a time source backed by the supplied realtime clock.
    private TimeSource(Clock realtimeClock) {
        this.realtimeClock = realtimeClock;
        this.startInstant = realtimeClock.instant();
    }

    /// Creates a time source backed by the host UTC clock.
    public static TimeSource system() {
        return new TimeSource(Clock.systemUTC());
    }

    /// Creates a time source backed by a fixed realtime instant.
    public static TimeSource fixed(Instant instant) {
        return new TimeSource(Clock.fixed(instant, ZoneOffset.UTC));
    }

    /// Creates a fixed time source from nanoseconds since the Unix epoch.
    public static TimeSource fixedEpochNanoseconds(long epochNanoseconds) {
        return fixed(Instant.ofEpochSecond(
                epochNanoseconds / NANOSECONDS_PER_SECOND,
                epochNanoseconds % NANOSECONDS_PER_SECOND));
    }

    /// Returns the current realtime instant.
    public Instant realtimeInstant() {
        return realtimeClock.instant();
    }

    /// Returns the non-negative duration elapsed since this source was created.
    public Duration elapsedDuration() {
        Duration duration = Duration.between(startInstant, realtimeInstant());
        return duration.isNegative() ? Duration.ZERO : duration;
    }

    /// Returns the host monotonic nanosecond counter for wait bookkeeping.
    public long monotonicNanoseconds() {
        return System.nanoTime();
    }
}
