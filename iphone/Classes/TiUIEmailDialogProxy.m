/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIEMAILDIALOG

#import "TiUIEmailDialogProxy.h"
#import "Mimetypes.h"
#import "TiApp.h"
#import "TiBase.h"
#import "TiBlob.h"
#import "TiColor.h"
#import "TiFile.h"
#import "TiUtils.h"

@implementation TiUIEmailDialogProxy

- (void)dealloc
{
  RELEASE_TO_NIL(attachments);
  [super dealloc];
}

- (void)_destroy
{
  RELEASE_TO_NIL(attachments);
  [super _destroy];
}

- (NSString *)apiName
{
  return @"Ti.UI.EmailDialog";
}

- (NSArray *)attachments
{
  return attachments;
}

- (void)addAttachment:(id)ourAttachment
{
  ENSURE_SINGLE_ARG(ourAttachment, NSObject);

  if (attachments == nil) {
    attachments = [[NSMutableArray alloc] initWithObjects:ourAttachment, nil];
  } else {
    [attachments addObject:ourAttachment];
  }
}

- (id)isSupported:(id)args
{
#if TARGET_IPHONE_SIMULATOR
  DebugLog(@"[INFO] iOS Simulator does not support sending emails. Use a device instead.");
  return NUMBOOL(NO);
#endif
  return NUMBOOL([MFMailComposeViewController canSendMail]);
}

- (void)open:(id)args
{
  [self rememberSelf];
  NSDictionary *properties = nil;
  ENSURE_ARG_OR_NIL_AT_INDEX(properties, args, 0, NSDictionary);
  Class arrayClass = [NSArray class];
  NSArray *toArray = [self valueForUndefinedKey:@"toRecipients"];
  ENSURE_CLASS_OR_NIL(toArray, arrayClass);
  NSArray *bccArray = [self valueForUndefinedKey:@"bccRecipients"];
  ENSURE_CLASS_OR_NIL(bccArray, arrayClass);
  NSArray *ccArray = [self valueForUndefinedKey:@"ccRecipients"];
  ENSURE_CLASS_OR_NIL(ccArray, arrayClass);

  ENSURE_UI_THREAD(open, args);

  NSString *subject = [TiUtils stringValue:[self valueForUndefinedKey:@"subject"]];
  NSString *message = [TiUtils stringValue:[self valueForUndefinedKey:@"messageBody"]];

#if TARGET_IPHONE_SIMULATOR
  DebugLog(@"[INFO] iOS Simulator does not support sending emails. Use a device instead.");
  NSDictionary *event = [NSDictionary dictionaryWithObject:NUMINT(MFMailComposeResultFailed) forKey:@"result"];
  [self fireEvent:@"complete" withObject:event errorCode:MFMailComposeResultFailed message:@"iOS Simulator does not support sending emails. Use a device instead."];
#else
  if (![MFMailComposeViewController canSendMail]) {
    NSDictionary *event = [NSDictionary dictionaryWithObject:NUMINT(MFMailComposeResultFailed) forKey:@"result"];
    [self fireEvent:@"complete" withObject:event errorCode:MFMailComposeResultFailed message:@"system can't send email"];
    return;
  }

  UIColor *barColor = [[TiUtils colorValue:[self valueForUndefinedKey:@"barColor"]] _color];

  MFMailComposeViewController *composer = [[MFMailComposeViewController alloc] init];
  [composer setMailComposeDelegate:self];
  if (barColor != nil) {
    [[composer navigationBar] setBarTintColor:barColor];
  }

  [composer setSubject:subject];
  [composer setToRecipients:toArray];
  [composer setBccRecipients:bccArray];
  [composer setCcRecipients:ccArray];
  [composer setMessageBody:message isHTML:[TiUtils boolValue:[self valueForUndefinedKey:@"html"] def:NO]];

  if (attachments != nil) {
    for (id attachment in attachments) {
      if ([attachment isKindOfClass:[TiBlob class]]) {
        NSString *path = [(TiBlob *)attachment path];
        if (path == nil) {
          path = @"attachment";
        } else {
          path = [path lastPathComponent];
        }
        NSString *mimetype = [attachment mimeType];
        if (mimetype == nil) {
          mimetype = [Mimetypes mimeTypeForExtension:path];
        }
        [composer addAttachmentData:[attachment data]
                           mimeType:mimetype
                           fileName:path];
      } else if ([attachment isKindOfClass:[TiFile class]]) {
          TiFile *file = (TiFile *)attachment;
          NSString *path = [file path];
          NSData *data = [NSData dataWithContentsOfFile:path];
          NSString *mimetype = [Mimetypes mimeTypeForExtension:path];
          [composer addAttachmentData:data mimeType:mimetype fileName:[path lastPathComponent]];
      } else if ([attachment isKindOfClass:[NSDictionary class]]) {
          NSDictionary *dict = (NSDictionary *)attachment;
          NSString *path = [file path];
          id data = [dict objectForKey:@"content"];
          NSString * mimetype = [dict objectForKey:@"mimetype"]?[dict objectForKey:@"mimetype"]:[TiUtils mimeTypeForObject:data];
          NSString *filename = [TiUtils stringValue:[dict objectForKey:@"filename"]];
          [composer addAttachmentData:[TiUtils dataValue:data] mimeType:mimetype fileName:filename];
      }
    }
  }

  BOOL animated = [TiUtils boolValue:@"animated" properties:properties def:YES];
  [self retain];
  [[TiApp app] showModalController:composer animated:animated];
#endif
}

MAKE_SYSTEM_PROP(SENT, MFMailComposeResultSent);
MAKE_SYSTEM_PROP(SAVED, MFMailComposeResultSaved);
MAKE_SYSTEM_PROP(CANCELLED, MFMailComposeResultCancelled);
MAKE_SYSTEM_PROP(FAILED, MFMailComposeResultFailed);

#pragma mark Delegate

- (void)mailComposeController:(MFMailComposeViewController *)composer didFinishWithResult:(MFMailComposeResult)result error:(NSError *)error
{
  if (error != nil) {
    NSLog(@"[ERROR] Unexpected composing error: %@", error);
  }

  BOOL animated = YES;

  [[TiApp app] hideModalController:composer animated:animated];
  if ([self _hasListeners:@"complete"]) {
    if (error) {
      [self fireEvent:@"complete" withObject:[TiUtils dictionaryWithCode:[error code] message:[TiUtils messageFromError:error]] checkForListener:NO];
    } else {
      [self fireEvent:@"complete"
                withObject:@{
                  @"result" : @(result),
                  @"success" : @(result == MFMailComposeResultSent || result == MFMailComposeResultSaved),

                }
          checkForListener:NO];
    }
  }
  [self forgetSelf];
  [composer autorelease];
  [self autorelease];
}

@end

#endif
