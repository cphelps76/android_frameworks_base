LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES:= \
	system_key_server.c
	
LOCAL_SHARED_LIBRARIES := \
	libcutils \
	libutils \

LOCAL_MODULE:= system_key_server

include $(BUILD_EXECUTABLE)


