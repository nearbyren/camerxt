APP_ABI := armeabi-v7a,arm64-v8a
APP_PLATFORM := android-23
APP_STL := c++_shared
APP_CPPFLAGS := -fexceptions -frtti
#APP_CPPFLAGS := -fexceptions -fno-rtti
APP_CPPFLAGS +=-std=c++11
APP_CPPFLAGS +=-fopenmp -static-openmp
