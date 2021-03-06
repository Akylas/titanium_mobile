/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UISCROLLVIEW

#ifdef USE_TI_UIREFRESHCONTROL
#import "TiUIRefreshControlProxy.h"
#endif

#import "TiScrollingView.h"

@interface TiUIScrollView : TiScrollingView <TiScrolling> {

  @private
  TDUIScrollView *scrollView;
#ifdef TI_USE_AUTOLAYOUT
  TiLayoutView *contentView;
#else
  UIView *wrapperView;
  TiDimension contentWidth;
  TiDimension contentHeight;
#endif
  CGFloat minimumContentHeight;

#ifdef USE_TI_UIREFRESHCONTROL
  TiUIRefreshControlProxy *refreshControl;
#endif

  BOOL needsHandleContentSize;
}

@property (nonatomic, retain, readonly) TDUIScrollView *scrollView;

//@property(nonatomic,readonly) TiDimension contentWidth;

- (void)setNeedsHandleContentSize;
- (void)setNeedsHandleContentSizeIfAutosizing;
- (BOOL)handleContentSizeIfNeeded;
- (void)handleContentSize;
#ifndef TI_USE_AUTOLAYOUT
- (UIView *)wrapperView;
#endif
- (BOOL)flexibleContentWidth;
- (BOOL)flexibleContentHeight;
@end

#endif
