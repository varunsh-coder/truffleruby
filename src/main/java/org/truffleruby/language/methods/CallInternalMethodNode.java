/*
 * Copyright (c) 2015, 2021 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language.methods;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.MaterializedFrame;
import com.oracle.truffle.api.nodes.EncapsulatingNodeReference;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeUtil;
import com.oracle.truffle.api.profiles.BranchProfile;
import org.truffleruby.RubyContext;
import org.truffleruby.builtins.CoreMethodNodeManager;
import org.truffleruby.core.inlined.AlwaysInlinedMethodNode;
import org.truffleruby.language.RubyBaseNode;

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.ReportPolymorphism;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import org.truffleruby.language.RubyCheckArityRootNode;
import org.truffleruby.language.RubyNode;
import org.truffleruby.language.arguments.RubyArguments;
import org.truffleruby.language.control.RaiseException;
import org.truffleruby.language.dispatch.DispatchNode;
import org.truffleruby.language.dispatch.LiteralCallNode;

@ReportPolymorphism
@GenerateUncached
@ImportStatic(RubyArguments.class)
public abstract class CallInternalMethodNode extends RubyBaseNode {

    public static CallInternalMethodNode create() {
        return CallInternalMethodNodeGen.create();
    }

    /** Callers should use {@link RubyArguments#assertFrameArguments} unless they use {@code RubyArguments#pack}.
     * {@code literalCallNode} is only non-null if this was called splatted with a ruby2_keyword Hash. */
    public abstract Object execute(Frame frame, InternalMethod method, Object receiver, Object[] rubyArgs,
            LiteralCallNode literalCallNode);

    @Specialization(
            guards = {
                    "isSingleContext()",
                    "method.getCallTarget() == cachedCallTarget",
                    "!cachedMethod.alwaysInlined()" },
            assumptions = "getMethodAssumption(cachedMethod)", // to remove the inline cache entry when the method is redefined or removed
            limit = "getCacheLimit()")
    protected Object callCached(
            InternalMethod method, Object receiver, Object[] rubyArgs, LiteralCallNode literalCallNode,
            @Cached("method.getCallTarget()") RootCallTarget cachedCallTarget,
            @Cached("method") InternalMethod cachedMethod,
            @Cached("createCall(cachedCallTarget)") DirectCallNode callNode) {
        if (literalCallNode != null) {
            literalCallNode.copyRuby2KeywordsHash(rubyArgs, cachedMethod.getSharedMethodInfo());
        }

        return callNode.call(RubyArguments.repackForCall(rubyArgs));
    }

    @Specialization(
            guards = {
                    "!isSingleContext()",
                    "method.getCallTarget() == cachedCallTarget",
                    "cachedMethodNotAlwaysInlined" },
            limit = "getCacheLimit()")
    protected Object callCachedMultiContext(
            InternalMethod method, Object receiver, Object[] rubyArgs, LiteralCallNode literalCallNode,
            @Cached("method.getCallTarget()") RootCallTarget cachedCallTarget,
            @Cached("!method.alwaysInlined()") boolean cachedMethodNotAlwaysInlined,
            @Cached("method.getSharedMethodInfo()") SharedMethodInfo cachedSharedMethodInfo,
            @Cached("createCall(cachedCallTarget)") DirectCallNode callNode) {
        if (literalCallNode != null) {
            literalCallNode.copyRuby2KeywordsHash(rubyArgs, cachedSharedMethodInfo);
        }

        return callNode.call(RubyArguments.repackForCall(rubyArgs));
    }

    @Specialization(guards = "!method.alwaysInlined()", replaces = { "callCached", "callCachedMultiContext" })
    protected Object callUncached(
            InternalMethod method, Object receiver, Object[] rubyArgs, LiteralCallNode literalCallNode,
            @Cached IndirectCallNode indirectCallNode) {
        if (literalCallNode != null) {
            literalCallNode.copyRuby2KeywordsHash(rubyArgs, method.getSharedMethodInfo());
        }

        return indirectCallNode.call(method.getCallTarget(), RubyArguments.repackForCall(rubyArgs));
    }

    @Specialization(
            guards = {
                    "isSingleContext()",
                    "method.getCallTarget() == cachedCallTarget",
                    "cachedMethod.alwaysInlined()" },
            assumptions = "getMethodAssumption(cachedMethod)", // to remove the inline cache entry when the method is redefined or removed
            limit = "getCacheLimit()")
    protected Object alwaysInlined(
            Frame frame, InternalMethod method, Object receiver, Object[] rubyArgs, LiteralCallNode literalCallNode,
            @Cached(value = "method.getCallTarget()") RootCallTarget cachedCallTarget,
            @Cached("method") InternalMethod cachedMethod,
            @Cached("createAlwaysInlinedMethodNode(cachedMethod)") AlwaysInlinedMethodNode alwaysInlinedNode,
            @Cached(value = "cachedMethod.getSharedMethodInfo().getArity()") Arity cachedArity,
            @Cached BranchProfile checkArityProfile,
            @Cached BranchProfile exceptionProfile) {
        assert !cachedArity
                .acceptsKeywords() : "AlwaysInlinedMethodNodes are currently assumed to not use keyword arguments, the arity check depends on this";
        assert RubyArguments.getSelf(rubyArgs) == receiver;

        if (literalCallNode != null) {
            literalCallNode.copyRuby2KeywordsHash(rubyArgs, cachedMethod.getSharedMethodInfo());
        }

        try {
            int given = RubyArguments.getPositionalArgumentsCount(rubyArgs, false);
            if (!cachedArity.check(given)) {
                checkArityProfile.enter();
                RubyCheckArityRootNode.checkArityError(cachedArity, given, alwaysInlinedNode);
            }

            return alwaysInlinedNode.execute(frame, receiver, RubyArguments.repackForCall(rubyArgs), cachedCallTarget);
        } catch (RaiseException e) {
            exceptionProfile.enter();
            final Node location = e.getLocation();
            if (location != null && location.getRootNode() == alwaysInlinedNode.getRootNode()) {
                // if the error originates from the inlined node, rethrow it through the CallTarget to get a proper backtrace
                return RubyContext.indirectCallWithCallNode(this, cachedCallTarget, e);
            } else {
                throw e;
            }
        }
    }

    @Specialization(guards = "method.alwaysInlined()", replaces = "alwaysInlined")
    protected Object alwaysInlinedUncached(
            Frame frame, InternalMethod method, Object receiver, Object[] rubyArgs, LiteralCallNode literalCallNode) {
        return alwaysInlinedBoundary(
                frame == null ? null : frame.materialize(),
                method,
                receiver,
                rubyArgs,
                literalCallNode,
                isAdoptable());
    }

    @TruffleBoundary // getUncachedAlwaysInlinedMethodNode(method) and arity are not PE constants
    private Object alwaysInlinedBoundary(
            MaterializedFrame frame, InternalMethod method, Object receiver, Object[] rubyArgs,
            LiteralCallNode literalCallNode, boolean cachedToUncached) {
        EncapsulatingNodeReference encapsulating = null;
        Node prev = null;
        if (cachedToUncached) {
            encapsulating = EncapsulatingNodeReference.getCurrent();
            prev = encapsulating.set(this);
        }
        try {
            return alwaysInlined(
                    frame,
                    method,
                    receiver,
                    rubyArgs,
                    literalCallNode,
                    method.getCallTarget(),
                    method,
                    getUncachedAlwaysInlinedMethodNode(method),
                    method.getSharedMethodInfo().getArity(),
                    BranchProfile.getUncached(),
                    BranchProfile.getUncached());
        } finally {
            if (cachedToUncached) {
                encapsulating.set(prev);
            }
        }
    }

    protected AlwaysInlinedMethodNode createAlwaysInlinedMethodNode(InternalMethod method) {
        return (AlwaysInlinedMethodNode) CoreMethodNodeManager
                .createNodeFromFactory(method.alwaysInlinedNodeFactory, RubyNode.EMPTY_ARRAY);
    }

    /** Asserted in {@link CoreMethodNodeManager#createCoreMethodRootNode} */
    protected AlwaysInlinedMethodNode getUncachedAlwaysInlinedMethodNode(InternalMethod method) {
        return (AlwaysInlinedMethodNode) method.alwaysInlinedNodeFactory.getUncachedInstance();
    }

    protected Assumption getMethodAssumption(InternalMethod method) {
        return isSingleContext()
                ? method.getDeclaringModule().fields.getOrCreateMethodAssumption(method.getName())
                : Assumption.ALWAYS_VALID;
    }

    protected int getCacheLimit() {
        return getLanguage().options.DISPATCH_CACHE;
    }

    protected DirectCallNode createCall(RootCallTarget callTarget) {
        final DirectCallNode callNode = DirectCallNode.create(callTarget);
        final DispatchNode dispatch = NodeUtil.findParent(this, DispatchNode.class);
        if (dispatch != null) {
            dispatch.applySplittingInliningStrategy(callTarget, callNode);
        }
        return callNode;
    }
}
