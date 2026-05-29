package com.cyberlearnix.enrollment.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.pdf.draw.LineSeparator;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.util.Map;

@Service
public class PdfService {

    public byte[] generateReceiptPdf(Map<String, Object> data) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, outputStream);
            document.open();

            // Header
            Font headerFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 12, Font.NORMAL);
            Font labelFont = new Font(Font.HELVETICA, 11, Font.BOLD);
            
            Paragraph header = new Paragraph("Payment Receipt - CyberLearnix", headerFont);
            header.setAlignment(Element.ALIGN_CENTER);
            header.setSpacingAfter(20);
            document.add(header);

            // Receipt Number
            Paragraph receiptNumber = new Paragraph("Receipt Number: " + data.get("receiptId"), titleFont);
            receiptNumber.setAlignment(Element.ALIGN_CENTER);
            receiptNumber.setSpacingAfter(10);
            document.add(receiptNumber);

            // Date
            Paragraph date = new Paragraph("Date: " + data.get("submittedAt"), normalFont);
            date.setAlignment(Element.ALIGN_CENTER);
            date.setSpacingAfter(30);
            document.add(date);

            // Line separator
            LineSeparator line = new LineSeparator();
            document.add(line);
            document.add(new Paragraph(" "));

            // Create table for details
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(20);
            table.setSpacingAfter(20);

            // Add cells
            addTableCell(table, "Student Name:", (String) data.get("studentName"), labelFont, normalFont);
            addTableCell(table, "Student Email:", (String) data.get("studentEmail"), labelFont, normalFont);
            addTableCell(table, "Course Title:", (String) data.get("courseTitle"), labelFont, normalFont);
            addTableCell(table, "Amount Paid:", (String) data.get("amountPaid"), labelFont, normalFont);
            addTableCell(table, "Transaction ID:", (String) data.get("transactionId"), labelFont, normalFont);
            addTableCell(table, "PayU Reference ID:", (String) data.get("payuRefId"), labelFont, normalFont);
            addTableCell(table, "Payment Mode:", (String) data.get("paymentMode"), labelFont, normalFont);
            addTableCell(table, "Bank Reference No:", (String) data.get("bankRefNo"), labelFont, normalFont);
            addTableCell(table, "Payment Status:", (String) data.get("paymentStatus"), labelFont, normalFont);

            document.add(table);

            // Status indicator
            String status = (String) data.get("paymentStatus");
            Paragraph statusPara = new Paragraph("Status: " + status, titleFont);
            statusPara.setAlignment(Element.ALIGN_CENTER);
            
            if ("PAID".equalsIgnoreCase(status)) {
                statusPara.setAlignment(Element.ALIGN_CENTER);
            }
            statusPara.setSpacingBefore(20);
            statusPara.setSpacingAfter(20);
            document.add(statusPara);

            // Footer
            document.add(line);
            document.add(new Paragraph(" "));
            
            Paragraph footer = new Paragraph("Thank you for your payment! If you have any questions, please contact support.", normalFont);
            footer.setAlignment(Element.ALIGN_CENTER);
            footer.setSpacingAfter(10);
            document.add(footer);

            Paragraph contact = new Paragraph("Email: support@cyberlearnix.com | Phone: +91-XXXXXXXXXX", normalFont);
            contact.setAlignment(Element.ALIGN_CENTER);
            document.add(contact);

            document.close();

        } catch (Exception e) {
            throw new RuntimeException("Error generating PDF receipt", e);
        }

        return outputStream.toByteArray();
    }

    private void addTableCell(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setBorderColor(Color.WHITE);
        labelCell.setPadding(8);
        
        PdfPCell valueCell = new PdfPCell(new Phrase(value != null ? value : "N/A", valueFont));
        valueCell.setBorderColor(Color.WHITE);
        valueCell.setPadding(8);
        
        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}
