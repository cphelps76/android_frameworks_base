#include <stdio.h>
#include <stdlib.h>
#include <malloc.h>
#include <unistd.h>
#include <pthread.h>
#include <media/AudioRecord.h>
#include <media/AudioTrack.h>
#include <media/mediarecorder.h>
#include <system/audio.h>
#include <android/log.h>

#include "audio_global_cfg.h"
#include "mAlsa.h"

//#define LOG_TAG "LibAudioCtl"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
//#undef LOGD
//#undef LOGE
//#define LOGD printf
//#define LOGE printf
using namespace android;

#define CC_ALSA_HAS_MUTEX_LOCK              (1)

static AudioRecord* glpRecorder = NULL;
static AudioTrack* glpTracker = NULL;

#if CC_ALSA_HAS_MUTEX_LOCK == 1
static Mutex free_recorder_lock;
static Mutex free_tracker_lock;
static Mutex alsa_init_lock;
static Mutex alsa_uninit_lock;
#endif

static void FreeRecorder(void) {
#if CC_DISABLE_ALSA_MODULE == 0
#if CC_ALSA_HAS_MUTEX_LOCK == 1
  //  Mutex::Autolock _l(free_recorder_lock);
#endif

    if (glpRecorder != NULL) {
        glpRecorder->stop();
        //delete glpRecorder;
        //glpRecorder = NULL;
    }
#endif
}

static void FreeTracker(void) {
#if CC_DISABLE_ALSA_MODULE == 0
#if CC_ALSA_HAS_MUTEX_LOCK == 1
  //  Mutex::Autolock _l(free_tracker_lock);
#endif

    if (glpTracker != NULL) {
        glpTracker->stop();
        //delete glpTracker;
        //glpTracker = NULL;
    }
#endif
}

static void recorderCallback(int event, void* user, void *info) {
}

static void trackerCallback(int event, void* user, void *info) {
}

int mAlsaInit(int tm_sleep, int init_flag) {
#if CC_DISABLE_ALSA_MODULE == 0
    int tmp_ret;
    int record_rate = 48000;
    int track_rate = 48000;

    LOGD("Enter mAlsaInit function.\n");

#if CC_ALSA_HAS_MUTEX_LOCK == 1
   // Mutex::Autolock _l(alsa_init_lock);
#endif

    mAlsaUninit(0);

    if (init_flag & CC_FLAG_CREATE_RECORD) {
        LOGD("Start to create recorder.\n");

        LOGD("Recorder rate = %d.\n", record_rate);

        // create an AudioRecord object
        glpRecorder = new AudioRecord();
        if (glpRecorder == NULL) {
            LOGE("Error creating AudioRecord instance: new AudioRecord class failed.\n");
            return -1;
        }

        tmp_ret = glpRecorder->set(AUDIO_SOURCE_DEFAULT, record_rate, AUDIO_FORMAT_PCM_16_BIT, AUDIO_CHANNEL_IN_STEREO, 0, 
		  // (AudioRecord::record_flags) 0, // flags
                recorderCallback,// callback_t
                NULL,// void* user
                0, // notificationFrames,
                false, // threadCanCallJava
                0); // sessionId)

        tmp_ret = glpRecorder->initCheck();
        if (tmp_ret != NO_ERROR) {
            LOGE("Error creating AudioRecord instance: initialization check failed. status = %d\n", tmp_ret);
            FreeRecorder();
            return -1;
        }

        if (init_flag & CC_FLAG_START_RECORD) {
            glpRecorder->start();
        }
	
        LOGD(" create recorder finished.\n");
        if (init_flag & CC_FLAG_SOP_RECORD) {
            mAlsaStopRecorder();
            FreeRecorder();
            LOGD("stop recorder finish.\n");
            
        }        
    }

    if (init_flag & CC_FLAG_CREATE_TRACK) {
        LOGD("Start to create Tracker.\n");

        LOGD("Tracker rate = %d.\n", track_rate);

        glpTracker = new AudioTrack();
        if (glpTracker == 0) {
            LOGE("Error creating AudioTrack instance: new AudioTrack class failed.\n");
            FreeRecorder();
            return -1;
        }

        tmp_ret = glpTracker->set(AUDIO_STREAM_DEFAULT, track_rate, AUDIO_FORMAT_PCM_16_BIT, AUDIO_CHANNEL_OUT_STEREO, 0, // frameCount
                (audio_output_flags_t)0, // flags
                trackerCallback, 0, // user when callback
                0, // notificationFrames
                NULL, // shared buffer
                0);

        tmp_ret = glpTracker->initCheck();
        if (tmp_ret != NO_ERROR) {
            LOGE("Error creating AudioTrack instance: initialization check failed.status = %d\n", tmp_ret);
            FreeRecorder();
            FreeTracker();
            return -1;
        }

        if (init_flag & CC_FLAG_START_TRACK) {
            glpTracker->start();
        }

        LOGD("End to create Tracker.\n");
    }

    if (tm_sleep > 0)
        sleep(tm_sleep);

    LOGD("Exit mAlsaInit function sucess.\n");

    return 0;
#else
    return 0;
#endif
}

int mAlsaUninit(int tm_sleep) {
#if CC_DISABLE_ALSA_MODULE == 0
    LOGD("Enter mAlsaUninit function.\n");

#if CC_ALSA_HAS_MUTEX_LOCK == 1   
  //  Mutex::Autolock _l(alsa_uninit_lock);
#endif

    FreeRecorder();
    FreeTracker();

    if (tm_sleep > 0)
        sleep(tm_sleep);

    LOGD("Exit mAlsaUninit function sucess.\n");

    return 0;
#else
    return 0;
#endif
}

int mAlsaStartRecorder(void) {
#if CC_DISABLE_ALSA_MODULE == 0
    if (glpRecorder != NULL) {
        glpRecorder->start();
        return 1;
    }

    return 0;
#else
    return 0;
#endif
}

int mAlsaStopRecorder(void) {
#if CC_DISABLE_ALSA_MODULE == 0
    if (glpRecorder != NULL) {
        glpRecorder->stop();
        return 1;
    }

    return 0;
#else
    return 0;
#endif
}

void mAlsaStartTracker(void) {
#if CC_DISABLE_ALSA_MODULE == 0
    if (glpTracker != NULL) {
        glpTracker->start();
    }
#endif
}

void mAlsaStopTracker(void) {
#if CC_DISABLE_ALSA_MODULE == 0
    if (glpTracker != NULL) {
        glpTracker->stop();
    }
#endif
}
