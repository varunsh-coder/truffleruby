/*
 * Copyright (c) 2015, 2019 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.stdlib;

import java.util.Set;

import org.truffleruby.Layouts;
import org.truffleruby.builtins.CoreMethod;
import org.truffleruby.builtins.CoreMethodArrayArgumentsNode;
import org.truffleruby.builtins.CoreModule;
import org.truffleruby.core.regexp.RubyMatchData;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.regexp.MatchDataNodes.ValuesNode;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.language.objects.ObjectGraph;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.object.DynamicObject;

@CoreModule("Truffle::ObjSpace")
public abstract class ObjSpaceNodes {

    @CoreMethod(names = "memsize_of", onSingleton = true, required = 1)
    public abstract static class MemsizeOfNode extends CoreMethodArrayArgumentsNode {

        @Specialization
        protected int memsizeOfArray(RubyArray object) {
            return memsizeOfObject(object) + object.size;
        }

        @Specialization(guards = "isRubyHash(object)")
        protected int memsizeOfHash(DynamicObject object) {
            return memsizeOfObject(object) + Layouts.HASH.getSize(object);
        }

        @Specialization
        protected int memsizeOfString(RubyString object) {
            return memsizeOfObject(object) + object.rope.byteLength();
        }

        @Specialization
        protected int memsizeOfMatchData(RubyMatchData object,
                @Cached ValuesNode matchDataValues) {
            return memsizeOfObject(object) + matchDataValues.execute(object).length;
        }

        @Specialization(
                guards = {
                        "!isRubyArray(object)",
                        "!isRubyHash(object)",
                        "!isRubyString(object)",
                        "!isRubyMatchData(object)" })
        protected int memsizeOfObject(DynamicObject object) {
            return 1 + object.getShape().getPropertyListInternal(false).size();
        }

        @Specialization(guards = "!isDynamicObject(object)")
        protected int memsize(Object object) {
            return 0;
        }
    }

    @CoreMethod(names = "adjacent_objects", onSingleton = true, required = 1)
    public abstract static class AdjacentObjectsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        protected RubyArray adjacentObjects(DynamicObject object) {
            final Set<Object> objects = ObjectGraph.getAdjacentObjects(object);
            return createArray(objects.toArray());
        }

        @Fallback
        protected Object adjacentObjectsPrimitive(Object object) {
            return nil;
        }

    }

    @CoreMethod(names = "root_objects", onSingleton = true)
    public abstract static class RootObjectsNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        protected RubyArray rootObjects() {
            final Set<Object> objects = ObjectGraph.stopAndGetRootObjects(this, getContext());
            return createArray(objects.toArray());
        }

    }

    @CoreMethod(names = "trace_allocations_start", onSingleton = true)
    public abstract static class TraceAllocationsStartNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        protected Object traceAllocationsStart() {
            getContext().getObjectSpaceManager().traceAllocationsStart(getContext().getLanguage());
            return nil;
        }

    }

    @CoreMethod(names = "trace_allocations_stop", onSingleton = true)
    public abstract static class TraceAllocationsStopNode extends CoreMethodArrayArgumentsNode {

        @TruffleBoundary
        @Specialization
        protected Object traceAllocationsStop() {
            getContext().getObjectSpaceManager().traceAllocationsStop(getContext().getLanguage());
            return nil;
        }

    }

}
