/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.brekka.pegasus.core.services.impl;

import java.util.Date;
import java.util.List;

import org.brekka.pegasus.core.dao.AgreementAcceptedEventDAO;
import org.brekka.pegasus.core.dao.BundleCreatedEventDAO;
import org.brekka.pegasus.core.dao.BundleUnlockEventDAO;
import org.brekka.pegasus.core.dao.FileDownloadEventDAO;
import org.brekka.pegasus.core.model.AgreementAcceptedEvent;
import org.brekka.pegasus.core.model.Allocation;
import org.brekka.pegasus.core.model.AllocationFile;
import org.brekka.pegasus.core.model.AuthenticatedMember;
import org.brekka.pegasus.core.model.FileDownloadEvent;
import org.brekka.pegasus.core.model.Member;
import org.brekka.pegasus.core.model.RemoteUserEvent;
import org.brekka.pegasus.core.model.Transfer;
import org.brekka.pegasus.core.model.TransferCreatedEvent;
import org.brekka.pegasus.core.model.TransferUnlockEvent;
import org.brekka.pegasus.core.model.XmlEntity;
import org.brekka.pegasus.core.security.WebAuthenticationDetails;
import org.brekka.pegasus.core.services.EventService;
import org.brekka.pegasus.core.services.MemberService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Captures events produced within Pegasus
 * 
 * TODO needs to be more generic. Perhaps hooked into the Spring event mechanism and using {@link XmlEntity} to store
 * event specific metadata.
 * 
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Service
@Transactional
public class EventServiceImpl implements EventService {

    @Autowired
    private FileDownloadEventDAO fileDownloadEventDAO;

    @Autowired
    private BundleUnlockEventDAO bundleUnlockEventDAO;

    @Autowired
    private BundleCreatedEventDAO bundleCreatedEventDAO;

    @Autowired
    private AgreementAcceptedEventDAO agreementAcceptedEventDAO;

    @Autowired
    private MemberService memberService;

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.services.EventService#bundleUnlocked(java.lang.String, java.lang.String,
     * java.lang.String, org.brekka.pegasus.core.model.Bundle, java.util.Date)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transferUnlock(Transfer transfer, boolean success) {
        TransferUnlockEvent event = new TransferUnlockEvent();
        event.setTransfer(transfer);
        event.setSuccess(success);
        populate(event);
        bundleUnlockEventDAO.create(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.services.EventService#beginFileDownloadEvent(java.lang.String, java.lang.String,
     * java.lang.String, java.util.UUID)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileDownloadEvent beginFileDownloadEvent(AllocationFile bundleFile) {
        FileDownloadEvent event = new FileDownloadEvent();
        event.setTransferFile(bundleFile);
        populate(event);
        fileDownloadEventDAO.create(event);
        return event;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.brekka.pegasus.core.services.EventService#fileDownloadCount(org.brekka.pegasus.core.model.AllocationFile,
     * org.brekka.pegasus.core.model.Transfer)
     */
    @Override
    @Transactional()
    public int fileDownloadCount(AllocationFile bundleFile, Transfer transfer) {
        return fileDownloadEventDAO.fileDownloadCount(bundleFile, transfer);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.services.EventService#isAccepted(org.brekka.pegasus.core.model.Bundle)
     */
    @Override
    @Transactional()
    public boolean isAccepted(Transfer transfer) {
        return retrieveAgreement(transfer) != null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.services.EventService#retrieveAgreement(org.brekka.pegasus.core.model.Transfer)
     */
    @Override
    @Transactional()
    public AgreementAcceptedEvent retrieveAgreement(Transfer transfer) {
        return agreementAcceptedEventDAO.retrieveByTransfer(transfer);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.services.EventService#bundleCreated(java.lang.String, java.lang.String,
     * java.lang.String, org.brekka.pegasus.core.model.Bundle)
     */
    @Override
    @Transactional()
    public void transferCreated(Transfer transfer) {
        TransferCreatedEvent event = new TransferCreatedEvent();
        event.setTransfer(transfer);
        populate(event);
        bundleCreatedEventDAO.create(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.services.EventService#completeEvent(org.brekka.pegasus.core.model.FileDownloadEvent)
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeEvent(FileDownloadEvent event) {
        event.setCompleted(new Date());
        populate(event);
        fileDownloadEventDAO.update(event);
    }

    @Override
    @Transactional()
    public void agreementAccepted(Transfer transfer) {
        AgreementAcceptedEvent event = new AgreementAcceptedEvent();
        event.setTransfer(transfer);
        populate(event);
        agreementAcceptedEventDAO.create(event);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.brekka.pegasus.core.services.EventService#retrieveFailedUnlockAttempts(org.brekka.pegasus.core.model.Transfer
     * )
     */
    @Override
    @Transactional(readOnly=true)
    public int retrieveFailedUnlockAttempts(Transfer transfer) {
        return bundleUnlockEventDAO.retrieveFailedUnlockAttempts(transfer);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.brekka.pegasus.core.services.EventService#retrieveUnlockAttempts(org.brekka.pegasus.core.model.Transfer)
     */
    @Override
    @Transactional(readOnly=true)
    public List<TransferUnlockEvent> retrieveUnlockAttempts(Transfer transfer) {
        return bundleUnlockEventDAO.retrieveAttempts(transfer);
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * org.brekka.pegasus.core.services.EventService#retrieveFileDownloads(org.brekka.pegasus.core.model.Allocation)
     */
    @Override
    @Transactional(readOnly=true)
    public List<FileDownloadEvent> retrieveFileDownloads(Allocation allocation) {
        return fileDownloadEventDAO.retrieveFileDownloads(allocation);
    }

    protected void populate(RemoteUserEvent remoteUserEvent) {
        remoteUserEvent.setInitiated(new Date());
        SecurityContext context = SecurityContextHolder.getContext();
        Authentication authentication = context.getAuthentication();
        Object details = authentication.getDetails();
        if (details instanceof WebAuthenticationDetails) {
            WebAuthenticationDetails wad = (WebAuthenticationDetails) details;
            remoteUserEvent.setOnBehalfOfAddress(wad.getOnBehalfOfAddress());
            remoteUserEvent.setRemoteAddress(wad.getRemoteAddress());
            remoteUserEvent.setUserAgent(wad.getUserAgent());
        } else {
            throw new IllegalStateException("No web authentication details found.");
        }

        AuthenticatedMember<Member> current = memberService.getCurrent(Member.class);
        if (current != null) {
            remoteUserEvent.setMember(current.getMember());
        }
    }

}
