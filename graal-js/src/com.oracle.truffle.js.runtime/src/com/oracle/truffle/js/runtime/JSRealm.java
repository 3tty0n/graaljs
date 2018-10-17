/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.js.runtime;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleContext;
import com.oracle.truffle.api.TruffleLanguage;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.runtime.array.TypedArray;
import com.oracle.truffle.js.runtime.array.TypedArrayFactory;
import com.oracle.truffle.js.runtime.builtins.Builtin;
import com.oracle.truffle.js.runtime.builtins.JSAdapter;
import com.oracle.truffle.js.runtime.builtins.JSArray;
import com.oracle.truffle.js.runtime.builtins.JSArrayBuffer;
import com.oracle.truffle.js.runtime.builtins.JSArrayBufferView;
import com.oracle.truffle.js.runtime.builtins.JSBigInt;
import com.oracle.truffle.js.runtime.builtins.JSBoolean;
import com.oracle.truffle.js.runtime.builtins.JSCollator;
import com.oracle.truffle.js.runtime.builtins.JSConstructor;
import com.oracle.truffle.js.runtime.builtins.JSDataView;
import com.oracle.truffle.js.runtime.builtins.JSDate;
import com.oracle.truffle.js.runtime.builtins.JSDateTimeFormat;
import com.oracle.truffle.js.runtime.builtins.JSError;
import com.oracle.truffle.js.runtime.builtins.JSFunction;
import com.oracle.truffle.js.runtime.builtins.JSFunctionData;
import com.oracle.truffle.js.runtime.builtins.JSGlobalObject;
import com.oracle.truffle.js.runtime.builtins.JSIntl;
import com.oracle.truffle.js.runtime.builtins.JSJavaWorkerBuiltin;
import com.oracle.truffle.js.runtime.builtins.JSMap;
import com.oracle.truffle.js.runtime.builtins.JSMath;
import com.oracle.truffle.js.runtime.builtins.JSNumber;
import com.oracle.truffle.js.runtime.builtins.JSNumberFormat;
import com.oracle.truffle.js.runtime.builtins.JSON;
import com.oracle.truffle.js.runtime.builtins.JSObjectFactory;
import com.oracle.truffle.js.runtime.builtins.JSObjectPrototype;
import com.oracle.truffle.js.runtime.builtins.JSPluralRules;
import com.oracle.truffle.js.runtime.builtins.JSPromise;
import com.oracle.truffle.js.runtime.builtins.JSProxy;
import com.oracle.truffle.js.runtime.builtins.JSRegExp;
import com.oracle.truffle.js.runtime.builtins.JSSIMD;
import com.oracle.truffle.js.runtime.builtins.JSSet;
import com.oracle.truffle.js.runtime.builtins.JSSharedArrayBuffer;
import com.oracle.truffle.js.runtime.builtins.JSString;
import com.oracle.truffle.js.runtime.builtins.JSSymbol;
import com.oracle.truffle.js.runtime.builtins.JSTest262;
import com.oracle.truffle.js.runtime.builtins.JSTestV8;
import com.oracle.truffle.js.runtime.builtins.JSUserObject;
import com.oracle.truffle.js.runtime.builtins.JSWeakMap;
import com.oracle.truffle.js.runtime.builtins.JSWeakSet;
import com.oracle.truffle.js.runtime.builtins.SIMDType;
import com.oracle.truffle.js.runtime.builtins.SIMDType.SIMDTypeFactory;
import com.oracle.truffle.js.runtime.interop.JavaImporter;
import com.oracle.truffle.js.runtime.interop.JavaPackage;
import com.oracle.truffle.js.runtime.objects.Accessor;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.objects.Undefined;
import com.oracle.truffle.js.runtime.util.PrintWriterWrapper;
import com.oracle.truffle.js.runtime.util.TRegexUtil;

/**
 * Container for JavaScript globals (i.e. an ECMAScript 6 Realm object).
 */
public class JSRealm {

    public static final String POLYGLOT_CLASS_NAME = "Polyglot";
    // used for non-public properties of Polyglot
    public static final String POLYGLOT_INTERNAL_CLASS_NAME = "PolyglotInternal";
    public static final String REFLECT_CLASS_NAME = "Reflect";
    public static final String SHARED_ARRAY_BUFFER_CLASS_NAME = "SharedArrayBuffer";
    public static final String ATOMICS_CLASS_NAME = "Atomics";
    public static final String REALM_BUILTIN_CLASS_NAME = "Realm";
    public static final String ARGUMENTS_NAME = "arguments";
    public static final String JAVA_CLASS_NAME = "Java";
    public static final String JAVA_CLASS_NAME_NASHORN_COMPAT = "JavaNashornCompat";
    private static final String JAVA_WORKER_PROPERTY_NAME = "Worker";
    public static final String PERFORMANCE_CLASS_NAME = "performance";
    public static final String DEBUG_CLASS_NAME = "Debug";

    private static final String ALT_GRAALVM_VERSION_PROPERTY = "graalvm.version";
    private static final String GRAALVM_VERSION_PROPERTY = "org.graalvm.version";
    private static final String GRAALVM_VERSION;

    static {
        // Copied from `Launcher`. See GR-6243.
        String version = System.getProperty(GRAALVM_VERSION_PROPERTY);
        String altVersion = System.getProperty(ALT_GRAALVM_VERSION_PROPERTY);
        GRAALVM_VERSION = version != null ? version : altVersion;
    }

    private final JSContext context;

    @CompilationFinal private DynamicObject globalObject;

    private final DynamicObject objectConstructor;
    private final DynamicObject objectPrototype;
    private final DynamicObject functionConstructor;
    private final DynamicObject functionPrototype;

    private final JSConstructor arrayConstructor;
    private final JSConstructor booleanConstructor;
    private final JSConstructor numberConstructor;
    private final JSConstructor bigIntConstructor;
    private final JSConstructor stringConstructor;
    private final JSConstructor regExpConstructor;
    private final JSConstructor collatorConstructor;
    private final JSConstructor numberFormatConstructor;
    private final JSConstructor pluralRulesConstructor;
    private final JSConstructor dateTimeFormatConstructor;
    private final JSConstructor dateConstructor;
    @CompilationFinal(dimensions = 1) private final JSConstructor[] errorConstructors;
    private final JSConstructor callSiteConstructor;

    private final Shape initialUserObjectShape;
    private final Shape lazyRegexArrayShape;
    private final JSObjectFactory.RealmData objectFactories;

    // ES6:
    private final JSConstructor symbolConstructor;
    private final JSConstructor mapConstructor;
    private final JSConstructor setConstructor;
    private final JSConstructor weakMapConstructor;
    private final JSConstructor weakSetConstructor;

    private final DynamicObject mathObject;
    private DynamicObject realmBuiltinObject;
    private Object evalFunctionObject;
    private Object applyFunctionObject;
    private Object callFunctionObject;
    private Object reflectApplyFunctionObject;
    private Object reflectConstructFunctionObject;

    private final JSConstructor arrayBufferConstructor;
    private final JSConstructor sharedArrayBufferConstructor;

    @CompilationFinal(dimensions = 1) private final JSConstructor[] typedArrayConstructors;
    private final JSConstructor dataViewConstructor;
    private final JSConstructor jsAdapterConstructor;
    private final JSConstructor javaImporterConstructor;
    private final JSConstructor proxyConstructor;

    private final DynamicObject iteratorPrototype;
    private final DynamicObject arrayIteratorPrototype;
    private final DynamicObject setIteratorPrototype;
    private final DynamicObject mapIteratorPrototype;
    private final DynamicObject stringIteratorPrototype;
    private final DynamicObject regExpStringIteratorPrototype;
    private final DynamicObject enumerateIteratorPrototype;

    @CompilationFinal(dimensions = 1) private final JSConstructor[] simdTypeConstructors;
    @CompilationFinal(dimensions = 1) private final JSObjectFactory[] simdTypeFactories;

    private final JSConstructor generatorFunctionConstructor;
    private final DynamicObject generatorObjectPrototype;

    private final JSConstructor asyncFunctionConstructor;

    private final DynamicObject asyncIteratorPrototype;
    private final DynamicObject asyncFromSyncIteratorPrototype;
    private final DynamicObject asyncGeneratorObjectPrototype;
    private final JSConstructor asyncGeneratorFunctionConstructor;

    private final DynamicObject throwerFunction;
    private final Accessor throwerAccessor;

    private final JSConstructor promiseConstructor;

    private DynamicObject javaPackageToPrimitiveFunction;

    private final JSConstructor javaInteropWorkerConstructor;

    private final DynamicObject arrayProtoValuesIterator;
    @CompilationFinal private DynamicObject typedArrayConstructor;
    @CompilationFinal private DynamicObject typedArrayPrototype;

    @CompilationFinal private DynamicObject simdTypeConstructor;
    @CompilationFinal private DynamicObject simdTypePrototype;

    private volatile Map<List<String>, DynamicObject> templateRegistry;

    private final DynamicObject globalScope;

    private TruffleLanguage.Env truffleLanguageEnv;

    /**
     * True while calling Error.prepareStackTrace via the stack property of an error object.
     */
    private boolean preparingStackTrace;

    /**
     * Slot for Realm-specific data of the embedder of the JS engine.
     */
    private Object embedderData;

    /** Support for RegExp.$1. */
    private TruffleObject regexResult;

    public static final long NANOSECONDS_PER_MILLISECOND = 1000000;
    private final SplittableRandom random = new SplittableRandom();
    private final long nanoToZeroTimeOffset = -System.nanoTime();
    private final long nanoToCurrentTimeOffset = System.currentTimeMillis() * NANOSECONDS_PER_MILLISECOND + nanoToZeroTimeOffset;
    private long lastFuzzyTime = Long.MIN_VALUE;

    private OutputStream outputStream;
    private OutputStream errorStream;
    private PrintWriterWrapper outputWriter;
    private PrintWriterWrapper errorWriter;

    @CompilationFinal private JSConsoleUtil consoleUtil;

    public JSRealm(JSContext context, TruffleLanguage.Env env) {
        this.context = context;
        this.truffleLanguageEnv = env; // can be null

        if (env != null && isChildRealm()) {
            context.noChildRealmsAssumption.invalidate("no child realms");
        }

        // need to build Function and Function.proto in a weird order to avoid circular dependencies
        this.objectPrototype = JSObjectPrototype.create(context);

        this.functionPrototype = JSFunction.createFunctionPrototype(this, objectPrototype);

        this.objectFactories = context.newObjectFactoryRealmData();

        this.throwerFunction = createThrowerFunction();
        this.throwerAccessor = new Accessor(throwerFunction, throwerFunction);

        if (context.isOptionAnnexB()) {
            putProtoAccessorProperty(this);
        }

        this.globalObject = JSGlobalObject.create(this, objectPrototype);
        this.globalScope = JSObject.createInit(context.getGlobalScopeShape());

        this.objectConstructor = createObjectConstructor(this, objectPrototype);
        JSObjectUtil.putDataProperty(context, this.objectPrototype, JSObject.CONSTRUCTOR, objectConstructor, JSAttributes.getDefaultNotEnumerable());
        JSObjectUtil.putFunctionsFromContainer(this, this.objectPrototype, JSUserObject.PROTOTYPE_NAME);
        this.functionConstructor = JSFunction.createFunctionConstructor(this);
        JSFunction.fillFunctionPrototype(this);

        this.initialUserObjectShape = JSObjectUtil.getProtoChildShape(this.objectPrototype, JSUserObject.INSTANCE, context);

        this.arrayConstructor = JSArray.createConstructor(this);
        this.lazyRegexArrayShape = JSRegExp.makeLazyRegexArrayShape(context, arrayConstructor.getPrototype());
        this.booleanConstructor = JSBoolean.createConstructor(this);
        this.numberConstructor = JSNumber.createConstructor(this);
        this.bigIntConstructor = JSBigInt.createConstructor(this);
        this.stringConstructor = JSString.createConstructor(this);
        this.regExpConstructor = JSRegExp.createConstructor(this);
        this.dateConstructor = JSDate.createConstructor(this);
        boolean es6 = JSTruffleOptions.MaxECMAScriptVersion >= 6;
        if (es6) {
            this.symbolConstructor = JSSymbol.createConstructor(this);
            this.mapConstructor = JSMap.createConstructor(this);
            this.setConstructor = JSSet.createConstructor(this);
            this.weakMapConstructor = JSWeakMap.createConstructor(this);
            this.weakSetConstructor = JSWeakSet.createConstructor(this);
            this.proxyConstructor = JSProxy.createConstructor(this);
            this.promiseConstructor = JSPromise.createConstructor(this);
        } else {
            this.symbolConstructor = null;
            this.mapConstructor = null;
            this.setConstructor = null;
            this.weakMapConstructor = null;
            this.weakSetConstructor = null;
            this.proxyConstructor = null;
            this.promiseConstructor = null;
        }

        this.errorConstructors = new JSConstructor[JSErrorType.values().length];
        initializeErrorConstructors();
        this.callSiteConstructor = JSError.createCallSiteConstructor(this);

        this.arrayBufferConstructor = JSArrayBuffer.createConstructor(this);
        this.typedArrayConstructors = new JSConstructor[TypedArray.factories().length];
        initializeTypedArrayConstructors();
        this.dataViewConstructor = JSDataView.createConstructor(this);

        if (JSTruffleOptions.SIMDJS) {
            this.simdTypeFactories = new JSObjectFactory[SIMDType.FACTORIES.length];
            this.simdTypeConstructors = new JSConstructor[SIMDType.FACTORIES.length];
            initializeSIMDTypeConstructors();
        } else {
            this.simdTypeFactories = null;
            this.simdTypeConstructors = null;
        }

        if (context.isOptionIntl402()) {
            this.collatorConstructor = JSCollator.createConstructor(this);
            this.numberFormatConstructor = JSNumberFormat.createConstructor(this);
            this.dateTimeFormatConstructor = JSDateTimeFormat.createConstructor(this);
            this.pluralRulesConstructor = JSPluralRules.createConstructor(this);
        } else {
            this.collatorConstructor = null;
            this.numberFormatConstructor = null;
            this.dateTimeFormatConstructor = null;
            this.pluralRulesConstructor = null;
        }

        boolean nashornCompat = context.isOptionNashornCompatibilityMode() || JSTruffleOptions.NashornCompatibilityMode;
        boolean nashornJavaInterop = isJavaInteropAvailable() && (context.isOptionNashornCompatibilityMode() || JSTruffleOptions.NashornJavaInterop);
        this.jsAdapterConstructor = nashornCompat ? JSAdapter.createConstructor(this) : null;
        this.javaImporterConstructor = nashornJavaInterop ? JavaImporter.createConstructor(this) : null;

        this.iteratorPrototype = createIteratorPrototype();
        this.arrayIteratorPrototype = es6 ? createArrayIteratorPrototype() : null;
        this.setIteratorPrototype = es6 ? createSetIteratorPrototype() : null;
        this.mapIteratorPrototype = es6 ? createMapIteratorPrototype() : null;
        this.stringIteratorPrototype = es6 ? createStringIteratorPrototype() : null;
        this.regExpStringIteratorPrototype = JSTruffleOptions.MaxECMAScriptVersion >= JSTruffleOptions.ECMAScript2019 ? createRegExpStringIteratorPrototype() : null;

        this.generatorFunctionConstructor = es6 ? JSFunction.createGeneratorFunctionConstructor(this) : null;
        this.generatorObjectPrototype = es6 ? (DynamicObject) generatorFunctionConstructor.getPrototype().get(JSObject.PROTOTYPE, null) : null;
        this.enumerateIteratorPrototype = JSFunction.createEnumerateIteratorPrototype(this);
        this.arrayProtoValuesIterator = (DynamicObject) getArrayConstructor().getPrototype().get(Symbol.SYMBOL_ITERATOR, Undefined.instance);

        if (context.isOptionSharedArrayBuffer()) {
            this.sharedArrayBufferConstructor = JSSharedArrayBuffer.createConstructor(this);
        } else {
            this.sharedArrayBufferConstructor = null;
        }

        this.mathObject = JSMath.create(this);

        boolean es8 = JSTruffleOptions.MaxECMAScriptVersion >= 8;
        this.asyncFunctionConstructor = es8 ? JSFunction.createAsyncFunctionConstructor(this) : null;

        boolean es9 = JSTruffleOptions.MaxECMAScriptVersion >= 9;
        this.asyncIteratorPrototype = es9 ? JSFunction.createAsyncIteratorPrototype(this) : null;
        this.asyncFromSyncIteratorPrototype = es9 ? JSFunction.createAsyncFromSyncIteratorPrototype(this) : null;
        this.asyncGeneratorFunctionConstructor = es9 ? JSFunction.createAsyncGeneratorFunctionConstructor(this) : null;
        this.asyncGeneratorObjectPrototype = es9 ? (DynamicObject) asyncGeneratorFunctionConstructor.getPrototype().get(JSObject.PROTOTYPE, null) : null;

        this.javaInteropWorkerConstructor = isJavaInteropAvailable() ? JSJavaWorkerBuiltin.createWorkerConstructor(this) : null;

        this.outputStream = System.out;
        this.errorStream = System.err;
        this.outputWriter = new PrintWriterWrapper(outputStream, true);
        this.errorWriter = new PrintWriterWrapper(errorStream, true);
    }

    private void initializeTypedArrayConstructors() {
        JSConstructor taConst = JSArrayBufferView.createTypedArrayConstructor(this);
        typedArrayConstructor = taConst.getFunctionObject();
        typedArrayPrototype = taConst.getPrototype();

        for (TypedArrayFactory factory : TypedArray.factories()) {
            JSConstructor constructor = JSArrayBufferView.createConstructor(this, factory, taConst);
            typedArrayConstructors[factory.getFactoryIndex()] = constructor;
        }
    }

    private void initializeSIMDTypeConstructors() {
        assert JSTruffleOptions.SIMDJS;
        JSConstructor taConst = JSSIMD.createSIMDTypeConstructor(this);
        simdTypeConstructor = taConst.getFunctionObject();
        simdTypePrototype = taConst.getPrototype();

        for (SIMDTypeFactory<? extends SIMDType> factory : SIMDType.FACTORIES) {
            JSConstructor constructor = JSSIMD.createConstructor(this, factory, taConst);
            simdTypeConstructors[factory.getFactoryIndex()] = constructor;
        }
    }

    private void initializeErrorConstructors() {
        for (JSErrorType type : JSErrorType.values()) {
            JSConstructor errorConstructor = JSError.createErrorConstructor(this, type);
            errorConstructors[type.ordinal()] = errorConstructor;
        }
    }

    public final JSContext getContext() {
        return context;
    }

    public final DynamicObject lookupFunction(String containerName, String methodName) {
        Builtin builtin = Objects.requireNonNull(context.getFunctionLookup().lookupBuiltinFunction(containerName, methodName));
        JSFunctionData functionData = builtin.createFunctionData(context);
        return JSFunction.create(this, functionData);
    }

    public static DynamicObject createObjectConstructor(JSRealm realm, DynamicObject objectPrototype) {
        JSContext context = realm.getContext();
        DynamicObject objectConstructor = realm.lookupFunction(JSConstructor.BUILTINS, JSUserObject.CLASS_NAME);
        JSObjectUtil.putConstructorPrototypeProperty(context, objectConstructor, objectPrototype);
        JSObjectUtil.putFunctionsFromContainer(realm, objectConstructor, JSUserObject.CLASS_NAME);
        return objectConstructor;
    }

    public final JSConstructor getErrorConstructor(JSErrorType type) {
        return errorConstructors[type.ordinal()];
    }

    public final DynamicObject getGlobalObject() {
        return globalObject;
    }

    public final void setGlobalObject(DynamicObject global) {
        this.globalObject = global;
    }

    public final DynamicObject getObjectConstructor() {
        return objectConstructor;
    }

    public final DynamicObject getObjectPrototype() {
        return objectPrototype;
    }

    public final DynamicObject getFunctionConstructor() {
        return functionConstructor;
    }

    public final DynamicObject getFunctionPrototype() {
        return functionPrototype;
    }

    public final JSConstructor getArrayConstructor() {
        return arrayConstructor;
    }

    public final JSConstructor getBooleanConstructor() {
        return booleanConstructor;
    }

    public final JSConstructor getNumberConstructor() {
        return numberConstructor;
    }

    public final JSConstructor getBigIntConstructor() {
        return bigIntConstructor;
    }

    public final JSConstructor getStringConstructor() {
        return stringConstructor;
    }

    public final JSConstructor getRegExpConstructor() {
        return regExpConstructor;
    }

    public final JSConstructor getCollatorConstructor() {
        return collatorConstructor;
    }

    public final JSConstructor getNumberFormatConstructor() {
        return numberFormatConstructor;
    }

    public final JSConstructor getPluralRulesConstructor() {
        return pluralRulesConstructor;
    }

    public final JSConstructor getDateTimeFormatConstructor() {
        return dateTimeFormatConstructor;
    }

    public final JSConstructor getDateConstructor() {
        return dateConstructor;
    }

    public final JSConstructor getSymbolConstructor() {
        return symbolConstructor;
    }

    public final JSConstructor getMapConstructor() {
        return mapConstructor;
    }

    public final JSConstructor getSetConstructor() {
        return setConstructor;
    }

    public final JSConstructor getWeakMapConstructor() {
        return weakMapConstructor;
    }

    public final JSConstructor getWeakSetConstructor() {
        return weakSetConstructor;
    }

    public final Shape getInitialUserObjectShape() {
        return initialUserObjectShape;
    }

    public final JSConstructor getArrayBufferConstructor() {
        return arrayBufferConstructor;
    }

    public JSConstructor getSharedArrayBufferConstructor() {
        assert context.isOptionSharedArrayBuffer();
        return sharedArrayBufferConstructor;
    }

    public final JSConstructor getArrayBufferViewConstructor(TypedArrayFactory factory) {
        return typedArrayConstructors[factory.getFactoryIndex()];
    }

    public final JSConstructor getDataViewConstructor() {
        return dataViewConstructor;
    }

    public final DynamicObject getTypedArrayConstructor() {
        return typedArrayConstructor;
    }

    public final DynamicObject getTypedArrayPrototype() {
        return typedArrayPrototype;
    }

    public final DynamicObject getRealmBuiltinObject() {
        return realmBuiltinObject;
    }

    public final JSConstructor getProxyConstructor() {
        return proxyConstructor;
    }

    public final JSConstructor getGeneratorFunctionConstructor() {
        return generatorFunctionConstructor;
    }

    public final JSConstructor getAsyncFunctionConstructor() {
        return asyncFunctionConstructor;
    }

    public final JSConstructor getAsyncGeneratorFunctionConstructor() {
        return asyncGeneratorFunctionConstructor;
    }

    public final DynamicObject getEnumerateIteratorPrototype() {
        return enumerateIteratorPrototype;
    }

    public final DynamicObject getGeneratorObjectPrototype() {
        return generatorObjectPrototype;
    }

    public final DynamicObject getAsyncGeneratorObjectPrototype() {
        return asyncGeneratorObjectPrototype;
    }

    public final JSConstructor getJavaImporterConstructor() {
        return javaImporterConstructor;
    }

    public final DynamicObject getJavaPackageToPrimitiveFunction() {
        if (javaPackageToPrimitiveFunction == null) {
            javaPackageToPrimitiveFunction = JavaPackage.createToPrimitiveFunction(this);
        }
        return javaPackageToPrimitiveFunction;
    }

    public final Map<List<String>, DynamicObject> getTemplateRegistry() {
        if (templateRegistry == null) {
            createTemplateRegistry();
        }
        return templateRegistry;
    }

    @TruffleBoundary
    private synchronized void createTemplateRegistry() {
        if (templateRegistry == null) {
            templateRegistry = new HashMap<>();
        }
    }

    public final Object getEvalFunctionObject() {
        return evalFunctionObject;
    }

    public final Object getApplyFunctionObject() {
        return applyFunctionObject;
    }

    public final Object getCallFunctionObject() {
        return callFunctionObject;
    }

    public final Object getReflectApplyFunctionObject() {
        return reflectApplyFunctionObject;
    }

    public final Object getReflectConstructFunctionObject() {
        return reflectConstructFunctionObject;
    }

    private static void putProtoAccessorProperty(final JSRealm realm) {
        JSContext context = realm.getContext();
        DynamicObject getProto = JSFunction.create(realm, context.protoGetterFunctionData);
        DynamicObject setProto = JSFunction.create(realm, context.protoSetterFunctionData);

        // ES6 draft annex, B.2.2 Additional Properties of the Object.prototype Object
        JSObjectUtil.putConstantAccessorProperty(context, realm.getObjectPrototype(), JSObject.PROTO, getProto, setProto);
    }

    public final DynamicObject getThrowerFunction() {
        return throwerFunction;
    }

    public final Accessor getThrowerAccessor() {
        assert throwerAccessor != null;
        return throwerAccessor;
    }

    public DynamicObject getIteratorPrototype() {
        return iteratorPrototype;
    }

    public DynamicObject getAsyncIteratorPrototype() {
        return asyncIteratorPrototype;
    }

    public DynamicObject getAsyncFromSyncIteratorPrototype() {
        return asyncFromSyncIteratorPrototype;
    }

    public DynamicObject getArrayIteratorPrototype() {
        return arrayIteratorPrototype;
    }

    public DynamicObject getSetIteratorPrototype() {
        return setIteratorPrototype;
    }

    public DynamicObject getMapIteratorPrototype() {
        return mapIteratorPrototype;
    }

    public DynamicObject getStringIteratorPrototype() {
        return stringIteratorPrototype;
    }

    public DynamicObject getRegExpStringIteratorPrototype() {
        return regExpStringIteratorPrototype;
    }

    /**
     * This function is used whenever a function is required that throws a TypeError. It is used by
     * some of the builtins that provide accessor functions that should not be called (e.g., as a
     * method of deprecation). In the specification, this is often referred to as
     * "[[ThrowTypeError]] function Object (13.2.3)".
     *
     */
    private DynamicObject createThrowerFunction() {
        DynamicObject thrower = JSFunction.create(this, context.throwerFunctionData);
        JSObject.preventExtensions(thrower);
        JSObject.setIntegrityLevel(thrower, true);
        return thrower;
    }

    public DynamicObject getPromiseConstructor() {
        return promiseConstructor.getFunctionObject();
    }

    public DynamicObject getPromisePrototype() {
        return promiseConstructor.getPrototype();
    }

    public final JSObjectFactory.RealmData getObjectFactories() {
        return objectFactories;
    }

    public void setupGlobals() {
        CompilerAsserts.neverPartOfCompilation("do not setup globals from compiled code");
        long time = JSTruffleOptions.ProfileTime ? System.nanoTime() : 0L;

        DynamicObject global = getGlobalObject();
        putGlobalProperty(global, JSUserObject.CLASS_NAME, getObjectConstructor());
        putGlobalProperty(global, JSFunction.CLASS_NAME, getFunctionConstructor());
        putGlobalProperty(global, JSArray.CLASS_NAME, getArrayConstructor().getFunctionObject());
        putGlobalProperty(global, JSString.CLASS_NAME, getStringConstructor().getFunctionObject());
        putGlobalProperty(global, JSDate.CLASS_NAME, getDateConstructor().getFunctionObject());
        putGlobalProperty(global, JSNumber.CLASS_NAME, getNumberConstructor().getFunctionObject());
        putGlobalProperty(global, JSBigInt.CLASS_NAME, getBigIntConstructor().getFunctionObject());
        putGlobalProperty(global, JSBoolean.CLASS_NAME, getBooleanConstructor().getFunctionObject());
        putGlobalProperty(global, JSRegExp.CLASS_NAME, getRegExpConstructor().getFunctionObject());
        putGlobalProperty(global, JSMath.CLASS_NAME, mathObject);
        putGlobalProperty(global, JSON.CLASS_NAME, JSON.create(this));

        if (context.isOptionIntl402()) {
            DynamicObject intlObject = JSIntl.create(this);
            DynamicObject collatorFn = getCollatorConstructor().getFunctionObject();
            DynamicObject numberFormatFn = getNumberFormatConstructor().getFunctionObject();
            DynamicObject dateTimeFormatFn = getDateTimeFormatConstructor().getFunctionObject();
            DynamicObject pluralRulesFn = getPluralRulesConstructor().getFunctionObject();
            JSObjectUtil.putDataProperty(context, intlObject, JSFunction.getName(collatorFn), collatorFn, JSAttributes.getDefaultNotEnumerable());
            JSObjectUtil.putDataProperty(context, intlObject, JSFunction.getName(numberFormatFn), numberFormatFn, JSAttributes.getDefaultNotEnumerable());
            JSObjectUtil.putDataProperty(context, intlObject, JSFunction.getName(dateTimeFormatFn), dateTimeFormatFn, JSAttributes.getDefaultNotEnumerable());
            JSObjectUtil.putDataProperty(context, intlObject, JSFunction.getName(pluralRulesFn), pluralRulesFn, JSAttributes.getDefaultNotEnumerable());
            putGlobalProperty(global, JSIntl.CLASS_NAME, intlObject);
        }

        JSObjectUtil.putDataProperty(context, global, JSRuntime.NAN_STRING, Double.NaN);
        JSObjectUtil.putDataProperty(context, global, JSRuntime.INFINITY_STRING, Double.POSITIVE_INFINITY);
        JSObjectUtil.putDataProperty(context, global, Undefined.NAME, Undefined.instance);

        JSObjectUtil.putFunctionsFromContainer(this, global, JSGlobalObject.CLASS_NAME);

        this.evalFunctionObject = JSObject.get(global, JSGlobalObject.EVAL_NAME);
        this.applyFunctionObject = JSObject.get(getFunctionPrototype(), "apply");
        this.callFunctionObject = JSObject.get(getFunctionPrototype(), "call");

        for (JSErrorType type : JSErrorType.values()) {
            putGlobalProperty(global, type.name(), getErrorConstructor(type).getFunctionObject());
        }

        putGlobalProperty(global, JSArrayBuffer.CLASS_NAME, getArrayBufferConstructor().getFunctionObject());
        for (TypedArrayFactory factory : TypedArray.factories()) {
            putGlobalProperty(global, factory.getName(), getArrayBufferViewConstructor(factory).getFunctionObject());
        }
        putGlobalProperty(global, JSDataView.CLASS_NAME, getDataViewConstructor().getFunctionObject());

        if (JSTruffleOptions.SIMDJS) {
            DynamicObject simdObject = JSObject.createInit(this, this.getObjectPrototype(), JSUserObject.INSTANCE);
            for (SIMDTypeFactory<? extends SIMDType> factory : SIMDType.FACTORIES) {
                JSObjectUtil.putDataProperty(context, simdObject, factory.getName(), getSIMDTypeConstructor(factory).getFunctionObject(), JSAttributes.getDefaultNotEnumerable());
            }
            putGlobalProperty(global, JSSIMD.SIMD_OBJECT_NAME, simdObject);
        }

        if (context.isOptionNashornCompatibilityMode()) {
            initGlobalNashornExtensions(global);
        }
        if (JSTruffleOptions.TruffleInterop) {
            setupPolyglot(global);
        }
        if (isJavaInteropAvailable()) {
            setupJavaInterop(global);
        }
        if (context.isOptionDebugBuiltin()) {
            putGlobalProperty(global, JSTruffleOptions.DebugPropertyName, createDebugObject());
        }
        if (JSTruffleOptions.Test262Mode) {
            putGlobalProperty(global, JSTest262.CLASS_NAME, JSTest262.create(this));
        }
        if (JSTruffleOptions.TestV8Mode) {
            putGlobalProperty(global, JSTestV8.CLASS_NAME, JSTestV8.create(this));
        }
        if (context.getEcmaScriptVersion() >= 6) {
            Object parseInt = JSObject.get(global, "parseInt");
            Object parseFloat = JSObject.get(global, "parseFloat");
            putGlobalProperty(getNumberConstructor().getFunctionObject(), "parseInt", parseInt);
            putGlobalProperty(getNumberConstructor().getFunctionObject(), "parseFloat", parseFloat);

            putGlobalProperty(global, JSMap.CLASS_NAME, getMapConstructor().getFunctionObject());
            putGlobalProperty(global, JSSet.CLASS_NAME, getSetConstructor().getFunctionObject());
            putGlobalProperty(global, JSWeakMap.CLASS_NAME, getWeakMapConstructor().getFunctionObject());
            putGlobalProperty(global, JSWeakSet.CLASS_NAME, getWeakSetConstructor().getFunctionObject());
            putGlobalProperty(global, JSSymbol.CLASS_NAME, getSymbolConstructor().getFunctionObject());
            setupPredefinedSymbols(getSymbolConstructor().getFunctionObject());

            DynamicObject reflectObject = createReflect();
            putGlobalProperty(global, REFLECT_CLASS_NAME, reflectObject);
            this.reflectApplyFunctionObject = JSObject.get(reflectObject, "apply");
            this.reflectConstructFunctionObject = JSObject.get(reflectObject, "construct");

            putGlobalProperty(global, JSProxy.CLASS_NAME, getProxyConstructor().getFunctionObject());
            putGlobalProperty(global, JSPromise.CLASS_NAME, getPromiseConstructor());
        }

        if (context.isOptionSharedArrayBuffer()) {
            putGlobalProperty(global, SHARED_ARRAY_BUFFER_CLASS_NAME, getSharedArrayBufferConstructor().getFunctionObject());
        }
        if (context.isOptionAtomics()) {
            putGlobalProperty(global, ATOMICS_CLASS_NAME, createAtomics());
        }
        if (getEnv() != null && JSContextOptions.GLOBAL_THIS.getValue(getEnv().getOptions()) && !context.isOptionV8CompatibilityMode()) {
            putGlobalProperty(global, "global", global);
        }
        if (JSTruffleOptions.GraalBuiltin) {
            putGraalObject(global);
        }
        if (JSTruffleOptions.Extensions) {
            putConsoleObject(global);
            putGlobalProperty(global, PERFORMANCE_CLASS_NAME, createPerformance());
        }
        if (JSTruffleOptions.ProfileTime) {
            System.out.println("SetupGlobals: " + (System.nanoTime() - time) / 1000000);
        }
    }

    private void initGlobalNashornExtensions(DynamicObject global) {
        assert getContext().isOptionNashornCompatibilityMode();
        putGlobalProperty(global, JSAdapter.CLASS_NAME, jsAdapterConstructor.getFunctionObject());
        DynamicObject parseToJSON = lookupFunction(JSGlobalObject.CLASS_NAME_NASHORN_EXTENSIONS, "parseToJSON");
        JSObjectUtil.putOrSetDataProperty(getContext(), global, "parseToJSON", parseToJSON, JSAttributes.getDefaultNotEnumerable());
    }

    private void initGlobalScriptingExtensions(DynamicObject global) {
        DynamicObject exec = lookupFunction(JSGlobalObject.CLASS_NAME_NASHORN_EXTENSIONS, "exec");
        JSObjectUtil.putOrSetDataProperty(getContext(), global, "$EXEC", exec, JSAttributes.getDefaultNotEnumerable());
    }

    private void putGraalObject(DynamicObject global) {
        DynamicObject graalObject = JSUserObject.createInit(this);
        int flags = JSAttributes.notConfigurableEnumerableNotWritable();
        JSObjectUtil.putDataProperty(context, graalObject, "language", AbstractJavaScriptLanguage.NAME, flags);
        JSObjectUtil.putDataProperty(context, graalObject, "versionJS", AbstractJavaScriptLanguage.VERSION_NUMBER, flags);
        if (GRAALVM_VERSION != null) {
            JSObjectUtil.putDataProperty(context, graalObject, "versionGraalVM", GRAALVM_VERSION, flags);
        }
        JSObjectUtil.putDataProperty(context, graalObject, "isGraalRuntime", JSFunction.create(this, isGraalRuntimeFunction(context)), flags);
        putGlobalProperty(global, "Graal", graalObject);
    }

    private static JSFunctionData isGraalRuntimeFunction(JSContext context) {
        return JSFunctionData.createCallOnly(context, Truffle.getRuntime().createCallTarget(new JavaScriptRootNode(context.getLanguage(), null, null) {
            @Override
            public Object execute(VirtualFrame frame) {
                return isGraalRuntime();
            }

            @TruffleBoundary
            private boolean isGraalRuntime() {
                return Truffle.getRuntime().getName().contains("Graal");
            }
        }), 0, "isGraalRuntime");
    }

    public JSConstructor getSIMDTypeConstructor(SIMDTypeFactory<? extends SIMDType> factory) {
        return simdTypeConstructors[factory.getFactoryIndex()];
    }

    public JSObjectFactory getSIMDTypeFactory(SIMDTypeFactory<? extends SIMDType> factory) {
        return simdTypeFactories[factory.getFactoryIndex()];
    }

    /**
     * Convenience method for defining global data properties with default attributes.
     */
    private void putGlobalProperty(DynamicObject global, String name, Object value) {
        JSObjectUtil.putDataProperty(context, global, name, value, JSAttributes.getDefaultNotEnumerable());
    }

    private static void setupPredefinedSymbols(DynamicObject symbolFunction) {
        putSymbolProperty(symbolFunction, "hasInstance", Symbol.SYMBOL_HAS_INSTANCE);
        putSymbolProperty(symbolFunction, "isConcatSpreadable", Symbol.SYMBOL_IS_CONCAT_SPREADABLE);
        putSymbolProperty(symbolFunction, "iterator", Symbol.SYMBOL_ITERATOR);
        putSymbolProperty(symbolFunction, "asyncIterator", Symbol.SYMBOL_ASYNC_ITERATOR);
        putSymbolProperty(symbolFunction, "match", Symbol.SYMBOL_MATCH);
        putSymbolProperty(symbolFunction, "matchAll", Symbol.SYMBOL_MATCH_ALL);
        putSymbolProperty(symbolFunction, "replace", Symbol.SYMBOL_REPLACE);
        putSymbolProperty(symbolFunction, "search", Symbol.SYMBOL_SEARCH);
        putSymbolProperty(symbolFunction, "species", Symbol.SYMBOL_SPECIES);
        putSymbolProperty(symbolFunction, "split", Symbol.SYMBOL_SPLIT);
        putSymbolProperty(symbolFunction, "toStringTag", Symbol.SYMBOL_TO_STRING_TAG);
        putSymbolProperty(symbolFunction, "toPrimitive", Symbol.SYMBOL_TO_PRIMITIVE);
        putSymbolProperty(symbolFunction, "unscopables", Symbol.SYMBOL_UNSCOPABLES);
    }

    private static void putSymbolProperty(DynamicObject symbolFunction, String name, Symbol symbol) {
        symbolFunction.define(name, symbol, JSAttributes.notConfigurableNotEnumerableNotWritable(), (s, v) -> s.allocator().constantLocation(v));
    }

    static boolean isJavaInteropAvailable() {
        return !JSTruffleOptions.SubstrateVM;
    }

    private void setupJavaInterop(DynamicObject global) {
        if (!isJavaInteropAvailable()) {
            return;
        }
        DynamicObject java = JSObject.createInit(this, this.getObjectPrototype(), JSUserObject.INSTANCE);
        JSObjectUtil.putDataProperty(context, java, Symbol.SYMBOL_TO_STRING_TAG, JAVA_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        JSObjectUtil.putFunctionsFromContainer(this, java, JAVA_CLASS_NAME);
        if (context.isOptionNashornCompatibilityMode() || JSTruffleOptions.NashornJavaInterop) {
            JSObjectUtil.putFunctionsFromContainer(this, java, JAVA_CLASS_NAME_NASHORN_COMPAT);
        }
        JSObjectUtil.putDataProperty(context, java, JAVA_WORKER_PROPERTY_NAME, getJavaInteropWorkerConstructor().getFunctionObject(), JSAttributes.getDefaultNotEnumerable());
        putGlobalProperty(global, JAVA_CLASS_NAME, java);

        if (getEnv() != null && getEnv().isHostLookupAllowed()) {
            if (JSContextOptions.JAVA_PACKAGE_GLOBALS.getValue(getEnv().getOptions())) {
                putGlobalProperty(global, "Packages", JavaPackage.createInit(this, ""));
                putGlobalProperty(global, "java", JavaPackage.createInit(this, "java"));
                putGlobalProperty(global, "javafx", JavaPackage.createInit(this, "javafx"));
                putGlobalProperty(global, "javax", JavaPackage.createInit(this, "javax"));
                putGlobalProperty(global, "com", JavaPackage.createInit(this, "com"));
                putGlobalProperty(global, "org", JavaPackage.createInit(this, "org"));
                putGlobalProperty(global, "edu", JavaPackage.createInit(this, "edu"));
            }

            if (context.isOptionNashornCompatibilityMode() || JSTruffleOptions.NashornJavaInterop) {
                putGlobalProperty(global, JavaImporter.CLASS_NAME, getJavaImporterConstructor().getFunctionObject());
            }
        }
    }

    private void setupPolyglot(DynamicObject global) {
        DynamicObject obj = JSObject.createInit(this, this.getObjectPrototype(), JSUserObject.INSTANCE);
        JSObjectUtil.putFunctionsFromContainer(this, obj, POLYGLOT_CLASS_NAME);
        if (getContext().isOptionDebugBuiltin()) {
            JSObjectUtil.putFunctionsFromContainer(this, obj, POLYGLOT_INTERNAL_CLASS_NAME);
        }
        putGlobalProperty(global, POLYGLOT_CLASS_NAME, obj);
        putGlobalProperty(global, "Interop", obj); // temporary workaround to fix gates
    }

    private void putConsoleObject(DynamicObject global) {
        DynamicObject console = JSUserObject.createInit(this);
        JSObjectUtil.putFunctionsFromContainer(this, console, "Console");
        putGlobalProperty(global, "console", console);
    }

    private DynamicObject createPerformance() {
        DynamicObject obj = JSUserObject.createInit(this);
        JSObjectUtil.putFunctionsFromContainer(this, obj, PERFORMANCE_CLASS_NAME);
        return obj;
    }

    /**
     * Creates the %IteratorPrototype% object as specified in ES6 25.1.2.
     */
    private DynamicObject createIteratorPrototype() {
        DynamicObject prototype = JSObject.createInit(this, this.getObjectPrototype(), JSUserObject.INSTANCE);
        JSObjectUtil.putDataProperty(context, prototype, Symbol.SYMBOL_ITERATOR, createIteratorPrototypeSymbolIteratorFunction(this), JSAttributes.getDefaultNotEnumerable());
        return prototype;
    }

    private static DynamicObject createIteratorPrototypeSymbolIteratorFunction(JSRealm realm) {
        return JSFunction.create(realm, JSFunctionData.createCallOnly(realm.getContext(), realm.getContext().getSpeciesGetterFunctionCallTarget(), 0, "[Symbol.iterator]"));
    }

    /**
     * Creates the %ArrayIteratorPrototype% object as specified in ES6 22.1.5.2.
     */
    private DynamicObject createArrayIteratorPrototype() {
        DynamicObject prototype = JSObject.createInit(this, this.iteratorPrototype, JSUserObject.INSTANCE);
        JSObjectUtil.putFunctionsFromContainer(this, prototype, JSArray.ITERATOR_PROTOTYPE_NAME);
        JSObjectUtil.putDataProperty(context, prototype, Symbol.SYMBOL_TO_STRING_TAG, JSArray.ITERATOR_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        return prototype;
    }

    /**
     * Creates the %SetIteratorPrototype% object.
     */
    private DynamicObject createSetIteratorPrototype() {
        DynamicObject prototype = JSObject.createInit(this, this.iteratorPrototype, JSUserObject.INSTANCE);
        JSObjectUtil.putFunctionsFromContainer(this, prototype, JSSet.ITERATOR_PROTOTYPE_NAME);
        JSObjectUtil.putDataProperty(context, prototype, Symbol.SYMBOL_TO_STRING_TAG, JSSet.ITERATOR_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        return prototype;
    }

    /**
     * Creates the %MapIteratorPrototype% object.
     */
    private DynamicObject createMapIteratorPrototype() {
        DynamicObject prototype = JSObject.createInit(this, this.iteratorPrototype, JSUserObject.INSTANCE);
        JSObjectUtil.putFunctionsFromContainer(this, prototype, JSMap.ITERATOR_PROTOTYPE_NAME);
        JSObjectUtil.putDataProperty(context, prototype, Symbol.SYMBOL_TO_STRING_TAG, JSMap.ITERATOR_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        return prototype;
    }

    /**
     * Creates the %StringIteratorPrototype% object.
     */
    private DynamicObject createStringIteratorPrototype() {
        DynamicObject prototype = JSObject.createInit(this, this.iteratorPrototype, JSUserObject.INSTANCE);
        JSObjectUtil.putFunctionsFromContainer(this, prototype, JSString.ITERATOR_PROTOTYPE_NAME);
        JSObjectUtil.putDataProperty(context, prototype, Symbol.SYMBOL_TO_STRING_TAG, JSString.ITERATOR_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        return prototype;
    }

    /**
     * Creates the %RegExpStringIteratorPrototype% object.
     */
    private DynamicObject createRegExpStringIteratorPrototype() {
        DynamicObject prototype = JSObject.createInit(this, this.iteratorPrototype, JSUserObject.INSTANCE);
        JSObjectUtil.putFunctionsFromContainer(this, prototype, JSString.REGEXP_ITERATOR_PROTOTYPE_NAME);
        JSObjectUtil.putDataProperty(context, prototype, Symbol.SYMBOL_TO_STRING_TAG, JSString.REGEXP_ITERATOR_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        return prototype;
    }

    public DynamicObject getArrayProtoValuesIterator() {
        return arrayProtoValuesIterator;
    }

    private DynamicObject createReflect() {
        DynamicObject obj = JSObject.createInit(this, this.getObjectPrototype(), JSUserObject.INSTANCE);
        JSObjectUtil.putDataProperty(context, obj, Symbol.SYMBOL_TO_STRING_TAG, REFLECT_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        JSObjectUtil.putFunctionsFromContainer(this, obj, REFLECT_CLASS_NAME);
        return obj;
    }

    private DynamicObject createAtomics() {
        DynamicObject obj = JSObject.createInit(this, this.getObjectPrototype(), JSUserObject.INSTANCE);
        JSObjectUtil.putDataProperty(context, obj, Symbol.SYMBOL_TO_STRING_TAG, ATOMICS_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        JSObjectUtil.putFunctionsFromContainer(this, obj, ATOMICS_CLASS_NAME);
        return obj;
    }

    public JSConstructor getCallSiteConstructor() {
        return callSiteConstructor;
    }

    public final DynamicObject getGlobalScope() {
        return globalScope;
    }

    /**
     * Adds several objects to the global object, in case scripting mode is enabled (for Nashorn
     * compatibility). This includes an {@code $OPTIONS} property that exposes several options to
     * the script, an {@code $ARG} array with arguments to the script, an {@code $ENV} object with
     * environment variables, and an {@code $EXEC} function to execute external code.
     */
    public void addScriptingObjects() {
        CompilerAsserts.neverPartOfCompilation();
        DynamicObject globalObj = getGlobalObject();

        // $OPTIONS
        String timezone = context.getLocalTimeZoneId().getId();
        DynamicObject timezoneObj = JSUserObject.create(context, this);
        JSObjectUtil.putDataProperty(context, timezoneObj, "ID", timezone, JSAttributes.configurableEnumerableWritable());

        DynamicObject optionsObj = JSUserObject.create(context, this);
        JSObjectUtil.putDataProperty(context, optionsObj, "_timezone", timezoneObj, JSAttributes.configurableEnumerableWritable());
        JSObjectUtil.putDataProperty(context, optionsObj, "_scripting", true, JSAttributes.configurableEnumerableWritable());
        JSObjectUtil.putDataProperty(context, optionsObj, "_compile_only", false, JSAttributes.configurableEnumerableWritable());

        JSObjectUtil.putOrSetDataProperty(context, globalObj, "$OPTIONS", optionsObj, JSAttributes.configurableNotEnumerableWritable());

        // $ARG
        DynamicObject argObj = JSArray.createConstant(context, getEnv().getApplicationArguments());
        JSObjectUtil.putOrSetDataProperty(context, globalObj, "$ARG", argObj, JSAttributes.configurableNotEnumerableWritable());

        // $ENV
        DynamicObject envObj = JSUserObject.create(context, this);
        Map<String, String> sysenv = System.getenv();
        for (Map.Entry<String, String> entry : sysenv.entrySet()) {
            JSObjectUtil.putDataProperty(context, envObj, entry.getKey(), entry.getValue(), JSAttributes.configurableEnumerableWritable());
        }
        JSObjectUtil.putOrSetDataProperty(context, globalObj, "$ENV", envObj, JSAttributes.configurableNotEnumerableWritable());

        // $EXEC
        initGlobalScriptingExtensions(globalObj);

        // $OUT, $ERR, $EXIT
        JSObjectUtil.putOrSetDataProperty(context, globalObj, "$EXIT", Undefined.instance, JSAttributes.getDefaultNotEnumerable());
        JSObjectUtil.putOrSetDataProperty(context, globalObj, "$OUT", Undefined.instance, JSAttributes.getDefaultNotEnumerable());
        JSObjectUtil.putOrSetDataProperty(context, globalObj, "$ERR", Undefined.instance, JSAttributes.getDefaultNotEnumerable());
    }

    public void setRealmBuiltinObject(DynamicObject realmBuiltinObject) {
        if (this.realmBuiltinObject == null && realmBuiltinObject != null) {
            this.realmBuiltinObject = realmBuiltinObject;
            putGlobalProperty(globalObject, "Realm", realmBuiltinObject);
        }
    }

    public void initRealmBuiltinObject() {
        if (context.getContextOptions().isV8RealmBuiltin()) {
            setRealmBuiltinObject(createRealmBuiltinObject());
        }
    }

    private DynamicObject createRealmBuiltinObject() {
        DynamicObject obj = JSUserObject.createInit(this);
        JSObjectUtil.putDataProperty(getContext(), obj, Symbol.SYMBOL_TO_STRING_TAG, REALM_BUILTIN_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        JSObjectUtil.putFunctionsFromContainer(this, obj, REALM_BUILTIN_CLASS_NAME);
        return obj;
    }

    private DynamicObject createDebugObject() {
        DynamicObject obj = JSUserObject.createInit(this);
        JSObjectUtil.putDataProperty(context, obj, Symbol.SYMBOL_TO_STRING_TAG, DEBUG_CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        JSObjectUtil.putFunctionsFromContainer(this, obj, DEBUG_CLASS_NAME);
        return obj;
    }

    public void setArguments(Object[] arguments) {
        JSObjectUtil.putOrSetDataProperty(context, getGlobalObject(), ARGUMENTS_NAME, JSArray.createConstant(context, arguments),
                        context.isOptionV8CompatibilityMode() ? JSAttributes.getDefault() : JSAttributes.getDefaultNotEnumerable());
    }

    public JSConstructor getJSAdapterConstructor() {
        return jsAdapterConstructor;
    }

    public JSConstructor getJavaInteropWorkerConstructor() {
        return javaInteropWorkerConstructor;
    }

    public TruffleLanguage.Env getEnv() {
        return truffleLanguageEnv;
    }

    public void patchTruffleLanguageEnv(TruffleLanguage.Env env) {
        CompilerAsserts.neverPartOfCompilation();
        Objects.requireNonNull(env, "New env cannot be null.");
        truffleLanguageEnv = env;
        context.setAllocationReporter(env);
        context.getContextOptions().setOptionValues(env.getOptions());
    }

    @TruffleBoundary
    public JSRealm createChildRealm() {
        TruffleContext nestedContext = getEnv().newContextBuilder().build();
        Object prev = nestedContext.enter();
        try {
            JSRealm childRealm = AbstractJavaScriptLanguage.getCurrentJSRealm();
            // "Realm" object is shared by all realms (V8 compatibility mode)
            childRealm.setRealmBuiltinObject(getRealmBuiltinObject());
            return childRealm;
        } finally {
            nestedContext.leave(prev);
        }
    }

    public boolean isPreparingStackTrace() {
        return preparingStackTrace;
    }

    public void setPreparingStackTrace(boolean preparingStackTrace) {
        this.preparingStackTrace = preparingStackTrace;
    }

    public TruffleContext getTruffleContext() {
        return getEnv().getContext();
    }

    public boolean isChildRealm() {
        return getTruffleContext().getParent() != null;
    }

    public final Object getEmbedderData() {
        return embedderData;
    }

    public final void setEmbedderData(Object embedderData) {
        this.embedderData = embedderData;
    }

    public TruffleObject getRegexResult() {
        assert context.isOptionRegexpStaticResult();
        if (regexResult == null) {
            regexResult = TRegexUtil.getTRegexEmptyResult();
        }
        return regexResult;
    }

    public void setRegexResult(TruffleObject regexResult) {
        assert context.isOptionRegexpStaticResult();
        assert TRegexUtil.readResultIsMatch(TRegexUtil.createReadNode(), regexResult);
        this.regexResult = regexResult;
    }

    public OptionValues getOptions() {
        return getEnv().getOptions();
    }

    public final PrintWriter getOutputWriter() {
        return outputWriter;
    }

    /**
     * Returns the stream used by {@link #getOutputWriter}, or null if the stream is not available.
     *
     * Do not write to the stream directly, always use the {@link #getOutputWriter writer} instead.
     * Use this method only to check if the current writer is already writing to the stream you want
     * to use, in which case you can avoid creating a new {@link PrintWriter}.
     */
    public final OutputStream getOutputStream() {
        return outputStream;
    }

    public final PrintWriter getErrorWriter() {
        return errorWriter;
    }

    /**
     * Returns the stream used by {@link #getErrorWriter}, or null if the stream is not available.
     *
     * Do not write to the stream directly, always use the {@link #getErrorWriter writer} instead.
     * Use this method only to check if the current writer is already writing to the stream you want
     * to use, in which case you can avoid creating a new {@link PrintWriter}.
     */
    public final OutputStream getErrorStream() {
        return errorStream;
    }

    public final void setOutputWriter(Writer writer, OutputStream stream) {
        if (writer instanceof PrintWriterWrapper) {
            this.outputWriter.setFrom((PrintWriterWrapper) writer);
        } else {
            if (stream != null) {
                this.outputWriter.setDelegate(stream);
            } else {
                this.outputWriter.setDelegate(writer);
            }
        }
        this.outputStream = stream;
    }

    public final void setErrorWriter(Writer writer, OutputStream stream) {
        if (writer instanceof PrintWriterWrapper) {
            this.errorWriter.setFrom((PrintWriterWrapper) writer);
        } else {
            if (stream != null) {
                this.errorWriter.setDelegate(stream);
            } else {
                this.errorWriter.setDelegate(writer);
            }
        }
        this.errorStream = stream;
    }

    public long nanoTime() {
        return nanoTime(nanoToZeroTimeOffset);
    }

    public long nanoTime(long offset) {
        long ns = System.nanoTime() + offset;
        long resolution = getContext().getTimerResolution();
        if (resolution > 0) {
            return (ns / resolution) * resolution;
        } else {
            // fuzzy time
            long fuzz = random.nextLong(NANOSECONDS_PER_MILLISECOND) + 1;
            ns = ns - ns % fuzz;
            long last = lastFuzzyTime;
            if (ns > last) {
                lastFuzzyTime = ns;
                return ns;
            } else {
                return last;
            }
        }
    }

    public long currentTimeMillis() {
        return nanoTime(nanoToCurrentTimeOffset) / NANOSECONDS_PER_MILLISECOND;
    }

    public Shape getLazyRegexArrayShape() {
        return lazyRegexArrayShape;
    }

    public JSConsoleUtil getConsoleUtil() {
        if (consoleUtil == null) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            consoleUtil = new JSConsoleUtil();
        }
        return consoleUtil;
    }

}
