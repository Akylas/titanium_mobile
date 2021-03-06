/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2009-2010 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#ifdef USE_TI_UIWEBVIEW

#import "TiUIWebView.h"
#import "APSHTTPResponse.h"
#import "Base64Transcoder.h"
#import "Mimetypes.h"
#import "Mimetypes.h"
#import "TiApp.h"
#import "TiBlob.h"
#import "TiFile.h"
#import "TiFileSystemHelper.h"
#import "TiUIWebViewProxy.h"
#import "Webcolor.h"

extern NSString *const TI_APPLICATION_ID;

static NSString *const kMimeTextHTML = @"text/html";
static NSString *const kContentData = @"kContentData";
static NSString *const kContentDataEncoding = @"kContentDataEncoding";
static NSString *const kContentTextEncoding = @"kContentTextEncoding";
static NSString *const kContentMimeType = @"kContentMimeType";
static NSString *const kContentInjection = @"kContentInjection";
static NSOperationQueue *_operationQueue = nil;

static unsigned long localId = 0;

NSString *HTMLTextEncodingNameForStringEncoding(NSStringEncoding encoding)
{
  if (encoding == NSUTF8StringEncoding) {
    return @"utf-8";
  } else if (encoding == NSUTF16StringEncoding) {
    return @"utf-16";
  } else if (encoding == NSASCIIStringEncoding) {
    return @"us-ascii";
  } else if (encoding == NSISOLatin1StringEncoding) {
    return @"latin1";
  } else if (encoding == NSShiftJISStringEncoding) {
    return @"shift_jis";
  } else if (encoding == NSWindowsCP1252StringEncoding) {
    return @"windows-1251";
  }
  return nil;
}

@interface LocalProtocolHandler : NSURLProtocol {
}
+ (void)setContentInjection:(NSString *)contentInjection;

@end

@implementation TiUIWebView {
  APSHTTPRequest *_currentRequest;
  BOOL _asyncLoad;
  NJKWebViewProgress *_progressProxy;
  NSURL *loadingurl;
  BOOL alwaysInjectTi;
}
@synthesize reloadData, reloadDataProperties;

#ifdef TI_USE_AUTOLAYOUT
- (void)initializeTiLayoutView
{
  [super initializeTiLayoutView];
  [self setDefaultHeight:TiDimensionAutoFill];
  [self setDefaultWidth:TiDimensionAutoFill];
}
#endif

- (id)init
{
  self = [super init];
  if (self != nil) {
    _asyncLoad = NO;
    willHandleTouches = NO;
    alwaysInjectTi = NO;
  }
  return self;
}

- (void)dealloc
{
  if (webview != nil) {
    webview.delegate = nil;

    // per doc, must stop webview load before releasing
    if (webview.loading) {
      [webview stopLoading];
    }
  }
  if (listeners != nil) {
    RELEASE_TO_NIL(listeners);
  }
  RELEASE_TO_NIL(pageToken);
  RELEASE_TO_NIL(webview);
  RELEASE_TO_NIL(url);
  RELEASE_TO_NIL(loadingurl);
  RELEASE_TO_NIL(spinner);
  RELEASE_TO_NIL(basicCredentials);
  RELEASE_TO_NIL(reloadData);
  RELEASE_TO_NIL(reloadDataProperties);
  RELEASE_TO_NIL(lastValidLoad);
  RELEASE_TO_NIL(_currentRequest);
  RELEASE_TO_NIL(_progressProxy)
  RELEASE_TO_NIL(blacklistedURLs);
  RELEASE_TO_NIL(insecureConnection);
  [super dealloc];
}

+ (BOOL)isLocalURL:(NSURL *)url
{
  NSString *scheme = [url scheme];
  return [scheme isEqualToString:@"file"] || [scheme isEqualToString:@"app"];
}

- (void)viewForHitTest
{
  return webview;
}

- (UIView *)hitTest:(CGPoint)point withEvent:(UIEvent *)event
{
  /*	webview is a little _special_ and refuses to share events.
	 *	As such, we have to take the events away if we have event listeners
	 *	Or let webview has his entire cake. Through experimenting, if the
	 *	webview is interested, a subview or subsubview will be the target.
	 */

  UIView *view = [super hitTest:point withEvent:event];
  if (([self hasTouchableListener]) && willHandleTouches) {
    UIView *superview = [view superview];
    UIView *superduperview = [superview superview];
    if ((view == webview) || (superview == webview) || (superduperview == webview)) {
      return self;
    }
  }

  return view;
}

- (void)onInterceptTouchEvent:(UIEvent *)event
{
  UITouch *touch = [[event allTouches] anyObject];
  if ([self interactionEnabled]) {
    if (touch.phase == UITouchPhaseBegan) {
      [self processTouchesBegan:[event allTouches] withEvent:event];
    } else if (touch.phase == UITouchPhaseMoved) {
      [self processTouchesMoved:[event allTouches] withEvent:event];
    } else if (touch.phase == UITouchPhaseEnded) {
      [self processTouchesEnded:[event allTouches] withEvent:event];
    } else if (touch.phase == UITouchPhaseCancelled) {
      [self processTouchesCancelled:[event allTouches] withEvent:event];
    }
  }
}

- (void)setWillHandleTouches_:(id)args
{
  willHandleTouches = [TiUtils boolValue:args def:YES];
}

- (void)setAlwaysInjectTi_:(id)args
{
  alwaysInjectTi = [TiUtils boolValue:args def:YES];
}

- (UIWebView *)webview
{
  if (webview == nil) {
    // we attach the XHR bridge the first time we need a webview
    [[TiApp app] attachXHRBridgeIfRequired];

    webview = [[UIWebView alloc] initWithFrame:CGRectMake(0, 0, 10, 1)];
    webview.delegate = self;
    webview.opaque = NO;
    [self scrollView].delegate = self;
    webview.backgroundColor = [UIColor whiteColor];
    webview.contentMode = UIViewContentModeRedraw;
    webview.autoresizingMask = UIViewAutoresizingFlexibleHeight | UIViewAutoresizingFlexibleWidth;

#if IS_XCODE_9
    if ([TiUtils isIOS11OrGreater]) {
      webview.scrollView.contentInsetAdjustmentBehavior = UIScrollViewContentInsetAdjustmentNever;
    }
#endif

    [self addSubview:webview];

    _progressProxy = [[NJKWebViewProgress alloc] init]; // instance variable
    webview.delegate = _progressProxy;
    _progressProxy.webViewProxyDelegate = self;
    _progressProxy.progressDelegate = self;

    BOOL hideLoadIndicator = [TiUtils boolValue:[self.proxy valueForKey:@"hideLoadIndicator"] def:NO];
    ignoreSslError = [TiUtils boolValue:[[self proxy] valueForKey:@"ignoreSslError"] def:NO];
    isAuthenticated = NO;

    // only show the loading indicator if it's a remote URL and 'hideLoadIndicator' property is not set.
    if (![[self class] isLocalURL:url] && !hideLoadIndicator) {
      TiColor *bgcolor = [TiUtils colorValue:[self.proxy valueForKey:@"backgroundColor"]];
      UIActivityIndicatorViewStyle style = UIActivityIndicatorViewStyleGray;
      if (bgcolor != nil) {
        // check to see if the background is a dark color and if so, we want to
        // show the white indicator instead
        if ([Webcolor isDarkColor:[bgcolor _color]]) {
          style = UIActivityIndicatorViewStyleWhite;
        }
      }
      spinner = [[UIActivityIndicatorView alloc] initWithActivityIndicatorStyle:style];
      [spinner setHidesWhenStopped:YES];
      spinner.autoresizingMask = UIViewAutoresizingFlexibleTopMargin | UIViewAutoresizingFlexibleBottomMargin | UIViewAutoresizingFlexibleLeftMargin | UIViewAutoresizingFlexibleRightMargin;
      [self addSubview:spinner];
      [spinner sizeToFit];
      spinner.center = CGPointMake(CGRectGetMidX(self.bounds), CGRectGetMidY(self.bounds));
      [spinner startAnimating];
    }

    if ([[NSUserDefaults standardUserDefaults] objectForKey:@"DefaultUserAgent"] == nil) {
      NSString *defaultUserAgent = [webview stringByEvaluatingJavaScriptFromString:@"navigator.userAgent"];
      [[NSUserDefaults standardUserDefaults] setObject:defaultUserAgent forKey:@"DefaultUserAgent"];
    }
  }
  return webview;
}

- (id)accessibilityElement
{
  return [self webview];
}

+ (NSOperationQueue *)operationQueue;
{
  if (_operationQueue == nil) {
    _operationQueue = [[NSOperationQueue alloc] init];
    [_operationQueue setMaxConcurrentOperationCount:4];
  }
  return _operationQueue;
}
- (void)loadURLRequest:(NSMutableURLRequest *)request
{

  if (basicCredentials != nil) {
    [request setValue:basicCredentials forHTTPHeaderField:@"Authorization"];
  }

  // Set the custom request headers if specified
  NSDictionary *requestHeaders = [[self proxy] valueForKey:@"requestHeaders"];
  if (requestHeaders != nil) {
    for (NSString *key in [requestHeaders allKeys]) {
      [request setValue:[requestHeaders objectForKey:key] forHTTPHeaderField:key];
    }
  }

  if (_asyncLoad) {
    [NSURLConnection sendAsynchronousRequest:request
                                       queue:[TiUIWebView operationQueue]
                           completionHandler:^(NSURLResponse *response, NSData *data, NSError *error) {
                             if (error == nil) {
  [[self webview] loadRequest:request];
                             } else {
                               [self webView:[self webview] didFailLoadWithError:error];
}
                           }];
  } else {
    [[self webview] loadRequest:request];
  }
}

- (void)frameSizeChanged:(CGRect)frame bounds:(CGRect)bounds
{
  [super frameSizeChanged:frame bounds:bounds];
  if (webview != nil) {
    [TiUtils setView:webview positionRect:bounds];

    if (spinner != nil) {
      spinner.center = CGPointMake(CGRectGetMidX(bounds), CGRectGetMidY(bounds));
    }
  }
}

- (NSURL *)fileURLToAppURL:(NSURL *)url_
{
  NSString *basepath = [TiHost resourcePath];
  NSString *urlstr = [url_ path];
  NSString *path = [urlstr stringByReplacingOccurrencesOfString:[NSString stringWithFormat:@"%@/", basepath] withString:@""];
  if ([path hasPrefix:@"/"]) {
    path = [path substringFromIndex:1];
  }
  return [NSURL URLWithString:[[NSString stringWithFormat:@"app://%@/%@", TI_APPLICATION_ID, path] stringByAddingPercentEscapesUsingEncoding:NSUTF8StringEncoding]];
}

- (NSString *)_titaniumInjection
{
  if (pageToken == nil) {
    pageToken = [[NSString stringWithFormat:@"%lu", (unsigned long)[self hash]] retain];
    [(TiUIWebViewProxy *)self.proxy setPageToken:pageToken];
  }

  static NSString *const kTitaniumJavascript = @"Ti.App={},Ti.API={},Ti.App._listeners={},Ti.App._listener_id=1,Ti.App.id=Ti.appId,Ti.App._xhr=XMLHttpRequest,Ti._broker=function(e,i,n){try{var xhr=new Ti.App._xhr();xhr.open('GET','app://'+Ti.appId+'/_TiA0_'+Ti.pageToken+'/'+e+'/'+i+'?'+encodeURIComponent(JSON.stringify(n)),false);xhr.send()}catch(X){}},Ti.App._dispatchEvent=function(e,i,n){var p=Ti.App._listeners[e];if(p)for(var r=0;r<p.length;r++){var o=p[r];o.id==i&&o.callback.call(o.callback,n)}},Ti.App.emit=Ti.App.fireEvent=function(e,i){Ti._broker('App','emit',{name:e,event:i})},Ti.API.log=function(e,i){Ti._broker('API','log',{level:e,message:i})},Ti.API.debug=function(e){Ti._broker('API','log',{level:'debug',message:e})},Ti.API.error=function(e){Ti._broker('API','log',{level:'error',message:e})},Ti.API.info=function(e){Ti._broker('API','log',{level:'info',message:e})},Ti.API.fatal=function(e){Ti._broker('API','log',{level:'fatal',message:e})},Ti.API.warn=function(e){Ti._broker('API','log',{level:'warn',message:e})},Ti.App.on=Ti.App.addEventListener=function(e,i){var n=Ti.App._listeners[e];'undefined'==typeof n&&(n=[],Ti.App._listeners[e]=n);var p=Ti.pageToken+Ti.App._listener_id++;n.push({callback:i,id:p}),Ti._broker('App','on',{name:e,id:p})},Ti.App.off=Ti.App.removeEventListener=function(e,i){var n=Ti.App._listeners[e];if(n)for(var p=0;p<n.length;p++){var r=n[p];if(r.callback==i){n.splice(p,1),Ti._broker('App','off',{name:e,id:r.id});break}}};";

  NSMutableString *html = [[[NSMutableString alloc] init] autorelease];
  [html appendString:@"<script id='__ti_injection'>"];
  NSString *ti = [NSString stringWithFormat:@"%@%s", @"Ti", "tanium"];
  [html appendFormat:@"window.%@={};window.Ti=%@;Ti.pageToken=%@;Ti.appId='%@';", ti, ti, pageToken, TI_APPLICATION_ID];
  [html appendString:kTitaniumJavascript];
  [html appendString:@"</script>"];
  return html;
}
- (NSString *)__titaniumRemoteInjection
{
  if (pageToken == nil) {
    pageToken = [[NSString stringWithFormat:@"%lu", (unsigned long)[self hash]] retain];
    [(TiUIWebViewProxy *)self.proxy setPageToken:pageToken];
  }
  static NSString *const kTitaniumRemoteJavascript = @"Ti.App={},Ti.API={},Ti.App._listeners={},Ti.App._listener_id=1,Ti.App.id=Ti.appId,Ti._broker=function(e,i,n){window.location='app://'+Ti.appId+'/_TiA0_'+Ti.pageToken+'/'+e+'/'+i+'?'+encodeURIComponent(JSON.stringify(n))},Ti.App._dispatchEvent=function(e,i,n){var p=Ti.App._listeners[e];if(p)for(var r=0;r<p.length;r++){var o=p[r];o.id==i&&o.callback.call(o.callback,n)}},Ti.App.emit=Ti.App.fireEvent=function(e,i){Ti._broker('App','emit',{name:e,event:i})},Ti.API.log=function(e,i){Ti._broker('API','log',{level:e,message:i})},Ti.API.debug=function(e){Ti._broker('API','log',{level:'debug',message:e})},Ti.API.error=function(e){Ti._broker('API','log',{level:'error',message:e})},Ti.API.info=function(e){Ti._broker('API','log',{level:'info',message:e})},Ti.API.fatal=function(e){Ti._broker('API','log',{level:'fatal',message:e})},Ti.API.warn=function(e){Ti._broker('API','log',{level:'warn',message:e})},Ti.App.on=Ti.App.addEventListener=function(e,i){var n=Ti.App._listeners[e];'undefined'==typeof n&&(n=[],Ti.App._listeners[e]=n);var p=Ti.pageToken+Ti.App._listener_id++;n.push({callback:i,id:p}),Ti._broker('App','on',{name:e,id:p})},Ti.App.off=Ti.App.removeEventListener=function(e,i){var n=Ti.App._listeners[e];if(n)for(var p=0;p<n.length;p++){var r=n[p];if(r.callback==i){n.splice(p,1),Ti._broker('App','off',{name:e,id:r.id});break}}};";

  NSMutableString *html = [[[NSMutableString alloc] init] autorelease];
  NSString *ti = [NSString stringWithFormat:@"%@%s", @"Ti", "tanium"];
  [html appendFormat:@"window.%@={};window.Ti=%@;Ti.pageToken=%@;Ti.appId='%@';", ti, ti, pageToken, TI_APPLICATION_ID];
  [html appendString:kTitaniumRemoteJavascript];
  return html;
}

+ (NSString *)content:(NSString *)content withInjection:(NSString *)injection
{
  if ([content length] == 0) {
    return content;
  }
  // attempt to make well-formed HTML and inject in our Titanium bridge code
  // However, we only do this if the content looks like HTML
  NSRange range = [content rangeOfString:@"<html"];
  if (range.location == NSNotFound) {
    //TODO: Someone did a DOCTYPE, and our search wouldn't find it. This search is tailored for him
    //to cause the bug to go away, but is this really the right thing to do? Shouldn't we have a better
    //way to check?
    range = [content rangeOfString:@"<!DOCTYPE html"];
  }

  if (range.location != NSNotFound) {
    NSMutableString *html = [[NSMutableString alloc] initWithCapacity:[content length] + 2000];
    NSRange nextRange = [content rangeOfString:@">" options:0 range:NSMakeRange(range.location, [content length] - range.location) locale:nil];
    if (nextRange.location != NSNotFound) {
      [html appendString:[content substringToIndex:nextRange.location + 1]];
      [html appendString:injection];
      [html appendString:[content substringFromIndex:nextRange.location + 1]];
    } else {
      // oh well, just jack it in
      [html appendString:injection];
      [html appendString:content];
    }

    return [html autorelease];
  }
  return content;
}

- (void)loadHTML:(NSString *)content
            encoding:(NSStringEncoding)encoding
    textEncodingName:(NSString *)textEncodingName
            mimeType:(NSString *)mimeType
             baseURL:(NSURL *)baseURL
{
  if (baseURL == nil) {
    baseURL = [NSURL fileURLWithPath:[TiHost resourcePath]];
  }
  content = [[self class] content:content withInjection:[self _titaniumInjection]];

  [self ensureLocalProtocolHandler];
  [[self webview] loadData:[content dataUsingEncoding:encoding] MIMEType:mimeType textEncodingName:textEncodingName baseURL:baseURL];
  if (scalingOverride == NO) {
    [[self webview] setScalesPageToFit:NO];
  }
}

- (void)loadFile:(NSURL *)requestURL
            encoding:(NSStringEncoding)encoding
    textEncodingName:(NSString *)textEncodingName
            mimeType:(NSString *)mimeType
{
  NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:requestURL];
  [NSURLProtocol setProperty:textEncodingName forKey:kContentTextEncoding inRequest:request];
  [NSURLProtocol setProperty:mimeType forKey:kContentMimeType inRequest:request];

  [request setValue:[NSString stringWithFormat:@"%lu", (localId++)] forHTTPHeaderField:@"X-Titanium-Local-Id"];

  [self loadURLRequest:request];
  if (scalingOverride == NO) {
    [[self webview] setScalesPageToFit:NO];
  }
}

- (UIScrollView *)scrollview
{
  return [[self webview] scrollView];
}

#pragma mark Public APIs

- (id)url
{
  NSString *result = [[[webview request] URL] absoluteString];
  if (result != nil) {
    return result;
  }
  return url;
}

- (void)setAllowsLinkPreview_:(id)value
{
  if ([TiUtils isIOS9OrGreater] == NO) {
    return;
  }
  ENSURE_TYPE(value, NSNumber);
  [webview setAllowsLinkPreview:[TiUtils boolValue:value]];
}

- (void)reload
{
  RELEASE_TO_NIL(lastValidLoad);
  if (webview == nil) {
    return;
  }
  if (reloadData != nil) {
    [self performSelector:reloadMethod withObject:reloadData withObject:reloadDataProperties];
    return;
  }
  [webview reload];
}

- (void)stopLoading
{
  [webview stopLoading];
}

- (void)goBack
{
  [webview goBack];
}

- (void)goForward
{
  [webview goForward];
}

- (BOOL)loading
{
  return [webview isLoading];
}

- (BOOL)canGoBack
{
  return [webview canGoBack];
}

- (BOOL)canGoForward
{
  return [webview canGoForward];
}

- (void)clearCache
{
  [[NSURLCache sharedURLCache] removeAllCachedResponses];
}

- (void)clearHistory
{
  //not working for now
}

- (void)setIgnoreSslError_:(id)value
{
  ENSURE_TYPE(value, NSNumber);

  ignoreSslError = [TiUtils boolValue:value def:NO];
  isAuthenticated = NO;
  [[self proxy] replaceValue:value forKey:@"ignoreSslError" notification:NO];
}

- (void)setBackgroundColor_:(id)color
{
  UIColor *c = [Webcolor webColorNamed:color];
  [self setBackgroundColor:c];
  [[self webview] setBackgroundColor:c];
}

- (void)setAutoDetect_:(NSArray *)values
{
  UIDataDetectorTypes result = UIDataDetectorTypeNone;
  for (NSNumber *thisNumber in values) {
    result |= [TiUtils intValue:thisNumber];
  }
  [[self webview] setDataDetectorTypes:result];
}

- (void)setZoomLevel_:(id)value
{
  ENSURE_TYPE(value, NSNumber);

  [[self webview] stringByEvaluatingJavaScriptFromString:[NSString stringWithFormat:@"document.body.style.zoom = %@;", value]];
}

- (void)setHtml_:(NSString *)content withObject:(id)property
{
  NSString *baseURLString = [TiUtils stringValue:@"baseURL" properties:property];
  NSURL *baseURL = baseURLString == nil ? nil : [NSURL URLWithString:baseURLString];
  NSString *mimeType = [TiUtils stringValue:@"mimeType" properties:property def:kMimeTextHTML];
  ignoreNextRequest = YES;
  [self setReloadData:content];
  [self setReloadDataProperties:property];
  reloadMethod = @selector(setHtml_:withObject:);
  RELEASE_TO_NIL(lastValidLoad);
  [self loadHTML:content encoding:NSUTF8StringEncoding textEncodingName:@"utf-8" mimeType:mimeType baseURL:baseURL];
}

- (void)setData_:(id)args
{
  ignoreNextRequest = YES;
  [self setReloadData:args];
  [self setReloadDataProperties:nil];
  reloadMethod = @selector(setData_:);
  RELEASE_TO_NIL(url);
  RELEASE_TO_NIL(lastValidLoad);
  ENSURE_SINGLE_ARG(args, NSObject);

  [self stopLoading];

  if ([args isKindOfClass:[TiBlob class]]) {
    TiBlob *blob = (TiBlob *)args;
    TiBlobType type = [blob type];
    switch (type) {
    case TiBlobTypeData: {
      [self ensureLocalProtocolHandler];
      // Empty NSURL since nil is not accepted here
      NSURL *emptyURL = [[NSURL new] autorelease];
      [[self webview] loadData:[blob data] MIMEType:[blob mimeType] textEncodingName:@"utf-8" baseURL:emptyURL];
      if (scalingOverride == NO) {
        [[self webview] setScalesPageToFit:YES];
      }
      break;
    }
    case TiBlobTypeFile: {
      url = [[NSURL fileURLWithPath:[blob path]] retain];
      [self loadLocalURL];
      break;
    }
    default: {
      [self.proxy throwException:@"invalid blob type" subreason:[NSString stringWithFormat:@"expected either file or data blob, was: %d", type] location:CODELOCATION];
    }
    }
  } else if ([args isKindOfClass:[TiFile class]]) {
    TiFile *file = (TiFile *)args;
    url = [[NSURL fileURLWithPath:[file path]] retain];
    [self loadLocalURL];
  } else {
    [self.proxy throwException:@"invalid datatype" subreason:[NSString stringWithFormat:@"expected a TiBlob, was: %@", [args class]] location:CODELOCATION];
  }
}

- (void)setScalesPageToFit_:(id)args
{
  // allow the user to overwrite the scale (usually if local)
  BOOL scaling = [TiUtils boolValue:args];
  scalingOverride = YES;
  [[self webview] setScalesPageToFit:scaling];
}

- (void)setMultipleTouchEnabled_:(id)args
{
  BOOL value = [TiUtils boolValue:args];
  [[self webview] setMultipleTouchEnabled:value];
}

- (void)setAllowsInlineMediaPlayback_:(id)value
{
  BOOL result = [TiUtils boolValue:value def:YES];
  [[self webview] setAllowsInlineMediaPlayback:result];
}

- (void)setMediaPlaybackAllowsAirPlay_:(id)value
{
  BOOL result = [TiUtils boolValue:value def:YES];
  [[self webview] setMediaPlaybackAllowsAirPlay:result];
}

- (void)setMediaPlaybackRequiresUserAction_:(id)value
{
  BOOL result = [TiUtils boolValue:value def:YES];
  [[self webview] setMediaPlaybackRequiresUserAction:result];
}

- (void)setUrl_:(id)args
{
  ENSURE_SINGLE_ARG_OR_NIL(args, NSString);
  if (args == nil)
    return;
  ignoreNextRequest = YES;
  isAuthenticated = NO;
  [self setReloadData:args];
  [self setReloadDataProperties:nil];
  reloadMethod = @selector(setUrl_:);

  RELEASE_TO_NIL(url);
  RELEASE_TO_NIL(lastValidLoad);

  url = [[TiUtils toURL:args proxy:(TiProxy *)self.proxy] retain];
  NSArray<NSURLQueryItem *> *queryItems = [[NSURLComponents componentsWithURL:[NSURL URLWithString:args] resolvingAgainstBaseURL:NO] queryItems];
  if (queryItems) {

    NSURLComponents *components = [NSURLComponents componentsWithURL:url resolvingAgainstBaseURL:NO];
    [components setQueryItems:queryItems];
    [url release];
    url = [[components URL] retain];
  }

  if (insecureConnection) {
    [insecureConnection cancel];
  }

  [self stopLoading];

  if ([[self class] isLocalURL:url]) {
    [self loadLocalURL];
  } else {
    NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
    [self loadURLRequest:request];
    if (scalingOverride == NO) {
      [[self webview] setScalesPageToFit:YES];
    }
  }
}

- (void)setUsername_:(id)value
{
  RELEASE_TO_NIL(_currentRequest)
}

- (void)setPassword_:(id)value
{
  RELEASE_TO_NIL(_currentRequest)
}

- (void)setBlacklistedURLs_:(id)args
{
  ENSURE_TYPE(args, NSArray);

  if (blacklistedURLs) {
    RELEASE_TO_NIL(blacklistedURLs);
  }

  for (id blacklistedURL in args) {
    ENSURE_TYPE(blacklistedURL, NSString);
  }

  blacklistedURLs = [args copy];
}

- (void)setHandlePlatformUrl_:(id)arg
{
  willHandleUrl = [TiUtils boolValue:arg];
}

- (void)setAsyncLoad_:(id)arg
{
  _asyncLoad = [TiUtils boolValue:arg def:NO];
}

- (void)setUserAgent_:(id)value
{
  ENSURE_TYPE_OR_NIL(value, NSString);

  if (value == nil || [value isEqualToString:@""]) {
    value = [[NSUserDefaults standardUserDefaults] objectForKey:@"DefaultUserAgent"];
  }

  [[NSUserDefaults standardUserDefaults] registerDefaults:@{ @"UserAgent" : value }];
  [[self proxy] replaceValue:value forKey:@"userAgent" notification:NO];
}

- (void)ensureLocalProtocolHandler
{
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    [NSURLProtocol registerClass:[LocalProtocolHandler class]];
  });
}

- (void)loadLocalURL
{
  [self ensureLocalProtocolHandler];
  NSStringEncoding encoding = NSUTF8StringEncoding;
  NSString *path = [url path];
  NSString *mimeType = [Mimetypes mimeTypeForExtension:path];
  NSString *textEncodingName = @"utf-8";
  NSError *error = nil;
  NSURL *baseURL = [[url copy] autorelease];

  // first check to see if we're attempting to load a file from the
  // filesystem and if so, and it exists, use that
  if ([[NSFileManager defaultManager] fileExistsAtPath:path]) {
    // per the Apple docs on what to do when you don't know the encoding ahead of a
    // file read:
    // step 1: read and attempt to have system determine
    NSString *html = [NSString stringWithContentsOfFile:path usedEncoding:&encoding error:&error];
    if (html == nil && error != nil) {
      //step 2: if unknown encoding, try UTF-8
      error = nil;
      html = [NSString stringWithContentsOfFile:path encoding:NSUTF8StringEncoding error:&error];
      if (html == nil && error != nil) {
        //step 3: try an appropriate legacy encoding (if one) -- what's that? Latin-1?
        //at this point we're just going to fail
        //This is assuming, of course, that this just isn't a pdf or some other non-HTML file.
        if ([[path pathExtension] hasPrefix:@"htm"]) {
          DebugLog(@"[ERROR] Couldn't determine the proper encoding. Make sure this file: %@ is UTF-8 encoded.", [path lastPathComponent]);
        }
      } else {
        // if we get here, it succeeded using UTF8
        encoding = NSUTF8StringEncoding;
        textEncodingName = @"utf-8";
      }
    } else {
      error = nil;
      textEncodingName = HTMLTextEncodingNameForStringEncoding(encoding);
      if (textEncodingName == nil) {
        DebugLog(@"[WARN] Could not determine correct text encoding for content: %@.", url);
        textEncodingName = @"utf-8";
      }
    }
    if ((error != nil && [error code] == 261) || [mimeType isEqualToString:(NSString *)svgMimeType]) {
      //TODO: Shouldn't we be checking for an HTML mime type before trying to read? This is right now rather inefficient, but it
      //Gets the job done, with minimal reliance on extensions.
      // this is a different encoding than specified, just send it to the webview to load

      NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
      [self loadURLRequest:request];
      if (scalingOverride == NO) {
        [[self webview] setScalesPageToFit:YES];
      }
      return;
    } else if (error != nil) {
      DebugLog(@"[DEBUG] Cannot load file: %@. Error message was: %@", path, error);
      RELEASE_TO_NIL(url);
      return;
    }
    [self loadFile:baseURL encoding:encoding textEncodingName:textEncodingName mimeType:mimeType];
  } else {
    // convert it into a app:// relative path to load the resource
    // from our application
    url = [[self fileURLToAppURL:url] retain];
    NSData *data = [TiUtils loadAppResource:url];
    NSString *html = nil;
    if (data != nil) {
      html = [[[NSString alloc] initWithData:data encoding:encoding] autorelease];
    }
    if (html != nil) {
      //Because local HTML may rely on JS that's stored in the app: schema, we must kee the url in the app: format.
      [self loadHTML:html encoding:encoding textEncodingName:textEncodingName mimeType:mimeType baseURL:baseURL];
    } else {
      NSLog(@"[WARN] couldn't load URL: %@", url);
      RELEASE_TO_NIL(url);
    }
  }
}

- (void)request:(APSHTTPRequest *)request onUseAuthenticationChallenge:(NSURLAuthenticationChallenge *)challenge
{
  if ([challenge previousFailureCount] > 0) {
    [[challenge sender] cancelAuthenticationChallenge:challenge];
  }
}

- (void)request:(APSHTTPRequest *)request onRequestForAuthenticationChallenge:(NSURLAuthenticationChallenge *)challenge
{
  if ([self.proxy _hasListeners:@"authentication"]) {
    NSMutableDictionary *event = [self eventForUrl:[request url]];
    [self.proxy fireEvent:@"authentication" withObject:event];
  }
}
- (void)request:(APSHTTPRequest *)request onLoad:(APSHTTPResponse *)tiResponse
{
  [[self webview] loadRequest:request.request];
}

- (void)setBasicAuthentication:(NSArray *)args
{
  ENSURE_ARG_COUNT(args, 2);
  NSString *username = [args objectAtIndex:0];
  NSString *password = [args objectAtIndex:1];

  if (username == nil && password == nil) {
    RELEASE_TO_NIL(basicCredentials);
    return;
  }

  //	NSString *toEncode = [NSString stringWithFormat:@"%@:%@",username,password];
  NSString *authString = [TiUtils base64encode:[[NSString stringWithFormat:@"%@:%@", username, password] dataUsingEncoding:NSUTF8StringEncoding]];
  RELEASE_TO_NIL(basicCredentials);

  basicCredentials = [[NSString stringWithFormat:@"Basic %@", authString] retain];
    if (url != nil) {
      [self setUrl_:[NSArray arrayWithObject:[url absoluteString]]];
    }
  }

- (NSString *)stringByEvaluatingJavaScriptFromString:(NSString *)code
{
  return [[self webview] stringByEvaluatingJavaScriptFromString:code];
}

- (CGSize)contentSizeForSize:(CGSize)size
{
  CGRect oldBounds = [[self webview] bounds];
  BOOL oldVal = webview.scalesPageToFit;
  [webview setScalesPageToFit:NO];
  [webview setBounds:CGRectMake(0, 0, size.width, 1)];
  CGSize ret = [webview sizeThatFits:CGSizeMake(size.width, 1)];
  [webview setBounds:oldBounds];
  [webview setScalesPageToFit:oldVal];
  return ret;
}

- (void)hideSpinner
{
  if (spinner != nil) {
    [UIView beginAnimations:@"webspiny" context:nil];
    [UIView setAnimationDuration:0.3];
    [spinner removeFromSuperview];
    [UIView commitAnimations];
    [spinner autorelease];
    spinner = nil;
}
}

- (BOOL)shoudTryToAuth:(UIWebViewNavigationType)navigationType
{
  return navigationType == UIWebViewNavigationTypeOther && !basicCredentials && ([self.proxy valueForKey:@"username"] || [self.proxy valueForKey:@"password"] || [self.proxy valueForKey:@"needsAuth"]);
}

- (void)setKeyboardDisplayRequiresUserAction_:(id)value
{
  ENSURE_TYPE(value, NSNumber);
  [[self proxy] replaceValue:value forKey:@"keyboardDisplayRequiresUserAction" notification:NO];

  [[self webview] setKeyboardDisplayRequiresUserAction:[TiUtils boolValue:value def:YES]];
}

- (NSMutableDictionary *)eventForUrl:(NSURL *)theUrl
{
  if (!theUrl) {
    return nil;
  }
  NSMutableDictionary *event = [NSMutableDictionary dictionary];
  NSString *urlPath = [theUrl absoluteString];
  NSString *path = [[NSURL fileURLWithPath:[TiFileSystemHelper resourcesDirectory]] absoluteString];
  urlPath = [[[urlPath stringByReplacingOccurrencesOfString:@"file:///private/var" withString:@"file:///var"]
      stringByReplacingOccurrencesOfString:path
                                withString:@""]
      stringByReplacingOccurrencesOfString:@"%20"
                                withString:@" "];
  [event setObject:urlPath forKey:@"url"];
  return event;
}

#pragma mark WebView Delegate

- (BOOL)webView:(UIWebView *)webView shouldStartLoadWithRequest:(NSURLRequest *)request navigationType:(UIWebViewNavigationType)navigationType
{
  NSURL *newUrl = [request URL];

  if (blacklistedURLs && blacklistedURLs.count > 0) {
    NSString *urlAbsoluteString = [newUrl absoluteString];

    for (NSString *blackListedUrl in blacklistedURLs) {
      if ([urlAbsoluteString rangeOfString:blackListedUrl options:NSCaseInsensitiveSearch].location != NSNotFound) {
        if ([[self proxy] _hasListeners:@"blacklisturl"]) {
          [[self proxy] fireEvent:@"blacklisturl"
                       withObject:@{
                         @"url" : urlAbsoluteString,
                         @"message" : @"Webview did not load blacklisted url."
                       }];
        }

        [self hideSpinner];
        return NO;
      }
    }
  }

  if ([[newUrl scheme] isEqualToString:[AppProtocolHandler specialProtocolScheme]] && [[newUrl path] hasPrefix:@"/_TiA0_"]) {
    return ![AppProtocolHandler handleAppToTiRequest:newUrl];
  }

  BOOL isFragmentJump = NO;
  if (request.URL.fragment) {
    NSString *nonFragmentURL = [request.URL.absoluteString stringByReplacingOccurrencesOfString:[@"#" stringByAppendingString:request.URL.fragment] withString:@""];
    isFragmentJump = [nonFragmentURL isEqualToString:webView.request.URL.absoluteString];
  }

  BOOL isTopLevelNavigation = [request.mainDocumentURL isEqual:request.URL];

  NSString *scheme = [[newUrl scheme] lowercaseString];
  BOOL isHTTPOrLocalFile = [scheme hasPrefix:@"http"] || [scheme isEqualToString:@"ftp"]
      || [scheme isEqualToString:@"file"] || [scheme isEqualToString:@"app"];

  if ([self.proxy _hasListeners:@"beforeload"]) {
    NSMutableDictionary *event = [self eventForUrl:newUrl];
    if (event) {
      [event setObject:@(navigationType) forKey:@"navigationType"];
    }
    [self.proxy fireEvent:@"beforeload" withObject:event];
  }

  if (navigationType != UIWebViewNavigationTypeOther) {
    RELEASE_TO_NIL(lastValidLoad);
  }

  // Handle invalid SSL certificate
  if (ignoreSslError && !isAuthenticated) {
    RELEASE_TO_NIL(insecureConnection);
    isAuthenticated = NO;
    insecureConnection = [[NSURLConnection alloc] initWithRequest:request delegate:self];
    [insecureConnection start];

    return NO;
  }

  if (isHTTPOrLocalFile) {
    DebugLog(@"[DEBUG] New scheme: %@", request);
    BOOL valid = !ignoreNextRequest;

    if (!isFragmentJump && isTopLevelNavigation) {
      loadingurl = [newUrl retain];
    }
    if ([scheme hasPrefix:@"http"]) {
      //UIWebViewNavigationTypeOther means we are either in a META redirect
      //or it is a js request from within the page
      valid = valid && (navigationType != UIWebViewNavigationTypeOther);
    }
    if (valid) {
      [self setReloadData:[newUrl absoluteString]];
      [self setReloadDataProperties:nil];
      reloadMethod = @selector(setUrl_:);
    }
    if ([scheme isEqualToString:@"file"] || [scheme isEqualToString:@"app"]) {
      [LocalProtocolHandler setContentInjection:[self _titaniumInjection]];
    }

    if ([self shoudTryToAuth:navigationType] && !_currentRequest) {
      _currentRequest = [[APSHTTPRequest alloc] init];
      [_currentRequest setAuthRetryCount:3];
      [_currentRequest setRequestUsername:[TiUtils stringValue:[self.proxy valueForKey:@"username"]]];
      [_currentRequest setRequestPassword:[TiUtils stringValue:[self.proxy valueForKey:@"password"]]];
      [_currentRequest setUrl:newUrl];
      [_currentRequest setDelegate:self];
      [_currentRequest setMethod:@"GET"];
      [_currentRequest send];
      return NO;
    }
    return YES;
  }

  UIApplication *uiApp = [UIApplication sharedApplication];

  if ([uiApp canOpenURL:newUrl] && !willHandleUrl) {
    if ([TiUtils isIOS10OrGreater]) {
      [uiApp openURL:newUrl options:@{} completionHandler:nil];
    } else {
      [uiApp openURL:newUrl];
    }
    return NO;
  }

  //It's likely to fail, but that way we pass it on to error handling.
  return YES;
}

- (void)webViewDidStartLoad:(UIWebView *)webView
{
  if (!loadingurl) {
    return;
}
  //    if (alwaysInjectTi) {
  //        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 0.01 * NSEC_PER_SEC),
  //                       dispatch_get_main_queue(), ^
  //                       {
  //                           [webView stringByEvaluatingJavaScriptFromString:[self __titaniumRemoteInjection]];
  //                       });
  //    }

  if ([[self viewProxy] _hasListeners:@"startload" checkParent:NO]) {
    [self.proxy fireEvent:@"startload" withObject:[self eventForUrl:loadingurl] propagate:NO checkForListener:NO];
  }
}

- (void)webViewDidFinishLoad:(UIWebView *)webView
{
  [self hideSpinner];
  [url release];
  if (loadingurl) {
    if (alwaysInjectTi) {
      //            dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 0.01 * NSEC_PER_SEC),
      //                           dispatch_get_main_queue(), ^
      //                           {
      [webView stringByEvaluatingJavaScriptFromString:[self __titaniumRemoteInjection]];
      //                           });
    }
    RELEASE_TO_NIL(loadingurl)
  }
  url = [[[webview request] URL] retain];
  NSMutableDictionary *event = [self eventForUrl:url];
  NSString *urlAbs = [event objectForKey:@"url"];

    if (![urlAbs isEqualToString:lastValidLoad]) {
    [[self proxy] replaceValue:urlAbs forKey:[event objectForKey:@"url"] notification:NO];
    if ([[self viewProxy] _hasListeners:@"load" checkParent:NO]) {
      [self.proxy fireEvent:@"load" withObject:event propagate:NO checkForListener:NO];
      [lastValidLoad release];
      lastValidLoad = [urlAbs retain];
    }
  }

  if ([[self viewProxy] _hasListeners:@"afterload" checkParent:NO]) {
    [self.proxy fireEvent:@"afterload" withObject:event propagate:NO checkForListener:NO];
  }

  // Disable user selection and the attached callout
  BOOL disableSelection = [TiUtils boolValue:[[self proxy] valueForKey:@"disableContextMenu"] def:NO];
  if (disableSelection) {
    [webView stringByEvaluatingJavaScriptFromString:@"document.documentElement.style.webkitUserSelect='none';"];
    [webView stringByEvaluatingJavaScriptFromString:@"document.documentElement.style.webkitTouchCallout='none';"];
    [webView stringByEvaluatingJavaScriptFromString:@"window.getSelection().removeAllRanges();"];
  }

  [webView setNeedsDisplay];
  ignoreNextRequest = NO;
  TiUIWebViewProxy *ourProxy = (TiUIWebViewProxy *)[self proxy];
  [ourProxy webviewDidFinishLoad];
}

- (void)webView:(UIWebView *)webView didFailLoadWithError:(NSError *)error
{
  // Ignore "Frame Load Interrupted" errors. Seen after opening url-schemes that
  // are already handled by the `Ti.App.iOS.handleurl` event
  if (error.code == 102 && [error.domain isEqual:@"WebKitErrorDomain"])
    return;

  NSString *offendingUrl = [self url];

  if ([[error domain] isEqual:NSURLErrorDomain]) {
    offendingUrl = [[error userInfo] objectForKey:NSURLErrorFailingURLStringErrorKey];

    // this means the pending request has been cancelled and should be
    // safely squashed
    if ([error code] == NSURLErrorCancelled) {
      return;
    }
  }
  [self hideSpinner];

  NSLog(@"[ERROR] Error loading: %@, Error: %@", offendingUrl, error);

  if ([[self viewProxy] _hasListeners:@"error" checkParent:NO]) {
    NSString *message = [TiUtils messageFromError:error];
    NSMutableDictionary *event = [NSMutableDictionary dictionaryWithObject:message forKey:@"message"];

    // We combine some error codes into a single one which we share with Android.
    NSInteger rawErrorCode = [error code];
    NSInteger returnErrorCode = rawErrorCode;

    if (rawErrorCode == NSURLErrorUserCancelledAuthentication) {
      returnErrorCode = NSURLErrorUserAuthenticationRequired; // URL_ERROR_AUTHENTICATION
    } else if (rawErrorCode == NSURLErrorNoPermissionsToReadFile || rawErrorCode == NSURLErrorCannotCreateFile || rawErrorCode == NSURLErrorFileIsDirectory || rawErrorCode == NSURLErrorCannotCloseFile || rawErrorCode == NSURLErrorCannotWriteToFile || rawErrorCode == NSURLErrorCannotRemoveFile || rawErrorCode == NSURLErrorCannotMoveFile) {
      returnErrorCode = NSURLErrorCannotOpenFile; // URL_ERROR_FILE
    } else if (rawErrorCode == NSURLErrorDNSLookupFailed) {
      returnErrorCode = NSURLErrorCannotFindHost; // URL_ERROR_HOST_LOOKUP
    }

    [event setObject:[NSNumber numberWithInteger:returnErrorCode] forKey:@"errorCode"];
    [event setValuesForKeysWithDictionary:[self eventForUrl:webView.request.URL]];
    [self.proxy fireEvent:@"error" withObject:event propagate:NO reportSuccess:YES errorCode:returnErrorCode message:message checkForListener:NO];
  }
}

- (void)webViewProgress:(NJKWebViewProgress *)webViewProgress updateProgress:(float)progress
{
  if ([[self viewProxy] _hasListeners:@"loadprogress" checkParent:NO]) {
    NSMutableDictionary *event = [self eventForUrl:webview.request.URL];
    [event setObject:@(progress) forKey:@"progress"];
    [self.proxy fireEvent:@"loadprogress" withObject:event propagate:NO checkForListener:NO];
  }
}
#pragma mark NSURLConnection Delegates (used for the "ignoreSslError" property)

- (void)connection:(NSURLConnection *)connection didReceiveAuthenticationChallenge:(NSURLAuthenticationChallenge *)challenge;
{
  if ([challenge previousFailureCount] == 0) {
    isAuthenticated = YES;

    [[challenge sender] useCredential:[NSURLCredential credentialForTrust:[[challenge protectionSpace] serverTrust]]
           forAuthenticationChallenge:challenge];
  } else {
    [[challenge sender] cancelAuthenticationChallenge:challenge];
  }
}

- (void)connection:(NSURLConnection *)connection didReceiveResponse:(NSURLResponse *)response;
{
  isAuthenticated = YES;

  [webview loadRequest:[NSURLRequest requestWithURL:url]];
  [insecureConnection cancel];
}

- (BOOL)connection:(NSURLConnection *)connection canAuthenticateAgainstProtectionSpace:(NSURLProtectionSpace *)protectionSpace
{
  return [[protectionSpace authenticationMethod] isEqualToString:NSURLAuthenticationMethodServerTrust];
}

#pragma mark UIGestureRecognizer Delegates

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)otherGestureRecognizer;
{
  return !willHandleTouches;
}

#pragma mark TiEvaluator

- (void)evalFile:(NSString *)path
{
  NSURL *url_ = [path hasPrefix:@"file:"] ? [NSURL URLWithString:path] : [NSURL fileURLWithPath:path];

  if (![path hasPrefix:@"/"] && ![path hasPrefix:@"file:"]) {
    NSURL *root = [[[self proxy] _host] baseURL];
    url_ = [NSURL fileURLWithPath:[NSString stringWithFormat:@"%@/%@", root, path]];
  }

  NSString *code = [NSString stringWithContentsOfURL:url_ encoding:NSUTF8StringEncoding error:nil];
  [self stringByEvaluatingJavaScriptFromString:code];
}

- (void)fireEvent:(id)listener withObject:(id)obj remove:(BOOL)yn thisObject:(id)thisObject_
{
  // don't bother firing an app event to the webview if we don't have a webview yet created
  if (webview != nil) {
    NSDictionary *event = (NSDictionary *)obj;
    NSString *name = [event objectForKey:@"type"];
    NSString *js = [NSString stringWithFormat:@"Ti.App._dispatchEvent('%@',%@,%@);", name, listener, [TiUtils jsonStringify:event]];
    // Not waiting for JS execution since this can cause deadlock on main queue.
    [webview performSelectorOnMainThread:@selector(stringByEvaluatingJavaScriptFromString:)
                              withObject:js
                           waitUntilDone:NO];
  }
}

@end

@implementation LocalProtocolHandler
static NSString *_contentInjection = nil;

+ (void)setContentInjection:(NSString *)contentInjection
{
  if (_contentInjection != nil) {
    RELEASE_TO_NIL(_contentInjection);
  }
  _contentInjection = [contentInjection retain];
}

- (void)dealloc
{
  RELEASE_TO_NIL(_contentInjection);
  [super dealloc];
}

+ (BOOL)canInitWithRequest:(NSURLRequest *)request
{
  return [request.URL.scheme isEqualToString:@"file"];
}

+ (NSURLRequest *)canonicalRequestForRequest:(NSURLRequest *)request
{
  // TIMOB-25762: iOS 11.3 breaks NSURLProtocol properties, so we need to set it here instead of inside the webview
  [NSURLProtocol setProperty:_contentInjection forKey:@"kContentInjection" inRequest:(NSMutableURLRequest *)request];

  return request;
}

+ (BOOL)requestIsCacheEquivalent:(NSURLRequest *)a toRequest:(NSURLRequest *)b
{
  return NO;
}

- (void)startLoading
{
  id<NSURLProtocolClient> client = [self client];
  NSURLRequest *request = [self request];
  NSURL *url = [request URL];
  NSString *absolutePath = [url path];

  NSStringEncoding contentDataEncoding = [[[self class] propertyForKey:kContentDataEncoding inRequest:request] unsignedIntegerValue];
  if (contentDataEncoding == 0) {
    contentDataEncoding = NSUTF8StringEncoding;
  }
  NSString *contentTextEncoding = [[self class] propertyForKey:kContentTextEncoding inRequest:request];
  NSData *contentData = [[self class] propertyForKey:kContentData inRequest:request];
  if (contentData == nil) {
    contentData = [TiUtils loadAppResource:url];
    if (contentData == nil) {
      contentData = [NSData dataWithContentsOfFile:absolutePath];
      if (contentData == nil) {
        NSLog(@"[ERROR] Error loading %@", absolutePath);
        [client URLProtocol:self didFailWithError:[NSError errorWithDomain:NSURLErrorDomain code:NSURLErrorResourceUnavailable userInfo:nil]];
        [client URLProtocolDidFinishLoading:self];
        return;
      }
    }
  }
  NSString *contentMimeType = [[self class] propertyForKey:kContentMimeType inRequest:request];
  if (contentMimeType == nil) {
    contentMimeType = [Mimetypes mimeTypeForExtension:absolutePath];
  }
  NSString *contentInjection = [[self class] propertyForKey:kContentInjection inRequest:request];
  if ((contentInjection != nil) && [contentMimeType isEqualToString:kMimeTextHTML]) {
    NSString *content = [[NSString alloc] initWithData:contentData encoding:contentDataEncoding];
    contentData = [[TiUIWebView content:content withInjection:contentInjection] dataUsingEncoding:contentDataEncoding];
    [content release];
  }
  NSURLResponse *response = [[NSURLResponse alloc] initWithURL:url MIMEType:contentMimeType expectedContentLength:[contentData length] textEncodingName:contentTextEncoding];
  [client URLProtocol:self didReceiveResponse:response cacheStoragePolicy:NSURLCacheStorageNotAllowed];
  [client URLProtocol:self didLoadData:contentData];
  [client URLProtocolDidFinishLoading:self];
  [response release];
}

- (void)stopLoading
{
  // NO-OP
}

@end

#endif
