package org.appcelerator.titanium.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.appcelerator.kroll.KrollFunction;
import org.appcelerator.kroll.annotations.Kroll;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.animation.TiAnimation;
import org.appcelerator.titanium.animation.TiAnimator;
import org.appcelerator.titanium.animation.TiAnimatorListener;
import org.appcelerator.titanium.animation.TiAnimatorSet;
import org.appcelerator.titanium.util.TiConvert;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.view.animation.Interpolator;

//import android.animation.Animator;
//import android.animation.AnimatorSet;
//import android.animation.ValueAnimator;

@SuppressLint("NewApi")
@Kroll.proxy
public class AnimatableProxy extends ParentingProxy {
	private static final String TAG = "AnimatableProxy";
	protected ArrayList<TiAnimatorSet> pendingAnimations;
	protected ArrayList<TiAnimatorSet> runningAnimations;
	protected Object pendingAnimationLock;
	protected Object runningAnimationsLock;

	public AnimatableProxy() {
		super();
		pendingAnimations = new ArrayList<TiAnimatorSet>();
		runningAnimations = new ArrayList<TiAnimatorSet>();
		pendingAnimationLock = new Object();
		runningAnimationsLock = new Object();
	}
	
	protected void applyPendingAnimationsFromOptions() {
	    synchronized (pendingAnimationLock) {
            if (pendingAnimations.size() == 0) {
                return;
            }
            for(TiAnimatorSet set : pendingAnimations) {
                set.applyFromOptions(this);
            }
        }
	}
	
	protected void handlePendingAnimation(TiAnimatorSet tiSet) {
	    if (tiSet.getDuration() == null || tiSet.getDuration() == 0) {
           tiSet.setProxy(this);
	       tiSet.handleFinish();
	       return;
	    }
	    synchronized (runningAnimationsLock) {
            tiSet.setProxy(this);
            tiSet.needsToRestartFromBeginning = (runningAnimations.size() > 0);
            if (tiSet.cancelRunningAnimations) {
                for (int i = 0; i < runningAnimations.size(); i++) {
                    runningAnimations.get(i).cancelWithoutResetting();
                }
                runningAnimations.clear();
            }
            else if (tiSet.animationProxy != null){
                for (int i = 0; i < runningAnimations.size(); i++) {
                    TiAnimator anim = runningAnimations.get(i);
                    if (anim.animationProxy == tiSet.animationProxy) {
                        anim.cancelWithoutResetting();
                        runningAnimations.remove(anim);
                        break;
                    }
                }
                runningAnimations.clear();
            }
            runningAnimations.add(tiSet);
            prepareAnimatorSet(tiSet);
            tiSet.set().start();
        }
	}

	protected void handlePendingAnimation() {
	    ArrayList<TiAnimatorSet> toHandle = null;
		synchronized (pendingAnimationLock) {
			if (pendingAnimations.size() == 0) {
				return;
			}
			toHandle = (ArrayList<TiAnimatorSet>) pendingAnimations.clone();
			pendingAnimations.clear();
		}
		
	    for (TiAnimatorSet tiSet: toHandle) {
	        handlePendingAnimation(tiSet);
        }
	}
	
	public TiAnimatorSet createAnimator(Object arg) {
	    if (arg == null) {
	        return null;
	    } else if (arg instanceof TiAnimatorSet) {
	        return (TiAnimatorSet) arg;
	    }
	    TiAnimatorSet tiAnimator = createAnimator();
        if (arg instanceof HashMap) {
            HashMap options = (HashMap) arg;
            tiAnimator.setOptions(options);
        } else if (arg instanceof TiAnimation) {
            TiAnimation anim = (TiAnimation) arg;
            tiAnimator.setAnimation(anim);
        } else {
            throw new IllegalArgumentException(
                    "Unhandled argument to animate: "
                            + arg.getClass().getSimpleName());
        }
        return tiAnimator;
	}

	public AnimatorSet getAnimatorSetForAnimation(Object arg) {
	    TiAnimatorSet tiAnimator = createAnimator(arg);
		tiAnimator.setProxy(this);
		prepareAnimatorSet(tiAnimator);

		return ((TiAnimatorSet) tiAnimator).set();
	}

	protected TiAnimatorSet createAnimator() {
		return new TiAnimatorSet();
	}
	
	public TiAnimator animateInternal(Object arg, KrollFunction callback) {
        TiAnimatorSet pendingAnimation;
        synchronized (pendingAnimationLock) {
            if (arg instanceof TiAnimatorSet) {
                pendingAnimation = (TiAnimatorSet)arg;
            }
            else {
                pendingAnimation = createAnimator();
                if (arg instanceof HashMap) {
                    HashMap options = (HashMap) arg;
                    pendingAnimation.setOptions(options);
                } else if (arg instanceof TiAnimation) {
                    TiAnimation anim = (TiAnimation) arg;
                    pendingAnimation.setAnimation(anim);
                } else {
                    throw new IllegalArgumentException(
                            "Unhandled argument to animate: "
                                    + arg.getClass().getSimpleName());
                }
            }
            if (callback != null) {
                pendingAnimation.setCallback(callback);
            }
            pendingAnimations.add(pendingAnimation);

        }
        handlePendingAnimation();
        return pendingAnimation;
    }
	
	@Kroll.method
	public void animate(Object arg,
			@Kroll.argument(optional = true) KrollFunction callback) {
	    animateInternal(arg, callback);
	}
    
//    public void applyPropertiesWithoutSaving(final KrollDict dict) {
//        if (modelListener != null) {
//            if (!mProcessInUIThread || TiApplication.isUIThread()) {
//                modelListener.get().processApplyProperties(dict);
//            } else {
//                    TiMessenger.sendBlockingMainMessage(getMainHandler()
//                            .obtainMessage(MSG_MODEL_APPLY_PROPERTIES),
//                            dict);                  
//            }
//        }
//    }
	
	
	static void prepareAnimationList(List<Animator> list, Double setDuration, Double setDelay, Interpolator interpolator) {
	    for (int i = 0; i < list.size(); i++) {
            Animator anim = list.get(i);
            if (anim instanceof AnimatorSet) {
                prepareAnimationList(((AnimatorSet) anim).getChildAnimations(), setDuration, setDelay, interpolator);
            } else {
                final long duration = anim.getDuration();
                final long startDelay = anim.getStartDelay();
                final Object interpo = anim.getInterpolator();
                if (startDelay == 0 && setDelay != null) {
                    anim.setStartDelay(setDelay.longValue());
                }
                if (duration == 0 && setDuration != null) {
                    anim.setDuration(setDuration.longValue());
                }
                if (interpo == null && interpolator != null) {
                    anim.setInterpolator(interpolator);
                }
            }
        }
	}
	public void prepareAnimatorSet(TiAnimatorSet tiSet) {
		tiSet.aboutToBePrepared();
		AnimatorSet set = tiSet.set();
		HashMap options = tiSet.getOptions();

		TiAnimatorListener listener = new TiAnimatorListener(tiSet,
				options);		
		
		List<Animator> list = new ArrayList<Animator>();
		List<Animator> listReverse = tiSet.autoreverse?new ArrayList<Animator>():null;
		   
		if (tiSet.options.containsKey("from")) {
            this.applyPropertiesNoSave(TiConvert.toHashMap(tiSet.options.get("from")), false, true);
        }
		prepareAnimatorSet(tiSet, list, listReverse);
		
		int repeatCount = (tiSet.repeat == ValueAnimator.INFINITE ? tiSet.repeat : tiSet.repeat - 1);
		tiSet.setRepeatCount(repeatCount);
		Interpolator interpolator = tiSet.getCurve();
		Double setDuration = tiSet.getDuration();
		Double setDelay = tiSet.delay;

		prepareAnimationList(list, setDuration, setDelay, interpolator);
        set.playTogether(list);

		//reverse set
		if (listReverse != null) {
			AnimatorSet reverseSet = tiSet.getOrCreateReverseSet();
			Double reverseSetDuration = tiSet.getReverseDuration();
	        Double reverseSetDelay = tiSet.delay;
			Interpolator reverseInterpolator = tiSet.getReverseCurve();
	        prepareAnimationList(listReverse, reverseSetDuration, reverseSetDelay, reverseInterpolator);
			reverseSet.playTogether(listReverse);
		}
		
		tiSet.createClonableSets();
        tiSet.setListener(listener);
	}

	protected void prepareAnimatorSet(TiAnimatorSet tiSet, List<Animator> list, List<Animator> listReverse) {
		Log.d(TAG, "prepareAnimatorSet", Log.DEBUG_MODE);
	}
	
	public void animationFinished(TiAnimator animation) {
		synchronized (runningAnimationsLock) {
			runningAnimations.remove(animation);
		}
		handlePendingAnimation();
	}

	public void clearAnimation(TiAnimator builder)
	{
		synchronized(pendingAnimationLock) {
			pendingAnimations.remove(builder);
		}
	}

	@Kroll.method
	public void cancelAllAnimations() {
		synchronized (runningAnimationsLock) {
		    final int count = runningAnimations.size();
			for (int i = count - 1; i >= 0; i--) {
				runningAnimations.get(i).cancel();
			}
			runningAnimations.clear();
		}
	}
	
	public void afterAnimationReset()
	{
		
	}
	
   @Kroll.getProperty(name="isAnimating", enumerable=false)
   @Kroll.method
   public boolean getAnimating() {
       synchronized (runningAnimationsLock) {
           return runningAnimations.size() > 0;
       }
   }
   
   @Override
   protected void handleStateDiffPropertyForKey(String key, Object obj, Iterator<Map.Entry<String, Object>> it, HashMap newValues)
   {
       if (key.equals(TiC.PROPERTY_DURATION)) {
           newValues.put(key, obj);
           it.remove();
       } else {
          super.handleStateDiffPropertyForKey(key, obj, it, newValues);
       }
//       it.remove();
//       if (hasProperty(key)) {
//           newValues.put(key, getProperty(key));
//       } else {
//           newValues.put(key, null);
//       }
   }

   @Override
   protected void applyStateProperties(HashMap props)
   {
       if (props.containsKey(TiC.PROPERTY_DURATION)) {
           TiAnimatorSet animation = createAnimator();
           animation.setOptions(props);
           animation.dontApplyOnFinish = true;
           handlePendingAnimation(animation);
       } else {
           super.applyStateProperties(props);
       }
   }
}
