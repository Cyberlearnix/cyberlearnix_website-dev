package com.cyberlearnix.enrollment.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private PdfService pdfService;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public void sendFailureEmail(String to, String studentName, String txnid, String errorMsg) {
        if (!isValidEmail(to)) {
            throw new IllegalArgumentException("Invalid email address: " + to);
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false);
            helper.setTo(to);
            helper.setSubject("Payment Failed - CyberLearnix");
            helper.setText(buildFailureHtml(studentName, txnid, errorMsg), true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send failure email", e);
        }
    }

    public void sendReceiptEmail(String to, String subject, String template, Map<String, Object> data) {
        if (!isValidEmail(to)) {
            throw new IllegalArgumentException("Invalid email address: " + to);
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            
            String htmlContent = buildHtmlContent(data);
            helper.setText(htmlContent, true);

            byte[] pdfBytes = pdfService.generateReceiptPdf(data);
            helper.addAttachment("payment-receipt.pdf", new jakarta.mail.util.ByteArrayDataSource(pdfBytes, "application/pdf"));

            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send receipt email", e);
        }
    }

    private boolean isValidEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return false;
        }
        return EMAIL_PATTERN.matcher(email).matches();
    }

    private String buildHtmlContent(Map<String, Object> data) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<meta charset='UTF-8'>");
        html.append("<meta name='viewport' content='width=device-width, initial-scale=1.0'>");
        html.append("<title>Payment Receipt - CyberLearnix</title>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: 0 auto; padding: 20px; }");
        html.append(".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 30px; text-align: center; border-radius: 10px 10px 0 0; }");
        html.append(".header h1 { margin: 0; font-size: 28px; }");
        html.append(".content { background: #f9f9f9; padding: 30px; border-radius: 0 0 10px 10px; }");
        html.append(".receipt-number { text-align: center; margin: 20px 0; padding: 15px; background: white; border-radius: 8px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        html.append(".receipt-number strong { color: #667eea; font-size: 18px; }");
        html.append(".details-table { width: 100%; margin: 20px 0; border-collapse: collapse; background: white; border-radius: 8px; overflow: hidden; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        html.append(".details-table td { padding: 12px 15px; border-bottom: 1px solid #eee; }");
        html.append(".details-table tr:last-child td { border-bottom: none; }");
        html.append(".label { font-weight: bold; color: #555; width: 40%; }");
        html.append(".value { color: #333; }");
        html.append(".status { text-align: center; padding: 15px; background: #d4edda; color: #155724; border-radius: 8px; margin: 20px 0; font-weight: bold; font-size: 16px; }");
        html.append(".footer { text-align: center; margin-top: 30px; padding-top: 20px; border-top: 1px solid #eee; color: #666; }");
        html.append(".footer a { color: #667eea; text-decoration: none; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class='header'>");
        html.append("<h1>Payment Receipt</h1>");
        html.append("<p>CyberLearnix</p>");
        html.append("</div>");
        
        html.append("<div class='content'>");
        html.append("<div class='receipt-number'>");
        html.append("<strong>Receipt Number: ").append(data.get("receiptId")).append("</strong>");
        html.append("<br><small>Date: ").append(data.get("submittedAt")).append("</small>");
        html.append("</div>");
        
        html.append("<table class='details-table'>");
        html.append("<tr><td class='label'>Student Name:</td><td class='value'>").append(data.get("studentName")).append("</td></tr>");
        html.append("<tr><td class='label'>Student Email:</td><td class='value'>").append(data.get("studentEmail")).append("</td></tr>");
        html.append("<tr><td class='label'>Course Title:</td><td class='value'>").append(data.get("courseTitle")).append("</td></tr>");
        html.append("<tr><td class='label'>Amount Paid:</td><td class='value'>").append(data.get("amountPaid")).append("</td></tr>");
        html.append("<tr><td class='label'>Transaction ID:</td><td class='value'>").append(data.get("transactionId")).append("</td></tr>");
        html.append("<tr><td class='label'>PayU Reference ID:</td><td class='value'>").append(data.get("payuRefId")).append("</td></tr>");
        html.append("<tr><td class='label'>Payment Mode:</td><td class='value'>").append(data.get("paymentMode")).append("</td></tr>");
        html.append("<tr><td class='label'>Bank Reference No:</td><td class='value'>").append(data.get("bankRefNo")).append("</td></tr>");
        html.append("</table>");
        
        html.append("<div class='status'>");
        html.append("Payment Status: ").append(data.get("paymentStatus"));
        html.append("</div>");
        
        html.append("<div class='footer'>");
        html.append("<p>Thank you for your payment! The detailed receipt is attached as a PDF.</p>");
        html.append("<p>If you have any questions, please contact our support team.</p>");
        html.append("<p>Email: <a href='mailto:support@cyberlearnix.com'>support@cyberlearnix.com</a></p>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }

    private String buildFailureHtml(String studentName, String txnid, String errorMsg) {
        return "<!DOCTYPE html><html><head><meta charset='UTF-8'><style>"
                + "body{font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;color:#333;}"
                + ".header{background:#e53e3e;color:white;padding:30px;text-align:center;border-radius:10px 10px 0 0;}"
                + ".content{background:#f9f9f9;padding:30px;border-radius:0 0 10px 10px;}"
                + ".txn{background:white;padding:15px;border-radius:8px;margin:20px 0;border-left:4px solid #e53e3e;}"
                + ".btn{display:inline-block;background:#667eea;color:white;padding:12px 30px;border-radius:6px;text-decoration:none;font-weight:bold;margin-top:20px;}"
                + ".footer{text-align:center;margin-top:20px;color:#666;font-size:13px;}"
                + "</style></head><body>"
                + "<div class='header'><h1>Payment Failed</h1><p>CyberLearnix</p></div>"
                + "<div class='content'>"
                + "<p>Hi " + studentName + ",</p>"
                + "<p>Unfortunately, your payment could not be processed.</p>"
                + "<div class='txn'><strong>Transaction ID:</strong> " + txnid + "<br>"
                + (errorMsg != null && !errorMsg.isEmpty() ? "<strong>Reason:</strong> " + errorMsg : "") + "</div>"
                + "<p>Please try again. If the issue persists, contact our support team.</p>"
                + "<a href='mailto:support@cyberlearnix.com' class='btn'>Contact Support</a>"
                + "<div class='footer'><p>Email: support@cyberlearnix.com</p></div>"
                + "</div></body></html>";
    }
}
