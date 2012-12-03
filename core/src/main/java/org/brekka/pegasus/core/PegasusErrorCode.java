package org.brekka.pegasus.core;

import org.brekka.commons.lang.ErrorCode;

/**
 * Error types relating to the Pegasus subsystem.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
public enum PegasusErrorCode implements ErrorCode {

    PG100,
    PG101,
    PG102,
    
    PG103,
    
    PG200,
    
    /**
     * Token already in use
     */
    PG300,
    
    /**
     * Incorrect unlock code for vault
     */
    PG302,
    
    PG400,
    
    PG401,
    
    PG402,
    
    PG403,
    
    PG500,
    
    PG600,
    PG601,
    
    PG900,
    PG901,
    ;
    
    private static final Area AREA = ErrorCode.Utils.createArea("PG");
    private int number = 0;

    @Override
    public int getNumber() {
        return (this.number == 0 ? this.number = ErrorCode.Utils.extractErrorNumber(name(), getArea()) : this.number);
    }
    @Override
    public Area getArea() { return AREA; }
}
