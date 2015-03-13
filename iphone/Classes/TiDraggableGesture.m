/**
 * An enhanced fork of the original TiDraggable module by Pedro Enrique,
 * allows for simple creation of "draggable" views.
 *
 * Copyright (C) 2013 Seth Benjamin
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * -- Original License --
 *
 * Copyright 2012 Pedro Enrique
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#import <objc/runtime.h>
#import <objc/message.h>
#import "TiDraggableGesture.h"

#define LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(methodName,layoutName,converter,postaction)	\
-(void)methodName:(id)value	\
{	\
TiDimension result = converter(value);\
if ( TiDimensionIsDip(result) || TiDimensionIsPercent(result) ) {\
layoutName = result;\
}\
else {\
if (!TiDimensionIsUndefined(result)) {\
DebugLog(@"[WARN] Invalid value %@ specified for property %@",[TiUtils stringValue:value],@#layoutName); \
} \
layoutName = TiDimensionUndefined;\
}\
postaction; \
}


@implementation TiDraggableGesture
{
    
    TiDimension _minLeft;
    TiDimension _maxLeft;
    TiDimension _maxRight;
    TiDimension _minTop;
    TiDimension _maxTop;
}

- (id)initWithProxy:(TiViewProxy*)proxy andOptions:(NSDictionary *)options
{
    if (self = [super init])
    {
        self.proxy = proxy;
        self.gesture = [[UIPanGestureRecognizer alloc] initWithTarget:self action:@selector(panDetected:)];

        [self.proxy setProxyObserver:self];

        [self setValuesForKeysWithDictionary:options];
        [self correctMappedProxyPositions];
    }

    return self;
}

- (void)proxyDidRelayout:(id)sender
{
    BOOL gestureIsAttached = [self.proxy.view.gestureRecognizers containsObject:self.gesture];

    if (! gestureIsAttached && [self.proxy viewReady])
    {
        [self.proxy.view addGestureRecognizer:self.gesture];
    }
}

-(void)propChanged
{
    
}

LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setMinLeft,_minLeft,TiDimensionFromObject,[self propChanged])
LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setMaxLeft,_maxLeft,TiDimensionFromObject,[self propChanged])
LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setMaxRight,_maxRight,TiDimensionFromObject,[self propChanged])
LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setMinTop,_minTop,TiDimensionFromObject,[self propChanged])
LAYOUTPROPERTIES_SETTER_IGNORES_AUTO(setMaxTop,_maxTop,TiDimensionFromObject,[self propChanged])

- (void)dealloc
{
    RELEASE_TO_NIL(self.gesture);

    [super dealloc];
}

- (void)setConfig:(id)args
{
    BOOL didUpdateConfig = NO;

    if ([args isKindOfClass:[NSDictionary class]])
    {
        [self setValuesForKeysWithDictionary:args];

        didUpdateConfig = YES;
    }
    else if ([args isKindOfClass:[NSArray class]] && [args count] >= 2)
    {
        NSString* key = nil;
        id value = [args objectAtIndex:1];

        ENSURE_ARG_AT_INDEX(key, args, 0, NSString);

        NSMutableDictionary* params = [[self valueForKey:@"config"] mutableCopy];

        if (! params)
        {
            params = [[NSMutableDictionary alloc] init];
        }

        [params setValue:value forKeyPath:key];

        [self setValuesForKeysWithDictionary:[[params copy] autorelease]];

        [params release];

        didUpdateConfig = YES;
    }

    if (didUpdateConfig)
    {
        [self correctMappedProxyPositions];
    }
}

- (void)panDetected:(UIPanGestureRecognizer *)panRecognizer
{
    ENSURE_UI_THREAD_1_ARG(panRecognizer);

    if ([TiUtils boolValue:[self valueForKey:@"enabled"] def:YES] == NO)
    {
        return;
    }
    TiViewProxy* parentViewProxy = [self.proxy viewParent];
    UIView *parentView = [parentViewProxy parentViewForChild:self.proxy];
    CGSize referenceSize = (parentView != nil) ? parentView.bounds.size : self.proxy.sandboxBounds.size;
    NSString* axis = [self valueForKey:@"axis"];
    CGFloat maxLeft =  TiDimensionCalculateValue(_maxLeft, referenceSize.width);
    CGFloat minLeft = TiDimensionCalculateValue(_minLeft, referenceSize.width);
    CGFloat maxRight = TiDimensionCalculateValue(_maxRight, referenceSize.width);
    CGFloat maxTop = TiDimensionCalculateValue(_maxTop, referenceSize.height);
    CGFloat minTop = TiDimensionCalculateValue(_minTop, referenceSize.height);
    BOOL hasMaxLeft = !TiDimensionIsUndefined(_maxLeft);
    BOOL hasMinLeft = !TiDimensionIsUndefined(_minLeft);
    BOOL hasMaxRight = !TiDimensionIsUndefined(_maxRight);
    BOOL hasMaxTop = !TiDimensionIsUndefined(_maxTop);
    BOOL hasMinTop = !TiDimensionIsUndefined(_minTop);
    BOOL ensureRight = [TiUtils boolValue:[self valueForKey:@"ensureRight"] def:NO];
    BOOL ensureBottom = [TiUtils boolValue:[self valueForKey:@"ensureBottom"] def:NO];
    BOOL cancelAnimations = [TiUtils boolValue:[self valueForKey:@"cancelAnimations"] def:YES];

    if (cancelAnimations && [[self.proxy.view.layer animationKeys] count] > 0)
    {
        [self.proxy.view setFrame:[[self.proxy.view.layer presentationLayer] frame]];
        [self.proxy.view.layer removeAllAnimations];
    }

    CGPoint translation = [panRecognizer translationInView:self.proxy.view];
    CGPoint newCenter = self.proxy.view.center;
    CGSize size = self.proxy.view.frame.size;

    float tmpTranslationX = 0.0, tmpTranslationY = 0.0;

    if ([panRecognizer state] == UIGestureRecognizerStateBegan)
    {
        touchStart = self.proxy.view.frame.origin;
    }
    else if ([panRecognizer state] == UIGestureRecognizerStateEnded)
    {
        touchEnd = self.proxy.view.frame.origin;
    }

    if([[self valueForKey:@"axis"] isEqualToString:@"x"])
    {
        tmpTranslationX = translation.x;

        newCenter.x += translation.x;
        newCenter.y = newCenter.y;
    }
    else if([[self valueForKey:@"axis"] isEqualToString:@"y"])
    {
        tmpTranslationY = translation.y;

        newCenter.x = newCenter.x;
        newCenter.y += translation.y;
    }
    else
    {
        tmpTranslationX = translation.x;
        tmpTranslationY = translation.y;

        newCenter.x += translation.x;
        newCenter.y += translation.y;
    }
    
    CGFloat width_2 = size.width / 2;
    CGFloat height_2 = size.height / 2;

    if(hasMaxLeft || hasMaxTop || hasMinLeft || hasMinTop)
    {
        if(hasMaxLeft && newCenter.x - width_2 > maxLeft)
        {
            newCenter.x = maxLeft + width_2;
        }
        else if(hasMaxRight && newCenter.x + width_2 > maxRight)
        {
            newCenter.x = maxRight - width_2;
        }
        else if(hasMinLeft && newCenter.x - width_2 < minLeft)
        {
            newCenter.x = minLeft + width_2;
        }

        if(hasMaxTop && newCenter.y - height_2 > maxTop)
        {
            newCenter.y = maxTop + height_2;
        }
        else if(hasMinTop && newCenter.y - height_2 < minTop)
        {
            newCenter.y = minTop + height_2;
        }
    }

    LayoutConstraint* layoutProperties = [self.proxy layoutProperties];

    if ([self valueForKey:@"axis"] == nil || [[self valueForKey:@"axis"] isEqualToString:@"x"])
    {
        layoutProperties->left = TiDimensionDip(newCenter.x - size.width / 2);

        if (ensureRight)
        {
            layoutProperties->right = TiDimensionDip(layoutProperties->left.value * -1);
        }
    }

    if ([self valueForKey:@"axis"] == nil || [[self valueForKey:@"axis"] isEqualToString:@"y"])
    {
        layoutProperties->top = TiDimensionDip(newCenter.y - size.height / 2);

        if (ensureBottom)
        {
            layoutProperties->bottom = TiDimensionDip(layoutProperties->top.value * -1);
        }
    }

    [self.proxy performBlockWithoutLayout:^{
        [self.proxy willChangeSize];
        [self.proxy willChangePosition];
    }];
    [self.proxy refreshViewOrParent];

    [panRecognizer setTranslation:CGPointZero inView:self.proxy.view];

    [self mapProxyOriginToCollection:[self valueForKey:@"maps"]
                    withTranslationX:tmpTranslationX
                     andTranslationY:tmpTranslationY];

    TiViewProxy* panningProxy = (TiViewProxy*)[self.proxy.view proxy];

    float left = [panningProxy view].frame.origin.x;
    float top = [panningProxy view].frame.origin.y;

    if([panningProxy _hasListeners:@"dragstart" checkParent:NO] && [panRecognizer state] == UIGestureRecognizerStateBegan)
    {
        [panningProxy fireEvent:@"dragstart" withObject:[NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                     [NSNumber numberWithFloat:left], @"left",
                                                     [NSNumber numberWithFloat:top], @"top",
                                                     [TiUtils pointToDictionary:self.proxy.view.center], @"center",
                                                     [TiUtils pointToDictionary:[panRecognizer velocityInView:self.proxy.view]], @"velocity",
                                                     nil] propagate:NO checkForListener:NO];
    }
    else if([panningProxy _hasListeners:@"dragmove" checkParent:NO] && [panRecognizer state] == UIGestureRecognizerStateChanged)
    {
        [panningProxy fireEvent:@"dragmove" withObject:[NSMutableDictionary dictionaryWithObjectsAndKeys:
                                                    [NSNumber numberWithFloat:left], @"left",
                                                    [NSNumber numberWithFloat:top], @"top",
                                                    [TiUtils pointToDictionary:self.proxy.view.center], @"center",
                                                    [TiUtils pointToDictionary:[panRecognizer velocityInView:self.proxy.view]], @"velocity",
                                                    nil] propagate:NO checkForListener:NO];
    }
    else if([panRecognizer state] == UIGestureRecognizerStateEnded || [panRecognizer state] == UIGestureRecognizerStateCancelled)
    {
        if([panningProxy _hasListeners:@"dragend" checkParent:NO]) {
            [panningProxy fireEvent:@"dragend"
                         withObject:[NSMutableDictionary dictionaryWithObjectsAndKeys:[NSDictionary dictionaryWithObjectsAndKeys:
                                                                                       [NSNumber numberWithFloat:touchEnd.x - touchStart.x], @"x",
                                                                                       [NSNumber numberWithFloat:touchEnd.y - touchStart.y], @"y",
                                                                                       nil], @"distance",
                                     @([panRecognizer state] == UIGestureRecognizerStateCancelled), @"cancel", nil]];
        }
        
    }
}

- (void)correctMappedProxyPositions
{
    NSArray* maps = [self valueForKey:@"maps"];

    if ([maps isKindOfClass:[NSArray class]])
    {
        [maps enumerateObjectsUsingBlock:^(id map, NSUInteger index, BOOL *stop) {
            TiViewProxy* proxy = [map objectForKey:@"view"];
            NSDictionary* constraints = [map objectForKey:@"constrain"];
            NSDictionary* constraintX = [constraints objectForKey:@"x"];
            NSDictionary* constraintY = [constraints objectForKey:@"y"];
            BOOL fromCenterX = [TiUtils boolValue:[constraintX objectForKey:@"fromCenter"] def:NO];
            BOOL fromCenterY = [TiUtils boolValue:[constraintY objectForKey:@"fromCenter"] def:NO];

            CGSize proxySize = [proxy view].frame.size;
            CGPoint proxyCenter = [proxy view].center;

            NSNumber* parallaxAmount = [TiUtils numberFromObject:[map objectForKey:@"parallaxAmount"]];

            if (! parallaxAmount)
            {
                parallaxAmount = [NSNumber numberWithInteger:1];
            }

            if (constraintX)
            {
                NSNumber* startX = [constraintX objectForKey:@"start"];

                if (fromCenterX)
                {
                    proxyCenter.x = [startX floatValue];
                    proxyCenter.x += [proxy.parent view].frame.size.width / 2;
                }
                else
                {
                    proxyCenter.x = [startX floatValue] / [parallaxAmount floatValue];
                    proxyCenter.x += proxySize.width / 2;
                }
            }

            if (constraintY)
            {
                NSNumber* startY = [constraintY objectForKey:@"start"];

                if (fromCenterY)
                {
                    proxyCenter.y = [startY floatValue];
                    proxyCenter.y += [proxy.parent view].frame.size.height / 2;
                }
                else
                {
                    proxyCenter.y = [startY floatValue] / [parallaxAmount floatValue];
                    proxyCenter.y += proxySize.height / 2;
                }
            }

            if (constraintX || constraintY)
            {
                [proxy view].center = proxyCenter;
            }

            LayoutConstraint* layoutProperties = [proxy layoutProperties];

            if (constraintX)
            {
                layoutProperties->left = TiDimensionDip([proxy view].frame.origin.x);
            }

            if (constraintY)
            {
                layoutProperties->top = TiDimensionDip([proxy view].frame.origin.y);
            }
            
            [proxy performBlockWithoutLayout:^{
                [proxy willChangeSize];
                [proxy willChangePosition];
            }];
            [proxy refreshViewOrParent];
        }];
    }
}

- (void)mapProxyOriginToCollection:(NSArray*)proxies withTranslationX:(float)translationX andTranslationY:(float)translationY
{
    if ([proxies isKindOfClass:[NSArray class]])
    {
        BOOL cancelAnimations = [TiUtils boolValue:[self valueForKey:@"cancelAnimations"] def:YES];

        [proxies enumerateObjectsUsingBlock:^(id map, NSUInteger index, BOOL *stop) {
            TiViewProxy* proxy = [map objectForKey:@"view"];

            if (cancelAnimations && [[proxy.view.layer animationKeys] count] > 0)
            {
                [proxy.view setFrame:[[proxy.view.layer presentationLayer] frame]];
                [proxy.view.layer removeAllAnimations];
            }

            CGPoint proxyCenter = [proxy view].center;
            CGSize proxySize = [proxy view].frame.size;
            CGSize parentSize = [[proxy.parent view] frame].size;

            NSNumber* parallaxAmount = [TiUtils numberFromObject:[map objectForKey:@"parallaxAmount"]];

            if (! parallaxAmount)
            {
                parallaxAmount = [NSNumber numberWithInteger:1];
            }

            NSDictionary* constraints = [map objectForKey:@"constrain"];
            NSDictionary* xConstraint = [constraints objectForKey:@"x"];
            NSDictionary* yConstraint = [constraints objectForKey:@"y"];
            NSString* constraintAxis = [constraints objectForKey:@"axis"];

            if (constraints)
            {
                if (xConstraint && ([constraintAxis isEqualToString:@"x"] || constraintAxis == nil))
                {
                    NSNumber* parentMinLeft = [self valueForKey:@"minLeft"];
                    NSNumber* parentMaxLeft = [self valueForKey:@"maxLeft"];
                    NSNumber* xStart = [xConstraint objectForKey:@"start"];
                    NSNumber* xEnd = [xConstraint objectForKey:@"end"];

                    float xDistance = [parentMaxLeft floatValue] - [parentMinLeft floatValue];
                    float xCalcCenter = proxySize.width / 2;
                    float xWidth, xStartParallax = 0.0, xEndParallax, xRatio;

                    if (xStart && xEnd)
                    {
                        xStartParallax = [xStart floatValue] / [parallaxAmount floatValue];
                        xWidth = fabsf(xStartParallax) + fabsf([xEnd floatValue]);
                    }
                    else
                    {
                        xWidth = proxySize.width / [parallaxAmount floatValue];
                    }

                    if (parentMinLeft || parentMaxLeft)
                    {
                        xRatio = xDistance == 0 ? 1 : xWidth / xDistance;
                    }
                    else
                    {
                        xRatio = xWidth / (parentSize.width / 2);
                    }

                    proxyCenter.x += (translationX * ([xEnd floatValue] < xStartParallax ? -1 : 1)) * xRatio;

                    if(xStart && xEnd)
                    {
                        BOOL xFromCenter = [TiUtils boolValue:[xConstraint objectForKey:@"fromCenter"] def:NO];
                        float xLeftEdge = proxyCenter.x - xCalcCenter;

                        if (xFromCenter)
                        {
                            xStart = [NSNumber numberWithFloat:[xStart floatValue] + xLeftEdge];
                            xEnd = [NSNumber numberWithFloat:[xEnd floatValue] + xLeftEdge];
                        }

                        if ([xEnd floatValue] > [xStart floatValue])
                        {
                            if(xLeftEdge > [xEnd floatValue])
                            {
                                proxyCenter.x = [xEnd floatValue] + xCalcCenter;
                            }
                            else if(xLeftEdge < xStartParallax)
                            {
                                proxyCenter.x = xStartParallax + xCalcCenter;
                            }
                        }
                        else
                        {
                            if(xLeftEdge < [xEnd floatValue])
                            {
                                proxyCenter.x = [xEnd floatValue] + xCalcCenter;
                            }
                            else if(xLeftEdge > xStartParallax)
                            {
                                proxyCenter.x = xStartParallax + xCalcCenter;
                            }
                        }

                        KrollCallback* xCallback = [xConstraint objectForKey:@"callback"];

                        if (xCallback)
                        {
                            float currentLeftEdge = proxyCenter.x - xCalcCenter;
                            float translationCompleted = fabsf((currentLeftEdge - xStartParallax) / xWidth);

                            [proxy.parent _fireEventToListener:@"translated"
                                                    withObject:@{ @"completed" : [NSNumber numberWithFloat:translationCompleted] }
                                                      listener:xCallback
                                                    thisObject:nil];
                        }
                    }
                }
                else if ([constraintAxis isEqualToString:@"x"])
                {
                    proxyCenter.x += translationX / [parallaxAmount floatValue];
                }

                if (yConstraint && ([constraintAxis isEqualToString:@"y"] || constraintAxis == nil))
                {
                    NSNumber* parentMinTop = [self valueForKey:@"minTop"];
                    NSNumber* parentMaxTop = [self valueForKey:@"maxTop"];
                    NSNumber* yStart = [yConstraint objectForKey:@"start"];
                    NSNumber* yEnd = [yConstraint objectForKey:@"end"];

                    float yDistance = [parentMaxTop floatValue] - [parentMinTop floatValue];
                    float yCalcCenter = proxySize.height / 2;
                    float yHeight, yStartParallax, yEndParallax, yRatio;

                    if (yStart && yEnd)
                    {
                        yStartParallax = [yStart floatValue] / [parallaxAmount floatValue];
                        yHeight = fabsf(yStartParallax) + fabsf([yEnd floatValue]);
                    }
                    else
                    {
                        yHeight = proxySize.height / [parallaxAmount floatValue];
                    }

                    if (parentMinTop || parentMaxTop)
                    {
                        yRatio = yDistance == 0 ? 1 : yHeight / yDistance;
                    }
                    else
                    {
                        yRatio = yHeight / (parentSize.height / 2);
                    }

                    proxyCenter.y += (translationY * ([yEnd floatValue] < yStartParallax ? -1 : 1)) * yRatio;

                    if(yStart && yEnd)
                    {
                        BOOL yFromCenter = [TiUtils boolValue:[yConstraint objectForKey:@"fromCenter"] def:NO];
                        float yTopEdge = proxyCenter.y - yCalcCenter;

                        if (yFromCenter)
                        {
                            yStart = [NSNumber numberWithFloat:[yStart floatValue] + yTopEdge];
                            yEnd = [NSNumber numberWithFloat:[yEnd floatValue] + yTopEdge];
                        }

                        if ([yEnd floatValue] > [yStart floatValue])
                        {
                            if(yTopEdge > [yEnd floatValue])
                            {
                                proxyCenter.y = [yEnd floatValue] + yCalcCenter;
                            }
                            else if(yTopEdge < yStartParallax)
                            {
                                proxyCenter.y = yStartParallax + yCalcCenter;
                            }
                        }
                        else
                        {
                            if(yTopEdge < [yEnd floatValue])
                            {
                                proxyCenter.y = [yEnd floatValue] + yCalcCenter;
                            }
                            else if(yTopEdge > yStartParallax)
                            {
                                proxyCenter.y = yStartParallax + yCalcCenter;
                            }
                        }

                        KrollCallback* yCallback = [yConstraint objectForKey:@"callback"];

                        if (yCallback)
                        {
                            float currentTopEdge = proxyCenter.y - yCalcCenter;
                            float translationCompleted = fabsf((currentTopEdge - yStartParallax) / yHeight);

                            [proxy.parent _fireEventToListener:@"translated"
                                                    withObject:@{ @"completed" : [NSNumber numberWithFloat:translationCompleted] }
                                                      listener:yCallback
                                                    thisObject:nil];
                        }
                    }
                }
                else if ([constraintAxis isEqualToString:@"y"])
                {
                    proxyCenter.y += translationY / [parallaxAmount floatValue];
                }
            }
            else
            {
                proxyCenter.x += translationX / [parallaxAmount floatValue];
                proxyCenter.y += translationY / [parallaxAmount floatValue];
            }

            TiProxy* proxyDraggable = [proxy valueForKey:@"draggable"];

            NSInteger maxLeft = [[proxyDraggable valueForKey:@"maxLeft"] floatValue];
            NSInteger minLeft = [[proxyDraggable valueForKey:@"minLeft"] floatValue];
            NSInteger maxTop = [[proxyDraggable valueForKey:@"maxTop"] floatValue];
            NSInteger minTop = [[proxyDraggable valueForKey:@"minTop"] floatValue];
            BOOL hasMaxLeft = [proxyDraggable valueForKey:@"maxLeft"] != nil;
            BOOL hasMinLeft = [proxyDraggable valueForKey:@"minLeft"] != nil;
            BOOL hasMaxTop = [proxyDraggable valueForKey:@"maxTop"] != nil;
            BOOL hasMinTop = [proxyDraggable valueForKey:@"minTop"] != nil;
            BOOL ensureRight = [TiUtils boolValue:[proxyDraggable valueForKey:@"ensureRight"] def:NO];
            BOOL ensureBottom = [TiUtils boolValue:[proxyDraggable valueForKey:@"ensureBottom"] def:NO];

            if(hasMaxLeft || hasMaxTop || hasMinLeft || hasMinTop)
            {
                if(hasMaxLeft && proxyCenter.x - proxySize.width / 2 > maxLeft)
                {
                    proxyCenter.x = maxLeft + proxySize.width / 2;
                }
                else if(hasMinLeft && proxyCenter.x - proxySize.width / 2 < minLeft)
                {
                    proxyCenter.x = minLeft + proxySize.width / 2;
                }

                if(hasMaxTop && proxyCenter.y - proxySize.height / 2 > maxTop)
                {
                    proxyCenter.y = maxTop + proxySize.height / 2;
                }
                else if(hasMinTop && proxyCenter.y - proxySize.height / 2 < minTop)
                {
                    proxyCenter.y = minTop + proxySize.height / 2;
                }
            }

            LayoutConstraint* layoutProperties = [proxy layoutProperties];

            layoutProperties->top = TiDimensionDip(proxyCenter.y - proxySize.height / 2);
            layoutProperties->left = TiDimensionDip(proxyCenter.x - proxySize.width / 2);

            if (ensureBottom)
            {
                layoutProperties->bottom = TiDimensionDip(layoutProperties->top.value * -1);
            }

            if (ensureRight)
            {
                layoutProperties->right = TiDimensionDip(layoutProperties->left.value * -1);
            }
            
            [proxy performBlockWithoutLayout:^{
                [proxy willChangeSize];
                [proxy willChangePosition];
            }];
            [proxy refreshViewOrParent];
        }];
    }
}

@end