package com.grouphive.serviceagreement.service;

import com.grouphive.serviceagreement.model.Worklog;
import com.grouphive.serviceagreement.util.Values;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class WorklogExportService {
  public byte[] createWorkbook(List<Worklog> worklogs) {
    try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
      var sheet = workbook.createSheet("Worklogs");
      String[] headers = {
          "ID", "Order Number", "Customer", "Ticket Number", "Booked At", "Person", "Hours", "Comment"
      };

      Font headerFont = workbook.createFont();
      headerFont.setBold(true);
      CellStyle headerStyle = workbook.createCellStyle();
      headerStyle.setFont(headerFont);

      Row header = sheet.createRow(0);
      for (int i = 0; i < headers.length; i++) {
        var cell = header.createCell(i);
        cell.setCellValue(headers[i]);
        cell.setCellStyle(headerStyle);
      }

      int rowIndex = 1;
      for (Worklog worklog : worklogs) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(worklog.id());
        row.createCell(1).setCellValue(worklog.orderNumber());
        row.createCell(2).setCellValue(worklog.customer());
        row.createCell(3).setCellValue(worklog.ticketNumber());
        row.createCell(4).setCellValue(Values.iso(worklog.bookedAt()));
        row.createCell(5).setCellValue(worklog.person());
        row.createCell(6).setCellValue(Values.quarter(worklog.hours()).doubleValue());
        row.createCell(7).setCellValue(worklog.comment());
      }

      for (int i = 0; i < headers.length; i++) {
        sheet.autoSizeColumn(i);
      }

      workbook.write(output);
      return output.toByteArray();
    } catch (IOException exception) {
      throw new IllegalStateException("Failed to create worklog export", exception);
    }
  }
}
