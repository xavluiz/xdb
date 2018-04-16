/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.baddata.log.Logger;

/**
 * A simple factory to give created thread a meaningful name. Used mainly for
 * thread pools.
 */
public class NamedThreadFactory implements ThreadFactory {
    
    protected static Logger logger = Logger.getLogger(NamedThreadFactory.class.getName());

    // default prefix for Baddata "bd"
    private static final String DEFAULT_PREFIX = "bd";

    private static NamedThreadFactory defaultFactory;

    private String prefix;

    private AtomicInteger number;

    static {
        defaultFactory = new NamedThreadFactory(DEFAULT_PREFIX);
    }

    public static Thread create(Runnable job, String name) {
        return defaultFactory.create0(job, name);
    }

    public static Thread createWithPrefix(Runnable job, String prefix) {
        return defaultFactory.create1(job, prefix);
    }

    public NamedThreadFactory(String poolName) {
        prefix = poolName;
        number = new AtomicInteger(0);
    }

    public Thread newThread(Runnable job) {
        ThreadGroup g = Thread.currentThread().getThreadGroup();
        Thread t = new Thread(g, job, prefix + "-" + number.incrementAndGet());

        logger.debug("created thread id:" + t.getId() + ", name:" + t.getName());

        return t;
    }

    private Thread create0(Runnable job, String name) {
        Thread t;
        ThreadGroup g = Thread.currentThread().getThreadGroup();
        if ( name != null ) {
            t = new Thread(g, job, prefix + "-" + name);
        }
        else {
            t = new Thread(g, job);
            long id = t.getId();
            t.setName(prefix + "-" + id);
        }

        logger.debug("created thread id:" + t.getId() + ", name:" + t.getName());

        return t;
    }

    private Thread create1(Runnable job, String pre) {
        Thread t;
        ThreadGroup g = Thread.currentThread().getThreadGroup();

        if ( pre == null )
            pre = prefix;

        t = new Thread(g, job);
        long id = t.getId();
        t.setName(pre + "-" + id);

        logger.debug("created thread id:" + t.getId() + ", name:" + t.getName());

        return t;
    }
}
