#pragma once

#include <chrono>
#include <limits>
#include <thread>

namespace ai {
namespace kognition {
namespace pilecv4j {

template<typename>
struct is_chrono_duration : std::false_type
{};

template<typename Rep, typename Period>
struct is_chrono_duration<std::chrono::duration<Rep, Period>> : std::true_type
{};

template<typename T = std::chrono::milliseconds, bool = is_chrono_duration<T>::value>
class EndTime;

template<typename T>
class EndTime<T, true>
{
public:
  explicit EndTime(const T duration)
    : m_startTime(std::chrono::steady_clock::now()), m_totalWaitTime(duration)
  {  }

  EndTime() = default;
  EndTime(const EndTime& right) = delete;
  ~EndTime() = default;

  void set(const T duration)
  {
    m_startTime = std::chrono::steady_clock::now();
    m_totalWaitTime = duration;
  }

  bool isTimePast() const
  {
    if (m_totalWaitTime == m_infinity)
      return false;

    const auto now = std::chrono::steady_clock::now();

    return ((now - m_startTime) >= m_totalWaitTime);
  }

  T getTimeLeft() const
  {
    if (m_totalWaitTime == m_infinity)
      return m_infinity;

    const auto now = std::chrono::steady_clock::now();

    const auto left = ((m_startTime + m_totalWaitTime) - now);

    if (left < T::zero())
      return T::zero();

    return std::chrono::duration_cast<T>(left);
  }

  void setExpired() { m_totalWaitTime = T::zero(); }

  void setInfinite() { m_totalWaitTime = m_infinity; }

  bool isInfinite() const { return (m_totalWaitTime == m_infinity); }

  T getInitialTimeoutValue() const { return m_totalWaitTime; }

  std::chrono::steady_clock::time_point getStartTime() const { return m_startTime; }

private:
  std::chrono::steady_clock::time_point m_startTime;
  T m_totalWaitTime = T::zero();

  const T m_infinity = T::max();
};

}
}
}

