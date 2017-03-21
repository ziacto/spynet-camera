/*
 * This file is part of spyNet Camera, the Android IP camera
 *
 * Copyright (C) 2016-2017 Paolo Dematteis
 *
 * spyNet Camera is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * spyNet Camera is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Paolo Dematteis - spynet314@gmail.com
 */

package com.spynet.camera.common;

import java.io.Closeable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Defines a timeout cache that uses thread-safe concurrent HashMap to cache
 * data and uses a ScheduledExecutorService to execute a Runnable after a designated timeout
 * to remove expired cache entries.
 */
public class TimeoutCache<K, V> implements Closeable {

    protected final String TAG = getClass().getSimpleName();

    /**
     * A thread-safe HashMap that supports full concurrency of retrievals and
     * high expected concurrency for updates. It store CacheValues.
     */
    private ConcurrentHashMap<K, CacheValues> mResults =
            new ConcurrentHashMap<>();

    /**
     * Executor service that will execute Runnable after certain timeouts to remove
     * expired CacheValues.
     */
    private ScheduledExecutorService mScheduledExecutorService =
            Executors.newScheduledThreadPool(1);

    /**
     * Datatype that represents the contents of the cache. It contains the value of the cache entity
     * and a future that executes a runnable after certain time period elapses to remove expired
     * CacheValue objects.
     */
    private class CacheValues {
        /**
         * Value of the cache.
         */
        final public V mValue;

        /**
         * Result of an asynchronous computation. It references a runnable that has been scheduled
         * to execute after certain time period elapses.
         */
        public ScheduledFuture<?> mFuture = null;

        /**
         * Constructor for CacheValue.
         *
         * @param value the cache entry
         */
        public CacheValues(V value) {
            mValue = value;
        }

        /**
         * Setter for the ScheduledFuture.
         *
         * @param future a ScheduledFuture that can be used to cancel a Runnable
         */
        public void setFuture(ScheduledFuture<?> future) {
            mFuture = future;
        }
    }

    /**
     * Put the value into the cache at the designated key with a certain timeout
     * after which the CacheValue will expire.
     *
     * @param key     the key for the cache entry
     * @param value   the value of the cache entry
     * @param timeout the timeout period in seconds
     */
    public void put(final K key, V value, int timeout) {

        // Create this object here so it can be referenced in the cleanupCacheRunnable below.
        final CacheValues cacheValues = new CacheValues(value);

        // Runnable that when executed will remove a CacheValues when its timeout expires.
        final Runnable cleanupCacheRunnable = new Runnable() {
            @Override
            public void run() {
                // Only remove key if it is currently associated with cacheValues.
                // This avoid race conditions that would otherwise occur since an mFuture to
                // a previous CacheValues isn't canceled until after the new CacheValues
                // is added to the map.
                mResults.remove(key, cacheValues);
            }
        };

        // Put a new CacheValues object into the ConcurrentHashMap associated with the key
        // and return the previous CacheValues.
        CacheValues prevCacheValues = mResults.put(key, cacheValues);

        // If there was a previous CacheValues associated with this key then cancel the future
        // immediately. Note that there is no race condition between the ScheduledExecutorService
        // running the cleanupCacheRunnable and canceling the future here since the
        // ConcurrentHashMap.remove() call won't actually remove the key unless the value is equal
        // to the original cacheValues reference.
        if (prevCacheValues != null)
            prevCacheValues.mFuture.cancel(true);

        // Create a ScheduledFuture for the new cacheValues object that will execute the
        // cleanupCacheRunnable after the designated timeout.
        ScheduledFuture<?> future = mScheduledExecutorService.schedule(
                cleanupCacheRunnable, timeout, TimeUnit.SECONDS);

        // Now that we have a future, attach it to the cacheValues object that has already been
        // safely added to the cache. The reason we do not set the future before adding the
        // cacheValues object to the cache is because it is possible (but unlikely) for the future
        // to trigger in the small time window between when it is started and returned from the
        // ScheduledExecutorService and when the put() call is made to add it to the cache.
        cacheValues.setFuture(future);
    }

    /**
     * Gets the value from the cache at the designated key.
     *
     * @param key the key for the cache entry
     * @return value  the value associated with the key, which may be null
     * if there's no key in the cache
     */
    public final V get(K key) {
        CacheValues cacheValues = mResults.get(key);
        return cacheValues != null ? cacheValues.mValue : null;
    }

    /**
     * Removes the value associated with the designated key.
     *
     * @param key the key for the cache entry
     */
    public void remove(K key) {
        mResults.remove(key);
    }

    /**
     * @return the current number of entries in the cache
     */
    public final int size() {
        return mResults.size();
    }

    /**
     * Shutdown the ScheduledExecutorService.
     */
    @Override
    public void close() {
        // Cancel all remaining futures.
        for (CacheValues cvs : mResults.values()) {
            if (cvs.mFuture != null)
                cvs.mFuture.cancel(true);
        }

        // Shutdown the ScheduledExecutorService immediately.
        mScheduledExecutorService.shutdownNow();
    }
}
