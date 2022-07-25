//#include <cstdint>
//#include <opencv2/core/mat.hpp>
//#include <opencv2/cudaoptflow.hpp>
//#include <opencv2/cudaimgproc.hpp>
//#include <opencv2/cudaarithm.hpp>
//#include "common/jfloats.h"
//#includecommon/ "kog_exports.h"
//
//extern "C" {
//  KAI_EXPORT uint64_t GpuMat_create(uint64_t native);
//  KAI_EXPORT void GpuMat_destroy(uint64_t gpuMatL);
//  KAI_EXPORT uint64_t Gpu_createSparsePyrLKOpticalFlowEngine();
//  KAI_EXPORT void Gpu_destroySparsePyrLKOpticalFlowEngine(uint64_t ptrL);
//  // default values are for documentation purposes.
//  KAI_EXPORT uint64_t Gpu_createGoodFeaturesToTrackDetector(int32_t srcType, int32_t maxCorners = 1000,
//      float64_t qualityLevel = 0.01, float64_t minDistance = 0.0,
//      int32_t blockSize = 3, bool useHarrisDetector = false, float64_t harrisK = 0.04);
//  KAI_EXPORT void Gpu_destroyGoodFeaturesToTrackDetector(uint64_t ptrL);
//  KAI_EXPORT void Gpu_GoodFeaturesToTrackDetector_detect(uint64_t detectorPtrL,uint64_t imageL, uint64_t cornersL, uint64_t maskL);
//
//  KAI_EXPORT void Gpu_calcSparsePyrLKOpticalFlow(uint64_t ptrEngineL, uint64_t prevImgL, uint64_t nextImgL,
//      uint64_t prevPtsL, uint64_t nextPtsL,
//      uint64_t statusL,
//      uint64_t errL);
//}
//
//uint64_t GpuMat_create(uint64_t matNative) {
//  cv::Mat* mat = (cv::Mat*) matNative;
//  return (uint64_t) (new cv::cuda::GpuMat(mat));
//}
//
//void GpuMat_destroy(uint64_t gpuMatL) {
//  cv::cuda::GpuMat* gpuMat = (cv::cuda::GpuMat*) gpuMatL;
//  delete gpuMat;
//}
//
//uint64_t Gpu_createSparsePyrLKOpticalFlowEngine(int32_t winSize, int32_t maxLevel, int32_t iters) {
//  cv::Ptr<cv::cuda::SparsePyrLKOpticalFlow> ret = cv::cuda::SparsePyrLKOpticalFlow::create(
//              cv::Size(winSize, winSize), maxLevel, iters);
//  return (uint64_t)(new cv::Ptr<cv::cuda::SparsePyrLKOpticalFlow>(ret));
//}
//
//void Gpu_destroySparsePyrLKOpticalFlowEngine(uint64_t ptrL) {
//  if (ptrL != 0L) {
//    cv::Ptr<cv::cuda::SparsePyrLKOpticalFlow>* ptr = (cv::Ptr<cv::cuda::SparsePyrLKOpticalFlow>*)ptrL;
//    delete ptr;
//  }
//}
//
//uint64_t Gpu_createGoodFeaturesToTrackDetector(int32_t srcType, int32_t maxCorners, float64_t qualityLevel, float64_t minDistance,
//    int32_t blockSize, bool useHarrisDetector, float64_t harrisK) {
//  cv::Ptr<cv::cuda::CornersDetector> ret = cv::cuda::createGoodFeaturesToTrackDetector(srcType,  maxCorners, qualityLevel, minDistance, blockSize, useHarrisDetector, harrisK);
//  return (uint64_t) (new cv::Ptr<cv::cuda::CornersDetector>(ret));
//}
//
//void Gpu_destroyGoodFeaturesToTrackDetector(uint64_t ptrL) {
//  if (ptrL != 0L) {
//    cv::Ptr<cv::cuda::CornersDetector>* ptr = (cv::Ptr<cv::cuda::CornersDetector>*)ptrL;
//    delete ptr;
//  }
//}
//
//void Gpu_GoodFeaturesToTrackDetector_detect(uint64_t detectorPtrL, uint64_t imageL, uint64_t cornersL, uint64_t maskL) {
//  cv::Ptr<cv::cuda::CornersDetector>* detectorPtr = (cv::Ptr<cv::cuda::CornersDetector>*)detectorPtrL;
//  cv::cuda::GpuMat* image = (cv::cuda::GpuMat*)(imageL);
//  cv::cuda::GpuMat* corners = (cv::cuda::GpuMat*)(cornersL);
//  cv::cuda::GpuMat* mask = maskL == 0L ? cv::noArray() : (cv::cuda::GpuMat*)(maskL);
//  (*detectorPtr)->detect((*image),(*corners), (*mask));
//}
//
//
////void Gpu_calcSparsePyrLKOpticalFlow(uint64_t ptrEngineL, InputArray prevImg, InputArray nextImg,
////                      InputArray prevPts, InputOutputArray nextPts,
////                      OutputArray status,
////                      OutputArray err
