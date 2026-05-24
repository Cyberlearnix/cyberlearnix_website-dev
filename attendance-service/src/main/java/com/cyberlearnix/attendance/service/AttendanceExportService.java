package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.AttendanceDto;
import com.cyberlearnix.attendance.dto.StudentAttendanceReport;
import com.cyberlearnix.attendance.entity.*;
import com.cyberlearnix.attendance.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceExportService {

    private final MeetingRepository meetingRepo;
    private final FinalAttendanceRepository finalAttRepo;
    private final AttendanceAnalyticsService analyticsService;

    /**
     * Export all attendance for a meeting as Excel bytes.
     */
    public byte[] exportMeetingAttendanceExcel(String meetingId) throws IOException {
        Meeting meeting = meetingRepo.findById(meetingId)
            .orElseThrow(() -> new IllegalArgumentException("Meeting not found: " + meetingId));
        List<FinalAttendance> records = finalAttRepo.findByMeetingIdOrderByStudentNameAsc(meetingId);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Attendance");

            // Header
            Row header = sheet.createRow(0);
            String[] cols = {"Student ID", "Name", "Email", "Duration (min)", "Meeting Duration (min)",
                "Attendance %", "Status", "Rejoin Count", "Late", "Late By (min)", "Overridden", "Counts for Certificate"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                CellStyle style = wb.createCellStyle();
                Font font = wb.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            // Data rows
            int rowNum = 1;
            for (FinalAttendance fa : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(fa.getStudentId());
                row.createCell(1).setCellValue(safe(fa.getStudentName()));
                row.createCell(2).setCellValue(safe(fa.getStudentEmail()));
                row.createCell(3).setCellValue(fa.getTotalActiveSeconds() != null ? fa.getTotalActiveSeconds() / 60.0 : 0);
                row.createCell(4).setCellValue(fa.getMeetingDurationSeconds() != null ? fa.getMeetingDurationSeconds() / 60.0 : 0);
                row.createCell(5).setCellValue(fa.getAttendancePercentage() != null ? fa.getAttendancePercentage() : 0);
                row.createCell(6).setCellValue(fa.getStatus() != null ? fa.getStatus().name() : "");
                row.createCell(7).setCellValue(fa.getRejoinCount() != null ? fa.getRejoinCount() : 0);
                row.createCell(8).setCellValue(Boolean.TRUE.equals(fa.getLate()) ? "Yes" : "No");
                row.createCell(9).setCellValue(fa.getLateByMinutes() != null ? fa.getLateByMinutes() : 0);
                row.createCell(10).setCellValue(Boolean.TRUE.equals(fa.getOverridden()) ? "Yes" : "No");
                row.createCell(11).setCellValue(Boolean.TRUE.equals(fa.getCountsForCertificate()) ? "Yes" : "No");
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    /**
     * Export student report for a course as Excel.
     */
    public byte[] exportStudentReportExcel(String studentId, String courseId) throws IOException {
        StudentAttendanceReport report = analyticsService.buildStudentReport(studentId, courseId);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Student Attendance");

            // Summary
            Row r0 = sheet.createRow(0);
            r0.createCell(0).setCellValue("Student");
            r0.createCell(1).setCellValue(safe(report.getStudentName()) + " (" + safe(report.getStudentEmail()) + ")");

            Row r1 = sheet.createRow(1);
            r1.createCell(0).setCellValue("Overall %");
            r1.createCell(1).setCellValue(report.getOverallPercentage() != null ? report.getOverallPercentage() : 0);

            Row r2 = sheet.createRow(2);
            r2.createCell(0).setCellValue("Certificate Eligible");
            r2.createCell(1).setCellValue(Boolean.TRUE.equals(report.getCertificateEligible()) ? "YES" : "NO");

            sheet.createRow(3); // blank
            Row header = sheet.createRow(4);
            String[] cols = {"Meeting", "Date", "Duration (min)", "Meeting Duration (min)", "Attendance %", "Status", "Late"};
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                CellStyle style = wb.createCellStyle();
                Font font = wb.createFont();
                font.setBold(true);
                style.setFont(font);
                cell.setCellStyle(style);
            }

            int rowNum = 5;
            if (report.getSessions() != null) {
                for (AttendanceDto a : report.getSessions()) {
                    Row row = sheet.createRow(rowNum++);
                    row.createCell(0).setCellValue(safe(a.getMeetingTitle()));
                    row.createCell(1).setCellValue(a.getMeetingScheduledStart() != null ? a.getMeetingScheduledStart().toString() : "");
                    row.createCell(2).setCellValue(a.getTotalActiveSeconds() != null ? a.getTotalActiveSeconds() / 60.0 : 0);
                    row.createCell(3).setCellValue(a.getMeetingDurationSeconds() != null ? a.getMeetingDurationSeconds() / 60.0 : 0);
                    row.createCell(4).setCellValue(a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0);
                    row.createCell(5).setCellValue(a.getStatus() != null ? a.getStatus().name() : "");
                    row.createCell(6).setCellValue(Boolean.TRUE.equals(a.getLate()) ? "Yes" : "No");
                }
            }

            for (int i = 0; i < cols.length; i++) sheet.autoSizeColumn(i);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
