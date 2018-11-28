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
package com.oracle.truffle.js.nodes.unary;

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.runtime.JSRuntime;
import com.oracle.truffle.js.runtime.truffleinterop.JSInteropNodeUtil;
import com.oracle.truffle.js.runtime.truffleinterop.JSInteropUtil;

/**
 * Represents abstract operation IsCallable.
 *
 * @see JSRuntime#isCallable(Object)
 */
@ImportStatic({JSInteropUtil.class})
public abstract class IsCallableNode extends JSUnaryNode {

    protected IsCallableNode(JavaScriptNode operand) {
        super(operand);
    }

    @Override
    public abstract boolean executeBoolean(VirtualFrame frame);

    public abstract boolean executeBoolean(Object operand);

    @SuppressWarnings("unused")
    @Specialization(guards = {"shape.check(function)", "isJSFunctionShape(shape)"}, limit = "1")
    protected static boolean doJSFunctionShape(DynamicObject function,
                    @Cached("function.getShape()") Shape shape) {
        return true;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = "isJSFunction(function)", replaces = "doJSFunctionShape")
    protected static boolean doJSFunction(DynamicObject function) {
        return true;
    }

    @Specialization(guards = "isJSProxy(proxy)")
    protected static boolean doJSProxy(DynamicObject proxy) {
        return JSRuntime.isCallableProxy(proxy);
    }

    @Specialization(guards = "isForeignObject(obj)")
    protected static boolean doTruffleObject(TruffleObject obj,
                    @Cached("createIsExecutable()") Node isExecutableNode) {
        return JSInteropNodeUtil.isExecutable(obj, isExecutableNode);
    }

    @SuppressWarnings("unused")
    @Specialization
    protected static boolean doCharSequence(CharSequence string) {
        return false;
    }

    @SuppressWarnings("unused")
    @Specialization(guards = {"!isJSFunction(other)", "!isJSProxy(other)"})
    protected static boolean doOther(Object other) {
        return false;
    }

    public static IsCallableNode create(JavaScriptNode operand) {
        return IsCallableNodeGen.create(operand);
    }

    public static IsCallableNode create() {
        return IsCallableNodeGen.create(null);
    }

    @Override
    protected JavaScriptNode copyUninitialized() {
        return IsCallableNode.create(cloneUninitialized(getOperand()));
    }
}
