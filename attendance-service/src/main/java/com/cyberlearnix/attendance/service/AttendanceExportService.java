package com.cyberlearnix.attendance.service;

import com.cyberlearnix.attendance.dto.AttendanceDto;
import com.cyberlearnix.attendance.dto.StudentAttendanceReport;
import com.cyberlearnix.attendance.entity.Meeting;
import com.cyberlearnix.attendance.entity.MeetingAttendance;
import com.cyberlearnix.attendance.repository.MeetingAttendanceRepository;
import com.cyberlearnix.attendance.repository.MeetingRepository;
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
    private final MeetingAttendanceRepository attendanceRepo;
    private final AttendanceAnalyticsService analyticsService;

    public byte[] exportMeetingAttendanceExcel(String meetingId) throws IOException {
        if (!meetingRepo.existsById(meetingId)) {
            throw new IllegalArgumentException("Meeting not found: " + meetingId);
        }
        List<MeetingAttendance> records = attendanceRepo.findByMeetingIdOrderByStudentIdAsc(meetingId);

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Attendance");
            String[] cols = {"Student ID", "Duration (min)", "Attendance %", "Status", "Joined At", "Left At"};
            createStyledHeaderRow(sheet, cols, wb);
            int rowNum = 1;
            for (MeetingAttendance fa : records) {
                writeMeetingAttendanceRow(sheet.createRow(rowNum++), fa);
            }
            autoSize(sheet, cols.length);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

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

    private void writeMeetingAttendanceRow(Row row, MeetingAttendance fa) {
        row.createCell(0).setCellValue(fa.getStudentId());
        row.createCell(1).setCellValue(fa.getDurationMinutes() != null ? fa.getDurationMinutes() : 0);
        row.createCell(2).setCellValue(fa.getAttendancePercentage() != null ? fa.getAttendancePercentage() : 0);
        row.createCell(3).setCellValue(fa.getAttendanceStatus() != null ? fa.getAttendanceStatus().name() : "");
        row.createCell(4).setCellValue(fa.getJoinTime() != null ? fa.getJoinTime().toString() : "");
        row.createCell(5).setCellValue(fa.getLeaveTime() != null ? fa.getLeaveTime().toString() : "");
    }

    private void writeSummarySection(Sheet sheet, StudentAttendanceReport report) {
        Row r0 = sheet.createRow(0);
        r0.createCell(0).setCellValue("Student:");
        r0.createCell(1).setCellValue(report.getStudentName());
        r0.createCell(3).setCellValue("Overall Attendance:");
        r0.createCell(4).setCellValue(report.getOverallPercentage() != null ? report.getOverallPercentage() / 100.0 : 0);

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("Total Classes:");
        r1.createCell(1).setCellValue(report.getTotalMeetings() != null ? report.getTotalMeetings() : 0);
        r1.createCell(3).setCellValue("Present Status:");
        r1.createCell(4).setCellValue("P: " + report.getPresentCount() + ", L: " + report.getLateCount() + ", A: " + report.getAbsentCount());
    }

    private void writeSessionRow(Row row, AttendanceDto a) {
        row.createCell(0).setCellValue(safe(a.getMeetingTitle()));
        row.createCell(1).setCellValue(a.getMeetingScheduledStart() != null ? a.getMeetingScheduledStart().toString() : "");
        row.createCell(2).setCellValue(a.getTotalActiveSeconds() != null ? a.getTotalActiveSeconds() / 60.0 : 0);
        row.createCell(3).setCellValue(a.getMeetingDurationSeconds() != null ? a.getMeetingDurationSeconds() / 60.0 : 0);
        row.createCell(4).setCellValue(a.getAttendancePercentage() != null ? a.getAttendancePercentage() : 0);
        row.createCell(5).setCellValue(a.getStatus() != null ? a.getStatus().name() : "");
        row.createCell(6).setCellValue(Boolean.TRUE.equals(a.getLate()) ? "LATE" : "ON TIME");
    }

    private void autoSize(Sheet sheet, int cols) {
        for (int i = 0; i < cols; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }
}
