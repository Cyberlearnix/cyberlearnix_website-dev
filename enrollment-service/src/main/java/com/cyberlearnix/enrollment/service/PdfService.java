package com.cyberlearnix.enrollment.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.util.Map;

@Service
public class PdfService {

    public byte[] generateReceiptPdf(Map<String, Object> data) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try {
            // A4 page with standard 36pt (0.5 inch / ~13mm) margins for perfect printing
            Document document = new Document(PageSize.A4, 36, 36, 36, 36);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // Font system matching corporate identity
            Font brandFont = new Font(Font.HELVETICA, 18, Font.BOLD, new Color(30, 58, 138)); // Dark Blue #1e3a8a
            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(15, 23, 42)); // Slate 900
            Font headerFont = new Font(Font.HELVETICA, 10, Font.BOLD, Color.WHITE);
            Font labelFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(71, 85, 105)); // Slate 600
            Font valueFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(15, 23, 42)); // Slate 900
            Font valueBoldFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(15, 23, 42));
            Font textFont = new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(100, 116, 139)); // Slate 500
            Font textBoldFont = new Font(Font.HELVETICA, 9, Font.BOLD, new Color(30, 58, 138));
            Font termsFont = new Font(Font.HELVETICA, 7, Font.NORMAL, new Color(100, 116, 139));
            Font termsHeaderFont = new Font(Font.HELVETICA, 8, Font.BOLD, new Color(71, 85, 105));

            // ─── HEADER SECTION (Logo left, Business details right) ───
            PdfPTable headerTable = new PdfPTable(2);
            headerTable.setWidthPercentage(100);
            headerTable.setWidths(new float[]{60f, 40f});
            headerTable.setSpacingAfter(10);

            // Left Cell: Logo
            PdfPCell logoCell = new PdfPCell();
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            try {
                ClassPathResource resource = new ClassPathResource("logo.png");
                if (resource.exists()) {
                    URL logoUrl = resource.getURL();
                    Image logo = Image.getInstance(logoUrl);
                    logo.scaleToFit(160, 55);
                    logoCell.addElement(logo);
                } else {
                    Paragraph altText = new Paragraph("CyberLearnix", brandFont);
                    logoCell.addElement(altText);
                }
            } catch (Exception e) {
                Paragraph altText = new Paragraph("CyberLearnix", brandFont);
                logoCell.addElement(altText);
            }
            headerTable.addCell(logoCell);

            // Right Cell: Company Details
            PdfPCell companyCell = new PdfPCell();
            companyCell.setBorder(Rectangle.NO_BORDER);
            companyCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            companyCell.setVerticalAlignment(Element.ALIGN_MIDDLE);

            Paragraph p1 = new Paragraph("CyberLearnix Private Limited", valueBoldFont);
            p1.setAlignment(Element.ALIGN_RIGHT);
            companyCell.addElement(p1);

            Paragraph p2 = new Paragraph("GSTIN: 36AAMCC8479N1ZN\nVanasthalipuram, Hyderabad, 500070\nEmail: support@cyberlearnix.com", textFont);
            p2.setAlignment(Element.ALIGN_RIGHT);
            companyCell.addElement(p2);
            
            headerTable.addCell(companyCell);
            document.add(headerTable);

            // Horizontal Line
            LineSeparator line = new LineSeparator(1.5f, 100, new Color(59, 130, 246), Element.ALIGN_CENTER, -2);
            document.add(line);
            document.add(new Paragraph(" "));

            // Title
            Paragraph title = new Paragraph("TAX INVOICE", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(15);
            document.add(title);

            // ─── BILL TO & INVOICE DETAILS ───
            PdfPTable detailsTable = new PdfPTable(2);
            detailsTable.setWidthPercentage(100);
            detailsTable.setWidths(new float[]{50f, 50f});
            detailsTable.setSpacingAfter(15);

            // Bill To Box (Left)
            PdfPCell billToCell = new PdfPCell();
            billToCell.setBackgroundColor(new Color(248, 250, 252)); // Slate 50
            billToCell.setBorderColor(new Color(241, 245, 249)); // Slate 100
            billToCell.setPadding(10);
            
            Paragraph bTitle = new Paragraph("BILL TO (STUDENT)", labelFont);
            bTitle.setSpacingAfter(5);
            billToCell.addElement(bTitle);

            billToCell.addElement(new Paragraph("Name: " + data.getOrDefault("studentName", "—"), valueBoldFont));
            billToCell.addElement(new Paragraph("Email: " + data.getOrDefault("studentEmail", "—"), valueFont));
            billToCell.addElement(new Paragraph("State: Telangana", valueFont)); // Defaults to Supply State
            detailsTable.addCell(billToCell);

            // Invoice Info Box (Right)
            PdfPCell infoCell = new PdfPCell();
            infoCell.setBackgroundColor(new Color(248, 250, 252));
            infoCell.setBorderColor(new Color(241, 245, 249));
            infoCell.setPadding(10);

            Paragraph iTitle = new Paragraph("INVOICE INFORMATION", labelFont);
            iTitle.setSpacingAfter(5);
            infoCell.addElement(iTitle);

            infoCell.addElement(new Paragraph("Invoice No: " + data.getOrDefault("receiptId", "—"), valueBoldFont));
            infoCell.addElement(new Paragraph("Date: " + data.getOrDefault("submittedAt", "—"), valueFont));
            infoCell.addElement(new Paragraph("Txn ID: " + data.getOrDefault("transactionId", "—"), valueFont));
            infoCell.addElement(new Paragraph("Payment Mode: " + data.getOrDefault("paymentMode", "Online"), valueFont));
            detailsTable.addCell(infoCell);

            document.add(detailsTable);

            // ─── MATH CALCULATIONS ───
            double totalPaid = getDouble(data.get("amountPaidVal"));
            double discount = getDouble(data.get("discountAmount"));
            double listPrice = totalPaid + discount;

            double subtotal = Math.round((listPrice / 1.18) * 100.0) / 100.0;
            double discountBase = Math.round((discount / 1.18) * 100.0) / 100.0;
            double taxableValue = Math.round((totalPaid / 1.18) * 100.0) / 100.0;
            double cgst = Math.round((taxableValue * 0.09) * 100.0) / 100.0;
            double sgst = cgst;
            double calculatedTotal = taxableValue + cgst + sgst;
            double roundOff = Math.round((totalPaid - calculatedTotal) * 100.0) / 100.0;

            // ─── SERVICES TABLE ───
            PdfPTable itemTable = new PdfPTable(4);
            itemTable.setWidthPercentage(100);
            itemTable.setWidths(new float[]{8f, 52f, 15f, 25f});
            itemTable.setSpacingAfter(15);

            // Headers
            addHeaderCell(itemTable, "S.No", headerFont);
            addHeaderCell(itemTable, "Description of Service", headerFont);
            addHeaderCell(itemTable, "SAC Code", headerFont);
            addHeaderCell(itemTable, "Amount (INR)", headerFont);

            // Row 1
            addBodyCell(itemTable, "1", valueFont, Element.ALIGN_CENTER);
            
            // Description block
            PdfPCell descCell = new PdfPCell();
            descCell.setBorderColor(new Color(226, 232, 240));
            descCell.setPadding(8);
            Paragraph descTitle = new Paragraph((String) data.getOrDefault("courseTitle", "Course Enrollment Fee"), valueBoldFont);
            Paragraph descSub = new Paragraph("Professional Skills Development Course Training.", textFont);
            descCell.addElement(descTitle);
            descCell.addElement(descSub);
            itemTable.addCell(descCell);

            addBodyCell(itemTable, "999293", valueFont, Element.ALIGN_CENTER);
            addBodyCell(itemTable, String.format("Rs.%,.2f", subtotal), valueFont, Element.ALIGN_RIGHT);

            document.add(itemTable);

            // ─── BANK DETAILS & FINANCIALS BREAKDOWN ───
            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{55f, 45f});
            summaryTable.setSpacingAfter(20);

            // Bank Details (Left)
            PdfPCell bankCell = new PdfPCell();
            bankCell.setBackgroundColor(new Color(248, 250, 252));
            bankCell.setBorderColor(new Color(241, 245, 249));
            bankCell.setPadding(10);
            
            Paragraph bankTitle = new Paragraph("BANK TRANSFER DETAILS", labelFont);
            bankTitle.setSpacingAfter(5);
            bankCell.addElement(bankTitle);
            
            Paragraph bankInfo = new Paragraph(
                "Bank Name: HDFC Bank\n" +
                "Account Name: CyberLearnix\n" +
                "Account Number: 50200087452361\n" +
                "IFSC Code: HDFC0000124\n" +
                "Branch: Vanasthalipuram", valueFont);
            bankCell.addElement(bankInfo);
            summaryTable.addCell(bankCell);

            // Financial Breakdown (Right)
            PdfPCell totalsCell = new PdfPCell();
            totalsCell.setBorder(Rectangle.NO_BORDER);
            
            PdfPTable tTable = new PdfPTable(2);
            tTable.setWidthPercentage(100);
            tTable.setWidths(new float[]{60f, 40f});

            addTotalRow(tTable, "Subtotal (Excl. Tax):", String.format("Rs.%,.2f", subtotal), labelFont, valueFont);
            
            if (discount > 0) {
                addTotalRow(tTable, "Discount (" + data.getOrDefault("couponCode", "Coupon") + "):", String.format("-Rs.%,.2f", discountBase), labelFont, valueFont);
                addTotalRow(tTable, "Taxable Value:", String.format("Rs.%,.2f", taxableValue), labelFont, valueBoldFont);
            }
            
            addTotalRow(tTable, "CGST @ 9%:", String.format("Rs.%,.2f", cgst), labelFont, valueFont);
            addTotalRow(tTable, "SGST @ 9%:", String.format("Rs.%,.2f", sgst), labelFont, valueFont);
            
            if (Math.abs(roundOff) > 0.0) {
                addTotalRow(tTable, "Round Off:", String.format("Rs.%,.2f", roundOff), labelFont, valueFont);
            }
            
            addTotalRow(tTable, "Total Paid:", String.format("Rs.%,.2f", totalPaid), labelFont, textBoldFont);

            totalsCell.addElement(tTable);
            summaryTable.addCell(totalsCell);

            document.add(summaryTable);

            // ─── TERMS AND SIGNATURE ───
            PdfPTable footerTable = new PdfPTable(2);
            footerTable.setWidthPercentage(100);
            footerTable.setWidths(new float[]{65f, 35f});
            footerTable.setSpacingBefore(10);

            // Terms
            PdfPCell termsCell = new PdfPCell();
            termsCell.setBorder(Rectangle.NO_BORDER);
            termsCell.addElement(new Paragraph("TERMS & CONDITIONS", termsHeaderFont));
            
            Paragraph termsList = new Paragraph(
                "1. This is a computer-generated tax invoice, requiring no physical signature.\n" +
                "2. All course enrollment fees paid are non-refundable and non-transferable.\n" +
                "3. Any disputes are subject to the exclusive jurisdiction of the courts in Hyderabad.", termsFont);
            termsCell.addElement(termsList);
            footerTable.addCell(termsCell);

            // Signature stamp (Seal only, no signature text/line)
            PdfPCell signCell = new PdfPCell();
            signCell.setBorder(Rectangle.NO_BORDER);
            signCell.setHorizontalAlignment(Element.ALIGN_CENTER);
            signCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            
            try {
                ClassPathResource resource = new ClassPathResource("seal.png");
                if (resource.exists()) {
                    Image sealImg = Image.getInstance(resource.getURL());
                    sealImg.scaleToFit(85, 85);
                    sealImg.setAlignment(Element.ALIGN_CENTER);
                    signCell.addElement(sealImg);
                }
            } catch (Exception e) {
                // If seal fails to load, ignore
            }
            
            footerTable.addCell(signCell);
            document.add(footerTable);

            document.close();

        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF receipt", e);
        }

        return outputStream.toByteArray();
    }

    private double getDouble(Object val) {
        if (val == null) return 0.0;
        if (val instanceof Number) {
            return ((Number) val).doubleValue();
        }
        try {
            return Double.parseDouble(val.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(30, 58, 138)); // Dark Blue #1e3a8a
        cell.setBorderColor(new Color(30, 58, 138));
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    private void addBodyCell(PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorderColor(new Color(226, 232, 240)); // Slate 200
        cell.setPadding(8);
        cell.setHorizontalAlignment(alignment);
        table.addCell(cell);
    }

    private void addTotalRow(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell lCell = new PdfPCell(new Phrase(label, labelFont));
        lCell.setBorder(Rectangle.NO_BORDER);
        lCell.setPadding(4);
        lCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        PdfPCell vCell = new PdfPCell(new Phrase(value, valueFont));
        vCell.setBorder(Rectangle.NO_BORDER);
        vCell.setPadding(4);
        vCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        table.addCell(lCell);
        table.addCell(vCell);
    }
}
