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

import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.js.builtins.math.MathBuiltins;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.JSRealm;
import com.oracle.truffle.js.runtime.Symbol;
import com.oracle.truffle.js.runtime.objects.JSAttributes;
import com.oracle.truffle.js.runtime.objects.JSObjectUtil;

/**
 * see MathBuiltins.
 */
public final class JSMath {

    public static final String CLASS_NAME = "Math";

    private JSMath() {
    }

    public static DynamicObject create(JSRealm realm) {
        JSContext ctx = realm.getContext();
        DynamicObject obj = JSUserObject.createInit(realm);
        JSObjectUtil.putDataProperty(ctx, obj, Symbol.SYMBOL_TO_STRING_TAG, CLASS_NAME, JSAttributes.configurableNotEnumerableNotWritable());

        JSObjectUtil.putDataProperty(ctx, obj, "E", Math.E);
        JSObjectUtil.putDataProperty(ctx, obj, "PI", Math.PI);
        JSObjectUtil.putDataProperty(ctx, obj, "LN10", 2.302585092994046);
        JSObjectUtil.putDataProperty(ctx, obj, "LN2", 0.6931471805599453);
        JSObjectUtil.putDataProperty(ctx, obj, "LOG2E", 1.4426950408889634);
        JSObjectUtil.putDataProperty(ctx, obj, "LOG10E", 0.4342944819032518);
        JSObjectUtil.putDataProperty(ctx, obj, "SQRT1_2", 0.7071067811865476);
        JSObjectUtil.putDataProperty(ctx, obj, "SQRT2", 1.4142135623730951);

        JSObjectUtil.putFunctionsFromContainer(realm, obj, MathBuiltins.BUILTINS);
        return obj;
    }
}
