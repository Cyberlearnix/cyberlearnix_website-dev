package com.cyberlearnix.user.service;

import com.cyberlearnix.shared.entity.user.UserProfile;
import com.cyberlearnix.shared.repository.user.UserProfileRepository;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

@Service
public class EnrollmentCardService {

    @Autowired private UserProfileRepository userProfileRepository;

    private String publicUrl;

    @Value("${app.public-url:https://cyberlearnix.com}")
    public void setPublicUrl(String url) {
        if (url != null) {
            url = url.trim();
            url = url.replace("verify.cyberlearnix.com", "cyberlearnix.com");
            url = url.replace("verify.cyberlearnix", "cyberlearnix.com");
        }
        this.publicUrl = url;
    }

    private static final int QR_SIZE = 400;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Generates and saves enrollment number + QR code for a student profile.
     * Returns true if the card was successfully persisted; false otherwise.
     * On failure the in-memory profile is NOT mutated so callers never observe
     * a stale enrollment number that was never written to the database.
     */
    public boolean issueCard(UserProfile profile) {
        if (profile.getEnrollmentNumber() != null && !profile.getEnrollmentNumber().isBlank()) {
            return true;
        }
        String enrollmentNumber = null;
        String qrBase64 = null;
        try {
            enrollmentNumber = generateEnrollmentNumber();
            String base = publicUrl;
            if (base != null && base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String verifyUrl = base + "/verify.html?enrollment=" + enrollmentNumber;
            qrBase64 = generateQrCode(verifyUrl);

            // Set on the profile only just before saving so that a save failure
            // leaves the in-memory object in its original (no enrollment number) state.
            profile.setEnrollmentNumber(enrollmentNumber);
            profile.setQrCodeData(qrBase64);
            userProfileRepository.save(profile);
            return true;
        } catch (Exception e) {
            // Reset in-memory state so callers don't see a phantom enrollment number
            // that was never actually written to the database.
            profile.setEnrollmentNumber(null);
            profile.setQrCodeData(null);
            System.err.println("[EnrollmentCard] Failed to issue card for "
                    + profile.getEmail() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Regenerates and updates the QR code for an existing user profile using the sanitized public URL.
     */
    public void updateQrCode(UserProfile profile) {
        if (profile.getEnrollmentNumber() == null || profile.getEnrollmentNumber().isBlank()) {
            return;
        }
        try {
            String base = publicUrl;
            if (base != null && base.endsWith("/")) {
                base = base.substring(0, base.length() - 1);
            }
            String verifyUrl = base + "/verify.html?enrollment=" + profile.getEnrollmentNumber();
            String qrBase64 = generateQrCode(verifyUrl);
            profile.setQrCodeData(qrBase64);
            userProfileRepository.save(profile);
            System.out.println("[EnrollmentCard] Updated QR code for user profile " + profile.getEmail() + " using URL: " + verifyUrl);
        } catch (Exception e) {
            System.err.println("[EnrollmentCard] Failed to update QR code for " + profile.getEmail() + ": " + e.getMessage());
        }
    }

    /**
     * Generates CLX-YYYYMMDD-NNNN where NNNN is the next sequential card number.
     * Based on how many cards have already been issued (enrollment numbers assigned).
     * Student 1 → CLX-20260601-0001, student 2 → CLX-20260601-0002, etc.
     */
    private synchronized String generateEnrollmentNumber() {
        long issued = userProfileRepository.countByEnrollmentNumberIsNotNull();
        String date = LocalDate.now().format(DATE_FMT);
        return String.format("CLX-%s-%04d", date, issued + 1);
    }

    /**
     * Generates a QR code PNG with the Cyberlearnix logo overlaid in the center.
     * Returns a Base64-encoded data URI string.
     */
    private String generateQrCode(String content) throws WriterException, IOException {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H); // High error correction for logo overlay
        hints.put(EncodeHintType.MARGIN, 1);

        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);

        // Build QR image
        BufferedImage qrImage = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < QR_SIZE; x++) {
            for (int y = 0; y < QR_SIZE; y++) {
                qrImage.setRGB(x, y, bitMatrix.get(x, y) ? 0xFF1a2756 : 0xFFFFFFFF);
            }
        }

        // Overlay logo
        try {
            ClassPathResource logoResource = new ClassPathResource("logo.png");
            if (logoResource.exists()) {
                BufferedImage logo = ImageIO.read(logoResource.getInputStream());
                int logoSize = QR_SIZE / 5;          // 20% of QR code size
                int logoX = (QR_SIZE - logoSize) / 2;
                int logoY = (QR_SIZE - logoSize) / 2;

                // White rounded background for the logo
                BufferedImage overlay = new BufferedImage(QR_SIZE, QR_SIZE, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g2 = overlay.createGraphics();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int padding = 8;
                g2.setColor(Color.WHITE);
                g2.fill(new RoundRectangle2D.Float(
                    logoX - padding, logoY - padding,
                    logoSize + padding * 2, logoSize + padding * 2,
                    16, 16
                ));
                g2.drawImage(logo, logoX, logoY, logoSize, logoSize, null);
                g2.dispose();

                Graphics2D qrG = qrImage.createGraphics();
                qrG.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                qrG.drawImage(overlay, 0, 0, null);
                qrG.dispose();
            }
        } catch (Exception logoEx) {
            // Logo overlay failed — QR code still valid
            System.err.println("[EnrollmentCard] Logo overlay skipped: " + logoEx.getMessage());
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(qrImage, "PNG", baos);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }
}
