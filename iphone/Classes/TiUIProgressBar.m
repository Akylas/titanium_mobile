/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2014 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIPROGRESSBAR

#import "TiUIProgressBar.h"
#import "TiUtils.h"
#import "WebFont.h"

@implementation TiUIProgressBar {
  float currentValue;
}

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoSize];
  [self setDefaultWidth:TiDimensionAutoFill];
}
#endif

- (id)initWithStyle:(UIProgressViewStyle)_style andMinimumValue:(CGFloat)_min maximumValue:(CGFloat)_max;
{
  if (self = [super initWithFrame:CGRectZero]) {
    currentValue = 0;
    style = _style;
    min = _min;
    max = _max;
    [self setHidden:YES];

#ifdef TI_USE_AUTOLAYOUT
    backgroundView = [[UIView alloc] init];
    [backgroundView setTranslatesAutoresizingMaskIntoConstraints:NO];
    [self addSubview:backgroundView];
#endif
  }
  return self;
}

- (void)dealloc
{
  RELEASE_TO_NIL(progress);
  //	RELEASE_TO_NIL(messageLabel);
  [super dealloc];
}

- (UIView *)viewForHitTest
{
  return progress;
}

//-(CGSize)sizeForFont:(CGFloat)suggestedWidth
//{
//	NSAttributedString *value = [messageLabel attributedText];
//	CGSize maxSize = CGSizeMake(suggestedWidth<=0 ? 480 : suggestedWidth, 1000);
//    CGSize returnVal = [value boundingRectWithSize:maxSize
//                                  options:NSStringDrawingUsesLineFragmentOrigin
//                                  context:nil].size;
//    return CGSizeMake(ceilf(returnVal.width), ceilf(returnVal.height));
//}

- (CGSize)contentSizeForSize:(CGSize)size
{
    //CGSize fontSize = [self sizeForFont:width];
    //CGSize progressSize = [progress sizeThatFits:fontSize];
    //if (messageLabel == nil) {
    //    return fontSize.height + progressSize.height;
    //}
    //return fontSize.height + progressSize.height + 5;
  return [progress sizeThatFits:size];
}

- (id)accessibilityElement
{
  return [self progress];
  }

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  [progress setFrame:bounds];
  [super frameSizeChanged:frame bounds:bounds];
}

#pragma mark Accessors

- (UIProgressView *)progress
{
  if (progress == nil) {
    progress = [[UIProgressView alloc] initWithProgressViewStyle:style];
#ifdef TI_USE_AUTOLAYOUT
    [progress setTranslatesAutoresizingMaskIntoConstraints:NO];
    [backgroundView addSubview:progress];
#else
    [self addSubview:progress];
#endif
  }
  return progress;
}

//-(UILabel *)messageLabel
//{
//	if (messageLabel==nil)
//	{
//		messageLabel=[[UILabel alloc] init];
//		[messageLabel setBackgroundColor:[UIColor clearColor]];
//
//		[self setNeedsLayout];
//		[self addSubview:messageLabel];
//	}
//	return messageLabel;
//}

//- (id)accessibilityElement
//{
//	return [self messageLabel];
//}

#pragma mark Repositioning

//-(void)layoutSubviews
//{
//	if(progress == nil)
//	{
//		return;
//	}
//
//	CGRect boundsRect = [self bounds];
//
//	CGSize barSize = [progress sizeThatFits:boundsRect.size];
//
//	CGPoint centerPoint = CGPointMake(boundsRect.origin.x + (boundsRect.size.width/2),
//			boundsRect.origin.y + (boundsRect.size.height/2));
//
//	[progress setBounds:CGRectMake(0, 0, barSize.width, barSize.height)];
//
//	if (messageLabel == nil)
//	{
//		[progress setCenter:centerPoint];
//		return;
//	}
//
//	CGSize messageSize = [messageLabel sizeThatFits:boundsRect.size];
//
//	float fittingHeight = barSize.height + messageSize.height + 5;
//
//	[progress setCenter:CGPointMake(centerPoint.x,
//			centerPoint.y + (fittingHeight - barSize.height)/2)];
//
//	[messageLabel setBounds:CGRectMake(0, 0, messageSize.width, messageSize.height)];
//	[messageLabel setCenter:CGPointMake(centerPoint.x,
//			centerPoint.y - (fittingHeight - messageSize.height)/2)];
//}

#pragma mark Properties

- (void)setMin_:(id)value
{
  min = [TiUtils floatValue:value];
  [self updateValue];
}

- (void)setMax_:(id)value
{
  max = [TiUtils floatValue:value];
  [self updateValue];
}

- (void)setValue_:(id)value
{
  currentValue = [TiUtils floatValue:value];
  [self updateValue];
}

- (void)updateValue
{
  [[self progress] setProgress:(currentValue - min) / (max - min)];
}

//-(void)setFont_:(id)value
//{
//	WebFont * newFont = [TiUtils fontValue:value def:[WebFont defaultFont]];
//	[[self messageLabel] setFont:[newFont font]];
//	[self setNeedsLayout];
//}

//-(void)setColor_:(id)value
//{
//	UIColor * newColor = [[TiUtils colorValue:value] _color];
//	if (newColor == nil) {
//		newColor = [UIColor blackColor];
//	}
//	[[self messageLabel] setTextColor:newColor];
//}

//-(void)setMessage_:(id)value
//{
//	NSString * text = [TiUtils stringValue:value];
//	if ([text length]>0)
//	{
//		[[self messageLabel] setText:text];
//	}
//	else
//	{
//		[messageLabel removeFromSuperview];
//		RELEASE_TO_NIL(messageLabel);
//	}
//	[self setNeedsLayout];
//}

- (void)setTrackTintColor_:(id)value
{
  UIColor *newColor = [[TiUtils colorValue:value] _color];
  [[self progress] setTrackTintColor:newColor];
}

#ifdef TI_USE_AUTOLAYOUT
- (void)updateConstraints
{
  if (!_constraintsAdded) {
    _constraintsAdded = YES;
    messageLabel = [self messageLabel];
    progress = [self progress];
    [backgroundView addConstraints:TI_CONSTR(@"V:|[progress]-[messageLabel]|", NSDictionaryOfVariableBindings(progress, messageLabel))];
    [backgroundView addConstraints:TI_CONSTR(@"H:|[progress]|", NSDictionaryOfVariableBindings(progress, messageLabel))];
    [backgroundView addConstraint:[NSLayoutConstraint constraintWithItem:messageLabel
                                                               attribute:NSLayoutAttributeCenterX
                                                               relatedBy:NSLayoutRelationEqual
                                                                  toItem:backgroundView
                                                               attribute:NSLayoutAttributeCenterX
                                                              multiplier:1
                                                                constant:0]];
  }
  [super updateConstraints];
}
#endif

@end

#endif
