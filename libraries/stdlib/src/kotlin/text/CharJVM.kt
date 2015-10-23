/*
 * Copyright 2010-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("CharsKt")


package kotlin

/**
 * Returns `true` if this character (Unicode code point) is defined in Unicode.
 */
@kotlin.jvm.JvmName("~isDefined")
public fun Char.isDefined(): Boolean = Character.isDefined(this)

/**
 * Returns `true` if this character is a letter.
 */
@kotlin.jvm.JvmName("~isLetter")
public fun Char.isLetter(): Boolean = Character.isLetter(this)

/**
 * Returns `true` if this character is a letter or digit.
 */
@kotlin.jvm.JvmName("~isLetterOrDigit")
public fun Char.isLetterOrDigit(): Boolean = Character.isLetterOrDigit(this)

/**
 * Returns `true` if this character (Unicode code point) is a digit.
 */
@kotlin.jvm.JvmName("~isDigit")
public fun Char.isDigit(): Boolean = Character.isDigit(this)


/**
 * Returns `true` if this character (Unicode code point) should be regarded as an ignorable
 * character in a Java identifier or a Unicode identifier.
 */
@kotlin.jvm.JvmName("~isIdentifierIgnorable")
public fun Char.isIdentifierIgnorable(): Boolean = Character.isIdentifierIgnorable(this)

/**
 * Returns `true` if this character is an ISO control character.
 */
@kotlin.jvm.JvmName("~isISOControl")
public fun Char.isISOControl(): Boolean = Character.isISOControl(this)

/**
 * Returns `true` if this  character (Unicode code point) may be part of a Java identifier as other than the first character.
 */
@kotlin.jvm.JvmName("~isJavaIdentifierPart")
public fun Char.isJavaIdentifierPart(): Boolean = Character.isJavaIdentifierPart(this)

/**
 * Returns `true` if this character is permissible as the first character in a Java identifier.
 */
@kotlin.jvm.JvmName("~isJavaIdentifierStart")
public fun Char.isJavaIdentifierStart(): Boolean = Character.isJavaIdentifierStart(this)

/**
 * Determines whether a character is whitespace according to the Unicode standard.
 * Returns `true` if the character is whitespace.
 */
public fun Char.isWhitespace(): Boolean = Character.isWhitespace(this) || Character.isSpaceChar(this)

/**
 * Returns `true` if this character is upper case.
 */
@kotlin.jvm.JvmName("~isUpperCase")
public fun Char.isUpperCase(): Boolean = Character.isUpperCase(this)

/**
 * Returns `true` if this character is lower case.
 */
@kotlin.jvm.JvmName("~isLowerCase")
public fun Char.isLowerCase(): Boolean = Character.isLowerCase(this)

/**
 * Converts this character to uppercase.
 */
@kotlin.jvm.JvmName("~toUpperCase")
public fun Char.toUpperCase(): Char = Character.toUpperCase(this)

/**
 * Converts this character to lowercase.
 */
@kotlin.jvm.JvmName("~toLowerCase")
public fun Char.toLowerCase(): Char = Character.toLowerCase(this)

/**
 * Returns `true` if this character is a titlecase character.
 */
@kotlin.jvm.JvmName("~isTitleCase")
public fun Char.isTitleCase(): Boolean = Character.isTitleCase(this)

/**
 * Converts this character to titlecase.
 *
 * @see Character.toTitleCase
 */
@kotlin.jvm.JvmName("~toTitleCase")
public fun Char.toTitleCase(): Char = Character.toTitleCase(this)

/**
 * Returns a value indicating a character's general category.
 */
public fun Char.category(): CharCategory = CharCategory.valueOf(Character.getType(this))

/**
 * Returns the Unicode directionality property for the given character.
 */
public fun Char.directionality(): CharDirectionality = CharDirectionality.valueOf(Character.getDirectionality(this).toInt())

// TODO Provide name for JVM7+
///**
// * Returns the Unicode name of this character, or `null` if the code point of this character is unassigned.
// */
//public fun Char.name(): String? = Character.getName(this.toInt())
