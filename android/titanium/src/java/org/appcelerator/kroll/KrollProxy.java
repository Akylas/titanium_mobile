/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.kroll;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.APIMap;
import org.appcelerator.kroll.common.AsyncResult;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.kroll.common.TiMessenger;
import org.appcelerator.kroll.common.TiMessenger.Command;
import org.appcelerator.kroll.common.TiMessenger.CommandNoReturn;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiViewEventOverrideDelegate;
import org.appcelerator.titanium.proxy.ActivityProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.TiLifecycle.OnLifecycleEvent;
import org.appcelerator.titanium.util.TiActivityHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiRHelper;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiUrl;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Bundle;
import android.util.Pair;
import android.util.SparseArray;

import org.json.JSONObject;

/**
 * This is the parent class of all proxies. A proxy is a dynamic object that can be created or
 * queried by the user through a module or another proxy's API. When you create a native view with
 * <a href="http://developer.appcelerator.com/apidoc/mobile/latest/Titanium.UI.createView-method.html">Titanium.UI.createView </a>,
 * the view object is a proxy itself.
 */
@Kroll.proxy(propertyAccessors = {
        TiC.PROPERTY_BUBBLE_PARENT,
        TiC.PROPERTY_BIND_ID}, propertyDontEnumAccessors = {
                KrollProxy.PROPERTY_HAS_JAVA_LISTENER })
public class KrollProxy implements Handler.Callback, KrollProxySupport, OnLifecycleEvent {
    public static interface SetPropertyChangeListener {
        public void onSetProperty(KrollProxy proxy, String name, Object value);

        public void onApplyProperties(KrollProxy krollProxy, HashMap arg,
                boolean force, boolean wait);

        public Object onGetProperty(KrollProxy krollProxy, String name);
    }

	private static final String TAG = "KrollProxy";
	private static final int INDEX_NAME = 0;
	private static final int INDEX_OLD_VALUE = 1;
	private static final int INDEX_VALUE = 2;

	private static final String ERROR_CREATING_PROXY = "Error creating proxy";

	protected static final int MSG_MODEL_PROPERTY_CHANGE = KrollObject.MSG_LAST_ID + 100;
	protected static final int MSG_LISTENER_ADDED = KrollObject.MSG_LAST_ID + 101;
	protected static final int MSG_LISTENER_REMOVED = KrollObject.MSG_LAST_ID + 102;
	protected static final int MSG_MODEL_PROCESS_PROPERTIES = KrollObject.MSG_LAST_ID + 103;
	protected static final int MSG_MODEL_PROPERTIES_CHANGED = KrollObject.MSG_LAST_ID + 104;
	protected static final int MSG_INIT_KROLL_OBJECT = KrollObject.MSG_LAST_ID + 105;
	protected static final int MSG_SET_PROPERTY = KrollObject.MSG_LAST_ID + 106;
	protected static final int MSG_FIRE_EVENT = KrollObject.MSG_LAST_ID + 107;
	protected static final int MSG_FIRE_SYNC_EVENT = KrollObject.MSG_LAST_ID + 108;
	protected static final int MSG_CALL_PROPERTY_ASYNC = KrollObject.MSG_LAST_ID + 109;
	protected static final int MSG_CALL_PROPERTY_SYNC = KrollObject.MSG_LAST_ID + 110;
    protected static final int MSG_MODEL_APPLY_PROPERTIES = KrollObject.MSG_LAST_ID + 111;
    protected static final int MSG_UPDATE_KROLL_PROPERTIES = KrollObject.MSG_LAST_ID + 112;
    protected static final int MSG_GET_PROPERTY = KrollObject.MSG_LAST_ID + 113;
    protected static final int MSG_LAST_ID = MSG_GET_PROPERTY;
	protected static final String PROPERTY_NAME = "name";
    protected static final String PROPERTY_BUBBLES = "checkParent";
	protected static final String PROPERTY_HAS_JAVA_LISTENER = "_hasJavaListener";

	protected static AtomicInteger proxyCounter = new AtomicInteger();
	protected AtomicInteger listenerIdGenerator;

    protected Map<String, List<KrollDict>> evaluators;
    protected Map<String, ArrayList<Object>> eventListeners;
	protected KrollObject krollObject;
    protected boolean krollObjectSupported = false;
	protected WeakReference<Activity> activity;
	protected String proxyId;
	protected TiUrl creationUrl;
    protected WeakReference<KrollProxyListener> modelListener;
    protected SetPropertyChangeListener setPropertyListener;
	protected KrollModule createdInModule;
	protected boolean coverageEnabled;
	protected KrollDict properties = new KrollDict();
	protected KrollDict defaultValues = new KrollDict();
	protected Handler mainHandler = null;
	protected Handler runtimeHandler = null;

	private KrollDict langConversionTable = null;
	private boolean bubbleParent = true;
    private String mBindId = null;
    private boolean bubbleParentDefined = false;
    private WeakReference<TiViewEventOverrideDelegate> eventOverrideDelegate = null;

    private Set<String> mSyncEvents;

    private HashMap<String, Object> propertiesToUpdateNativeSide = null;
    private boolean readyToUpdateNativeSideProperties = false;

	public static final String PROXY_ID_PREFIX = "proxy$";

    protected boolean mProcessInUIThread = false;
    private boolean needsToUpdateNativeSideProps = false; 
    
    
    private String mCustomState;
    private String mCurrentState;
    private HashMap mStates;
    private HashMap mCurrentStateValues;

	/**
     * The default KrollProxy constructor. Equivalent to
     * <code>KrollProxy("")</code>
     * 
	 * @module.api
	 */
    public KrollProxy() {
		this("");
	}

	/**
	 * Constructs a KrollProxy, using the passed in creation URL.
     * 
     * @param baseCreationUrl
     *            the creation URL for this proxy, which can be used to resolve
     *            relative paths
	 * @module.api
	 */
    public KrollProxy(String baseCreationUrl) {
		creationUrl = new TiUrl(baseCreationUrl);
		this.listenerIdGenerator = new AtomicInteger(0);
        this.eventListeners = Collections.synchronizedMap(new HashMap<String, ArrayList<Object>>());
		this.langConversionTable = getLangConversionTable();
	}

    protected void setupProxy(KrollObject object, Object[] creationArguments,
            TiUrl creationUrl) {
        if (object != null) {
            // Store reference to the native object that represents this proxy
            // so we can drive changes to the JS
		// object
		krollObject = object;
            krollObjectSupported = true;
		object.setProxySupport(this);
        }
		this.creationUrl = creationUrl;

        // Associate the activity with the proxy. if the proxy needs activity
        // association delayed until a
        // later point then initActivity should be overridden to be a no-op and
        // then call setActivity directly
		// at the appropriate time
		initActivity(TiApplication.getAppRootOrCurrentActivity());

        // Setup the proxy according to the creation arguments TODO - pass in
        // createdInModule
		handleCreationArgs(null, creationArguments);
	}

	// entry point for generator code
    public static KrollProxy createProxy(
            Class<? extends KrollProxy> proxyClass, KrollObject object,
            Object[] creationArguments, String creationUrl) {
		try {
			KrollProxy proxyInstance = proxyClass.newInstance();
            proxyInstance.setupProxy(object, creationArguments,
                    TiUrl.createProxyUrl(creationUrl));
			return proxyInstance;

		} catch (Exception e) {
			Log.e(TAG, ERROR_CREATING_PROXY, e);
		}

		return null;
	}

    public static KrollProxy createProxy(Class<? extends KrollProxy> proxyClass, Object props) {
           return createProxy(proxyClass, props, null);
	}
    public static KrollProxy createProxy(Class<? extends KrollProxy> proxyClass, Object props, KrollProxy rootProxy) {
        try {
            KrollProxy proxyInstance = proxyClass.newInstance();
            // Associate the activity with the proxy. if the proxy needs activity
            // association delayed until a
            // later point then initActivity should be overridden to be a no-op and
            // then call setActivity directly
            // at the appropriate time
//            proxyInstance.initActivity(TiApplication.getInstance().getCurrentActivity());

            if (props instanceof HashMap) {
                proxyInstance.handleCreationDict((HashMap) props, rootProxy);
            }
            return proxyInstance;

        } catch (Exception e) {
            Log.e(TAG, ERROR_CREATING_PROXY, e);
        }

        return null;
    }

    protected void initActivity(Activity activity) {
        setActivity(activity);
    }

    public void setActivity(Activity activity) {
        if (activity != null) {
            this.activity = new WeakReference<Activity>(activity);
        } else {
            this.activity = null;
//            this.activity = new WeakReference<Activity>(TiApplication.getAppRootOrCurrentActivity());
            // our proxy must always be associated with a valid activity
            //if (activity == null) {
            //    initActivity(TiApplication.getAppRootOrCurrentActivity());
            //    return;
            //}
        }
	}

	public void attachActivityLifecycle(Activity activity)
	{
		setActivity(activity);
		((TiBaseActivity) activity).addOnLifecycleEventListener(this);
	}


	/**
	 * @return the activity associated with this proxy.
	 * @module.api
	 */
	public Activity getActivity()
	{
		// our proxy must always be associated with a valid activity
		if (this.activity == null || this.activity.get() == null) {
//			setActivity(TiApplication.getAppRootOrCurrentActivity());
		    return null;
		}
		return this.activity.get();
	}

	/**
     * Handles the arguments passed into the "create" method for this proxy. If
     * your proxy simply needs to handle a HashMap, see
     * {@link KrollProxy#handleCreationDict(HashMap)}
     * 
	 * @param args
	 * @module.api
	 */
    public void handleCreationArgs(KrollModule createdInModule, Object[] args) {
		this.createdInModule = createdInModule;

        if (args == null || args.length == 0 || !(args[0] instanceof HashMap)) {
			handleDefaultValues();
			return;
		}

        HashMap dict = null;
        if (args[0] instanceof HashMap) {
            dict = (HashMap) args[0];
		}
		handleCreationDict(dict);
	}

	/**
	 * Handles initialization of the proxy's default property values.
     * 
	 * @module.api
	 */
    protected void handleDefaultValues() {
//        synchronized (properties) {
		for (String key : defaultValues.keySet()) {
                if (!hasProperty(key)) {
				setProperty(key, defaultValues.get(key));
			}
		}
//        }

	}

    public Object getDefaultValue(String key) {
        return defaultValues.get(key);
	}

	/**
     * @return the language conversion table used to load localized values for
     *         certain properties from the locale files. For each localizable
     *         property, such as "title," the proxy should define a second
     *         property, such as "titleid", used to specify a localization key
     *         for that property. If the user specifies a localization key in
     *         "titleid", the corresponding localized text from the locale file
	 *	is used for "title."
	 *
     *         Subclasses should override this method to return a table mapping
     *         localizable properties to the corresponding localization key
     *         properties.
	 *
     *         For example, if the proxy has two properties, "title" and "text",
     *         and the corresponding localization key properties are "titleid"
     *         and "textid", this might look like: </br>
	 *
     *         <pre>
     * <code>protected KrollDict getLangConversionTable()
	 *{
	 *	KrollDict table = new KrollDict();
	 *	table.put("title", "titleid");
	 *	table.put("text", "textid");
	 *	return table;
     * }
     * </pre>
	 *
     *         </code>
	 *
	 * @module.api
	 *
	 */
    protected KrollDict getLangConversionTable() {
		return null;
	}

	/**
	 * Handles initialization of the proxy's locale string properties.
	 *
	 * @see #getLangConversionTable()
	 */
    private void handleLocaleProperties() {
		if (langConversionTable == null) {
			return;
		}

		/*
         * Iterate through the language conversion table. This table maps target
         * properties to their locale lookup property. Example: title -> titleid
		 *
         * The lookup identifier stored in the locale property (titleid) will be
         * used to query the locale strings file to get the localized value.
		 * This localized value will be set to the targeted property (title).
		 */
//        synchronized (properties) {
            for (Map.Entry<String, Object> entry : langConversionTable
                    .entrySet()) {
			// Get the lookup identifier stored in the locale property.
			String localeProperty = entry.getValue().toString();
                String lookupId;
                synchronized (properties) {
                    lookupId = properties.getString(localeProperty);
                }
			if (lookupId == null) {
                    // If no locale lookup identifier is provided, skip this
                    // entry.
				continue;
			}

			// Lookup the localized string from the locale file.
			String localizedValue = getLocalizedText(lookupId);
			if (localizedValue == null) {
				// If there is no localized value for this identifier,
				// log a warning and skip over the entry.
                    Log.w(TAG, "No localized string found for identifier: "
                            + lookupId);
				continue;
			}

			// Set the localized value to the targeted property.
			String targetProperty = entry.getKey();
			setProperty(targetProperty, localizedValue);
		}
//        }

	}

	/**
     * Updates the lookup identifier value of a locale property. This will also
     * update the targeted value with the string found using the new lookup
     * identifier.
	 *
     * @param localeProperty
     *            name of the locale property (example: titleid)
     * @param newLookupId
     *            the new lookup identifier
     * @return a pair containing the name of the target property which was
     *         updated and the new value set on it.
	 */
    public Pair<String, String> updateLocaleProperty(String localeProperty,
            String newLookupId) {
		if (langConversionTable == null) {
			return null;
		}

        synchronized (properties) {
		properties.put(localeProperty, newLookupId);
        }

		// Determine which localized property this locale property updates.
		for (Map.Entry<String, Object> entry : langConversionTable.entrySet()) {
			if (entry.getValue().toString().equals(localeProperty)) {
				String targetProperty = entry.getKey();
				String localizedValue = getLocalizedText(newLookupId);
				if (localizedValue == null) {
					return null;
				}
				setProperty(targetProperty, localizedValue);

				return Pair.create(targetProperty, localizedValue);
			}
		}

		// If we reach this point, the provided locale property is not valid.
		return null;
	}


	/**
	 * Return true if the given property is a locale property.
     * 
     * @param propertyName
     *            name of the property to check (ex: titleid)
	 * @return true if this property is a locale property
	 */
    public boolean isLocaleProperty(String propertyName) {
		return propertyName.endsWith("id");
	}

	/**
	 * Looks up a localized string given an identifier.
	 *
     * @param lookupId
     *            the identifier of the localized value to look up.
	 * @return the localized string if found, otherwise null.
	 */
    private String getLocalizedText(String lookupId) {
		try {
			int resid = TiRHelper.getResource("string." + lookupId);
			if (resid != 0) {
				return getActivity().getString(resid);
			}
			return null;

		} catch (TiRHelper.ResourceNotFoundException e) {
			return null;
		}
	}

	/**
     * Handles the creation {@link KrollDict} passed into the create method for
     * this proxy. This is usually the first (and sometimes only) argument to
     * the proxy's create method.
	 *
     * To set default property values, add them to the
     * {@link KrollProxy#defaultValues map}
     * 
	 * @param dict
	 * @module.api
	 */
    public void handleCreationDict(HashMap dict) {
        handleCreationDict(dict, null);
    }
    
    public void handleCreationDict(HashMap dict, KrollProxy rootProxy) {
        synchronized (properties) {
            properties.clear();
        }
		if (dict == null) {
			return;
		}

        synchronized (properties) {
            properties.putAll(dict);
        }
        handleDefaultValues();
        handleLocaleProperties();
        
        if (dict.get("states") instanceof HashMap) {
            setStates((HashMap) dict.get("states"));
//            dict.remove(TiC.PROPERTY_BUBBLE_PARENT);
        }
		if (dict.containsKey(TiC.PROPERTY_BUBBLE_PARENT)) {
            bubbleParent = TiConvert.toBoolean(dict,
                    TiC.PROPERTY_BUBBLE_PARENT, true);
            bubbleParentDefined = true;
//            dict.remove(TiC.PROPERTY_BUBBLE_PARENT);
		}
        if (dict.containsKey(TiC.PROPERTY_BIND_ID)) {
            mBindId = TiConvert.toString(dict, TiC.PROPERTY_BIND_ID);
//            dict.remove(TiC.PROPERTY_BIND_ID);
        }

        if (dict.containsKey(TiC.PROPERTY_SYNCEVENTS)) {
            setSyncEvents(TiConvert.toStringArray(dict .get(TiC.PROPERTY_SYNCEVENTS)));
		}

		if (dict.containsKey(TiC.PROPERTY_LIFECYCLE_CONTAINER)) {
			KrollProxy lifecycleProxy = (KrollProxy) dict.get(TiC.PROPERTY_LIFECYCLE_CONTAINER);
			if (lifecycleProxy instanceof TiWindowProxy) {
				ActivityProxy activityProxy = ((TiWindowProxy) lifecycleProxy).getWindowActivityProxy();
				if (activityProxy != null) {
					attachActivityLifecycle(activityProxy.getActivity());
				} else {
					((TiWindowProxy) lifecycleProxy).addProxyWaitingForActivity(this);
				}
			} else if (lifecycleProxy instanceof ActivityProxy) {
                    attachActivityLifecycle(lifecycleProxy.getActivity());
			} else {
				Log.e(TAG, TiC.PROPERTY_LIFECYCLE_CONTAINER + " must be a WindowProxy or TabGroupProxy (TiWindowProxy)");
			}
		}

		if (modelListener != null) {
            modelListener.get().processProperties(dict);
		}
	}

    public Handler getMainHandler() {
		if (mainHandler == null) {
            mainHandler = new Handler(TiMessenger.getMainMessenger()
                    .getLooper(), this);
		}

		return mainHandler;
	}

    public Handler getRuntimeHandler() {
		if (runtimeHandler == null) {
            runtimeHandler = new Handler(TiMessenger.getRuntimeMessenger()
                    .getLooper(), this);
		}

		return runtimeHandler;
	}

	/**
	 * Invokes the given runnable on the main UI thread.
	 * <p>
	 * If this method was called on the main UI thread, then the given runnable is invoked immediately.
	 * Otherwise, the runnable is posted to the main UI thread via the main "Handler".
	 * <p>
	 * This method is the equivalent to the Android Activity.runOnUiThread() method.
	 * @param runnable The runnable to be invoked. Can be null, in which case this method will no-op.
	 */
	public void runOnMainThread(Runnable runnable)
	{
		runWithHandler(runnable, getMainHandler());
	}

	/**
	 * Invokes the given runnable on the JavaScript runtime thread.
	 * <p>
	 * If this method was called on the runtime thread, then the given runnable is invoked immediately.
	 * Otherwise, the runnable is posted to the runtime thread via a "Handler".
	 * @param runnable The runnable to be invoked. Can be null, in which case this method will no-op.
	 */
	public void runOnRuntimeThread(Runnable runnable)
	{
		runWithHandler(runnable, getRuntimeHandler());
	}

	/**
	 * Invokes the given runnable on the thread the given handler is attached to.
	 * <p>
	 * If this method was called on the handler's thread, then the runnable is invoked immediately.
	 * Otherwise, handler.post() is called with the given runnable to be invoked on its thread.
	 * @param runnable The runnable to be invoked. Can be null, in which case this method will no-op.
	 * @param handler The handler to be used. Can be null, which case this method will no-op.
	 */
	private void runWithHandler(Runnable runnable, Handler handler)
	{
		if ((runnable != null) && (handler != null)) {
			Looper looper = handler.getLooper();
			if ((looper != null) && (looper.getThread().getId() == Thread.currentThread().getId())) {
				runnable.run();
			} else {
				handler.post(runnable);
			}
		}
	}

	public void setKrollObject(KrollObject object)
	{
		this.krollObject = object;
	}

    protected void updateNativePropsIfNecessary() {
        if (krollObject == null) {
            return;
        }
        if (needsToUpdateNativeSideProps) {
            needsToUpdateNativeSideProps = false;
            doUpdateKrollObjectProperties();
        } else if (propertiesToUpdateNativeSide != null) {
            doUpdateKrollObjectProperties(propertiesToUpdateNativeSide);
            propertiesToUpdateNativeSide = null;
        }
    }
    
    private boolean isRuntimeThread() {
        return KrollRuntime.getInstance().isRuntimeThread();
    }

	/**
     * @return the KrollObject associated with this proxy if it exists.
     *         Otherwise create it in the KrollRuntime thread.
	 * @module.api
	 */
    public KrollObject getKrollObject() {
        final boolean runtimeThread = isRuntimeThread();
		if (krollObject == null) {
            if (runtimeThread) {
				initKrollObject();

			} else {
                TiMessenger.sendBlockingRuntimeMessage(getRuntimeHandler()
                        .obtainMessage(MSG_INIT_KROLL_OBJECT));
			}
		}
        if (runtimeThread) {
            updateNativePropsIfNecessary();
        }
		return krollObject;
	}

    public void initKrollObject() {
        if (!KrollRuntime.isDisposed()) {
		KrollRuntime.getInstance().initObject(this);
	}
    }

	/**
     * @return the absolute URL of the location in code where the proxy was
     *         created in Javascript.
	 * @module.api
	 */
    public TiUrl getCreationUrl() {
		return creationUrl;
	}

	@Kroll.method
    public void setCreationUrl(String url) {
		creationUrl = TiUrl.createProxyUrl(url);
	}

    // native extending support allows us to whole-sale apply properties and
    // only fire one event / job
	@Kroll.method
    public void extend(KrollDict options) {
		if (options == null || options.isEmpty()) {
			return;
		}
        applyPropertiesInternal(options, false, true);
	}

    private void firePropertiesChanged(Object[][] changes) {
		if (modelListener == null) {
			return;
		}

		int changesLength = changes.length;
        // if (modelListener != null) {
        // modelListener.propertyChanged((String) name, change[INDEX_OLD_VALUE],
        // change[INDEX_VALUE], this);
        // }
		for (int i = 0; i < changesLength; ++i) {
			Object[] change = changes[i];
			if (change.length != 3) {
				continue;
			}

			Object name = change[INDEX_NAME];
			if (name == null || !(name instanceof String)) {
				continue;
			}

			if (modelListener != null) {
                modelListener.get().propertyChanged((String) name,
                        change[INDEX_OLD_VALUE], change[INDEX_VALUE], this);
			}
		}
        // if (modelListener != null) {
        // modelListener.propertyChanged((String) name, change[INDEX_OLD_VALUE],
        // change[INDEX_VALUE], this);
        // }
	}

    public Object getIndexedProperty(int index) {
		// TODO(josh): return undefined value
		return 0;
	}

    public void setIndexedProperty(int index, Object value) {
		// no-op
	}

	/**
     * @param name
     *            the lookup key.
	 * @return  true if the proxy contains this property, false otherwise.
	 * @module.api
	 */
    public boolean hasProperty(String name) {
        synchronized (properties) {
			return properties.containsKey(name);
		}
	}

	/**
	 * @param name  the lookup key.
	 * @return  true if the proxy contains this property and it is not null, false otherwise.
	 * @module.api
	 */
	public boolean hasPropertyAndNotNull(String name)
	{
        synchronized (properties) {
			return properties.containsKeyAndNotNull(name);
		}
	}

	/**
     * Returns the property value given its key. Properties are cached on the
     * Proxy and updated from JS for relevant annotated APIs
     * 
     * @param name
     *            the lookup key.
     * @return the property object or null if a property for the given key does
     *         not exist.
	 * @module.api
	 */
    public Object getProperty(String name) {
        if (setPropertyListener != null) {
            Object value = setPropertyListener.onGetProperty(this, name);
            if (value != null) {
                return value;
            }
        }
        synchronized (properties) {
			return properties.get(name);
		}
	}

    public Object getProperty(String name, boolean lookJS) {
        synchronized (properties) {
            Object value = properties.get(name);
            if (!lookJS || value != null) {
                return value;
            }
            return getJsPropertySync(name);
        }
    }

	/**
     * Returns the property value given its key. Properties are cached on the
     * Proxy and updated from JS for relevant annotated APIs
     * 
     * @param name
     *            the lookup key.
     * @return the property object or null if a property for the given key does
     *         not exist.
	 * @module.api
	 */
    public Object getProperty(String name, Object defaultValue) {
        synchronized (properties) {
            if (properties.containsKey(name)) {
                return properties.get(name);
		}
        }
        return defaultValue;
    }

//    /**
//     * @deprecated use setPropertyAndFire instead
//     */
//    @Deprecated
//    public void setProperty(String name, Object value, boolean fireChange) {
//        if (!fireChange) {
//            setProperty(name, value);
//
//        } else {
//            setPropertyAndFire(name, value);
//        }
//    }

    public void propagateSetProperty(String name, Object value) {
        if (setPropertyListener != null) {
            setPropertyListener.onSetProperty(this, name, value);
        }
    }

    /**
     * This sets the named property WITHOUT updating the actual JS object.
     * 
     * @module.api
     */
    public void setPropertyJava(String name, Object value) {
        synchronized (properties) {
		properties.put(name, value);
        }

        // That line is for listitemproxy to update its data
        propagateSetProperty(name, value);
    }

    /**
     * This sets the named property as well as updating the actual JS object.
     * 
     * @module.api
     */
    public void setProperty(String name, Object value, boolean wait) {
        setPropertyJava(name, value);

        if (wait && isRuntimeThread()) {
			doSetProperty(name, value);
		} else {
            if (wait && !TiApplication.appRunOnMainThread()) {
                Message message = getRuntimeHandler().obtainMessage(
                        MSG_SET_PROPERTY, value);
			message.getData().putString(PROPERTY_NAME, name);
                TiMessenger.sendBlockingRuntimeMessage(message);
            } else {
                Message message = getRuntimeHandler().obtainMessage(
                        MSG_SET_PROPERTY, value);
                message.getData().putString(PROPERTY_NAME, name);
			message.sendToTarget();
		}
	}
    }
    public void setProperty(String name, Object value) {
        setProperty(name, value, false);
    }
    public void updateKrollObjectProperties() {

        //use a shallow copy because properties is synchronized
        if (krollObject == null && isRuntimeThread()) {
            initKrollObject();
        }
        if (krollObject != null) {
            internalUpdateKrollObjectProperties(properties, true);
        } else {
            needsToUpdateNativeSideProps  = true;
        }
        propertiesToUpdateNativeSide = null;
        readyToUpdateNativeSideProperties = true;
    }

    public void updateKrollObjectProperties(HashMap<String, Object> props) {
        updateKrollObjectProperties(props, true);
    }
    public void updateKrollObjectProperties(HashMap<String, Object> props, final boolean wait) {
        if (krollObject != null) {
            internalUpdateKrollObjectProperties(props, wait);
        } else {
            propertiesToUpdateNativeSide  = props;
        }
    }   

    public void internalUpdateKrollObjectProperties(
            HashMap<String, Object> props, final boolean wait) {
        long startTime = System.currentTimeMillis();
        if (wait && isRuntimeThread()) {
            doUpdateKrollObjectProperties(props);

        } else {
            if (wait && !TiApplication.appRunOnMainThread()) {
                Message msg = getRuntimeHandler()
                        .obtainMessage(MSG_UPDATE_KROLL_PROPERTIES);
                TiMessenger.sendBlockingRuntimeMessage(msg, props);
            } else {
                Message message = getRuntimeHandler().obtainMessage(
                        MSG_UPDATE_KROLL_PROPERTIES, new HashMap(props));
                message.sendToTarget();
            }
        }
        long endTime = System.currentTimeMillis();
        long diff = endTime - startTime;
        if (diff > 20) {
            Log.w(TAG, "propertySet " + diff + "ms");
        }
    }

    /**
     * This sets the named property as well as updating the actual JS object.
     * 
     * @module.api
     */
    public void setProperties(HashMap newProps) {
        KrollDict realProperties = (newProps != null) ? new KrollDict(newProps)
                : new KrollDict();
        synchronized (properties) {
            for (String key : properties.keySet()) {
                if (!realProperties.containsKey(key)) {
                    realProperties.put(key, null);
                }
            }
        }

        KrollDict changedProps = new KrollDict();
        for (Object key : realProperties.keySet()) {
            String name = TiConvert.toString(key);
            Object value = realProperties.get(key);
            Object current = getProperty(name);
            if (shouldFireChange(current, value)) {
                changedProps.put(name, value);
                onPropertyChanged(name, value, current);
            }
        }
        
        synchronized (properties) {
            properties.clear();
            if (newProps != null)
                properties.putAll(newProps);
        }
        
        internalApplyModelProperties(changedProps);
    }
    
    public void internalApplyModelProperties(final HashMap changedProps) {
        if (modelListener != null) {
            if (!mProcessInUIThread || TiApplication.isUIThread()) {
                modelListener.get().processApplyProperties(changedProps);
            } else {
                Message message = getMainHandler().obtainMessage(
                        MSG_MODEL_APPLY_PROPERTIES, changedProps);
                message.sendToTarget();
            }
        }
    }

	public class KrollPropertyChangeSet extends KrollPropertyChange {
		public int entryCount;
		public String[] keys;
		public Object[] oldValues;
		public Object[] newValues;

		public KrollPropertyChangeSet(int capacity) {
			super(null, null, null);
			entryCount = 0;
			keys = new String[capacity];
			oldValues = new Object[capacity];
			newValues = new Object[capacity];
		}

		public void addChange(String key, Object oldValue, Object newValue){
			keys[entryCount] = key;
			oldValues[entryCount] = oldValue;
			newValues[entryCount] = newValue;
			entryCount++;
		}

		public void fireEvent(KrollProxy proxy, KrollProxyListener listener) {
			if (listener == null) {
				return;
			}
			for (int i = 0; i < entryCount; i++) {
                listener.propertyChanged(keys[i], oldValues[i], newValues[i],
                        proxy);
			}
		}
	}

    public void applyPropertiesNoSave(Object arg, boolean force, boolean wait) {
		if (!(arg instanceof HashMap)) {
            Log.w(TAG, "Cannot apply properties: invalid type for properties",
                    Log.DEBUG_MODE);
			return;
		}
        HashMap<String, Object> props = (HashMap<String, Object>) arg;
        KrollDict changedProps = new KrollDict();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            Object current = getProperty(name);
            if (current instanceof KrollProxy && value instanceof HashMap) {
                // we handle binded objects (same as done with listitems)
                ((KrollProxy) current).applyPropertiesNoSave(value, force,
                        wait);
            } else {
                if (name.equals(TiC.PROPERTY_BUBBLE_PARENT)) {
                } else if (name.equals(TiC.PROPERTY_BIND_ID)) {
                } else if (name.equals(TiC.PROPERTY_SYNCEVENTS)) {
                } else if (force || shouldFireChange(current, value)) {
                    changedProps.put(name, value);
			}
            }
        }
        internalApplyModelProperties(changedProps);
    }

    public void applyPropertiesInternal(Object arg, boolean force, boolean wait, boolean needsToUpdateNativeSide) {
        
        if (!(arg instanceof HashMap)) {
            Log.w(TAG, "Cannot apply properties: invalid type for properties",
                    Log.DEBUG_MODE);
			return;
		}
        if (setPropertyListener != null) {
            setPropertyListener.onApplyProperties(this, (HashMap)arg, force, wait);
            return;
        }
        HashMap<String, Object> props = (HashMap<String, Object>) arg;
        KrollDict changedProps = new KrollDict();
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
				Object current = getProperty(name);
            if (current instanceof KrollProxy && value instanceof HashMap) {
                // we handle binded objects (same as done with listitems)
                ((KrollProxy) current).applyPropertiesInternal(value, force,
                        wait, false);
            } else {
                if (name.equals(TiC.PROPERTY_BUBBLE_PARENT)) {
                    bubbleParent = TiConvert.toBoolean(value, true);
                    bubbleParentDefined = true;
                } else if (name.equals(TiC.PROPERTY_BIND_ID)) {
                    mBindId = TiConvert.toString(value);
                } else if (name.equals(TiC.PROPERTY_SYNCEVENTS)) {
                    setSyncEvents(TiConvert.toStringArray(value));
                } else if (force || shouldFireChange(current, value)) {
                    setPropertyJava(name, value);
                    changedProps.put(name, value);
                    onPropertyChanged(name, value, current);
				}
			}
		}
        internalApplyModelProperties(changedProps);
        if (needsToUpdateNativeSide) {
            updateKrollObjectProperties(props, wait);
		}
	}
    public void applyPropertiesInternal(Object arg, boolean force, boolean wait) {
        applyPropertiesInternal(arg, force, wait, true);
		}

    public void applyPropertiesInternal(Object arg, boolean force) {
        applyPropertiesInternal(arg, force, false);
	}

    @Kroll.method(name="_applyProperties")
    public void applyProperties(Object arg, @Kroll.argument(optional = true) Object options) {
        boolean wait = false;
        if (options instanceof HashMap) {
            wait = TiConvert.toBoolean((HashMap) options, "wait", wait);
        }
        applyPropertiesInternal(arg, false, wait, false);
    }
    
    public void applyProperties(Object arg) {
        applyProperties(arg, null);
	}

	/**
	 * Asynchronously calls a function referenced by a property on this object.
	 * This may be called safely on any thread.
	 *
	 * @see KrollObject#callProperty(String, Object[])
     * @param name
     *            the property that references the function
     * @param args
     *            the arguments to pass when calling the function.
	 */
	public void callPropertyAsync(String name, Object[] args) {
        Message msg = getRuntimeHandler().obtainMessage(
                MSG_CALL_PROPERTY_ASYNC, args);
		msg.getData().putString(PROPERTY_NAME, name);
		msg.sendToTarget();
	}

	/**
	 * Synchronously calls a function referenced by a property on this object.
	 * This may be called safely on any thread.
	 *
	 * @see KrollObject#callProperty(String, Object[])
     * @param name
     *            the property that references the function
     * @param args
     *            the arguments to pass when calling the function.
	 */
    public void callPropertySync(String name, Object[] args) {
		if (isRuntimeThread()) {
			getKrollObject().callProperty(name, args);
		} else {
            Message msg = getRuntimeHandler().obtainMessage(
                    MSG_CALL_PROPERTY_SYNC);
			msg.getData().putString(PROPERTY_NAME, name);
			TiMessenger.sendBlockingRuntimeMessage(msg, args);
		}
	}

    public Object getJsPropertySync(String name) {
        if (isRuntimeThread()) {
            return getKrollObject().getProperty(name);
        } else {
            Message msg = getRuntimeHandler().obtainMessage(
                    MSG_GET_PROPERTY);
            msg.getData().putString(PROPERTY_NAME, name);
            return TiMessenger.sendBlockingRuntimeMessage(msg);
	}
    }

    protected void doSetProperty(String name, Object value) {
        KrollObject obj =  getKrollObject();
        if (obj != null) {
            obj.setProperty(name, value);
	}
    }

    protected void doUpdateKrollObjectProperties() {
        if (krollObject != null) {
            krollObject.updateNativeProperties(properties);
	}
    }

    protected void doUpdateKrollObjectProperties(HashMap<String, Object> props) {
        for (Map.Entry<String, Object> entry : props.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof KrollProxy) {
                ((KrollProxy) value).updateNativePropsIfNecessary();
            }
        }
        if (krollObject != null) {
            long startTime = System.currentTimeMillis();
            krollObject.updateNativeProperties(props);
            long endTime = System.currentTimeMillis();
            long diff = endTime - startTime;
            if (diff > 20) {
                Log.w(TAG, "doUpdateKrollObjectProperties " + diff + "ms, "
                        + this.getClass().toString());
            }
        }
    }

//    @Kroll.getProperty
//    @Kroll.method
//    public boolean getBubbleParent() {
//        return bubbleParent;
//    }
//
//    @Kroll.setProperty
//    @Kroll.method
//    public void setBubbleParent(Object value) {
//        bubbleParent = TiConvert.toBoolean(value);
//    }

	/**
     * Fires an event asynchronously via KrollRuntime thread, which can be
     * intercepted on JS side.
     * 
     * @param event
     *            the event to be fired.
     * @param data
     *            the data to be sent.
	 * @return whether this proxy has an eventListener for this event.
	 * @module.api
	 */
    public boolean fireEvent(String event) {
        return fireEvent(event, null, false, true);
    }

    /**
     * Fires an event asynchronously via KrollRuntime thread, which can be
     * intercepted on JS side.
     * 
     * @param event
     *            the event to be fired.
     * @param data
     *            the data to be sent.
     * @return whether this proxy has an eventListener for this event.
     * @module.api
     */
    @Kroll.method
    public boolean fireEvent(String event,
            @Kroll.argument(optional = true) Object data) {
        boolean bubbles = false;
        if (data instanceof HashMap) {
            bubbles = TiConvert.toBoolean((HashMap<String, Object>) data, TiC.PROPERTY_BUBBLES, bubbles);
        }
        return fireEvent(event, data, bubbles, true);
    }
    
    //alias
    @Kroll.method
    public boolean emit(String event,
            @Kroll.argument(optional = true) Object data) {
        return fireEvent(event, data);
    }

    /**
     * Send an event to the view who is next to receive the event.
     * 
     * @param eventName
     *            event to send to the next view
     * @param data
     *            the data to include in the event
     * @return true if the event was handled
     */
    @Kroll.method(name = "_addEvaluator")
    public int addEvaluator(String eventName, Object data) {
        if (eventName == null) {
            throw new IllegalStateException(
                    "addEvaluator expects a non-null eventName");

        } else if (!(data instanceof HashMap)) {
            throw new IllegalStateException(
                    "addEvaluator expects a non-null listener");
        }
        
        if(evaluators==null){
            evaluators = new HashMap<String, List<KrollDict>>();
            setProperty(PROPERTY_HAS_JAVA_LISTENER, true);
        }

        synchronized (evaluators) {
            List<KrollDict> theListeners = evaluators.get(eventName);
            if (theListeners == null) {
                theListeners = new ArrayList< KrollDict>();
                evaluators.put(eventName, theListeners);
            }
            theListeners.add(TiConvert.toKrollDict(data));
            eventListenerAdded(eventName, theListeners.size(), this);
            return theListeners.size();
        }
    }
    
    public boolean equals(Object object1, Object object2) {
        if (object1 == object2) {
			return true;
		}
        if (object1 instanceof Map && object2 instanceof Map) {
            Map<?, ?> map1 = (Map<?, ?>) object1;
            Map<?, ?> map2 = (Map<?, ?>) object2;
            if (map2.size() != map1.size()) {
                return false;
            }

            try {
                for (Entry<?, ?> entry : map2.entrySet()) {
                    Object key = entry.getKey();
                    Object mine = entry.getValue();
                    Object theirs = map1.get(key);
                    if (mine == null) {
                        if (theirs != null || !map1.containsKey(key)) {
		return false;
	}
                    } else {
                        if (mine instanceof Object[] && theirs instanceof Object[]) {
                            if (!Arrays.equals((Object[])mine, (Object[])theirs)) {
                                return false;
                            }
                        } else if (!mine.equals(theirs)) {
                            return false;
                        }
                    }
                }
            } catch (NullPointerException ignored) {
                return false;
            } catch (ClassCastException ignored) {
                return false;
            }
            return true;
        } else if (object1 instanceof Object[] && object2 instanceof Object[]) {
            return Arrays.equals((Object[])object1, (Object[])object2);
        }
        return false;
    }

    @Kroll.method(name = "_removeEvaluator")
    public void removeEvaluator(String eventName, Object data) {
        if (eventName == null || !(data instanceof HashMap) || evaluators == null) {
            return;
        }
        synchronized (evaluators) {
            List<KrollDict> theListeners = evaluators.get(eventName);
            
            if (theListeners != null) {
                for (KrollDict hashMap : theListeners) {
                    if (equals(data, hashMap)) {
                        theListeners.remove(hashMap);
                        break;
                    }
                }
                eventListenerRemoved(eventName, theListeners.size(), this);
                if (theListeners.isEmpty()) {
                    evaluators.remove(eventName);
                }
                if (evaluators.isEmpty()) {
                    setProperty(PROPERTY_HAS_JAVA_LISTENER, false);
                }
            }
        }
    }
    
	/**
	 * Send an event to the view who is next to receive the event.
	 *
     * @param eventName
     *            event to send to the next view
     * @param data
     *            the data to include in the event
	 * @return true if the event was handled
	 */
	@Kroll.method(name = "_fireEventToParent")
    public boolean fireEventToParent(String eventName, Object data) {
		if (bubbleParent) {
			KrollProxy parentProxy = getParentForBubbling();
			if (parentProxy != null) {
                return parentProxy.fireEvent(eventName, data, true, false);
			}
		}
		return false;
	}

	/**
     * Fires an event synchronously via KrollRuntime thread, which can be
     * intercepted on JS side.
     * 
     * @param event
     *            the event to be fired.
     * @param data
     *            the data to be sent.
	 * @return whether this proxy has an eventListener for this event.
	 * @module.api
	 */
    public boolean fireSyncEvent(String event, Object data) {
        return fireSyncEvent(event, data, false);
    }

    public boolean fireSyncEvent(String event, Object data, final boolean bubble) {
        return fireSyncEvent(event, data, false, true);

    }

    /**
     * Fires an event synchronously via KrollRuntime thread, which can be
     * intercepted on JS side.
     * 
     * @param event
     *            the event to be fired.
     * @param data
     *            the data to be sent.
     * @return whether this proxy has an eventListener for this event.
     * @module.api
     */
    public boolean fireSyncEvent(String event, Object data,
            final boolean bubble, boolean checkListeners) {
        if (checkListeners && !hasListeners(event, bubble)) {
            return false;
        }
		if (isRuntimeThread()) {
            return doFireEvent(event, data, bubble);

		} else {
            Message message = getRuntimeHandler().obtainMessage(
                    MSG_FIRE_SYNC_EVENT);
			message.getData().putString(PROPERTY_NAME, event);
            message.getData().putBoolean(PROPERTY_BUBBLES, bubble);

            return (Boolean) TiMessenger.sendBlockingRuntimeMessage(message,
                    data);
		}
	}

	/**
     * Fires an event synchronously via KrollRuntime thread, which can be
     * intercepted on JS side.
     * 
     * @param event
     *            the event to be fired.
     * @param data
     *            the data to be sent.
     * @param maxTimeout
     *            the maximum time to wait for the result to return, in the unit
     *            of milliseconds.
	 * @return whether this proxy has an eventListener for this event.
	 * @module.api
	 */
    public boolean fireSyncEvent(String event, Object data, long maxTimeout) {
        if (!hasListeners(event, true)) {
            return false;
        }
		if (isRuntimeThread()) {
			return doFireEvent(event, data);

		} else {
            Message message = getRuntimeHandler().obtainMessage(
                    MSG_FIRE_SYNC_EVENT);
			message.getData().putString(PROPERTY_NAME, event);

            Object result = TiMessenger.sendBlockingRuntimeMessage(message,
                    data, maxTimeout);
			return TiConvert.toBoolean(result, false);
		}
	}

    /**
     * Fires an event that can optionally be "bubbled" to the parent view.
     * 
     * @param eventName
     *            event to get dispatched to listeners
     * @param data
     *            data to include in the event
     * @param bubbles
     *            if true will send the event to the parent view after it has
     *            been dispatched to this view's listeners.
     * @return true if the event was handled
     */
    public boolean fireEvent(String event, Object data, boolean bubbles) {
        return fireEvent(event, data, bubbles, true);
    }

    /**
     * Fires an event asynchronously via KrollRuntime thread, which can be
     * intercepted on JS side.
     * 
     * @param event
     *            the event to be fired.
     * @param data
     *            the data to be sent.
     * @param bubbles
     *            should bubble to parent.
     * @param checkListeners
     *            should check for Listeners. Optimisation if the check was
     *            already done
     * @return whether the message is sent.
     * @module.api
     */
    public boolean fireEvent(String event, Object data, boolean bubbles,
            boolean checkListeners) {
        if (mSyncEvents != null && mSyncEvents.contains(event)) {
            return fireSyncEvent(event, data, bubbles, checkListeners);
        }
        if (checkListeners && !hasListeners(event, bubbles)) {
			return false;
		}
        if (bubbleParentDefined) {
            bubbles = bubbleParent;
        }
        if (eventOverrideDelegate != null) {
            data = eventOverrideDelegate.get().overrideEvent(data, event, this);
        }
			HashMap<String, Object> dict = (HashMap) data;
        Object sourceProxy = this;
			if (dict == null) {
            data = dict = new KrollDict();
            dict.put(TiC.EVENT_PROPERTY_SOURCE, sourceProxy);
			} else if (dict instanceof HashMap) {
            if (dict.containsKey(TiC.EVENT_PROPERTY_SOURCE)) {
                sourceProxy = dict.get(TiC.EVENT_PROPERTY_SOURCE);
            } else {
                dict.put(TiC.EVENT_PROPERTY_SOURCE, sourceProxy);
				}
			}
        if (sourceProxy instanceof KrollProxy) {
            Object bindId = ((KrollProxy) sourceProxy).getBindId();
            if (bindId != null) {
                dict.put(TiC.PROPERTY_BIND_ID, bindId);
		}
		}

        dict.put(TiC.EVENT_PROPERTY_TYPE, event);
        if (evaluators != null && data instanceof HashMap) {
            List<KrollDict> theListeners = null;
            synchronized (evaluators) {
                theListeners = evaluators.get(event);
				}
            if (theListeners != null) {
                for (KrollDict hashMap : theListeners) {
                    TiUIHelper.applyMathDict(hashMap, TiConvert.toKrollDict(data), (KrollProxy) sourceProxy);
				}
			}
		}
        if (!_hasListeners(event)) {
            //in this case no need to go through the native object, jni,... 
            if (bubbles) {
                fireEventToParent(event, data);
			}
            return true;
		}
        final boolean isRuntimeThread = isRuntimeThread();
//        
        if (isRuntimeThread) {
            doFireEvent(event, data, bubbles);
		} else {
            Message message = getRuntimeHandler().obtainMessage(MSG_FIRE_EVENT,
                    data);
            message.getData().putString(PROPERTY_NAME, event);
            message.getData().putBoolean(PROPERTY_BUBBLES, bubbles);
            message.sendToTarget();
		}
        
        return true;
	}
    
    public String getBindId() {
        return mBindId;
	}

    public boolean doFireEvent(String event, Object data, boolean bubbles) {
		boolean reportSuccess = false;
		int code = 0;
        KrollProxy source = this;
		String message = null;

        HashMap<String, Object> krollData = null;
		if (data != null) {
            if (data instanceof HashMap) {
                krollData = (HashMap) data;
			} else if (data instanceof JSONObject) {
				try {
					krollData = new KrollDict((JSONObject) data);
				} catch (Exception e) {
				}
			}

		if (krollData != null) {
                Object hashValue;
                for(Iterator<Map.Entry<String, Object>> it = krollData.entrySet().iterator(); it.hasNext(); ) {
                    Map.Entry<String, Object> entry = it.next();
                    switch ((String) entry.getKey()) {
                    case TiC.PROPERTY_BUBBLES:
                        bubbles &= TiConvert.toBoolean(entry.getValue());
                        it.remove();
                        break;
//                    case TiC.PROPERTY_SUCCESS:
//                        hashValue = entry.getValue();
//                        if (hashValue != null && reportSuccess == false) {
//                            reportSuccess = true;
//                            code = ((Boolean) hashValue).booleanValue()?0:-1;
//                        }
//                        it.remove();
//                        break;
//                    case TiC.PROPERTY_CODE:
//                        hashValue = entry.getValue();
//                        if (hashValue instanceof Integer) {
//                            code = ((Integer) hashValue).intValue();
//                            reportSuccess = true;
//                        } 
//                        it.remove();
//                        break;
//                    case TiC.EVENT_PROPERTY_ERROR:
//                        hashValue = entry.getValue();
//                        if (hashValue instanceof String) {
//                            message = (String) hashValue;
//                            it.remove();
//                        }
//                        break;
                    case TiC.PROPERTY_SOURCE:
                        source = null;
//                        hashValue = entry.getValue();
//        				if (hashValue != this) {
//        					source = ((KrollProxy)hashValue);
//        					source.getKrollObject();
//        					it.remove();
//        				}
//                        break;
                    default:
                        hashValue = entry.getValue();
			if (hashValue instanceof KrollProxy) {
                            //make sure the kroll object exists
                            ((KrollProxy) hashValue).getKrollObject();
			}
                        break;
					}
				}

//                Object hashValue = krollData.get(TiC.PROPERTY_BUBBLES);
//                if (hashValue != null) {
//                    bubbles &= TiConvert.toBoolean(hashValue);
//                    krollData.remove(TiC.PROPERTY_BUBBLES);
//                }
//                hashValue = krollData.get(TiC.PROPERTY_SUCCESS);
//                if (hashValue instanceof Boolean) {
//                    boolean successValue = ((Boolean) hashValue).booleanValue();
//                    hashValue = krollData.get(TiC.PROPERTY_CODE);
//                    if (hashValue instanceof Integer) {
//                        int codeValue = ((Integer) hashValue).intValue();
//                        if (successValue == (codeValue == 0)) {
//                            reportSuccess = true;
//                            code = codeValue;
//                            krollData.remove(TiC.PROPERTY_SUCCESS);
//                            krollData.remove(TiC.PROPERTY_CODE);
//                        } else {
//                            Log.w(TAG,
//                                    "DEPRECATION WARNING: Events with 'code' and 'success' should have success be true if and only if code is nonzero. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
//                                    Log.DEBUG_MODE);
//                        }
//                    } else if (successValue) {
//                        Log.w(TAG,
//                                "DEPRECATION WARNING: Events with 'success' of true should have an integer 'code' property that is 0. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
//                                Log.DEBUG_MODE);
//                    } else {
//                        Log.w(TAG,
//                                "DEPRECATION WARNING: Events with 'success' of false should have an integer 'code' property that is nonzero. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
//                                Log.DEBUG_MODE);
//                    }
//                } else if (hashValue != null) {
//                    Log.w(TAG,
//                            "DEPRECATION WARNING: The 'success' event property is reserved to be a boolean. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
//                            Log.DEBUG_MODE);
//                }
//                hashValue = krollData.get(TiC.EVENT_PROPERTY_ERROR);
//                if (hashValue instanceof String) {
//                    message = (String) hashValue;
//                    krollData.remove(TiC.EVENT_PROPERTY_ERROR);
//                } else if (hashValue != null) {
//                    Log.w(TAG,
//                            "DEPRECATION WARNING: The 'error' event property is reserved to be a string. For java modules, consider the putCodeAndMessage() method to do this for you. The capability to use other types will be removed in a future version.",
//                            Log.DEBUG_MODE);
//                }
//                hashValue = krollData.get(TiC.EVENT_PROPERTY_SOURCE);
//                if (hashValue instanceof KrollProxy) {
//                    if (hashValue != this) {
//                        source = ((KrollProxy) hashValue).getKrollObject();
//                    }
////                    krollData.remove(TiC.EVENT_PROPERTY_SOURCE);
//                }
			if (krollData.size() == 0) {
				krollData = null;
			}
		}
				}

        if (eventListeners != null && !eventListeners.isEmpty()) {
            ArrayList<Object> listeners = eventListeners.get(event);
            if (listeners != null) {
                for(int i = 0; i < listeners.size(); i++) {
                    Object callback = listeners.get(i);
                    if (callback instanceof KrollEventCallback) {
                        ((KrollEventCallback) callback).call(data);
                    } else if (callback instanceof KrollFunction) {
                        ((KrollFunction) callback).call(getKrollObject(), (HashMap) data);
//                        ((KrollEventCallback) callback).call(data);
			}
			}
		}
        }
        return getKrollObject().fireEvent(source, event, krollData, bubbles,
                reportSuccess, code, message);
    }

    public boolean doFireEvent(String event, Object data) {
        return doFireEvent(event, data, true);
	}

    public void firePropertyChanged(String name, Object oldValue,
            Object newValue) {
        onPropertyChanged(name, newValue, oldValue);
		if (modelListener != null) {
            if (!mProcessInUIThread || TiApplication.isUIThread()) {
                modelListener.get().propertyChanged(name, oldValue, newValue, this);

			} else {
                KrollPropertyChange pch = new KrollPropertyChange(name,
                        oldValue, newValue);
                getMainHandler().obtainMessage(MSG_MODEL_PROPERTY_CHANGE, pch)
                        .sendToTarget();
			}
		}
	}

    public void onHasListenersChanged(String event, boolean hasListeners) {
//        if (TiApplication.isUIThread()) {
            if (hasListeners) {
                eventListenerAdded(event, 1, this);

            } else {
                eventListenerRemoved(event, 0, this);
	}
//        } else {
//            Message msg = getMainHandler().obtainMessage(
//                    hasListeners ? MSG_LISTENER_ADDED : MSG_LISTENER_REMOVED);
//    		msg.obj = event;
//    		TiMessenger.getMainMessenger().sendMessage(msg);
//    	}
    }

	/**
     * @param event
     *            the event to check
     * @return whether the associated KrollObject has an event listener for the
     *         passed in event.
	 * @module.api
	 */
    public boolean hasListeners(String event) {
        return hasListeners(event, true);
	}

    public boolean _hasListeners(String event) {
        return hasNonJSEventListener(event)
                || hasEvaluatorListener(event)
                || (krollObject !=null) && krollObject.hasListeners(event);
    }

	/**
     * @param event
     *            the event to check
     * @return whether the associated KrollObject has an event listener for the
     *         passed in event.
     * @module.api
	 */
    public boolean hasListeners(String event, boolean checkParent) {
        boolean hasListener = _hasListeners(event);
        if (bubbleParentDefined) {
            checkParent = bubbleParent;
        }
		// Checks whether the parent has the listener or not
        if (checkParent && !hasListener) {
			KrollProxy parentProxy = getParentForBubbling();
			if (parentProxy != null && bubbleParent) {
                return parentProxy.hasListeners(event, true);
			}
		}

		return hasListener;
	}

    /**
     * Returns true if any view in the hierarchy has the event listener.
     */
    public boolean hierarchyHasListener(String event) {
        return hasListeners(event, true);
			}

    /**
     * Returns true if any view in the hierarchy has the event listener.
     */
    public KrollProxy firstHierarchyListener(String event) {
        boolean hasListener = hasListeners(event, false);

        // Checks whether the parent has the listener or not
        if (!hasListener) {
            KrollProxy parentProxy = getParentForBubbling();
            if (parentProxy != null && bubbleParent) {
                return parentProxy.firstHierarchyListener(event);
		}
            return null;
        }

        return this;
	}

    public boolean shouldFireChange(Object oldValue, Object newValue) {
        return (oldValue != null || newValue != null) && 
                ((oldValue == null || newValue == null) || (!oldValue.equals(newValue)));
	}

	/**
	 * Same behavior as {@link #setProperty(String, Object)}, but also invokes
     * {@link KrollProxyListener#propertyChanged(String, Object, Object, KrollProxy)}
     * .
     * 
     * @param name
     *            the property name.
     * @param value
     *            the property value.
	 * @module.api
	 */
    public void setPropertyAndFire(String name, Object value) {
		Object current = getProperty(name);

		if (shouldFireChange(current, value)) {
            setProperty(name, value);
			firePropertyChanged(name, current, value);
		}
	}

    public void setPropertyAndForceFire(String name, Object value) {
        Object current = getProperty(name);
        setProperty(name, value);

        firePropertyChanged(name, current, value);
    }
    
    public void onPropertyChanged(String name, Object value, Object oldValue) {
        // to be overriden
    }

    public void onPropertyChanged(String name, Object value) {
		String propertyName = name;
		Object newValue = value;

        if (name.equals(TiC.PROPERTY_BUBBLE_PARENT)) {
            bubbleParent = TiConvert.toBoolean(value, true);
            bubbleParentDefined = true;
            return;
        } else if (name.equals(TiC.PROPERTY_BIND_ID)) {
            mBindId = TiConvert.toString(newValue);
            return;
        } else if (name.equals(TiC.PROPERTY_SYNCEVENTS)) {
            setSyncEvents(TiConvert.toStringArray(value));
            return;
        } else if (isLocaleProperty(name)) {
            Pair<String, String> update = updateLocaleProperty(name,
                    TiConvert.toString(value));
			if (update != null) {
				propertyName = update.first;
				newValue = update.second;
			}
		}
        Object oldValue;
        synchronized (properties) {
            oldValue = properties.get(propertyName);
		properties.put(propertyName, newValue);
        }
		firePropertyChanged(propertyName, oldValue, newValue);
	}

    public void onPropertiesChanged(HashMap changes) {
//        int changesLength = changes.length;
//        boolean isUiThread = !mProcessInUIThread || TiApplication.isUIThread();
//
//        for (int i = 0; i < changesLength; ++i) {
//            Object[] change = changes[i];
//            if (change.length != 3) {
//                continue;
//            }
//
//            Object name = change[INDEX_NAME];
//            if (name == null || !(name instanceof String)) {
//                continue;
//            }
//
//            String nameString = (String) name;
//            Object value = change[INDEX_VALUE];
//
//            synchronized (properties) {
//                properties.put(nameString, change[INDEX_VALUE]);
//            }
//            if (isUiThread && modelListener != null) {
//                modelListener.get().propertyChanged(nameString,
//                        change[INDEX_OLD_VALUE], value, this);
//            }
//        }
//
//        if (isUiThread || modelListener == null) {
//            return;
//        }
//
//        Message message = getMainHandler().obtainMessage(
//                MSG_MODEL_PROPERTIES_CHANGED, changes);
//        message.sendToTarget();
        applyPropertiesInternal(changes, true, false, false);
	}

    public ActivityProxy getActivityProxy() {
		Activity activity = getActivity();
		if (activity instanceof TiBaseActivity) {
			return ((TiBaseActivity) activity).getActivityProxy();
		}

		return null;
	}

	/**
     * Returns proxy that should receive the event next in a case of bubbling.
     * Return null if the class does not bubble or there is no parent.
     * Optionally return null if the "bubbleParent" property is false -- i.e.,
	 * bubbleParent must be checked as well.
	 *
	 * @return proxy which is next to receive events
	 */
    public KrollProxy getParentForBubbling() {
		return null;
	}

	/**
     * Returns a KrollDict object that contains all current properties
     * associated with this proxy.
     * 
	 * @return KrollDict properties object.
	 * @module.api
	 */
    public KrollDict getProperties() {
		return properties;
	}

    public KrollDict getClonedProperties() {
//        KrollDict props = null;
        synchronized (properties) {
            return (KrollDict) properties.clone();
        }
//        return props;
    }
    public HashMap getShallowProperties() {
//        HashMap props = null;
        synchronized (properties) {
            return new HashMap(properties);
        }
//        return props;
    }

	/**
	 * @return the KrollModule that this proxy was created in.
	 */
    public KrollModule getCreatedInModule() {
		return createdInModule;
	}

	public boolean handleMessage(Message msg)
	{
		try {
			switch (msg.what) {
				case MSG_MODEL_PROPERTY_CHANGE: {
            if (modelListener != null) {
                ((KrollPropertyChange) msg.obj).fireEvent(this, modelListener.get());
            }

					return true;
				}
				case MSG_LISTENER_ADDED:
				case MSG_LISTENER_REMOVED: {
					String event = (String) msg.obj;

					if (msg.what == MSG_LISTENER_ADDED) {
						eventListenerAdded(event, 1, this);

					} else {
						eventListenerRemoved(event, 0, this);
					}

					return true;
				}
				case MSG_MODEL_PROCESS_PROPERTIES: {
					if (modelListener != null) {
                modelListener.get().processProperties(getShallowProperties());
				}
				return true;
			}
        case MSG_MODEL_APPLY_PROPERTIES: {
            if (modelListener != null) {
                if (msg.obj instanceof AsyncResult) {
                    AsyncResult result = (AsyncResult) msg.obj;
                    modelListener
                            .get().processApplyProperties((HashMap) result.getArg());
                    result.setResult(null);
                    return true;

                } else {
                    modelListener.get().processApplyProperties((HashMap) msg.obj);
                }
					}
					return true;
				}
				case MSG_MODEL_PROPERTIES_CHANGED: {
					firePropertiesChanged((Object[][]) msg.obj);

					return true;
				}
				case MSG_INIT_KROLL_OBJECT: {
					initKrollObject();
					((AsyncResult) msg.obj).setResult(null);

					return true;
				}
				case MSG_SET_PROPERTY: {
					Object value = msg.obj;
					String property = msg.getData().getString(PROPERTY_NAME);
					doSetProperty(property, value);

					return true;
				}
        case MSG_GET_PROPERTY: {
            AsyncResult asyncResult = (AsyncResult) msg.obj;
            String propertyName = msg.getData().getString(PROPERTY_NAME);
            asyncResult.setResult(getKrollObject().getProperty(propertyName));
            return true;
        }
				case MSG_FIRE_EVENT: {
					Object data = msg.obj;
					String event = msg.getData().getString(PROPERTY_NAME);
            boolean checkParent = msg.getData().getBoolean(PROPERTY_BUBBLES);
            doFireEvent(event, data, checkParent);

					return true;
				}
				case MSG_FIRE_SYNC_EVENT: {
					AsyncResult asyncResult = (AsyncResult) msg.obj;
            boolean handled = doFireEvent(msg.getData()
                    .getString(PROPERTY_NAME), asyncResult.getArg(), msg
                    .getData().getBoolean(PROPERTY_BUBBLES));
					asyncResult.setResult(handled);

					return handled;
				}
				case MSG_CALL_PROPERTY_ASYNC: {
					String propertyName = msg.getData().getString(PROPERTY_NAME);
					Object[] args = (Object[]) msg.obj;
					getKrollObject().callProperty(propertyName, args);

					return true;
				}
				case MSG_CALL_PROPERTY_SYNC: {
					String propertyName = msg.getData().getString(PROPERTY_NAME);
					AsyncResult asyncResult = (AsyncResult) msg.obj;
					Object[] args = (Object[]) asyncResult.getArg();
					getKrollObject().callProperty(propertyName, args);
					asyncResult.setResult(null);

					return true;
				}
        case MSG_UPDATE_KROLL_PROPERTIES: {
            if (msg.obj instanceof AsyncResult) {
                AsyncResult asyncResult = (AsyncResult) msg.obj;
                doUpdateKrollObjectProperties((HashMap<String, Object>) asyncResult
                        .getArg());
                asyncResult.setResult(null);
                return true;

            } else {
                doUpdateKrollObjectProperties((HashMap<String, Object>) msg.obj);
		}

            return true;
        }
			}
		} catch (Throwable t) {
			Thread.getDefaultUncaughtExceptionHandler().uncaughtException(null, t);
		}

		return false;
	}

	// TODO: count should be removed since we no longer report it.
	//       These methods only gets called now when the first listener
	//       is added or the last one has been removed.
	/**
	 * Called when a event listener is added to the proxy
	 *
     * @param event
     *            the event that the listener has been added for
     * @param count
     *            the number of listeners for this event. should not be used as
     *            this value is not reported correctly
     * @param proxy
     *            the proxy that the event was added to. otherwise known as
     *            "this"
	 * @return				<code>void</code>
	 */
    protected void eventListenerAdded(String event, int count, KrollProxy proxy) {
        if (modelListener != null) {
            modelListener.get().listenerAdded(event, count, this);
	}
	}

	/**
	 * Called when a event listener is removed from the proxy
	 *
     * @param event
     *            the event that the listener has been removed for
     * @param count
     *            the number of listeners for this event. should not be used as
     *            this value is not reported correctly
     * @param proxy
     *            the proxy that the event was removed from. otherwise known as
     *            "this"
	 * @return				<code>void</code>
	 */
    protected void eventListenerRemoved(String event, int count,
            KrollProxy proxy) {
        if (modelListener != null) {
            modelListener.get().listenerRemoved(event, count, this);
	}
	}

	/**
	 * Associates this proxy with the passed in {@link KrollProxyListener}.
     * 
     * @param modelListener
     *            the passed in KrollProxyListener.
	 * @module.api
	 */
    public void setModelListener(KrollProxyListener modelListener) {
        setModelListener(modelListener, true);
		}

    public KrollProxyListener getModelListener() {
        if (modelListener == null) {
            return null;
        }
        return modelListener.get();
    }

    public void setSetPropertyListener(SetPropertyChangeListener listener) {
        this.setPropertyListener = listener;
    }

    public void setModelListener(KrollProxyListener modelListener,
            boolean applyProps) {
		if (modelListener != null) {
            if (this.modelListener != null && modelListener.equals(this.modelListener.get())) {
                return;
            }
            this.modelListener = new WeakReference<KrollProxyListener>(modelListener);
            if (applyProps) {
                if (!mProcessInUIThread || TiApplication.isUIThread()) {
                    modelListener.processProperties(getShallowProperties());
			} else {
				getMainHandler().sendEmptyMessage(MSG_MODEL_PROCESS_PROPERTIES);
			}
		}
        } else {
            this.modelListener = null;
	}
	}

    public int addEventListener(String eventName, Object callback) {
		int listenerId = -1;

		if (eventName == null) {
            throw new IllegalStateException(
                    "addEventListener expects a non-null eventName");

		} else if (callback == null) {
            throw new IllegalStateException(
                    "addEventListener expects a non-null listener");
		}

		synchronized (eventListeners) {
			if (eventListeners.isEmpty()) {
				setProperty(PROPERTY_HAS_JAVA_LISTENER, true);
			}

            ArrayList<Object> listeners = eventListeners.get(eventName);
			if (listeners == null) {
                listeners = new ArrayList<Object>();
				eventListeners.put(eventName, listeners);
			}

            // if (Log.isDebugModeEnabled()) {
            //     Log.d(TAG, "Added for eventName '" + eventName + "' with id "
            //             + listenerId, Log.DEBUG_MODE);
            // }
//			listenerId = listenerIdGenerator.incrementAndGet();
			listeners.add(callback);
            eventListenerAdded(eventName, listeners.size(), this);
		}

		return listenerId;
	}

    public void removeEventListener(String eventName, int listenerId) {
		if (eventName == null) {
            throw new IllegalStateException(
                    "removeEventListener expects a non-null eventName");
		}

		synchronized (eventListeners) {
		    ArrayList<Object> listeners = eventListeners.get(eventName);
			if (listeners != null) {
                listeners.remove(listenerId);
                eventListenerRemoved(eventName, listeners.size(), this);
				if (listeners.size() == 0) {
					eventListeners.remove(eventName);
				}
				if (eventListeners.isEmpty()) {
                    // If we don't have any java listeners, we set the property
                    // to false
					setProperty(PROPERTY_HAS_JAVA_LISTENER, false);
				}
			}
		}
	}

    public void onEventFired(String event, Object data) {

				}

    public boolean hasNonJSEventListener(String event) {
        return eventListeners != null && eventListeners.containsKey(event)
                && eventListeners.get(event) != null;
			}
    
    public boolean hasEvaluatorListener(String event) {
        return evaluators != null && evaluators.get(event) != null;
	}

	/**
     * Resolves the passed in scheme / path, and uses the Proxy's creationUrl if
     * the path is relative.
     * 
     * @param scheme
     *            the scheme of Url.
     * @param path
     *            the path of Url.
	 * @return a string representation of URL given its components.
	 * @module.api
	 */
    public String resolveUrl(String scheme, String path) {
		return TiUrl.resolve(creationUrl.baseUrl, path, scheme);
	}

    public String getProxyId() {
		return proxyId;
	}

    protected KrollDict createErrorResponse(int code, String message) {
		KrollDict error = new KrollDict();
		error.putCodeAndMessage(code, message);
		error.put(TiC.ERROR_PROPERTY_MESSAGE, message);
		return error;
	}

    

    public void GCFinalize() {
        krollObjectSupported = false;
       release();
       modelListener = null;
       evaluators = null;
			eventListeners = null;
       eventOverrideDelegate = null;
       mSyncEvents = null;
			defaultValues.clear();
       createdInModule = null;
    }
    
    public boolean canBeReleasedFromJava() {
        return !krollObjectSupported;
    }
    
    static public void releaseProxyFromJava(KrollProxy proxy) {
        if (proxy != null && proxy.canBeReleasedFromJava()) {
            Log.e(TAG, "releaseProxyFromJava", proxy.getClass().getName());
            proxy.release();
        }
    }

	/**
	 * Releases the KrollObject, freeing memory.
     * 
	 * @module.api
	 */
	public void release()
	{
         if (krollObjectSupported) {
             return;
		}
		// if (eventListeners != null) {
		// 	eventListeners.clear();
		// 	eventListeners = null;
		// }
		// if (properties != null) {
		// 	properties.clear();
		// 	properties = null;
		// }
		// if (defaultValues != null) {
		// 	defaultValues.clear();
		// 	defaultValues = null;
		// }
		releaseKroll();
//        if (properties != null) {
//            synchronized (properties) {
////                Iterator it = properties.entrySet().iterator();
////                while (it.hasNext()) {
////                    Map.Entry pairs = (Map.Entry)it.next();
////                    Object value = pairs.getValue();
////                    if (value instanceof KrollProxy) {
////                        ((KrollProxy) value).release();
////                    }
////                }
//                properties.clear();
//            }
//        }
        
//        modelListener = null;
//        evaluators = null;
//        eventListeners = null;
//        eventOverrideDelegate = null;
//        mSyncEvents = null;
//        defaultValues.clear();
//        createdInModule = null;
	}

	/**
	 * Only release kroll, but maintain instance
	 * @module.api
	 */
	public void releaseKroll()
	{
		if (krollObject != null) {
			krollObject.release();
		}
	}

	// For subclasses to override
	@Kroll.method
    @Kroll.getProperty(enumerable=false)
    public String getApiName() {
		return "Ti.Proxy";
	}

    protected void addPropToUpdateNativeSide(String key, Object value) {
        if (propertiesToUpdateNativeSide == null) {
            propertiesToUpdateNativeSide = new HashMap<String, Object>();
        }
        propertiesToUpdateNativeSide.put(key, value);
    }

    public void updatePropertiesNativeSide() {
        if (readyToUpdateNativeSideProperties
                && propertiesToUpdateNativeSide != null) {
            updateKrollObjectProperties(propertiesToUpdateNativeSide);
//            propertiesToUpdateNativeSide = null;
        }
    }
    
    public boolean isReadyToUpdateNativeSideProperties() {
        return readyToUpdateNativeSideProperties;
    }

    protected void setReadyToUpdateNativeSideProperties(final boolean value) {
        if (readyToUpdateNativeSideProperties != value) {
            readyToUpdateNativeSideProperties = value;
            if (value) {
                updatePropertiesNativeSide();
            }
        }
    }

    public void addBinding(String bindId, KrollProxy bindingProxy) {
        if (bindId == null)
            return;
        //dont update JS side yet
        setPropertyJava(bindId, bindingProxy);
        addPropToUpdateNativeSide(bindId, bindingProxy);
    }

    @SuppressWarnings("unchecked")
    protected void initFromTemplate(HashMap template_, KrollProxy rootProxy,
            boolean updateKrollProperties, boolean recursive) {
        if (rootProxy != null) {
            mBindId = TiConvert.toString(template_, TiC.PROPERTY_BIND_ID, mBindId);
//            if (mBindId != null) {
//                setPropertyJava(TiC.PROPERTY_BIND_ID, mBindId);
//            }
            // always call addBinding even with bindId=null, subclasses might
            // need it
            if (rootProxy != this) {
                rootProxy.addBinding(getBindId(), this);
            }
        }
        if (template_.containsKey(TiC.PROPERTY_EVENTS)) {
            Object events = template_.get(TiC.PROPERTY_EVENTS);

            if (events instanceof HashMap) {
                Iterator entries = ((HashMap) events).entrySet().iterator();
                while (entries.hasNext()) {
                    Map.Entry entry = (Map.Entry) entries.next();
                    String key = (String) entry.getKey();
                    Object value = entry.getValue();
                    int count = -1;
                    if (value instanceof KrollFunction) {
                        count = addEventListener(key, value);
                    } else {
                        count = addEvaluator(key, value);
                    }
                }
            }
        }
    }

    public KrollProxy createProxyFromObject(Object data, KrollProxy rootProxy,
            boolean updateKrollProperties) {
        if (data instanceof HashMap) {
            KrollProxy result = createProxyFromTemplate((HashMap) data,
                    rootProxy, false);
            if (result != null) {
                result.setActivity(rootProxy.getActivity());
                if (updateKrollProperties) {
                    result.updateKrollObjectProperties();
                    rootProxy.updatePropertiesNativeSide();
                }
            }
            return result;
        } else if (data instanceof KrollProxy) {
            ((KrollProxy) data).setActivity(rootProxy.getActivity());
            return (KrollProxy) data;
        }
        return null;
    }

    public KrollProxy createProxyFromTemplate(HashMap template_,
            KrollProxy rootProxy, boolean updateKrollProperties) {
        long startTime = System.currentTimeMillis();
        KrollProxy result = createProxyFromTemplate(template_, rootProxy,
                updateKrollProperties, true);
        long endTime = System.currentTimeMillis();
        long diff = endTime - startTime;
        if (diff > 20) {
            Log.w(TAG, "createProxyFromTemplate " + diff + "ms");
        }
        return result;
    }
    
    private static final String DEFAULT_TEMPLATE_TYPE = "Ti.UI.View";
    protected String defaultProxyTypeFromTemplate() {
        return DEFAULT_TEMPLATE_TYPE;
    }

    @SuppressWarnings("unchecked")
    public KrollProxy createProxyFromTemplate(HashMap template_,
            KrollProxy rootProxy, boolean updateKrollProperties,
            boolean recursive) {
        if (template_ == null) {
            return null;
        }
//        boolean creationArgsHandlesTemplate = true;
        String type = TiConvert.toString(template_, TiC.PROPERTY_TYPE, defaultProxyTypeFromTemplate());
//        String bindId = TiConvert.toString(template_, TiC.PROPERTY_BIND_ID);
        Object props = template_;
        if (template_.get(TiC.PROPERTY_PROPERTIES) != null) {
            props =  template_.get(TiC.PROPERTY_PROPERTIES);
//            creationArgsHandlesTemplate = false;
        } else if (template_.containsKey(TiC.PROPERTY_CHILD_TEMPLATES) || 
                template_.containsKey(TiC.PROPERTY_EVENTS)){
            props = new HashMap(template_);
            ((HashMap) props).remove(TiC.PROPERTY_EVENTS);
            ((HashMap) props).remove(TiC.PROPERTY_CHILD_TEMPLATES);
        }
        try {
            Class<? extends KrollProxy> cls = (Class<? extends KrollProxy>) Class
                    .forName(APIMap.getProxyClass(type));
            KrollProxy proxy = KrollProxy.createProxy(cls, props, rootProxy);
            if (proxy == null)
                return null;
            if  (rootProxy == null) {
                rootProxy = proxy;
            } else {
                proxy.setActivity(rootProxy.getActivity());
            }
//            if (creationArgsHandlesTemplate) {
//                template_.remove(TiC.PROPERTY_EVENTS);
//                template_.remove(TiC.PROPERTY_CHILD_TEMPLATES);
//            }
            proxy.initFromTemplate(template_, rootProxy, updateKrollProperties,
                    recursive);
            if (updateKrollProperties) {
                rootProxy.updatePropertiesNativeSide();
            }
            return proxy;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view from template: " + e.toString());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public KrollProxy createTypeViewFromDict(HashMap template_, String type) {
        Object props = template_.get(TiC.PROPERTY_PROPERTIES);
        try {
            Class<? extends KrollProxy> cls = (Class<? extends KrollProxy>) Class
                    .forName(APIMap.getProxyClass(type));
            KrollProxy proxy = createProxy(cls, props);
           if (proxy == null)
                return null;
            proxy.initFromTemplate(template_, proxy, TiApplication.appRunOnMainThread(), true);
            return proxy;
        } catch (Exception e) {
            Log.e(TAG, "Error creating view from dict: " + e.toString());
            return null;
        }
    }

    public void reloadProperties() {
        if (modelListener != null) {
//            synchronized (properties) {
                modelListener.get().processProperties(getShallowProperties());
//            }
        }
    }

    public void addSyncEvent(final String event) {
        if (mSyncEvents == null) {
            mSyncEvents = new HashSet<String>();
        }
        mSyncEvents.add(event);
    }

    public void setSyncEvents(final String[] events) {
        mSyncEvents = new HashSet<String>(Arrays.asList(events));
    }

    public void removeSyncEvent(final String event) {
        if (mSyncEvents != null) {
            mSyncEvents.remove(event);
        }
    }

    public void setEventOverrideDelegate(
            final TiViewEventOverrideDelegate eventOverrideDelegate) {
        if (eventOverrideDelegate != null) {
            this.eventOverrideDelegate = new WeakReference<TiViewEventOverrideDelegate>(eventOverrideDelegate);
        } else {
            this.eventOverrideDelegate = null;
        }
    }
    
    public TiViewEventOverrideDelegate getEventOverrideDelegate() {
        if (eventOverrideDelegate != null) {
            return eventOverrideDelegate.get();
        }
        return null;
    }

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onCreate life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onCreate(Activity activity, Bundle savedInstanceState) {
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onResume life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onResume(Activity activity) {
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onPause life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onPause(Activity activity) {
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onDestroy life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onDestroy(Activity activity) {
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onStart life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onStart(Activity activity) {
	}

	/**
	 * A place holder for subclasses to extend. Its purpose is to receive native Android onStop life cycle events.
	 * @param activity the activity attached to this module.
	 * @module.api
	 */
	public void onStop(Activity activity) {
	}
	

    @Override
    public void onLowMemory(Activity activity) {
        // TODO Auto-generated method stub
        
}

    public <T> T getValueInUIThread(final Command<T> command, T defaultValue){
        return TiActivityHelper.getValueInUIThread(getActivity(), this, command, defaultValue);
    }
    
    public void runInUiThread(final CommandNoReturn command, final boolean blocking) {
        if (TiApplication.isUIThread()) {
            command.execute();
        } else {
            if (blocking) {
                TiMessenger.sendBlockingMainCommand(command);
            } else {
                TiMessenger.sendMainCommand(command);
            }

        }
    }
    public <T> T getInUiThread(final Command<T> command) {
        if (TiApplication.isUIThread()) {
            return command.execute();
        } else {
            return (T) TiMessenger.sendBlockingMainCommand(command);

        }
    }
    
    protected void handleStateDiffPropertyForKey(String key, Object obj, Iterator<Map.Entry<String, Object>> it, HashMap newValues)
	{
        it.remove();
        if (hasProperty(key)) {
            newValues.put(key, getProperty(key));
        } else {
            newValues.put(key, null);
	}
}
    
    private void mergeHasMaps(HashMap map1 , HashMap map2) {
        if (map2 == null) {
            return;
        }
        for (Iterator<Map.Entry<String, Object>> it = map2.entrySet()
                .iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();
            final String key = entry.getKey();
            Object current = map1.get(key);
            Object value = entry.getValue();
            if (current instanceof HashMap && value instanceof HashMap) {
                HashMap toReplace = new HashMap((HashMap)current);
                mergeHasMaps(toReplace, new HashMap((HashMap)value));
                entry.setValue(toReplace);
            } else if (value == null){
                map1.remove(key);
            } else if (value instanceof HashMap) {
                map1.put(key, new HashMap((HashMap)value));
            } else {
                map1.put(key, value);
            }
        }
    }

    protected HashMap generateDiffDictionary(HashMap currentValues,
            HashMap newValues) {
        HashMap result = (HashMap) ((newValues != null) ? newValues.clone()
                : new HashMap());
        for (Iterator<Map.Entry<String, Object>> it = currentValues.entrySet()
                .iterator(); it.hasNext();) {
            Map.Entry<String, Object> entry = it.next();
            final String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof HashMap
                    && getProperty(key) instanceof KrollProxy) {
                KrollProxy target = (KrollProxy) getProperty(key);
                HashMap dict = target.generateDiffDictionary((HashMap) value,
                        (HashMap) ((newValues != null)?newValues.get(key):null));
                result.put(key, dict);
                if (((HashMap) value).size() == 0) {
                    it.remove();
                }
            } else {
                handleStateDiffPropertyForKey(key, value, it, result);
            }
        }
        // NSMutableDictionary* result = [NSMutableDictionary
        // dictionaryWithDictionary:newValues];
        // [currentValues enumerateKeysAndObjectsUsingBlock:^(id _Nonnull key,
        // id _Nonnull obj, BOOL * _Nonnull stop) {
        // if (IS_OF_CLASS(obj, NSDictionary) && [self bindingForKey:key]) {
        // TiProxy* target = [self bindingForKey:key];
        // NSDictionary* dict = [target generateDiffDictionary:obj
        // newValues:[newValues objectForKey:key]];
        // [result setObject:dict forKey:key];
        // if ([obj count] == 0) {
        // [currentValues removeObjectForKey:key];
        // }
        // } else {
        // [self handleStateDiffPropertyForKey:key value:obj
        // currentValues:currentValues newValues:result];
        // }
        // }];
        // [currentValues mergeWithDictionary:newValues];
        mergeHasMaps(currentValues, newValues);
        return result;
    }
    
    @Kroll.method
    @Kroll.setProperty
    public void setStates(HashMap states) {
        mStates = states;
    }
    @Kroll.method
    @Kroll.getProperty(enumerable=false)
    public HashMap getStates() {
        return mStates;
    }
    
    @Kroll.method
    @Kroll.setProperty
    public void setCustomState(String state) {
        mCustomState = state;
        setState(state);
    }

    protected void applyStateProperties(HashMap props)
    {
        applyPropertiesNoSave(props, true, false);
    }

    @Kroll.method
    @Kroll.setProperty
    public void setState(String state) {
        if (mStates == null || (state != null && !mStates.containsKey(state))) {
            return;
        }
        if ((state == null && mCurrentState == null) || (mCustomState != null && !mCustomState.equals(state))
                || (state != null && state.equals(mCurrentState))
                || (mCurrentState != null && mCurrentState.equals(state))) {
            return;
        }
        mCurrentState = state;
        if (mCurrentStateValues == null) {
            mCurrentStateValues = new HashMap();
        }
        HashMap props = generateDiffDictionary(mCurrentStateValues, TiConvert.toHashMap(mStates.get(state)));
        applyStateProperties(props);        
	}
}
