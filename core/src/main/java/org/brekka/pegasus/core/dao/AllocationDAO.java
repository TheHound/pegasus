/**
 * 
 */
package org.brekka.pegasus.core.dao;

import java.util.List;
import java.util.UUID;

import org.brekka.commons.persistence.dao.EntityDAO;
import org.brekka.commons.persistence.model.ListingCriteria;
import org.brekka.pegasus.core.model.Allocation;
import org.brekka.pegasus.core.model.Dispatch;

/**
 * @author Andrew Taylor
 *
 */
public interface AllocationDAO extends EntityDAO<UUID, Allocation> {

    /**
     * @param bundle
     * @return 
     */
    void refresh(Allocation allocation);

    /**
     * @param maxAllocationCount
     * @return
     */
    List<Allocation> retrieveOldestExpired(int maxAllocationCount);
    
    int retrieveDerivedFromListingRowCount(Dispatch derivedFrom);
    
    List<Allocation> retrieveDerivedFromListing(Dispatch derivedFrom, ListingCriteria listingCriteria);
}
