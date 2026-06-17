package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.identity.Member;
import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

@Service
public class IdCardPdfService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] generateIdCardPdf(Member member) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        
        // Define Credit Card Size in Points (approx 2.2" x 3.4" = 158 x 244 points, 
        // but let's use 240 x 380 points for high-quality printing layout)
        Rectangle cardSize = new Rectangle(240, 380);
        Document document = new Document(cardSize, 12, 12, 12, 12);
        
        try {
            PdfWriter writer = PdfWriter.getInstance(document, baos);
            document.open();
            
            // --- FRONT SIDE (PAGE 1) ---
            drawFrontSide(document, writer, member);
            
            // New Page for Back Side
            document.newPage();
            
            // --- BACK SIDE (PAGE 2) ---
            drawBackSide(document, writer, member);
            
            document.close();
        } catch (Exception e) {
            System.err.println("Failed to generate PDF: " + e.getMessage());
            e.printStackTrace();
        }
        
        return baos.toByteArray();
    }

    private void drawFrontSide(Document document, PdfWriter writer, Member member) throws Exception {
        PdfContentByte cb = writer.getDirectContent();
        
        // Background - Dark Navy Top/Bottom Band
        cb.setColorFill(new Color(26, 39, 86)); // Navy #1a2756
        cb.rectangle(0, 330, 240, 50); // Header band
        cb.fill();
        
        cb.setColorFill(new Color(232, 25, 44)); // Red Accent Line
        cb.rectangle(0, 327, 240, 3);
        cb.fill();
        
        cb.setColorFill(new Color(248, 250, 252)); // Light slate body background
        cb.rectangle(0, 0, 240, 327);
        cb.fill();

        // Footer Band
        cb.setColorFill(new Color(26, 39, 86));
        cb.rectangle(0, 0, 240, 25);
        cb.fill();

        // 1. Logo and Company Name Header
        try {
            ClassPathResource logoResource = new ClassPathResource("logo.png");
            if (logoResource.exists()) {
                Image logo = Image.getInstance(logoResource.getURL());
                logo.scaleToFit(20, 20);
                logo.setAbsolutePosition(15, 345);
                document.add(logo);
            }
        } catch (Exception ignored) {}

        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 12);
        cb.setColorFill(Color.WHITE);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "CYBERLEARNIX", 42, 350, 0);
        cb.endText();

        // 2. Profile Photo
        float photoWidth = 75;
        float photoHeight = 75;
        float photoX = (240 - photoWidth) / 2;
        float photoY = 230;

        // White border under photo
        cb.setColorFill(Color.WHITE);
        cb.rectangle(photoX - 2, photoY - 2, photoWidth + 4, photoHeight + 4);
        cb.fill();

        try {
            Image photo = null;
            if (member.getProfilePhoto() != null && member.getProfilePhoto().startsWith("http")) {
                photo = Image.getInstance(new URL(member.getProfilePhoto()));
            } else if (member.getProfilePhoto() != null && member.getProfilePhoto().startsWith("data:image")) {
                String base64Data = member.getProfilePhoto().substring(member.getProfilePhoto().indexOf(",") + 1);
                photo = Image.getInstance(Base64.getDecoder().decode(base64Data));
            }
            
            if (photo != null) {
                photo.scaleAbsolute(photoWidth, photoHeight);
                photo.setAbsolutePosition(photoX, photoY);
                document.add(photo);
            } else {
                drawPhotoPlaceholder(cb, photoX, photoY, photoWidth, photoHeight, member.getFullName());
            }
        } catch (Exception e) {
            drawPhotoPlaceholder(cb, photoX, photoY, photoWidth, photoHeight, member.getFullName());
        }

        // 3. Member Name
        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 13);
        cb.setColorFill(new Color(26, 39, 86));
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, member.getFullName().toUpperCase(), 120, 205, 0);

        // 4. Designation / Role Type Badge
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 9);
        cb.setColorFill(new Color(100, 116, 139)); // Slate-500
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, member.getDesignation() != null ? member.getDesignation() : member.getMemberType(), 120, 192, 0);

        // 5. Member Details Table (ID, Dept, Type)
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.setColorFill(new Color(15, 23, 42)); // Slate-900
        
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "MEMBER ID:", 100, 175, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "DEPARTMENT:", 100, 162, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "ROLE TYPE:", 100, 149, 0);

        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, member.getMemberId(), 108, 175, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, member.getDepartment() != null ? member.getDepartment() : "Operations", 108, 162, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, member.getMemberType(), 108, 149, 0);
        cb.endText();

        // 6. QR Code
        if (member.getQrCodeUrl() != null && member.getQrCodeUrl().startsWith("data:image")) {
            try {
                String base64Data = member.getQrCodeUrl().substring(member.getQrCodeUrl().indexOf(",") + 1);
                Image qrCode = Image.getInstance(Base64.getDecoder().decode(base64Data));
                qrCode.scaleToFit(70, 70);
                qrCode.setAbsolutePosition(85, 55);
                document.add(qrCode);
            } catch (Exception ignored) {}
        }

        // Footer Text
        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.setColorFill(Color.WHITE);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "OFFICIAL IDENTITY BADGE", 120, 8, 0);
        cb.endText();
    }

    private void drawBackSide(Document document, PdfWriter writer, Member member) throws Exception {
        PdfContentByte cb = writer.getDirectContent();
        
        // Background - Dark Navy Frame
        cb.setColorFill(new Color(26, 39, 86)); // Navy #1a2756
        cb.rectangle(0, 0, 240, 380);
        cb.fill();

        // Inner Light Slate Box
        cb.setColorFill(new Color(248, 250, 252));
        cb.rectangle(10, 10, 220, 360);
        cb.fill();

        // Header Title
        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 10);
        cb.setColorFill(new Color(26, 39, 86));
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "VERIFICATION & DETAILS", 120, 345, 0);

        // Underline
        cb.endText();
        cb.setColorFill(new Color(232, 25, 44));
        cb.rectangle(90, 338, 60, 2);
        cb.fill();

        // Content
        cb.beginText();
        cb.setColorFill(new Color(15, 23, 42)); // Slate-900
        
        // Portal URL Info
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "PUBLIC VERIFICATION PORTAL", 120, 310, 0);
        cb.setFontAndSize(BaseFont.createFont(BaseFont.COURIER, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 7);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, member.getVerificationUrl(), 120, 298, 0);

        // Emergency Contact Details
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "EMERGENCY CONTACT", 120, 260, 0);
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "Phone: +91 99999 99999", 120, 248, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "Email: support@cyberlearnix.com", 120, 238, 0);

        // Date of issue & Validity
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "ISSUE DATE:", 110, 195, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_RIGHT, "VALIDITY:", 110, 182, 0);

        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, member.getJoiningDate().format(DATE_FMT), 118, 195, 0);
        cb.showTextAligned(PdfContentByte.ALIGN_LEFT, "5 YEARS FROM ISSUE", 118, 182, 0);

        // Terms of Use
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 8);
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "TERMS AND CONDITIONS", 120, 140, 0);
        cb.endText();

        // Multi-line terms rendering
        String termsText = "This identification card is non-transferable and remains the sole property of CyberLearnix Private Limited. The cardholder is required to present this card upon request by authorized personnel. If found, please return to CyberLearnix Private Limited, Hyderabad, India.";
        
        Paragraph p = new Paragraph(termsText, new Font(Font.HELVETICA, 6.5f, Font.NORMAL, new Color(71, 85, 105)));
        p.setAlignment(Element.ALIGN_CENTER);
        
        ColumnText ct = new ColumnText(cb);
        ct.setSimpleColumn(20, 40, 220, 130);
        ct.addElement(p);
        ct.go();

        // Footer signature line / authorization
        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 7);
        cb.setColorFill(new Color(15, 23, 42));
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, "AUTHORIZED SIGNATORY", 120, 22, 0);
        cb.endText();
    }

    private void drawPhotoPlaceholder(PdfContentByte cb, float x, float y, float w, float h, String name) throws Exception {
        cb.setColorFill(new Color(226, 232, 240)); // Slate-200
        cb.rectangle(x, y, w, h);
        cb.fill();

        // Show initials
        String initials = "";
        if (name != null && !name.isBlank()) {
            String[] parts = name.split(" ");
            if (parts.length > 0 && !parts[0].isEmpty()) initials += parts[0].substring(0, 1).toUpperCase();
            if (parts.length > 1 && !parts[1].isEmpty()) initials += parts[1].substring(0, 1).toUpperCase();
        }
        if (initials.isEmpty()) initials = "CLX";

        cb.beginText();
        cb.setFontAndSize(BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.CP1252, BaseFont.NOT_EMBEDDED), 20);
        cb.setColorFill(new Color(71, 85, 105)); // Slate-600
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER, initials, x + (w / 2), y + (h / 2) - 6, 0);
        cb.endText();
    }
}
