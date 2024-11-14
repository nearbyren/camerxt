#include <jni.h>
#include <string>
#include <android/asset_manager_jni.h>
#include <android/bitmap.h>
#include <android/log.h>
#include <seeta/FaceDetector.h>
#include <seeta/FaceLandmarker.h>
#include <seeta/FaceAntiSpoofing.h>
#include <seeta/Common/Struct.h>
//#include "tools.h"
#include "seetaface.hpp"
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/opencv.hpp>


using namespace seeta::SEETA_FACE_DETECTOR_NAMESPACE_VERSION;

using namespace std;
using namespace cv;

#define ASSERT(status, ret)     if (!(status)) { return ret; }
#define ASSERT_FALSE(status)    ASSERT(status, false)

static cv::Mat image;
static cv::Mat cimage;
static Seetaface seetaNet;
static SeetaImageData simage;
static SeetaRect sbox;
static std::vector<SeetaPointF> points5;
static std::vector<SeetaPointF> points68;
//static Seetaface seetaNet;
static int init=1;

//#define LOG_TAG "YLZ_seetaface"
//#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,LOG_TAG,__VA_ARGS__) // 定义LOGI类型
//#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR,LOG_TAG,__VA_ARGS__) // 定义LOGE类型

bool BitmapToMatrix(JNIEnv * env, jobject obj_bitmap, cv::Mat & matrix) {
    void * bitmapPixels;                                            // Save picture pixel data
    AndroidBitmapInfo bitmapInfo;                                   // Save picture parameters

    ASSERT_FALSE( AndroidBitmap_getInfo(env, obj_bitmap, &bitmapInfo) >= 0);        // Get picture parameters
    ASSERT_FALSE( bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888
                  || bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGB_565 );          // Only ARGB? 8888 and RGB? 565 are supported
    ASSERT_FALSE( AndroidBitmap_lockPixels(env, obj_bitmap, &bitmapPixels) >= 0 );  // Get picture pixels (lock memory block)
    ASSERT_FALSE( bitmapPixels );

    if (bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC4, bitmapPixels);    // Establish temporary mat
        tmp.copyTo(matrix);                                                         // Copy to target matrix
    } else {
        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC2, bitmapPixels);
        cv::cvtColor(tmp, matrix, cv::COLOR_BGR5652RGB);
    }

    //convert RGB to BGR
    cv::cvtColor(matrix,matrix,cv::COLOR_RGB2BGR);

    AndroidBitmap_unlockPixels(env, obj_bitmap);            // Unlock
    return true;
}



bool MatrixToBitmap(JNIEnv * env, cv::Mat & matrix, jobject obj_bitmap) {
    void * bitmapPixels;                                            // Save picture pixel data
    AndroidBitmapInfo bitmapInfo;                                   // Save picture parameters
    ASSERT_FALSE( AndroidBitmap_getInfo(env, obj_bitmap, &bitmapInfo) >= 0);        // Get picture parameters
    ASSERT_FALSE( bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888
                  || bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGB_565 );          // Only ARGB? 8888 and RGB? 565 are supported
    ASSERT_FALSE( matrix.dims == 2
                  && bitmapInfo.height == (uint32_t)matrix.rows
                  && bitmapInfo.width == (uint32_t)matrix.cols );                   // It must be a 2-dimensional matrix with the same length and width
    ASSERT_FALSE( matrix.type() == CV_8UC1 || matrix.type() == CV_8UC3 || matrix.type() == CV_8UC4 );
    ASSERT_FALSE( AndroidBitmap_lockPixels(env, obj_bitmap, &bitmapPixels) >= 0 );  // Get picture pixels (lock memory block)
    ASSERT_FALSE( bitmapPixels );
    if (bitmapInfo.format == ANDROID_BITMAP_FORMAT_RGBA_8888) {
        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC4, bitmapPixels);
        switch (matrix.type()) {
            case CV_8UC1:   cv::cvtColor(matrix, tmp, cv::COLOR_GRAY2RGBA);     break;
            case CV_8UC3:   cv::cvtColor(matrix, tmp, cv::COLOR_RGB2RGBA);      break;
            case CV_8UC4:   matrix.copyTo(tmp);                                 break;
            default:        AndroidBitmap_unlockPixels(env, obj_bitmap);        return false;
        }
    } else {
        cv::Mat tmp(bitmapInfo.height, bitmapInfo.width, CV_8UC2, bitmapPixels);
        switch (matrix.type()) {
            case CV_8UC1:   cv::cvtColor(matrix, tmp, cv::COLOR_GRAY2BGR565);   break;
            case CV_8UC3:   cv::cvtColor(matrix, tmp, cv::COLOR_RGB2BGR565);    break;
            case CV_8UC4:   cv::cvtColor(matrix, tmp, cv::COLOR_RGBA2BGR565);   break;
            default:        AndroidBitmap_unlockPixels(env, obj_bitmap);        return false;
        }
    }
    AndroidBitmap_unlockPixels(env, obj_bitmap);                // Unlock
    return true;
}

void draw_box(cv::Mat &img, SeetaRect& box) {
    // 绘制单个人脸框
    cv::rectangle(img, cv::Point2i{box.x, box.y}, cv::Point2i{box.width+box.x, box.height+box.y}, cv::Scalar(0, 255, 255), 3, 8, 0);
}

void draw_points(cv::Mat &img, std::vector<SeetaPointF> &pts) {
    // 绘制特征点
    cv::Scalar color=cv::Scalar(225, 0, 225);
    if(pts.size()==68){
        color=cv::Scalar(225, 0, 0);
    }
    for (int j = 0; j < pts.size().; ++j) {
        cv::circle(img, cv::Point2d(pts[j].x, pts[j].y), 2, color, -1, 8,0);
    }
}

//com.example.testcamera-+++++++
extern "C" JNIEXPORT jboolean
Java_com_example_camerx_SeetaFace_loadModel(JNIEnv *env,jobject thiz,jstring cstapath,jobjectArray string_array) {
    //模型初始化
    const char *modelpath = env->GetStringUTFChars(cstapath, 0); //jstring 转char*
    seetaNet.Init_face(modelpath);  //加载模型
    jint strlength = env->GetArrayLength(string_array);   //string_array 为要加载模型名称的字符串数组
    for (int i = 0; i < strlength; ++i) {
        jstring str = static_cast<jstring>(env->GetObjectArrayElement(string_array, i));
        const char* func = env->GetStringUTFChars(str,NULL);
        LOGI("获取java的参数:%s",func);
        string res = seetaNet.Init(modelpath,func);
        if(res !="ok"){
            LOGE("输入模型:%s名称不对",func);
            return JNI_FALSE;
        }
        else{
            LOGI("%s初始化成功",func);
        }
        env->ReleaseStringUTFChars(str,func);
    }

    env->ReleaseStringUTFChars(cstapath, modelpath);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_example_camerx_SeetaFace_InitLiveThreshold(JNIEnv *env,jobject thiz,jfloat clarity,jfloat reality){
    seetaNet.Init_liveThreshold(clarity,reality);
    LOGI("活体检测初始化成功,clarity=%f,reality=%f",clarity,reality);
}

//JNIEXPORT jintArray JNICALL Java_com_example_testcamera_SeetaFace_detectFace(JNIEnv *, jobject, jint, jint, jint, jlong);

extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_camerx_SeetaFace_detectFace(JNIEnv *env, jobject thiz, jobject input) {
   // LOGI("Java_com_example_testcamera_SeetaFace_detectFace start ");
    bool bit_cv = BitmapToMatrix(env,input,image);  //bitmap转cvMat,格式还为RGBA
    cv::cvtColor(image,cimage,COLOR_RGBA2BGR);  //
 //   LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 2 ");
    //cv图片转Seeta图片
    simage.width = cimage.cols;
    simage.height = cimage.rows;
    simage.channels = cimage.channels();
    simage.data = cimage.data;
    SeetaFaceInfoArray faces = seetaNet.detect_face(simage); //调用人脸检测
    if (faces.size<=0){
        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 3 ");
        return nullptr;
    }else{
        auto face = faces.data[0];
        sbox = face.pos;
        //        float score = face.score;@
        int x1 = int(sbox.x), y1 = int(sbox.y), width = int(sbox.width), height = int(sbox.height);
        int bbox[4] = {x1, y1, width, height};
        jintArray boxInfo = env->NewIntArray(4);
        env->SetIntArrayRegion(boxInfo, 0, 4, bbox);
        return boxInfo;
    }
}
/*
extern "C" JNIEXPORT jintArray JNICALL
Java_com_example_testcamera_SeetaFace_detectFace(JNIEnv *env, jobject thiz, jlong  matdata) {
//Java_com_example_testcamera_SeetaFace_detectFace(JNIEnv *env, jobject thiz, jint cols ,jint rows,jint channels,jlong  ldata) {
   // std::map<int, std::vector<int> > *newMap = (std::map<int, std::vector<int> > *) ldata;
LOGI("Java_com_example_testcamera_SeetaFace_detectFace start ");
    Mat* cimage = (Mat*)matdata ;
//    unsigned char a1=  ucdata[0];
//    unsigned char a2=  ucdata[1];
//    unsigned char a3=  ucdata[2];
//    unsigned char a4=  ucdata[3];
LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 1 ");
  //  const Mat c2 = cimage->clone();
 //   Mat cimage = c1->clone();
    simage.width = cimage->cols;
    simage.height = cimage->rows;
    simage.channels = cimage->channels();
    simage.data = cimage->data;
LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 2 0x%llx",cimage->data);
//    jbyte* jbdata = env->GetByteArrayElements(jdata, NULL);
//    int ndatalen = (int)env->GetArrayLength(jdata);
//    if (jbdata != NULL) {
//        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 7");
//        simage.data = new unsigned char[ndatalen];
//        memcpy(simage.data, jbdata, ndatalen);
//        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 8");
//        env->ReleaseByteArrayElements(jdata, jbdata, JNI_ABORT);
//        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 9");
//    }
 try
 {
        SeetaFaceInfoArray faces = seetaNet.detect_face(simage); //调用人脸检测
        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 3");
        if (faces.size==0){
             return nullptr;
        }
        if(faces.size>0) {
            auto face = faces.data[0];
            sbox = face.pos;
            //        float score = face.score;@
            int x1 = int(sbox.x), y1 = int(sbox.y), width = int(sbox.width), height = int(sbox.height);
            int bbox[4] = {x1, y1, width, height};
            jintArray boxInfo = env->NewIntArray(4);
            env->SetIntArrayRegion(boxInfo, 0, 4, bbox);
            return boxInfo;
        }
 }
 catch(...){
    LOGI("nativeDetect caught unknown exception");
    jclass je = env->FindClass("java/lang/Exception");
    env->ThrowNew(je, "Unknown exception in JNI code DetectionBasedTracker.nativeDetect()");
 }

//catch(const cv::Exception& e){
//LOGI("nativeCreateObject caught cv::Exception: %s", e.what());
//jclass je = env->FindClass("org/opencv/core/CvException");
//if(!je)je = env->FindClass("java/lang/Exception");
//env->ThrowNew(je, e.what());
//}
}
 */
/*
//Java_com_example_testcamera_SeetaFace_detectFace(JNIEnv *env, jobject thiz, jobject input) {
Java_com_example_testcamera_SeetaFace_detectFace(JNIEnv *env, jobject thiz, jobject input) {

//    bool bit_cv = BitmapToMatrix(env,input,image);  //bitmap转cvMat,格式还为RGBA
//    cv::cvtColor(image,cimage,COLOR_RGBA2BGR);  //
//    //cv图片转Seeta图片
//    simage.width = cimage.cols;
//    simage.height = cimage.rows;
//    simage.channels = cimage.channels();
//    simage.data = cimage.data;
    LOGI("Java_com_example_testcamera_SeetaFace_detectFace start");
    jclass clazz_TestNative = env->GetObjectClass( input);
    jfieldID cols = env->GetFieldID(clazz_TestNative, "cols", "I");
    jfieldID rows = env->GetFieldID(clazz_TestNative, "rows", "I");
 //   jfieldID channels = env->GetFieldID(clazz_TestNative, "channels", "I");
 //jmethodID mid = env.GetMethodID(classtring, "getBytes", "(Ljava/lang/String;)[B");
 LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 1");
    jmethodID f_channels = env->GetMethodID( clazz_TestNative, "channels", "(V)I");

LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 2");
 //  jfieldID data = env->GetFieldID(clazz_TestNative, "data","Ljava/nio/ByteBuffer;");
   jfieldID data = env->GetFieldID(clazz_TestNative, "data", "[B");
LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 3");
   // jobject odata = env->GetObjectField(input, data);
LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 4");
    ///////////////////////////////////////////////////////
    //java public int Send(char buffer[],int length)  c++ int send(unsigned char *buf, int len)
    //首先分析java的参数char buffer[ ]，它在jni中的对应类型为jcharArray，说明从java传到jni的参数类型就是jcharArray
//    jclass cls = env->GetObjectClass(obj);
//jfieldID fid = env->GetFieldID(cls, "data","Ljava/nio/ByteBuffer;");
//jobject bar = env->GetObjectField(obj, fid);
//pImageData->data= (MByte*)env->GetDirectBufferAddress(bar);
    ///////////////////////////////////////////////////////

    int ncols = (int)env->GetIntField(input, cols);
    int nrows = (int)env->GetIntField(input, rows);
LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 5");
 //   int nchannels = env->CallObjectMethod(input, f_channels);
 int nchannels = (int)env->CallIntMethod(input, f_channels);
LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 6");
    //jmethodID create_audio_track_mid = env->GetMethodID(player_class, "createAudioTrack","(II)Landroid/media/AudioTrack;");
 //jbyteArray barr = (jbyteArray) env.CallObjectMethod(class, mid, strencode);

    jbyteArray ndata = (jbyteArray)env->GetObjectField(input, data);
    ///////////////////////////////////////////////////////////////////////
    int ndatalen = (int)env->GetArrayLength(ndata);
    jbyte* jbdata = env->GetByteArrayElements(ndata, NULL);
    if (jbdata != NULL) {
        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 7");
        simage.data = new unsigned char[ndatalen];
        memcpy(simage.data, jbdata, ndatalen);
        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 8");
        env->ReleaseByteArrayElements(ndata, jbdata, JNI_ABORT);
        LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 9");
    }

    ///////////////////////////////////////////////////////////////////////
  //  jbyte* bBuffer = env->GetByteArrayElements(env,jBuffer,0);
  //  unsigned char* buf=(unsigned char*)bBuffer;

//    jbyte*  ndata = env->GetIntField(clazz_TestNative, data);
    //LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 7");

    simage.width = ncols;
    simage.height = nrows;
    simage.channels = nchannels;
 //   simage.data = ndata. ;

    SeetaFaceInfoArray faces = seetaNet.detect_face(simage); //调用人脸检测
    if (faces.size==0){
        return nullptr;
    }
    if(faces.size>0) {
        auto face = faces.data[0];
        sbox = face.pos;
        //        float score = face.score;@
        int x1 = int(sbox.x), y1 = int(sbox.y), width = int(sbox.width), height = int(sbox.height);
        int bbox[4] = {x1, y1, width, height};
        jintArray boxInfo = env->NewIntArray(4);
        env->SetIntArrayRegion(boxInfo, 0, 4, bbox);
        return boxInfo;
    }
}
*/

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_camerx_SeetaFace_landmark(JNIEnv *env, jobject thiz, jint mark_num) {
    if(mark_num==5)
        points5 = seetaNet.detect_land5(simage, sbox);
        else if(mark_num==68){
        points68 = seetaNet.detect_land68(simage, sbox);
    }
    else{
        return JNI_FALSE;
    }

    return JNI_TRUE;
}


extern "C" JNIEXPORT void JNICALL
Java_com_example_camerx_SeetaFace_detectDraw(JNIEnv *env, jobject thiz,jboolean drawOfbox,jboolean drawOfpts5,jboolean drawOfpts68,jobject bitmapOut) {
    cv::Mat imageOut;
    cimage.copyTo(imageOut);
    if(drawOfbox){
        draw_box(imageOut, sbox);  //人脸框与五官画图
    }
    if(drawOfpts5){
         draw_points(imageOut, points5);  //五官画图
    }
    if(drawOfpts68){
        draw_points(imageOut, points68);  //五官画图
    }
    cv::cvtColor(imageOut,imageOut,COLOR_BGR2RGBA);
    LOGI("Java_com_example_testcamera_SeetaFace_detectDraw 000 cols=%d rows=%d",imageOut.cols,imageOut.rows);
    bool cv_bit = MatrixToBitmap(env,imageOut,bitmapOut);
    LOGI("Java_com_example_testcamera_SeetaFace_detectDraw %d ",(int)cv_bit);
}

//todo 性别预测
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_camerx_SeetaFace_detectGender(JNIEnv *env, jobject thiz) {
char* gender= seetaNet.predict_gender(simage,points5);
return env->NewStringUTF(gender);
}
//todo 年龄预测
extern "C" JNIEXPORT jint JNICALL
Java_com_example_camerx_SeetaFace_detectAge(JNIEnv *env, jobject thiz) {
int age= seetaNet.predict_age(simage,points5);
return (jint)age;
}

//扩展的 1 detect  2 point5 4 point68 8 sex  16 age
extern "C" JNIEXPORT jint JNICALL
Java_com_example_camerx_SeetaFace_detectFaceEx(JNIEnv *env, jobject thiz, jobject input,jint iflag) {//,jobject bitmapOut
    cv::Mat timage ,tcimage;
    SeetaImageData tsimage;
    SeetaRect tsbox ;
    bool bit_cv = BitmapToMatrix(env,input,timage);  //bitmap转cvMat,格式还为RGBA
    cv::cvtColor(timage,tcimage,COLOR_RGBA2BGR);  //
  //  LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 2 ");
    //cv图片转Seeta图片
    tsimage.width = tcimage.cols;
    tsimage.height = tcimage.rows;
    tsimage.channels = tcimage.channels();
    tsimage.data = tcimage.data;
    SeetaFaceInfoArray faces = seetaNet.detect_face(tsimage); //调用人脸检测
    if (faces.size<=0){
  //      LOGI("Java_com_example_testcamera_SeetaFace_detectFace start 3 ");
        return -1;
    }else{
        auto face = faces.data[0];
        tsbox = face.pos;
        //        float score = face.score;@
      //  int x1 = int(tsbox.x), y1 = int(tsbox.y), width = int(tsbox.width), height = int(tsbox.height);
    //    int bbox[4] = {x1, y1, width, height};
     //   jintArray boxInfo = env->NewIntArray(4);
     //   env->SetIntArrayRegion(boxInfo, 0, 4, bbox);
       // return boxInfo;
    }

     //       points68 = seetaNet.detect_land68(simage, sbox);
    int age = 0;
   {
      //  cv::Mat imageOut;
     //   tcimage.copyTo(imageOut);
        iflag |= 1 ;
        if((iflag &1 ) > 0){
            draw_box(tcimage, tsbox);  //人脸框与五官画图
        }

        if((iflag &2 ) > 0){
            std::vector<SeetaPointF>  tpoints5 = seetaNet.detect_land5(tsimage, tsbox);
            if((iflag &16 ) > 0){
                age = seetaNet.predict_age(tsimage,tpoints5)*2;
            }
            if((iflag &8 ) > 0){
                  int sex = seetaNet.predict_gender_int(tsimage,tpoints5);
                  age += sex ;
            }
            draw_points(tcimage, tpoints5);  //五官画图
        }else if((iflag & 24) > 0 ){
            std::vector<SeetaPointF>  tpoints5 = seetaNet.detect_land5(tsimage, tsbox);
            if((iflag &16 ) > 0){
                age = seetaNet.predict_age(tsimage,tpoints5)*2;
            }
            if((iflag &8 ) > 0){
                   int sex = seetaNet.predict_gender_int(tsimage,tpoints5);
                   age += sex ;
            }
        }
        if((iflag &4 ) > 0){
            std::vector<SeetaPointF>  points68 = seetaNet.detect_land68(tsimage, tsbox);
            draw_points(tcimage, points68);  //五官画图
       }

    //    LOGI("Java_com_example_testcamera_SeetaFace_detectDraw 000 cols=%d rows=%d",imageOut.cols,imageOut.rows);
        bool cv_bit = MatrixToBitmap(env,tcimage,input);
    //    LOGI("Java_com_example_testcamera_SeetaFace_detectDraw %d ",(int)cv_bit);
   }
   return age;
}
