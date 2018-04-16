/**
 * Copyright (c) 2016 by Baddata.
 * All rights reserved.
 */
package com.baddata.util;

import java.util.HashMap;
import java.util.Map;

import com.baddata.api.dto.profiler.Phase;
import com.baddata.api.dto.profiler.PhaseStat;
import com.baddata.exception.IndexPersistException;
import com.baddata.manager.db.PersistenceManager;
import com.google.common.base.Strings;


public class PhaseTimer {
    
    private String currentPhase = "";
    private String name = "";
    private long firstStartTime = System.currentTimeMillis();
    private long startTime = firstStartTime;
    private static PersistenceManager persistence = PersistenceManager.getInstance();
    
    // static to allow it to persist across phase timer instantiations
    private static Map<String, Long> lastLoggedPhaseTimerMap = new HashMap<String, Long>();
    private static Map<String, Boolean> allowPhaseLoggingMap = new HashMap<String, Boolean>();
    
    private Phase phase;
    
    public PhaseTimer( String name ) {
        
        if ( Strings.isNullOrEmpty(name) ) {
            name = this.getCallersMethodName();
        }
        
        //
        // update the last time this phase timer instance has logged timer info
        // and whether this specific instance will be allowed to log or not
        //
        Long lastLoggedPhase = lastLoggedPhaseTimerMap.get( name );
        long now = System.currentTimeMillis();
        Boolean allowPhaseLoggingForPhaseInstance = new Boolean( true );
        if ( lastLoggedPhase == null || (now - lastLoggedPhase.longValue()) >= DateUtil.MINUTE_IN_MILLIS ) {
            // set the last time logged to now.
            // the last time logged is either null or it's been over a minute
            lastLoggedPhaseTimerMap.put( name, new Long( now ) );
        } else {
            // it logged less than a minute ago, don't allow logging
            allowPhaseLoggingForPhaseInstance =  new Boolean( false );
        }
        
        // update the allow logging map
        allowPhaseLoggingMap.put( name, allowPhaseLoggingForPhaseInstance );
        
        this.name = name;
        
        phase = new Phase();
        phase.setName( name );
    }
    
    public void nextPhase( String nextPhase ) {
        if ( allowPhaseLoggingMap.get( name ).booleanValue() ) {
        
            this.logCurrentPhase();
        
            currentPhase = nextPhase;
        }
    }
    
    public void done() {
        if ( allowPhaseLoggingMap.get( name ).booleanValue() ) {
            this.logCurrentPhase();
            
            // update the last logged phase timer to now
            lastLoggedPhaseTimerMap.put( name, new Long( System.currentTimeMillis() ) );

            // (TODO) persist...
            try {
                persistence.save( this.phase );
            } catch (IndexPersistException e) {
                //
            }
        }
    }
    
    private void logCurrentPhase() {
        long now = System.currentTimeMillis();
        
        // this logs the previous phase
        if ( !currentPhase.equals("") ) {
            long delta = ( now - startTime );
            PhaseStat phaseStat = new PhaseStat();
            phaseStat.setName( currentPhase );
            phaseStat.setEllapsed( delta );
            phase.addStat( phaseStat );
        }
        startTime = now;
    }
    
    public String toString() {
        long now = System.currentTimeMillis();
        
        long ellapsedTime = now - firstStartTime;
        if ( ellapsedTime > 1000 ) {
            float seconds = (float) ( ellapsedTime ) / (float) 1000;
            
            return "PhaseTimer [" + name + "] : Total Ellapsed Time [" + seconds + " (sec)]";
        }
        return "PhaseTimer [" + name + "] : Total Ellapsed Time [" + ellapsedTime + " (ms)]";
    }
    
    /**
     * Get the method caller of this PhaseTimer
     * @return
     */
    private String getCallersMethodName() { 
        StackTraceElement[] stElements = Thread.currentThread().getStackTrace();
        for (int i = 1; i < stElements.length; i++) {
            StackTraceElement ste = stElements[i];
            if ( ste == null ) {
                continue;
            }
            
            String className = ste.getClassName();
            
            if ( className == null ) {
                continue;
            }
            
            if ( className.indexOf(PhaseTimer.class.getName()) == -1 && !className.equalsIgnoreCase("java.lang.Thread") ) {
                if ( ste.getMethodName() != null ) {
                    return ste.getMethodName();
                }
                return ste.getClassName();
            }
        }
        return null;
     }

}
