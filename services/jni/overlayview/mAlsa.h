#ifndef __M_ALSA_H__
#define __M_ALSA_H__

#define CC_FLAG_CREATE_RECORD           (0x0001)
#define CC_FLAG_CREATE_TRACK            (0x0002)
#define CC_FLAG_START_RECORD            (0x0004)
#define CC_FLAG_START_TRACK             (0x0008)
#define CC_FLAG_SOP_RECORD		(0x0010)	
#ifdef __cplusplus
extern "C" {
#endif

int mAlsaInit(int tm_sleep, int init_flag);
int mAlsaUninit(int tm_sleep);
int mAlsaStartRecorder(void);
int mAlsaStopRecorder(void);
void mAlsaStartTracker(void);
void mAlsaStopTracker(void);

#ifdef __cplusplus
}
#endif

#endif
