package org.appcelerator.titanium.animation;

import org.appcelerator.kroll.common.Log;

import android.animation.AnimatorSet;

//import android.animation.AnimatorSet;

public class TiAnimatorSet extends TiAnimator {
    private final String TAG = "TiAnimatorSet";
	private AnimatorSet set;
	private AnimatorSet clonableSet;
	private AnimatorSet clonableReverseSet;
	private AnimatorSet reverseSet;
	public int repeatCount;
	public boolean needsToRestartFromBeginning = false;
	public int currentRepeatCount;
	TiAnimatorListener listener;

	public TiAnimatorSet() {
		set = new AnimatorSet();
	}

	public AnimatorSet set() {
		return set;
	}
	
	public AnimatorSet getOrCreateReverseSet() {
		if (reverseSet == null) {
			reverseSet = new AnimatorSet();
		}
		return reverseSet;
	}
	
	public AnimatorSet reverseSet() {
		return reverseSet;
	}
	
	public void resetSet() {
	    AnimatorSet oldSet = set;
		set = clonableSet.clone();
		set.addListener(this.listener);
		oldSet.removeAllListeners();
	}
	
	public void resetReverseSet() {
		if (clonableReverseSet != null) {
		    AnimatorSet oldSet = clonableReverseSet;
			reverseSet = clonableReverseSet.clone();
			oldSet.removeAllListeners();
			reverseSet.addListener(this.listener);
		}
	}
	
	@Override
	protected void handleCancel() {
		set.cancel();
		if (reverseSet != null) {
			reverseSet.cancel();
		}
        super.handleCancel();
	}
	
	@Override
	public void cancelWithoutResetting(){
//        Log.d(TAG, "cancelWithoutResetting " + this, Log.DEBUG_MODE);
		super.cancelWithoutResetting();
		set.cancel();
		if (reverseSet != null) {
			reverseSet.cancel();
		}
	}
	
	public void setRepeatCount (int count) {
		repeatCount = currentRepeatCount = count;
	}
	
	public void setAnimating (boolean value) {
		animating = value;
	}
	
	public boolean getAnimating () {
		return animating;
	}
	
	public void setListener (TiAnimatorListener listener) {
		this.listener = listener;
		set.removeAllListeners();
		set.addListener(listener);
		if (reverseSet != null) {
			reverseSet.removeAllListeners();
			reverseSet.addListener(listener);
		}
	}
	
	public void createClonableSets () {
		this.clonableSet = set.clone();
		if (reverseSet != null) {
			this.clonableReverseSet = reverseSet.clone();
		}
	}

	public void aboutToBePrepared() {
		if (needsToRestartFromBeginning && restartFromBeginning) {
			restartFromBeginning();
		}
	}
}