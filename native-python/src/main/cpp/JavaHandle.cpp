#include <JavaHandle.h>

namespace pilecv4j {
namespace python {
  ImageSource* JavaHandle::getImageSource() {
    return (ImageSource*)(getImageSourceCb((uint64_t) this));
  }

  static JavaHandle* instance;
  void JavaHandle::set(JavaHandle* pinstance) {
    instance = pinstance;
  }

  JavaHandle* convertPyTorch() {
    if (isEnabled(TRACE))
      log(TRACE, "converting PyTorch instance at %ld to python", static_cast<long>((uint64_t)instance));
    return instance;
  }
}
}
