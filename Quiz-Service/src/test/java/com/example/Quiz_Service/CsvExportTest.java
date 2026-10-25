package com.example.Quiz_Service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.Quiz_Service.service.AdminReportService;
import org.junit.jupiter.api.Test;

class CsvExportTest {
  @Test
  void csv_shouldEscapeQuotesAndNeutralizeFormulas() {
    assertEquals("\"' =SUM(1,2)\"", AdminReportService.csvCell(" =SUM(1,2)"));
    assertEquals("\"A\"\"B\"", AdminReportService.csvCell("A\"B"));
    assertEquals("\"42\"", AdminReportService.csvCell(42));
  }
}
