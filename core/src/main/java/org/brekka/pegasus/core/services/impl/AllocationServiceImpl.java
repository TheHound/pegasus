/**
 * 
 */
package org.brekka.pegasus.core.services.impl;

import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.brekka.commons.persistence.model.ListingCriteria;
import org.brekka.commons.persistence.support.EntityUtils;
import org.brekka.paveway.core.dao.CryptedFileDAO;
import org.brekka.paveway.core.model.CryptedFile;
import org.brekka.pegasus.core.dao.AllocationDAO;
import org.brekka.pegasus.core.dao.AllocationFileDAO;
import org.brekka.pegasus.core.model.AccessorContext;
import org.brekka.pegasus.core.model.Allocation;
import org.brekka.pegasus.core.model.AllocationFile;
import org.brekka.pegasus.core.model.Dispatch;
import org.brekka.pegasus.core.model.FileDownloadEvent;
import org.brekka.pegasus.core.model.KeySafeAware;
import org.brekka.pegasus.core.model.XmlEntity;
import org.brekka.pegasus.core.services.AllocationService;
import org.brekka.pegasus.core.services.KeySafeService;
import org.brekka.xml.pegasus.v2.model.AllocationDocument;
import org.brekka.xml.pegasus.v2.model.BundleType;
import org.brekka.xml.pegasus.v2.model.FileType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


/**
 * @author Andrew Taylor (andrew@brekka.org)
 *
 */
@Service
@Transactional
public class AllocationServiceImpl extends AllocationServiceSupport implements AllocationService {

    
    @Autowired
    private AllocationFileDAO allocationFileDAO;
    
    @Autowired
    private AllocationDAO allocationDAO;
    
    @Autowired
    private KeySafeService keySafeService;
    
    @Autowired
    private CryptedFileDAO cryptedFileDAO;
    
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.BundleService#incrementDownloadCounter(org.brekka.pegasus.core.model.BundleFile)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void incrementDownloadCounter(AllocationFile allocationFile) {
        FileType xml = allocationFile.getXml();
        int maxDownloads = Integer.MAX_VALUE;
        if (xml.isSetMaxDownloads()) {
            maxDownloads = xml.getMaxDownloads();
        }
        AllocationFile managed = allocationFileDAO.retrieveById(allocationFile.getId());
        int downloadCount = managed.getDownloadCount();
        // Increment the downloads
        downloadCount++;
        managed.setDownloadCount(downloadCount);
        if (downloadCount == maxDownloads) {
            // Mark this file for deletion
            managed.setExpires(new Date());
        }
        allocationFileDAO.update(managed);
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.AllocationService#retrieveFile(java.util.UUID)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public AllocationFile retrieveFile(UUID allocationFileId) {
        AccessorContext currentContext = AccessorContextImpl.getCurrent();
        AllocationFile unlockedAllocationFile = currentContext.retrieve(allocationFileId, AllocationFile.class);
        if (unlockedAllocationFile != null) {
            allocationFileDAO.refresh(unlockedAllocationFile);
            return unlockedAllocationFile;
        }
        
        AllocationFile allocationFile = allocationFileDAO.retrieveById(allocationFileId);
        if (allocationFile == null) {
            return null;
        }
        
        Allocation allocation = allocationFile.getAllocation();
               
        Allocation unlockedAllocation = currentContext.retrieve(allocation.getId(), Allocation.class);
        if (unlockedAllocation == null) {
            // Allocation has not yet been unlocked
            decryptDocument(allocation, true);
            currentContext.retain(allocation.getId(), allocation);
            unlockedAllocation = allocation;
        }
        allocationFile.setAllocation(unlockedAllocation);
        BundleType bundle = unlockedAllocation.getXml().getBean().getAllocation().getBundle();
        List<FileType> fileList = bundle.getFileList();
        for (FileType fileType : fileList) {
            if (allocationFile.getCryptedFile().getId().toString().equals(fileType.getUUID())) {
                allocationFile.setXml(fileType);
                break;
            }
        }
        currentContext.retain(allocationFile.getId(), allocationFile);
        return allocationFile;
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.AllocationService#deallocateAllocation(org.brekka.pegasus.core.model.Allocation)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public void clearAllocation(Allocation allocation) {
        List<AllocationFile> fileList = allocationFileDAO.retrieveByAllocation(allocation);
        for (AllocationFile file : fileList) {
            clearAllocationFile(file, false);
        }
        
        XmlEntity<AllocationDocument> xml = allocation.getXml();
        
        allocation.setDeleted(new Date());
        allocation.setXml(null);
        allocationDAO.update(allocation);
        
        xmlEntityService.delete(xml.getId());
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.AllocationService#releaseDetails(java.util.List)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public <T extends Allocation & KeySafeAware> void releaseDetails(List<T> allocationList) {
        for (T allocation : allocationList) {
            if (allocation == null) {
                continue;
            }
            decryptDocument(allocation, true);
            bindToContext(allocation);
        }
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.AllocationService#forceExpireAllocation(org.brekka.pegasus.core.model.Allocation)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void forceExpireAllocation(Allocation allocation) {
        Date expiryDate = new Date();
        // Update the incoming reference with the date
        allocation.setExpires(expiryDate);
        
        Allocation managed = allocationDAO.retrieveById(allocation.getId());
        managed.setExpires(expiryDate);
        
        allocationDAO.update(managed);
    }

    /**
     * @param file
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public void clearAllocationFile(AllocationFile file) {
        clearAllocationFile(file, true);
    }
    
    
    protected void clearAllocationFile(AllocationFile file, boolean deleteAllocationIfPossible) {
        AllocationFile allocationFile = allocationFileDAO.retrieveById(file.getId());
        
        if (allocationFile.getDeleted() != null) {
            // Already deleted
            return;
        }
        CryptedFile cryptedFile = allocationFile.getCryptedFile();
        List<AllocationFile> active = allocationFileDAO.retrieveActiveForCryptedFile(cryptedFile);
        boolean canDeleteCryptedFile = active.size() == 1;
        
        // Check whether we can delete the rest of the allocation also
        if (deleteAllocationIfPossible) {
            Allocation allocation = allocationFile.getAllocation();
            active = allocationFileDAO.retrieveActiveForAllocation(allocation);
            if (active.size() == 1) {
                // Make the allocation as expired. The reaper will pick it up soon
                allocation.setExpires(new Date());
                allocationDAO.update(allocation);
            }
        }
        allocationFile.setDeleted(new Date());
        allocationFile.setCryptedFile(null);
        allocationFileDAO.update(allocationFile);
        
        if (canDeleteCryptedFile) {
            // This is the only file. Safe to obliterate the crypted file
            pavewayService.removeFile(cryptedFile); 
        }
    }
    
    
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.DispatchService#updateDetails(org.brekka.pegasus.core.model.Dispatch, org.brekka.xml.pegasus.v2.model.DetailsType)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public void updateDetails(Allocation allocation) {
        XmlEntity<AllocationDocument> updatedXml = allocation.getXml();
        Allocation latest = allocationDAO.retrieveById(allocation.getId());
        XmlEntity<AllocationDocument> updatedEntity = xmlEntityService.updateEntity(updatedXml, latest.getXml(), AllocationDocument.class);
        latest.setXml(updatedEntity);
        allocationDAO.update(latest);
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.AllocationService#retrieveDerivedFromListing(org.brekka.pegasus.core.model.Dispatch, org.brekka.commons.persistence.model.ListingCriteria)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public List<Allocation> retrieveDerivedFromListing(Dispatch derivedFrom, ListingCriteria listingCriteria) {
        List<Allocation> listing = allocationDAO.retrieveDerivedFromListing(derivedFrom, listingCriteria);
        return listing;
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.AllocationService#retrieveDerivedFromListingRowCount(org.brekka.pegasus.core.model.Dispatch)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public int retrieveDerivedFromListingRowCount(Dispatch derivedFrom) {
        return retrieveDerivedFromListingRowCount(derivedFrom);
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.AllocationService#retrievePopulatedDownloadEvents(org.brekka.pegasus.core.model.Allocation)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public List<FileDownloadEvent> retrievePopulatedDownloadEvents(Allocation allocation) {
        Allocation managed = allocationDAO.retrieveById(allocation.getId());
        Dispatch derivedFrom = managed.getDerivedFrom();
        decryptDocument(derivedFrom, false);
        List<FileDownloadEvent> downloads = eventService.retrieveFileDownloads(managed);
        
        List<AllocationFile> files = derivedFrom.getFiles();
        for (FileDownloadEvent fileDownloadEvent : downloads) {
            AllocationFile eventTransferFile = fileDownloadEvent.getTransferFile();
            for (AllocationFile dispatchTransferFile : files) {
                if (EntityUtils.identityEquals(dispatchTransferFile, eventTransferFile.getDerivedFrom())) {
                    // Copy XML to event transfer - just to provide the name. The UUID will be incorrect.
                    eventTransferFile.setXml(dispatchTransferFile.getXml());
                    break;
                }
            }
        }
        return downloads;
    }

//    /**
//     * Perform the de-allocation
//     * 
//     * @param bundle
//     */
//    @Override
//    @Transactional(propagation=Propagation.REQUIRED)
//    public void deallocateBundle(Bundle bundle) {
//        List<CryptedFile> fileList = cryptedFileDAO.retrieveByBundle(bundle);
//        for (CryptedFile bundleFile : fileList) {
//            // Bundle file id matches the crypted file id from paveway.
//            deallocateCryptedFile(bundleFile);
//        }
//        
//        // Clear the bundle XML
//        resourceStorageService.remove(bundle.getId());
//        bundleDAO.delete(bundle.getId());
//    }
//    
//    @Override
//    @Transactional(propagation=Propagation.REQUIRED)
//    public void deallocateCryptedFile(CryptedFile cryptedFile) {
//        cryptedFile = cryptedFileDAO.retrieveById(cryptedFile.getId());
//        List<CryptedPart> parts = cryptedFile.getParts();
//        for (CryptedPart cryptedPart : parts) {
//            UUID partId = cryptedPart.getId();
//            resourceStorageService.remove(partId);
//            cryptedPartDAO.delete(partId);
//        }
//        cryptedFileDAO.delete(cryptedFile.getId());
//    }
    
}
