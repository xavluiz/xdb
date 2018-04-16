/**
 * Copyright (c) 2017 by Baddata.
 * All rights reserved.
 */
package com.baddata.api.dto.system;

import com.baddata.api.dto.TypedObject;

public class LogMonitor extends TypedObject {
    
    private long lastMessageTimestamp = 0;
    private long charactersAlreadyRead = 0;

    public long getLastMessageTimestamp() {
        return lastMessageTimestamp;
    }

    public void setLastMessageTimestamp( long lastMessageTimestamp ) {
        this.lastMessageTimestamp = lastMessageTimestamp;
    }
    
    public long getCharactersAlreadyRead() {
        return charactersAlreadyRead;
    }

    public void setCharactersAlreadyRead(long charactersAlreadyRead) {
        this.charactersAlreadyRead = charactersAlreadyRead;
    }

}
