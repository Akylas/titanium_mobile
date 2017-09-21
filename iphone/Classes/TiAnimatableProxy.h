#import "TiAnimation.h"
#import "TiParentingProxy.h"

@interface TiAnimatableProxy : TiParentingProxy <TiAnimationDelegate>
@property (nonatomic, assign) BOOL animating;

- (void)animate:(id)arg;

- (HLSAnimation *)animationForAnimation:(TiAnimation *)animation;

- (void)handleAnimation:(TiAnimation *)animation;
- (void)handleAnimation:(TiAnimation *)animation witDelegate:(id)delegate;
- (void)handlePendingAnimation:(TiAnimation *)pendingAnimation;
- (void)handlePendingAnimation;
- (void)cancelAnimation:(TiAnimation *)animation;
- (void)cancelAnimation:(TiAnimation *)animation shouldReset:(BOOL)reset;

- (void)addRunningAnimation:(TiAnimation *)animation;
- (void)removeRunningAnimation:(TiAnimation *)animation;

- (BOOL)animating;
- (BOOL)readyToAnimate;
- (void)playAnimation:(HLSAnimation *)animation withRepeatCount:(NSUInteger)repeatCount afterDelay:(double)delay;

#pragma Public API
- (void)cancelAllAnimations:(id)arg;
- (void)resetProxyPropertiesForAnimation:(TiAnimation *)animation;
- (void)clearAnimations;
- (void)removePendingAnimation:(TiAnimation *)animation;
- (void)applyPendingFromProps;

//used to know i a new animation should be created when we found a binded proxy
//in animation properties. Important because it might not use the same animation system
- (Class)animationClassType;

@end
