/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget.webview;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollEventCallback;
import org.appcelerator.kroll.KrollLogging;
import org.appcelerator.kroll.KrollModule;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.util.TiConvert;
import org.json.JSONException;
import org.json.JSONObject;

import android.webkit.JavascriptInterface;
import android.webkit.WebView;

public class TiWebViewBinding
{

	private static final String TAG = "TiWebViewBinding";
	// This is based on binding.min.js. If you have to change anything...
	// - change binding.js
	// - minify binding.js to create binding.min.js
	protected final static String SCRIPT_INJECTION_ID = "__ti_injection";
	protected final static String INJECTION_CODE;
    protected final static String REMOTE_INJECTION_CODE;
    protected final static String SCRIPT_TAG_INJECTION_CODE;

	// This is based on polling.min.js. If you have to change anything...
	// - change polling.js
	// - minify polling.js to create polling.min.js
	protected static String POLLING_CODE = "";
	static {
//		StringBuilder jsonCode = readResourceFile("json2.js");
	    StringBuilder tiCode = readResourceFile("binding.min.js");
		StringBuilder pollingCode = readResourceFile("polling.min.js");

		if (pollingCode == null) {
			Log.w(TAG, "Unable to read polling code");
		} else {
			POLLING_CODE = pollingCode.toString();
		}

		StringBuilder scriptCode = new StringBuilder();
		StringBuilder injectionCode = new StringBuilder();
//		if (jsonCode == null) {
//			Log.w(TAG, "Unable to read JSON code for injection");
//		} else {
//			scriptCode.append(jsonCode);
//			injectionCode.append(jsonCode);
//		}

		if (tiCode == null) {
			Log.w(TAG, "Unable to read Titanium binding code for injection");
		} else {
			scriptCode.append("\n");
			scriptCode.append(tiCode.toString());
			injectionCode.append(tiCode.toString());
		}
//		jsonCode = null;
		tiCode = null;
		REMOTE_INJECTION_CODE = "(function() {" + injectionCode.toString() + "})()";
		SCRIPT_TAG_INJECTION_CODE = "\n<script id=\"" + SCRIPT_INJECTION_ID + "\">\n" + scriptCode.toString() + "\n</script>\n";
		INJECTION_CODE = "\n<script id=\"" + SCRIPT_INJECTION_ID + "\">\n" + injectionCode.toString() + "\n</script>\n";
		scriptCode = null;
		injectionCode = null;
	}

	private Stack<String> codeSnippets;
	private boolean destroyed;

	private ApiBinding apiBinding;
	private AppBinding appBinding;
	private TiReturn tiReturn;
	private WeakReference<WebView> webView;
	private boolean interfacesAdded = false;

	public TiWebViewBinding(WebView webView)
	{
		codeSnippets = new Stack<String>();
		this.webView = new WeakReference<WebView>(webView);
		apiBinding = new ApiBinding();
		appBinding = new AppBinding();
		tiReturn = new TiReturn();
	}

	public void addJavascriptInterfaces()
	{
		if (webView != null && !interfacesAdded) {
			webView.get().addJavascriptInterface(appBinding, "TiApp");
			webView.get().addJavascriptInterface(apiBinding, "TiAPI");
			webView.get().addJavascriptInterface(tiReturn, "_TiReturn");
			interfacesAdded = true;
		}
	}
	
	public void removeJavascriptInterfaces() 
    {
        if (webView != null && interfacesAdded) {
            webView.get().removeJavascriptInterface("TiApp");
            webView.get().removeJavascriptInterface("TiAPI");
            webView.get().removeJavascriptInterface("_TiReturn");
            interfacesAdded = false;
        }
    }
	
	public void removeAllEventListeners() {
        appBinding.removeAllEventListeners();
	}

	public void destroy()
	{
		// remove any event listener that have already been added to the Ti.APP through
		// this web view instance
		removeAllEventListeners();
		removeJavascriptInterfaces();
		webView = null;
		returnSemaphore.release();
		codeSnippets.clear();
        apiBinding = null;
        appBinding = null;
        tiReturn = null;
		destroyed = true;
	}

	private static StringBuilder readResourceFile(String fileName)
	{
		InputStream stream = TiWebViewBinding.class.getClassLoader().getResourceAsStream(
			"ti/modules/titanium/ui/widget/webview/" + fileName);
		BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
		StringBuilder code = new StringBuilder();
		try {
			for (String line = reader.readLine(); line != null; line = reader.readLine()) {
				code.append(line + "\n");
			}
		} catch (IOException e) {
			Log.e(TAG, "Error reading input stream", e);
			return null;
		} finally {
			if (stream != null) {
				try {
					stream.close();
				} catch (IOException e) {
					Log.w(TAG, "Problem closing input stream.", e);
				}
			}
		}
		return code;
	}

	private Semaphore returnSemaphore = new Semaphore(0);
	private String returnValue;

//    @JavascriptInterface
	synchronized public String getJSValue(String expression)
	{
		// Don't try to evaluate js code again if the binding has already been destroyed
		if (!destroyed && interfacesAdded) {

		    final String code = "_TiReturn.setValue((function(){try{ " + expression
                        + "}catch(ti_eval_err){return ti_eval_err;}})());";

//			Log.d(TAG, "getJSValue:" + code, Log.DEBUG_MODE);
			returnSemaphore.drainPermits();
			synchronized (codeSnippets) {
				codeSnippets.push(code);
			}
			try {
				if (!returnSemaphore.tryAcquire(3500, TimeUnit.MILLISECONDS)) {
					synchronized (codeSnippets) {
						codeSnippets.removeElement(code);
					}
					Log.w(TAG, "Timeout waiting to evaluate JS");
				}
				return returnValue;
			} catch (InterruptedException e) {
				Log.e(TAG, "Interrupted", e);
			}
		}
		return null;
	}

	private class TiReturn
	{
		@JavascriptInterface
		public void setValue(String value)
		{
			if (value != null) {
				returnValue = value;
			}
			returnSemaphore.release();
		}
	}

	private class WebViewCallback implements KrollEventCallback
	{
		private int id;

		public WebViewCallback(int id)
		{
			this.id = id;
		}

		public void call(Object data)
		{
			String dataString;
			if (data == null) {
				dataString = "";
			} else if (data instanceof HashMap) {
				JSONObject json = TiConvert.toJSON((HashMap) data);
				dataString = ", " + String.valueOf(json);
			} else {
				dataString = ", " + String.valueOf(data);
			}

			String code = "Ti.executeListener(" + id + dataString + ");";
			synchronized (codeSnippets) {
				codeSnippets.push(code);
			}
		}
	}

	@SuppressWarnings("unused")
	private class AppBinding
	{
		private WeakReference<KrollModule> module;
		private HashMap<String, Integer> appListeners = new HashMap<String, Integer>();
		private int counter = 0;
		private String code = null;
		public AppBinding()
		{
			module =  new WeakReference<KrollModule>(TiApplication.getInstance().getModuleByName("App"));
		}

		@JavascriptInterface
		public void emit(String event, String json)
		{
			try {
				KrollDict dict = new KrollDict();
				if (json != null && !json.equals("undefined")) {
					dict = new KrollDict(new JSONObject(json));
				}
				module.get().fireEvent(event, dict);
			} catch (JSONException e) {
				Log.e(TAG, "Error parsing event JSON", e);
			}
		}

		@JavascriptInterface
		public int on(String event, int id)
		{
			WebViewCallback callback = new WebViewCallback(id);

			int result = module.get().addEventListener(event, callback);
			appListeners.put(event, result);

			return result;
		}

		@JavascriptInterface
		public void off(String event, int id)
		{
		    module.get().removeEventListener(event, id);
		}

		@JavascriptInterface
		public void removeAllEventListeners()
		{
			for (String event : appListeners.keySet()) {
				off(event, appListeners.get(event));
			}
		}

		@JavascriptInterface
		public String getJSCode()
		{
			if (destroyed) {
				return null;
			}
			return code;
		}

		@JavascriptInterface
		public int hasResult()
		{
			if (destroyed) {
				return -1;
			}
			int result = 0;
			synchronized (codeSnippets) {
				if(codeSnippets.empty()) {
					code = "";
				} else {
					result = 1;
					code = codeSnippets.pop();
				}
			}
			return result;

		}
	}

	private class ApiBinding
	{
		private KrollLogging logging;

		public ApiBinding()
		{
			logging = KrollLogging.getDefault();
		}

		@JavascriptInterface
		public void log(String level, String arg)
		{
			logging.log(level, arg);
		}

		@JavascriptInterface
		public void info(String arg)
		{
			logging.info(arg);
		}

		@JavascriptInterface
		public void debug(String arg)
		{
			logging.debug(arg);
		}

		@JavascriptInterface
		public void error(String arg)
		{
			logging.error(arg);
		}

		@JavascriptInterface
		public void trace(String arg)
		{
			logging.trace(arg);
		}

		@JavascriptInterface
		public void warn(String arg)
		{
			logging.warn(arg);
		}
	}
}
