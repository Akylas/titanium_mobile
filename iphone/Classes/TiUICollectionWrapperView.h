/**
 * Akylas
 * Copyright (c) 2009-2010 by Akylas. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#ifdef USE_TI_UICOLLECTIONVIEW

#import "TiUICollectionView.h"

@class TiUICollectionWrapperViewProxy;
@interface TiUICollectionWrapperView : UICollectionReusableView<TiProxyDelegate>
{
}

@property (nonatomic, readonly) TiUICollectionWrapperViewProxy *proxy;
@property (nonatomic, readonly) TiUIView *viewHolder;
@property (nonatomic, readwrite, retain) NSDictionary *dataItem;

- (id)initWithProxy:(TiUICollectionWrapperViewProxy *)proxy;

- (BOOL)canApplyDataItem:(NSDictionary *)otherItem;
-(void)configurationStart;
-(void)configurationSet;
@end

#endif