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

import java.io.StringWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.PostConstruct;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.brekka.commons.persistence.model.ListingCriteria;
import org.brekka.commons.persistence.model.OrderByPart;
import org.brekka.commons.persistence.model.OrderByProperty;
import org.brekka.pegasus.core.PegasusErrorCode;
import org.brekka.pegasus.core.PegasusException;
import org.brekka.pegasus.core.dao.TemplateDAO;
import org.brekka.pegasus.core.model.KeySafe;
import org.brekka.pegasus.core.model.Template;
import org.brekka.pegasus.core.model.TemplateEngine;
import org.brekka.pegasus.core.model.Token;
import org.brekka.pegasus.core.model.XmlEntity;
import org.brekka.pegasus.core.services.TemplateService;
import org.brekka.pegasus.core.services.XmlEntityService;
import org.brekka.xml.pegasus.v2.model.ExportedTemplateType;
import org.brekka.xml.pegasus.v2.model.ExportedTemplatesDocument;
import org.brekka.xml.pegasus.v2.model.ExportedTemplatesDocument.ExportedTemplates;
import org.brekka.xml.pegasus.v2.model.TemplateDocument;
import org.brekka.xml.pegasus.v2.model.TemplateType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ClassUtils;

/**
 * Standard implementation of the template service.
 *
 * @author Andrew Taylor (andrew@brekka.org)
 */
@Service
@Transactional
public class TemplateServiceImpl implements TemplateService {

    private static final Log log = LogFactory.getLog(TemplateServiceImpl.class);

    @Autowired
    private TemplateDAO templateDAO;

    @Autowired
    private XmlEntityService xmlEntityService;


    public Map<TemplateEngine, TemplateEngineAdapter> adapters;

    @PostConstruct
    public void init() {
        if (this.adapters != null) {
            // externally configured
            return;
        }
        Map<TemplateEngine, TemplateEngineAdapter> adapters = new LinkedHashMap<>();
        if (ClassUtils.isPresent("org.apache.velocity.app.VelocityEngine", getClass().getClassLoader())) {
            // Velocity is present, add it
            TemplateEngineAdapter templateEngineAdapter = new org.brekka.pegasus.core.services.impl.VelocityTemplateEngine();
            templateEngineAdapter.init(this.templateDAO, this.xmlEntityService);
            adapters.put(TemplateEngine.VELOCITY, templateEngineAdapter);
        }
        this.adapters = adapters;
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#merge(org.brekka.pegasus.core.model.Template, java.util.Map)
     */
    @Override
    @Nullable
    public String merge(@Nonnull final Template template, @Nonnull final Map<String, Object> context) {
        StringWriter writer = new StringWriter();
        merge(template, context, writer);
        return writer.toString();
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#merge(org.brekka.pegasus.core.model.Template, java.util.Map, java.io.Writer)
     */
    @Override
    public void merge(final Template template, final Map<String, Object> context, final Writer out) {
        TemplateEngineAdapter templateEngineAdapter = this.adapters.get(template.getEngine());
        if (templateEngineAdapter != null) {
            templateEngineAdapter.merge(template, context, out);
        } else {
            throw new PegasusException(PegasusErrorCode.PG773,
                    "No logic configured for engine '%s'", template.getEngine());
        }
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#preview(java.lang.String, org.brekka.pegasus.core.model.TemplateEngine, java.util.Map)
     */
    @Override
    @Nullable
    public String preview(final String templateContent, final TemplateEngine templateEngine, final Map<String, Object> context) {
        StringWriter writer = new StringWriter();
        preview(templateContent, templateEngine, context, writer);
        return writer.toString();
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#preview(java.lang.String, org.brekka.pegasus.core.model.TemplateEngine, java.util.Map, java.io.Writer)
     */
    @Override
    public void preview(final String templateContent, final TemplateEngine templateEngine, final Map<String, Object> context, final Writer out) {
        TemplateEngineAdapter templateEngineAdapter = this.adapters.get(templateEngine);
        if (templateEngineAdapter != null) {
            templateEngineAdapter.preview(templateContent, context, out);
        } else {
            throw new PegasusException(PegasusErrorCode.PG773,
                    "No logic configured for engine '%s'", templateEngine);
        }
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#retrieveByToken(org.brekka.pegasus.core.model.Token)
     */
    @Override
    @Nullable
    @Transactional(readOnly=true)
    public Template retrieveByToken(@Nonnull final Token token) {
        return this.templateDAO.retrieveByToken(token);
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#retrieveBySlug(java.lang.String)
     */
    @Override
    @Nullable
    @Transactional(readOnly=true)
    public Template retrieveBySlug(@Nonnull final String slug) {
        return this.templateDAO.retrieveBySlug(slug);
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#retrieveById(java.util.UUID)
     */
    @Override
    @Nullable
    @Transactional(readOnly=true)
    public Template retrieveById(@Nonnull final UUID templateId) {
        return this.templateDAO.retrieveById(templateId);
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#create(org.brekka.xml.pegasus.v2.model.TemplateType, java.lang.String, org.brekka.pegasus.core.model.Token)
     */
    @Override
    @Nonnull
    @Transactional()
    public Template create(@Nonnull final TemplateType details, @Nonnull final TemplateEngine engine, @Nullable final KeySafe<?> keySafe,
            @Nullable final String slug, @Nullable final Token token, @Nullable final String label) {
        return create(details, engine, keySafe, slug, token, label, false);
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#update(org.brekka.pegasus.core.model.Template)
     */
    @Override
    @Transactional(isolation=Isolation.SERIALIZABLE)
    public void update(@Nonnull final Template template) {
        Template managed = this.templateDAO.retrieveById(template.getId());
        fixDetails(template.getXml().getBean().getTemplate());
        XmlEntity<TemplateDocument> incoming = template.getXml();
        XmlEntity<TemplateDocument> current = managed.getXml();
        XmlEntity<TemplateDocument> xml = this.xmlEntityService.updateEntity(incoming, current, TemplateDocument.class);
        managed.setXml(xml);
        // Allow the caller to update the slug/token.
        managed.setSlug(template.getSlug());
        managed.setToken(template.getToken());
        managed.setEngine(template.getEngine());
        managed.setLabel(template.getLabel());
        managed.setImported(template.getImported());

        this.templateDAO.update(managed);
        template.setXml(xml);
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#delete(java.util.UUID)
     */
    @Override
    @Transactional()
    public void delete(final UUID templateId) {
        this.templateDAO.delete(templateId);
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#retrieveListing(org.brekka.commons.persistence.model.ListingCriteria)
     */
    @Override
    @Transactional(readOnly=true)
    public List<Template> retrieveListing(final ListingCriteria listingCriteria) {
        return this.templateDAO.retrieveListing(listingCriteria);
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#retrieveListingRowCount()
     */
    @Override
    @Transactional(readOnly=true)
    public int retrieveListingRowCount() {
        return this.templateDAO.retrieveListingRowCount();
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#exportAll()
     */
    @Override
    @Transactional(readOnly=true)
    public ExportedTemplatesDocument exportAll() {
        int count = this.templateDAO.retrieveListingRowCount();
        List<Template> listing = retrieveListing(new ListingCriteria(0, count, Arrays.<OrderByPart>asList(new OrderByProperty("created", false))));
        ExportedTemplatesDocument doc = ExportedTemplatesDocument.Factory.newInstance();
        ExportedTemplates templates = doc.addNewExportedTemplates();
        for (Template template : listing) {
            this.xmlEntityService.release(template, TemplateDocument.class);
            TemplateType templateXml = template.getXml().getBean().getTemplate();
            ExportedTemplateType exportedTemplate = templates.addNewExportedTemplate();
            exportedTemplate.setSlug(template.getSlug());
            exportedTemplate.setPlainLabel(template.getLabel());
            exportedTemplate.setLabel(templateXml.getLabel());
            exportedTemplate.setContent(templateXml.getContent());
            exportedTemplate.setDocumentation(templateXml.getDocumentation());
            exportedTemplate.setEngine(template.getEngine().toString());
            exportedTemplate.setExampleVariables(templateXml.getExampleVariables());
            exportedTemplate.setEncrypt(template.getXml().getCryptedDataId() != null);
        }
        return doc;
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#importFrom(org.brekka.xml.pegasus.v2.model.ExportedTemplatesDocument)
     */
    @Override
    @Transactional()
    public int importFrom(final ExportedTemplatesDocument exportedTemplatesDocument, final KeySafe<?> keySafe) {
        int count = 0;
        ExportedTemplates exportedTemplates = exportedTemplatesDocument.getExportedTemplates();
        List<ExportedTemplateType> exportedTemplateList = exportedTemplates.getExportedTemplateList();
        for (ExportedTemplateType exportedTemplateType : exportedTemplateList) {
            String slug = exportedTemplateType.getSlug();
            TemplateType template = TemplateType.Factory.newInstance();
            template.setLabel(exportedTemplateType.getLabel());
            template.setContent(exportedTemplateType.getContent());
            template.setDocumentation(exportedTemplateType.getDocumentation());
            template.setExampleVariables(exportedTemplateType.getExampleVariables());
            TemplateEngine templateEngine = TemplateEngine.valueOf(exportedTemplateType.getEngine());
            Template existing = this.templateDAO.retrieveBySlug(slug);
            if (existing == null) {
                KeySafe<?> keySafeForCreate = (exportedTemplateType.getEncrypt() ? keySafe : null);
                create(template, templateEngine, keySafeForCreate, slug, null, exportedTemplateType.getPlainLabel(), true);
                count++;
            } else if (BooleanUtils.isTrue(existing.getImported())) {
                if (existing.getXml().getCryptedDataId() != null) {
                    // We are unable to update encrypted templates, unable to decrypt.
                    continue;
                }
                // The template was originally imported, we can update it.
                existing.setEngine(templateEngine);
                existing.setLabel(exportedTemplateType.getLabel());
                this.xmlEntityService.release(existing, TemplateDocument.class);
                XmlEntity<TemplateDocument> xml = existing.getXml();
                TemplateType newXml = xml.getBean().getTemplate();
                newXml.setContent(exportedTemplateType.getContent());
                newXml.setDocumentation(exportedTemplateType.getDocumentation());
                newXml.setExampleVariables(exportedTemplateType.getExampleVariables());
                newXml.setLabel(exportedTemplateType.getLabel());
                update(existing);
                if (xml.getVersion() != existing.getXml().getVersion()) {
                    count++;
                }
            }
        }
        return count;
    }

    /* (non-Javadoc)
     * @see org.brekka.pegasus.core.services.TemplateService#getAvailableEngines()
     */
    @Override
    public Set<TemplateEngine> getAvailableEngines() {
        Set<TemplateEngine> engines = EnumSet.copyOf(this.adapters.keySet());
        return engines;
    }

    protected Template create(@Nonnull final TemplateType details, @Nonnull final TemplateEngine engine, @Nullable final KeySafe<?> keySafe,
            @Nullable final String slug, @Nullable final Token token, @Nullable final String label, final boolean imported) {
        Template template = new Template();

        fixDetails(details);
        TemplateDocument templateDocument = TemplateDocument.Factory.newInstance();
        templateDocument.setTemplate(details);

        XmlEntity<TemplateDocument> xml;
        if (keySafe == null) {
            xml = this.xmlEntityService.persistPlainEntity(templateDocument, false);
        } else {
            xml = this.xmlEntityService.persistEncryptedEntity(templateDocument, keySafe, false);
        }
        template.setEngine(engine);
        template.setSlug(slug);
        template.setToken(token);
        template.setXml(xml);
        template.setLabel(label);
        template.setImported(imported);
        this.templateDAO.create(template);
        return template;
    }

    /**
     * @param details
     */
    protected void fixDetails(final TemplateType details) {
        if (details == null) {
            return;
        }
        if (details.isSetDocumentation()
                && details.getDocumentation() == null) {
            details.unsetDocumentation();
        }
        if (details.getContent() == null) {
            details.setContent(StringUtils.EMPTY);
        }
    }

    /**
     * @param adapters the adapters to set
     */
    public void setAdapters(final Map<TemplateEngine, TemplateEngineAdapter> adapters) {
        this.adapters = adapters;
    }
}
