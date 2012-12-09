/**
 * 
 */
package org.brekka.pegasus.core.model;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Transient;

import org.brekka.phalanx.api.model.PrivateKeyToken;
import org.hibernate.annotations.Type;

/**
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Entity
@DiscriminatorValue("Associate")
public class Associate extends Actor {

    /**
     * Serial UID
     */
    private static final long serialVersionUID = -7785708271201081308L;

    /**
     * The person who is an employee
     */
    @ManyToOne
    @JoinColumn(name="`MemberID`")
    private Member member;
    
    /**
     * The organization this employee belongs to
     */
    @ManyToOne
    @JoinColumn(name="`OrganizationID`")
    private Organization organization;
    
    /**
     * The main e-mail address used to identify this employee (at the organization).
     */
    @OneToOne
    @JoinColumn(name="`PrimaryEMailAddressID`")
    private EMailAddress primaryEMailAddress;
    
    /**
     * The associate copy of the organization-wide key pair.
     */
    @Column(name="`KeyPairID`")
    @Type(type="pg-uuid")
    private UUID keyPairId;
    
    /**
     * The private key token which becomes available when the key pair above is unlocked.
     */
    @Transient
    private transient PrivateKeyToken privateKeyToken;

    
    public Member getMember() {
        return member;
    }

    public void setMember(Member member) {
        this.member = member;
    }

    public Organization getOrganization() {
        return organization;
    }

    public void setOrganization(Organization organization) {
        this.organization = organization;
    }

    public EMailAddress getPrimaryEMailAddress() {
        return primaryEMailAddress;
    }

    public void setPrimaryEMailAddress(EMailAddress primaryEMailAddress) {
        this.primaryEMailAddress = primaryEMailAddress;
    }

    public UUID getKeyPairId() {
        return keyPairId;
    }

    public void setKeyPairId(UUID keyPairId) {
        this.keyPairId = keyPairId;
    }

    public PrivateKeyToken getPrivateKeyToken() {
        return privateKeyToken;
    }

    public void setPrivateKeyToken(PrivateKeyToken privateKeyToken) {
        this.privateKeyToken = privateKeyToken;
    }
}
