/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2016 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */

#if defined(USE_TI_AUDIOOPENMUSICLIBRARY) || defined(USE_TI_AUDIOQUERYMUSICLIBRARY) || defined(USE_TI_AUDIOSYSTEMMUSICPLAYER) || defined(USE_TI_AUDIOAPPMUSICPLAYER) || defined(USE_TI_AUDIOGETSYSTEMMUSICPLAYER) || defined(USE_TI_MEDIAGETAPPMUSICPLAYER)

#import "TiBlob.h"
#import "TiProxy.h"
#import <Foundation/Foundation.h>
#import <MediaPlayer/MediaPlayer.h>

// Not 'officially' a proxy since we don't want users being able to create these; they're
// generated internally only for the media player.
@interface TiAudioItem : TiProxy {
  MPMediaItem *item;
}

#pragma mark Properties

@property (nonatomic, readonly) TiBlob *artwork;

#pragma mark Internal

- (id)_initWithPageContext:(id<TiEvaluator>)context item:(MPMediaItem *)item_;
- (MPMediaItem *)item;

@end

#endif
