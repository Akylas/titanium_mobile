/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2011 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
package ti.modules.titanium.ui.widget;

import java.util.ArrayList;
import java.util.HashMap;
import java.lang.Math;

import org.appcelerator.kroll.KrollDict;
import org.appcelerator.kroll.KrollProxy;
import org.appcelerator.kroll.common.Log;
import org.appcelerator.titanium.TiApplication;
import org.appcelerator.titanium.TiBaseActivity;
import org.appcelerator.titanium.TiC;
import org.appcelerator.titanium.TiDimension;
import org.appcelerator.titanium.proxy.TiViewProxy;
import org.appcelerator.titanium.proxy.TiWindowProxy;
import org.appcelerator.titanium.transition.Transition;
import org.appcelerator.titanium.transition.TransitionHelper;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.util.TiUIHelper;
import org.appcelerator.titanium.util.TiViewHelper;
import org.appcelerator.titanium.view.TiCompositeLayout;
import org.appcelerator.titanium.view.TiCompositeLayout.LayoutParams;
import org.appcelerator.titanium.view.TiUIView;

import com.lambergar.verticalviewpager.VerticalViewPager;

import ti.modules.titanium.ui.ScrollableViewProxy;
import ti.modules.titanium.ui.widget.TiUIScrollView.TiScrollView;
import ti.modules.titanium.ui.widget.abslistview.AbsListViewProxy;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.os.Parcelable;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPager;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

@SuppressLint("NewApi")
public class TiUIScrollableView extends TiUIView implements  ViewPager.OnPageChangeListener, VerticalViewPager.OnPageChangeListener
//, ConfigurationChangedListener
{
	private static final String TAG = "TiUIScrollableView";

	private static final int PAGE_LEFT = 200;
	private static final int PAGE_RIGHT = 201;
    private TiUIPagerTabStrip mStrip;
	private boolean verticalLayout = false;
	boolean mNeedsRedraw = true;
	boolean hardwaredDisabled = false;
	int cacheSize = 3;
	private Transition transition;
	private boolean updateCurrentPageDuringScroll = true;

	protected static final int TIFLAG_NEEDS_DATASET               = 0x00000001;
	protected static final int TIFLAG_NEEDS_ADAPTER_CHANGE        = 0x00000002;
    protected static final int TIFLAG_NEEDS_CURRENT_PAGE          = 0x00000004;
    
	private interface IPageAdapter {

		void notifyDataSetChanged();
		
	}
	
	public static interface  TiPageTransformer {
		void transformPage(View page, float position);
	}

	private interface IViewPager {
		public void setOffscreenPageLimit(int limit);
		public void setOverScrollMode(int overScrollMode);
		public void setCurrentItem(int item);
		public void setCurrentItem(int item, boolean smoothScroll);
		public int getChildCount();
		public void removeViewAt (int index);
		public void removeView(View child);
		public void addView(View child, int index, ViewGroup.LayoutParams params);
        public void addView(View child, ViewGroup.LayoutParams params);
        public void addView(View child);
		public void setAdapter(IPageAdapter adapter);
		public void setPageMargin (int marginPixels);
		public void requestDisallowInterceptTouchEvent(boolean b);
		public void removeAllViews();
		public void measure(int widthSpec, int heightSpec);
		public int getMeasuredWidth();
		public void layout(int i, int offset, int measuredWidth, int j);
		public void setBackgroundColor(int red);
		public void setLayoutParams(ViewGroup.LayoutParams params);
		public ViewGroup.LayoutParams getLayoutParams();
		public void updatePageTransformer();
		public View getChildAt(int i);
		public void resetTransformations();
	}
	
	private class HViewPager extends ViewPager implements IViewPager{

		public HViewPager(Context context) {
			super(context);
			setClipChildren(false);
		}
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (mScrollingEnabled) {
				return super.onTouchEvent(event);
			}

			return false;
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
            if (mScrollingEnabled && isTouchEnabled) {
				return super.onInterceptTouchEvent(event);
			}

			return false;
		}
		@Override
		public void setAdapter(IPageAdapter adapter) {
			setAdapter((PagerAdapter)adapter);
			
		}

		@Override
		public void updatePageTransformer() {
			if (transition != null) {
				setPageTransformer(!TransitionHelper.isPushSubType(transition.subType), new PageTransformer() {
					
					@Override
					public void transformPage(View page, float position) {
					    
	                    transformView(page, transition, position);
					}
				});
				for (int i = 0; i < getChildCount(); i++) {
		            final View child = getChildAt(i);
		            TiViewHelper.resetValues(child);
		            transformView(child, transition, i - mCurIndex);
		        }
			}
			else {
				setPageTransformer(false, null);
			}
		}
		@Override
		public void resetTransformations() {
			for (int i = 0; i < getChildCount(); i++) {
	            final View child = getChildAt(i);
	            TiViewHelper.resetValues(child);
	        }
		}
		
        @Override
		protected boolean canScroll(View v, boolean checkV, int dx, int x, int y) {
	        if (v instanceof ViewGroup) {
	            final ViewGroup group = (ViewGroup) v;
	            
	            final int scrollX = v.getScrollX();
	            final int scrollY = v.getScrollY();
	            final int count = group.getChildCount();
	            // Count backwards - let topmost views consume scroll distance first.
	            for (int i = count - 1; i >= 0; i--) {
	                final View child = group.getChildAt(i);
	                if (child instanceof TiScrollView) {
                        return ((TiScrollView) child).canScroll(-dx, 0);
                    }
//	                if (child instanceof TiCompositeLayout && !((TiCompositeLayout) child).canScroll(x + scrollX, y + scrollY)) {
//	                    continue;
//	                }
//	                int left = child.getLeft();
	                if (x + scrollX >= child.getLeft() && x + scrollX < child.getRight() &&
	                        y + scrollY >= child.getTop() && y + scrollY < child.getBottom() &&
	                        canScroll(child, true, dx, x + scrollX - child.getLeft(),
	                                y + scrollY - child.getTop())) {
	                    return true;
	                }
	            }
	        }

	        return checkV && ViewCompat.canScrollHorizontally(v, -dx);
	    }
	}
	private class VViewPager extends VerticalViewPager implements IViewPager{

		public VViewPager(Context context) {
			super(context);
			setClipChildren(false);
		}
		@Override
		public boolean onTouchEvent(MotionEvent event) {
			if (mScrollingEnabled) {
				return super.onTouchEvent(event);
			}

			return false;
		}

		@Override
		public boolean onInterceptTouchEvent(MotionEvent event) {
			if (mScrollingEnabled) {
				return super.onInterceptTouchEvent(event);
			}

			return false;
		}

		@Override
		public void setAdapter(IPageAdapter adapter) {
			setAdapter((com.lambergar.verticalviewpager.PagerAdapter)adapter);
			
		}
		@Override
		public void updatePageTransformer() {
			if (transition != null) {
				setPageTransformer(!TransitionHelper.isPushSubType(transition.subType), new VerticalViewPager.PageTransformer() {
				
					@Override
					public void transformPage(View page, float position) {
	                    transformView(page, transition, position);
					}
				});
				for (int i = 0; i < getChildCount(); i++) {
		            final View child = getChildAt(i);
		            TiViewHelper.resetValues(child);
                    transformView(child, transition, i - mCurIndex);
		        }
			}
			else {
				setPageTransformer(false, null);
			}
		}
		@Override
		public void resetTransformations() {
			for (int i = 0; i < getChildCount(); i++) {
	            final View child = getChildAt(i);
	            TiViewHelper.resetValues(child);
	        }
		}
	}
	
	private IViewPager mPager;
	private IPageAdapter mAdapter;
	private final TiCompositeLayout mContainer;
	private final RelativeLayout mPagingControl;
	private boolean creatingViews = false;

	private int mMovingFromIndex = -1;
	private int mCurIndex = -1;
	private int mCurrentPage = 0;
	private boolean mScrollingEnabled = true;
	
	private boolean isValidScroll = false;
	private boolean justFiredDragEnd = false;

	public TiUIScrollableView(ScrollableViewProxy proxy, TiBaseActivity activity)
	{
		super(proxy);
//		activity.addConfigurationChangedListener(this);


		buildViewPager(activity);
		mContainer = new TiViewPagerLayout(activity);
		mContainer.addView((View)mPager, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		mPagingControl = buildPagingControl(activity);
		mContainer.addView(mPagingControl, buildFillLayoutParams());
				
		setNativeView(mContainer);
	}
	
	
	private void setCurrentPageIndex(int newPage, boolean animated)
	{
	    int viewCount = getViewCount();
		if (viewCount == 0 || mCurIndex >= viewCount || newPage == mCurIndex) return;
		int oldIndex = mCurIndex;
		mCurIndex = newPage;
		mCurrentPage = mCurIndex;
		proxy.setProperty(TiC.PROPERTY_CURRENT_PAGE, mCurrentPage);
		updateCacheSize();
		((ScrollableViewProxy)proxy).firePageChange(mCurIndex, oldIndex);
	}
	
	private int currentScrollState = ViewPager.SCROLL_STATE_IDLE;
	@Override
	public void onPageScrollStateChanged(int scrollState) {
	    if (currentScrollState == scrollState) {
	        return;
	    }
	    currentScrollState = scrollState;
		if (hardwaredDisabled) {
			hardwaredDisabled = false;
			enableHWAcceleration(nativeView);
		}
		mPager.requestDisallowInterceptTouchEvent(scrollState != ViewPager.SCROLL_STATE_IDLE);		
		
		if (scrollState == ViewPager.SCROLL_STATE_DRAGGING) {
			updateCurrentPageDuringScroll = true;
			((ScrollableViewProxy)proxy).fireScrollStart(mCurIndex);
		}
		else if ((scrollState == ViewPager.SCROLL_STATE_IDLE) && isValidScroll) {
			int oldIndex = mCurIndex;
//			{
				updateCurrentPageDuringScroll = false;
//			}
			if (mCurIndex >= 0) {
                ((ScrollableViewProxy)proxy).fireScrollEnd(mCurIndex);
//				if (oldIndex >=0 && oldIndex != mCurIndex && oldIndex < mViews.size()) {
//					// Don't know what these focused and unfocused
//					// events are good for, but they were in our previous
//					// scrollable implementation.
//					// cf. https://github.com/appcelerator/titanium_mobile/blob/20335d8603e2708b59a18bafbb91b7292278de8e/android/modules/ui/src/ti/modules/titanium/ui/widget/TiScrollableView.java#L260
//					TiEventHelper.fireFocused(mViews.get(oldIndex));
//				}
//
//				TiEventHelper.fireUnfocused(mViews.get(mCurIndex));
//				if (oldIndex >= 0) {
//					// oldIndex will be -1 if the view has just
//					// been created and is setting currentPage
//					// to something other than 0. In that case we
//					// don't want a `scrollend` to fire.
//					((ScrollableViewProxy)proxy).fireScrollEnd(mCurIndex, mViews.get(mCurIndex));
//				}

				if (shouldShowPager()) {
					showPager();
				}
			}

			// If we don't use this state variable to check if it's a valid
			// scroll, this event will fire when the view is first created
			// because on creation, the scroll state is initialized to 
			// `idle` and this handler is called.
			isValidScroll = false;
		} else if (scrollState == ViewPager.SCROLL_STATE_SETTLING) {
			updateCurrentPageDuringScroll = false;
			((ScrollableViewProxy)proxy).fireDragEnd(mCurIndex);

			// Note that we just fired a `dragend` so the `onPageSelected`
			// handler below doesn't fire a `scrollend`.  Read below comment.
			justFiredDragEnd = true;
		}
	}

	@Override
	public void onPageSelected(int page)
	{

		setCurrentPageIndex(page, true);
		updateCurrentPageDuringScroll = false;
		// If we didn't just fire a `dragend` event then this is the case
		// where a user drags the view and settles it on a different view.
		// Since the OS settling logic is never run, the
		// `onPageScrollStateChanged` handler is never run, and therefore
		// we forgot to inform the Javascripters that the user just scrolled
		// their thing.

		if (!justFiredDragEnd) {
			((ScrollableViewProxy)proxy).fireScrollEnd(mCurIndex);

			if (shouldShowPager()) {
				showPager();
			}
		}
	}


	@Override
	public void onPageScrolled(int positionRoundedDown, float positionOffset, int positionOffsetPixels)
	{
		if (hardwaredDisabled) {
			hardwaredDisabled = false;
			enableHWAcceleration(nativeView);
		}
		isValidScroll = true;
		int oldIndex = mCurIndex;

		// When we touch and drag the view and hold it inbetween the second
		// and third sub-view, this function will have been called with values
		// similar to:
		//		positionRoundedDown:	1
		//		positionOffset:			 0.5
		// ie, the first parameter is always rounded down; the second parameter
		// is always just an offset between the current and next view, it does
		// not take into account the current view.

		// If we add positionRoundedDown to positionOffset, positionOffset will
		// have the 'correct' value; ie, will be a natural number when we're on
		// one particular view, something.5 when inbetween views, etc.
		float positionFloat = positionOffset + positionRoundedDown;

		// `positionFloat` can now be used to calculate the correct value for
		// the current index. We add 0.5 so that positionFloat will be rounded
		// half up; ie, if it has a value of 1.5, it will be rounded up to 2; if
		// it has a value of 1.4, it will be rounded down to 1.
		int count =  getViewCount();
		int index = Math.min(Math.max((int) Math.floor(positionFloat + 0.5), 0), count - 1);
		if (updateCurrentPageDuringScroll)
		{
			setCurrentPageIndex(index, true);
		}
	    ((ScrollableViewProxy)proxy).fireScroll(mCurIndex, positionFloat);

		// Note that we didn't just fire a `dragend`.  See the above comment
		// in `onPageSelected`.
		justFiredDragEnd = false;
		
		//Force the container to redraw on scrolling.
        //Without this the outer pages render initially and then stay static
        nativeView.invalidate();
	}
	private void buildViewPager(Context context)
	{
        if (proxy.hasProperty("scrollDirection") && TiConvert.toString(proxy.getProperty("scrollDirection")).equalsIgnoreCase("vertical")) {
			mPager = new VViewPager(context);
			((VViewPager)mPager).setOnPageChangeListener(this);
			mAdapter = new VViewPagerAdapter();
			verticalLayout = true;
		}
		else {
			mPager = new HViewPager(context);
			((HViewPager)mPager).setOnPageChangeListener(this);
			mAdapter = new HViewPagerAdapter();
		}
		mPager.setAdapter(mAdapter);
	}
	
	private TiUIPagerTabStrip applyPropertiesPageStrip(KrollDict d) {
	    if (mPager == null) {
	        return null;
	    }
	    if (mStrip == null) {
	        d.put(TiC.PROPERTY_TYPE, "PagerTabStrip");
	        
	        TiViewProxy viewProxy = (TiViewProxy)proxy.createProxyFromObject(d, proxy, false);
	        if (viewProxy != null) {
	            mStrip = (TiUIPagerTabStrip)viewProxy.getOrCreateView();
	            if (mStrip != null) {
	                mPager.addView(mStrip.getNativeView());
	            }
	        }
	    }
	    else {
	        mStrip.processApplyProperties(d);
	    }
	    return mStrip;
	}

	private boolean shouldShowPager()
	{
		Object showPagingControl = proxy.getProperty(TiC.PROPERTY_SHOW_PAGING_CONTROL);
		if (showPagingControl != null) {
			return TiConvert.toBoolean(showPagingControl);
		} else {
			return false;
		}
	}

	private TiCompositeLayout.LayoutParams buildFillLayoutParams()
	{
		TiCompositeLayout.LayoutParams params = new TiCompositeLayout.LayoutParams();
		params.autoFillsHeight = true;
		params.autoFillsWidth = true;
		return params;
	}

	private RelativeLayout buildPagingControl(Context context)
	{
		RelativeLayout layout = new RelativeLayout(context);
		layout.setFocusable(false);
		layout.setFocusableInTouchMode(false);

		TiArrowView left = new TiArrowView(context);
		left.setVisibility(View.INVISIBLE);
		left.setId(PAGE_LEFT);
		left.setMinimumWidth(80); // TODO density?
		left.setMinimumHeight(80);
		left.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				movePrevious();
			}});
		RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
		params.addRule(RelativeLayout.CENTER_VERTICAL);
		layout.addView(left, params);

		TiArrowView right = new TiArrowView(context);
		right.setLeft(false);
		right.setVisibility(View.INVISIBLE);
		right.setId(PAGE_RIGHT);
		right.setMinimumWidth(80); // TODO density?
		right.setMinimumHeight(80);
		right.setOnClickListener(new OnClickListener(){
			public void onClick(View v)
			{
				moveNext();
			}});
		params = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT,
				LayoutParams.WRAP_CONTENT);
		params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
		params.addRule(RelativeLayout.CENTER_VERTICAL);
		layout.addView(right, params);

		layout.setVisibility(View.GONE);

		return layout;
	}

	private int currentCacheSize = 1; //default is 1
	private void updateCacheSize() {
	    int count = getViewCount();
		if (count == 0) {
			return;
		}
	    final int currentIndex = Math.max(mCurIndex, 0);
		int realCache = (int) Math.floor(cacheSize/2); //floored
		int rangeStart = currentIndex - realCache;
		int rangeEnd = currentIndex + realCache;
		if (rangeStart < 0)
			realCache -= rangeStart;
		if (rangeEnd > (count - 1))
			realCache += rangeEnd - count + 1;
		
		if(currentCacheSize != realCache) {
		    currentCacheSize = realCache;
	        mPager.setOffscreenPageLimit(realCache);
		}
	}
	
	private void setPageWidth(Object value)
	{
		if (verticalLayout) {
			TiCompositeLayout.LayoutParams params = (LayoutParams) mPager.getLayoutParams();
    		params.optionHeight = TiConvert.toTiDimension(TiConvert.toString(value), TiDimension.TYPE_HEIGHT);
    		mPager.setLayoutParams(params);
		}
		else {
			TiCompositeLayout.LayoutParams params = (LayoutParams) mPager.getLayoutParams();
    		params.optionWidth = TiConvert.toTiDimension(TiConvert.toString(value), TiDimension.TYPE_WIDTH);
    		mPager.setLayoutParams(params);
		}
		((ViewGroup)nativeView).setClipChildren(false);
		
		hardwaredDisabled = true;
		disableHWAcceleration(nativeView); //we ll reenable it later because of a glitch
	}
	
	private void setPageOffset(Object value)
	{
		if (verticalLayout) {
			TiCompositeLayout.LayoutParams params = (LayoutParams) mPager.getLayoutParams();
    		params.optionHeight = TiConvert.toTiDimension(TiConvert.toString(value), TiDimension.TYPE_TOP);
    		mPager.setLayoutParams(params);
		}
		else {
			TiCompositeLayout.LayoutParams params = (LayoutParams) mPager.getLayoutParams();
    		params.optionWidth = TiConvert.toTiDimension(TiConvert.toString(value), TiDimension.TYPE_LEFT);
    		mPager.setLayoutParams(params);
		}
	}
	
	@Override
    public void propertySet(String key, Object newValue, Object oldValue,
            boolean changedProperty) {
        switch (key) {
        case TiC.PROPERTY_PAGE_WIDTH:
            setPageWidth(newValue);
            break;
        case TiC.PROPERTY_PAGE_OFFSET:
            setPageOffset(newValue);
            break;
        case TiC.PROPERTY_VIEWS:
            if (changedProperty) {
                ScrollableViewProxy viewProxy = (ScrollableViewProxy) proxy;
                ((ScrollableViewProxy)proxy).setViews(newValue);
            } else {
                ScrollableViewProxy viewProxy = (ScrollableViewProxy) proxy;
                if (!viewProxy.getPreload()) {
                    ((ScrollableViewProxy)proxy).setViews(newValue);
                } else {
                    mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
                    viewProxy.setPreload(false);
                }
            }
            break;
        case TiC.PROPERTY_SHOW_PAGING_CONTROL:
            if (TiConvert.toBoolean(newValue, true)) {
                showPager();
            }
            else {
                hidePager();
            }
            break;
        case TiC.PROPERTY_SCROLLING_ENABLED:
            mScrollingEnabled = TiConvert.toBoolean(newValue);
            break;
        case TiC.PROPERTY_OVER_SCROLL_MODE:
            mPager.setOverScrollMode(TiConvert.toInt(newValue, View.OVER_SCROLL_ALWAYS));
            break;
        case TiC.PROPERTY_CACHE_SIZE:
            cacheSize = TiConvert.toInt(newValue);
            if (cacheSize < 1) {
                // WHAT.  Let's make it something sensible.
                cacheSize = 1;
            }
            updateCacheSize();
            break;
        case TiC.PROPERTY_TRANSITION:
            transition = TransitionHelper.transitionFromObject((HashMap) newValue, null, null);
            mPager.updatePageTransformer();
            break;
        case TiC.PROPERTY_STRIP:
            applyPropertiesPageStrip(TiConvert.toKrollDict(newValue));
            break;
        case TiC.PROPERTY_CURRENT_PAGE:
            mCurrentPage = TiConvert.toInt(newValue, 0);
            mProcessUpdateFlags |= TIFLAG_NEEDS_CURRENT_PAGE;
            break;
        default:
            super.propertySet(key, newValue, oldValue, changedProperty);
            break;
        }
    }

	@Override
	public void processProperties(HashMap d)
	{
        super.processProperties(d);
		//simulate the first page change so that the user can interact with the events
        ((ScrollableViewProxy)proxy).fireScrollEnd(mCurIndex);
	}


	public void showPager()
	{
		View v = null;
		v = mContainer.findViewById(PAGE_LEFT);
		if (v != null) {
			v.setVisibility(mCurIndex > 0 ? View.VISIBLE : View.INVISIBLE);
		}

		v = mContainer.findViewById(PAGE_RIGHT);
		if (v != null) {
			v.setVisibility(mCurIndex < (getViewCount() - 1) ? View.VISIBLE : View.INVISIBLE);
		}

		mPagingControl.setVisibility(View.VISIBLE);
		((ScrollableViewProxy) proxy).setPagerTimeout();
	}

	public void hidePager()
	{
		mPagingControl.setVisibility(View.INVISIBLE);
	}
	
	public void showStrip()
    {
        mStrip.setVisibility(View.VISIBLE);
    }

    public void hideStrip()
    {
        mStrip.setVisibility(View.INVISIBLE);
    }

	public void moveNext(boolean animated)
	{
		move(mCurIndex + 1, animated);
	}

	public void moveNext()
	{
		moveNext(true);
	}

	public void movePrevious(boolean animated)
	{
		move(mCurIndex - 1, animated);
	}

	public void movePrevious()
	{
		movePrevious(true);
	}

	private void move(int index, boolean animated)
	{
		if (index < 0 || index >= getViewCount()) {
			if (Log.isDebugModeEnabled()) {
				Log.w(TAG, "Request to move to index " + index+ " ignored, as it is out-of-bounds.", Log.DEBUG_MODE);
			}
			return;
		}
		//we dont to update page during scroll but immediately. Otherwise if we jump multiple page, we will have multiple events!
		
		mMovingFromIndex = mCurIndex;

        setCurrentPageIndex(index, animated);
		mPager.setCurrentItem(mCurIndex, animated);
	}

//	private void move(int index)
//	{
//		move(index, true);
//	}

	public void scrollTo(Object view, boolean animated)
	{
		Log.i(TAG, "scrollTo " + animated);
		if (view instanceof Number) {
			move(((Number) view).intValue(), animated);
		} else if (view instanceof TiViewProxy) {
			move(((ScrollableViewProxy)proxy).getPositionOfView((TiViewProxy) view), animated);
		}
	}
	public void scrollTo(Object view)
	{
		scrollTo(view, true);
	}

	public int getCurrentPage()
	{
		return mCurIndex;
	}

	public void setCurrentPage(Object view)
	{
		scrollTo(view, false);
	}

//	public void setScrollingEnabled(Object value)
//	{
//		mScrollingEnabled = TiConvert.toBoolean(value);
//	}
//
//	public boolean getScrollingEnabled()
//	{
//		return mScrollingEnabled;
//	}

	public void clearViewsList()
	{
		mPager.removeAllViews();
	}
		
	private void transformView (View view, Transition transition, float position) {
	    if (transition != null) {
            TiViewHelper.setTranslationRelativeX(view, 0);
            TiViewHelper.setTranslationRelativeY(view, 0);
//          float dest = multiplier * position * (adjustScroll ? 1 : 0);
            if (verticalLayout) {
                TiViewHelper.setTranslationRelativeY(view, TiViewHelper.getTranslationRelativeY(view) - position);
            } else {
                TiViewHelper.setTranslationRelativeX(view, TiViewHelper.getTranslationRelativeX(view) - position);
            }
            
            int childIndex = (int) view.getTag();
            float realPosition = position;
            if (childIndex != mMovingFromIndex && childIndex != mCurIndex) {
                realPosition = -2;
            } else {
                realPosition /= Math.abs(mCurIndex - mMovingFromIndex);
            }
            transition.transformView(view, realPosition);
        }
	}

	public void onViewsChanged()
	{
        mProcessUpdateFlags |= TIFLAG_NEEDS_ADAPTER_CHANGE;
        mProcessUpdateFlags |= TIFLAG_NEEDS_CURRENT_PAGE;
        mProcessUpdateFlags |= TIFLAG_NEEDS_DATASET;
	}
	
	@Override
    protected void didProcessProperties() {        
        super.didProcessProperties();

        if ((mProcessUpdateFlags & TIFLAG_NEEDS_ADAPTER_CHANGE) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_ADAPTER_CHANGE;
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_DATASET;
            mPager.setAdapter(mAdapter);
        }
        else if ((mProcessUpdateFlags & TIFLAG_NEEDS_DATASET) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_DATASET;
            currentItemCount = ((ScrollableViewProxy)proxy).getViewCount();
            mAdapter.notifyDataSetChanged();
        }
        if ((mProcessUpdateFlags & TIFLAG_NEEDS_CURRENT_PAGE) != 0) {
            mProcessUpdateFlags &= ~TIFLAG_NEEDS_CURRENT_PAGE;
            setCurrentPage(mCurrentPage);
        }
    }

//	public ArrayList<TiViewProxy> getViews()
//	{
//	    if (creatingViews) {
//	        return null;
//	    }
//	    synchronized (viewsLock) {
//	        return mViews;
//	    }
//	}

	@Override
	public void release()
	{
		if (mPager != null) {
			mPager.removeAllViews();
//			for (int i = mPager.getChildCount() - 1; i >=  0; i--) {
//				mPager.removeViewAt(i);
//			}
		}		
		super.release();
	}

	public class HViewPagerAdapter extends PagerAdapter implements IPageAdapter
	{
		public HViewPagerAdapter()
		{
		}
		@Override
		public void destroyItem(View container, int position, Object object)
		{
			TiViewProxy tiProxy = (TiViewProxy) object;
			if (tiProxy != null) {
				View outerView = tiProxy.getOuterView();
				if (outerView != null) {
					TiCompositeLayout layout = (TiCompositeLayout) outerView.getParent();
					if (layout != null) {
						((ViewPager) container).removeView(layout);
					}
				}
//				synchronized (viewsLock) {
//				    if (!mViews.contains(tiProxy)) {
		                tiProxy.releaseViews(false);
//				    }
//	            }
			}
		}

		@Override
		public void finishUpdate(View container) {}

		@Override
		public int getCount()
		{
            return getViewCount();
		}

		@Override
		public Object instantiateItem(View container, int position)
		{
//			synchronized (viewsLock) {
			    
			    ViewGroup pager = (ViewGroup) container;
                TiViewProxy tiProxy = getViewAt(position);
                Activity activity = proxy.getActivity();
                if (tiProxy.getParent() != TiUIScrollableView.this.proxy) {
                    TiUIHelper.removeViewFromSuperView(tiProxy);
                    tiProxy.setParent(TiUIScrollableView.this.proxy);
                }
                tiProxy.setActivity(activity);
                
                TiCompositeLayout layout = new TiCompositeLayout(activity);
                layout.setInternalTouchPassThrough(true);
                TiUIHelper.addView(layout, tiProxy);
                layout.setTag(position);
                ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
                if (position < pager.getChildCount()) {
                    pager.addView(layout, position, params);
                } else {
                    pager.addView(layout, params);
                }                
                if (tiProxy instanceof TiWindowProxy && !((TiWindowProxy)tiProxy).isOpenedOrOpening()) {
                    TiWindowProxy childWin = (TiWindowProxy)tiProxy;
                    childWin.onWindowActivityCreated();
//                    childWin.focus();
                }
				return tiProxy;
//			}
		}

		@Override
		public boolean isViewFromObject(View view, Object object)
		{
//			synchronized (viewsLock) {
				TiViewProxy tiProxy = (TiViewProxy) object;
				if (tiProxy != null) {
					View outerView = tiProxy.getOuterView();
					if (outerView != null) {
						TiCompositeLayout layout = (TiCompositeLayout) outerView.getParent();
						return (layout != null && layout.equals(view));
					}
				}
//			}
			return false;
		}

		@Override
		public void restoreState(Parcelable state, ClassLoader loader) {}

		@Override
		public Parcelable saveState() {return null;}

		@Override
		public void startUpdate(View container) {
		}

		@Override
		public int getItemPosition(Object object)
		{

			TiViewProxy proxy = (TiViewProxy) object;
//			if (proxy == null || needsRepopulate == true) return POSITION_NONE;
			if (proxy == null) return POSITION_NONE;

			int index = ((ScrollableViewProxy)TiUIScrollableView.this.proxy).getPositionOfView(proxy);
            if (index != -1) {
                return index;
            }
			// if we arrive here, the data-item for which the Proxy was created
			// does not exist anymore.
			proxy.releaseViews(false);

			return POSITION_NONE;
		
		}
		
		@Override
	    public CharSequence getPageTitle(int position) {
		    if (mStrip != null) {
		        return mStrip.getPageTitle(position);
		    }
		    return null;
	    }
	}
	
	public class VViewPagerAdapter extends com.lambergar.verticalviewpager.PagerAdapter  implements IPageAdapter
	{
		public VViewPagerAdapter()
		{
		}
		@Override
		public void destroyItem(View container, int position, Object object)
		{
			TiViewProxy tiProxy = (TiViewProxy) object;
			if (tiProxy != null) {
				View outerView = tiProxy.getOuterView();
				if (outerView != null) {
					TiCompositeLayout layout = (TiCompositeLayout) outerView.getParent();
					if (layout != null) {
						((ViewPager) container).removeView(layout);
					}
				}
				tiProxy.releaseViews(false);
			}
		}

		@Override
		public void finishUpdate(View container) {
		    
		}

		@Override
		public int getCount()
		{
			return getViewCount();
		}

		@Override
		public Object instantiateItem(View container, int position)
		{
//			synchronized (viewsLock) {
				ViewGroup pager = (ViewGroup) container;
				TiViewProxy tiProxy = getViewAt(position);
                Activity activity = proxy.getActivity();
				if (tiProxy.getParent() != TiUIScrollableView.this.proxy) {
	                tiProxy.setActivity(activity);
	                TiUIHelper.removeViewFromSuperView(tiProxy);
	                tiProxy.setParent(TiUIScrollableView.this.proxy);
				}
                TiCompositeLayout layout = new TiCompositeLayout(activity);
                layout.setInternalTouchPassThrough(true);
                TiUIHelper.addView(layout, tiProxy);
				ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
				if (position < pager.getChildCount()) {
					pager.addView(layout, position, params);
				} else {
					pager.addView(layout, params);
				}
				return tiProxy;
//			}
		}

		@Override
		public boolean isViewFromObject(View view, Object object)
		{
//			synchronized (viewsLock) {
				TiViewProxy tiProxy = (TiViewProxy) object;
				if (tiProxy != null) {
					View outerView = tiProxy.getOuterView();
					if (outerView != null) {
						TiCompositeLayout layout = (TiCompositeLayout) outerView.getParent();
						return (layout != null && layout.equals(view));
					}
				}
//			}
			return false;
		}

		@Override
		public void restoreState(Parcelable state, ClassLoader loader) {}

		@Override
		public Parcelable saveState() {return null;}

		@Override
		public void startUpdate(View container) {}

		@Override
		public int getItemPosition(Object object)
		{

			TiViewProxy proxy = (TiViewProxy) object;
//			if (proxy == null || needsRepopulate == true) return POSITION_NONE;
			if (proxy == null) return POSITION_NONE;

			int index = ((ScrollableViewProxy)proxy).getPositionOfView(proxy);
			if (index != -1) {
			    return index;
			}
			// if we arrive here, the data-item for which the Proxy was created
			// does not exist anymore.
			proxy.releaseViews(false);

			return POSITION_NONE;
		}

        @Override
        public CharSequence getPageTitle(int position) {
            if (mStrip != null) {
                return mStrip.getPageTitle(position);
            }
            return null;
        }
	}
	

	public class TiViewPagerLayout extends TiCompositeLayout
	{
		
		public TiViewPagerLayout(Context context)
		{
			super(context, TiUIScrollableView.this);
			setFocusable(true);
			setFocusableInTouchMode(true);
			setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
		}
		
		private Point mCenter = new Point();
	    private Point mInitialTouch = new Point();
	 
	    @Override
	    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
	        mCenter.x = w / 2;
	        mCenter.y = h / 2;
	    }
	    
//	    @Override
//	    public boolean dispatchTouchEvent(MotionEvent event) {
//	        return super.dispatchTouchEvent(event);
//	    }

	    @Override
	    public boolean onTouchEvent(MotionEvent ev) {
	        //We capture any touches not already handled by the ViewPager
	        // to implement scrolling from a touch outside the pager bounds.
	        switch (ev.getAction()) {
	            case MotionEvent.ACTION_DOWN:
	                mInitialTouch.x = (int)ev.getX();
	                mInitialTouch.y = (int)ev.getY();
//	            case MotionEvent.ACTION_UP:
	            default:
	                ev.offsetLocation(mCenter.x - mInitialTouch.x, mCenter.y - mInitialTouch.y);
	                break;
	        }
	 
	        return ((View) mPager).onTouchEvent(ev);
	    }

		@Override
		public boolean onTrackballEvent(MotionEvent event)
		{
			// Any trackball activity should show the pager.
			if (shouldShowPager() && mPagingControl.getVisibility() != View.VISIBLE) {
				showPager();
			}
			return super.onTrackballEvent(event);
		}

		@Override
		public boolean dispatchKeyEvent(KeyEvent event)
		{
			boolean handled = false;
			if (event.getAction() == KeyEvent.ACTION_DOWN) {
				switch (event.getKeyCode()) {
					case KeyEvent.KEYCODE_DPAD_LEFT: {
						movePrevious();
						handled = true;
						break;
					}
					case KeyEvent.KEYCODE_DPAD_RIGHT: {
						moveNext();
						handled = true;
						break;
					}
				}
			}
			return handled || super.dispatchKeyEvent(event);
		}
	}
	
	TiViewProxy getViewAt(int position) {
	    return ((ScrollableViewProxy)proxy).getViewAt(position);
    }
	
	int currentItemCount = -1;
	int getViewCount() {   
        return currentItemCount;
    }
	
	public void notifyViewsChanged() {
        if (!TiApplication.isUIThread()) {
            proxy.getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    notifyViewsChanged();
                }
            });
            return;
        }
        currentItemCount = ((ScrollableViewProxy)proxy).getViewCount();
        mProcessUpdateFlags &= ~TIFLAG_NEEDS_DATASET;
        mAdapter.notifyDataSetChanged();
        setCurrentPage(mCurrentPage);
    }
}
