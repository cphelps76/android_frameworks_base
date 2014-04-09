#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <sys/types.h>
#include <sys/socket.h>

#include <unistd.h>
#include <pthread.h>
#include <stdlib.h>
#include <fcntl.h>
#include <string.h>
#include <sys/stat.h>
#include <time.h>
#include <signal.h>
#include "semaphore.h"
#include <assert.h>
#include <errno.h>
#include <cutils/log.h>
#include <cutils/sockets.h>
#define SYS_WRITE "sys_write"
#define LOGV ALOGV
#define LOGD ALOGD
#define LOGI ALOGI
#define LOGW ALOGW
#define LOGE ALOGE


void send_remote_request(char *msg);

#ifdef __cplusplus
}
#endif