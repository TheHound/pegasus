/**
 * 
 */
package org.brekka.pegasus.core.model;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.Transient;

import org.brekka.phalanx.api.model.AuthenticatedPrincipal;
import org.hibernate.annotations.Type;

/**
 * A vault is a place for a member to store various keys.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Entity
@DiscriminatorValue("Vault")
public class Vault extends KeySafe<Member> {
    
    /**
     * Serial UID
     */
    private static final long serialVersionUID = -5208658520688698466L;
    
    /**
     * The principal Id of this vault
     */
    @Type(type="pg-uuid")
    @Column(name="`PrincipalID`")
    private UUID principalId;
    
    /**
     * When the vault has been 'opened' this field will be set to the marker used to 
     * identify the open now open "principal".
     */
    @Transient
    private transient AuthenticatedPrincipal authenticatedPrincipal;
    
    /**
     * 
     */
    public Vault() {
    }
    
    public Vault(UUID id) {
        setId(id);
    }
    

    public UUID getPrincipalId() {
        return principalId;
    }

    public void setPrincipalId(UUID principalId) {
        this.principalId = principalId;
    }
    
    public AuthenticatedPrincipal getAuthenticatedPrincipal() {
        return authenticatedPrincipal;
    }

    public void setAuthenticatedPrincipal(AuthenticatedPrincipal authenticatedPrincipal) {
        this.authenticatedPrincipal = authenticatedPrincipal;
    }
}
