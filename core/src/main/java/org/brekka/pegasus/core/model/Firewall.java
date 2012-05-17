/**
 * 
 */
package org.brekka.pegasus.core.model;

import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Table;

import org.hibernate.annotations.Index;
import org.hibernate.annotations.Type;

/**
 * A set of rules that can be used to control what remote users can access resources based on their IP address.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Entity
@Table(name="`Firewall`")
public class Firewall extends SnapshotEntity {

    /**
     * Serial UID
     */
    private static final long serialVersionUID = 4426014107250666833L;

    /**
     * The entity that owns this firewall ruleset. 
     * For example a member, division or even the system id.
     */
    @Column(name="OwningEntityId", nullable=false)
    @Type(type="pg-uuid")
    @Index(name="IDX_Firewall_Owner")
    private UUID owningEntityId;
    
    /**
     * Optional name for this firewall.
     */
    @Column(name="`Name`", length=128)
    private String name;
    
    /**
     * The default action to be performed if no rules are matched.
     */
    @Column(name="`DefaultAction`", length=5, nullable=false)
    @Enumerated(EnumType.STRING)
    private FirewallAction defaultAction;

    public UUID getOwningEntityId() {
        return owningEntityId;
    }

    public void setOwningEntityId(UUID owningEntityId) {
        this.owningEntityId = owningEntityId;
    }

    public FirewallAction getDefaultAction() {
        return defaultAction;
    }

    public void setDefaultAction(FirewallAction defaultAction) {
        this.defaultAction = defaultAction;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}