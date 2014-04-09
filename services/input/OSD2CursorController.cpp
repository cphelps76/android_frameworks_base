/*
 * Copyright (C) 2012 Amlogic, Inc.
 * All rights reserved.
 *
 */

#define LOG_TAG "OSD2Cursor"

#define DEBUG 0
#if DEBUG > 0
#define LOG_NDEBUG 0
#endif

#include "OSD2CursorController.h"
#include <stdint.h>
#include <fcntl.h>
#include <linux/fb.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <cutils/properties.h>
#include <utils/Log.h>
#include <utils/misc.h>
#include <SkBitmap.h>
#include <SkCanvas.h>
#include <SkDevice.h>
#include <SkColor.h>
#include <SkPaint.h>
#include <SkXfermode.h>

namespace android
{
#define OSD2_DEV "/dev/graphics/fb1"

static uint32_t xscale = 0;
static uint32_t yscale = 0;

// ----------------------------- cursor data --------------------------------
//#define USE_OLD_CURSOR
#ifdef USE_OLD_CURSOR
// white cursor, black border, transparent background
#define CURSOR_WIDTH  13
#define CURSOR_HEIGHT 18
#define OSD_WIDTH  18
#define OSD_HEIGHT 18

#define b 0xff000000
#define w 0xffffffff
#define t 0x00000000
// padding on left because 2x scale causes a line on the right side
static const int gCurmap[CURSOR_HEIGHT][CURSOR_WIDTH] = {
    {t, b, b, t, t, t, t, t, t, t, t, t, t},
    {t, b, b, b, t, t, t, t, t, t, t, t, t},
    {t, b, b, b, b, t, t, t, t, t, t, t, t},
    {t, b, b, w, b, b, t, t, t, t, t, t, t},
    {t, b, b, w, w, b, b, t, t, t, t, t, t},
    {t, b, b, w, w, w, b, b, t, t, t, t, t},
    {t, b, b, w, w, w, w, b, b, t, t, t, t},
    {t, b, b, w, w, w, w, w, b, b, t, t, t},
    {t, b, b, w, w, w, w, w, w, b, b, t, t},
    {t, b, b, w, w, w, w, w, w, w, b, b, b},
    {t, b, b, w, w, w, w, w, b, b, b, b, b},
    {t, b, b, w, w, b, w, w, b, b, b, b, t},
    {t, b, b, w, b, b, b, w, b, b, t, t, t},
    {t, b, b, b, b, b, b, w, w, b, b, t, t},
    {t, b, b, b, t, t, b, b, w, w, b, b, t},
    {t, b, b, t, t, t, b, b, w, w, b, b, t},
    {t, t, t, t, t, t, t, b, b, w, b, b, t},
    {t, t, t, t, t, t, t, t, b, b, b, t, t},
};
#undef b
#undef w
#undef t
#endif // USE_OLD_CURSOR
// ----------------------------- /cursor data --------------------------------
// --- private ---
#ifdef USE_OLD_CURSOR
void OSD2CursorController::drawCursor(uint8_t *buffer, ssize_t buflen, uint32_t line_len) {
    long int location;
    uint32_t xx, yy;

    xx = yy = 0;
    memset(buffer, 0, buflen);
    if ((mHWrotation == 0 && mRotation == ROTATION_0) ||
        (mHWrotation == 90 && mRotation == ROTATION_270) ||
        (mHWrotation == 180 && mRotation == ROTATION_180) ||
        (mHWrotation == 270 && mRotation == ROTATION_90) ||
        mTvOutEnabled) {
        for (yy=0; yy < (CURSOR_HEIGHT<<yscale); yy++) {
            location = ((yy) * line_len);
            for (xx=0; xx < (CURSOR_WIDTH<<xscale); xx++) {
                *(uint32_t*)(buffer + location) = gCurmap[yy>>yscale][xx>>xscale];
                location += 4; // location += (vinfo.bits_per_pixel / 8)
            }
        }
    }
    else if ((mHWrotation == 0 && mRotation == ROTATION_90) ||
             (mHWrotation == 90 && mRotation == ROTATION_0) ||
             (mHWrotation == 180 && mRotation == ROTATION_270) ||
             (mHWrotation == 270 && mRotation == ROTATION_180)) {
        for (yy=0; yy < (CURSOR_WIDTH<<xscale); yy++) {
            location = ((yy) * line_len);
            for (xx=0; xx < (CURSOR_HEIGHT<<yscale); xx++) {
                *(uint32_t*)(buffer + location) = gCurmap[(CURSOR_HEIGHT - 1 - xx)>>yscale][yy>>xscale];
                location += 4;
            }
        }
    }
    else if ((mHWrotation == 0 && mRotation == ROTATION_180) ||
             (mHWrotation == 90 && mRotation == ROTATION_90) ||
             (mHWrotation == 180 && mRotation == ROTATION_0) ||
             (mHWrotation == 270 && mRotation == ROTATION_270)) {
        for (yy=0; yy < (CURSOR_HEIGHT<<yscale); yy++) {
            location = ((yy) * line_len);
            for (xx=0; xx < (CURSOR_WIDTH<<xscale); xx++) {
                *(uint32_t*)(buffer + location) = gCurmap[(CURSOR_HEIGHT  - 1 - yy)>>yscale][(CURSOR_WIDTH  - 1 - xx)>>xscale];
                location += 4;
            }
        }
    }
    else if ((mHWrotation == 0 && mRotation == ROTATION_270) ||
             (mHWrotation == 90 && mRotation == ROTATION_180) ||
             (mHWrotation == 180 && mRotation == ROTATION_90) ||
             (mHWrotation == 270 && mRotation == ROTATION_0)) {
        for (yy=0; yy < (CURSOR_WIDTH<<xscale); yy++) {
            location = ((yy) * line_len);
            for (xx=0; xx < (CURSOR_HEIGHT<<yscale); xx++) {
                *(uint32_t*)(buffer + location) = gCurmap[xx>>yscale][(CURSOR_WIDTH - 1 - yy)>>xscale];
                location += 4;
            }
        }
    }
    ALOGV("drawCursor\n");
}
#else // !USE_OLD_CURSOR
static void convertRGBAtoBGRA(const SkBitmap& bm) {
    SkAutoLockPixels lock(bm);
    uint8_t* p = (uint8_t*)bm.getPixels();
    uint8_t* stop = p + bm.getSize();
    uint8_t* start = p;
    while (p < stop - 4) {
        // swap red/blue
        //ALOGV("%02x %02x %02x %02x", p[0], p[1], p[2], p[3]);
        uint8_t t = p[0];
        p[0] = p[2];
        p[2] = t;
        p += 4;
    }
}

void OSD2CursorController::drawCursor(uint8_t *buffer, ssize_t buflen, uint32_t line_len) {
    int rotation;
    unsigned int osdw, osdh;

    rotation = (mHWrotation + (mRotation * 90)) % 360;
    if (mTvOutEnabled)
        rotation = 0;
    ALOGV("rotation=%d mHWrotation=%d mRotation=%d", rotation, mHWrotation, mRotation);
    ALOGV("line_len=%d", line_len);

    if (!mIcon.isValid()) {
        ALOGD("mIcon not valid");
        return;
    }

    osdw = mIcon.bitmap.width() << xscale;
    osdh = mIcon.bitmap.height() << yscale;

    if (rotation == 90 || rotation == 270)
        SkTSwap(osdw, osdh);
    ALOGV("mIcon.bitmap=%dx%d scale_shift=%dx%x osd=%dx%d",
            mIcon.bitmap.width(), mIcon.bitmap.height(), xscale, yscale, osdw, osdh);

    memset(buffer, 0, buflen);

    SkBitmap surfaceBitmap;
    surfaceBitmap.setConfig(SkBitmap::kARGB_8888_Config,
            osdw, osdh, line_len);
    surfaceBitmap.setPixels(buffer);

    SkCanvas surfaceCanvas(surfaceBitmap);
    //surfaceCanvas.setBitmapDevice(surfaceBitmap);	

    SkPaint paint;
    //paint.setXfermodeMode(SkXfermode::kSrc_Mode);
    /* rotate */
    SkScalar cx = mIcon.bitmap.width() / 2;
    SkScalar cy = mIcon.bitmap.height() / 2;
    surfaceCanvas.translate(cx, cy);
    surfaceCanvas.rotate(rotation);
    surfaceCanvas.translate(-cx, -cy);

    /* scale */
    if (xscale > 0 || yscale > 0) {
        surfaceCanvas.scale(1 << xscale, 1 << yscale);
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
    }
    surfaceCanvas.drawBitmap(mIcon.bitmap, 0, 0, &paint);

    if (DEBUG > 0) {
        paint.setARGB(0xaa, 0xff, 0x00, 0x00);
        paint.setStyle(SkPaint::kStroke_Style);
        surfaceCanvas.resetMatrix();
        surfaceCanvas.drawRectCoords(0, 0, osdw - 1, osdh - 1, paint);
    }

    convertRGBAtoBGRA(surfaceBitmap);

    ALOGV("drawCursor\n");
}
#endif // !USE_OLD_CURSOR

void OSD2CursorController::setCursor() {
    if (mFb1fd < 0) {
        mFb1fd = open(OSD2_DEV, O_RDWR);
        ALOGV("setCursor, open, mFb1fd=%d", mFb1fd);
    }
    if (mFb1fd >= 0) {
        struct fb_var_screeninfo vinfo;
        struct fb_fix_screeninfo finfo;
        uint8_t *buffer = NULL;
        ssize_t buflen;
        int cursorw, cursorh;

#ifdef USE_OLD_CURSOR
        cursorw = OSD_WIDTH;
        cursorh = OSD_HEIGHT;
#else
        if (!mIcon.isValid()) {
            ALOGV("setCursor, mIcon not valid");
            return;
        }
        cursorw = mIcon.bitmap.width();
        cursorh = mIcon.bitmap.height();
#endif

        if (!ioctl(mFb1fd, FBIOGET_VSCREENINFO, &vinfo) &&
                !ioctl(mFb1fd, FBIOGET_FSCREENINFO, &finfo)) {
            /* get scale value */
            int scale;
            ALOGV("vinfo. %d %d", vinfo.xres, vinfo.yres);

            xscale = 0;
            scale = vinfo.xres / cursorw;
            ALOGV("cursorw=%d scale=%d", cursorw, scale);
            while (scale > 1) {
                xscale++;
                scale >>= 1;
            }
            yscale = 0;
            scale = vinfo.yres / cursorh;
            ALOGV("cursorh=%d scale=%d", cursorh, scale);
            while (scale > 1) {
                yscale++;
                scale >>= 1;
            }
            /* resize osd2 */
            vinfo.xres = vinfo.xres_virtual = ((cursorw << xscale) + 1) & ~1;
            vinfo.yres = vinfo.yres_virtual = ((cursorh << yscale) + 1) & ~1;
            char property[PROPERTY_VALUE_MAX];
            if (property_get("ro.platform.UI.800x480", property, NULL) > 0) {
                if (strcmp(property, "true")==0) {
                    vinfo.xres = vinfo.xres_virtual = 22;
                    vinfo.yres = vinfo.yres_virtual = 28;
                }
            }else if(property_get("ro.osd2.size", property, NULL) > 0)
            {
                int tmpw,tmph;
                if (sscanf(property, "%ix%i",&tmpw,&tmph)==2) {
                    vinfo.xres = vinfo.xres_virtual = tmpw;
                    vinfo.yres = vinfo.yres_virtual = tmph;
                    ALOGV("%i %i",vinfo.xres ,vinfo.yres );
                }
            }
            vinfo.xoffset = vinfo.yoffset = 0;
            vinfo.bits_per_pixel = 32;
            if (ioctl(mFb1fd, FBIOPUT_VSCREENINFO, &vinfo))
                ALOGE("set info fail\n");
            ioctl(mFb1fd, FBIOGET_FSCREENINFO, &finfo);

            buflen = vinfo.yres_virtual * finfo.line_length;
            buffer = (uint8_t*)mmap(NULL,
                                    buflen,
                                    PROT_READ|PROT_WRITE,
                                    MAP_SHARED,
                                    mFb1fd,
                                    0);
            if (buffer != MAP_FAILED) {
                /* draw */
                drawCursor(buffer, buflen, finfo.line_length);
                munmap(buffer, buflen);
                ALOGV("setCursor ok\n");
            }
            else
                ALOGE("mmap fail\n");
        }
        else
            ALOGE("get info fail\n");
    }
    else
        ALOGE("open fail\n");
}

void OSD2CursorController::setblank(bool blank)
{
    if (mFb1fd >= 0)
        ioctl(mFb1fd, FBIOBLANK, blank ? 1 : 0);
}

// --- public ---
OSD2CursorController::OSD2CursorController() :
        mFb1fd(-1), mIsShown(true),
        mHWrotation(0), mRotation(ROTATION_0),
        mDispW(0), mDispH(0), mTvOutEnabled(false) {
    char value[PROPERTY_VALUE_MAX];
    property_get("ro.sf.hwrotation", value, "0");
    if (180 == atoi(value) )
        mHWrotation = 180;

    ALOGV("created");
}

void OSD2CursorController::setIcon(const SpriteIcon& icon) {
    ALOGV("setIcon line=%d, mFb1fd=%d", __LINE__, mFb1fd);

    if (icon.isValid()) {
        icon.bitmap.copyTo(&mIcon.bitmap, SkBitmap::kARGB_8888_Config);

        if (!mIcon.isValid()
                || mIcon.hotSpotX != icon.hotSpotX
                || mIcon.hotSpotY != icon.hotSpotY) {
            mIcon.hotSpotX = icon.hotSpotX;
            mIcon.hotSpotY = icon.hotSpotY;
        }
    } else if (mIcon.isValid()) {
        mIcon.bitmap.reset();
    } else {
        return; // setting to invalid icon and already invalid so nothing to do
    }

    setCursor();
}

void OSD2CursorController::setPosition(short x, short y) {
    //if (mIsShown == false)
    //    return;
    if (mFb1fd < 0) {
        ALOGV("setPosition line=%d, mFb1fd=%d", __LINE__, mFb1fd);
        setCursor();
    }
    if (mFb1fd >= 0) {
        struct fb_cursor cinfo;
        int osdw, osdh;
#ifdef USE_OLD_CURSOR
        osdw = OSD_WIDTH;
        osdh = OSD_HEIGHT;
#else
        if (!mIcon.isValid()) {
            ALOGV("setPosition, mIcon not valid");
            return;
        }
        osdw = mIcon.bitmap.width() << xscale;
        osdh = mIcon.bitmap.height() << yscale;
#endif

        if ((mHWrotation == 0 && mRotation == ROTATION_0) ||
            (mHWrotation == 90 && mRotation == ROTATION_270) ||
            (mHWrotation == 180 && mRotation == ROTATION_180) ||
            (mHWrotation == 270 && mRotation == ROTATION_90)) {
            cinfo.hot.x = x;
            cinfo.hot.y = y;
        }
        else if ((mHWrotation == 0 && mRotation == ROTATION_90) ||
                 (mHWrotation == 90 && mRotation == ROTATION_0) ||
                 (mHWrotation == 180 && mRotation == ROTATION_270) ||
                 (mHWrotation == 270 && mRotation == ROTATION_180)) {
            /**
             * 1. When rotated, the w/h is changed.. 1280x720 -> 720x1280
             * 2. Subtract OSD_HEIGHT to place the cursor's point (top-left
             *    in original orientation) on the desired spot
             */
            if (mTvOutEnabled)
                cinfo.hot.x = mDispH - y;
            else if((mHWrotation == 270 && mRotation == ROTATION_180))
            	cinfo.hot.x = mDispH - y - osdh;
            else if((mHWrotation == 90 && mRotation == ROTATION_0))
            	cinfo.hot.x = mDispH - y - osdh;
            else
                cinfo.hot.x = mDispW - y - osdh;
            cinfo.hot.y = x;
        }
        else if ((mHWrotation == 0 && mRotation == ROTATION_180) ||
                 (mHWrotation == 90 && mRotation == ROTATION_90) ||
                 (mHWrotation == 180 && mRotation == ROTATION_0) ||
                 (mHWrotation == 270 && mRotation == ROTATION_270)) {
            if (mTvOutEnabled) {
                cinfo.hot.x = mDispW - x;
                cinfo.hot.y = mDispH - y;
            }else if((mHWrotation == 270 && mRotation == ROTATION_270)){
                cinfo.hot.x = mDispH - x - osdw;
                cinfo.hot.y = mDispW - y - osdh;
            }else if((mHWrotation == 90 && mRotation == ROTATION_90)){
                cinfo.hot.x = mDispH - x - osdw;
                cinfo.hot.y = mDispW - y - osdh;
            }else {
                cinfo.hot.x = mDispW - x - osdw;
                cinfo.hot.y = mDispH - y - osdh;
            }
        }
        else if ((mHWrotation == 0 && mRotation == ROTATION_270) ||
                 (mHWrotation == 90 && mRotation == ROTATION_180) ||
                 (mHWrotation == 180 && mRotation == ROTATION_90) ||
                 (mHWrotation == 270 && mRotation == ROTATION_0)) {
            cinfo.hot.x = y;
            if (mTvOutEnabled)
                cinfo.hot.y = mDispW - x;
            else if((mHWrotation == 270 && mRotation == ROTATION_0))
            	cinfo.hot.y = mDispW - x - osdw;
            else if((mHWrotation == 90 && mRotation == ROTATION_180))
            	cinfo.hot.y = mDispW - x - osdw;
            else
                cinfo.hot.y = mDispH - x - osdw;
        }
        else
            return;
        if (DEBUG > 1) ALOGV("setPosition %d, %d -> %d, %d disp=%d, %d  userrotation=%d, rotation=%d\n",
                            x, y, (int16_t)cinfo.hot.x, (int16_t)cinfo.hot.y, mDispW, mDispH, mHWrotation, mRotation);
        ioctl(mFb1fd, FBIO_CURSOR, &cinfo);
    }
}

void OSD2CursorController::show()
{
    ALOGV("show() line=%d", __LINE__);
    if (!mIsShown) {
        setCursor();
        setblank(false);
        mIsShown = true;
    }
}

void OSD2CursorController::hide()
{
    ALOGV("hide() line=%d", __LINE__);
    //if (mIsShown) {
        setblank(true);
        mIsShown = false;
    //}
}

void OSD2CursorController::setRotation(short rotation)
{
    if (rotation == ROTATION_0 || rotation == ROTATION_90 ||
        rotation == ROTATION_180 || rotation == ROTATION_270) {
        mRotation = rotation;
        if (mIsShown)
            setCursor();
    }
    ALOGV("setRotation %d\n", rotation);
}

void OSD2CursorController::setDisplaySize(short w, short h)
{
    ALOGV("setDisplaySize %d %d\n", w, h);
    mDispW = w;
    mDispH = h;
}

void OSD2CursorController::tvOutChanged(bool isEnabled)
{
    ALOGV("tvOutChanged %d\n", isEnabled);
    mTvOutEnabled = isEnabled;
    if (mIsShown)
        setCursor();
}

}; // namespace android

