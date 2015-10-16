/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2015 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#if IS_XCODE_7
#import "TiAppiOSSearchableItemAttributeSetProxy.h"
#import "TiUtils.h"

#ifdef USE_TI_APPIOS

@implementation TiAppiOSSearchableItemAttributeSetProxy

-(NSString*)apiName
{
    return @"Ti.App.iOS.SearchableItemAttributeSet";
}

-(void)dealloc
{
    RELEASE_TO_NIL(_attributes);
    [super dealloc];
}

static NSArray * dateFieldTypes = nil;
static NSArray * urlFieldTypes = nil;
static NSArray * unsupportedFieldTypes = nil;
-(void)initFieldTypeInformation
{
    if (dateFieldTypes==nil)
    {
        dateFieldTypes = @[@"metadataModificationDate",@"recordingDate",@"downloadedDate",@"lastUsedDate",@"contentCreationDate",@"contentModificationDate",@"addedDate",@"recordingDate",@"downloadedDate",@"lastUsedDate"];
    }
    if (urlFieldTypes==nil)
    {
        urlFieldTypes = @[@"contentURL",@"thumbnailURL",@"url"];
    }
    if (unsupportedFieldTypes==nil)
    {
        unsupportedFieldTypes = @[@"thumbnailData"];
    }
}

-(NSArray *)keySequence
{
    static NSArray *keySequence = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        keySequence = [@[@"contentType"] retain];
    });
    return keySequence;
}

-(CSSearchableItemAttributeSet*)attributes
{
    if (!_attributes) {
        [self initFieldTypeInformation];
        _attributes = [[CSSearchableItemAttributeSet alloc] initWithItemContentType:[TiUtils stringValue:[self valueForKey:@"contentType"]]];
    }
    return _attributes;
}

-(void)setValue:(id)value forKey:(NSString *)key
{
    if ([key isEqualToString:@"contentType"]) {
        [self replaceValue:value forKey:key notification:NO];
        return;
    }
    if ([[self attributes] respondsToSelector:NSSelectorFromString(key)]){
        [self replaceValue:value forKey:key notification:NO];
        TiThreadPerformBlockOnMainThread(^{
            //Check this is a supported type
            if(![unsupportedFieldTypes containsObject:key]){
                if([dateFieldTypes containsObject:key]){
                    //Use date logic to add
                    [_attributes setValue:[TiUtils dateForUTCDate:value] forKey:key];
                }else if([urlFieldTypes containsObject:key]){
                    //Use URL logic to add
                    [_attributes setValue:[self sanitizeURL:value] forKey:key];
                }else{
//                    if (IS_OF_CLASS(NSMutableArray, value)) {
//                    if([value respondsToSelector:@selector(removeObjectAtIndex:)]){
//                        id result=[NSKeyedUnarchiver unarchiveObjectWithData:[NSKeyedArchiver archivedDataWithRootObject:value]];
//                        [_attributes setValue:[[NSArray alloc] initWithArray:value copyItems:YES] forKey:key];
//                    } else {
                        [_attributes setValue:value forKey:key];
//                    }
                }
            }
            else {
                //Use blob to add
                [_attributes setValue:[value data] forKey:key];
            }
        },YES);
    } else {
        [super setValue:value forKey:key];
    }
}

@end
#endif
#endif