/**
 * 
 */
package org.brekka.pegasus.core.services.impl;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.apache.xmlbeans.XmlException;
import org.brekka.paveway.core.model.FileBuilder;
import org.brekka.pegasus.core.PegasusErrorCode;
import org.brekka.pegasus.core.PegasusException;
import org.brekka.pegasus.core.dao.DepositDAO;
import org.brekka.pegasus.core.dao.InboxDAO;
import org.brekka.pegasus.core.model.AuthenticatedMember;
import org.brekka.pegasus.core.model.Bundle;
import org.brekka.pegasus.core.model.Deposit;
import org.brekka.pegasus.core.model.Inbox;
import org.brekka.pegasus.core.model.InboxTransferKey;
import org.brekka.pegasus.core.model.Member;
import org.brekka.pegasus.core.model.OpenVault;
import org.brekka.pegasus.core.model.Token;
import org.brekka.pegasus.core.model.Vault;
import org.brekka.pegasus.core.services.InboxService;
import org.brekka.pegasus.core.services.MemberService;
import org.brekka.pegasus.core.services.TokenService;
import org.brekka.pegasus.core.services.VaultService;
import org.brekka.phalanx.api.beans.IdentityPrincipal;
import org.brekka.phalanx.api.model.CryptedData;
import org.brekka.phoenix.CryptoFactory;
import org.brekka.xml.pegasus.v1.model.BundleDocument;
import org.brekka.xml.pegasus.v1.model.BundleType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Service
@Transactional
public class InboxServiceImpl extends PegasusServiceSupport implements InboxService {

    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private InboxDAO inboxDAO;
    
    @Autowired
    private DepositDAO depositDAO;
    
    @Autowired
    private MemberService memberService;
    
    @Autowired
    private VaultService vaultService;
    
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.InboxService#createInbox(java.lang.String, org.brekka.pegasus.core.model.Vault)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public Inbox createInbox(String name, String introduction, String inboxToken, Vault vault) {
        Inbox inbox = new Inbox();
        Token token = tokenService.createForInbox(inboxToken);
        inbox.setToken(token);
        inbox.setIntroduction(introduction);
        inbox.setVault(vault);
        inbox.setName(name);
        AuthenticatedMember authenticatedMember = memberService.getCurrent();
        Member member = authenticatedMember.getMember();
        inbox.setOwner(member);
        inboxDAO.create(inbox);
        return inbox;
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.InboxService#depositFiles(java.lang.String, java.lang.String, java.util.List)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public InboxTransferKey depositFiles(Inbox inbox, String reference, String comment, List<FileBuilder> fileBuilders) {
        // Bring the inbox under management
        inbox = inboxDAO.retrieveById(inbox.getId());
        Vault vault = inbox.getVault();
        UUID principalId = vault.getPrincipalId();
        
        Bundle bundleModel = new Bundle();
        bundleModel.setId(UUID.randomUUID());
        
        BundleDocument bundleDocument = prepareBundleDocument(comment, fileBuilders);
        BundleType bundleType = bundleDocument.getBundle();
        bundleType.setReference(reference);
        
        // Fetch the default crypto factory, generate a new secret key
        CryptoFactory defaultCryptoFactory = cryptoFactoryRegistry.getDefault();
        SecretKey secretKey = defaultCryptoFactory.getSymmetric().getKeyGenerator().generateKey();
        bundleModel.setProfile(defaultCryptoFactory.getProfileId());
        
        encryptBundleDocument(bundleDocument, bundleModel, secretKey);
        
        CryptedData cryptedData = phalanxService.asymEncrypt(secretKey.getEncoded(), new IdentityPrincipal(principalId));
        bundleModel.setCryptedDataId(cryptedData.getId());
        bundleDAO.create(bundleModel);
        
        Deposit deposit = new Deposit();
        deposit.setBundle(bundleModel);
        deposit.setCreated(new Date());
        deposit.setInbox(inbox);
        deposit.setVault(inbox.getVault());
        
        depositDAO.create(deposit);
        
        return new InboxTransferKeyImp(inbox, fileBuilders.size());
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.InboxService#retrieveForToken(java.lang.String)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public Inbox retrieveForToken(String inboxToken) {
        Token token = tokenService.retrieveByPath(inboxToken);
        return inboxDAO.retrieveByToken(token);
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.InboxService#unlock(org.brekka.pegasus.core.model.Deposit)
     */
    @Override
    public BundleType unlock(Deposit deposit) {
        Bundle bundle = deposit.getBundle();
        UUID cryptedDataId = bundle.getCryptedDataId();
        
        AuthenticatedMember current = memberService.getCurrent();
        OpenVault activeVault = current.getActiveVault();
        
        byte[] secretKeyBytes = vaultService.releaseKey(cryptedDataId, activeVault);
        
        try {
            return decryptBundle(null, bundle, secretKeyBytes);
        } catch (XmlException | IOException e) {
            throw new PegasusException(PegasusErrorCode.PG200, e, 
                    "Failed to retrieve bundle XML for deposit '%s'" , deposit.getId());
        }
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.InboxService#retrieveForMember()
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public List<Inbox> retrieveForMember() {
        AuthenticatedMember authenticatedMember = memberService.getCurrent();
        return inboxDAO.retrieveForMember(authenticatedMember.getMember());
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.InboxService#retrieveDeposits(org.brekka.pegasus.core.model.Inbox)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public List<Deposit> retrieveDeposits(Inbox inbox) {
        return depositDAO.retrieveByInbox(inbox);
    }
}