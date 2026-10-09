/*
 * FakeMutexGuard.h
 *
 *  Created on: Jul 21, 2022
 *      Author: jim
 */

#ifndef _FAKEMUTEXGUARD_H_
#define _FAKEMUTEXGUARD_H_

#include <atomic>
#include <thread>

namespace ai
{
namespace kognition
{
namespace pilecv4j
{

/**
 * This uses an atomic<bool> as a NON-recursive mutex. When the bool
 * has the value of 'false', it's considered "unlocked." Locking it
 * by instantiating a FakeMutexGuard will atomically transform it to
 * false.
 */
class FakeMutextGuard {
  // false means it's free. true means I need to wait.
  std::atomic<bool>& fmut;
  bool didIt;

public:
  inline FakeMutextGuard(std::atomic<bool>& pfmut, bool doIt = true) : fmut(pfmut) {
    if (doIt) {
      bool mfalse = false;
      while(!fmut.compare_exchange_weak(mfalse, true, std::memory_order_acquire, std::memory_order_relaxed)) {
        mfalse = false;
        // back off so we don't peg a core busy-waiting under contention
        std::this_thread::yield();
      }
      didIt = true;
    } else
      didIt = false;
  }

  inline ~FakeMutextGuard() {
    if (didIt) {
      // we hold the "lock" so the value is known to be true; a plain
      // release store is sufficient (and cannot fail) to unlock it.
      fmut.store(false, std::memory_order_release);
    }
  }
};


}
}
} /* namespace pilecv4j */

#endif /* SRC_MAIN_CPP_UTILS_FAKEMUTEXGUARD_H_ */
