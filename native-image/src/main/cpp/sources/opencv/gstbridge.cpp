#include <opencv2/core/mat.hpp>
#include "imagemaker.h"

#include <memory>

struct GstMat : public cv::Mat {
  void* userdata;
  uint8_t* me;

  inline GstMat(int height, int width, int stride, void* data, uint8_t* newReturned, uint8_t* puserdata) :
      cv::Mat(height, width, CV_8UC3, data, stride), me(newReturned), userdata(puserdata) {  }
};

class ImageMaker : public ai::kognition::pilecv4j::ImageMaker {
public:
  virtual uint64_t makeImage(int height, int width, int stride, std::size_t extraDataSize, ai::kognition::pilecv4j::DataMapper* dataMapper){
    std::size_t totalSize = sizeof(GstMat) + extraDataSize + 8;
    uint8_t* objLocation = new uint8_t[totalSize];
    uint8_t* space = objLocation + totalSize - 8 - extraDataSize;
    void* ptr = space;
    std::size_t spaceForAlignedStruct = extraDataSize+8;
    uint8_t* userdata = (uint8_t*)std::align(8, extraDataSize, ptr, spaceForAlignedStruct);

    GstMat* rret = new ((void*)objLocation) GstMat(height, width, stride, dataMapper->mapData(userdata),objLocation, userdata);
    cv::Mat* cvmat = rret;
    return (uint64_t)cvmat;
  }

  virtual void* userdata(uint64_t im) {
    cv::Mat* cvmat = (cv::Mat*)im;
    GstMat* it = static_cast<GstMat*>(cvmat);
    return it->userdata;
  }
};

static ai::kognition::pilecv4j::ImageMaker* imaker = new ImageMaker();

extern "C" {

  uint64_t get_im_maker() {
    return (uint64_t)imaker;
  }

  void free_gstmat(uint64_t gstmat) {
    cv::Mat* cvmat = (cv::Mat*)gstmat;
    GstMat* it = static_cast<GstMat*>(cvmat);
    delete[] it->me;
  }

}
