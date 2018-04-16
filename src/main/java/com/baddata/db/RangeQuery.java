/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.db;

public class RangeQuery {
    private String field;
    private String min;
    private String max;
    private boolean longRange;
    
    /**
     * Constructor to build a range query using a Long max value, which
     * means it's a long range query.
     * 
     * @param field
     * @param max
     */
    public RangeQuery(String field, Long max) {
        this(field, null /*min*/, String.valueOf(max), true /*longRange*/);
    }
    
    /**
     * Constructor to build a range query using a Long min 
     * and max value, which means it's a long range query.
     * 
     * @param field
     * @param min
     * @param max
     */
    public RangeQuery(String field, Long min, Long max) {
        this(field, ((min != null) ? min.toString() : null), ((max != null) ? max.toString() : null), true /*longRange*/);
    }

    public RangeQuery(String field, String min, String max, boolean longRange) {
        this.field = field;
        this.min = min;
        this.max = max;
        this.longRange = longRange;
    }
    
    public String getField() {
        return field;
    }
    public String getMin() {
        return min;
    }
    public String getMax() {
        return max;
    }
    public boolean isLongRange() {
        return longRange;
    }

    @Override
    public String toString() {
        return "RangeQuery [field=" + field + ", min=" + min + ", max="
                + max + ", longRange=" + longRange + "]";
    }
}
