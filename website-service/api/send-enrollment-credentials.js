// FILE: /api/send-enrollment-credentials.js
// Purpose: Send login credentials to student after account creation

import { Resend } from 'resend';

const resend = new Resend(process.env.RESEND_API_KEY || process.env.VITE_RESEND_API_KEY);

export default async function handler(req, res) {
    // CORS
    res.setHeader('Access-Control-Allow-Credentials', 'true');
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

    if (req.method === 'OPTIONS') return res.status(200).end();
    if (req.method !== 'POST') return res.status(405).json({ error: 'Method not allowed' });

    try {
        const { 
            studentEmail, 
            studentName, 
            temporaryPassword, 
            courseName, 
            loginUrl = 'https://cyberlearnix.com/login.html'
        } = req.body;

        // Validate required fields
        if (!studentEmail || !studentName || !temporaryPassword) {
            return res.status(400).json({ 
                error: 'studentEmail, studentName, and temporaryPassword are required' 
            });
        }

        // Create HTML email template
        const htmlTemplate = `
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    body {
                        font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                        line-height: 1.6;
                        color: #1e293b;
                    }
                    .container {
                        max-width: 600px;
                        margin: 0 auto;
                        padding: 20px;
                    }
                    .header {
                        background: linear-gradient(135deg, #0057ff, #e10600);
                        color: white;
                        padding: 30px;
                        border-radius: 8px 8px 0 0;
                        text-align: center;
                    }
                    .content {
                        background: #f8fafc;
                        padding: 30px;
                        border-radius: 0 0 8px 8px;
                    }
                    .credential-box {
                        background: white;
                        border: 2px dashed #cbd5e1;
                        padding: 20px;
                        border-radius: 8px;
                        margin: 20px 0;
                    }
                    .credential-row {
                        display: flex;
                        justify-content: space-between;
                        padding: 10px 0;
                        border-bottom: 1px solid #e2e8f0;
                    }
                    .credential-row:last-child {
                        border-bottom: none;
                    }
                    .label {
                        font-weight: 600;
                        color: #64748b;
                    }
                    .value {
                        font-family: 'Courier New', monospace;
                        color: #0057ff;
                        font-weight: 500;
                    }
                    .button {
                        display: inline-block;
                        background: #0057ff;
                        color: white;
                        padding: 12px 24px;
                        text-decoration: none;
                        border-radius: 6px;
                        margin-top: 20px;
                        font-weight: 600;
                    }
                    .warning-box {
                        background: #fef3c7;
                        border-left: 4px solid #f59e0b;
                        padding: 15px;
                        margin: 20px 0;
                        border-radius: 4px;
                    }
                    .warning-box strong {
                        color: #92400e;
                    }
                    .footer {
                        text-align: center;
                        padding: 20px;
                        color: #94a3b8;
                        font-size: 0.85rem;
                        border-top: 1px solid #e2e8f0;
                        margin-top: 20px;
                    }
                    .course-badge {
                        display: inline-block;
                        background: #dbeafe;
                        color: #0c4a6e;
                        padding: 6px 12px;
                        border-radius: 20px;
                        font-size: 0.85rem;
                        font-weight: 600;
                        margin: 10px 0;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>🎓 Welcome to Cyberlearnix!</h1>
                        <p>Your course access is ready</p>
                    </div>
                    
                    <div class="content">
                        <h2>Hello ${studentName},</h2>
                        <p>Congratulations! Your enrollment has been approved and your account is now active. You can now access your course materials and start learning.</p>
                        
                        <div class="course-badge">${courseName}</div>
                        
                        <h3>Your Login Credentials:</h3>
                        <div class="credential-box">
                            <div class="credential-row">
                                <span class="label">Email:</span>
                                <span class="value">${studentEmail}</span>
                            </div>
                            <div class="credential-row">
                                <span class="label">Temporary Password:</span>
                                <span class="value">${temporaryPassword}</span>
                            </div>
                        </div>
                        
                        <div class="warning-box">
                            <strong>⚠️ Important:</strong> This is a temporary password. You must change it on your first login for security reasons.
                        </div>
                        
                        <h3>Next Steps:</h3>
                        <ol>
                            <li>Click the button below to login to your account</li>
                            <li>Enter your email and temporary password</li>
                            <li>Change your password to something secure</li>
                            <li>Start exploring your course materials</li>
                        </ol>
                        
                        <a href="${loginUrl}" class="button">Login to Your Account</a>
                        
                        <h3>Course Access:</h3>
                        <p>You now have full access to:</p>
                        <ul>
                            <li>📚 Course materials and lessons</li>
                            <li>💻 Hands-on lab exercises</li>
                            <li>📝 Assignments and quizzes</li>
                            <li>🎓 Certificate of completion</li>
                            <li>👨‍🏫 Mentorship and support</li>
                        </ul>
                        
                        <h3>Need Help?</h3>
                        <p>If you have any issues logging in or accessing your course, please contact our support team at <strong>support@cyberlearnix.com</strong> or reply to this email.</p>
                        
                        <p><strong>Happy Learning! 🚀</strong></p>
                    </div>
                    
                    <div class="footer">
                        <p>© 2026 Cyberlearnix Private Limited. All rights reserved.</p>
                        <p>This email contains sensitive information. Please keep it secure and don't share your password with anyone.</p>
                    </div>
                </div>
            </body>
            </html>
        `;

        // Send email using Resend
        const emailResponse = await resend.emails.send({
            from: 'Cyberlearnix Academy <academy@cyberlearnix.com>',
            to: [studentEmail],
            subject: `🎓 Your Course Access is Ready - Login Credentials Inside`,
            html: htmlTemplate,
            tags: ['enrollment', 'credentials']
        });

        if (emailResponse.error) {
            throw new Error(`Email sending failed: ${emailResponse.error.message}`);
        }

        // Also send a copy to admin (optional but useful)
        await resend.emails.send({
            from: 'Cyberlearnix Admissions <admissions@cyberlearnix.com>',
            to: ['admin@cyberlearnix.com'],
            subject: `[ADMIN] Credentials Sent - ${studentName}`,
            html: `
                <p>Credentials have been sent to <strong>${studentEmail}</strong></p>
                <p>Student Name: <strong>${studentName}</strong></p>
                <p>Course: <strong>${courseName}</strong></p>
                <p>Time: ${new Date().toLocaleString()}</p>
            `,
            tags: ['enrollment', 'admin-log']
        }).catch(err => {
            console.warn('Admin notification email failed (non-critical):', err.message);
        });

        return res.status(200).json({
            success: true,
            message: 'Credentials sent successfully',
            email: {
                to: studentEmail,
                subject: '🎓 Your Course Access is Ready',
                timestamp: new Date().toISOString()
            }
        });

    } catch (error) {
        console.error('Error sending credentials:', error);
        return res.status(500).json({ 
            error: error.message || 'Failed to send credentials' 
        });
    }
}
