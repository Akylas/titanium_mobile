/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.webview;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import android.support.annotation.StringRes;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.ViewParent;
import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.kroll.common.TiMessenger.Command;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBlob;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.io.TiBaseFile;
import org.appcelerator.titanium.io.TiFileFactory;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiMimeTypeHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.view.TiBackgroundDrawable;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiUINonViewGroupView;
import org.appcelerator.titanium.view.TiUIView;

import ti.modules.titanium.ui.WebViewProxy;
import ti.modules.titanium.ui.android.AndroidModule;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ValueCallback;
import android.webkit.WebSettings;
import android.webkit.WebSettings.LayoutAlgorithm;
import android.webkit.WebView;

@SuppressLint("NewApi")
public class TiUIWebView extends TiUINonViewGroupView
{

	private static final String TAG = "TiUIWebView";
	private TiWebViewClient client;
	private TiWebChromeClient chromeClient;
	private boolean bindingCodeInjected = false;
	private boolean isLocalHTML = false;
    private boolean alwaysInjectTi = false;
	private boolean disableContextMenu = false;
	private HashMap<String, String> extraHeaders = new HashMap<String, String>();
	private float zoomLevel =
		TiApplication.getInstance().getApplicationContext().getResources().getDisplayMetrics().density;
	private float initScale = zoomLevel;

	private static Enum<?> enumPluginStateOff;
	private static Enum<?> enumPluginStateOn;
	private static Enum<?> enumPluginStateOnDemand;
	private static Method internalSetPluginState;
//	private static Method internalWebViewPause;
//	private static Method internalWebViewResume;

	public static final int PLUGIN_STATE_OFF = 0;
	public static final int PLUGIN_STATE_ON = 1;
	public static final int PLUGIN_STATE_ON_DEMAND = 2;
	private boolean mScrollingEnabled = true;

	// TIMOB-25462: minor 'hack' to prevent 'beforeload' and 'load' being
	// called when the user-agent has been changed, this is a chromium bug
	// https://bugs.chromium.org/p/chromium/issues/detail?id=315891
	public boolean hasSetUserAgent = false;

	private static enum reloadTypes
	{
		DEFAULT, DATA, HTML, URL
	}

	private reloadTypes reloadMethod = reloadTypes.DEFAULT;
	private Object reloadData = null;
    private float currentProgress = -1;
    private String currentURL;

	private class TiWebView extends WebView
	{
		public TiWebViewClient client;

		public TiWebView(Context context)
		{
			super(context);
		}

		@Override
        public boolean onCheckIsTextEditor()
        {
            return true;
        }
		
		@Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (touchPassThrough(this, event))
                return false;
            return super.dispatchTouchEvent(event);
        }
		
		@Override
        public boolean onInterceptTouchEvent(MotionEvent event) {
            if (mScrollingEnabled && isTouchEnabled) {
                return super.onInterceptTouchEvent(event);
            }
            return false;
        }
		
        @Override
	    public boolean canScrollHorizontally(int direction) {
            if (!mScrollingEnabled || !isTouchEnabled) return false;
            return super.canScrollHorizontally(direction);
	    }
		public ActionMode startActionMode(ActionMode.Callback callback)
		{
			if (disableContextMenu) {
				return nullifiedActionMode();
			}

			return super.startActionMode(callback);
		}

		/**
		 * API 23 or higher is required for this startActionMode to be invoked otherwise other startActionMode is invoked.
		 */
		@Override
		public ActionMode startActionMode(ActionMode.Callback callback, int type)
		{
			if (disableContextMenu) {
				return nullifiedActionMode();
			}
			ViewParent parent = getParent();
			if (parent == null) {
				return null;
			}

			return parent.startActionModeForChild(this, callback, type);
		}

		public ActionMode nullifiedActionMode()
		{
			return new ActionMode()
			{
				@Override public void setTitle(CharSequence title)
				{
					
				}
				
				@Override public void setTitle(@StringRes int resId)
				{
					
				}
				
				@Override public void setSubtitle(CharSequence subtitle)
				{
					
				}
				
				@Override public void setSubtitle(@StringRes int resId)
				{
					
				}
				
				@Override public void setCustomView(View view)
				{
					
				}
				
				@Override public void invalidate()
				{
					
				}
				
				@Override public void finish()
				{
					
				}
				
				@Override public Menu getMenu()
				{
					return null;
				}
				
				@Override public CharSequence getTitle()
				{
					return null;
				}
				
				@Override public CharSequence getSubtitle()
				{
					return null;
				}
				
				@Override public View getCustomView()
				{
					return null;
				}
				
				@Override public MenuInflater getMenuInflater()
				{
					return null;
				}
			};
		}

		@Override
		public void destroy()
		{
			if (client != null) {
				client.getBinding().destroy();
			}
			super.destroy();
		}

		@Override
		public boolean onTouchEvent(MotionEvent event)
		{
//			boolean handled = false;
			
			switch (event.getAction()) {
                case MotionEvent.ACTION_MOVE:
                    if (!mScrollingEnabled) {
                        return false;
                    }
                    break;
                case MotionEvent.ACTION_UP:
                {
			// In Android WebView, all the click events are directly sent to WebKit. As a result, OnClickListener() is
			// never called. Therefore, we have to manually call performClick() when a click event is detected.
			//
			// In native Android and in the Ti world, it's possible to to have a touchEvent click on a link in a webview and
			// also to be detected as a click on the webview.  So we cannot let handling of the event one way block
			// the handling the other way -- it must be passed to both in all cases for everything to work correctly.
			//
                    if (hierarchyHasListener(TiC.EVENT_CLICK)) {
				Rect r = new Rect(0, 0, getWidth(), getHeight());
                        if (r.contains((int) event.getX(), (int) event.getY())) {
                            proxy.fireEvent(TiC.EVENT_CLICK, dictFromMotionEvent(event), true, false);
				}
			}
                    break;
			}

		}
            return super.onTouchEvent(event);
		}

		@Override
		protected void onLayout(boolean changed, int left, int top, int right, int bottom)
		{
			super.onLayout(changed, left, top, right, bottom);
            if (changed) {
                TiUIHelper.firePostLayoutEvent(TiUIWebView.this);
		}
	}
	
		@Override
	    protected void onScrollChanged(final int l, final int t, final int oldl, final int oldt)
	    {
	        super.onScrollChanged(l, t, oldl, oldt);
	        if (hasListeners(TiC.EVENT_SCROLL)) {
                getProxy().fireEvent(TiC.EVENT_SCROLL, TiConvert.toPointDict(l, t), false, false);
            }
	    }

	}

	//TIMOB-16952. Overriding onCheckIsTextEditor crashes HTC Sense devices
	private class NonHTCWebView extends TiWebView
	{
		public NonHTCWebView(Context context)
		{
			super(context);
		}

		@Override
		public boolean onCheckIsTextEditor()
		{
			int value = getFocusState();

				if (value == TiUIView.SOFT_KEYBOARD_HIDE_ON_FOCUS) {
					return false;
				} else if (value == TiUIView.SOFT_KEYBOARD_SHOW_ON_FOCUS) {
					return true;
				}
			return super.onCheckIsTextEditor();
		}
	}

	private boolean isHTCSenseDevice()
	{
		boolean isHTC = false;

		FeatureInfo[] features = TiApplication.getInstance().getApplicationContext().getPackageManager().getSystemAvailableFeatures();
		if (features == null) {
			return isHTC;
		}
		for (FeatureInfo f : features) {
			String fName = f.name;
			if (fName != null) {
				isHTC = fName.contains("com.htc.software.Sense");
				if (isHTC) {
					Log.i(TAG, "Detected com.htc.software.Sense feature " + fName);
					break;
				}
			}
		}

		return isHTC;
	}

	public TiUIWebView(TiViewProxy proxy)
	{
		super(proxy, new TiCompositeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

		// We can only support debugging in API 19 and higher
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			// Only enable webview debugging, when app is debuggable
			if (0 != (proxy.getActivity().getApplicationContext().getApplicationInfo().flags &= ApplicationInfo.FLAG_DEBUGGABLE)) {
				WebView.setWebContentsDebuggingEnabled(true);
			}
		}

        this.isFocusable = true;
		TiWebView webView = null;
		try {
			webView = isHTCSenseDevice() ? new TiWebView(proxy.getActivity()) : new NonHTCWebView(proxy.getActivity());
		} catch (Exception e) {
			// silence unnecessary internal logs...
		}
		webView.setVerticalScrollbarOverlay(true);

		WebSettings settings = webView.getSettings();
		settings.setUseWideViewPort(true);
		settings.setJavaScriptEnabled(true);
		settings.setSupportMultipleWindows(false);
		settings.setJavaScriptCanOpenWindowsAutomatically(false);
		settings.setAllowFileAccess(true);
		settings.setDomStorageEnabled(true); // Required by some sites such as Twitter. This is in our iOS WebView too.
		File path = TiApplication.getInstance().getFilesDir();
		if (path != null) {
			settings.setDatabasePath(path.getAbsolutePath());
			settings.setDatabaseEnabled(true);
		}

		File cacheDir = TiApplication.getInstance().getCacheDir();
		if (cacheDir != null) {
			settings.setAppCacheEnabled(true);
			settings.setAppCachePath(cacheDir.getAbsolutePath());
		}

		// enable zoom controls by default
		boolean enableZoom = true;
//
//		if (proxy.hasProperty(TiC.PROPERTY_ENABLE_ZOOM_CONTROLS)) {
//			enableZoom = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_ENABLE_ZOOM_CONTROLS));
//		}
//
		settings.setBuiltInZoomControls(enableZoom);
		settings.setSupportZoom(enableZoom);

		if (TiC.JELLY_BEAN_OR_GREATER) {
			settings.setAllowUniversalAccessFromFileURLs(true); // default is "false" for JellyBean, TIMOB-13065
		}

		// We can only support webview settings for plugin/flash in API 8 and higher.
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.ECLAIR_MR1) {
			initializePluginAPI(webView);
		}

		boolean enableJavascriptInterface = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_ENABLE_JAVASCRIPT_INTERFACE), true);
		chromeClient = new TiWebChromeClient(this);
		webView.setWebChromeClient(chromeClient);
		client = new TiWebViewClient(this, webView);
		webView.setWebViewClient(client);
		if (Build.VERSION.SDK_INT > 16 || enableJavascriptInterface) {
			client.getBinding().addJavascriptInterfaces();
		}

		webView.setWebViewClient(client);

		if (proxy instanceof WebViewProxy) {
			WebViewProxy webProxy = (WebViewProxy) proxy;
			String username = webProxy.getBasicAuthenticationUserName();
			String password = webProxy.getBasicAuthenticationPassword();
			if (username != null && password != null) {
				setBasicAuthentication(username, password);
			}
			webProxy.clearBasicAuthentication();
		}

		setNativeView(webView);
	}

	public WebView getWebView()
	{
		return (WebView) getNativeView();
	}

	private void initializePluginAPI(TiWebView webView)
	{
		try {
			synchronized (this.getClass()) {
				// Initialize
				if (enumPluginStateOff == null) {
					Class<?> webSettings = Class.forName("android.webkit.WebSettings");
					Class<?> pluginState = Class.forName("android.webkit.WebSettings$PluginState");

					Field f = pluginState.getDeclaredField("OFF");
					enumPluginStateOff = (Enum<?>) f.get(null);
					f = pluginState.getDeclaredField("ON");
					enumPluginStateOn = (Enum<?>) f.get(null);
					f = pluginState.getDeclaredField("ON_DEMAND");
					enumPluginStateOnDemand = (Enum<?>) f.get(null);
					internalSetPluginState = webSettings.getMethod("setPluginState", pluginState);
					// Hidden APIs
					// http://android.git.kernel.org/?p=platform/frameworks/base.git;a=blob;f=core/java/android/webkit/WebView.java;h=bbd8b95c7bea66b7060b5782fae4b3b2c4f04966;hb=4db1f432b853152075923499768639e14403b73a#l2558
//					internalWebViewPause = webView.getClass().getMethod("onPause");
//					internalWebViewResume = webView.getClass().getMethod("onResume");
				}
			}
		} catch (ClassNotFoundException e) {
			Log.e(TAG, "ClassNotFound: " + e.getMessage(), e);
		} catch (NoSuchMethodException e) {
			Log.e(TAG, "NoSuchMethod: " + e.getMessage(), e);
		} catch (NoSuchFieldException e) {
			Log.e(TAG, "NoSuchField: " + e.getMessage(), e);
		} catch (IllegalAccessException e) {
			Log.e(TAG, "IllegalAccess: " + e.getMessage(), e);
		}
	}

//	@Override
//	protected void doSetClickable(View view, boolean clickable)
//	{
//		super.doSetClickable(view, clickable);
//	}
	
	public void setScrollingEnabled(Object value)
	{
		try {
			mScrollingEnabled = TiConvert.toBoolean(value);
		} catch (IllegalArgumentException e) {
			mScrollingEnabled = true;
		}
	}
	
	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_SCALES_PAGE_TO_FIT:
            getWebView().getSettings().setLoadWithOverviewMode(TiConvert.toBoolean(newValue));
            getWebView().getSettings().setLayoutAlgorithm(LayoutAlgorithm.TEXT_AUTOSIZING);
            break;
        case TiC.PROPERTY_CACHE_MODE:
            getWebView().getSettings().setCacheMode(TiConvert.toInt(newValue, AndroidModule.WEBVIEW_LOAD_DEFAULT));
            break;
		case TiC.PROPERTY_REQUEST_HEADERS:
			if (newValue instanceof HashMap) {
				setRequestHeaders((HashMap) newValue);
			}
		break;
        case TiC.PROPERTY_URL:
            if (!TiC.URL_ANDROID_ASSET_RESOURCES.equals(TiConvert.toString(newValue))) {
                setUrl(TiConvert.toString(newValue));
            }
            else {
                setUrl(null);
            }
            break;
        case TiC.PROPERTY_HTML:
            setHtml(TiConvert.toString(newValue), (HashMap<String, Object>) (proxy.getProperty(WebViewProxy.OPTIONS_IN_SETHTML)));
            break;
        case TiC.PROPERTY_DATA:
            if (newValue instanceof TiBlob) {
                setData((TiBlob) newValue);
            } else {
                setData(null);
            }
            break;
        case TiC.PROPERTY_LIGHT_TOUCH_ENABLED:
            getWebView().getSettings().setLightTouchEnabled(TiConvert.toBoolean(newValue));
            break;
        case TiC.PROPERTY_PLUGIN_STATE:
            setPluginState(TiConvert.toInt(newValue));
            break;
        case TiC.PROPERTY_SHOW_SCROLLBARS:
            boolean value = TiConvert.toBoolean(newValue);
            getWebView().setVerticalScrollBarEnabled(value);
            getWebView().setHorizontalScrollBarEnabled(value);
            break;
        case TiC.PROPERTY_OVER_SCROLL_MODE:
            nativeView.setOverScrollMode(TiConvert.toInt(newValue, View.OVER_SCROLL_ALWAYS));
            break;
        case TiC.PROPERTY_SCROLLING_ENABLED:
            setScrollingEnabled(newValue);
            break;
        case TiC.PROPERTY_SHOW_HORIZONTAL_SCROLL_INDICATOR:
            nativeView.setHorizontalScrollBarEnabled(TiConvert.toBoolean(newValue));
            break;
        case TiC.PROPERTY_SHOW_VERTICAL_SCROLL_INDICATOR:
            nativeView.setVerticalScrollBarEnabled(TiConvert.toBoolean(newValue));
            break;
        case TiC.PROPERTY_ENABLE_ZOOM_CONTROLS:
            getWebView().getSettings().setDisplayZoomControls(TiConvert.toBoolean(newValue));
            break;
        case TiC.PROPERTY_ZOOM_ENABLED:
            getWebView().getSettings().setSupportZoom(TiConvert.toBoolean(newValue));
            break;
        case TiC.PROPERTY_ENABLE_JAVASCRIPT_INTERFACE:
            boolean enableJavascriptInterface = TiConvert.toBoolean(newValue, true);
            if (Build.VERSION.SDK_INT > 16 || enableJavascriptInterface) {
                client.getBinding().addJavascriptInterfaces();
            } else {
                client.getBinding().removeJavascriptInterfaces();
            }
            break;
        case "alwaysInjectTi":
            alwaysInjectTi = TiConvert.toBoolean(newValue, false);
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
        
        if (changedProperty) {
            // If TiUIView's propertyChanged ended up making a TiBackgroundDrawable
            // for the background, we must set the WebView background color to transparent
            // in order to see any of it.
            boolean isBgRelated = (key.startsWith(TiC.PROPERTY_BACKGROUND_PREFIX) || key.startsWith(TiC.PROPERTY_BORDER_PREFIX));
            if (isBgRelated && nativeView != null && nativeView.getBackground() instanceof TiBackgroundDrawable) {
                nativeView.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

	@Override
	public void processProperties(HashMap d)
	{
		super.processProperties(d);

		if (d.containsKey(TiC.PROPERTY_SCALES_PAGE_TO_FIT)) {
			WebSettings settings = getWebView().getSettings();
			settings.setLoadWithOverviewMode(TiConvert.toBoolean(d, TiC.PROPERTY_SCALES_PAGE_TO_FIT));
		}

		if (d.containsKey(TiC.PROPERTY_CACHE_MODE)) {
			int mode = TiConvert.toInt(d.get(TiC.PROPERTY_CACHE_MODE), AndroidModule.WEBVIEW_LOAD_DEFAULT);
			getWebView().getSettings().setCacheMode(mode);
		}

		if (d.containsKey(TiC.PROPERTY_REQUEST_HEADERS)) {
			Object value = d.get(TiC.PROPERTY_REQUEST_HEADERS);
			if (value instanceof HashMap) {
				setRequestHeaders((HashMap) value);
			}
		}

		if (d.containsKey(TiC.PROPERTY_URL) && !TiC.URL_ANDROID_ASSET_RESOURCES.equals(TiConvert.toString(d, TiC.PROPERTY_URL))) {
			setUrl(TiConvert.toString(d, TiC.PROPERTY_URL));
		} else if (d.containsKey(TiC.PROPERTY_HTML)) {
			setHtml(TiConvert.toString(d, TiC.PROPERTY_HTML), (HashMap<String, Object>) (d.get(WebViewProxy.OPTIONS_IN_SETHTML)));
		} else if (d.containsKey(TiC.PROPERTY_DATA)) {
			Object value = d.get(TiC.PROPERTY_DATA);
			if (value instanceof TiBlob) {
				setData((TiBlob) value);
			}
		}

		if (d.containsKey(TiC.PROPERTY_LIGHT_TOUCH_ENABLED)) {
			WebSettings settings = getWebView().getSettings();
			settings.setLightTouchEnabled(TiConvert.toBoolean(d, TiC.PROPERTY_LIGHT_TOUCH_ENABLED));
		}

		// If TiUIView's processProperties ended up making a TiBackgroundDrawable
		// for the background, we must set the WebView background color to transparent
		// in order to see any of it.
		if (nativeView != null && nativeView.getBackground() instanceof TiBackgroundDrawable) {
			nativeView.setBackgroundColor(Color.TRANSPARENT);
		}

		if (d.containsKey(TiC.PROPERTY_PLUGIN_STATE)) {
			setPluginState(TiConvert.toInt(d, TiC.PROPERTY_PLUGIN_STATE));
		}

		if (d.containsKey(TiC.PROPERTY_OVER_SCROLL_MODE)) {
			if (Build.VERSION.SDK_INT >= 9) {
				nativeView.setOverScrollMode(TiConvert.toInt(d.get(TiC.PROPERTY_OVER_SCROLL_MODE), View.OVER_SCROLL_ALWAYS));
			}
		}

		if (d.containsKey(TiC.PROPERTY_DISABLE_CONTEXT_MENU)) {
			disableContextMenu = TiConvert.toBoolean(d, TiC.PROPERTY_DISABLE_CONTEXT_MENU);
		}

		if (d.containsKey(TiC.PROPERTY_USER_AGENT)) {
			((WebViewProxy) getProxy()).setUserAgent(TiConvert.toString(d, TiC.PROPERTY_USER_AGENT));
		}

		if (d.containsKey(TiC.PROPERTY_ZOOM_LEVEL)) {
			zoomBy(getWebView(), TiConvert.toFloat(d, TiC.PROPERTY_ZOOM_LEVEL));
		}
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, KrollProxy proxy)
	{
		if (TiC.PROPERTY_URL.equals(key)) {
			setUrl(TiConvert.toString(newValue));
		} else if (TiC.PROPERTY_HTML.equals(key)) {
			setHtml(TiConvert.toString(newValue));
		} else if (TiC.PROPERTY_DATA.equals(key)) {
			if (newValue instanceof TiBlob) {
				setData((TiBlob) newValue);
			}
		} else if (TiC.PROPERTY_SCALES_PAGE_TO_FIT.equals(key)) {
			WebSettings settings = getWebView().getSettings();
			settings.setLoadWithOverviewMode(TiConvert.toBoolean(newValue));
		} else if (TiC.PROPERTY_OVER_SCROLL_MODE.equals(key)) {
			if (Build.VERSION.SDK_INT >= 9) {
				nativeView.setOverScrollMode(TiConvert.toInt(newValue, View.OVER_SCROLL_ALWAYS));
			}
		} else if (TiC.PROPERTY_CACHE_MODE.equals(key)) {
			getWebView().getSettings().setCacheMode(TiConvert.toInt(newValue));
		} else if (TiC.PROPERTY_LIGHT_TOUCH_ENABLED.equals(key)) {
			WebSettings settings = getWebView().getSettings();
			settings.setLightTouchEnabled(TiConvert.toBoolean(newValue));
		} else if (TiC.PROPERTY_REQUEST_HEADERS.equals(key)) {
			if (newValue instanceof HashMap) {
				setRequestHeaders((HashMap) newValue);
			}
		} else if (TiC.PROPERTY_DISABLE_CONTEXT_MENU.equals(key)) {
			disableContextMenu = TiConvert.toBoolean(newValue);
		} else if (TiC.PROPERTY_ZOOM_LEVEL.equals(key)) {
			zoomBy(getWebView(), TiConvert.toFloat(newValue, 1.0f));
		} else if (TiC.PROPERTY_USER_AGENT.equals(key)) {
			((WebViewProxy) getProxy()).setUserAgent(TiConvert.toString(newValue));
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	private void zoomBy(WebView webView, float scale)
	{
		if (Build.VERSION.SDK_INT >= 21 && webView != null) {
			if (scale <= 0.0f) {
				scale = 0.01f;
			} else if (scale >= 100.0f) {
				scale = 100.0f;
			}

			float targetVal = (initScale * scale) / zoomLevel;
			webView.zoomBy(targetVal);
		}
	}

	public void zoomBy(float scale)
	{
		zoomBy(getWebView(), scale);
	}

	public float getZoomLevel()
	{
		return zoomLevel;
	}

	public void setZoomLevel(float value)
	{
		getProxy().setProperty(TiC.PROPERTY_ZOOM_LEVEL, value / initScale);
		zoomLevel = value;
	}

	private boolean mightBeHtml(String url)
	{
		String mime = TiMimeTypeHelper.getMimeType(url);
		if (mime.equals("text/html")) {
			return true;
		} else if (mime.equals("application/xhtml+xml")) {
			return true;
		} else {
			return false;
		}
	}

	public void setUrl(String url)
	{
	    if (url == null) return;
		reloadMethod = reloadTypes.URL;
		reloadData = url;
		String finalUrl = currentURL = url;
		Uri uri = Uri.parse(finalUrl);
		boolean originalUrlHasScheme = (uri.getScheme() != null);

		if (!originalUrlHasScheme) {
			finalUrl = getProxy().resolveUrl(null, finalUrl);
		}

		if (TiFileFactory.isLocalScheme(finalUrl) && mightBeHtml(finalUrl)) {
			TiBaseFile tiFile = TiFileFactory.createTitaniumFile(finalUrl, false);
			if (tiFile != null) {
				StringBuilder out = new StringBuilder();
				InputStream fis = null;
				try {
					fis = tiFile.getInputStream();
					InputStreamReader reader = new InputStreamReader(fis, "utf-8");
					BufferedReader breader = new BufferedReader(reader);
					String line = breader.readLine();
					while (line != null) {
						if (!bindingCodeInjected) {
							int pos = line.indexOf("<html");
							if (pos >= 0) {
								int posEnd = line.indexOf(">", pos);
								if (posEnd > pos) {
									out.append(line.substring(pos, posEnd + 1));
									out.append(TiWebViewBinding.SCRIPT_TAG_INJECTION_CODE);
									if ((posEnd + 1) < line.length()) {
										out.append(line.substring(posEnd + 1));
									}
									out.append("\n");
									bindingCodeInjected = true;
									line = breader.readLine();
									continue;
								}
							}
						}
						out.append(line);
						out.append("\n");
						line = breader.readLine();
					}
					setHtmlInternal(out.toString(), (originalUrlHasScheme ? url : finalUrl), "text/html"); // keep app:// etc. intact in case
												  // html in file contains links
												  // to JS that use app:// etc.
					return;
				} catch (IOException ioe) {
								Log.e(TAG, "Problem reading from " + url + ": " + ioe.getMessage()
												+ ". Will let WebView try loading it directly.", ioe);
				} finally {
					if (fis != null) {
						try {
							fis.close();
						} catch (IOException e) {
							Log.w(TAG, "Problem closing stream: " + e.getMessage(), e);
						}
					}
				}
			}
		}

		Log.d(TAG, "WebView will load " + url + " directly without code injection.", Log.DEBUG_MODE);
		// iOS parity: for whatever reason, when a remote url is used, the iOS implementation
		// explicitly sets the native webview's setScalesPageToFit to YES if the
		// Ti scalesPageToFit property has _not_ been set.
		if (!proxy.hasProperty(TiC.PROPERTY_SCALES_PAGE_TO_FIT)) {
			getWebView().getSettings().setLoadWithOverviewMode(true);
		}
		isLocalHTML = false;
		currentProgress = -1;
		if (extraHeaders.size() > 0) {
			getWebView().loadUrl(finalUrl, extraHeaders);
		} else {
			getWebView().loadUrl(finalUrl);
		}
	}

	public void changeProxyUrl(String url)
	{
		getProxy().setProperty("url", url);
		if (!TiC.URL_ANDROID_ASSET_RESOURCES.equals(url)) {
			reloadMethod = reloadTypes.URL;
			reloadData = url;
		}
	}

	public String getUrl()
	{
		return getWebView().getUrl();
	}

	private static final char escapeChars[] = new char[] { '%', '#', '\'', '?' };

	private String escapeContent(String content)
	{
		// The Android WebView has a known bug
		// where it forgets to escape certain characters
		// when it creates a data:// URL in the loadData() method
		// http://code.google.com/p/android/issues/detail?id=1733
		for (char escapeChar : escapeChars) {
			String regex = "\\" + escapeChar;
			content = content.replaceAll(regex, "%" + Integer.toHexString(escapeChar));
		}
		return content;
	}

	public void setHtml(String html)
	{
		reloadMethod = reloadTypes.HTML;
		reloadData = null;
		setHtmlInternal(html, TiC.URL_ANDROID_ASSET_RESOURCES, "text/html");
	}

	public void setHtml(String html, HashMap<String, Object> d)
	{
		if (d == null) {
			setHtml(html);
			return;
		}

		reloadMethod = reloadTypes.HTML;
		reloadData = d;
		String baseUrl = TiC.URL_ANDROID_ASSET_RESOURCES;
		String mimeType = "text/html";
		if (d.containsKey(TiC.PROPERTY_BASE_URL_WEBVIEW)) {
			baseUrl = TiConvert.toString(d.get(TiC.PROPERTY_BASE_URL_WEBVIEW));
		}
		if (d.containsKey(TiC.PROPERTY_MIMETYPE)) {
			mimeType = TiConvert.toString(d.get(TiC.PROPERTY_MIMETYPE));
		}

		setHtmlInternal(html, baseUrl, mimeType);
	}

	/**
	 * Loads HTML content into the web view.  Note that the "historyUrl" property 
	 * must be set to non null in order for the web view history to work correctly 
	 * when working with local files (IE:  goBack() and goForward() will not work if 
	 * null is used)
	 * 
	 * @param html					HTML data to load into the web view
	 * @param baseUrl				url to associate with the data being loaded
	 * @param mimeType			mime type of the data being loaded
	 */
	private void setHtmlInternal(String html, String baseUrl, String mimeType)
	{
		// iOS parity: for whatever reason, when html is set directly, the iOS implementation
		// explicitly sets the native webview's setScalesPageToFit to NO if the
		// Ti scalesPageToFit property has _not_ been set.

		WebView webView = getWebView();
		if (!proxy.hasProperty(TiC.PROPERTY_SCALES_PAGE_TO_FIT)) {
			webView.getSettings().setLoadWithOverviewMode(false);
		}

		// Set flag to indicate that it's local html (used to determine whether we want to inject binding code)
		isLocalHTML = true;

		if (html.contains(TiWebViewBinding.SCRIPT_INJECTION_ID)) {
			// Our injection code is in there already, go ahead and show.
			webView.loadDataWithBaseURL(baseUrl, html, mimeType, "utf-8", baseUrl);
			return;
		}

		int tagStart = html.indexOf("<html");
		int tagEnd = -1;
		if (tagStart >= 0) {
			tagEnd = html.indexOf(">", tagStart + 1);

			if (tagEnd > tagStart) {
				StringBuilder sb = new StringBuilder(html.length() + 2500);
				sb.append(html.substring(0, tagEnd + 1));
				sb.append(TiWebViewBinding.SCRIPT_TAG_INJECTION_CODE);
				if ((tagEnd + 1) < html.length()) {
					sb.append(html.substring(tagEnd + 1));
				}
				webView.loadDataWithBaseURL(baseUrl, sb.toString(), mimeType, "utf-8", baseUrl);
				bindingCodeInjected = true;
				return;
			}
		}
        currentProgress = -1;
		webView.loadDataWithBaseURL(baseUrl, html, mimeType, "utf-8", baseUrl);
	}

	public void setData(TiBlob blob)
	{
		reloadMethod = reloadTypes.DATA;
		reloadData = blob;
		String mimeType = "text/html";

		// iOS parity: for whatever reason, in setData, the iOS implementation
		// explicitly sets the native webview's setScalesPageToFit to YES if the
		// Ti scalesPageToFit property has _not_ been set.
		if (!proxy.hasProperty(TiC.PROPERTY_SCALES_PAGE_TO_FIT)) {
			getWebView().getSettings().setLoadWithOverviewMode(true);
		}

		if (blob.getType() == TiBlob.TYPE_FILE) {
			String fullPath = blob.getNativePath();
			if (fullPath != null) {
				setUrl(fullPath);
				return;
			}
		}

        currentProgress = -1;
		if (blob.getMimeType() != null) {
			mimeType = blob.getMimeType();
		}
		if (TiMimeTypeHelper.isBinaryMimeType(mimeType)) {
			getWebView().loadData(blob.toBase64(), mimeType, "base64");
		} else {
			getWebView().loadData(escapeContent(new String(blob.getBytes())), mimeType, "utf-8");
		}
	}

	public void evalJSAsync(final String expression, final KrollFunction callback) {
	    if (TiC.KIT_KAT_OR_GREATER) {
	        final String code  = prepareEvalString(expression);
            proxy.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    getWebView().evaluateJavascript("(function(){" + code+ "})()", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String result) {
                            if (result != null) {
                                result = result.replaceAll("(^\")|(\"$)","");
                            }
                            callback.callAsync(getProxy().getKrollObject(), new Object[] {result});
                        }
                    });
                }
            });
        } else {
            String result = getJSValue(expression);
            callback.callAsync(getProxy().getKrollObject(), new Object[] {result});
       }
	}
	

	public void injectJS(final String code, final KrollFunction callback) {
        final String actualCode  = code.replaceAll("//.*?(\n|$)","").replaceAll("\n", "").replaceAll("\r", "");
        
        if (TiC.KIT_KAT_OR_GREATER) {
            if (callback != null) {
                getWebView().evaluateJavascript( "(function(){" + actualCode+ "})()", new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String result) {
                        callback.callAsync(getProxy().getKrollObject(), new Object[] {result});
                    }
                });
            } else {
                getWebView().evaluateJavascript( "(function(){" + actualCode+ "})()", null);
            }
        } else {
            if (callback != null) {
                final String result = getJSValue("javascript:(function(){" + actualCode+ "})()");
                callback.callAsync(getProxy().getKrollObject(), new Object[] {result});
            } else {
                getWebView().loadUrl("javascript:(function(){" + actualCode+ "})()");
            }
        }
    }
	
    private static final Pattern sJSValuePattern = Pattern.compile("(var|function|//|if|return|console)");
	private static String prepareEvalString(final String expression) {
	    String code  = expression.replaceFirst("\\s+$", "").replaceAll("//.*?(\n|$)","").replaceAll("\n", "").replaceAll("\r", "");
        if (sJSValuePattern.matcher(code).find()) {
            return code;
        } else {
            return "return " + code;
        }
	}

	public String getJSValue(final String expression)
	{
	    final WebView webView = getWebView();
	    if (webView == null) {
	        return "";
	}
	    final String code  = prepareEvalString(expression);
//	    if (TiC.KIT_KAT_OR_GREATER) {
//	        final CountDownLatch latch = new CountDownLatch(1);
//	        final StringBuffer result = new StringBuffer(); 
//	        proxy.getActivity().runOnUiThread(new Runnable() {
//	            @Override
//	            public void run() {
//	                webView.evaluateJavascript(expression, new ValueCallback<String>() {
//	                    @Override
//	                    public void onReceiveValue(String value) {
//	                        result.append(value);
//	                        latch.countDown();
//	                    }
//	                });
//	            }
//	        });
//	        try {
//	            latch.await();
//	        } catch (InterruptedException e) {
//	            // TODO Auto-generated catch block
//	            return "";
//	        }
//	        return result.toString();
//	    } else {
	        if (TiApplication.isUIThread()) {
	            return (String) TiMessenger.sendBlockingMainCommand(new Command<String>() {

	                @Override
	                public String execute() {
	                    return client.getBinding().getJSValue(code);
	                }
	            });
	        } else {
	            return client.getBinding().getJSValue(code);
	        }
//	    }
	}

	public void setBasicAuthentication(String username, String password)
	{
		client.setBasicAuthentication(username, password);
	}

	public void destroyWebView()
	{
        TiWebView webView = (TiWebView) getNativeView();
        if (webView != null) {
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            chromeClient.destroy();
            chromeClient = null;
            client.destroy();
            client = null;
            TiUIHelper.removeViewFromSuperView(webView);
            webView.destroy();
            
            
	}
    }
	public void setPluginState(int pluginState)
	{
			TiWebView webView = (TiWebView) getNativeView();
			WebSettings webSettings = webView.getSettings();
			if (webView != null) {
				try {
					switch (pluginState) {
						case PLUGIN_STATE_OFF:
							internalSetPluginState.invoke(webSettings, enumPluginStateOff);
							break;
						case PLUGIN_STATE_ON:
							internalSetPluginState.invoke(webSettings, enumPluginStateOn);
							break;
						case PLUGIN_STATE_ON_DEMAND:
							internalSetPluginState.invoke(webSettings, enumPluginStateOnDemand);
							break;
						default:
							Log.w(TAG, "Not a valid plugin state. Ignoring setPluginState request");
	}
				} catch (InvocationTargetException e) {
					Log.e(TAG, "Method not supported", e);
				} catch (IllegalAccessException e) {
					Log.e(TAG, "Illegal Access", e);
				}
			}
		}

	public void pauseWebView()
	{
       WebView currWebView = getWebView();
       if (currWebView == null) {
           return;
				}
       currWebView.onPause();
			}

	public void resumeWebView()
	{
	    WebView currWebView = getWebView();
	       if (currWebView == null) {
	           return;
				}
	       currWebView.onResume();
	}

	public void setUserAgentString(String userAgentString)
	{
		WebView currWebView = getWebView();
		if (currWebView != null) {
			hasSetUserAgent = true;
			currWebView.getSettings().setUserAgentString(userAgentString);
		}
	}

	public String getUserAgentString()
	{
		WebView currWebView = getWebView();
		return (currWebView != null) ? currWebView.getSettings().getUserAgentString() : "";
	}

	public void setRequestHeaders(HashMap items)
	{
		Map<String, String> map = items;
		for (Map.Entry<String, String> item : map.entrySet()) {
			extraHeaders.put(item.getKey().toString(), item.getValue().toString());
		}
	}

	public HashMap getRequestHeaders()
	{
		return extraHeaders;
	}

	public boolean canGoBack()
	{
		return getWebView().canGoBack();
	}

	public boolean canGoForward()
	{
		return getWebView().canGoForward();
	}

	public void goBack()
	{
		getWebView().goBack();
	}

	public void goForward()
	{
		getWebView().goForward();
	}

	public void reload()
	{
		switch (reloadMethod) {
			case DATA:
				if (reloadData != null && reloadData instanceof TiBlob) {
					setData((TiBlob) reloadData);
				} else {
				Log.d(TAG, "reloadMethod points to data but reloadData is null or of wrong type. Calling default", Log.DEBUG_MODE);
					getWebView().reload();
				}
				break;

			case HTML:
				if (reloadData == null || (reloadData instanceof HashMap<?, ?>) ) {
										setHtml(TiConvert.toString(getProxy().getProperty(TiC.PROPERTY_HTML)), (HashMap<String,Object>)reloadData);
				} else {
				Log.d(TAG, "reloadMethod points to html but reloadData is of wrong type. Calling default", Log.DEBUG_MODE);
					getWebView().reload();
				}
				break;

			case URL:
				if (reloadData != null && reloadData instanceof String) {
					setUrl((String) reloadData);
				} else {
				Log.d(TAG, "reloadMethod points to url but reloadData is null or of wrong type. Calling default", Log.DEBUG_MODE);
					getWebView().reload();
				}
				break;

			default:
				getWebView().reload();
		}
	}

	public void stopLoading()
	{
		getWebView().stopLoading();
	}

	public void onProgressChanged(WebView view, int progress)   
    {
	    float newProgress = (float)progress/100;
	    if (newProgress == currentProgress ) {
	        return;
	    }
	    if (currentProgress == -1 && newProgress != 0) {
	        onProgressChanged(view, 0);
	    }
	    if (progress == 0) {
	        //make sure all previous event are removed
	        client.getBinding().removeAllEventListeners();
	    }
	    if (progress == 100) {
	        boolean enableJavascriptInjection = true;
	        if (proxy.hasProperty(TiC.PROPERTY_ENABLE_JAVASCRIPT_INTERFACE)) {
	            enableJavascriptInjection = TiConvert.toBoolean(proxy.getProperty(TiC.PROPERTY_ENABLE_JAVASCRIPT_INTERFACE), true);
	        }
	        if (TiC.HONEYCOMB_OR_GREATER || enableJavascriptInjection) {
	            WebView nativeWebView = getWebView();

	            if (nativeWebView != null) {
	                if (shouldInjectBindingCode()) {
	                    if (isLocalHTML) {
	                        nativeWebView.loadUrl("javascript:" + TiWebViewBinding.INJECTION_CODE);

	                    } else {
//	                        if (TiC.KIT_KAT_OR_GREATER) {
//	                            nativeWebView.evaluateJavascript( TiWebViewBinding.REMOTE_INJECTION_CODE, null);
//	                        } else {
	                            nativeWebView.loadUrl("javascript:" + TiWebViewBinding.REMOTE_INJECTION_CODE);
//	                        }
	                    }
	                }
	                
                    nativeWebView.loadUrl("javascript:" + TiWebViewBinding.POLLING_CODE);
	            }
	            bindingCodeInjected = true;
	        }
	    }
	    currentProgress = newProgress;
	    
	    if (proxy.hasListeners("loadprogress", false)) {
            KrollDict event = eventForURL(currentURL);
            event.put("progress", (float)progress/100);
            proxy.fireEvent("loadprogress", event, false, false);
        }
    }
	
	public void clearWebView()
	{
		getWebView().loadUrl("about:blank");
	}


	public boolean shouldInjectBindingCode()
	{
		return (isLocalHTML || alwaysInjectTi) && !bindingCodeInjected;
	}

	public boolean isLocalHTML()
    {
        return isLocalHTML;
	}

	public void setBindingCodeInjected(boolean injected)
	{
		bindingCodeInjected = injected;
		initScale = getZoomLevel();
	}

	public boolean interceptOnBackPressed()
	{
		return chromeClient.interceptOnBackPressed();
	}

	public KrollDict eventForURL(String url) {
        KrollDict data = new KrollDict();
        if (url != null) {
            data.put(TiC.PROPERTY_URL, url.replace(TiC.URL_ANDROID_ASSET_RESOURCES, ""));
        }
        return data;
    }


    public void setIsLocalHTML(boolean b) {
        isLocalHTML = b;
        
    }


    public void clearHistory() {
        getWebView().clearHistory();    
    }
    public void clearCache() {
        getWebView().clearCache(true);    
    }

	@Override
    protected void disableHWAcceleration(View view) {
		if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.JELLY_BEAN) {
			super.disableHWAcceleration(view);
		} else {
			Log.d(TAG, "Do not disable HW acceleration for WebView.", Log.DEBUG_MODE);
		}
	}
}
