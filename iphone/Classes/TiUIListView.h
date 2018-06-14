/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UILISTVIEW

#import "MGSwipeTableCell.h"
#import "TiScrollingView.h"
#import "TiTableView.h"
#import "TiUIListViewProxy.h"

@interface TiUIListView : TiScrollingView <MGSwipeTableCellDelegate, UITableViewDelegate, UITableViewDataSource, UITableViewDataSourcePrefetching, UIGestureRecognizerDelegate, UISearchBarDelegate, UISearchResultsUpdating, UISearchControllerDelegate, TiScrolling, TiProxyObserver, TiUIListViewDelegateView>

#pragma mark - Private APIs

@property (nonatomic, readonly) TiTableView *tableView;
@property (nonatomic, readonly) BOOL isSearchActive;
@property (nonatomic, readonly) BOOL editing;

- (void)setContentInsets_:(id)value withObject:(id)props;
- (void)deselectAll:(BOOL)animated;
- (void)updateIndicesForVisibleRows;
- (void)viewResignFocus;
- (void)viewGetFocus;

+ (UITableViewRowAnimation)animationStyleForProperties:(NSDictionary *)properties;
- (BOOL)shouldHighlightCurrentListItem;
- (NSIndexPath *)nextIndexPath:(NSIndexPath *)indexPath;
- (NSMutableArray *)visibleCellsProxies;
- (void)selectItem:(NSIndexPath *)indexPath animated:(BOOL)animated;
- (void)deselectItem:(NSIndexPath *)indexPath animated:(BOOL)animated;

@end

#endif
