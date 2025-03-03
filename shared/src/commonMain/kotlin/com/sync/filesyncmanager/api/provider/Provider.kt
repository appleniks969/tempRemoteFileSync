package com.sync.filesyncmanager.api.provider

/**
 * A provider interface for dependency injection without external frameworks
 * @param T The type of object this provider supplies
 */
interface Provider<T> {
    /**
     * Gets an instance of the provided type
     * @return An instance of type T
     */
    fun get(): T
}

/**
 * A lazy provider that creates the instance only when first requested
 * @param T The type of object this provider supplies
 * @param factory A function that creates the instance
 */
class LazyProvider<T>(
    private val factory: () -> T,
) : Provider<T> {
    private val instance by lazy(factory)

    override fun get(): T = instance
}

/**
 * A singleton provider that always returns the same instance
 * @param T The type of object this provider supplies
 * @param instance The singleton instance to provide
 */
class SingletonProvider<T>(
    private val instance: T,
) : Provider<T> {
    override fun get(): T = instance
}

/**
 * A factory provider that creates a new instance on each request
 * @param T The type of object this provider supplies
 * @param factory A function that creates a new instance
 */
class FactoryProvider<T>(
    private val factory: () -> T,
) : Provider<T> {
    override fun get(): T = factory()
}
