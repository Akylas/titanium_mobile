/**
 * Appcelerator Titanium Mobile
 * Copyright (c) 2011-2018 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Apache Public License
 * Please see the LICENSE included with this distribution for details.
 */
#include <cstring>
#include <sstream>

#include <v8.h>

#include "V8Util.h"
#include "JNIUtil.h"
#include "JSException.h"
#include "AndroidUtil.h"
#include "TypeConverter.h"

namespace titanium {
using namespace v8;

#define TAG "V8Util"

Local<Value> V8Util::executeString(Isolate* isolate, Local<String> source, Local<Value> filename)
{
	EscapableHandleScope scope(isolate);
	TryCatch tryCatch(isolate);

	Local<Script> script = Script::Compile(source, filename.As<String>());
	if (script.IsEmpty()) {
		LOGF(TAG, "Script source is empty");
		reportException(isolate, tryCatch, true);
		return scope.Escape(Undefined(isolate));
	}

	Local<Value> result = script->Run();
	if (result.IsEmpty()) {
		LOGF(TAG, "Script result is empty");
		reportException(isolate, tryCatch, true);
		return scope.Escape(Undefined(isolate));
	}

	return scope.Escape(result);
}

Local<Value> V8Util::newInstanceFromConstructorTemplate(Persistent<FunctionTemplate>& t, const FunctionCallbackInfo<Value>& args)
{
	Isolate* isolate = args.GetIsolate();
	EscapableHandleScope scope(isolate);

	const int argc = args.Length();
	Local<Value>* argv = new Local<Value>[argc];
	for (int i = 0; i < argc; ++i) {
		argv[i] = args[i];
	}

	Local<Context> context = isolate->GetCurrentContext();

	TryCatch tryCatch(isolate);
	Local<Value> nativeObject;
	Local<Object> instance;
	MaybeLocal<Object> maybeInstance = t.Get(isolate)->GetFunction()->NewInstance(context, argc, argv);
	delete[] argv;
	if (!maybeInstance.ToLocal(&instance)) {
		V8Util::fatalException(isolate, tryCatch);
		return scope.Escape(Undefined(isolate));
	}
	return scope.Escape(instance);
}

#define EXC_TAG "V8Exception"

void V8Util::reportException(Isolate* isolate, TryCatch &tryCatch, bool showLine)
{
	HandleScope scope(isolate);
	Local<Context> context = isolate->GetCurrentContext();
	Local<Message> message = tryCatch.Message();

	if (showLine && !message.IsEmpty()) {
		String::Utf8Value filename(isolate, message->GetScriptResourceName());
		String::Utf8Value msg(isolate, message->Get());
		Maybe<int> linenum = message->GetLineNumber(context);
		LOGE(EXC_TAG, "Exception occurred at %s:%i: %s", *filename, linenum.FromMaybe(-1), *msg);
	}

	// Log the stack trace if we have one
	MaybeLocal<Value> maybeStackTrace = tryCatch.StackTrace(context);
	if (!maybeStackTrace.IsEmpty()) {
		Local<Value> stack = maybeStackTrace.ToLocalChecked();
		String::Utf8Value trace(isolate, stack);
		if (trace.length() > 0 && !stack->IsUndefined()) {
			LOGD(EXC_TAG, *trace);
			return;
		}
	}

	// no/empty stack trace, so if the exception is an object,
	// try to get the 'message' and 'name' properties
	Local<Value> exception = tryCatch.Exception();
	if (exception->IsObject()) {
		Local<Object> exceptionObj = exception.As<Object>();
		MaybeLocal<Value> message = exceptionObj->Get(context, NEW_SYMBOL(isolate, "message"));
		MaybeLocal<Value> name = exceptionObj->Get(context, NEW_SYMBOL(isolate, "name"));

		if (!message.IsEmpty() && !message.ToLocalChecked()->IsUndefined() && !name.IsEmpty() && !name.ToLocalChecked()->IsUndefined()) {
			String::Utf8Value nameValue(isolate, name.ToLocalChecked());
			String::Utf8Value messageValue(isolate, message.ToLocalChecked());
			LOGE(EXC_TAG, "%s: %s", *nameValue, *messageValue);
			return;
		}
	}

	// Fall back to logging exception as a string
	String::Utf8Value error(isolate, exception);
	LOGE(EXC_TAG, *error);
}

void V8Util::openJSErrorDialog(Isolate* isolate, TryCatch &tryCatch)
{
	JNIEnv *env = JNIUtil::getJNIEnv();
	if (!env) {
		return;
	}

	HandleScope scope(isolate);

	Local<Context> context = isolate->GetCurrentContext();
	Local<Message> message = tryCatch.Message();
	Local<Value> exception = tryCatch.Exception();

	Local<Value> jsStack;
	Local<Value> javaStack;

	// obtain javascript and java stack traces
	if (exception->IsObject()) {
		Local<Object> error = exception.As<Object>();
		jsStack = error->Get(context, STRING_NEW(isolate, "stack")).FromMaybe(Undefined(isolate).As<Value>());
		javaStack = error->Get(context, STRING_NEW(isolate, "nativeStack")).FromMaybe(Undefined(isolate).As<Value>());
	}

	// javascript stack trace not provided? obtain current javascript stack trace
	if (jsStack.IsEmpty() || jsStack->IsNullOrUndefined()) {
		Local<StackTrace> frames = message->GetStackTrace();
		if (frames.IsEmpty() || !frames->GetFrameCount()) {
			frames = StackTrace::CurrentStackTrace(isolate, MAX_STACK);
		}
		if (!frames.IsEmpty()) {
			std::string stackString = V8Util::stackTraceString(frames);
			if (!stackString.empty()) {
				jsStack = String::NewFromUtf8(isolate, stackString.c_str()).As<Value>();
			}
		}
	}

	jstring title = env->NewStringUTF("Runtime Error");
	jstring errorMessage = TypeConverter::jsValueToJavaString(isolate, env, message->Get());
	jstring resourceName = TypeConverter::jsValueToJavaString(isolate, env, message->GetScriptResourceName());
	jstring sourceLine = TypeConverter::jsValueToJavaString(isolate, env, message->GetSourceLine(context).FromMaybe(Null(isolate).As<Value>()));
	jstring jsStackString = TypeConverter::jsValueToJavaString(isolate, env, jsStack);
	jstring javaStackString = TypeConverter::jsValueToJavaString(isolate, env, javaStack);
	env->CallStaticVoidMethod(
		JNIUtil::krollRuntimeClass,
		JNIUtil::krollRuntimeDispatchExceptionMethod,
		title,
		errorMessage,
		resourceName,
		message->GetLineNumber(context).FromMaybe(-1),
		sourceLine,
        message->GetStartColumn(context).FromMaybe(-1),
		message->GetEndColumn(context).FromMaybe(-1),
		jsStackString,
		javaStackString);
	env->DeleteLocalRef(title);
	env->DeleteLocalRef(errorMessage);
	env->DeleteLocalRef(resourceName);
	env->DeleteLocalRef(sourceLine);
	env->DeleteLocalRef(jsStackString);
	env->DeleteLocalRef(javaStackString);
}

static int uncaughtExceptionCounter = 0;

void V8Util::fatalException(Isolate* isolate, TryCatch &tryCatch)
{
	HandleScope scope(isolate);

	// Check if uncaught_exception_counter indicates a recursion
	if (uncaughtExceptionCounter > 0) {
		reportException(isolate, tryCatch, true);
		LOGF(TAG, "Double exception fault");
	}
	reportException(isolate, tryCatch, true);
}

Local<String> V8Util::jsonStringify(Isolate* isolate, Local<Value> value)
{
	EscapableHandleScope scope(isolate);
	Local<Context> context = isolate->GetCurrentContext();

	TryCatch tryCatch(isolate);

	MaybeLocal<Value> jsonGlobal = context->Global()->Get(context, STRING_NEW(isolate, "JSON"));
	if (jsonGlobal.IsEmpty()) {
		LOGE(TAG, "!!!! JSON global not found/accessible !!!");
		return scope.Escape(STRING_NEW(isolate, "ERROR"));
	}

	Local<Object> jsonObject = jsonGlobal.ToLocalChecked().As<Object>();
	MaybeLocal<Value> stringifyValue = jsonObject->Get(context, STRING_NEW(isolate, "stringify"));
	if (stringifyValue.IsEmpty()) {
		LOGE(TAG, "!!!! JSON.stringifyValue not found/accessible !!!");
		return scope.Escape(STRING_NEW(isolate, "ERROR"));
	}

	Local<Function> stringify = stringifyValue.ToLocalChecked().As<Function>();
	Local<Value> args[] = { value };
	MaybeLocal<Value> result = stringify->Call(context, jsonObject, 1, args);
	if (result.IsEmpty()) {
		LOGE(TAG, "!!!! JSON.stringify() result is null/undefined.!!!");
		return scope.Escape(STRING_NEW(isolate, "ERROR"));
	}

	return scope.Escape(result.ToLocalChecked().As<String>());
}

bool V8Util::constructorNameMatches(Isolate* isolate, Local<Object> object, const char* name)
{
	HandleScope scope(isolate);
	Local<String> constructorName = object->GetConstructorName();
	return strcmp(*String::Utf8Value(isolate, constructorName), name) == 0;
}

static Persistent<Function> isNaNFunction;

bool V8Util::isNaN(Isolate* isolate, Local<Value> value)
{
	HandleScope scope(isolate);
	Local<Context> context = isolate->GetCurrentContext();
	Local<Object> global = context->Global();

	if (isNaNFunction.IsEmpty()) {
		MaybeLocal<Value> isNaNValue = global->Get(context, NEW_SYMBOL(isolate, "isNaN"));
		if (isNaNValue.IsEmpty()) {
			LOGE(TAG, "!!!! global isNaN function not found/inaccessible. !!!");
			return false;
		}
		isNaNFunction.Reset(isolate, isNaNValue.ToLocalChecked().As<Function>());
	}

	Local<Value> args[] = { value };
	MaybeLocal<Value> result = isNaNFunction.Get(isolate)->Call(context, global, 1, args);
	return result.FromMaybe(False(isolate).As<Value>())->BooleanValue();
}

void V8Util::dispose()
{
	isNaNFunction.Reset();
}

std::string V8Util::stackTraceString(Local<StackTrace> frames) {
	if (frames.IsEmpty()) {
		return std::string();
	}

	std::stringstream stack;

	for (int i = 0, count = frames->GetFrameCount(); i < count; i++) {
		v8::Local<v8::StackFrame> frame = frames->GetFrame(i);

		v8::String::Utf8Value jsFunctionName(frame->GetFunctionName());
		std::string functionName = std::string(*jsFunctionName, jsFunctionName.length());

		v8::String::Utf8Value jsScriptName(frame->GetScriptName());
		std::string scriptName = std::string(*jsScriptName, jsScriptName.length());

		stack << "    at " << functionName << "(" << scriptName << ":" << frame->GetLineNumber() << ":" << frame->GetColumn() << ")" << std::endl;
	}

	return stack.str();
}

}
