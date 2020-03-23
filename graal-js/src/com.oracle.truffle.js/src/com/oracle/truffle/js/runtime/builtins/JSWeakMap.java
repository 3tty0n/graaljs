/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.runtime.builtins;

import java.util.Map;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.builtins.WeakMapPrototypeBuiltins;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSBasicObject;
import com.oracle.truffle.js.runtime.objects.JSObject;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;
import com.oracle.truffle.js.runtime.util.WeakMap;

public final class JSWeakMap extends JSBuiltinObject implements JSConstructorFactory.Default, PrototypeSupplier {

    public static final JSWeakMap INSTANCE = new JSWeakMap();

    public static final String CLASS_NAME = "WeakMap";
    public static final String PROTOTYPE_NAME = CLASS_NAME + ".prototype";

    public static class WeakMapImpl extends JSBasicObject {
        private final Map<DynamicObject, Object> weakHashMap;

        protected WeakMapImpl(JSRealm realm, JSObjectFactory factory, Map<DynamicObject, Object> weakHashMap) {
            super(realm, factory);
            this.weakHashMap = weakHashMap;
        }

        protected WeakMapImpl(Shape shape, Map<DynamicObject, Object> weakHashMap) {
            super(shape);
            this.weakHashMap = weakHashMap;
        }

        public Map<DynamicObject, Object> getWeakHashMap() {
            return weakHashMap;
        }

        public static WeakMapImpl create(JSRealm realm, JSObjectFactory factory, Map<DynamicObject, Object> weakHashMap) {
            return new WeakMapImpl(realm, factory, weakHashMap);
        }
    }

    private JSWeakMap() {
    }

    public static DynamicObject create(JSContext context) {
        WeakMap weakMap = new WeakMap();
        JSRealm realm = context.getRealm();
        DynamicObject obj = WeakMapImpl.create(realm, context.getWeakMapFactory(), weakMap);
        assert isJSWeakMap(obj);
        return context.trackAllocation(obj);
    }

    @SuppressWarnings("unchecked")
    public static Map<DynamicObject, Object> getInternalWeakMap(DynamicObject obj) {
        assert isJSWeakMap(obj);
        // return (Map<DynamicObject, Object>) WEAKMAP_PROPERTY.get(obj, isJSWeakMap(obj));
        return ((WeakMapImpl) obj).getWeakHashMap();
    }

    @Override
    public DynamicObject createPrototype(final JSRealm realm, DynamicObject ctor) {
        JSContext ctx = realm.getContext();
        DynamicObject prototype = JSObjectUtil.createOrdinaryPrototypeObject(realm);
        JSObjectUtil.putConstructorProperty(ctx, prototype, ctor);
        JSObjectUtil.putFunctionsFromContainer(realm, prototype, WeakMapPrototypeBuiltins.BUILTINS);
        JSObjectUtil.putDataProperty(ctx, prototype, Symbol.SYMBOL_TO_STRING_TAG, CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());
        return prototype;
    }

    @Override
    public Shape makeInitialShape(JSContext context, DynamicObject prototype) {
        Shape initialShape = JSObjectUtil.getProtoChildShape(prototype, JSWeakMap.INSTANCE, context);
        return initialShape;
    }

    public static JSConstructor createConstructor(JSRealm realm) {
        return INSTANCE.createConstructorAndPrototype(realm);
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getClassName(DynamicObject object) {
        return getClassName();
    }

    @Override
    @TruffleBoundary
    public String toDisplayStringImpl(DynamicObject obj, int depth, boolean allowSideEffects, JSContext context) {
        if (context.isOptionNashornCompatibilityMode()) {
            return "[" + getClassName() + "]";
        } else {
            return getClassName();
        }
    }

    public static boolean isJSWeakMap(Object obj) {
        return JSObject.isJSObject(obj) && isJSWeakMap((DynamicObject) obj);
    }

    public static boolean isJSWeakMap(DynamicObject obj) {
        return isInstance(obj, INSTANCE);
    }

    @Override
    public DynamicObject getIntrinsicDefaultProto(JSRealm realm) {
        return realm.getWeakMapPrototype();
    }
}
