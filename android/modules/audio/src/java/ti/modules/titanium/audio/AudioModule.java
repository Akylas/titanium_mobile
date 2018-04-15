/**
 * Akylas
 * Copyright (c) 2014-2015 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

package ti.modules.titanium.audio;

import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.titanium.ContextSpecific;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;

import ti.modules.titanium.audio.streamer.AudioStreamerExoService;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;

@SuppressLint("NewApi")
@Kroll.module @ContextSpecific
public class AudioModule extends KrollModule
	implements Handler.Callback
{
	private static final String TAG = "TiAudio";

	@Kroll.constant public static final int SHUFFLE_NONE = 0;
    @Kroll.constant public static final int SHUFFLE_SONGS = 1;
    @Kroll.constant public static final int SHUFFLE_RANDOM = 2;
    @Kroll.constant public static final int SHUFFLE_ALBUMS = SHUFFLE_SONGS;
    @Kroll.constant public static final int SHUFFLE_DEFAULT = SHUFFLE_NONE;
    @Kroll.constant public static final int REPEAT_NONE = 0;
	@Kroll.constant public static final int REPEAT_ONE = 1;
	@Kroll.constant public static final int REPEAT_ALL = 2;
    @Kroll.constant public static final int REPEAT_DEFAULT = REPEAT_NONE;


	public AudioModule()
	{
		super();
	}
	
    public static FocusableAudioWidget sFocusedAudioWidget = null;

    public static void widgetGetsFocused(final FocusableAudioWidget widget) {
        sFocusedAudioWidget = widget;
    }

    public static void widgetAbandonsFocused(
            final FocusableAudioWidget widget) {
        if (widget == sFocusedAudioWidget) {
            sFocusedAudioWidget = null;
        }
    }

    public static FocusableAudioWidget focusedAudioWidget() {
        return sFocusedAudioWidget;
    }
    

    @Kroll.method
    public boolean hasAudioRecorderPermissions() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        Context context = TiApplication.getInstance().getApplicationContext();
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }
    

    @Kroll.method
    public void requestAudioRecorderPermissions(@Kroll.argument(optional=true)KrollFunction permissionCallback) {
        if (hasAudioRecorderPermissions()) {
            return;
        }
        String[] permissions = new String[] { Manifest.permission.RECORD_AUDIO };
        TiBaseActivity.addPermissionListener(TiC.PERMISSION_CODE_MICROPHONE, getKrollObject(), permissionCallback);
        Activity currentActivity = TiApplication.getInstance().getCurrentActivity();
        if (currentActivity != null) {
            currentActivity.requestPermissions(permissions, TiC.PERMISSION_CODE_MICROPHONE);
        }
    }
    

    @Kroll.method
    @Kroll.getProperty
    public boolean getCanRecord()
    {
        return TiApplication.getInstance().getPackageManager().hasSystemFeature("android.hardware.microphone");
    }

}

