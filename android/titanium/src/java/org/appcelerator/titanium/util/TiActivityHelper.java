package org.appcelerator.titanium.util;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.appcelerator.titanium.ITiAppInfo;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.ActionBarProxy;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.TypedValue;

public class TiActivityHelper {
    /**
     * Used to know if Apollo was sent into the background
     * 
     * @param context The {@link Context} to use
     */
    public static final boolean isApplicationSentToBackground(final Context context) {
        final ActivityManager activityManager = (ActivityManager)context
                .getSystemService(Context.ACTIVITY_SERVICE);
        final List<RunningTaskInfo> tasks = activityManager.getRunningTasks(1);
        if (!tasks.isEmpty()) {
            final ComponentName topActivity = tasks.get(0).topActivity;
            if (!topActivity.getPackageName().equals(context.getPackageName())) {
                return true;
            }
        }
        return false;
    }
    
    private static String capitalize(String line)
    {
        return Character.toUpperCase(line.charAt(0)) + line.substring(1).toLowerCase();
    }
    
    public static String getMainActivityName(){
        Pattern pattern = Pattern.compile("[^A-Za-z0-9_]");
        ITiAppInfo appInfo = TiApplication.getInstance().getAppInfo();
        String str = appInfo.getName();
        String className = "";
        String[] splitStr = pattern.split(str);
        for (int i = 0; i < splitStr.length; i++) {
            className = className + capitalize(splitStr[i]);
        }
        Pattern pattern2 = Pattern.compile("^[0-9]");
        Matcher matcher = pattern2.matcher(className);
        if (matcher.matches()) {
            className = "_" + className;
        }
        return appInfo.getId() + "." + className + "Activity";
    }
    
    public static ActionBar getActionBar(final Activity activity) {
        if (activity instanceof ActionBarActivity) {
            if (activity instanceof TiBaseActivity && !((TiBaseActivity) activity).isReadyToQueryActionBar()) {
                return null;
            }
            try {
                return ((ActionBarActivity) activity)
                        .getSupportActionBar();
            } catch (NullPointerException e) {
                return null;
            }
        }
        return null;
    }
    
    public static double getActionBarHeight(final Activity activity) {
        ActionBar actionBar = getActionBar(activity);
        if (actionBar != null) {
             // Calculate ActionBar height
            int actionBarHeight = ActionBarProxy.getActionBarSize(activity);
            return new TiDimension(actionBarHeight, TiDimension.TYPE_HEIGHT).getAsDefault();
        }
        return 0;
    }
    
    public static void setActionBarHidden(final Activity activity, final boolean hidden) {
        ActionBar actionBar = getActionBar(activity);
        if (actionBar != null) {
            try {
                if (hidden) {
                    actionBar.hide();
                }
                else {
                    actionBar.show();
                }
            } catch (NullPointerException e) {
                // no internal action bar
            }
        }
    }
    
    public static boolean setActionBarTitle(final Activity activity, final String title) {
        ActionBar actionBar = getActionBar(activity);
        if (actionBar != null) {
            try {
                actionBar.setTitle(title);
                return true;
            } catch (NullPointerException e) {
                // no internal action bar
            }
        }
        return false;
    }
}
