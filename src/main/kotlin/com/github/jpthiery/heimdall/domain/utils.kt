package com.github.jpthiery.heimdall.domain

sealed class Either<out L, out R> {

    data class Left<out L>(val left: L) : Either<L, Nothing>()
    data class Right<out R>(val right: R) : Either<Nothing, R>()

    companion object {
        fun <L> left(value: L): Left<L> = Either.Left(value)
        fun <R> right(value: R): Right<R> = Either.Right(value)

        fun <L> fail(value: L): Left<L> = Either.Left(value)
        fun <R> success(value: R): Right<R> = Either.Right(value)

        fun <R> tryTo(action: () -> R): Either<Exception, R> = try {
            success(action())
        } catch (e: Exception) {
            fail(e)
        }

    }

}

inline infix fun <L, R, R2> Either<L, R>.map(f: (R) -> R2): Either<L, R2> = when (this) {
    is Either.Left -> this
    is Either.Right -> Either.Right(f(this.right))
}

infix fun <L, R, R2> Either<L, (R) -> R2>.apply(f: Either<L, R>): Either<L, R2> = when (this) {
    is Either.Left -> this
    is Either.Right -> f.map(this.right)
}

inline infix fun <L, R, R2> Either<L, R>.flatMap(f: (R) -> Either<L, R2>): Either<L, R2> = when (this) {
    is Either.Left -> this
    is Either.Right -> f(right)
}

fun <L, R, V> Either<L, R>.fold(foldLeft: (L) -> V, foldRight: (R) -> V): V = when (this) {
    is Either.Left -> foldLeft(left)
    is Either.Right -> foldRight(right)
}

//  alias
fun <R, V> Either<R, Exception>.consume(onSuccess: (R) -> V, onFailed: (Exception) -> V): V = fold(onSuccess, onFailed)

fun <L, R> Either<L, R>.get(or: (L) -> R) : R = when (this) {
    is Either.Left -> or(left)
    is Either.Right -> right
}

