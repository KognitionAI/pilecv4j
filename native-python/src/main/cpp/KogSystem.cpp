#include "KogSystem.h"

namespace pilecv4j {
  ImageSource* KogSystem::getImageSource() {
    return (ImageSource*)(getImageSourceCb((uint64_t) this));
  }

  static KogSystem* instance;
  void KogSystem::set(KogSystem* pinstance) {
    instance = pinstance;
  }

  KogSystem* convertPyTorch() {
    log(TRACE, "converting PyTorch instance at %ld to python", (long)instance);
    return instance;
  }
}
