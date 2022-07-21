#include "KogSystem.h"

namespace pilecv4j {
namespace python {
  ImageSource* KogSystem::getImageSource() {
    return (ImageSource*)(getImageSourceCb((uint64_t) this));
  }

  static KogSystem* instance;
  void KogSystem::set(KogSystem* pinstance) {
    instance = pinstance;
  }

  KogSystem* convertPyTorch() {
    log(TRACE, "converting PyTorch instance at %ld to python", static_cast<long>((uint64_t)instance));
    return instance;
  }
}
}
