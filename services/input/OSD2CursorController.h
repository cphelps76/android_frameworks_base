/*
 * Copyright (C) 2012 Amlogic, Inc.
 * All rights reserved.
 *
 */

#ifndef _UI_OSD2_CURSOR_CONTROLLER_H
#define _UI_OSD2_CURSOR_CONTROLLER_H

#include <utils/RefBase.h>
#include "SpriteController.h"

namespace android
{
class OSD2CursorController : public RefBase {
public:
    OSD2CursorController();
    void setIcon(const SpriteIcon& icon);
    void setPosition(short x, short y);
    void show();
    void hide();
    void setRotation(short rotation);
    void setDisplaySize(short w, short h);
    void tvOutChanged(bool isEnabled);

private:
    enum {
        ROTATION_0 = 0,
        ROTATION_90 = 1,
        ROTATION_180 = 2,
        ROTATION_270 = 3,
    };
    int mFb1fd;
    bool mIsShown;
    SpriteIcon mIcon;
    /**
     *  mHWrotation is the default screen orientation. For most LCD screens,
     *  this is landscape. A value of 270 means ROTATION_0 will be portrait.
     *  mRotation is the rotation from the default screen orientation, usually
     *  controlled by the accelerometer.
     */
    short mHWrotation;
    short mRotation;
    short mDispW;
    short mDispH;
    bool mTvOutEnabled;

    void drawCursor(uint8_t *buffer, ssize_t buflen, uint32_t line_len);
    void setCursor();
    void setblank(bool blank);
};
} // namespace android
#endif // _UI_OSD2_CURSOR_CONTROLLER_H
