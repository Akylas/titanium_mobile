/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UILABEL

#import "TiUILabel.h"
#import "TiUILabelProxy.h"
#import "TiUtils.h"
#import "UIImage+Resize.h"
#import <CoreText/CoreText.h>

#ifdef USE_TI_UIATTRIBUTEDSTRING
#import "TiUIAttributedStringProxy.h"
#endif
#import "DTCoreText/DTCoreText.h"
#import "TiTransition.h"
#import "TiTransitionHelper.h"

@interface TiUILabel () {
  //    BOOL _reusing;
  BOOL _textIsSelectable;
  BOOL _autocapitalization;
  UILongPressGestureRecognizer *_longPressGestureRecognizer;
}
@property (nonatomic, retain) NSDictionary *transition;
@end

@implementation TiUILabel

#pragma mark Internal

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoSize];
  [self setDefaultWidth:TiDimensionAutoSize];
}
#endif

- (id)init
{
  if (self = [super init]) {
    //        _reusing = NO;
    _textIsSelectable = NO;
    _autocapitalization = NO;
    self.transition = nil;
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(_transition);
  RELEASE_TO_NIL(label);
  RELEASE_TO_NIL(_longPressGestureRecognizer)
  [super dealloc];
}

- (UIView *)viewForHitTest
{
  return label;
}

- (CGSize)suggestedFrameSizeToFitEntireStringConstraintedToSize:(CGSize)size
{
  CGSize maxSize = CGSizeMake(size.width <= 0 ? 10000 : size.width, 10000);
  CGSize result = [[self label] sizeThatFits:maxSize];
  if ([label shadowRadius] > 0) {
    CGSize shadowOffset = [label shadowOffset];
    if (result.width > 0) {
      result.width += fabs(shadowOffset.width);
    }
    if (result.height > 0) {
      result.height += fabs(shadowOffset.height);
    }
  }
  return result;
}

- (CGSize)contentSizeForSize:(CGSize)size
{
  return [self suggestedFrameSizeToFitEntireStringConstraintedToSize:size];
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
#ifndef TI_USE_AUTOLAYOUT
  [label setFrame:bounds];
#endif
  [super frameSizeChanged:frame
                   bounds:bounds];
}

- (void)configurationStart
{
  [super configurationStart];
  needsSetText = NO;
}

- (void)configurationSet
{

  [super configurationSet];
  if (needsSetText)
    [self setAttributedTextViewContent];
}

- (TTTAttributedLabel *)label
{
  if (label == nil) {
    label = [[TiLabel alloc] initWithFrame:CGRectZero];
    label.backgroundColor = [UIColor clearColor];
    label.numberOfLines = 0; //default wordWrap to True
    label.lineBreakMode = NSLineBreakByWordWrapping; //default ellipsis to none
    label.layer.shadowRadius = 0; //for backward compatibility
    label.layer.shadowOffset = CGSizeZero;
    label.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    label.touchDelegate = self;
    label.strokeColorAttributeProperty = DTBackgroundStrokeColorAttribute;
    label.strokeWidthAttributeProperty = DTBackgroundStrokeWidthAttribute;
    label.cornerRadiusAttributeProperty = DTBackgroundCornerRadiusAttribute;
    label.paddingAttributeProperty = DTPaddingAttribute;
    label.linkAttributeProperty = DTLinkAttribute;
    label.strikeOutAttributeProperty = NSStrikethroughStyleAttributeName;
    label.backgroundColorAttributeProperty = NSBackgroundColorAttributeName;

    label.delegate = self;
    [self updateContentMode];
    [self addSubview:label];
  }
  return label;
}

- (TTTAttributedLabelLink *)checkLinkAttributeForString:(NSAttributedString *)theString atPoint:(CGPoint)p
{
  if ([label.links count] == 0)
    return nil;
  return [label linkAtPoint:p];
}

- (NSInteger)characterIndexAtPoint:(CGPoint)p;
{
  return [label characterIndexAtPoint:p];
}

- (id)accessibilityElement
{
  return [self label];
}

- (void)setAttributedTextViewContent
{
  if (!configurationSet) {
    needsSetText = YES;
    return; // lazy init
  }
  if (![NSThread isMainThread]) {
    TiThreadPerformOnMainThread(^{
      [self setAttributedTextViewContent];
    },
        YES);
    return;
  }
  __block id content = [(TiUILabelProxy *)[self proxy] getLabelContent];
  if (_autocapitalization) {
    if (IS_OF_CLASS(content, NSAttributedString)) {
      NSMutableAttributedString *uppered = [[NSMutableAttributedString alloc] initWithAttributedString:content];
      [uppered enumerateAttributesInRange:NSMakeRange(0, uppered.length)
                                  options:0
                               usingBlock:^(__unused NSDictionary *attrs, NSRange range, __unused BOOL *stop) {
                                 NSString *substring = [uppered.string substringWithRange:range];

                                 NSAssert(substring != nil, @"The result of the mutator block cannot be nil.");

                                 // replaceCharactersInRange: inherits the attributes of the first replaced character,
                                 // and we're enumerating such that we get called at the beginning of each attribute run,
                                 // so this does the right thing.
                                 [uppered replaceCharactersInRange:range withString:substring];
                                 content = [uppered autorelease];
                               }];
    } else if (IS_OF_CLASS(content, NSString)) {
      content = [content uppercaseString];
    }
  }

  [self transitionToText:content];
}

- (TiLabel *)cloneView:(TiLabel *)source
{
  NSData *archivedViewData = [NSKeyedArchiver archivedDataWithRootObject:source];
  TiLabel *clone = [NSKeyedUnarchiver unarchiveObjectWithData:archivedViewData];

  //seems to be duplicated < ios7
  clone.font = source.font;
  clone.textColor = source.textColor;
  clone.highlightedTextColor = source.highlightedTextColor;
  //

  clone.touchDelegate = source.touchDelegate;
  clone.delegate = source.delegate;
  [clone setLinkModels:source.links];
  return clone;
}

- (void)transitionToText:(id)text
{
  TiTransition *transition = [TiTransitionHelper transitionFromArg:self.transition containerView:self];
  if (transition != nil) {
    TiLabel *oldView = [self label];
    TiLabel *newView = [self cloneView:oldView];
    newView.text = text;
    [TiTransitionHelper transitionFromView:oldView
        toView:newView
        insideView:self
        withTransition:transition
        prepareBlock:^{
        }
        completionBlock:^{
          [oldView release];
        }];
    label = [newView retain];
  } else {
    [[self label] setText:text];
  }
}

- (void)setHighlighted:(BOOL)newValue animated:(BOOL)animated
{
  [super setHighlighted:newValue animated:animated];
  [[self label] setHighlighted:newValue];
}

- (void)didMoveToSuperview
{
  [self setHighlighted:NO];
  [super didMoveToSuperview];
}

- (void)didMoveToWindow
{
  /*
     * See above
     */
  [self setHighlighted:NO];
  [super didMoveToWindow];
}

- (BOOL)isHighlighted
{
  return [[self label] isHighlighted];
}

- (void)setExclusiveTouch:(BOOL)value
{
  [super setExclusiveTouch:value];
  [[self label] setExclusiveTouch:value];
}

- (void)updateContentMode
{
  UIViewContentMode contentMode = UIViewContentModeRedraw;
  NSTextAlignment hor = [label textAlignment];
  TTTAttributedLabelVerticalAlignment vert = [label verticalAlignment];
  if (hor == NSTextAlignmentLeft || hor == NSTextAlignmentNatural) {
    switch (vert) {
    case UIControlContentVerticalAlignmentBottom:
      contentMode = UIViewContentModeBottomLeft;
      break;
    case UIControlContentVerticalAlignmentTop:
      contentMode = UIViewContentModeTopLeft;
      break;
    default:
      contentMode = UIViewContentModeLeft;
      break;
    }
  } else if (hor == NSTextAlignmentRight) {
    switch (vert) {
    case UIControlContentVerticalAlignmentBottom:
      contentMode = UIViewContentModeBottomRight;
      break;
    case UIControlContentVerticalAlignmentTop:
      contentMode = UIViewContentModeTopRight;
      break;
    default:
      contentMode = UIViewContentModeRight;
      break;
    }
  } else {
    switch (vert) {
    case UIControlContentVerticalAlignmentBottom:
      contentMode = UIViewContentModeBottom;
      break;
    case UIControlContentVerticalAlignmentTop:
      contentMode = UIViewContentModeTop;
      break;
    default:
      contentMode = UIViewContentModeCenter;
      break;
    }
  }
  label.contentMode = contentMode;
}

#pragma mark Public APIs

- (void)setCustomUserInteractionEnabled:(BOOL)value
{
  [super setCustomUserInteractionEnabled:value];
  [[self label] setEnabled:value];
}

- (void)setVerticalAlign_:(id)value
{
  UIControlContentVerticalAlignment verticalAlign = [TiUtils contentVerticalAlignmentValue:value];
  [[self label] setVerticalAlignment:(TTTAttributedLabelVerticalAlignment)verticalAlign];
  [self updateContentMode];
}
- (void)setAutoLink_:(id)value
{
  [[self label] setEnabledTextCheckingTypes:NSTextCheckingTypesFromUIDataDetectorTypes([TiUtils intValue:value])];
  //we need to update the text
  [self setAttributedTextViewContent];
}

- (void)setDisableLinkStyle_:(id)value
{
  BOOL currentlyDisabled = [self label].linkAttributes == nil;
  BOOL disable = [TiUtils boolValue:value def:NO];
  if (disable == currentlyDisabled)
    return;
  if (disable) {
    [self label].linkAttributes = nil;
    [self label].activeLinkAttributes = nil;
    [self label].inactiveLinkAttributes = nil;
  } else {
    [[self label] initLinksStyle];
  }
}

- (void)setTextIsSelectable_:(id)value
{
  _textIsSelectable = [TiUtils boolValue:value def:NO];
  if (_textIsSelectable) {
    if (!_longPressGestureRecognizer) {
      _longPressGestureRecognizer = [[UILongPressGestureRecognizer alloc] initWithTarget:self action:@selector(longPressGestureRecognized:)];
      [self addGestureRecognizer:_longPressGestureRecognizer];
    }
  } else {
    if (_longPressGestureRecognizer) {
      [self removeGestureRecognizer:_longPressGestureRecognizer];
      RELEASE_TO_NIL(_longPressGestureRecognizer)
    }
  }
}
- (void)longPressGestureRecognized:(UILongPressGestureRecognizer *)gestureRecognizer
{
  if (gestureRecognizer == _longPressGestureRecognizer) {
    if (gestureRecognizer.state == UIGestureRecognizerStateBegan) {
      [self becomeFirstResponder]; // must be called even when NS_BLOCK_ASSERTIONS=0

      UIMenuController *copyMenu = [UIMenuController sharedMenuController];
      [copyMenu setTargetRect:self.bounds inView:self];
      copyMenu.arrowDirection = UIMenuControllerArrowDefault;
      [copyMenu setMenuVisible:YES animated:YES];
    }
  }
}

- (void)setColor_:(id)color
{
  UIColor *newColor = [[TiUtils colorValue:color] _color];
  if (newColor == nil)
    newColor = [UIColor darkTextColor];
  [[self label] setTextColor:newColor];
}

- (void)setText_:(id)value
{
  needsSetText = YES;
}

- (void)setHtml_:(id)value
{
  needsSetText = YES;
}

- (void)setEllipsize_:(id)value
{
  ENSURE_SINGLE_ARG(value, NSNumber);
  NSInteger type = [TiUtils intValue:value];
  if (type == 1) {
    type = NSLineBreakByTruncatingTail;
    //        return;
  }
  [[self label] setLineBreakMode:type];
}

- (void)setHighlightedColor_:(id)color
{
  UIColor *newColor = [[TiUtils colorValue:color] _color];
  [[self label] setHighlightedTextColor:(newColor != nil) ? newColor : [UIColor lightTextColor]];
}

- (void)setSelectedColor_:(id)color
{
  UIColor *newColor = [[TiUtils colorValue:color] _color];
  [[self label] setHighlightedTextColor:(newColor != nil) ? newColor : [UIColor lightTextColor]];
}

- (void)setDisabledColor_:(id)color
{
  UIColor *newColor = [[TiUtils colorValue:color] _color];
  [[self label] setDisabledColor:newColor];
}

- (void)setFont_:(id)fontValue
{
  UIFont *font;
  if (fontValue != nil) {
    font = [[TiUtils fontValue:fontValue] font];
  } else {
    font = [UIFont systemFontOfSize:[UIFont labelFontSize]];
  }
  [[self label] setFont:font];
}

- (void)setMinimumFontSize_:(id)size
{
  CGFloat newSize = [TiUtils floatValue:size];
  if (newSize < 4) { // Beholden to 'most minimum' font size
    [[self label] setAdjustsFontSizeToFitWidth:NO];
    [[self label] setMinimumScaleFactor:0.0];
  } else {
    [[self label] setAdjustsFontSizeToFitWidth:YES];

    [[self label] setMinimumScaleFactor:(newSize / [self label].font.pointSize)];
  }
  [self updateNumberLines];
}

- (void)setAttributedString_:(id)arg
{
#ifdef USE_TI_UIATTRIBUTEDSTRING
  ENSURE_SINGLE_ARG(arg, TiUIAttributedStringProxy);
  [[self label] setText:[arg attributedString] afterInheritingLabelAttributesAndConfiguringWithBlock:nil];
  //    [[self label] setAttributedText:[arg attributedString]];
  [[self viewProxy] contentsWillChange];
#endif
}

- (void)setTextAlign_:(id)alignment
{
  [[self label] setTextAlignment:[TiUtils textAlignmentValue:alignment]];
  [self updateContentMode];
}

- (void)setShadowColor_:(id)color
{
  if (color == nil) {
    [[self label] setShadowColor:nil];
  } else {
    color = [TiUtils colorValue:color];
    [[self label] setShadowColor:[color _color]];
  }
}
- (void)setStrokeColor_:(id)color
{
  if (color == nil) {
    [[self label] setStrokeColor:nil];
  } else {
    color = [TiUtils colorValue:color];
    [[self label] setStrokeColor:[color _color]];
  }
}

- (void)setStrokeWidth_:(id)arg
{
  [[self label] setStrokeWidth:TiConvertToPixels(arg)];
}

- (void)setShadowRadius_:(id)arg
{
  [[self label] setShadowRadius:TiConvertToPixels(arg)];
}
- (void)setShadowOffset_:(id)value
{
  CGPoint p = [TiUtils pointValue:value];
  CGSize size = { p.x, p.y };
  [[self label] setShadowOffset:size];
}

- (void)setPadding:(UIEdgeInsets)inset
{
  [self label].textInsets = inset;
}

- (void)updateNumberLines
{
  if ([[self label] minimumScaleFactor] != 0) {
    [[self label] setNumberOfLines:1];
  } else if ([[self proxy] valueForKey:@"maxLines"]) {
    NSInteger maxLines = [TiUtils intValue:[[self proxy] valueForKey:@"maxLines"] def:0];
    [[self label] setNumberOfLines:maxLines];
  } else {
    BOOL shouldWordWrap = [TiUtils boolValue:[[self proxy] valueForKey:@"wordWrap"] def:YES];
    if (shouldWordWrap) {
      [[self label] setNumberOfLines:0];
    } else {
      [[self label] setNumberOfLines:1];
    }
  }
}

- (void)setWordWrap_:(id)value
{
  [self updateNumberLines];
}

- (void)setMaxLines_:(id)value
{
  [self updateNumberLines];
}

- (void)setMultiLineEllipsize_:(id)value
{
  NSInteger multilineBreakMode = [TiUtils intValue:value];
  if (multilineBreakMode != NSLineBreakByWordWrapping) {
    [[self label] setLineBreakMode:NSLineBreakByWordWrapping];
  }
}

- (void)setTransition_:(id)arg
{
  ENSURE_SINGLE_ARG_OR_NIL(arg, NSDictionary)
  self.transition = arg;
}

- (void)setAutocapitalization_:(id)value
{
  _autocapitalization = [TiUtils boolValue:value def:NO];
  needsSetText = YES;
}

#pragma mark -
#pragma mark DTAttributedTextContentViewDelegate

- (void)attributedLabel:(TTTAttributedLabel *)label
    didSelectLinkWithURL:(NSURL *)url
{
  if ([[self viewProxy] _hasListeners:@"link" checkParent:NO]) {
    NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                                url, @"url",
                                            nil];
    [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
  }
}

- (void)attributedLabel:(TTTAttributedLabel *)label
    didSelectLinkWithAddress:(NSDictionary *)addressComponents
{
  NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                              addressComponents, @"address",
                                          nil];
  if ([[self viewProxy] _hasListeners:@"link" checkParent:NO]) {

    [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
  }
}

- (void)attributedLabel:(TTTAttributedLabel *)label
    didSelectLinkWithPhoneNumber:(NSString *)phoneNumber
{
  if ([[self viewProxy] _hasListeners:@"link" checkParent:NO]) {
    NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                                phoneNumber, @"phomeNumber",
                                            nil];
    [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
  }
}

- (void)attributedLabel:(TTTAttributedLabel *)label
    didSelectLinkWithDate:(NSDate *)date
                 timeZone:(NSTimeZone *)timeZone
                 duration:(NSTimeInterval)duration
{
  if ([[self viewProxy] _hasListeners:@"link" checkParent:NO]) {
    NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                                @(date.timeIntervalSince1970), @"date",
                                            @(duration * 1000), @"duration",
                                            //                                   timeZone.name, @"timezone",
                                            nil];
    [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
  }
}

- (void)attributedLabel:(TTTAttributedLabel *)label
    didSelectLinkWithTransitInformation:(NSDictionary *)components
{
  if ([[self viewProxy] _hasListeners:@"link" checkParent:NO]) {
    NSDictionary *eventDict = [NSDictionary dictionaryWithObjectsAndKeys:
                                                components, @"transit",
                                            nil];
    [[self proxy] fireEvent:@"link" withObject:eventDict propagate:NO checkForListener:NO];
  }
}

- (NSDictionary *)dictionaryFromTouch:(UITouch *)touch
{
  NSMutableDictionary *event = [super dictionaryFromTouch:touch];
  if ([label activeLink]) {
    [self addLinkData:event forLink:[label activeLink]];
  }
  return event;
}

- (void)addLinkData:(NSMutableDictionary *)dict forLink:(TTTAttributedLabelLink *)link
{
  if (link) {
    switch (link.result.resultType) {
    case NSTextCheckingTypeLink:
      [(NSMutableDictionary *)dict setObject:link.result.URL forKey:@"link"];
      break;
    case NSTextCheckingTypePhoneNumber:
      [(NSMutableDictionary *)dict setObject:link.result.phoneNumber forKey:@"phoneNumber"];
      break;
    case NSTextCheckingTypeAddress:
      [(NSMutableDictionary *)dict setObject:link.result.addressComponents forKey:@"address"];
      break;
    case NSTextCheckingTypeTransitInformation:
      [(NSMutableDictionary *)dict setObject:link.result.components forKey:@"transit"];
      break;
    case NSTextCheckingTypeDate:
      [(NSMutableDictionary *)dict setObject:@(link.result.date.timeIntervalSince1970) forKey:@"date"];
      [(NSMutableDictionary *)dict setObject:@(link.result.duration * 1000) forKey:@"duration"];
      //                [(NSMutableDictionary*)dict setObject:link.result.timeZone.name forKey:@"timezone"];
      break;
    default:
      break;
    }
  }
}

- (NSMutableDictionary *)dictionaryFromGesture:(UIGestureRecognizer *)gesture
{
  NSMutableDictionary *event = [super dictionaryFromGesture:gesture];
  NSAttributedString *attString = label.attributedText;
  if (attString != nil) {
    CGPoint localPoint = [gesture locationInView:label];
    TTTAttributedLabelLink *result = [self checkLinkAttributeForString:attString atPoint:localPoint];
    [self addLinkData:event forLink:result];
  }
  //    if ([label activeLink]) {
  //        [self addLinkData:event forLink:[label activeLink]];
  //    }
  return event;
}

#pragma mark - UIResponder

- (BOOL)canBecomeFirstResponder
{
  return _textIsSelectable;
}

- (BOOL)canPerformAction:(SEL)action withSender:(id)sender
{
  BOOL retValue = NO;

  if (action == @selector(copy:)) {
    if (_textIsSelectable) {
      retValue = YES;
    }
  } else {
    // Pass the canPerformAction:withSender: message to the superclass
    // and possibly up the responder chain.
    retValue = [super canPerformAction:action withSender:sender];
  }

  return retValue;
}

- (void)copy:(id)sender
{
  if (_textIsSelectable) {
    UIPasteboard *pasteboard = [UIPasteboard generalPasteboard];
    id string = [self.proxy valueForUndefinedKey:@"selectableText"];
    if (!string) {
      string = [(TiUILabelProxy *)[self proxy] getLabelContent];
    }
    if (IS_OF_CLASS(string, NSAttributedString)) {
      [pasteboard setString:[string string]];
    } else {
      [pasteboard setString:string];
    }
  }
}

@end

#endif
