/*
 jBilling - The Enterprise Open Source Billing System
 Copyright (C) 2003-2011 Enterprise jBilling Software Ltd. and Emiliano Conde

 This file is part of jbilling.

 jbilling is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 jbilling is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with jbilling.  If not, see <http://www.gnu.org/licenses/>.

 This source was modified by Web Data Technologies LLP (www.webdatatechnologies.in) since 15 Nov 2015.
 You may download the latest source from webdataconsulting.github.io.

 */

package com.sapienter.jbilling.server.invoice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

;

import javax.sql.rowset.CachedRowSet;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PRAcroForm;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.SimpleBookmark;
import com.sapienter.jbilling.common.FormatLogger;
import com.sapienter.jbilling.common.SessionInternalError;
import com.sapienter.jbilling.common.Util;
import com.sapienter.jbilling.server.process.db.PaperInvoiceBatchDTO;
import com.sapienter.jbilling.server.invoice.db.InvoiceDTO;
import com.sapienter.jbilling.server.notification.NotificationBL;
import com.sapienter.jbilling.server.process.BillingProcessBL;
import com.sapienter.jbilling.server.process.db.PaperInvoiceBatchDAS;
import com.sapienter.jbilling.server.util.Constants;
import com.sapienter.jbilling.server.util.PreferenceBL;
import com.sapienter.jbilling.server.util.audit.EventLogger;

/**
 * @author Emil
 */
public class PaperInvoiceBatchBL {
    private PaperInvoiceBatchDTO batch = null;
    private static final FormatLogger LOG = new FormatLogger(PaperInvoiceBatchBL.class);
    private EventLogger eLogger = null;
    private PaperInvoiceBatchDAS batchHome = null;

    /** The maximum safe number of invoices to be batched.  */
    public static final Integer MAX_RESULTS = 10000;


    public PaperInvoiceBatchBL(Integer batchId) {
        init();
        set(batchId);
    }
    
    public PaperInvoiceBatchBL(PaperInvoiceBatchDTO batch) {
        init();
        this.batch = batch;
    }

    public PaperInvoiceBatchBL() {
        init();
    }

    private void init() {
        eLogger = EventLogger.getInstance();
        batchHome = new PaperInvoiceBatchDAS();
    }

    public PaperInvoiceBatchDTO getEntity() {
        return batch;
    }
    
    public void set(Integer id) {
        batch = batchHome.find(id);
    }

    /**
     * This method will create a record if there's none for the given
     * process id, otherwise it will return the existing one
     * @param processId
     * @return
     */
    public PaperInvoiceBatchDTO createGet(Integer processId) {
        BillingProcessBL process = new BillingProcessBL(processId);
        batch = process.getEntity().getPaperInvoiceBatch();
        if (batch == null) {
            Integer preferencePaperSelfDelivery = 
            	PreferenceBL.getPreferenceValueAsIntegerOrZero(
            		process.getEntity().getEntity().getId(), 
            		Constants.PREFERENCE_PAPER_SELF_DELIVERY);
            batch = batchHome.create(new Integer(0), preferencePaperSelfDelivery);
            process.getEntity().setPaperInvoiceBatch(batch);
        }
        return batch;
    }

    /**
     * Will take all the files generated by the process and 'paste' them
     * into a big one, deleting the originals.
     * This then will facilitate the printing of a batch.
     */
    public void compileInvoiceFilesForProcess(Integer entityId) 
            throws DocumentException, IOException {
        String filePrefix = Util.getSysProp("base_dir") + "invoices/" + 
            entityId + "-";
        // now go through each of the invoices
        // first - sort them
        List invoices = new ArrayList(batch.getInvoices());
        Collections.sort(invoices, new InvoiceEntityComparator());
        Integer[] invoicesIds = new Integer[invoices.size()];


        for (int f = 0; f < invoices.size(); f++) {
            InvoiceDTO invoice = (InvoiceDTO) invoices.get(f);
            invoicesIds[f] = invoice.getId();
        }
        
        compileInvoiceFiles(filePrefix, new Integer(batch.getId()).toString(), entityId,
                invoicesIds);
    }

    /**
     * Takes a list of invoices and replaces the individual PDF files for one
     * single PDF in the destination directory.
     * @param destination
     * @param prefix
     * @param entityId
     * @param invoices
     * @throws PdfFormatException
     * @throws IOException
     */
    public void compileInvoiceFiles(String destination, String prefix,
            Integer entityId, Integer[] invoices)
            throws DocumentException, IOException {

        String filePrefix = Util.getSysProp("base_dir") + "invoices/"
                + entityId + "-";
        String outFile = destination + prefix + "-batch.pdf";

        
        int pageOffset = 0;
        ArrayList master = new ArrayList();
        Document document = null;
        PdfCopy  writer = null;
        for(int f = 0; f < invoices.length ; f++) {
            // we create a reader for a certain document
            PdfReader reader = new PdfReader(filePrefix + invoices[f] + "-invoice.pdf");
            reader.consolidateNamedDestinations();
            // we retrieve the total number of pages
            int numberOfPages = reader.getNumberOfPages();
            List bookmarks = SimpleBookmark.getBookmark(reader);
            if (bookmarks != null) {
                if (pageOffset != 0)
                    SimpleBookmark.shiftPageNumbers(bookmarks, pageOffset, null);
                master.addAll(bookmarks);
            }
            pageOffset += numberOfPages;
            
            if (f == 0) {
                // step 1: creation of a document-object
                document = new Document(reader.getPageSizeWithRotation(1));
                // step 2: we create a writer that listens to the document
                writer = new PdfCopy(document, new FileOutputStream(outFile));
                // step 3: we open the document
                document.open();
            }
            // step 4: we add content
            PdfImportedPage page;
            for (int i = 0; i < numberOfPages; ) {
                ++i;
                page = writer.getImportedPage(reader, i);
                writer.addPage(page);
            }
            PRAcroForm form = reader.getAcroForm();
            if (form != null)
                writer.copyAcroForm(reader);
            
            //release and delete 
            writer.freeReader(reader);
            reader.close();
            File file = new File(filePrefix + invoices[f] + "-invoice.pdf");
            file.delete();
        }
        if (!master.isEmpty())
            writer.setOutlines(master);
        // step 5: we close the document
        if (document != null) {
            document.close();
        } else {
            LOG.warn("document == null");
        }

        LOG.debug("PDF batch file is ready %s", outFile);
    }

    
    public void sendEmail() {
        Integer entityId = batch.getProcess().getEntity().getId();
        
        int preferencePaperSelfDelivery = 
        	PreferenceBL.getPreferenceValueAsIntegerOrZero(entityId, Constants.PREFERENCE_PAPER_SELF_DELIVERY);
        
        Boolean selfDelivery = new Boolean(preferencePaperSelfDelivery == 1);
        // If the entity doesn't want to delivery the invoices, then
        // sapienter has to. Entity 1 is always sapienter.
        Integer pritingEntity;
        if (!selfDelivery.booleanValue()) {
            pritingEntity = new Integer(1);
        } else {
            pritingEntity = entityId;
        }
        try {
            NotificationBL.sendSapienterEmail(pritingEntity, "invoice_batch",
                    Util.getSysProp("base_dir") + "invoices/" + entityId + 
                    "-" + batch.getId() + "-batch.pdf", null);
        } catch (Exception e) {
            LOG.error("Could no send the email with the paper invoices " +
                    "for entity " + entityId, e);
        }
    }

    public String generateBatchPdf(List<InvoiceDTO> invoices, Integer entityId)
            throws SQLException,
            SessionInternalError, DocumentException,
            IOException {
        String realPath = Util.getSysProp("base_dir") + "invoices" + File.separator;

        Iterator<InvoiceDTO> iterator = invoices.iterator();
        NotificationBL notif = new NotificationBL();
        List<Integer> invoicesIdsList = new ArrayList<Integer>();

        int generated = 0;
        while (iterator.hasNext()) {
            Integer invoiceId = iterator.next().getId();
            InvoiceBL invoice = new InvoiceBL(invoiceId);
            LOG.debug("Generating paper invoice %d", invoiceId);
            notif.generatePaperInvoiceAsFile(invoice.getEntity());
            invoicesIdsList.add(invoiceId);

            // no more than 1000 invoices at a time, please
            generated++;
            if (generated >= 1000) break;
        }

        if (generated > 0) {
            // merge all these files into a single one
            String hash = String.valueOf(System.currentTimeMillis());
            Integer[] invoicesIds = new Integer[invoicesIdsList.size()];
            invoicesIdsList.toArray(invoicesIds);
            compileInvoiceFiles(realPath,
                    entityId + "-" + hash,
                    entityId,
                    invoicesIds);

            return entityId + "-" + hash + "-batch.pdf";
        } else {
            // there was no rows in that query ...
            return null;
        }
    }
    
    public String generateFile(CachedRowSet cachedRowSet, Integer entityId, 
            String realPath) throws SQLException,
            SessionInternalError, DocumentException,
            IOException {
        NotificationBL notif = new NotificationBL();
        List invoices = new ArrayList();

        int generated = 0;
        while (cachedRowSet.next()) {
            Integer invoiceId = new Integer(cachedRowSet.getInt(1));
            InvoiceBL invoice = new InvoiceBL(invoiceId);
            LOG.debug("Generating paper invoice %d", invoiceId);
            notif.generatePaperInvoiceAsFile(invoice.getEntity());
            invoices.add(invoiceId);
            
            // no more than 1000 invoices at a time, please
            generated++;
            if (generated >= 1000) break;
        }

        if (generated > 0) {
            // merge all these files into a single one
            String hash = String.valueOf(System.currentTimeMillis());
            Integer[] invoicesIds = new Integer[invoices.size()];
            invoices.toArray(invoicesIds);
            compileInvoiceFiles(realPath.substring(0, 
                    realPath.indexOf("_FILE_NAME_")) + "/", 
                    entityId + "-" + hash, entityId, invoicesIds);
    
            return entityId + "-" + hash + "-batch.pdf";
        } else {
            // there was no rows in that query ...
            return null;
        }
    }
    
}
