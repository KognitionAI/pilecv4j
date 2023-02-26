#pragma once

#include <malloc.h>
#include <stdint.h>

#include "log.h"

#define KOG_PYTORCH_PYTHON_NAME "PyTorch"

namespace pilecv4j {
namespace python {
  typedef uint64_t (*get_image_source)(uint64_t ptref);
  class ImageSource;

  class JavaHandle {
    get_image_source getImageSourceCb;
    const char** modelLabels;
    int numLabels;

  public:
    inline JavaHandle(get_image_source cb) : getImageSourceCb(cb), modelLabels(nullptr), numLabels(0) {
      log(TRACE, "Instantiating PyTorch(callback=%ld)", (uint64_t)cb);
    };

    static void set(JavaHandle* instance);

    inline static void freeModelLabels(const char** modelLabels, int numLabels) {
      if (modelLabels) {
        for (int i = 0; i < numLabels; i++)
          if (modelLabels[i])
            free((void*)(modelLabels[i]));
        delete [] modelLabels;
      }
    }

    inline int getNumLabels() { return numLabels; }
    inline const char* getModelLabel(int index) {
      return (index < 0 || index >= numLabels) ? nullptr :  modelLabels[index];
    }

    inline ~JavaHandle() {
      log(TRACE, "Deleting PyTorch(callback=%ld)", static_cast<long>( (uint64_t)((void*)getImageSourceCb)));
      freeModelLabels(modelLabels, numLabels);
    }

    void setModelLabels(const char** nml, int pnumLabels) {
      freeModelLabels(modelLabels,numLabels);
      modelLabels = nml;
      numLabels = pnumLabels;
    }

    static inline const char** emptyModelLabels(int sz) {
      const char** nlist = new const char*[sz];
      for (int i = 0; i < sz; i++)
        nlist[i] = nullptr;
      return nlist;
    }

    ImageSource* getImageSource();

  };

  JavaHandle* convertPyTorch();
}
}
