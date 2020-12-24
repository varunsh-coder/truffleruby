/*
 * Copyright (c) 2013, 2020 Oracle and/or its affiliates. All rights reserved. This
 * code is released under a tri EPL/GPL/LGPL license. You can use it,
 * redistribute it and/or modify it under the terms of the:
 *
 * Eclipse Public License version 2.0, or
 * GNU General Public License version 2, or
 * GNU Lesser General Public License version 2.1.
 */
package org.truffleruby.language;

import org.truffleruby.core.CoreLibrary;
import org.truffleruby.core.array.RubyArray;
import org.truffleruby.core.encoding.RubyEncoding;
import org.truffleruby.core.klass.RubyClass;
import org.truffleruby.core.method.RubyMethod;
import org.truffleruby.core.method.RubyUnboundMethod;
import org.truffleruby.core.hash.RubyHash;
import org.truffleruby.core.module.RubyModule;
import org.truffleruby.core.numeric.RubyBignum;
import org.truffleruby.core.proc.RubyProc;
import org.truffleruby.core.range.RubyIntRange;
import org.truffleruby.core.range.RubyRange;
import org.truffleruby.core.regexp.RubyMatchData;
import org.truffleruby.core.regexp.RubyRegexp;
import org.truffleruby.core.string.ImmutableRubyString;
import org.truffleruby.core.string.RubyString;
import org.truffleruby.core.symbol.RubySymbol;
import org.truffleruby.interop.ToJavaStringNode;

public abstract class RubyGuards {

    private static final long NEGATIVE_ZERO_DOUBLE_BITS = Double.doubleToRawLongBits(-0.0);

    // Basic Java types

    public static boolean isInteger(Object value) {
        return value instanceof Integer;
    }

    public static boolean fitsInInteger(long value) {
        return CoreLibrary.fitsIntoInteger(value);
    }

    public static boolean isLong(Object value) {
        return value instanceof Long;
    }

    public static boolean isDouble(Object value) {
        return value instanceof Double;
    }

    public static boolean isCharacter(Object value) {
        return value instanceof Character;
    }

    public static boolean isString(Object value) {
        return value instanceof String;
    }

    public static boolean isIntOrLong(Object value) {
        return value instanceof Integer || value instanceof Long;
    }

    public static boolean isBasicInteger(Object object) {
        return object instanceof Byte || object instanceof Short || object instanceof Integer || object instanceof Long;
    }

    public static boolean isBasicNumber(Object object) {
        return object instanceof Byte || object instanceof Short || object instanceof Integer ||
                object instanceof Long || object instanceof Float || object instanceof Double;
    }

    public static boolean isPrimitive(Object object) {
        return object instanceof Boolean || object instanceof Byte || object instanceof Short ||
                object instanceof Integer || object instanceof Long || object instanceof Float ||
                object instanceof Double;
    }

    public static boolean isPrimitiveClass(Class<?> clazz) {
        return clazz == Boolean.class || clazz == Byte.class || clazz == Short.class || clazz == Integer.class ||
                clazz == Long.class || clazz == Float.class || clazz == Double.class;
    }

    // Ruby types

    public static boolean isRubyBignum(Object value) {
        return value instanceof RubyBignum;
    }

    public static boolean isIntRange(Object value) {
        return value instanceof RubyIntRange;
    }

    public static boolean isRubyRange(Object value) {
        return value instanceof RubyRange;
    }

    public static boolean isRubyArray(Object value) {
        return value instanceof RubyArray;
    }

    public static boolean isRubyClass(Object value) {
        return value instanceof RubyClass;
    }

    public static boolean isRubyHash(Object value) {
        return value instanceof RubyHash;
    }

    public static boolean isRubyModule(Object value) {
        return value instanceof RubyModule;
    }

    public static boolean isRubyRegexp(Object value) {
        return value instanceof RubyRegexp;
    }

    /** Use RubyStringLibrary to check if it's a String */
    public static boolean isNotRubyString(Object value) {
        return !(value instanceof ImmutableRubyString) && !(value instanceof RubyString);
    }

    public static boolean isImmutableRubyString(Object value) {
        return value instanceof ImmutableRubyString;
    }

    public static boolean isRubySymbol(Object value) {
        return value instanceof RubySymbol;
    }

    /** Should be used only for interop together with {@link ToJavaStringNode} */
    public static boolean isRubySymbolOrString(Object value) {
        return isRubySymbol(value) || value instanceof RubyString || value instanceof ImmutableRubyString;
    }

    public static boolean isRubyEncoding(Object object) {
        return object instanceof RubyEncoding;
    }

    public static boolean isRubyMethod(Object value) {
        return value instanceof RubyMethod;
    }

    public static boolean isRubyUnboundMethod(Object value) {
        return value instanceof RubyUnboundMethod;
    }

    public static boolean isRubyProc(Object object) {
        return object instanceof RubyProc;
    }

    public static boolean isRubyMatchData(Object object) {
        return object instanceof RubyMatchData;
    }

    public static boolean isRubyInteger(Object object) {
        return isBasicInteger(object) || object instanceof RubyBignum;
    }

    public static boolean isRubyNumber(Object object) {
        // Doesn't include classes like BigDecimal
        return isBasicNumber(object) || object instanceof RubyBignum;
    }

    public static boolean isNil(Object object) {
        return object == Nil.INSTANCE;
    }

    // Internal types

    public static boolean isRubyDynamicObject(Object object) {
        return object instanceof RubyDynamicObject;
    }

    public static boolean isRubyValue(Object object) {
        return object instanceof RubyDynamicObject || object instanceof ImmutableRubyObject || isPrimitive(object);
    }

    public static boolean isForeignObject(Object object) {
        return !isRubyValue(object);
    }

    // Sentinels

    public static boolean wasProvided(Object value) {
        return !wasNotProvided(value);
    }

    public static boolean wasNotProvided(Object value) {
        return value == NotProvided.INSTANCE;
    }

    // Values

    public static boolean isNaN(double value) {
        return Double.isNaN(value);
    }

    public static boolean isInfinity(double value) {
        return Double.isInfinite(value);
    }

    public static boolean isFinite(double value) {
        return Double.isFinite(value);
    }

    public static boolean isPositive(double value) {
        return value >= 0;
    }

    public static boolean isNegativeZero(double value) {
        return Double.doubleToRawLongBits(value) == NEGATIVE_ZERO_DOUBLE_BITS;
    }

    // Composite

    public static boolean isSingletonClass(RubyModule value) {
        return value instanceof RubyClass && ((RubyClass) value).isSingleton;
    }

    public static boolean isMetaClass(RubyModule value) {
        return isSingletonClass(value) && ((RubyClass) value).attached instanceof RubyModule;
    }

    // Arguments

    public static boolean noArguments(Object[] args) {
        return args.length == 0;
    }

    public static boolean singleArgument(Object[] args) {
        return args.length == 1;
    }

}
