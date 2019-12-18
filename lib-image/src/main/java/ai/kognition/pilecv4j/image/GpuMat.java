package ai.kognition.pilecv4j.image;

public class GpuMat
// implements QuietCloseable
{
    // private boolean skipCloseOnceForReturn = false;
    // private boolean deletedAlready = false;
    //
    // public final long nativeObj;
    //
    // GpuMat(final long cvMatNative) {
    // nativeObj = ImageAPI.GpuMat_create(cvMatNative);
    // }
    //
    // @Override
    // public void close() {
    // if(!skipCloseOnceForReturn) {
    // if(!deletedAlready) {
    // try {
    // ImageAPI.GpuMat_destroy(nativeObj);
    // } catch(final IllegalArgumentException e) {
    // throw new RuntimeException("Got an exception trying to free the native gpu mat.", e);
    // }
    // deletedAlready = true;
    // }
    // } else
    // skipCloseOnceForReturn = false; // next close counts.
    // }
    //
    // public GpuMat returnMe() {
    // // still hacky, and still efficient.
    // skipCloseOnceForReturn = true;
    // return this;
    // }
}
