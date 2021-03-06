/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.platform;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.KrollRuntime;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.util.TiPlatformHelper;
import org.appcelerator.titanium.util.TiUIHelper;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;

@Kroll.module
public class PlatformModule extends KrollModule
{
	private static final String TAG = "PlatformModule";

	@Kroll.constant public static final int BATTERY_STATE_UNKNOWN = 0;
	@Kroll.constant public static final int BATTERY_STATE_UNPLUGGED = 1;
	@Kroll.constant public static final int BATTERY_STATE_CHARGING = 2;
	@Kroll.constant public static final int BATTERY_STATE_FULL = 3;

	protected DisplayCapsProxy displayCaps;

	protected int batteryState;
	protected double batteryLevel;
	protected boolean batteryStateReady;

	protected BroadcastReceiver batteryStateReceiver;

	public PlatformModule()
	{
		super();

		batteryState = BATTERY_STATE_UNKNOWN;
		batteryLevel = -1;
	}

	@Kroll.getProperty @Kroll.method
	public String getName() {
		return TiPlatformHelper.getInstance().getName();
	}

	@Kroll.getProperty @Kroll.method
	public String getOsname() {
		return TiPlatformHelper.getInstance().getName();
	}

	@Kroll.getProperty @Kroll.method
	public String getLocale() {
		return TiPlatformHelper.getInstance().getLocale();
	}

	@Kroll.getProperty @Kroll.method
	public DisplayCapsProxy getDisplayCaps() {
		if (displayCaps == null) {
			displayCaps = new DisplayCapsProxy();
			displayCaps.setActivity(TiApplication.getInstance().getCurrentActivity());
		}
		return displayCaps;
	}

	@Kroll.getProperty @Kroll.method
	public int getProcessorCount() {
		return TiPlatformHelper.getInstance().getProcessorCount();
	}

	@Kroll.getProperty @Kroll.method
	public String getUsername() {
		return TiPlatformHelper.getInstance().getUsername();
	}

	@Kroll.getProperty @Kroll.method
	public String getVersion() {
		return TiPlatformHelper.getInstance().getVersion();
	}

	@Kroll.getProperty @Kroll.method
    public int getSDKVersion() {
        return Build.VERSION.SDK_INT;
    }
	
	@Kroll.getProperty @Kroll.method
	public double getAvailableMemory() {
		return TiPlatformHelper.getInstance().getAvailableMemory();
	}

	@Kroll.getProperty @Kroll.method
	public String getModel() {
		return TiPlatformHelper.getInstance().getModel();
	}

	@Kroll.getProperty @Kroll.method
	public String getManufacturer() {
		return TiPlatformHelper.getInstance().getManufacturer();
	}

	@Kroll.getProperty @Kroll.method
	public String getOstype() {
		return TiPlatformHelper.getInstance().getOstype();
	}

	@Kroll.getProperty @Kroll.method
	public String getArchitecture() {
		return TiPlatformHelper.getInstance().getArchitecture();
	}

	@Kroll.method
	public boolean is24HourTimeFormat()
	{
		TiApplication app = TiApplication.getInstance();
		if (app != null) {
			return android.text.format.DateFormat.is24HourFormat(app.getApplicationContext());
		}
		return false;
	}

	@Kroll.method
	public String createUUID() {
		return TiPlatformHelper.getInstance().createUUID();
	}

	
	@Kroll.method
    public boolean openURL(String url) {
        Activity activity = TiApplication.getAppRootOrCurrentActivity();
        if (activity == null) {
            Log.e(TAG, "Activity not found: " + url);
            return false;
        }
        Uri uri = Uri.parse(url);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        try {
            if (activity != null) {
                activity.startActivity(intent);
            }
            return true;
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Activity not found: " + url, e);
        }
        return false;
    }

	@Kroll.getProperty @Kroll.method
	public String getMacaddress() {
		return TiPlatformHelper.getInstance().getMacaddress();
	}

	@Kroll.getProperty @Kroll.method
	public String getId() {
		return TiPlatformHelper.getInstance().getMobileId();
	}

	@Kroll.setProperty @Kroll.method
	public void setBatteryMonitoring(boolean monitor)
	{
		if (monitor && batteryStateReceiver == null) {
			registerBatteryStateReceiver();
		} else if (!monitor && batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
			batteryStateReceiver = null;
		}
	}
	@Kroll.getProperty @Kroll.method
	public boolean getBatteryMonitoring()
	{
		return batteryStateReceiver != null;
	}

	@Kroll.getProperty @Kroll.method
	public int getBatteryState()
	{
		return batteryState;
	}

	@Kroll.getProperty @Kroll.method
	public double getBatteryLevel()
	{
		return batteryLevel;
	}

	@Kroll.getProperty @Kroll.method
	public String getRuntime()
	{
		return KrollRuntime.getInstance().getRuntimeName();
	}

	protected void registerBatteryStateReceiver()
	{
		batteryStateReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent)
			{
				int scale = intent.getIntExtra(TiC.PROPERTY_SCALE, -1);
				batteryLevel = convertBatteryLevel(intent.getIntExtra(TiC.PROPERTY_LEVEL, -1), scale);
				batteryState = convertBatteryStatus(intent.getIntExtra(TiC.PROPERTY_STATUS, -1));

				KrollDict event = new KrollDict();
				event.put(TiC.PROPERTY_LEVEL, batteryLevel);
				event.put(TiC.PROPERTY_STATE, batteryState);
				fireEvent(TiC.EVENT_BATTERY, event);
			}
		};

		registerBatteryReceiver(batteryStateReceiver);
	}

	protected void unregisterBatteryStateReceiver()
	{
		getActivity().unregisterReceiver(batteryStateReceiver);
	}

	@Override
	public void eventListenerAdded(String type, int count, final KrollProxy proxy)
	{
		super.eventListenerAdded(type, count, proxy);
		if (TiC.EVENT_BATTERY.equals(type) && batteryStateReceiver == null) {
			registerBatteryStateReceiver();
		}
	}

	@Override
	public void eventListenerRemoved(String type, int count, KrollProxy proxy)
	{
		super.eventListenerRemoved(type, count, proxy);
		if (TiC.EVENT_BATTERY.equals(type) && count == 0 && batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
			batteryStateReceiver = null;
		}
	}

	private int convertBatteryStatus(int status)
	{
		int state = BATTERY_STATE_UNKNOWN;
		switch (status) {
			case BatteryManager.BATTERY_STATUS_CHARGING: {
				state = BATTERY_STATE_CHARGING;
				break;
			}
			case BatteryManager.BATTERY_STATUS_FULL: {
				state = BATTERY_STATE_FULL;
				break;
			}
			case BatteryManager.BATTERY_STATUS_DISCHARGING:
			case BatteryManager.BATTERY_STATUS_NOT_CHARGING: {
				state = BATTERY_STATE_UNPLUGGED;
				break;
			}
		}
		return state;
	}

	private double convertBatteryLevel(int level, int scale)
	{
		int l = -1;
		if (level >= 0 && scale > 0) {
			l = (level * 100) / scale;
		}
		return l;
	}

	private void registerBatteryReceiver(BroadcastReceiver batteryReceiver)
	{
		Activity a = getActivity();
		IntentFilter batteryFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		a.registerReceiver(batteryReceiver, batteryFilter);
	}

	@Override
	public void onResume(Activity activity)
	{
		super.onResume(activity);
		if (batteryStateReceiver != null) {
			Log.i(TAG, "Reregistering battery changed receiver", Log.DEBUG_MODE);
			registerBatteryReceiver(batteryStateReceiver);
		}
	}

	@Override
	public void onPause(Activity activity)
	{
		super.onPause(activity);
		if (batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
			batteryStateReceiver = null;
		}
	}

	@Override
	public void onDestroy(Activity activity)
	{
		super.onDestroy(activity);
		if (batteryStateReceiver != null) {
			unregisterBatteryStateReceiver();
			batteryStateReceiver = null;
		}
	}

	@Override
	public String getApiName()
	{
		return "Ti.Platform";
	}
	
    @Kroll.getProperty
    @Kroll.method
    public KrollDict getFullInfo() {
        KrollDict result = new KrollDict();
        result.put("abi", Build.CPU_ABI);
        result.put("dpi", getDisplayCaps().getDpi());
        result.put("xdpi", getDisplayCaps().getXdpi());
        result.put("ydpi", getDisplayCaps().getYdpi());
        result.put("density", getDisplayCaps().getDensity());
        result.put("version", getVersion());
        result.put("SDKVersion", getSDKVersion());
        result.put("ostype", getOstype());
        result.put("name", getName());
        result.put("model", getModel());
        result.put("locale", getLocale());
        result.put("id", getId());
        result.put("pixelWidth", getDisplayCaps().getPlatformWidth());
        result.put("pixelHeight", getDisplayCaps().getPlatformHeight());
        result.put("densityFactor", getDisplayCaps().getLogicalDensityFactor());
        return result;
    }
    
    @Kroll.getProperty @Kroll.method
    public TiBlob getSplashImageForCurrentOrientation() {
        Drawable drawable = TiUIHelper.getResourceDrawable((Object)"background.9.png");
        if (drawable == null) {
            drawable = TiUIHelper.getResourceDrawable((Object)"background.png");
        }
        if (drawable != null) {
            return TiBlob.blobFromObject(drawable);
        }
        return null;
    }


}
