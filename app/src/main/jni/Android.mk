LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaQualityAssessor300
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaQualityAssessor300.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaAgePredictor600
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaAgePredictor600.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaAuthorize
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaAuthorize.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaEyeStateDetector200
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaEyeStateDetector200.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaFaceAntiSpoofingX600
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaFaceAntiSpoofingX600.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaFaceDetector600
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaFaceDetector600.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaFaceLandmarker600
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaFaceLandmarker600.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaGenderPredictor600
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaGenderPredictor600.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaMaskDetector200
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaMaskDetector200.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := SeetaPoseEstimation600
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libSeetaPoseEstimation600.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := tennis
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libtennis.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := omp
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libomp.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE:=opencv_java4
LOCAL_SRC_FILES:=$(TARGET_ARCH_ABI)/libopencv_java4.so
include $(PREBUILT_SHARED_LIBRARY)


include $(CLEAR_VARS)

LOCAL_MODULE    := testseetaface

#LOCAL_C_INCLUDES := $(LOCAL_PATH)/include

LOCAL_SRC_FILES := \
    seetaface_jni_1.cpp

LOCAL_C_INCLUDES += $(LOCAL_PATH)/seetaface6/include
LOCAL_C_INCLUDES += $(LOCAL_PATH)/opencv

#LOCAL_LDLIBS :=  -llog
LOCAL_LDLIBS := -L$(TARGET_ARCH_ABI) -lSeetaQualityAssessor300 \
-lSeetaAgePredictor600 -lSeetaAuthorize -lSeetaEyeStateDetector200 \
-lSeetaFaceAntiSpoofingX600 -lSeetaFaceDetector600 -lSeetaFaceLandmarker600 \
-lSeetaGenderPredictor600 -lSeetaMaskDetector200 -lSeetaPoseEstimation600 -ltennis -lopencv_java4 \
-llog -landroid -ldl -ljnigraphics


include $(BUILD_SHARED_LIBRARY)