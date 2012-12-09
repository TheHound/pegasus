/**
 * 
 */
package org.brekka.pegasus.core.services.impl;

import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.brekka.pegasus.core.dao.AssociateDAO;
import org.brekka.pegasus.core.dao.DivisionAssociateDAO;
import org.brekka.pegasus.core.dao.DivisionDAO;
import org.brekka.pegasus.core.dao.OrganizationDAO;
import org.brekka.pegasus.core.model.ActorStatus;
import org.brekka.pegasus.core.model.Associate;
import org.brekka.pegasus.core.model.DivisionAssociate;
import org.brekka.pegasus.core.model.DomainName;
import org.brekka.pegasus.core.model.EMailAddress;
import org.brekka.pegasus.core.model.Member;
import org.brekka.pegasus.core.model.Organization;
import org.brekka.pegasus.core.model.Token;
import org.brekka.pegasus.core.model.TokenType;
import org.brekka.pegasus.core.model.Vault;
import org.brekka.pegasus.core.model.XmlEntity;
import org.brekka.pegasus.core.services.DivisionService;
import org.brekka.pegasus.core.services.EMailAddressService;
import org.brekka.pegasus.core.services.MemberService;
import org.brekka.pegasus.core.services.OrganizationService;
import org.brekka.pegasus.core.services.TokenService;
import org.brekka.pegasus.core.services.VaultService;
import org.brekka.pegasus.core.services.XmlEntityService;
import org.brekka.phalanx.api.model.KeyPair;
import org.brekka.xml.pegasus.v2.model.OrganizationDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Service
@Transactional
public class OrganizationServiceImpl implements OrganizationService {

    @Autowired
    private DivisionDAO divisionDAO;
    
    @Autowired
    private OrganizationDAO organizationDAO;
    
    @Autowired
    private AssociateDAO associateDAO;
    
    @Autowired
    private DivisionAssociateDAO divisionAssociateDAO;
    
    @Autowired
    private EMailAddressService eMailAddressService;
    
    @Autowired
    private TokenService tokenService;
    
    @Autowired
    private VaultService vaultService;
    
    @Autowired
    private MemberService memberService;
    
    @Autowired
    private XmlEntityService xmlEntityService;
    
    @Autowired
    private DivisionService divisionService;
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.OrganizationService#createOrganization(java.lang.String, java.lang.String, java.lang.String, org.brekka.pegasus.core.model.Vault)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public DivisionAssociate createOrganization(String name, String tokenStr, String domainNameStr, 
            String ownerEmailStr, OrganizationDocument organizationDocument, Vault connectedTo, UUID idToAssign) {
        Organization organization = new Organization();
        organization.setId(idToAssign);
        organization.setName(name);
        
        if (StringUtils.isNotBlank(domainNameStr)) {
            DomainName domainName = eMailAddressService.toDomainName(domainNameStr);
            organization.setPrimaryDomainName(domainName);
        }
        Token token = tokenService.createToken(tokenStr, TokenType.ORG);
        organization.setToken(token);
        
        XmlEntity<OrganizationDocument> entity = xmlEntityService.persistEncryptedEntity(organizationDocument, connectedTo);
        organization.setXml(entity);
        
        organizationDAO.create(organization);
        
        Member owner = connectedTo.getOwner();
        
        
        // Create a new key pair (not currently used for anything).
        KeyPair organizationKeyPair = vaultService.createKeyPair(connectedTo);
        
        // Add current user as an associate
        Associate associate = new Associate();
        associate.setOrganization(organization);
        associate.setStatus(ActorStatus.ACTIVE);
        associate.setMember(owner);
        if (StringUtils.isNotBlank(ownerEmailStr)) {
            EMailAddress ownerEMail = eMailAddressService.createEMail(ownerEmailStr, owner, false);
            associate.setPrimaryEMailAddress(ownerEMail);
        }
        associate.setDefaultVault(connectedTo);
        associate.setKeyPairId(organizationKeyPair.getId());
        associateDAO.create(associate);
        
        DivisionAssociate divisionAssociate = divisionService.createRootDivision(associate, connectedTo, "top", "Top");
        return divisionAssociate;
    }
    
    
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public void updateOrganizationDetails(UUID orgId, XmlEntity<OrganizationDocument> orgXml) {
        Organization organization = organizationDAO.retrieveById(orgId);
        XmlEntity<OrganizationDocument> entity = xmlEntityService.updateEntity(orgXml, organization.getXml(), OrganizationDocument.class);
        organization.setXml(entity);
        organizationDAO.update(organization);
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.OrganizationService#retrieveById(java.util.UUID)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public Organization retrieveById(UUID orgId, boolean releaseXml) {
        Organization organization = organizationDAO.retrieveById(orgId);
        if (organization == null) {
            return null;
        }
        if (organization.getXml() != null && releaseXml) {
            XmlEntity<OrganizationDocument> entity = xmlEntityService.retrieveEntity(organization.getXml().getId(), OrganizationDocument.class);
            organization.setXml(entity);
        }
        return organization;
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.OrganizationService#exists(java.util.UUID)
     */
    @Override
    public boolean exists(UUID orgId) {
        return organizationDAO.retrieveById(orgId) != null;
    }
    
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.OrganizationService#retrieveByToken(java.lang.String)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public Organization retrieveByToken(String tokenPath) {
        Token token = tokenService.retrieveByPath(tokenPath);
        return organizationDAO.retrieveByToken(token);
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.OrganizationService#retrieveAssociate(org.brekka.pegasus.core.model.Organization, org.brekka.pegasus.core.model.Member)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public Associate retrieveAssociate(Organization organization, Member member) {
        Associate associate = associateDAO.retrieveByOrgAndMember(organization, member);
        return associate;
    }
    
    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.OrganizationService#retrieveAssociates(org.brekka.pegasus.core.model.KeySafe)
     */
    @Override
    @Transactional(propagation=Propagation.REQUIRED)
    public List<Associate> retrieveAssociates(Vault vault) {
        List<Associate> asociateList = associateDAO.retrieveAssociatesInVault(vault);
        return asociateList;
    }
}
