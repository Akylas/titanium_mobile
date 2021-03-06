/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2012 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package org.appcelerator.titanium.animation;

import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.proxy.AnimatableProxy;
import org.appcelerator.titanium.util.TiConvert;

import android.os.Looper;
import android.os.MessageQueue;
import android.view.animation.Interpolator;

@SuppressWarnings({"unchecked", "rawtypes"})
public class TiAnimator
{
	private static final String TAG = "TiAnimator";

	public Double delay = null;
    private Double duration = null;
    private Double reverseDuration = null;
	public int repeat = 1;
	public Boolean autoreverse = false;
	public Boolean restartFromBeginning = false;
	public Boolean cancelRunningAnimations = false;
    private Interpolator curve = null;
    private Interpolator reverseCurve = null;
	protected boolean animating;
	protected boolean cancelled = false;
    public boolean dontApplyOnFinish = false;

	public TiAnimation animationProxy;
	protected KrollFunction callback;
	public HashMap<String, Object> options;
	protected AnimatableProxy proxy;

	public TiAnimator()
	{
		animating = false;
		cancelled = false;
	}
	
	protected void handleCancel() {
        cancelled = true;
//		if (proxy != null) {
//			proxy.animationFinished(this);
//		}
//		resetAnimationProperties();
//		proxy.afterAnimationReset();
	};
	
	public void cancel(){
		if (animating == false || cancelled == true) return;
        cancelled = true;
		Log.d(TAG, "cancel", Log.DEBUG_MODE);
		handleCancel();
	}
	
	public void cancelWithoutResetting(){
		if (animating == false) return;
		Log.d(TAG, "cancelWithoutResetting", Log.DEBUG_MODE);
		animating = false; //will prevent the call the handleFinish
	}
	
	
	public void setOptions(HashMap options) {
		this.options = (HashMap) options.clone();
		applyOptions();
	}
	
	public void setAnimation(TiAnimation animation) {
		this.animationProxy = animation;
		this.animationProxy.setAnimator(this);
		this.setOptions(animation.getClonedProperties());
	}
	
	public void setProxy(AnimatableProxy proxy) {
		this.proxy = proxy;
	}
	
	public HashMap getOptions() {
	    return this.options;
//		return (this.animationProxy != null)?this.animationProxy.getProperties():this.options ;
	}

	protected void applyOptions()
	{
//		HashMap options = getOptions();
		
		if (options == null) {
			return;
		}

		if (options.containsKey(TiC.PROPERTY_DELAY)) {
			delay = TiConvert.toDouble(options, TiC.PROPERTY_DELAY);
			options.remove(TiC.PROPERTY_DELAY);
		}

		if (options.containsKey(TiC.PROPERTY_DURATION)) {
			duration = TiConvert.toDouble(options, TiC.PROPERTY_DURATION, 0);
            options.remove(TiC.PROPERTY_DURATION);
		}
		if (options.containsKey(TiC.PROPERTY_REVERSE_DURATION)) {
            reverseDuration = TiConvert.toDouble(options, TiC.PROPERTY_REVERSE_DURATION);
            options.remove(TiC.PROPERTY_REVERSE_DURATION);
        }
		if (options.containsKey(TiC.PROPERTY_REPEAT)) {
			repeat = TiConvert.toInt(options, TiC.PROPERTY_REPEAT);

			if (repeat == 0) {
				// A repeat of 0 is probably non-sensical. Titanium iOS
				// treats it as 1 and so should we.
				repeat = 1;
			}
            options.remove(TiC.PROPERTY_REPEAT);
		} else {
			repeat = 1; // Default as indicated in our documentation.
		}

		if (options.containsKey(TiC.PROPERTY_AUTOREVERSE)) {
			autoreverse = TiConvert.toBoolean(options, TiC.PROPERTY_AUTOREVERSE);
            options.remove(TiC.PROPERTY_AUTOREVERSE);
		}
		
		if (options.containsKey(TiC.PROPERTY_RESTART_FROM_BEGINNING)) {
			restartFromBeginning = TiConvert.toBoolean(options, TiC.PROPERTY_RESTART_FROM_BEGINNING);
            options.remove(TiC.PROPERTY_RESTART_FROM_BEGINNING);
		}
		
		if (options.containsKey(TiC.PROPERTY_CANCEL_RUNNING_ANIMATIONS)) {
			cancelRunningAnimations = TiConvert.toBoolean(options, TiC.PROPERTY_CANCEL_RUNNING_ANIMATIONS);
            options.remove(TiC.PROPERTY_CANCEL_RUNNING_ANIMATIONS);
		}
		if (options.containsKey(TiC.PROPERTY_DONT_APPLY_ON_FINISH)) {
            dontApplyOnFinish = TiConvert.toBoolean(options, TiC.PROPERTY_DONT_APPLY_ON_FINISH);
            options.remove(TiC.PROPERTY_DONT_APPLY_ON_FINISH);
        }
		if (options.containsKey(TiC.PROPERTY_CURVE)) {
			Object value = options.get(TiC.PROPERTY_CURVE);
			if (value instanceof Number) {
				curve = TiInterpolator.getInterpolator(TiConvert.toInt(value), duration);
			}
			
			else if (value instanceof Object[]) {
				double[] values = TiConvert.toDoubleArray((Object[]) value);
				if (values.length == 4) {
					curve =new CubicBezierInterpolator(values[0], values[1], values[2], values[3]);
				}
			}
            options.remove(TiC.PROPERTY_CURVE);
		}
		if (options.containsKey(TiC.PROPERTY_REVERSE_CURVE)) {
            Object value = options.get(TiC.PROPERTY_REVERSE_CURVE);
            if (value instanceof Number) {
                reverseCurve = TiInterpolator.getInterpolator(TiConvert.toInt(value), duration);
            }
            
            else if (value instanceof Object[]) {
                double[] values = TiConvert.toDoubleArray((Object[]) value);
                if (values.length == 4) {
                    reverseCurve =new CubicBezierInterpolator(values[0], values[1], values[2], values[3]);
                }
            }
            options.remove(TiC.PROPERTY_REVERSE_CURVE);
        }

//		this.options = options;
	}
	
	public boolean animating() {
		return animating;
	}
	
//	static List<String> kAnimationProperties = Arrays.asList(
//			TiC.PROPERTY_DURATION, TiC.PROPERTY_DELAY,
//			TiC.PROPERTY_AUTOREVERSE, TiC.PROPERTY_REPEAT,
//			TiC.PROPERTY_RESTART_FROM_BEGINNING,
//			TiC.PROPERTY_CANCEL_RUNNING_ANIMATIONS,
//			TiC.PROPERTY_CURVE);
//
//	protected List<String> animationProperties() {
//		return kAnimationProperties;
//	}
//	
//	protected List<String> animationResetProperties() {
//		return kAnimationProperties;
//	}
	
	
	
	public HashMap getToOptions() {
	    if (this.options.containsKey("to")) {
	        return (HashMap) this.options.get("to");
	    } else if  (this.options.containsKey("from")) {
	        KrollDict toProps = new KrollDict();
	        KrollDict properties = proxy.getProperties();
	        Iterator it = ((HashMap) this.options.get("from")).entrySet().iterator();        
	        while (it.hasNext()) {
	            Map.Entry pairs = (Map.Entry)it.next();
	            String key = (String)pairs.getKey();
                Object bindedProxy = properties.get(key);
	            if (pairs.getValue() instanceof HashMap && bindedProxy instanceof KrollProxy) {
	                HashMap inner = new HashMap<>();
	                for (String key2 : ((HashMap<String, Object>) pairs.getValue()).keySet()) {
	                    inner.put(key2, ((KrollProxy) bindedProxy).getProperty(key2));
	                }
                    toProps.put(key, inner);
	            } else {
	                toProps.put(key, properties.get(key));
	            }
	        }
	        return toProps;
	    }
	    return this.options;
	}
	
	public HashMap getFromOptions() {
        if (this.options.containsKey("from")) {
            return (HashMap) this.options.get("from");
        } else if  (this.options.containsKey("to")) {
            KrollDict toProps = new KrollDict();
            KrollDict properties = proxy.getProperties();
            Iterator it = ((HashMap) this.options.get("to")).entrySet().iterator();        
            while (it.hasNext()) {
                Map.Entry pairs = (Map.Entry)it.next();
                String key = (String)pairs.getKey();
                Object bindedProxy = properties.get(key);
                if (pairs.getValue() instanceof HashMap && bindedProxy instanceof KrollProxy) {
                    HashMap inner = new HashMap<>();
                    for (String key2 : ((HashMap<String, Object>) pairs.getValue()).keySet()) {
                        inner.put(key2, ((KrollProxy) bindedProxy).getProperty(key2));
                    }
                    toProps.put(key, inner);
                } else {
                    toProps.put(key, properties.get(key));
                }
            }
            return toProps;
        }
        return proxy.getProperties();
    }
	
	private void internalApplyFromOptions(AnimatableProxy theProxy, HashMap<String, Object> options) {
        if (options.containsKey("from")) {
            theProxy.applyPropertiesNoSave(TiConvert.toHashMap(options.get("from")), false, true);
        }
        for (Map.Entry<String, Object> entry : options.entrySet()) {
            final String key = entry.getKey();
            Object value = entry.getValue();
            Object proxyvalue = theProxy.getProperty(key);
            if (proxyvalue instanceof AnimatableProxy && value instanceof HashMap) {
                internalApplyFromOptions((AnimatableProxy)proxyvalue, (HashMap<String, Object>)value);
            }
        }
    }
	
	public void applyFromOptions(AnimatableProxy theProxy) {
	    internalApplyFromOptions(theProxy, this.options);
	}

	public void resetAnimationProperties()
	{
		applyResetProperties();
        proxy.afterAnimationReset();
	}
	
	public void handleFinish()
	{		
	    if (!dontApplyOnFinish) {
	        if (autoreverse == true || cancelled) {
	            resetAnimationProperties();
	        }
	        else {
	            applyCompletionProperties();
	        }
	        if (callback != null && proxy != null) {
	            callback.callAsync(proxy.getKrollObject(), new Object[] { new KrollDict() });
	        }

	        if (this.animationProxy != null) {
	            this.animationProxy.setAnimator(null);
	            // In versions prior to Honeycomb, don't fire the event
	            // until the message queue is empty. There appears to be
	            // a bug in versions before Honeycomb where this
	            // onAnimationEnd listener can be called even before the
	            // animation is really complete.
	            if (TiC.HONEYCOMB_OR_GREATER) {
	                this.animationProxy.fireEvent(TiC.EVENT_COMPLETE);
	            } else {
	                Looper.myQueue().addIdleHandler(new MessageQueue.IdleHandler() {
	                    public boolean queueIdle()
	                    {
	                        animationProxy.fireEvent(TiC.EVENT_COMPLETE);
	                        return false;
	                    }
	                });
	            }
	        }
	    } else if (cancelled) { // if dont apply and cancelled we should reset
            resetAnimationProperties();
	    }
		 
		if (proxy != null) {
			proxy.animationFinished(this);
		}
	}

	protected void applyCompletionProperties()
	{
		if (options == null || proxy == null || autoreverse == true) {
			return;
		}
        HashMap toProps = getToOptions();
        proxy.applyPropertiesInternal(toProps, true, false, true);
	}
	
	protected void applyResetProperties()
	{
	    if (this.options == null || proxy == null) {
            return;
        }
        HashMap toProps = getToOptions();
        HashMap fromProps = getFromOptions();

        Iterator it = toProps.entrySet().iterator();        
        KrollDict resetProps = new KrollDict();
        while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            String key = (String)pairs.getKey();
            resetProps.put(key, fromProps.get(key));
        }
        proxy.applyPropertiesInternal(resetProps, true, false, false);
	}

	public void setCallback(KrollFunction callback)
	{
		this.callback = callback;
	}
	
	public void restartFromBeginning(){
		applyResetProperties();
		proxy.afterAnimationReset();
	}

//	protected void addAnimation(AnimationSet animationSet, Animation animation)
//	{
//		// repeatCount is ignored at the AnimationSet level, so it needs to
//		// be set for each child animation manually.
//
//		// We need to reduce the repeat count by 1, since for native Android
//		// 1 would mean repeating it once.
//		int repeatCount = (repeat == ValueAnimator.INFINITE ? repeat : repeat - 1);
//
//		// In Android (native), the repeat count includes reverses. So we
//		// need to double-up and add one to the repeat count if we're reversing.
//		if (autoreverse != null && autoreverse.booleanValue()) {
//			repeatCount = repeatCount * 2 + 1;
//		}
//
//		animation.setRepeatCount(repeatCount);
//
//		animationSet.addAnimation(animation);
//	}
	
	public Interpolator getCurve() {
	    return curve;
	}
	public Interpolator getReverseCurve() {
	    if (reverseCurve != null) {
	        return reverseCurve;
	    } else if (curve != null) {
            return new TiInterpolator.ReverseInterpolator(curve);
        }
        return null;
    }
	
	public void setDuration(Double duration) {
        this.duration = duration;
    }
	public Double getDuration() {
        return duration;
    }
    public Double getReverseDuration() {
        if (reverseDuration != null) {
            return reverseDuration;
        }
        return duration;
    }
}
