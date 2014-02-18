/* //device/libs/android_runtime/android_server_AlarmManagerService.cpp
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#define LOG_TAG "ActivityStack-JNI"
#define LOG_NDEBUG 0

#include "JNIHelp.h"
#include "jni.h"
#include <utils/Log.h>
#include <utils/misc.h>

#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include <stdlib.h>
#include <errno.h>
#include <unistd.h>
#include <linux/ioctl.h>
#include <linux/android_alarm.h>

#include "player.h"

namespace android {

static jint com_android_server_am_ActivityStack_disableFreeScaleMBX(JNIEnv* env, jobject obj)
{
    int ret = 0;
    ret = GL_2X_scale(1);
    
    ret = disable_freescale_MBX();

    return 0;
}

static jint com_android_server_am_ActivityStack_enableFreeScaleMBX(JNIEnv* env, jobject obj)
{
    int ret = 0;

    ret = wait_play_end();
    if(ret < 0)
    {
        return ret;	
    }

    ret = GL_2X_scale(0);

    ret = enable_freescale_MBX();

    return 0;
}


static JNINativeMethod sMethods[] = {
     /* name, signature, funcPtr */
    {"enableFreeScaleMBX", "()I", (void*)com_android_server_am_ActivityStack_enableFreeScaleMBX},
    {"disableFreeScaleMBX", "()I", (void*)com_android_server_am_ActivityStack_disableFreeScaleMBX},
};

int register_android_server_am_ActivityStack(JNIEnv* env)
{
    return jniRegisterNativeMethods(env, "com/android/server/am/ActivityStack",
                                    sMethods, NELEM(sMethods));
}

} /* namespace android */
