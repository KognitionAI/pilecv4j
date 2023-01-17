#pragma once

#ifdef TIMING
#define NANOS_PER_MILLI 1000000L
#define NANOS_PER_SECOND 1000000000L

#include <chrono>
#define TIME_OPEN(x) start##x = currentTimeNanos()
#define TIME_CAP(x) { dur##x += (currentTimeNanos() - start##x); count##x++; }
#define TIME_DISPLAY(name, x) fprintf(stderr, "   %s - time spent nanos/millis: %ld/%ld over %ld calls. calls per second: %.2f\n", \
    name, (long)dur##x, (long)(dur##x / NANOS_PER_MILLI), (long)count##x, (double)((double)count##x * (double)NANOS_PER_SECOND) / (double)dur##x )
#define TIME_DECL(x) \
  static uint64_t start##x = 0; \
  static uint64_t dur##x = 0; \
  static uint64_t count##x = 0

static const auto arbitraryTimeInThePast = std::chrono::steady_clock::now();

inline static uint64_t currentTimeNanos() {
  auto finish = std::chrono::steady_clock::now();
  return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::nanoseconds>(finish - arbitraryTimeInThePast).count());
}

class TimeGuard {
  uint64_t& startTG;
  uint64_t& durTG;
  uint64_t& countTG;

public:
  inline TimeGuard(uint64_t& start, uint64_t& dur, uint64_t& count) : startTG(start), durTG(dur), countTG(count) {
    TIME_OPEN(TG);
  }
  inline ~TimeGuard() {
    TIME_CAP(TG);
  }
};

#define TIME_GUARD(x) TimeGuard _time_guard(start##x, dur##x, count##x)

#else
#define TIME_OPEN(x)
#define TIME_CAP(x)
#define TIME_DISPLAY(name, x)
#define TIME_DECL(x)
#define TIME_GUARD(x)
#endif
