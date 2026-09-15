package com.sky.service.impl;

import com.sky.service.WorkspaceService;
import com.sky.vo.BusinessDataVO;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportServiceImplTest {
    @Test void exportLoadsBundledTemplateAndProducesReadableWorkbook() throws Exception {
        WorkspaceService workspace = mock(WorkspaceService.class);
        when(workspace.getBusinessData(any(),any())).thenReturn(BusinessDataVO.builder()
                .turnover(100.0).validOrderCount(5).orderCompletionRate(0.5).unitPrice(20.0).newUsers(3).build());
        ReportServiceImpl service = new ReportServiceImpl();
        ReflectionTestUtils.setField(service,"workspaceService",workspace);
        MockHttpServletResponse response = new MockHttpServletResponse();
        service.exportBusinessData(response);
        assertEquals("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",response.getContentType());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(response.getContentAsByteArray()))) {
            assertEquals(100.0,workbook.getSheetAt(0).getRow(3).getCell(2).getNumericCellValue());
            assertNotNull(workbook.getSheetAt(0).getRow(36).getCell(1).getStringCellValue());
        }
    }
}
