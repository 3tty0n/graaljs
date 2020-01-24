/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.js.parser;

import java.util.ArrayList;
import java.util.List;

import com.oracle.js.parser.ir.FunctionNode;
import com.oracle.js.parser.ir.LexicalContext;
import com.oracle.js.parser.ir.Module;
import com.oracle.js.parser.ir.Module.ImportEntry;
import com.oracle.js.parser.ir.Scope;
import com.oracle.truffle.api.object.DynamicObject;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.js.lang.JavaScriptLanguage;
import com.oracle.truffle.js.nodes.JavaScriptNode;
import com.oracle.truffle.js.nodes.NodeFactory;
import com.oracle.truffle.js.nodes.ScriptNode;
import com.oracle.truffle.js.nodes.function.FunctionRootNode;
import com.oracle.truffle.js.nodes.function.JSFunctionExpressionNode;
import com.oracle.truffle.js.parser.env.Environment;
import com.oracle.truffle.js.parser.env.EvalEnvironment;
import com.oracle.truffle.js.runtime.Errors;
import com.oracle.truffle.js.runtime.JSContext;
import com.oracle.truffle.js.runtime.objects.ExportResolution;
import com.oracle.truffle.js.runtime.objects.JSModuleLoader;
import com.oracle.truffle.js.runtime.objects.JSModuleRecord;
import com.oracle.truffle.js.runtime.objects.ScriptOrModule;

public final class JavaScriptTranslator extends GraalJSTranslator {
    private final Module moduleNode;
    private ScriptOrModule scriptOrModule;

    private JavaScriptTranslator(LexicalContext lc, NodeFactory factory, JSContext context, Source source, Environment environment, boolean isParentStrict, Module moduleNode) {
        super(lc, factory, context, source, environment, isParentStrict);
        this.moduleNode = moduleNode;
    }

    private JavaScriptTranslator(NodeFactory factory, JSContext context, Source source, Environment environment, boolean isParentStrict, Module moduleNode) {
        this(new LexicalContext(), factory, context, source, environment, isParentStrict, moduleNode);
    }

    private JavaScriptTranslator(NodeFactory factory, JSContext context, Source source, Environment environment, boolean isParentStrict) {
        this(new LexicalContext(), factory, context, source, environment, isParentStrict, null);
    }

    public static ScriptNode translateScript(NodeFactory factory, JSContext context, Source source, boolean isParentStrict) {
        return translateScript(factory, context, null, source, isParentStrict, false, false, null);
    }

    public static ScriptNode translateEvalScript(NodeFactory factory, JSContext context, Source source, boolean isParentStrict, DirectEvalContext directEval) {
        Environment parentEnv = directEval == null ? null : directEval.env;
        EvalEnvironment env = new EvalEnvironment(parentEnv, factory, context, directEval != null);
        boolean evalInFunction = parentEnv != null && (parentEnv.function() == null || !parentEnv.function().isGlobal());
        return translateScript(factory, context, env, source, isParentStrict, true, evalInFunction, directEval);
    }

    public static ScriptNode translateInlineScript(NodeFactory factory, JSContext context, Environment env, Source source, boolean isParentStrict) {
        boolean evalInFunction = env.getParent() != null;
        return translateScript(factory, context, env, source, isParentStrict, true, evalInFunction, null);
    }

    private static ScriptNode translateScript(NodeFactory nodeFactory, JSContext context, Environment env, Source source, boolean isParentStrict,
                    boolean isEval, boolean evalInFunction, DirectEvalContext directEval) {
        Scope parentScope = directEval == null ? null : directEval.scope;
        FunctionNode parserFunctionNode = GraalJSParserHelper.parseScript(context, source, context.getParserOptions().putStrict(isParentStrict), isEval, evalInFunction, parentScope);
        Source src = applyExplicitSourceURL(source, parserFunctionNode);
        LexicalContext lc = new LexicalContext();
        if (directEval != null && directEval.enclosingClass != null) {
            lc.push(directEval.enclosingClass);
        }
        return new JavaScriptTranslator(lc, nodeFactory, context, src, env, isParentStrict, null).translateScript(parserFunctionNode);
    }

    private static Source applyExplicitSourceURL(Source source, FunctionNode parserFunctionNode) {
        String explicitURL = parserFunctionNode.getSource().getExplicitURL();
        if (explicitURL != null) {
            return Source.newBuilder(source).name(explicitURL).internal(source.isInternal() || explicitURL.startsWith(JavaScriptLanguage.INTERNAL_SOURCE_URL_PREFIX)).build();
        }
        return source;
    }

    public static ScriptNode translateFunction(NodeFactory factory, JSContext context, Environment env, Source source, boolean isParentStrict, com.oracle.js.parser.ir.FunctionNode rootNode) {
        return new JavaScriptTranslator(factory, context, source, env, isParentStrict).translateScript(rootNode);
    }

    public static JavaScriptNode translateExpression(NodeFactory factory, JSContext context, Environment env, Source source, boolean isParentStrict, com.oracle.js.parser.ir.Expression expression) {
        return new JavaScriptTranslator(factory, context, source, env, isParentStrict).translateExpression(expression);
    }

    public static JSModuleRecord translateModule(NodeFactory factory, JSContext context, Source source, JSModuleLoader moduleLoader) {
        FunctionNode parsed = GraalJSParserHelper.parseModule(context, source, context.getParserOptions().putStrict(true));
        JavaScriptTranslator translator = new JavaScriptTranslator(factory, context, source, null, true, parsed.getModule());
        JSModuleRecord moduleRecord = new JSModuleRecord(parsed.getModule(), context, moduleLoader, source, () -> translator.translateModule(parsed));
        translator.scriptOrModule = moduleRecord;
        return moduleRecord;
    }

    private JSModuleRecord translateModule(com.oracle.js.parser.ir.FunctionNode functionNode) {
        if (!functionNode.isModule()) {
            throw new IllegalArgumentException("root function node is not a module");
        }
        JSFunctionExpressionNode functionExpression = (JSFunctionExpressionNode) transformFunction(functionNode);
        FunctionRootNode functionRoot = functionExpression.getFunctionNode();
        JSModuleRecord moduleRecord = (JSModuleRecord) scriptOrModule;
        moduleRecord.setFunctionData(functionRoot.getFunctionData());
        moduleRecord.setFrameDescriptor(functionRoot.getFrameDescriptor());
        return moduleRecord;
    }

    @Override
    protected List<JavaScriptNode> setupModuleEnvironment(FunctionNode functionNode) {
        assert functionNode.isModule();
        final List<JavaScriptNode> declarations = new ArrayList<>();

        GraalJSEvaluator evaluator = (GraalJSEvaluator) context.getEvaluator();
        // Assert: all named exports from module are resolvable.
        for (ImportEntry importEntry : moduleNode.getImportEntries()) {
            JSModuleRecord importedModule = evaluator.hostResolveImportedModule(context, scriptOrModule, importEntry.getModuleRequest());
            String localName = importEntry.getLocalName();
            if (importEntry.getImportName().equals(Module.STAR_NAME)) {
                assert functionNode.getBody().getScope().hasSymbol(localName) && functionNode.getBody().getScope().getExistingSymbol(localName).hasBeenDeclared();
                // GetModuleNamespace(importedModule)
                DynamicObject namespace = evaluator.getModuleNamespace(importedModule);
                // envRec.CreateImmutableBinding(in.[[LocalName]], true).
                // Call envRec.InitializeBinding(in.[[LocalName]], namespace).
                declarations.add(factory.createLazyWriteFrameSlot(localName, factory.createConstant(namespace)));
            } else {
                assert functionNode.getBody().getScope().hasSymbol(localName) && functionNode.getBody().getScope().getExistingSymbol(localName).isImportBinding();
                // Let resolution be importedModule.ResolveExport(in.[[ImportName]], << >>, << >>).
                ExportResolution resolution = evaluator.resolveExport(importedModule, importEntry.getImportName());
                // If resolution is null or resolution is "ambiguous", throw SyntaxError.
                if (resolution.isNull() || resolution.isAmbiguous()) {
                    throw Errors.createSyntaxError("Could not resolve import entry");
                }
                // Call envRec.CreateImportBinding(in.[[LocalName]], resolution.[[module]],
                // resolution.[[bindingName]]).
                createImportBinding(localName, resolution.getModule(), resolution.getBindingName());
            }
        }

        return declarations;
    }

    @Override
    protected JavaScriptNode getActiveScriptOrModule() {
        if (scriptOrModule == null) {
            assert moduleNode == null;
            scriptOrModule = new ScriptOrModule(context, source);
        }
        return factory.createConstant(scriptOrModule);
    }

    @Override
    protected GraalJSTranslator newTranslator(Environment env, LexicalContext savedLC) {
        JavaScriptTranslator translator = new JavaScriptTranslator(savedLC.copy(), factory, context, source, env, false, moduleNode);
        translator.scriptOrModule = this.scriptOrModule;
        return translator;
    }
}
