/**
 * 
 */
package org.brekka.pegasus.core.model;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

/**
 * A bundle that has been or is in the process of being dispatched. Essentially exists as a way for
 * a member/employee to track what happened to the file they sent and potentially re-download it or
 * send it again.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Entity
@DiscriminatorValue("Dispatch")
public class Dispatch extends Allocation implements KeySafeAware {
    /**
     * Serial UID
     */
    private static final long serialVersionUID = -9040394712700014948L;

    
    /**
     * The division this from which this was dispatched.
     */
    @ManyToOne
    @JoinColumn(name="`DivisionID`")
    private Division<?> division;
    
    /**
     * The key safe that contains the key
     */
    @ManyToOne
    @JoinColumn(name="`KeySafeID`")
    private KeySafe<?> keySafe;

    @Override
    public KeySafe<?> getKeySafe() {
        return keySafe;
    }

    public void setKeySafe(KeySafe<?> keySafe) {
        this.keySafe = keySafe;
    }

    public Division<?> getDivision() {
        return division;
    }

    public void setDivision(Division<?> division) {
        this.division = division;
    }
}
