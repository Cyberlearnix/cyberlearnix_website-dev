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
        if (!meetingRepo.existsById(meetingId)) {
            throw new IllegalArgumentException("Meeting not found: " + meetingId);
        }
        List<FinalAttendance> records = finalAttRepo.findByMeetingIdOrderByStudentNameAsc(meetingId);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Attendance");
            String[] cols = {"Student ID", "Name", "Email", "Duration (min)", "Meeting Duration (min)",
                "Attendance %", "Status", "Rejoin Count", "Late", "Late By (min)", "Overridden", "Counts for Certificate"};
            createStyledHeaderRow(sheet, cols, wb);
            int rowNum = 1;
            for (FinalAttendance fa : records) {
                writeMeetingAttendanceRow(sheet.createRow(rowNum++), fa);
            }
            autoSize(sheet, cols.length);
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
            writeSummarySection(sheet, report);
            String[] cols = {"Meeting", "Date", "Duration (min)", "Meeting Duration (min)", "Attendance %", "Status", "Late"};
            createStyledHeaderRow(sheet, cols, wb, 4);
            int rowNum = 5;
            if (report.getSessions() != null) {
                for (AttendanceDto a : report.getSessions()) {
                    writeSessionRow(sheet.createRow(rowNum++), a);
                }
            }
            autoSize(sheet, cols.length);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ---- helpers ----

    private void createStyledHeaderRow(Sheet sheet, String[] cols, Workbook wb) {
        createStyledHeaderRow(sheet, cols, wb, 0);
    }

    private void createStyledHeaderRow(Sheet sheet, String[] cols, Workbook wb, int rowIndex) {
        Row header = sheet.createRow(rowIndex);
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        for (int i = 0; i < cols.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(cols[i]);
            cell.setCellStyle(style);
        }
    }

    private void writeMeetingAttendanceRow(Row row, FinalAttendance fa) {
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

    private void writeSummarySection(Sheet sheet, StudentAttendanceReport report) {
        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("Student");
        r0.createCell(1).setCellValue(safe(report.getStudentName()) + " (" + safe(report.getStudentEmail()) + ")");
        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("Overall %");
        r1.createCell(1).setCellValue(report.getOverallPercentage() != null ? report.getOverallPercentage() : 0);
        Row r2 = sheet.createRow(2);
        r2.createCell(0).setCellValue("Certificate Eligible");
        r2.createCell(1).setCellValue(Boolean.TRUE.equals(report.getCertificateEligible()) ? "YES" : "NO");
        sheet.createRow(3);
    }

    private void writeSessionRow(Row row, AttendanceDto a) {
        row.createCell(0).setCellValue(safe(a.getMeetingTitle()));
        row.createCell(1).setCellValue(a.getMeetingScheduledStart() != null ? a.getMeetingScheduledStart().toString() : "");
        row.createCell(2).setCellValue(a.getTotalActiveSeconds() != null ? a.getTotalActiveSeconds() / 60.0 : 0);
        row.createCell(3).setCellValue(a.getMeetingDurationSeconds() != null ? a.getMeetingDurationSeconds() / 60.0 : 0);
        row.createCell(4).setCellValue(a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0);
        row.createCell(5).setCellValue(a.getStatus() != null ? a.getStatus().name() : "");
        row.createCell(6).setCellValue(Boolean.TRUE.equals(a.getLate()) ? "Yes" : "No");
    }

    private void autoSize(Sheet sheet, int columns) {
        for (int i = 0; i < columns; i++) sheet.autoSizeColumn(i);
    }

    private String safe(String s) {
        return s != null ? s : "";
    }
}
